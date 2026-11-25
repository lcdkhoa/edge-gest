package com.lcd.edgegest;

import android.util.Log;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Gửi lệnh cử chỉ (1 ký tự) tới helper shell qua TCP localhost 127.0.0.1:47820.
 * Một luồng nền giữ kết nối, tự kết nối lại khi rớt.
 */
final class GestureClient implements Runnable {
    static final String TAG = "EdgeGest";
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 47820;

    private final LinkedBlockingQueue<Character> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private Socket socket;
    private OutputStream out;

    GestureClient() {
        Thread t = new Thread(this, "edgegest-client");
        t.setDaemon(true);
        t.start();
    }

    void send(char c) {
        queue.offer(c);
    }

    void stop() {
        running = false;
        queue.offer('\0');
        closeQuietly();
    }

    @Override
    public void run() {
        while (running) {
            try {
                Character c = queue.take();
                if (!running) break;
                ensureConnected();
                out.write(c);
                out.write('\n');
                out.flush();
            } catch (Throwable t) {
                Log.w(TAG, "gửi lệnh lỗi, sẽ kết nối lại: " + t);
                closeQuietly();
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void ensureConnected() throws Exception {
        if (socket != null && socket.isConnected() && !socket.isClosed()) return;
        closeQuietly();
        Socket s = new Socket();
        s.connect(new InetSocketAddress(HOST, PORT), 1000);
        s.setTcpNoDelay(true);
        socket = s;
        out = s.getOutputStream();
        Log.i(TAG, "đã kết nối helper injector");
    }

    private void closeQuietly() {
        try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
        socket = null;
        out = null;
    }
}
