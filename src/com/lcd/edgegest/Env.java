package com.lcd.edgegest;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Các thao tác cần quyền shell: tạo Context, inject phím, chuyển task, mở panel/app, chạy lệnh. */
final class Env {
    static final String TAG = "EdgeGest";

    private static Object inputManager;
    private static Method injectMethod;
    private static Object taskService;
    private static Method startFromRecents;

    private Env() {}

    static void log(String msg) {
        Log.i(TAG, msg);
        System.out.println(System.currentTimeMillis() + " " + msg);
    }

    /** Dựng ActivityThread giả (cần Looper sẵn) rồi lấy system context. */
    static Context systemContext() throws Exception {
        Class<?> at = Class.forName("android.app.ActivityThread");
        Constructor<?> ctor = at.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object thread = ctor.newInstance();

        Field cur = at.getDeclaredField("sCurrentActivityThread");
        cur.setAccessible(true);
        cur.set(null, thread);
        try {
            Field sys = at.getDeclaredField("mSystemThread");
            sys.setAccessible(true);
            sys.setBoolean(thread, true);
        } catch (Throwable ignored) {
        }

        initSharedMemory();

        Method m = at.getDeclaredMethod("getSystemContext");
        m.setAccessible(true);
        return (Context) m.invoke(thread);
    }

    /** Android 16+: WindowManagerGlobal cần ApplicationSharedMemory (bình thường do zygote/bindApplication tạo). */
    private static void initSharedMemory() {
        try {
            Class<?> c = Class.forName("com.android.internal.os.ApplicationSharedMemory");
            Field inst = c.getField("sInstance");
            if (inst.get(null) == null) {
                Object mem = c.getMethod("create").invoke(null);
                c.getMethod("setInstance", c).invoke(null, mem);
                log("ApplicationSharedMemory initialized");
            }
        } catch (ClassNotFoundException ignored) {
            // Android < 16
        } catch (Throwable t) {
            log("ApplicationSharedMemory init failed: " + t);
        }
    }

    static void initInput() throws Exception {
        try {
            Class<?> g = Class.forName("android.hardware.input.InputManagerGlobal");
            inputManager = g.getDeclaredMethod("getInstance").invoke(null);
        } catch (ClassNotFoundException e) {
            inputManager = android.hardware.input.InputManager.class
                    .getDeclaredMethod("getInstance").invoke(null);
        }
        injectMethod = inputManager.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
    }

    private static boolean injectViaManager = true;

    private static boolean inject(InputEvent ev) {
        try {
            injectMethod.invoke(inputManager, ev, 0 /* INJECT_INPUT_EVENT_MODE_ASYNC */);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static void key(int code) {
        if (injectViaManager && inputManager != null) {
            long now = SystemClock.uptimeMillis();
            int flags = KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY;
            boolean ok = inject(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, InputDevice.SOURCE_KEYBOARD));
            ok &= inject(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, InputDevice.SOURCE_KEYBOARD));
            if (ok) return;
            injectViaManager = false;
            log("InputManager inject failed -> dùng lệnh `input`");
        }
        sh("input keyevent " + code);
    }

    static void showPowerDialog() {
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Object wm = wmg.getMethod("getWindowManagerService").invoke(null);
            if (wm != null) {
                wm.getClass().getMethod("showGlobalActions").invoke(wm);
                return;
            }
        } catch (Throwable t) {
            log("showGlobalActions fallback: " + t);
        }
        shAsync("input keyevent --longpress " + KeyEvent.KEYCODE_POWER);
    }

    static void showVolumePanel(Context ctx) {
        try {
            if (ctx != null) {
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME,
                            AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                    return;
                }
            }
        } catch (Throwable t) {
            log("volumePanel fallback: " + t);
        }
        key(KeyEvent.KEYCODE_VOLUME_UP);
    }

    static void expandNotifications() {
        shAsync("cmd statusbar expand-notifications");
    }

    static void expandQuickSettings() {
        shAsync("cmd statusbar expand-settings");
    }

    static void launchApp(Context ctx, String pkg) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]+")) return;
        try {
            if (ctx != null) {
                Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    ctx.startActivity(intent);
                    log("launch app: " + pkg);
                    return;
                }
            }
        } catch (Throwable t) {
            log("startActivity app fallback (" + pkg + "): " + t);
        }
        shAsync("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
    }

    /** Chuyển sang app dùng gần nhất trước app hiện tại (vuốt ngang ở đáy). */
    @SuppressWarnings("deprecation")
    static void switchToPrevious(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> running = am.getRunningTasks(1);
            int topId = running.isEmpty() ? -1 : running.get(0).taskId;
            List<ActivityManager.RecentTaskInfo> recents =
                    am.getRecentTasks(6, ActivityManager.RECENT_IGNORE_UNAVAILABLE);
            int target = -1;
            for (ActivityManager.RecentTaskInfo r : recents) {
                if (r.taskId != topId && r.taskId > 0) {
                    target = r.taskId;
                    break;
                }
            }
            if (target < 0) {
                log("switch: no previous task");
                return;
            }
            if (taskService == null) {
                taskService = Class.forName("android.app.ActivityTaskManager")
                        .getMethod("getService").invoke(null);
                for (Method m : taskService.getClass().getMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (m.getName().equals("startActivityFromRecents") && p.length == 2
                            && p[0] == int.class && p[1] == Bundle.class) {
                        startFromRecents = m;
                        break;
                    }
                }
            }
            startFromRecents.invoke(taskService, target, null);
            log("switch -> task " + target);
        } catch (Throwable t) {
            log("switch failed: " + Log.getStackTraceString(t));
        }
    }

    static void shAsync(String cmd) {
        Thread t = new Thread(new AsyncShellTask(cmd), "edgegest-sh");
        t.setDaemon(true);
        t.start();
    }

    /** Chạy lệnh shell, trả về stdout. */
    static String sh(String cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"/system/bin/sh", "-c", cmd});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            p.waitFor();
        } catch (Throwable t) {
            log("sh failed: " + t);
        }
        return sb.toString();
    }

    private static final class AsyncShellTask implements Runnable {
        private final String cmd;

        AsyncShellTask(String cmd) {
            this.cmd = cmd;
        }

        @Override
        public void run() {
            sh(cmd);
        }
    }
}
