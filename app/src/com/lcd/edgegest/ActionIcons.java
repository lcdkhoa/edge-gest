package com.lcd.edgegest;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Bộ vẽ icon vector cho từng hành động nhấn giữ & render icon thật của từng app (PackageManager).
 * Dùng chung cho EdgeAnimView (dải cong overlay), AnimConfigActivity Preview, và các row/picker trong MainActivity.
 */
final class ActionIcons {
    private static final RectF TMP_RECT = new RectF();
    private static final Path TMP_TRI = new Path();

    private ActionIcons() {}

    static Drawable loadAppIcon(Context ctx, String pkg) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationIcon(pkg);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Bitmap buildCircularAppBitmap(Drawable d, int px) {
        if (d == null || px <= 0) return null;
        try {
            Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float r = px / 2f;

            // Vòng trắng làm nền nổi trên dải màu
            p.setColor(0xFFFFFFFF);
            c.drawCircle(r, r, r, p);

            Bitmap raw = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas rc = new Canvas(raw);
            int pad = Math.max(2, Math.round(px * 0.08f));
            d.setBounds(pad, pad, px - pad, px - pad);
            d.draw(rc);

            Bitmap clipped = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas cc = new Canvas(clipped);
            Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
            cc.drawCircle(r, r, Math.max(1f, r - Math.max(1.5f, px * 0.06f)), cp);
            cp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            cc.drawBitmap(raw, 0, 0, cp);
            c.drawBitmap(clipped, 0, 0, null);
            raw.recycle();
            clipped.recycle();
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void drawVector(Canvas canvas, String action, float cx, float cy, float size,
            Paint stroke, Paint fill, Path path) {
        float r = size * 0.48f;
        if (action == null || GestureActions.NONE.equals(action) || "_back_left".equals(action)) {
            path.reset();
            path.moveTo(cx + r * 0.35f, cy - r * 0.85f);
            path.lineTo(cx - r * 0.45f, cy);
            path.lineTo(cx + r * 0.35f, cy + r * 0.85f);
            canvas.drawPath(path, stroke);
            return;
        }
        if ("_back_right".equals(action)) {
            path.reset();
            path.moveTo(cx - r * 0.35f, cy - r * 0.85f);
            path.lineTo(cx + r * 0.45f, cy);
            path.lineTo(cx - r * 0.35f, cy + r * 0.85f);
            canvas.drawPath(path, stroke);
            return;
        }
        if (GestureActions.POWER.equals(action)) {
            TMP_RECT.set(cx - r * 0.82f, cy - r * 0.72f, cx + r * 0.82f, cy + r * 0.92f);
            canvas.drawArc(TMP_RECT, -50f, 280f, false, stroke);
            canvas.drawLine(cx, cy - r * 1.0f, cx, cy - r * 0.05f, stroke);
            return;
        }
        if (GestureActions.HOME.equals(action)) {
            path.reset();
            path.moveTo(cx - r * 0.95f, cy - r * 0.05f);
            path.lineTo(cx, cy - r * 0.92f);
            path.lineTo(cx + r * 0.95f, cy - r * 0.05f);
            path.moveTo(cx - r * 0.65f, cy + r * 0.08f);
            path.lineTo(cx - r * 0.65f, cy + r * 0.88f);
            path.lineTo(cx + r * 0.65f, cy + r * 0.88f);
            path.lineTo(cx + r * 0.65f, cy + r * 0.08f);
            canvas.drawPath(path, stroke);
            return;
        }
        if (GestureActions.SWITCH.equals(action)) {
            float hw = r * 0.85f;
            float ay = r * 0.42f;
            float ah = r * 0.32f;
            path.reset();
            path.moveTo(cx - hw, cy - ay);
            path.lineTo(cx + hw, cy - ay);
            path.moveTo(cx + hw - ah, cy - ay - ah);
            path.lineTo(cx + hw, cy - ay);
            path.lineTo(cx + hw - ah, cy - ay + ah);
            path.moveTo(cx + hw, cy + ay);
            path.lineTo(cx - hw, cy + ay);
            path.moveTo(cx - hw + ah, cy + ay - ah);
            path.lineTo(cx - hw, cy + ay);
            path.lineTo(cx - hw + ah, cy + ay + ah);
            canvas.drawPath(path, stroke);
            return;
        }
        if (GestureActions.RECENTS.equals(action)) {
            TMP_RECT.set(cx - r * 0.45f, cy - r * 0.55f, cx + r * 0.85f, cy + r * 0.85f);
            canvas.drawRoundRect(TMP_RECT, r * 0.22f, r * 0.22f, stroke);
            path.reset();
            path.moveTo(cx - r * 0.82f, cy + r * 0.25f);
            path.lineTo(cx - r * 0.82f, cy - r * 0.88f);
            path.lineTo(cx + r * 0.32f, cy - r * 0.88f);
            canvas.drawPath(path, stroke);
            return;
        }
        if (GestureActions.VOLUME.equals(action)) {
            TMP_TRI.reset();
            TMP_TRI.moveTo(cx - r * 0.9f, cy - r * 0.3f);
            TMP_TRI.lineTo(cx - r * 0.48f, cy - r * 0.3f);
            TMP_TRI.lineTo(cx - r * 0.02f, cy - r * 0.78f);
            TMP_TRI.lineTo(cx - r * 0.02f, cy + r * 0.78f);
            TMP_TRI.lineTo(cx - r * 0.48f, cy + r * 0.3f);
            TMP_TRI.lineTo(cx - r * 0.9f, cy + r * 0.3f);
            TMP_TRI.close();
            canvas.drawPath(TMP_TRI, stroke);
            TMP_RECT.set(cx - r * 0.1f, cy - r * 0.45f, cx + r * 0.52f, cy + r * 0.45f);
            canvas.drawArc(TMP_RECT, -48f, 96f, false, stroke);
            TMP_RECT.set(cx - r * 0.12f, cy - r * 0.82f, cx + r * 0.92f, cy + r * 0.82f);
            canvas.drawArc(TMP_RECT, -48f, 96f, false, stroke);
            return;
        }
        if (GestureActions.NOTIF.equals(action)) {
            path.reset();
            path.moveTo(cx - r * 0.65f, cy + r * 0.45f);
            path.lineTo(cx - r * 0.65f, cy - r * 0.1f);
            TMP_RECT.set(cx - r * 0.65f, cy - r * 0.78f, cx + r * 0.65f, cy + r * 0.52f);
            path.arcTo(TMP_RECT, 180f, 180f, false);
            path.lineTo(cx + r * 0.65f, cy + r * 0.45f);
            path.lineTo(cx + r * 0.88f, cy + r * 0.62f);
            path.lineTo(cx - r * 0.88f, cy + r * 0.62f);
            path.close();
            canvas.drawPath(path, stroke);
            canvas.drawCircle(cx, cy + r * 0.9f, Math.max(1.5f, r * 0.14f), fill);
            return;
        }
        if (GestureActions.QS.equals(action)) {
            float hw = r * 0.85f;
            float dy = r * 0.56f;
            canvas.drawLine(cx - hw, cy - dy, cx + hw, cy - dy, stroke);
            canvas.drawLine(cx - hw, cy, cx + hw, cy, stroke);
            canvas.drawLine(cx - hw, cy + dy, cx + hw, cy + dy, stroke);
            float kr = Math.max(2f, r * 0.19f);
            canvas.drawCircle(cx - r * 0.25f, cy - dy, kr, fill);
            canvas.drawCircle(cx + r * 0.32f, cy, kr, fill);
            canvas.drawCircle(cx - r * 0.18f, cy + dy, kr, fill);
            return;
        }
        if (GestureActions.SCREENSHOT.equals(action)) {
            float e = r * 0.86f;
            float k = r * 0.38f;
            path.reset();
            path.moveTo(cx - e, cy - e + k); path.lineTo(cx - e, cy - e); path.lineTo(cx - e + k, cy - e);
            path.moveTo(cx + e - k, cy - e); path.lineTo(cx + e, cy - e); path.lineTo(cx + e, cy - e + k);
            path.moveTo(cx - e, cy + e - k); path.lineTo(cx - e, cy + e); path.lineTo(cx - e + k, cy + e);
            path.moveTo(cx + e - k, cy + e); path.lineTo(cx + e, cy + e); path.lineTo(cx + e, cy + e - k);
            canvas.drawPath(path, stroke);
            canvas.drawCircle(cx, cy, r * 0.34f, stroke);
            return;
        }
        if (GestureActions.LOCK.equals(action)) {
            TMP_RECT.set(cx - r * 0.72f, cy - r * 0.08f, cx + r * 0.72f, cy + r * 0.88f);
            canvas.drawRoundRect(TMP_RECT, r * 0.22f, r * 0.22f, stroke);
            TMP_RECT.set(cx - r * 0.44f, cy - r * 0.82f, cx + r * 0.44f, cy + r * 0.04f);
            canvas.drawArc(TMP_RECT, 180f, 180f, false, stroke);
            canvas.drawCircle(cx, cy + r * 0.4f, Math.max(1.5f, r * 0.14f), fill);
            return;
        }
        if (GestureActions.MEDIA.equals(action)) {
            TMP_TRI.reset();
            TMP_TRI.moveTo(cx - r * 0.78f, cy - r * 0.62f);
            TMP_TRI.lineTo(cx + r * 0.08f, cy);
            TMP_TRI.lineTo(cx - r * 0.78f, cy + r * 0.62f);
            TMP_TRI.close();
            canvas.drawPath(TMP_TRI, fill);
            canvas.drawLine(cx + r * 0.36f, cy - r * 0.55f, cx + r * 0.36f, cy + r * 0.55f, stroke);
            canvas.drawLine(cx + r * 0.74f, cy - r * 0.55f, cx + r * 0.74f, cy + r * 0.55f, stroke);
            return;
        }
        // Fallback grid for app launcher
        float g = r * 0.22f;
        float s = r * 0.62f;
        TMP_RECT.set(cx - g - s, cy - g - s, cx - g, cy - g);
        canvas.drawRoundRect(TMP_RECT, r * 0.14f, r * 0.14f, stroke);
        TMP_RECT.set(cx + g, cy - g - s, cx + g + s, cy - g);
        canvas.drawRoundRect(TMP_RECT, r * 0.14f, r * 0.14f, stroke);
        TMP_RECT.set(cx - g - s, cy + g, cx - g, cy + g + s);
        canvas.drawRoundRect(TMP_RECT, r * 0.14f, r * 0.14f, stroke);
        TMP_RECT.set(cx + g, cy + g, cx + g + s, cy + g + s);
        canvas.drawRoundRect(TMP_RECT, r * 0.14f, r * 0.14f, stroke);
    }

    /**
     * View hiển thị icon hành động (vector) hoặc icon thật của app đã chọn.
     */
    static final class ActionIconView extends View {
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Rect bounds = new Rect();
        private String action = GestureActions.NONE;
        private Drawable appDrawable;

        ActionIconView(Context ctx, int color, float density) {
            super(ctx);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            strokePaint.setStrokeWidth(Math.max(1.8f, 1.9f * density));
            strokePaint.setColor(color);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(color);
        }

        void setAction(String action, Drawable preloadedAppDrawable) {
            this.action = action != null ? action : GestureActions.NONE;
            if (preloadedAppDrawable != null) {
                this.appDrawable = preloadedAppDrawable;
            } else if (this.action.startsWith(GestureActions.APP_PREFIX)
                    && this.action.length() > GestureActions.APP_PREFIX.length()) {
                this.appDrawable = loadAppIcon(getContext(),
                        this.action.substring(GestureActions.APP_PREFIX.length()));
            } else {
                this.appDrawable = null;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (appDrawable != null) {
                int pad = Math.max(1, Math.min(w, h) / 10);
                bounds.set(pad, pad, w - pad, h - pad);
                appDrawable.setBounds(bounds);
                appDrawable.draw(canvas);
                return;
            }
            float sz = Math.min(w, h) * 0.62f;
            drawVector(canvas, action, w / 2f, h / 2f, sz, strokePaint, fillPaint, path);
        }
    }
}
