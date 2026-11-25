package com.lcd.edgegest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Tự khởi động OverlayService và gọi Shizuku chạy helper server khi máy khởi động xong
 * (chờ sau khi đã mở khoá để đọc được CE SharedPreferences).
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        UserManager um = ctx.getSystemService(UserManager.class);
        if (um != null && !um.isUserUnlocked()) {
            Log.i(OverlayService.TAG, "boot: user chưa mở khoá, chờ BOOT_COMPLETED");
            return;
        }
        if (Settings.canDrawOverlays(ctx)) {
            Intent svc = new Intent(ctx, OverlayService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
            else ctx.startService(svc);
        }

        PendingResult pending = goAsync();
        new Thread(new BootHelperTask(ctx.getApplicationContext(), pending), "boot-shizuku").start();
    }

    private static final class BootHelperTask implements Runnable {
        private final Context appCtx;
        private final PendingResult pending;

        BootHelperTask(Context appCtx, PendingResult pending) {
            this.appCtx = appCtx;
            this.pending = pending;
        }

        @Override
        public void run() {
            try {
                if (ShizukuStarter.isHelperAlive()) return;
                // Thử tối đa 3 lần (cách nhau 3s) phòng Shizuku khởi tạo chậm sau boot.
                for (int i = 0; i < 3; i++) {
                    if (ShizukuStarter.startHelper(appCtx)) {
                        Log.i(OverlayService.TAG, "boot: đã bật helper qua Shizuku");
                        return;
                    }
                    if (i < 2) Thread.sleep(3000);
                }
                Log.i(OverlayService.TAG, "boot: Shizuku chưa sẵn sàng để bật helper");
            } catch (Throwable t) {
                Log.w(OverlayService.TAG, "boot Shizuku lỗi: " + t);
            } finally {
                pending.finish();
            }
        }
    }
}
