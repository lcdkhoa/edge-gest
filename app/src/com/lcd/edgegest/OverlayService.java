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

    // Mặc định độ dày (dp), vùng chừa mép, nhấn giữ & hiệu ứng animation
    static final int DEF_SIDE = 40;
    static final int DEF_BOTTOM = 24;
    static final int DEF_TOP_GUARD = 11;
    static final int DEF_BOTTOM_GAP = 0;
    static final int DEF_HOLD_MS = 320;
    static final boolean DEF_DEBUG = false;
    static final boolean DEF_ANIM = true;
    static final boolean DEF_ANIM_LEFT = true;
    static final boolean DEF_ANIM_RIGHT = true;
    static final boolean DEF_ANIM_BOTTOM = true;
    static final int DEF_ANIM_COLOR = 0xFF2563EB;
    static final int DEF_ANIM_WIDTH = 44;
    static final int DEF_ANIM_LENGTH = 150;
    static final int DEF_ANIM_ALPHA = 85;
    static final String DEF_LEFT_HOLD = GestureActions.NONE;
    static final String DEF_RIGHT_HOLD = GestureActions.NONE;

    private WindowManager wm;
    private DisplayManager dm;
    private Handler handler;
    private GestureClient client;
    private StripView left, right, bottom;
    private EdgeAnimView animLeftView, animRightView, animBottomView;

    // Tham số đang áp dụng, để dựng lại khi xoay màn hình.
    private int curSide = DEF_SIDE, curBottom = DEF_BOTTOM;
    private int curTopGuard = DEF_TOP_GUARD, curBottomGap = DEF_BOTTOM_GAP;
    private int curHoldMs = DEF_HOLD_MS;
    private boolean curDebug = DEF_DEBUG, curAnim = DEF_ANIM;
    private boolean curAnimLeft = DEF_ANIM_LEFT, curAnimRight = DEF_ANIM_RIGHT, curAnimBottom = DEF_ANIM_BOTTOM;
    private int curAnimColor = DEF_ANIM_COLOR, curAnimWidth = DEF_ANIM_WIDTH;
    private int curAnimLength = DEF_ANIM_LENGTH, curAnimAlpha = DEF_ANIM_ALPHA;
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

    @Override public void onDisplayAdded(int id) {}
    @Override public void onDisplayRemoved(int id) {}
    @Override
    public void onDisplayChanged(int id) {
        if (!started || id != Display.DEFAULT_DISPLAY) return;
        Point p = new Point();
        wm.getDefaultDisplay().getRealSize(p);
        if (p.x == lastW && p.y == lastH) return;
        rebuildFromCurrent();
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
        boolean vi = Strings.isVi(this);
        Notification n = new Notification.Builder(this, CH_ID)
                .setContentTitle(vi ? "EdgeGest đang chạy" : "EdgeGest running")
                .setContentText(vi ? "Gesture điều hướng ở mép màn hình" : "Screen edge navigation active")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();
        try {
            startForeground(1, n);
        } catch (Throwable t) {
            Log.w(TAG, "startForeground lỗi: " + t);
        }
    }

    private void rebuildFromCurrent() {
        removeStrips();
        curSide = clamp(curSide, 8, 120);
        curBottom = clamp(curBottom, 8, 120);
        curTopGuard = clamp(curTopGuard, 0, 50);
        curBottomGap = clamp(curBottomGap, 0, 400);
        curHoldMs = clamp(curHoldMs, 180, 800);
        curAnimWidth = clamp(curAnimWidth, 24, 80);
        curAnimLength = clamp(curAnimLength, 80, 220);
        curAnimAlpha = clamp(curAnimAlpha, 15, 100);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float density = metrics.density;

        int sidePx = Math.round(curSide * density);
        int bottomPx = Math.round(curBottom * density);
        int bottomGapPx = Math.round(curBottomGap * density);

        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        int w = size.x, h = size.y;
        lastW = w; lastH = h;
        int top = Math.round(h * (curTopGuard / 100f));
        int sideH = Math.max(1, h - top - bottomPx - bottomGapPx);

        int animSideW = Math.round((curAnimWidth + 24) * density);
        int animBotH = Math.round((curAnimWidth + 56) * density);

        if (curAnim && curAnimLeft) {
            animLeftView = new EdgeAnimView(this, StripView.LEFT, density,
                    curAnimColor, curAnimWidth, curAnimLength, curAnimAlpha);
            wm.addView(animLeftView, lp(animSideW, sideH, Gravity.TOP | Gravity.LEFT, 0, top, "EdgeGestAnimLeft", true));
        }
        if (curAnim && curAnimRight) {
            animRightView = new EdgeAnimView(this, StripView.RIGHT, density,
                    curAnimColor, curAnimWidth, curAnimLength, curAnimAlpha);
            wm.addView(animRightView, lp(animSideW, sideH, Gravity.TOP | Gravity.RIGHT, 0, top, "EdgeGestAnimRight", true));
        }
        if (curAnim && curAnimBottom) {
            animBottomView = new EdgeAnimView(this, StripView.BOTTOM, density,
                    curAnimColor, curAnimWidth, curAnimLength, curAnimAlpha);
            wm.addView(animBottomView, lp(w, animBotH, Gravity.BOTTOM | Gravity.LEFT, 0, 0, "EdgeGestAnimBottom", true));
        }

        left = new StripView(this, StripView.LEFT, client, animLeftView, density,
                curDebug ? 0x55FF0000 : 0, curLeftHold, curHoldMs);
        right = new StripView(this, StripView.RIGHT, client, animRightView, density,
                curDebug ? 0x5500FF00 : 0, curRightHold, curHoldMs);
        bottom = new StripView(this, StripView.BOTTOM, client, animBottomView, density,
                curDebug ? 0x550000FF : 0, GestureActions.NONE, curHoldMs);

        wm.addView(left, lp(sidePx, sideH, Gravity.TOP | Gravity.LEFT, 0, top, "EdgeGestLeft", false));
        wm.addView(right, lp(sidePx, sideH, Gravity.TOP | Gravity.RIGHT, 0, top, "EdgeGestRight", false));
        wm.addView(bottom, lp(w, bottomPx, Gravity.BOTTOM | Gravity.LEFT, 0, 0, "EdgeGestBottom", false));
        Log.i(TAG, "strips added: side=" + sidePx + "px bottom=" + bottomPx + "px topGuard=" + curTopGuard
                + "% bottomGap=" + bottomGapPx + "px anim=" + curAnim
                + "(" + curAnimLeft + "," + curAnimRight + "," + curAnimBottom + ")"
                + " debug=" + curDebug + " hold=" + curLeftHold + "/" + curRightHold + "@" + curHoldMs + "ms");
    }

    static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void removeStrips() {
        try { if (left != null) wm.removeView(left); } catch (Throwable ignored) {}
        try { if (right != null) wm.removeView(right); } catch (Throwable ignored) {}
        try { if (bottom != null) wm.removeView(bottom); } catch (Throwable ignored) {}
        try { if (animLeftView != null) wm.removeView(animLeftView); } catch (Throwable ignored) {}
        try { if (animRightView != null) wm.removeView(animRightView); } catch (Throwable ignored) {}
        try { if (animBottomView != null) wm.removeView(animBottomView); } catch (Throwable ignored) {}
        left = right = bottom = null;
        animLeftView = animRightView = animBottomView = null;
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
        curSide = prefs.getInt("side", DEF_SIDE);
        curBottom = prefs.getInt("bottom", DEF_BOTTOM);
        curTopGuard = prefs.getInt("topGuard", DEF_TOP_GUARD);
        curBottomGap = prefs.getInt("bottomGap", DEF_BOTTOM_GAP);
        curHoldMs = prefs.getInt("holdMs", DEF_HOLD_MS);
        curDebug = prefs.getBoolean("debug", DEF_DEBUG);
        curAnim = prefs.getBoolean("anim", DEF_ANIM);
        curAnimLeft = prefs.getBoolean("animLeft", DEF_ANIM_LEFT);
        curAnimRight = prefs.getBoolean("animRight", DEF_ANIM_RIGHT);
        curAnimBottom = prefs.getBoolean("animBottom", DEF_ANIM_BOTTOM);
        curAnimColor = prefs.getInt("animColor", DEF_ANIM_COLOR);
        curAnimWidth = prefs.getInt("animWidth", DEF_ANIM_WIDTH);
        curAnimLength = prefs.getInt("animLength", DEF_ANIM_LENGTH);
        curAnimAlpha = prefs.getInt("animAlpha", DEF_ANIM_ALPHA);
        curLeftHold = prefs.getString("leftHold", DEF_LEFT_HOLD);
        curRightHold = prefs.getString("rightHold", DEF_RIGHT_HOLD);

        if (intent != null && (intent.hasExtra("side") || intent.hasExtra("bottom")
                || intent.hasExtra("debug") || intent.hasExtra("anim")
                || intent.hasExtra("topGuard") || intent.hasExtra("bottomGap")
                || intent.hasExtra("holdMs") || intent.hasExtra("leftHold")
                || intent.hasExtra("rightHold"))) {
            curSide = intent.getIntExtra("side", curSide);
            curBottom = intent.getIntExtra("bottom", curBottom);
            curTopGuard = intent.getIntExtra("topGuard", curTopGuard);
            curBottomGap = intent.getIntExtra("bottomGap", curBottomGap);
            curHoldMs = intent.getIntExtra("holdMs", curHoldMs);
            curDebug = intent.getBooleanExtra("debug", curDebug);
            curAnim = intent.getBooleanExtra("anim", curAnim);
            if (intent.hasExtra("leftHold")) curLeftHold = intent.getStringExtra("leftHold");
            if (intent.hasExtra("rightHold")) curRightHold = intent.getStringExtra("rightHold");
            prefs.edit()
                    .putInt("side", curSide)
                    .putInt("bottom", curBottom)
                    .putInt("topGuard", curTopGuard)
                    .putInt("bottomGap", curBottomGap)
                    .putInt("holdMs", curHoldMs)
                    .putBoolean("debug", curDebug)
                    .putBoolean("anim", curAnim)
                    .putString("leftHold", curLeftHold)
                    .putString("rightHold", curRightHold)
                    .apply();
        }
        rebuildFromCurrent();
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
