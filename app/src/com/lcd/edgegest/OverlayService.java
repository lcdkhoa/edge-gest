package com.lcd.edgegest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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
 * Service nền giữ các dải overlay ở mép và cửa sổ hiệu ứng kéo.
 * Đọc và lưu cấu hình từ SharedPreferences.
 */
public final class OverlayService extends Service implements DisplayManager.DisplayListener {
    static final String TAG = "EdgeGest";
    static final String PREFS = "edgegest";
    static final String CH_ID = "edgegest";
    static volatile boolean running;

    // Độ dày mặc định (dp), vùng chừa mép, hiệu ứng & nhấn giữ
    static final int DEF_SIDE = 40;
    static final int DEF_BOTTOM = 24;
    static final int DEF_TOP_GUARD = 11;
    static final int DEF_BOTTOM_GAP = 0;
    static final int DEF_HOLD_MS = 320;
    static final boolean DEF_DEBUG = false;
    static final boolean DEF_ANIM = true;
    static final String DEF_LEFT_HOLD = GestureActions.NONE;
    static final String DEF_RIGHT_HOLD = GestureActions.NONE;

    private WindowManager wm;
    private DisplayManager dm;
    private Handler handler;
    private GestureClient client;
    private StripView left, right, bottom;
    private EdgeAnimView animLeft, animRight, animBottom;

    // Tham số đang áp dụng, để dựng lại khi xoay màn hình.
    private int curSide = DEF_SIDE, curBottom = DEF_BOTTOM;
    private int curTopGuard = DEF_TOP_GUARD, curBottomGap = DEF_BOTTOM_GAP;
    private int curHoldMs = DEF_HOLD_MS;
    private boolean curDebug = DEF_DEBUG, curAnim = DEF_ANIM;
    private String curLeftHold = DEF_LEFT_HOLD, curRightHold = DEF_RIGHT_HOLD;
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
        if (p.x == lastW && p.y == lastH) return;
        addStrips(curSide, curBottom, curDebug, curTopGuard, curBottomGap,
                curAnim, curLeftHold, curRightHold, curHoldMs);
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

