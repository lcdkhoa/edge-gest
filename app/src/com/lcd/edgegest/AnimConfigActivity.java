package com.lcd.edgegest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Màn hình con tuỳ chỉnh Animation kéo mép:
 * - Icon Back vector chuẩn, giao diện gọn bỏ mô tả thừa
 * - Khung Preview ghim cố định (sticky) bên dưới AppBar
 * - Hỗ trợ Bánh xe màu HSV + độ sáng + Hex đồng bộ 2 chiều
 */
public final class AnimConfigActivity extends Activity implements View.OnClickListener {
    private static final int[] SWATCH_COLORS = {
        0xFF2563EB, 0xFF7C3AED, 0xFF16A34A, 0xFFD97706, 0xFFDC2626, 0xFF1E242B
    };

    UiKit ui;
    boolean vi;
    private PreviewCanvas previewCanvas;
    private TextView previewDisabledMsg;
    private TextView hexLabel, widthVal, lengthVal, alphaVal;
    private Button simLeftBtn, simRightBtn, simBottomBtn, simHoldBtn;
    private Button langViBtn, langEnBtn, cancelBtn, applyBtn;
    private FrameLayout backBtn;
    private LinearLayout wheelBtn;
    private Switch leftSw, rightSw, bottomSw;
    private SeekBar widthSb, lengthSb, alphaSb;
    private final View[] swatchViews = new View[SWATCH_COLORS.length];

    int simEdge = StripView.LEFT;
    boolean simHold;
    float dragProg = 0.92f;
    boolean animLeft, animRight, animBottom;
    int animColor, animWidth, animLength, animAlpha;
    String leftHold = GestureActions.NONE, rightHold = GestureActions.NONE;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().hide();
        ui = new UiKit(this);
        vi = Strings.isVi(this);

