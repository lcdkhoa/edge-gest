package com.lcd.edgegest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Màn hình con tuỳ chỉnh Animation kéo mép: chọn cạnh (trái/phải/dưới), màu sắc,
 * độ vươn rộng, độ dài dải, độ trong suốt %, kèm khung xem trước trực tiếp và thanh Hủy / Áp dụng ở đáy.
 */
public final class AnimConfigActivity extends Activity implements View.OnClickListener {
    private static final int[] SWATCH_COLORS = {
        0xFF2563EB, 0xFF7C3AED, 0xFF16A34A, 0xFFD97706, 0xFFDC2626, 0xFF1E242B
    };

    private UiKit ui;
    private PreviewCanvas previewCanvas;
    private TextView previewDisabledMsg;
    private TextView hexLabel, widthVal, lengthVal, alphaVal;
    private Button simLeftBtn, simRightBtn, simBottomBtn, simHoldBtn;
    private Button backBtn, customColorBtn, cancelBtn, applyBtn;
    private Switch leftSw, rightSw, bottomSw;
    private SeekBar widthSb, lengthSb, alphaSb;
    private final View[] swatchViews = new View[SWATCH_COLORS.length];

    int simEdge = StripView.LEFT;
    boolean simHold;
    boolean animLeft, animRight, animBottom;
    int animColor, animWidth, animLength, animAlpha;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().hide();
        ui = new UiKit(this);

