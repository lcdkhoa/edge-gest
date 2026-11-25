package com.lcd.edgegest;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EdgeGest - gesture điều hướng không cần Accessibility, không cần overlay window.
 *
 * Đọc trực tiếp luồng cảm ứng từ /dev/input/eventX (uid shell thuộc nhóm input),
 * nhận diện cử chỉ ở mép màn hình rồi inject phím Back/Home/Recents.
 *
 * Chạy: CLASSPATH=.../edgegest.dex app_process /system/bin com.lcd.edgegest.Main [--debug] [--dev=/dev/input/eventN]
 */
public final class Main {
    private static final String MIUI_HOME = "com.miui.home";
    private static final long POLL_MS = 4000;

    // evdev constants
    private static final int EV_SYN = 0x00, EV_KEY = 0x01, EV_ABS = 0x03;
    private static final int SYN_REPORT = 0x00;
    private static final int BTN_TOUCH = 0x14a;
    private static final int ABS_MT_SLOT = 0x2f;
    private static final int ABS_MT_POSITION_X = 0x35;
    private static final int ABS_MT_POSITION_Y = 0x36;

    private static boolean debug;
    private static final Gestures G = new Gestures();

    private static Context ctx;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            Env.log("fatal: " + Log.getStackTraceString(t));
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        String dev = null;
        boolean server = false;
        for (String a : args) {
            if (a.equals("--debug")) debug = true;
            else if (a.equals("--server")) server = true;
            else if (a.startsWith("--dev=")) dev = a.substring(6);
            else if (a.startsWith("--edge=")) G.edgeFrac = Float.parseFloat(a.substring(7));
        }

        Looper.prepareMainLooper();
        Handler handler = new Handler(Looper.getMainLooper());

        // Context + InputManager để inject phím nhanh (có fallback sang lệnh `input`).
        try {
            ctx = new ShellContext(Env.systemContext());
            Env.initInput();
        } catch (Throwable t) {
            Env.log("init input/context lỗi (vẫn chạy, dùng lệnh input): " + t);
        }

        if (server) {
            // Chế độ injector: nhận lệnh cử chỉ từ APK overlay qua TCP localhost.
            Thread srv = new Thread(new CommandServer(), "edgegest-server");
            srv.setDaemon(false);
            srv.start();
            Env.log("command server started on 127.0.0.1:" + CommandServer.PORT);
        } else {
            // Chế độ đọc /dev/input (không overlay): tự nhận diện cử chỉ.
            if (dev == null) dev = findTouchDevice();
            readMaxes(dev);
            G.compute();
            Env.log("start: dev=" + dev + " maxX=" + G.maxX + " maxY=" + G.maxY
                    + " edgeX=" + G.edgeX + " bottomZone=" + G.bottomZone
                    + " backDist=" + G.backDist + " homeDist=" + G.homeDist + " switchDist=" + G.switchDist);
            Thread reader = new Thread(new ReaderLoop(dev), "edgegest-reader");
            reader.setDaemon(false);
            reader.start();
            Env.log("reader started");
        }