        SharedPreferences prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE);
        animLeft = prefs.getBoolean("animLeft", OverlayService.DEF_ANIM_LEFT);
        animRight = prefs.getBoolean("animRight", OverlayService.DEF_ANIM_RIGHT);
        animBottom = prefs.getBoolean("animBottom", OverlayService.DEF_ANIM_BOTTOM);
        animColor = prefs.getInt("animColor", OverlayService.DEF_ANIM_COLOR);
        animWidth = prefs.getInt("animWidth", OverlayService.DEF_ANIM_WIDTH);
        animLength = prefs.getInt("animLength", OverlayService.DEF_ANIM_LENGTH);
        animAlpha = prefs.getInt("animAlpha", OverlayService.DEF_ANIM_ALPHA);
        leftHold = prefs.getString("leftHold", OverlayService.DEF_LEFT_HOLD);
        rightHold = prefs.getString("rightHold", OverlayService.DEF_RIGHT_HOLD);

        if (b != null) {
            animLeft = b.getBoolean("animLeft", animLeft);
            animRight = b.getBoolean("animRight", animRight);
            animBottom = b.getBoolean("animBottom", animBottom);
            animColor = b.getInt("animColor", animColor);
            animWidth = b.getInt("animWidth", animWidth);
            animLength = b.getInt("animLength", animLength);
            animAlpha = b.getInt("animAlpha", animAlpha);
            simEdge = b.getInt("simEdge", simEdge);
            simHold = b.getBoolean("simHold", simHold);
            dragProg = b.getFloat("dragProg", dragProg);
        }

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(ui.cBg);
        if (Build.VERSION.SDK_INT >= 20) {
            outer.setOnApplyWindowInsetsListener(new UiKit.InsetPadding());
        }

        float density = getResources().getDisplayMetrics().density;

        // Top AppBar with crisp Vector Back Arrow
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ui.cCard);
        topBar.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));

        backBtn = new FrameLayout(this);
        backBtn.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 10));
        backBtn.setOnClickListener(this);
        backBtn.addView(new UiKit.BackArrowView(this, ui.cText, density),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams bkLp = new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36));
        bkLp.rightMargin = ui.dp(10);
        topBar.addView(backBtn, bkLp);

        topBar.addView(ui.text(Strings.pick(vi, "Tuỳ chỉnh Animation", "Animation Settings"), 16, ui.cText, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button[] viOut = new Button[1];
        Button[] enOut = new Button[1];
        topBar.addView(ui.makeLangPill(vi, viOut, enOut, this));
        langViBtn = viOut[0];
        langEnBtn = enOut[0];
        outer.addView(topBar);

        outer.addView(lineDivider());

        // Sticky Preview pinned ABOVE ScrollView
        LinearLayout stickySlot = new LinearLayout(this);
        stickySlot.setOrientation(LinearLayout.VERTICAL);
        stickySlot.setBackgroundColor(ui.cBg);
        stickySlot.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        buildPreviewCard(stickySlot);
        outer.addView(stickySlot);

        outer.addView(lineDivider());

        // Scrollable Body below sticky preview
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = ui.dp(14);
        body.setPadding(pad, ui.dp(12), pad, ui.dp(18));

        buildEdgesCard(body);
        body.addView(ui.vSpace(10));
        buildColorCard(body, density);
        body.addView(ui.vSpace(10));
        buildSizeAlphaCard(body);

        scroll.addView(body);
        outer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Sticky Bottom Bar
        outer.addView(lineDivider());

        cancelBtn = ui.actionButton(Strings.pick(vi, "Hủy", "Cancel"), ui.cSub, ui.cText, ui.cBorder);
        cancelBtn.setOnClickListener(this);
        applyBtn = ui.actionButton(Strings.pick(vi, "Áp dụng Animation", "Apply Animation"), ui.cPrimary, Color.WHITE, 0);
        applyBtn.setOnClickListener(this);
        outer.addView(ui.makeBottomBar(cancelBtn, applyBtn));

        setContentView(outer);
        syncUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("animLeft", animLeft);
        out.putBoolean("animRight", animRight);
        out.putBoolean("animBottom", animBottom);
        out.putInt("animColor", animColor);
        out.putInt("animWidth", animWidth);
        out.putInt("animLength", animLength);
        out.putInt("animAlpha", animAlpha);
        out.putInt("simEdge", simEdge);
        out.putBoolean("simHold", simHold);
        out.putFloat("dragProg", dragProg);
    }

    private View lineDivider() {
        View v = new View(this);
        v.setBackgroundColor(ui.cBorder);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, ui.dp(1))));
        return v;
    }

    private void buildPreviewCard(LinearLayout root) {
        LinearLayout card = ui.makeCard();
        int cp = ui.dp(11);
        card.setPadding(cp, cp, cp, cp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(ui.text(Strings.pick(vi, "Preview trực tiếp", "Live Preview"), 13, ui.cText, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        simLeftBtn = miniChip(Strings.pick(vi, "Trái", "Left"));
        simRightBtn = miniChip(Strings.pick(vi, "Phải", "Right"));
        simBottomBtn = miniChip(Strings.pick(vi, "Dưới", "Bottom"));
        simHoldBtn = miniChip(Strings.pick(vi, "Giữ", "Hold"));
        simLeftBtn.setOnClickListener(this);
        simRightBtn.setOnClickListener(this);
        simBottomBtn.setOnClickListener(this);
        simHoldBtn.setOnClickListener(this);

        top.addView(simLeftBtn, chipLp());
        top.addView(simRightBtn, chipLp());
        top.addView(simBottomBtn, chipLp());
        top.addView(simHoldBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(28)));
        card.addView(top);
        card.addView(ui.vSpace(8));

        LinearLayout stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setGravity(Gravity.CENTER);
        stage.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 12));

        previewCanvas = new PreviewCanvas(this);
        previewCanvas.setOnTouchListener(new PreviewTouchListener(this));
        stage.addView(previewCanvas, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(128)));

        previewDisabledMsg = ui.text(
                Strings.pick(vi, "Cạnh này đang tắt hiển thị animation", "Animation disabled for this edge"),
                12, ui.cMuted, false);
        previewDisabledMsg.setGravity(Gravity.CENTER);
        previewDisabledMsg.setPadding(ui.dp(12), ui.dp(44), ui.dp(12), ui.dp(44));
        stage.addView(previewDisabledMsg);

        card.addView(stage);
        root.addView(card);
    }

    private void buildEdgesCard(LinearLayout root) {
        LinearLayout card = ui.makeCard();
        card.addView(ui.text(Strings.pick(vi, "1. Hiện ở cử chỉ nào", "1. Active Edges"), 14, ui.cText, true));
        card.addView(ui.vSpace(6));

        EdgeSwitchListener sl = new EdgeSwitchListener(this);
        leftSw = addEdgeSwitch(card, Strings.pick(vi, "Mép trái", "Left edge"), animLeft, sl);
        card.addView(ui.divider());
        rightSw = addEdgeSwitch(card, Strings.pick(vi, "Mép phải", "Right edge"), animRight, sl);
        card.addView(ui.divider());
        bottomSw = addEdgeSwitch(card, Strings.pick(vi, "Mép dưới", "Bottom edge"), animBottom, sl);
        root.addView(card);
    }

    private Switch addEdgeSwitch(LinearLayout card, String title, boolean checked,
            EdgeSwitchListener listener) {
        LinearLayout row = ui.optionRow(title, null);
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        row.addView(sw);
        card.addView(row);
        return sw;
    }

    private void buildColorCard(LinearLayout root, float density) {
        LinearLayout card = ui.makeCard();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(ui.text(Strings.pick(vi, "2. Màu sắc dãy hiển thị", "2. Indicator Color"), 14, ui.cText, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        hexLabel = ui.text(ConfigBackup.formatHex(animColor), 12, ui.cMuted, true);
        hexLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        top.addView(hexLabel);
        card.addView(top);
        card.addView(ui.vSpace(10));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < SWATCH_COLORS.length; i++) {
            View sw = new View(this);
            sw.setOnClickListener(new SwatchClick(this, SWATCH_COLORS[i]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(30), ui.dp(30));
            lp.rightMargin = ui.dp(7);
            row.addView(sw, lp);
            swatchViews[i] = sw;
        }

        wheelBtn = new LinearLayout(this);
        wheelBtn.setOrientation(LinearLayout.HORIZONTAL);
        wheelBtn.setGravity(Gravity.CENTER_VERTICAL);
        wheelBtn.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 99));
        wheelBtn.setPadding(ui.dp(9), ui.dp(6), ui.dp(11), ui.dp(6));
        wheelBtn.setOnClickListener(this);

        UiKit.RainbowDotView dot = new UiKit.RainbowDotView(this, ui.cBorder, density);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16));
        dLp.rightMargin = ui.dp(6);
        wheelBtn.addView(dot, dLp);
        wheelBtn.addView(ui.text(Strings.pick(vi, "Bánh xe màu", "Color Wheel"), 11, ui.cText, true));
        row.addView(wheelBtn);

        card.addView(row);
        root.addView(card);
    }

    private void buildSizeAlphaCard(LinearLayout root) {
        LinearLayout card = ui.makeCard();
        card.addView(ui.text(Strings.pick(vi, "3. Kích thước & Độ trong suốt", "3. Size & Opacity"), 14, ui.cText, true));
        card.addView(ui.vSpace(8));

        TextView[] outLbl = new TextView[1];
        widthSb = addSlider(card, Strings.pick(vi, "Độ rộng / độ vươn (dp)", "Width / Bulge reach (dp)"), 24, 80, animWidth, outLbl);
        widthVal = outLbl[0];
        card.addView(ui.vSpace(8));

        lengthSb = addSlider(card, Strings.pick(vi, "Độ dài dải (dp)", "Indicator length (dp)"), 80, 220, animLength, outLbl);
        lengthVal = outLbl[0];
        card.addView(ui.vSpace(8));

        alphaSb = addSlider(card, Strings.pick(vi, "Độ trong suốt (%)", "Opacity (%)"), 15, 100, animAlpha, outLbl);
        alphaVal = outLbl[0];
        root.addView(card);
    }

    private SeekBar addSlider(LinearLayout parent, String title, int min, int max, int cur, TextView[] valOut) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(ui.text(title, 12, ui.cText, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = ui.text("", 12, ui.cPrimary, true);
        val.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        top.addView(val);
        parent.addView(top);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, cur - min)));
        sb.setOnSeekBarChangeListener(new SliderListener(this, min));
        parent.addView(sb);
        valOut[0] = val;
        return sb;
    }

    private Button miniChip(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(28));
        lp.rightMargin = ui.dp(4);
        return lp;
    }

    void syncUi() {
        styleChip(simLeftBtn, simEdge == StripView.LEFT, false);
        styleChip(simRightBtn, simEdge == StripView.RIGHT, false);
        styleChip(simBottomBtn, simEdge == StripView.BOTTOM, false);
        styleChip(simHoldBtn, simHold, true);

        hexLabel.setText(ConfigBackup.formatHex(animColor));
        for (int i = 0; i < SWATCH_COLORS.length; i++) {
            boolean sel = (SWATCH_COLORS[i] & 0x00FFFFFF) == (animColor & 0x00FFFFFF);
            swatchViews[i].setBackground(ui.roundRect(SWATCH_COLORS[i], sel ? ui.cText : ui.cBorder, 99));
        }

        widthVal.setText(animWidth + " dp");
        lengthVal.setText(animLength + " dp");
        alphaVal.setText(animAlpha + "%");

        boolean enabled = (simEdge == StripView.LEFT && animLeft)
                || (simEdge == StripView.RIGHT && animRight)
                || (simEdge == StripView.BOTTOM && animBottom);
        previewCanvas.setVisibility(enabled ? View.VISIBLE : View.GONE);
        previewDisabledMsg.setVisibility(enabled ? View.GONE : View.VISIBLE);
        previewCanvas.invalidate();
    }

    private void styleChip(Button b, boolean active, boolean warn) {
        if (active) {
            int bg = warn ? ui.cWarnBg : ui.cPrimary;
            int fg = warn ? ui.cWarnFg : Color.WHITE;
            int stroke = warn ? ui.cWarnFg : 0;
            b.setBackground(ui.roundRect(bg, stroke, 8));
            b.setTextColor(fg);
        } else {
            b.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 8));
            b.setTextColor(ui.cMuted);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == langViBtn) {
            Strings.setLang(this, Strings.LANG_VI);
            recreate();
        } else if (v == langEnBtn) {
            Strings.setLang(this, Strings.LANG_EN);
            recreate();
        } else if (v == backBtn || v == cancelBtn) {
            setResult(RESULT_CANCELED);
            finish();
        } else if (v == simLeftBtn) {
            simEdge = StripView.LEFT;
            syncUi();
        } else if (v == simRightBtn) {
            simEdge = StripView.RIGHT;
            syncUi();
        } else if (v == simBottomBtn) {
            simEdge = StripView.BOTTOM;
            syncUi();
        } else if (v == simHoldBtn) {
            simHold = !simHold;
            syncUi();
        } else if (v == wheelBtn) {
            showColorWheelDialog();
        } else if (v == applyBtn) {
            saveAndApply();
        }
    }

    private void showColorWheelDialog() {
        WheelHolder wh = new WheelHolder(this, animColor);
        new AlertDialog.Builder(this)
                .setTitle(Strings.pick(vi, "Bánh xe màu", "Color Wheel Picker"))
                .setView(wh.root)
                .setPositiveButton(Strings.pick(vi, "Chọn màu này", "Select"), new WheelApplyClick(this, wh))
                .setNegativeButton(Strings.pick(vi, "Hủy", "Cancel"), null)
                .show();
    }

    private void saveAndApply() {
        boolean anyEdge = animLeft || animRight || animBottom;
        getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE).edit()
                .putBoolean("anim", anyEdge)
                .putBoolean("animLeft", animLeft)
                .putBoolean("animRight", animRight)
                .putBoolean("animBottom", animBottom)
                .putInt("animColor", animColor)
                .putInt("animWidth", animWidth)
                .putInt("animLength", animLength)
                .putInt("animAlpha", animAlpha)
                .apply();

        if (OverlayService.running && Settings.canDrawOverlays(this)) {
            Intent svc = new Intent(this, OverlayService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        }
        Toast.makeText(this, Strings.pick(vi, "Đã áp dụng cấu hình Animation", "Applied Animation settings"),
                Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    String currentPreviewAction() {
        if (simEdge == StripView.BOTTOM) {
            return simHold ? GestureActions.RECENTS : GestureActions.HOME;
        }
        String configured = simEdge == StripView.RIGHT ? rightHold : leftHold;
        return GestureActions.isEnabled(configured) ? configured : GestureActions.POWER;
    }

    // ---- Color Wheel UI & HSV Math ----

    private static final class WheelHolder {
        final LinearLayout root;
        final ColorWheelView wheel;
        final SeekBar brightSb;
        final TextView brightPct;
        final View previewDot;
        final EditText hexEt;
        final UiKit ui;
        float hue, sat, val;
        boolean updatingHex;

        WheelHolder(AnimConfigActivity act, int initialColor) {
            this.ui = act.ui;
            float[] hsv = new float[3];
            Color.colorToHSV(initialColor, hsv);
            hue = hsv[0];
            sat = hsv[1];
            val = Math.max(0.1f, hsv[2]);

            root = new LinearLayout(act);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            int p = ui.dp(16);
            root.setPadding(p, ui.dp(10), p, ui.dp(6));

            wheel = new ColorWheelView(act, this);
            LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(ui.dp(220), ui.dp(220));
            wLp.bottomMargin = ui.dp(12);
            root.addView(wheel, wLp);

            LinearLayout bTop = new LinearLayout(act);
            bTop.setOrientation(LinearLayout.HORIZONTAL);
            bTop.addView(ui.text(Strings.pick(act.vi, "Độ sáng", "Brightness"), 12, ui.cText, true),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            brightPct = ui.text("", 12, ui.cPrimary, true);
            brightPct.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            bTop.addView(brightPct);
            root.addView(bTop, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            brightSb = new SeekBar(act);
            brightSb.setMax(90);
            brightSb.setProgress(Math.max(0, Math.min(90, Math.round(val * 100f) - 10)));
            brightSb.setOnSeekBarChangeListener(new BrightSeekListener(this));
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sLp.bottomMargin = ui.dp(10);
            root.addView(brightSb, sLp);

            LinearLayout bot = new LinearLayout(act);
            bot.setOrientation(LinearLayout.HORIZONTAL);
            bot.setGravity(Gravity.CENTER_VERTICAL);

            previewDot = new View(act);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36));
            dLp.rightMargin = ui.dp(10);
            bot.addView(previewDot, dLp);

            hexEt = new EditText(act);
            hexEt.setSingleLine(true);
            hexEt.setTextSize(13);
            hexEt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            hexEt.setTextColor(ui.cText);
            hexEt.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 10));
            hexEt.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
            hexEt.addTextChangedListener(new HexInputWatcher(this));
            bot.addView(hexEt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            root.addView(bot, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            sync(true);
        }

        int currentColor() {
            return Color.HSVToColor(new float[]{hue, sat, val}) | 0xFF000000;
        }

        void sync(boolean writeHexText) {
            int col = currentColor();
            brightPct.setText(Math.round(val * 100f) + "%");
            previewDot.setBackground(ui.roundRect(col, ui.cBorder, 10));
            if (writeHexText) {
                updatingHex = true;
                hexEt.setText(ConfigBackup.formatHex(col));
                updatingHex = false;
            }
            wheel.invalidate();
        }
    }

    private static final class ColorWheelView extends View {
        private final WheelHolder holder;
        private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobDark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int lastSize = -1;

        ColorWheelView(Context ctx, WheelHolder holder) {
            super(ctx);
            this.holder = holder;
            float d = ctx.getResources().getDisplayMetrics().density;
            knobWhite.setStyle(Paint.Style.STROKE);
            knobWhite.setColor(0xFFFFFFFF);
            knobWhite.setStrokeWidth(3f * d);
            knobDark.setStyle(Paint.Style.STROKE);
            knobDark.setColor(0xFF181A1F);
            knobDark.setStrokeWidth(1.2f * d);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            int size = Math.min(w, h);
            if (size <= 0) return;
            float cx = w / 2f, cy = h / 2f;
            float r = size / 2f - getResources().getDisplayMetrics().density * 8f;
            if (r <= 4f) return;

            if (size != lastSize) {
                lastSize = size;
                int[] hues = {
                    0xFFFF0000, 0xFFFFFF00, 0xFF00FF00,
                    0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
                };
                Shader sweep = new SweepGradient(cx, cy, hues, null);
                Shader radial = new RadialGradient(cx, cy, r,
                        0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
                wheelPaint.setShader(new ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER));
            }

            canvas.drawCircle(cx, cy, r, wheelPaint);
            if (holder.val < 0.99f) {
                dimPaint.setColor(Color.argb(Math.round((1f - holder.val) * 255f), 0, 0, 0));
                canvas.drawCircle(cx, cy, r, dimPaint);
            }

            double rad = Math.toRadians(holder.hue);
            float tx = cx + (float) Math.cos(rad) * (holder.sat * r);
            float ty = cy + (float) Math.sin(rad) * (holder.sat * r);
            float kr = 9f * getResources().getDisplayMetrics().density;
            knobFill.setColor(holder.currentColor());
            canvas.drawCircle(tx, ty, kr, knobFill);
            canvas.drawCircle(tx, ty, kr, knobWhite);
            canvas.drawCircle(tx, ty, kr + 1.5f, knobDark);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) / 2f - getResources().getDisplayMetrics().density * 8f;
            if (r <= 1f) return false;
            float dx = event.getX() - cx;
            float dy = event.getY() - cy;
            double deg = Math.toDegrees(Math.atan2(dy, dx));
            if (deg < 0) deg += 360.0;
            holder.hue = (float) deg;
            holder.sat = Math.min(1f, (float) Math.hypot(dx, dy) / r);
            holder.sync(true);
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
    }

    private static final class BrightSeekListener implements SeekBar.OnSeekBarChangeListener {
        private final WheelHolder holder;

        BrightSeekListener(WheelHolder holder) {
            this.holder = holder;
        }

        @Override
        public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
            if (!fromUser) return;
            holder.val = (10 + progress) / 100f;
            holder.sync(true);
        }

        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    private static final class HexInputWatcher implements TextWatcher {
        private final WheelHolder holder;

        HexInputWatcher(WheelHolder holder) {
            this.holder = holder;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (holder.updatingHex) return;
            String t = s.toString().trim();
            if (!t.startsWith("#")) t = "#" + t;
            if (t.length() != 7) return;
            try {
                int c = Color.parseColor(t) | 0xFF000000;
                float[] hsv = new float[3];
                Color.colorToHSV(c, hsv);
                holder.hue = hsv[0];
                holder.sat = hsv[1];
                holder.val = Math.max(0.1f, hsv[2]);
                holder.brightSb.setProgress(Math.max(0, Math.min(90, Math.round(holder.val * 100f) - 10)));
                holder.sync(false);
            } catch (Throwable ignored) {}
        }
    }

    private static final class WheelApplyClick implements DialogInterface.OnClickListener {
        private final AnimConfigActivity act;
        private final WheelHolder holder;

        WheelApplyClick(AnimConfigActivity act, WheelHolder holder) {
            this.act = act;
            this.holder = holder;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            act.animColor = holder.currentColor();
            act.syncUi();
        }
    }

    // ---- Existing Preview & Slider Listeners ----

    private static final class PreviewCanvas extends View {
        private final AnimConfigActivity act;
        private final float density;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private String cachedPkg = "";
        private Bitmap cachedBmp;

        PreviewCanvas(AnimConfigActivity act) {
            super(act);
            this.act = act;
            this.density = act.getResources().getDisplayMetrics().density;
            fillPaint.setStyle(Paint.Style.FILL);
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);
            iconPaint.setStrokeWidth(2.3f * density);
            iconPaint.setColor(0xFFFFFFFF);
            iconFillPaint.setStyle(Paint.Style.FILL);
            iconFillPaint.setColor(0xFFFFFFFF);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            String action = act.currentPreviewAction();
            Bitmap appBmp = null;
            if (act.simHold && action.startsWith(GestureActions.APP_PREFIX)
                    && action.length() > GestureActions.APP_PREFIX.length()) {
                String pkg = action.substring(GestureActions.APP_PREFIX.length());
                if (!pkg.equals(cachedPkg)) {
                    cachedPkg = pkg;
                    if (cachedBmp != null) {
                        cachedBmp.recycle();
                        cachedBmp = null;
                    }
                    cachedBmp = ActionIcons.buildCircularAppBitmap(
                            ActionIcons.loadAppIcon(act, pkg),
                            Math.max(24, Math.round(28f * density)));
                }
                appBmp = cachedBmp;
            }
            EdgeAnimView.drawIndicator(canvas, w, h, act.simEdge, density,
                    act.animColor, act.animWidth, act.animLength, act.animAlpha,
                    act.dragProg, w / 2f, h / 2f, true, act.simHold, action, appBmp,
                    fillPaint, iconPaint, iconFillPaint, path);
        }
    }

    private static final class PreviewTouchListener implements View.OnTouchListener {
        private final AnimConfigActivity act;

        PreviewTouchListener(AnimConfigActivity act) {
            this.act = act;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            int w = v.getWidth(), h = v.getHeight();
            if (w <= 0 || h <= 0) return false;
            float span = Math.max(80f, Math.min(w, h));
            float raw;
            if (act.simEdge == StripView.RIGHT) raw = (w - e.getX()) / span;
            else if (act.simEdge == StripView.BOTTOM) raw = (h - e.getY()) / (h * 0.7f);
            else raw = e.getX() / span;
            act.dragProg = Math.max(0.18f, Math.min(1f, raw));
            v.invalidate();
            return true;
        }
    }

    private static final class EdgeSwitchListener implements CompoundButton.OnCheckedChangeListener {
        private final AnimConfigActivity act;

        EdgeSwitchListener(AnimConfigActivity act) {
            this.act = act;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            act.animLeft = act.leftSw.isChecked();
            act.animRight = act.rightSw.isChecked();
            act.animBottom = act.bottomSw.isChecked();
            act.syncUi();
        }
    }

    private static final class SwatchClick implements View.OnClickListener {
        private final AnimConfigActivity act;
        private final int color;

        SwatchClick(AnimConfigActivity act, int color) {
            this.act = act;
            this.color = color;
        }

        @Override
        public void onClick(View v) {
            act.animColor = color;
            act.syncUi();
        }
    }

    private static final class SliderListener implements SeekBar.OnSeekBarChangeListener {
        private final AnimConfigActivity act;
        private final int min;

        SliderListener(AnimConfigActivity act, int min) {
            this.act = act;
            this.min = min;
        }

        @Override
        public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
            int val = min + progress;
            if (sb == act.widthSb) act.animWidth = val;
            else if (sb == act.lengthSb) act.animLength = val;
            else if (sb == act.alphaSb) act.animAlpha = val;
            act.syncUi();
        }

        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
