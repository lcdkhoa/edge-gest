package com.lcd.edgegest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Cửa sổ hiệu ứng (FLAG_NOT_TOUCHABLE) vẽ dải cong + biểu tượng khi người dùng kéo từ mép.
 * Hỗ trợ tuỳ chỉnh màu sắc, độ vươn rộng, độ dài dải và độ trong suốt (%).
 * Cũng được dùng chung hàm vẽ cho phần Preview trong AnimConfigActivity.
 */
final class EdgeAnimView extends View {
    private final int edge;
    private final float density;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final HideTask hideTask;

    private int baseColor;
    private int widthDp;
    private int lengthDp;
    private int alphaPct;

    private float progress;
    private float anchorY;
    private float anchorX;
    private boolean armed;
    private boolean holdActive;
    private String badge = "";

    EdgeAnimView(Context ctx, int edge, float density,
            int baseColor, int widthDp, int lengthDp, int alphaPct) {
        super(ctx);
        this.edge = edge;
        this.density = density;
        this.baseColor = baseColor;
        this.widthDp = widthDp;
        this.lengthDp = lengthDp;
        this.alphaPct = alphaPct;
        this.hideTask = new HideTask(this);

        fillPaint.setStyle(Paint.Style.FILL);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setStrokeWidth(2.4f * density);
        iconPaint.setColor(0xFFFFFFFF);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(12f * density);
        setVisibility(GONE);
    }

    void configure(int baseColor, int widthDp, int lengthDp, int alphaPct) {
        this.baseColor = baseColor;
        this.widthDp = widthDp;
        this.lengthDp = lengthDp;
        this.alphaPct = alphaPct;
        invalidate();
    }

    void update(float progress, float localX, float localY, boolean armed, boolean holdActive, String badge) {
        removeCallbacks(hideTask);
        this.progress = Math.max(0f, Math.min(1f, progress));
        this.anchorX = localX;
        this.anchorY = localY;
        this.armed = armed;
        this.holdActive = holdActive;
        this.badge = badge == null ? "" : badge;
        if (getVisibility() != VISIBLE) {
            setAlpha(1f);
            setVisibility(VISIBLE);
        }
        invalidate();
    }

    void release() {
        removeCallbacks(hideTask);
        if (getVisibility() != VISIBLE) return;
        animate().alpha(0f).setDuration(140).withEndAction(hideTask).start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress <= 0.02f) return;
        drawIndicator(canvas, getWidth(), getHeight(), edge, density,
                baseColor, widthDp, lengthDp, alphaPct,
                progress, anchorX, anchorY, armed, holdActive, badge,
                fillPaint, iconPaint, textPaint, path);
    }

    static void drawIndicator(Canvas canvas, int w, int h, int edge, float density,
            int baseColor, int widthDp, int lengthDp, int alphaPct,
            float progress, float anchorX, float anchorY,
            boolean armed, boolean holdActive, String badge,
            Paint fillPaint, Paint iconPaint, Paint textPaint, Path path) {
        int rgb = holdActive ? 0xD97706 : (armed ? (baseColor & 0x00FFFFFF) : 0x1E242B);
        float maxA = Math.max(0.15f, Math.min(1f, alphaPct / 100f));
        float aFrac = armed ? maxA : maxA * 0.78f;
        int a = Math.max(20, Math.min(255, Math.round(255f * aFrac)));
        fillPaint.setColor(Color.argb(a, (rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff));

        if (edge == StripView.BOTTOM) {
            float pillW = (lengthDp * 0.78f + 22f * progress) * density;
            pillW = Math.min(w * 0.85f, pillW);
            float pillH = (Math.max(12f, widthDp * 0.48f) + 6f * progress) * density;
            float cx = Math.max(pillW * 0.55f, Math.min(w - pillW * 0.55f, anchorX));
            float cy = h - (10f + 16f * progress) * density;
            float r = pillH / 2f;
            canvas.drawRoundRect(cx - pillW / 2f, cy - r, cx + pillW / 2f, cy + r, r, r, fillPaint);

            if (armed && badge != null && !badge.isEmpty()) {
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                canvas.drawText(badge, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
            }
            return;
        }

        float maxW = Math.min(w * 0.94f, widthDp * density);
        float bulge = maxW * (0.28f + 0.72f * progress);
        float halfH = (lengthDp * density) / 2f;
        halfH = Math.min(h * 0.48f, halfH);
        float cy = Math.max(halfH, Math.min(h - halfH, anchorY));

        path.reset();
        if (edge == StripView.LEFT) {
            path.moveTo(0, cy - halfH);
            path.cubicTo(0, cy - halfH * 0.48f, bulge, cy - halfH * 0.36f, bulge, cy);
            path.cubicTo(bulge, cy + halfH * 0.36f, 0, cy + halfH * 0.48f, 0, cy + halfH);
            path.close();
        } else {
            float rx = w;
            path.moveTo(rx, cy - halfH);
            path.cubicTo(rx, cy - halfH * 0.48f, rx - bulge, cy - halfH * 0.36f, rx - bulge, cy);
            path.cubicTo(rx - bulge, cy + halfH * 0.36f, rx, cy + halfH * 0.48f, rx, cy + halfH);
            path.close();
        }
        canvas.drawPath(path, fillPaint);

        if (progress > 0.2f) {
            float cx = edge == StripView.LEFT ? bulge * 0.52f : w - bulge * 0.52f;
            if (holdActive && badge != null && !badge.isEmpty()) {
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                canvas.drawText(badge, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
            } else {
                float r = Math.min(6.5f * density, bulge * 0.22f);
                path.reset();
                if (edge == StripView.LEFT) {
                    path.moveTo(cx + r * 0.45f, cy - r);
                    path.lineTo(cx - r * 0.45f, cy);
                    path.lineTo(cx + r * 0.45f, cy + r);
                } else {
                    path.moveTo(cx - r * 0.45f, cy - r);
                    path.lineTo(cx + r * 0.45f, cy);
                    path.lineTo(cx - r * 0.45f, cy + r);
                }
                iconPaint.setAlpha(armed ? 255 : 195);
                canvas.drawPath(path, iconPaint);
            }
        }
    }

    private static final class HideTask implements Runnable {
        private final EdgeAnimView view;

        HideTask(EdgeAnimView view) {
            this.view = view;
        }

        @Override
        public void run() {
            view.setVisibility(GONE);
            view.setAlpha(1f);
            view.progress = 0f;
        }
    }
}
