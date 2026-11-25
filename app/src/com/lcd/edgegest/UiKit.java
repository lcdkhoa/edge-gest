package com.lcd.edgegest;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Helper UI dùng chung giữa MainActivity và AnimConfigActivity (bảng màu Sáng/Tối, card, nút, ChevronView).
 * Tuân thủ ràng buộc d8 34.0.0: chỉ dùng static nested class, không lambda/anonymous class.
 */
final class UiKit {
    final boolean dark;
    final int cBg, cCard, cSub, cBorder, cText, cMuted, cPrimary;
    final int cOkFg, cOkBg, cWarnFg, cWarnBg, cErrFg, cErrBg;
    private final Context ctx;
    private final float density;

    UiKit(Context ctx) {
        this.ctx = ctx;
        this.density = ctx.getResources().getDisplayMetrics().density;
        int night = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        dark = (night == Configuration.UI_MODE_NIGHT_YES);
        if (dark) {
            cBg = 0xFF111315; cCard = 0xFF1B1E22; cSub = 0xFF24282E; cBorder = 0xFF2E333B;
            cText = 0xFFEEF0F3; cMuted = 0xFF9BA2AD; cPrimary = 0xFF3B82F6;
            cOkFg = 0xFF4ADE80; cOkBg = 0xFF142E20;
            cWarnFg = 0xFFFBBF24; cWarnBg = 0xFF2E2412;
            cErrFg = 0xFFF87171; cErrBg = 0xFF31181A;
        } else {
            cBg = 0xFFF4F5F7; cCard = 0xFFFFFFFF; cSub = 0xFFF7F8FA; cBorder = 0xFFE2E5EA;
            cText = 0xFF181A1F; cMuted = 0xFF646B75; cPrimary = 0xFF2563EB;
            cOkFg = 0xFF15803D; cOkBg = 0xFFDCFCE7;
            cWarnFg = 0xFFB45309; cWarnBg = 0xFFFEF3C7;
            cErrFg = 0xFFB91C1C; cErrBg = 0xFFFEE2E2;
        }
    }

    int dp(int v) {
        return Math.round(v * density);
    }

    GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(cCard, cBorder, 16));
        int p = dp(14);
        card.setPadding(p, p, p, p);
        return card;
    }

    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    TextView pill(String s, int fg, int bg) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(fg);
        t.setBackground(roundRect(bg, 0, 99));
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        return t;
    }

    void setPill(TextView t, String s, int fg, int bg) {
        t.setText(s);
        t.setTextColor(fg);
        t.setBackground(roundRect(bg, 0, 99));
    }

    Button actionButton(String label, int bg, int fg, int stroke) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(fg);
        b.setBackground(roundRect(bg, stroke, 12));
        b.setStateListAnimator(null);
        return b;
    }

    Button compactButton(String label, int bg, int fg, int stroke) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(fg);
        b.setBackground(roundRect(bg, stroke, 10));
        b.setPadding(dp(12), dp(5), dp(12), dp(5));
        b.setMinHeight(dp(32));
        b.setMinimumHeight(dp(32));
        b.setStateListAnimator(null);
        return b;
    }

    Button stepButton(String sym) {
        Button b = new Button(ctx);
        b.setText(sym);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(cText);
        b.setBackground(roundRect(cSub, cBorder, 8));
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        return b;
    }

    LinearLayout optionRow(String titleStr, String subStr) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(0, 0, dp(10), 0);
        left.addView(text(titleStr, 13, cText, true));
        if (subStr != null && !subStr.isEmpty()) {
            TextView sub = text(subStr, 11, cMuted, false);
            sub.setPadding(0, dp(1), 0, 0);
            left.addView(sub);
        }
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    View divider() {
        View v = new View(ctx);
        v.setBackgroundColor(cBorder);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        v.setLayoutParams(lp);
        return v;
    }

    View vSpace(int dpVal) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(dpVal)));
        return v;
    }

    LinearLayout makeLangPill(boolean vi, Button[] viOut, Button[] enOut, View.OnClickListener listener) {
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setBackground(roundRect(cSub, cBorder, 10));
        int p = dp(2);
        pill.setPadding(p, p, p, p);

        Button viBtn = langBtn("VI", vi, listener);
        Button enBtn = langBtn("EN", !vi, listener);
        pill.addView(viBtn, new LinearLayout.LayoutParams(dp(34), dp(26)));
        LinearLayout.LayoutParams enLp = new LinearLayout.LayoutParams(dp(34), dp(26));
        enLp.leftMargin = dp(2);
        pill.addView(enBtn, enLp);

        viOut[0] = viBtn;
        enOut[0] = enBtn;
        return pill;
    }

    private Button langBtn(String label, boolean active, View.OnClickListener listener) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setBackground(roundRect(active ? cPrimary : 0, 0, 8));
        b.setTextColor(active ? 0xFFFFFFFF : cMuted);
        b.setOnClickListener(listener);
        return b;
    }

    LinearLayout makeBottomBar(Button cancelBtn, Button applyBtn) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(cCard);
        bar.setPadding(dp(14), dp(10), dp(14), dp(12));

        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        cLp.rightMargin = dp(8);
        bar.addView(cancelBtn, cLp);
        bar.addView(applyBtn, new LinearLayout.LayoutParams(0, dp(42), 2f));
        return bar;
    }

    static final class ChevronView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int size;

        ChevronView(Context ctx, int color, int sizePx, int strokePx) {
            super(ctx);
            this.size = sizePx;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Math.max(2f, strokePx));
        }

        void setExpanded(boolean expanded, boolean animate) {
            float target = expanded ? 0f : -90f;
            if (animate) animate().rotation(target).setDuration(180).start();
            else setRotation(target);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            path.reset();
            path.moveTo(w * 0.25f, h * 0.375f);
            path.lineTo(w * 0.50f, h * 0.625f);
            path.lineTo(w * 0.75f, h * 0.375f);
            canvas.drawPath(path, paint);
        }
    }

    static final class BackArrowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        BackArrowView(Context ctx, int color, float density) {
            super(ctx);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Math.max(2f, 2.2f * density));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w * 0.5f, cy = h * 0.5f;
            float hw = Math.min(w, h) * 0.24f;
            float ah = hw * 0.85f;
            path.reset();
            path.moveTo(cx + hw, cy);
            path.lineTo(cx - hw, cy);
            path.moveTo(cx - hw + ah, cy - ah);
            path.lineTo(cx - hw, cy);
            path.lineTo(cx - hw + ah, cy + ah);
            canvas.drawPath(path, paint);
        }
    }

    static final class RainbowDotView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader shader;

        RainbowDotView(Context ctx, int borderColor, float density) {
            super(ctx);
            border.setStyle(Paint.Style.STROKE);
            border.setColor(borderColor);
            border.setStrokeWidth(Math.max(1f, density));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) / 2f - border.getStrokeWidth();
            if (shader == null) {
                int[] cols = {
                    0xFFEF4444, 0xFFEAB308, 0xFF22C55E,
                    0xFF06B6D4, 0xFF3B82F6, 0xFFA855F7, 0xFFEF4444
                };
                shader = new SweepGradient(cx, cy, cols, null);
                fill.setShader(shader);
            }
            canvas.drawCircle(cx, cy, r, fill);
            canvas.drawCircle(cx, cy, r, border);
        }
    }

    static final class InsetPadding implements View.OnApplyWindowInsetsListener {
        InsetPadding() {}

        @Override
        public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
            v.setPadding(insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        }
    }
}
