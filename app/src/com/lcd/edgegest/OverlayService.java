package com.lcd.edgegest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * Service nền giữ các dải overlay ở mép. Đọc cấu hình độ dày từ SharedPreferences.
 */
public final class OverlayService extends Service implements DisplayManager.DisplayListener {
    static final String TAG = "EdgeGest";
    static final String PREFS = "edgegest";
    static final String CH_ID = "edgegest";
    static volatile boolean running;

    // Độ dày mặc định (dp) và vùng chừa mép
    static final int DEF_SIDE = 40;
    static final int DEF_BOTTOM = 24;
    static final int DEF_TOP_GUARD = 11;   // % chiều cao chừa phía trên (thanh thông báo)
    static final int DEF_BOTTOM_GAP = 0;   // dp chừa thêm trên dải đáy cho dải trái/phải
    static final boolean DEF_DEBUG = false;

    private WindowManager wm;
    private DisplayManager dm;
    private Handler handler;
    private GestureClient client;
    private StripView left, right, bottom;

    // Tham số đang áp dụng, để dựng lại khi xoay màn hình.
    private int curSide = DEF_SIDE, curBottom = DEF_BOTTOM;
    private int curTopGuard = DEF_TOP_GUARD, curBottomGap = DEF_BOTTOM_GAP;
    private boolean curDebug = DEF_DEBUG;
    private int lastW = -1, lastH = -1;
    private boolean started;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        client = new GestureClient();
        startForegroundNotice();
        dm.registerDisplayListener(this, handler);
    }

    // --- DisplayListener: dựng lại dải khi xoay/đổi kích thước màn hình ---
    @Override public void onDisplayAdded(int id) {}
    @Override public void onDisplayRemoved(int id) {}
    @Override
    public void onDisplayChanged(int id) {
        if (!started || id != Display.DEFAULT_DISPLAY) return;
        Point p = new Point();
        wm.getDefaultDisplay().getRealSize(p);
        if (p.x == lastW && p.y == lastH) return; // chỉ dựng lại khi kích thước thực sự đổi
        addStrips(curSide, curBottom, curDebug, curTopGuard, curBottomGap);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        onDisplayChanged(Display.DEFAULT_DISPLAY);
    }

    private void startForegroundNotice() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH_ID, "EdgeGest",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, CH_ID)
                .setContentTitle("EdgeGest đang chạy")
                .setContentText("Gesture điều hướng ở mép màn hình")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();
        try {
            startForeground(1, n);
        } catch (Throwable t) {
            Log.w(TAG, "startForeground lỗi: " + t);
        }
    }

    private void addStrips(int sideDp, int bottomDp, boolean debug, int topGuardPct, int bottomGapDp) {
        removeStrips();
        sideDp = clamp(sideDp, 8, 120);
        bottomDp = clamp(bottomDp, 8, 120);
        topGuardPct = clamp(topGuardPct, 0, 50);
        bottomGapDp = clamp(bottomGapDp, 0, 400);
        curSide = sideDp; curBottom = bottomDp; curDebug = debug;
        curTopGuard = topGuardPct; curBottomGap = bottomGapDp;
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float density = metrics.density;

        int sidePx = Math.round(sideDp * density);
        int bottomPx = Math.round(bottomDp * density);
        int bottomGapPx = Math.round(bottomGapDp * density);

        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        int w = size.x, h = size.y;
        lastW = w; lastH = h;
        int top = Math.round(h * (topGuardPct / 100f)); // chừa vùng kéo thanh thông báo

        left = new StripView(this, StripView.LEFT, client, density, debug ? 0x55FF0000 : 0);
        right = new StripView(this, StripView.RIGHT, client, density, debug ? 0x5500FF00 : 0);
        bottom = new StripView(this, StripView.BOTTOM, client, density, debug ? 0x550000FF : 0);

        int sideH = Math.max(1, h - top - bottomPx - bottomGapPx);
        wm.addView(left, lp(sidePx, sideH, Gravity.TOP | Gravity.LEFT, 0, top, "EdgeGestLeft"));
        wm.addView(right, lp(sidePx, sideH, Gravity.TOP | Gravity.RIGHT, 0, top, "EdgeGestRight"));
        wm.addView(bottom, lp(w, bottomPx, Gravity.BOTTOM | Gravity.LEFT, 0, 0, "EdgeGestBottom"));
        Log.i(TAG, "strips added: side=" + sidePx + "px bottom=" + bottomPx + "px topGuard=" + topGuardPct
                + "% bottomGap=" + bottomGapPx + "px debug=" + debug + " screen=" + w + "x" + h);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void removeStrips() {
        try { if (left != null) wm.removeView(left); } catch (Throwable ignored) {}
        try { if (right != null) wm.removeView(right); } catch (Throwable ignored) {}
        try { if (bottom != null) wm.removeView(bottom); } catch (Throwable ignored) {}
        left = right = bottom = null;
    }

    private WindowManager.LayoutParams lp(int w, int h, int gravity, int x, int y, String title) {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                w, h, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = gravity;
        p.x = x;
        p.y = y;
        p.setTitle(title);
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        return p;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY tự khởi động lại gọi onStartCommand với intent=null:
        // nếu dải đã dựng thì giữ nguyên, đừng đè bằng tham số mặc định.
        if (intent == null && started) return START_STICKY;

        // Ưu tiên tham số từ Intent (adb/am); nếu có thì lưu lại vào SharedPreferences để giữ sau reboot.
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int sideDp = prefs.getInt("side", DEF_SIDE);
        int bottomDp = prefs.getInt("bottom", DEF_BOTTOM);
        int topGuardPct = prefs.getInt("topGuard", DEF_TOP_GUARD);
        int bottomGapDp = prefs.getInt("bottomGap", DEF_BOTTOM_GAP);
        boolean debug = prefs.getBoolean("debug", DEF_DEBUG);
        if (intent != null && (intent.hasExtra("side") || intent.hasExtra("bottom")
                || intent.hasExtra("debug") || intent.hasExtra("topGuard")
                || intent.hasExtra("bottomGap"))) {
            sideDp = intent.getIntExtra("side", sideDp);
            bottomDp = intent.getIntExtra("bottom", bottomDp);
            topGuardPct = intent.getIntExtra("topGuard", topGuardPct);
            bottomGapDp = intent.getIntExtra("bottomGap", bottomGapDp);
            debug = intent.getBooleanExtra("debug", debug);
            prefs.edit()
                    .putInt("side", sideDp)
                    .putInt("bottom", bottomDp)
                    .putInt("topGuard", topGuardPct)
                    .putInt("bottomGap", bottomGapDp)
                    .putBoolean("debug", debug)
                    .apply();
        }
        addStrips(sideDp, bottomDp, debug, topGuardPct, bottomGapDp);
        started = true;
        running = true;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        started = false;
        try { if (dm != null) dm.unregisterDisplayListener(this); } catch (Throwable ignored) {}
        removeStrips();
        if (client != null) client.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
