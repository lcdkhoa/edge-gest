package com.lcd.edgegest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Cửa sổ hiệu ứng (FLAG_NOT_TOUCHABLE) vẽ dải cong + biểu tượng khi người dùng kéo từ mép.
 * Mặc định ở trạng thái GONE khi không chạm để tránh tốn tài nguyên.
 */
final class EdgeAnimView extends View {
    private final int edge;
    private final float density;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final HideTask hideTask;

    private float progress; // 0..1
    private float anchorY;  // toạ độ Y theo view
    private float anchorX;  // toạ độ X theo view (cho cạnh dưới)
    private boolean armed;
    private boolean holdActive;
    private String badge = "";

    EdgeAnimView(Context ctx, int edge, float density) {
        super(ctx);
        this.edge = edge;
        this.density = density;
        this.hideTask = new HideTask(this);

        fillPaint.setStyle(Paint.Style.FILL);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setStrokeWidth(2.4f * density);
        iconPaint.setColor(0xFFFFFFFF);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(14f * density);
        setVisibility(GONE);
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

        int w = getWidth(), h = getHeight();
        int color;
        if (holdActive) color = 0xE6D97706;       // cam hổ phách khi kích hoạt nhấn giữ
        else if (armed) color = 0xE62563EB;       // xanh dương khi sẵn sàng Back / Home / Switch
        else color = 0xB81E242B;                  // xám đậm mờ khi mới kéo
        fillPaint.setColor(color);

        if (edge == StripView.BOTTOM) {
            drawBottomPill(canvas, w, h);
        } else {
            drawSideCurve(canvas, w, h);
        }
    }

    private void drawSideCurve(Canvas canvas, int w, int h) {
        float maxW = Math.min(w * 0.92f, 44f * density);
        float bulge = maxW * (0.28f + 0.72f * progress);
        float halfH = 76f * density;
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
            if (holdActive && !badge.isEmpty()) {
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                canvas.drawText(badge, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
            } else {
                float r = 5.5f * density;
                path.reset();
                if (edge == StripView.LEFT) {
                    path.moveTo(cx + r * 0.5f, cy - r);
                    path.lineTo(cx - r * 0.5f, cy);
                    path.lineTo(cx + r * 0.5f, cy + r);
                } else {
                    path.moveTo(cx - r * 0.5f, cy - r);
                    path.lineTo(cx + r * 0.5f, cy);
                    path.lineTo(cx - r * 0.5f, cy + r);
                }
                iconPaint.setAlpha(armed ? 255 : 190);
                canvas.drawPath(path, iconPaint);
            }
        }
    }

    private void drawBottomPill(Canvas canvas, int w, int h) {
        float pillW = (92f + 28f * progress) * density;
        float pillH = (10f + 16f * progress) * density;
        float cx = Math.max(pillW * 0.6f, Math.min(w - pillW * 0.6f, anchorX));
        float cy = h - (10f + 18f * progress) * density;
        float r = pillH / 2f;
        canvas.drawRoundRect(cx - pillW / 2f, cy - r, cx + pillW / 2f, cy + r, r, r, fillPaint);

        if (armed && !badge.isEmpty()) {
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            canvas.drawText(badge, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
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
