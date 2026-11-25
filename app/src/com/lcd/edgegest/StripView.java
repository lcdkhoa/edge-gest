package com.lcd.edgegest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Một dải cảm ứng ở mép. Vì là overlay nhận ACTION_DOWN, cú chạm bị NUỐT
 * (app bên dưới không nhận) — hết trùng chạm.
 *
 *  LEFT/RIGHT: vuốt vào trong          -> Back ('B')
 *  BOTTOM    : vuốt lên (nhanh)        -> Home ('H')
 *              vuốt lên rồi giữ        -> Recents ('R')  (quyết định theo thời lượng lúc nhấc tay)
 *              vuốt ngang              -> app trước ('S')
 */
final class StripView extends View {
    static final int LEFT = 0, RIGHT = 1, BOTTOM = 2;

    private static final int NONE = 0, BACK = 1, HOME = 2, SWITCH = 3;

    private final int edge;
    private final GestureClient client;
    private final float backDist, homeDist, switchDist;
    private final long holdMs = 300;

    private float x0, y0;
    private long startT;
    private int armed = NONE;

    StripView(Context ctx, int edge, GestureClient client, float density, int debugColor) {
        super(ctx);
        this.edge = edge;
        this.client = client;
        backDist = 40 * density;
        homeDist = 50 * density;
        switchDist = 75 * density;
        if (debugColor != 0) setBackgroundColor(debugColor);
    }

    private void buzz() {
        try {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } catch (Throwable ignored) {}
    }

    private void arm(int mode) {
        if (armed != mode && mode != NONE) buzz();
        armed = mode;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getRawX(), y = e.getRawY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                x0 = x; y0 = y; armed = NONE;
                startT = SystemClock.uptimeMillis();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (edge == BOTTOM) {
                    float up = y0 - y, dx = x - x0;
                    if (Math.abs(dx) > switchDist && Math.abs(dx) > up * 1.5f) arm(SWITCH);
                    else if (up > homeDist) arm(HOME);
                    else armed = NONE;
                } else {
                    float in = edge == LEFT ? x - x0 : x0 - x;
                    float dy = Math.abs(y - y0);
                    if (in > backDist && dy < in * 1.2f) arm(BACK);
                    else armed = NONE;
                }
                return true;

            case MotionEvent.ACTION_UP:
                fire();
                armed = NONE;
                return true;

            case MotionEvent.ACTION_CANCEL:
                armed = NONE;
                return true;
        }
        return true;
    }

    private void fire() {
        long dur = SystemClock.uptimeMillis() - startT;
        switch (armed) {
            case BACK:   client.send('B'); break;
            case SWITCH: client.send('S'); break;
            case HOME:
                // Vuốt lên rồi giữ (chạm lâu) -> Recents; vuốt lên dứt khoát -> Home.
                client.send(dur >= holdMs ? 'R' : 'H');
                break;
            default: break;
        }
    }
}