        SharedPreferences prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE);
        animLeft = prefs.getBoolean("animLeft", OverlayService.DEF_ANIM_LEFT);
        animRight = prefs.getBoolean("animRight", OverlayService.DEF_ANIM_RIGHT);
        animBottom = prefs.getBoolean("animBottom", OverlayService.DEF_ANIM_BOTTOM);
        animColor = prefs.getInt("animColor", OverlayService.DEF_ANIM_COLOR);
        animWidth = prefs.getInt("animWidth", OverlayService.DEF_ANIM_WIDTH);
        animLength = prefs.getInt("animLength", OverlayService.DEF_ANIM_LENGTH);
        animAlpha = prefs.getInt("animAlpha", OverlayService.DEF_ANIM_ALPHA);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(ui.cBg);
        if (Build.VERSION.SDK_INT >= 20) {
            outer.setOnApplyWindowInsetsListener(new UiKit.InsetPadding());
        }

        // Top AppBar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ui.cCard);
        topBar.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));

        backBtn = ui.compactButton("←", ui.cSub, ui.cText, ui.cBorder);
        backBtn.setOnClickListener(this);
        LinearLayout.LayoutParams bkLp = new LinearLayout.LayoutParams(ui.dp(38), ui.dp(36));
        bkLp.rightMargin = ui.dp(10);
        topBar.addView(backBtn, bkLp);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(ui.text("Tuỳ chỉnh Animation", 16, ui.cText, true));
        titleBox.addView(ui.text("Xem trước & tinh chỉnh dải màu khi vuốt", 11, ui.cMuted, false));
        topBar.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        outer.addView(topBar);

        View topDiv = new View(this);
        topDiv.setBackgroundColor(ui.cBorder);
        outer.addView(topDiv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, ui.dp(1))));

        // Scrollable Body
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = ui.dp(14);
        body.setPadding(pad, pad, pad, ui.dp(18));

        buildPreviewCard(body);
        body.addView(ui.vSpace(10));
        buildEdgesCard(body);
        body.addView(ui.vSpace(10));
        buildColorCard(body);
        body.addView(ui.vSpace(10));
        buildSizeAlphaCard(body);

        scroll.addView(body);
        outer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Sticky Bottom Bar
        View botDiv = new View(this);
        botDiv.setBackgroundColor(ui.cBorder);
        outer.addView(botDiv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, ui.dp(1))));

        cancelBtn = ui.actionButton("Hủy", ui.cSub, ui.cText, ui.cBorder);
        cancelBtn.setOnClickListener(this);
        applyBtn = ui.actionButton("Áp dụng Animation", ui.cPrimary, Color.WHITE, 0);
        applyBtn.setOnClickListener(this);
        outer.addView(ui.makeBottomBar(cancelBtn, applyBtn));

        setContentView(outer);
        syncUi();
    }

    private void buildPreviewCard(LinearLayout root) {
        LinearLayout card = ui.makeCard();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(ui.text("Preview trực tiếp", 13, ui.cText, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        simLeftBtn = miniChip("Trái");
        simRightBtn = miniChip("Phải");
        simBottomBtn = miniChip("Dưới");
        simHoldBtn = miniChip("Giữ");
        simLeftBtn.setOnClickListener(this);
        simRightBtn.setOnClickListener(this);
        simBottomBtn.setOnClickListener(this);
        simHoldBtn.setOnClickListener(this);

        top.addView(simLeftBtn, chipLp());
        top.addView(simRightBtn, chipLp());
        top.addView(simBottomBtn, chipLp());
        top.addView(simHoldBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(30)));
        card.addView(top);
        card.addView(ui.vSpace(10));

        LinearLayout stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setGravity(Gravity.CENTER);
        stage.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 12));

        previewCanvas = new PreviewCanvas(this);
        stage.addView(previewCanvas, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(156)));

        previewDisabledMsg = ui.text("Cạnh này đang tắt hiển thị animation", 12, ui.cMuted, false);
        previewDisabledMsg.setGravity(Gravity.CENTER);
        previewDisabledMsg.setPadding(ui.dp(12), ui.dp(56), ui.dp(12), ui.dp(56));
        stage.addView(previewDisabledMsg);

        card.addView(stage);
        root.addView(card);
    }

    private void buildEdgesCard(LinearLayout root) {
        LinearLayout card = ui.makeCard();
        card.addView(ui.text("1. Hiện ở cử chỉ nào", 14, ui.cText, true));
        card.addView(ui.vSpace(6));

        EdgeSwitchListener sl = new EdgeSwitchListener(this);
        leftSw = addEdgeSwitch(card, "Hiển thị animation mép trái", animLeft, sl);
        card.addView(ui.divider());
        rightSw = addEdgeSwitch(card, "Hiển thị animation mép phải", animRight, sl);
        card.addView(ui.divider());
        bottomSw = addEdgeSwitch(card, "Hiển thị animation mép dưới", animBottom, sl);
        root.addView(card);
    }

    private Switch addEdgeSwitch(LinearLayout card, String title, boolean checked,
            EdgeSwitchListener listener) {
        LinearLayout row = ui.optionRow(title, "Bật/tắt dải hiệu ứng khi vuốt cạnh này");
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        row.addView(sw);
        card.addView(row);
        return sw;
    }

    private void buildColorCard(LinearLayout root) {
        LinearLayout card = ui.makeCard();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(ui.text("2. Màu sắc dãy hiển thị", 14, ui.cText, true),
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
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32));
            lp.rightMargin = ui.dp(8);
            row.addView(sw, lp);
            swatchViews[i] = sw;
        }
        customColorBtn = ui.compactButton("Hex...", ui.cSub, ui.cText, ui.cBorder);
        customColorBtn.setOnClickListener(this);
        row.addView(customColorBtn);

        card.addView(row);
        root.addView(card);
    }

    private void buildSizeAlphaCard(LinearLayout root) {
        LinearLayout card = ui.makeCard();
        card.addView(ui.text("3. Kích thước & Độ trong suốt", 14, ui.cText, true));
        card.addView(ui.vSpace(8));

        TextView[] outLbl = new TextView[1];
        widthSb = addSlider(card, "Độ rộng / độ vươn (dp)", 24, 80, animWidth, outLbl);
        widthVal = outLbl[0];
        card.addView(ui.vSpace(8));

        lengthSb = addSlider(card, "Độ dài / bề rộng dải (dp)", 80, 220, animLength, outLbl);
        lengthVal = outLbl[0];
        card.addView(ui.vSpace(8));

        alphaSb = addSlider(card, "Độ trong suốt (%)", 15, 100, animAlpha, outLbl);
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
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(30));
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
        if (v == backBtn || v == cancelBtn) {
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
        } else if (v == customColorBtn) {
            showHexDialog();
        } else if (v == applyBtn) {
            saveAndApply();
        }
    }

    private void showHexDialog() {
        EditText et = new EditText(this);
        et.setText(ConfigBackup.formatHex(animColor));
        et.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Nhập mã màu Hex (#RRGGBB)")
                .setView(et)
                .setPositiveButton("Chọn", new HexPickListener(this, et))
                .setNegativeButton("Hủy", null)
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
        Toast.makeText(this, "Đã áp dụng cấu hình Animation", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private static final class PreviewCanvas extends View {
        private final AnimConfigActivity act;
        private final float density;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        PreviewCanvas(AnimConfigActivity act) {
            super(act);
            this.act = act;
            this.density = act.getResources().getDisplayMetrics().density;
            fillPaint.setStyle(Paint.Style.FILL);
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);
            iconPaint.setStrokeWidth(2.4f * density);
            iconPaint.setColor(0xFFFFFFFF);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(12f * density);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            String badge = act.simEdge == StripView.BOTTOM ? (act.simHold ? "REC" : "HOME") : "PWR";
            EdgeAnimView.drawIndicator(canvas, w, h, act.simEdge, density,
                    act.animColor, act.animWidth, act.animLength, act.animAlpha,
                    1.0f, w / 2f, h / 2f, true, act.simHold, badge,
                    fillPaint, iconPaint, textPaint, path);
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

    private static final class HexPickListener implements DialogInterface.OnClickListener {
        private final AnimConfigActivity act;
        private final EditText et;

        HexPickListener(AnimConfigActivity act, EditText et) {
            this.act = act;
            this.et = et;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            act.animColor = ConfigBackup.parseHex(et.getText().toString(), act.animColor);
            act.syncUi();
        }
    }
}
