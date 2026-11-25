package com.lcd.edgegest;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Giao tiếp trực tiếp với Shizuku Server (v13+) thông qua Binder được Shizuku đẩy vào ShizukuProvider
 * (không gọi ShizukuManagerProvider vì provider của manager khoá bởi INTERACT_ACROSS_USERS_FULL).
 *
 * Transaction codes xác minh từ Shizuku 13.6.0 (moe.shizuku.server.IShizukuService):
 *  - 8 : newProcess(String[] cmd, String[] env, String dir)
 *  - 15: requestPermission(int requestCode)
 *  - 16: checkSelfPermission() -> boolean
 *  - 18: attachApplication(IShizukuApplication, Bundle)
 */
final class ShizukuStarter {
    static final int STATE_NOT_RUNNING = 0;
    static final int STATE_NO_PERMISSION = 1;
    static final int STATE_READY = 2;

    static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";
    private static final String ACTION_REQUEST_BINDER = "rikka.shizuku.intent.action.REQUEST_BINDER";
    private static final String DESCRIPTOR = "moe.shizuku.server.IShizukuService";
    private static final String APP_DESCRIPTOR = "moe.shizuku.server.IShizukuApplication";
    private static final int BRIDGE_TX_CODE = ('_' << 24) | ('S' << 16) | ('U' << 8) | 'I';
    private static final int BRIDGE_ACTION_GET_BINDER = 2;

    private static final int TX_NEW_PROCESS = 8;
    private static final int TX_REQUEST_PERMISSION = 15;
    private static final int TX_CHECK_SELF_PERMISSION = 16;
    private static final int TX_ATTACH_APPLICATION = 18;

    private static final String START_SCRIPT = "/data/local/tmp/edgegest/start-server.sh";
    private static final IBinder APP_TOKEN = new AppToken();

    private static volatile IBinder remoteBinder;
    private static volatile boolean attached;
    private static volatile Runnable binderListener;

    private ShizukuStarter() {}

    static void setOnBinderListener(Runnable listener) {
        binderListener = listener;
    }

    static synchronized void onBinderReceived(IBinder binder) {
        if (binder == null) return;
        remoteBinder = binder;
        attached = false;
        try {
            binder.linkToDeath(new BinderDeath(binder), 0);
        } catch (Throwable ignored) {}
        Runnable l = binderListener;
        if (l != null) l.run();
    }

    static void tryAcquireSuiBinder() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);
            IBinder am = (IBinder) getService.invoke(null, "activity");
            if (am == null) return;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.app.IActivityManager");
                data.writeInt(BRIDGE_ACTION_GET_BINDER);
                am.transact(BRIDGE_TX_CODE, data, reply, 0);
                reply.readException();
                IBinder b = reply.readStrongBinder();
                if (b != null && b.pingBinder()) onBinderReceived(b);
            } finally {
                reply.recycle();
                data.recycle();
            }
        } catch (Throwable ignored) {
            // Không phải Sui; chờ shizuku_server gọi sendBinder vào ShizukuProvider.
        }
    }

    static IBinder acquireBinder(Context ctx, long waitMs) {
        IBinder b = remoteBinder;
        if (b != null && b.pingBinder()) return b;
        tryAcquireSuiBinder();
        b = remoteBinder;
        if (b != null && b.pingBinder()) return b;

        // Nhắc Shizuku Manager gửi lại binder vào com.lcd.edgegest.shizuku nếu server đang chạy.
        try {
            Intent req = new Intent(ACTION_REQUEST_BINDER);
            req.setPackage(SHIZUKU_PKG);
            ctx.sendBroadcast(req);
        } catch (Throwable ignored) {}

        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            b = remoteBinder;
            if (b != null && b.pingBinder()) return b;
            try { Thread.sleep(100); } catch (InterruptedException ignored) { break; }
        }
        b = remoteBinder;
        return (b != null && b.pingBinder()) ? b : null;
    }

    private static synchronized boolean ensureAttached(IBinder service, String pkg) {
        if (attached && service == remoteBinder) return true;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            Bundle args = new Bundle();
            args.putInt("shizuku:attach-api-version", 13);
            args.putString("shizuku:attach-package-name", pkg);
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeStrongBinder(APP_TOKEN);
            data.writeInt(1);
            args.writeToParcel(data, 0);
            service.transact(TX_ATTACH_APPLICATION, data, reply, 0);
            reply.readException();
            attached = true;
            return true;
        } catch (Throwable t) {
            Log.w(OverlayService.TAG, "Shizuku attachApplication lỗi: " + t);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static boolean checkSelfPermission(IBinder service) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            service.transact(TX_CHECK_SELF_PERMISSION, data, reply, 0);
            reply.readException();
            return reply.readInt() != 0;
        } catch (Throwable t) {
            Log.w(OverlayService.TAG, "Shizuku checkSelfPermission lỗi: " + t);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    static int getState(Context ctx) {
        IBinder s = acquireBinder(ctx, 800);
        if (s == null) return STATE_NOT_RUNNING;
        if (!ensureAttached(s, ctx.getPackageName())) return STATE_NOT_RUNNING;
        return checkSelfPermission(s) ? STATE_READY : STATE_NO_PERMISSION;
    }

    static boolean requestPermission(Context ctx, int requestCode) {
        IBinder s = acquireBinder(ctx, 800);
        if (s == null || !ensureAttached(s, ctx.getPackageName())) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(requestCode);
            s.transact(TX_REQUEST_PERMISSION, data, reply, 0);
            reply.readException();
            return true;
        } catch (Throwable t) {
            Log.w(OverlayService.TAG, "Shizuku requestPermission lỗi: " + t);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    static boolean startHelper(Context ctx) {
        IBinder s = acquireBinder(ctx, 1500);
        if (s == null || !ensureAttached(s, ctx.getPackageName())) return false;
        if (!checkSelfPermission(s)) return false;

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        IBinder remoteProc = null;
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeStringArray(new String[] {"/system/bin/sh", START_SCRIPT});
            data.writeStringArray(null);
            data.writeString(null);
            s.transact(TX_NEW_PROCESS, data, reply, 0);
            reply.readException();
            remoteProc = reply.readStrongBinder();
        } catch (Throwable t) {
            Log.w(OverlayService.TAG, "Shizuku newProcess lỗi: " + t);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
        if (remoteProc == null) return false;

        for (int i = 0; i < 15; i++) {
            if (isHelperAlive()) return true;
            try { Thread.sleep(200); } catch (InterruptedException ignored) { break; }
        }
        return isHelperAlive();
    }

    static boolean isHelperAlive() {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress("127.0.0.1", 47820), 250);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class AppToken extends Binder {
        AppToken() {
            attachInterface(null, APP_DESCRIPTOR);
        }
    }

    private static final class BinderDeath implements IBinder.DeathRecipient {
        private final IBinder dead;

        BinderDeath(IBinder dead) {
            this.dead = dead;
        }

        @Override
        public void binderDied() {
            if (remoteBinder == dead) {
                remoteBinder = null;
                attached = false;
            }
        }
    }
}