        handler.post(poll);
        Looper.loop();
    }

    /** Nhận lệnh một ký tự từ APK overlay và inject phím tương ứng. */
    static final class CommandServer implements Runnable {
        static final int PORT = 47820;

        @Override
        public void run() {
            while (true) {
                try (java.net.ServerSocket ss = new java.net.ServerSocket()) {
                    ss.setReuseAddress(true);
                    ss.bind(new java.net.InetSocketAddress(
                            java.net.InetAddress.getByName("127.0.0.1"), PORT));
                    while (true) {
                        java.net.Socket s = ss.accept();
                        handle(s);
                    }
                } catch (Throwable t) {
                    Env.log("server lỗi, thử lại sau 1s: " + t);
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
        }

        private void handle(java.net.Socket s) {
            try (java.net.Socket sock = s) {
                sock.setTcpNoDelay(true);
                java.io.InputStream in = sock.getInputStream();
                int c;
                while ((c = in.read()) != -1) {
                    doCommand((char) c);
                }
            } catch (Throwable t) {
                Env.log("handle lỗi: " + t);
            }
        }

        private void doCommand(char c) {
            switch (c) {
                case 'B': Env.log("cmd BACK");    Env.key(KeyEvent.KEYCODE_BACK); break;
                case 'H': Env.log("cmd HOME");    Env.key(KeyEvent.KEYCODE_HOME); break;
                case 'R': Env.log("cmd RECENTS"); Env.key(KeyEvent.KEYCODE_APP_SWITCH); break;
                case 'S': Env.log("cmd SWITCH");  Env.switchToPrevious(ctx); break;
                default: break; // bỏ qua \n và ký tự lạ
            }
        }
    }

    // ---- tìm thiết bị cảm ứng theo tên ----

    private static String findTouchDevice() {
        String out = Env.sh("getevent -pl 2>/dev/null");
        String cur = null;
        for (String line : out.split("\n")) {
            Matcher m = Pattern.compile("add device \\d+: (/dev/input/event\\d+)").matcher(line);
            if (m.find()) cur = m.group(1);
            if (line.contains("Xiaomi_Touch_Input") && cur != null) return cur;
        }
        // Fallback: thiết bị đầu tiên có ABS_MT_POSITION_X
        cur = null;
        for (String line : out.split("\n")) {
            Matcher m = Pattern.compile("add device \\d+: (/dev/input/event\\d+)").matcher(line);
            if (m.find()) cur = m.group(1);
            if (line.contains("ABS_MT_POSITION_X") && cur != null) return cur;
        }
        return "/dev/input/event7";
    }

    private static void readMaxes(String dev) {
        String out = Env.sh("getevent -pl " + dev + " 2>/dev/null");
        Matcher mx = Pattern.compile("ABS_MT_POSITION_X.*?max (\\d+)").matcher(out);
        if (mx.find()) G.maxX = Integer.parseInt(mx.group(1));
        Matcher my = Pattern.compile("ABS_MT_POSITION_Y.*?max (\\d+)").matcher(out);
        if (my.find()) G.maxY = Integer.parseInt(my.group(1));
    }

    // ---- vòng đọc evdev ----

    static final class ReaderLoop implements Runnable {
        private final String dev;

        ReaderLoop(String dev) {
            this.dev = dev;
        }

        @Override
        public void run() {
            while (true) {
                try (DataInputStream in = new DataInputStream(new FileInputStream(new File(dev)))) {
                    loop(in);
                } catch (Throwable t) {
                    Env.log("reader lỗi, thử lại sau 1s: " + t);
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
        }

        private void loop(DataInputStream in) throws Exception {
            byte[] buf = new byte[24]; // struct input_event 64-bit: time(16) + type(2) + code(2) + value(4)
            int slot = 0;
            int curX = 0, curY = 0;
            int startX = 0, startY = 0;
            long startT = 0;
            boolean tracking = false;
            boolean pendingDown = false, pendingUp = false;

            while (true) {
                in.readFully(buf);
                int type = u16(buf, 16);
                int code = u16(buf, 18);
                int value = s32(buf, 20);

                if (type == EV_ABS) {
                    if (code == ABS_MT_SLOT) {
                        slot = value;
                    } else if (slot == 0) {
                        if (code == ABS_MT_POSITION_X) curX = value;
                        else if (code == ABS_MT_POSITION_Y) curY = value;
                    }
                } else if (type == EV_KEY && code == BTN_TOUCH) {
                    if (value == 1) pendingDown = true;
                    else pendingUp = true;
                } else if (type == EV_SYN && code == SYN_REPORT) {
                    if (pendingDown) {
                        startX = curX;
                        startY = curY;
                        startT = SystemClock.uptimeMillis();
                        tracking = true;
                        pendingDown = false;
                    }
                    if (pendingUp) {
                        if (tracking) {
                            evaluate(startX, startY, curX, curY, SystemClock.uptimeMillis() - startT);
                        }
                        tracking = false;
                        pendingUp = false;
                    }
                }
            }
        }
    }

    private static void evaluate(int sx, int sy, int ex, int ey, long dur) {
        String g = G.classify(sx, sy, ex, ey, dur);
        if (g == null) {
            if (debug) Env.log("ignore: s=(" + sx + "," + sy + ") e=(" + ex + "," + ey + ") dur=" + dur);
            return;
        }
        Env.log("gesture: " + g + " s=(" + sx + "," + sy + ") e=(" + ex + "," + ey + ") dur=" + dur);
        switch (g) {
            case Gestures.BACK:    Env.key(KeyEvent.KEYCODE_BACK); break;
            case Gestures.HOME:    Env.key(KeyEvent.KEYCODE_HOME); break;
            case Gestures.RECENTS: Env.key(KeyEvent.KEYCODE_APP_SWITCH); break;
            case Gestures.SWITCH:  Env.switchToPrevious(ctx); break;
        }
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static int s32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    // ---- giữ thanh 3 nút luôn ẩn khi KHÔNG ở MIUI launcher ----

    private static final Runnable poll = new Poll();

    static final class Poll implements Runnable {
        @Override
        public void run() {
            try {
                boolean awake = true;
                try {
                    PowerManager pm = ctx == null ? null
                            : (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                    awake = pm == null || pm.isInteractive();
                } catch (Throwable ignored) {}

                if (awake) {
                    String out = Env.sh("cmd settings get global force_fsg_nav_bar;"
                            + "cmd role get-role-holders android.app.role.HOME");
                    String[] lines = out.split("\n");
                    String fsg = lines.length > 0 ? lines[0].trim() : "";
                    String home = lines.length > 1 ? lines[1].trim() : "";
                    if (!home.contains(MIUI_HOME) && !"1".equals(fsg)) {
                        Env.sh("cmd settings put global force_fsg_nav_bar 1");
                        Env.log("force_fsg_nav_bar '" + fsg + "' -> 1");
                    }
                }
            } catch (Throwable t) {
                Env.log("poll lỗi: " + t);
            }
            new Handler(Looper.getMainLooper()).postDelayed(this, POLL_MS);
        }
    }
}