    private void addStrips(int sideDp, int bottomDp, boolean debug, int topGuardPct, int bottomGapDp,
            boolean anim, String leftHold, String rightHold, int holdMs) {
        removeStrips();
        sideDp = clamp(sideDp, 8, 120);
        bottomDp = clamp(bottomDp, 8, 120);
        topGuardPct = clamp(topGuardPct, 0, 50);
        bottomGapDp = clamp(bottomGapDp, 0, 400);
        holdMs = clamp(holdMs, 180, 800);
        curSide = sideDp; curBottom = bottomDp; curDebug = debug;
        curTopGuard = topGuardPct; curBottomGap = bottomGapDp;
        curAnim = anim; curHoldMs = holdMs;
        curLeftHold = leftHold != null ? leftHold : DEF_LEFT_HOLD;
        curRightHold = rightHold != null ? rightHold : DEF_RIGHT_HOLD;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float density = metrics.density;

        int sidePx = Math.round(sideDp * density);
        int bottomPx = Math.round(bottomDp * density);
        int bottomGapPx = Math.round(bottomGapDp * density);

        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        int w = size.x, h = size.y;
        lastW = w; lastH = h;
        int top = Math.round(h * (topGuardPct / 100f));
        int sideH = Math.max(1, h - top - bottomPx - bottomGapPx);

        if (anim) {
            int animSideW = Math.round(68 * density);
            int animBotH = Math.round(92 * density);
            animLeft = new EdgeAnimView(this, StripView.LEFT, density);
            animRight = new EdgeAnimView(this, StripView.RIGHT, density);
            animBottom = new EdgeAnimView(this, StripView.BOTTOM, density);
            wm.addView(animLeft, lp(animSideW, sideH, Gravity.TOP | Gravity.LEFT, 0, top, "EdgeGestAnimLeft", true));
            wm.addView(animRight, lp(animSideW, sideH, Gravity.TOP | Gravity.RIGHT, 0, top, "EdgeGestAnimRight", true));
            wm.addView(animBottom, lp(w, animBotH, Gravity.BOTTOM | Gravity.LEFT, 0, 0, "EdgeGestAnimBottom", true));
        }

        left = new StripView(this, StripView.LEFT, client, animLeft, density,
                debug ? 0x55FF0000 : 0, curLeftHold, holdMs);
        right = new StripView(this, StripView.RIGHT, client, animRight, density,
                debug ? 0x5500FF00 : 0, curRightHold, holdMs);
        bottom = new StripView(this, StripView.BOTTOM, client, animBottom, density,
                debug ? 0x550000FF : 0, GestureActions.NONE, holdMs);

        wm.addView(left, lp(sidePx, sideH, Gravity.TOP | Gravity.LEFT, 0, top, "EdgeGestLeft", false));
        wm.addView(right, lp(sidePx, sideH, Gravity.TOP | Gravity.RIGHT, 0, top, "EdgeGestRight", false));
        wm.addView(bottom, lp(w, bottomPx, Gravity.BOTTOM | Gravity.LEFT, 0, 0, "EdgeGestBottom", false));
        Log.i(TAG, "strips added: side=" + sidePx + "px bottom=" + bottomPx + "px topGuard=" + topGuardPct
                + "% bottomGap=" + bottomGapPx + "px anim=" + anim + " debug=" + debug
                + " hold=" + curLeftHold + "/" + curRightHold + "@" + holdMs + "ms screen=" + w + "x" + h);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void removeStrips() {
        try { if (left != null) wm.removeView(left); } catch (Throwable ignored) {}
        try { if (right != null) wm.removeView(right); } catch (Throwable ignored) {}
        try { if (bottom != null) wm.removeView(bottom); } catch (Throwable ignored) {}
        try { if (animLeft != null) wm.removeView(animLeft); } catch (Throwable ignored) {}
        try { if (animRight != null) wm.removeView(animRight); } catch (Throwable ignored) {}
        try { if (animBottom != null) wm.removeView(animBottom); } catch (Throwable ignored) {}
        left = right = bottom = null;
        animLeft = animRight = animBottom = null;
    }

    private WindowManager.LayoutParams lp(int w, int h, int gravity, int x, int y,
            String title, boolean notTouchable) {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (notTouchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT);
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
        if (intent == null && started) return START_STICKY;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int sideDp = prefs.getInt("side", DEF_SIDE);
        int bottomDp = prefs.getInt("bottom", DEF_BOTTOM);
        int topGuardPct = prefs.getInt("topGuard", DEF_TOP_GUARD);
        int bottomGapDp = prefs.getInt("bottomGap", DEF_BOTTOM_GAP);
        int holdMs = prefs.getInt("holdMs", DEF_HOLD_MS);
        boolean debug = prefs.getBoolean("debug", DEF_DEBUG);
        boolean anim = prefs.getBoolean("anim", DEF_ANIM);
        String leftHold = prefs.getString("leftHold", DEF_LEFT_HOLD);
        String rightHold = prefs.getString("rightHold", DEF_RIGHT_HOLD);

        if (intent != null && (intent.hasExtra("side") || intent.hasExtra("bottom")
                || intent.hasExtra("debug") || intent.hasExtra("anim")
                || intent.hasExtra("topGuard") || intent.hasExtra("bottomGap")
                || intent.hasExtra("holdMs") || intent.hasExtra("leftHold")
                || intent.hasExtra("rightHold"))) {
            sideDp = intent.getIntExtra("side", sideDp);
            bottomDp = intent.getIntExtra("bottom", bottomDp);
            topGuardPct = intent.getIntExtra("topGuard", topGuardPct);
            bottomGapDp = intent.getIntExtra("bottomGap", bottomGapDp);
            holdMs = intent.getIntExtra("holdMs", holdMs);
            debug = intent.getBooleanExtra("debug", debug);
            anim = intent.getBooleanExtra("anim", anim);
            if (intent.hasExtra("leftHold")) leftHold = intent.getStringExtra("leftHold");
            if (intent.hasExtra("rightHold")) rightHold = intent.getStringExtra("rightHold");
            prefs.edit()
                    .putInt("side", sideDp)
                    .putInt("bottom", bottomDp)
                    .putInt("topGuard", topGuardPct)
                    .putInt("bottomGap", bottomGapDp)
                    .putInt("holdMs", holdMs)
                    .putBoolean("debug", debug)
                    .putBoolean("anim", anim)
                    .putString("leftHold", leftHold)
                    .putString("rightHold", rightHold)
                    .apply();
        }
        addStrips(sideDp, bottomDp, debug, topGuardPct, bottomGapDp, anim, leftHold, rightHold, holdMs);
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
