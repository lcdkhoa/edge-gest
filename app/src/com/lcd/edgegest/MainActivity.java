package com.lcd.edgegest;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Màn hình cấu hình theo nhóm options có collapsible.
 * Tuân thủ ràng buộc d8 34.0.0: chỉ dùng static nested class với constructor tường minh,
 * không dùng lambda, anonymous class hay non-static inner class.
 */
public final class MainActivity extends Activity implements View.OnClickListener {
    private boolean dark;
    private int cBg, cCard, cSub, cBorder, cText, cMuted, cPrimary;
    private int cOkFg, cOkBg, cWarnFg, cWarnBg, cErrFg, cErrBg;

    private TextView overallBadge;
    private TextView ovVal, svcVal, helperVal, shzVal;
    private TextView permSummary, geomSummary, debugSummary;
    private TextView ovActionLabel, helperDesc;
    private LinearLayout permBody;
    private TextView permChevron;

    private EditText sideEt, bottomEt, topGuardEt, bottomGapEt;
    private Switch debugSw;
    private Button applyBtn, stopBtn, grantBtn, shizukuBtn, appInfoBtn, resetBtn;

    Handler uiHandler;
    private int shizukuState = ShizukuStarter.STATE_NOT_RUNNING;
    private boolean autoHelperTried;
    private boolean autoPermCollapsedOnce;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().hide();
        uiHandler = new Handler(Looper.getMainLooper());
        initPalette();

        SharedPreferences prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(cBg);
        if (Build.VERSION.SDK_INT >= 20) {
            scroll.setOnApplyWindowInsetsListener(new InsetPadding());
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, dp(28));

        buildHeroCard(root);
        root.addView(vSpace(12));
        buildPermissionsSection(root);
        root.addView(vSpace(10));
        buildGeometrySection(root, prefs);
        root.addView(vSpace(10));
        buildVisualSection(root, prefs);
        root.addView(vSpace(10));
        buildDiagnosticsSection(root);

        scroll.addView(root);
        setContentView(scroll);

        if (Settings.canDrawOverlays(this) && !OverlayService.running) {
            startOverlayService();
            uiHandler.postDelayed(new RefreshTask(this), 250);
        }
        ShizukuStarter.setOnBinderListener(new BinderArrivedTask(this));
        new Thread(new AutoStartHelperTask(this), "auto-shizuku").start();
    }

    @Override
    protected void onDestroy() {
        ShizukuStarter.setOnBinderListener(null);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void initPalette() {
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
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

    private void buildHeroCard(LinearLayout root) {
        LinearLayout card = makeCard();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("EdgeGest", 20, cText, true);
        TextView sub = text("Điều hướng mép · Không Root / Accessibility", 12, cMuted, false);
        sub.setPadding(0, dp(2), 0, 0);
        titleBox.addView(title);
        titleBox.addView(sub);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        overallBadge = pill("Đang kiểm tra", cMuted, cSub);
        header.addView(overallBadge);
        card.addView(header);
        card.addView(vSpace(12));

        // Lưới trạng thái 2x2
        TextView[] out = new TextView[1];
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(statusCell("Overlay", out), rowCellLp(true));
        ovVal = out[0];
        row1.addView(statusCell("Dải mép", out), rowCellLp(false));
        svcVal = out[0];
        card.addView(row1);
        card.addView(vSpace(8));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(statusCell("Helper :47820", out), rowCellLp(true));
        helperVal = out[0];
        row2.addView(statusCell("Shizuku", out), rowCellLp(false));
        shzVal = out[0];
        card.addView(row2);
        card.addView(vSpace(14));

        // Hàng nút chính
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        applyBtn = actionButton("BẬT / Áp dụng", cPrimary, Color.WHITE, 0);
        applyBtn.setOnClickListener(this);
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(0, dp(42), 2f);
        applyLp.rightMargin = dp(8);
        actions.addView(applyBtn, applyLp);

        stopBtn = actionButton("TẮT", cSub, cText, cBorder);
        stopBtn.setOnClickListener(this);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        actions.addView(stopBtn, stopLp);

        card.addView(actions);
        root.addView(card);
    }

    private void buildPermissionsSection(LinearLayout root) {
        TextView[] badgeOut = new TextView[1];
        TextView[] chevOut = new TextView[1];
        permBody = makeCollapsibleSection(root, "Quyền & Helper",
                "Overlay, Shizuku, khởi chạy injector", badgeOut, chevOut, true);
        permSummary = badgeOut[0];
        permChevron = chevOut[0];

        // Row 1: Overlay permission
        LinearLayout r1 = optionRow("Hiển thị trên ứng dụng khác", "App-op SYSTEM_ALERT_WINDOW");
        ovActionLabel = pill("Đã cấp ✓", cOkFg, cOkBg);
        grantBtn = compactButton("Cấp quyền", cPrimary, Color.WHITE, 0);
        grantBtn.setOnClickListener(this);
        r1.addView(ovActionLabel);
        r1.addView(grantBtn);
        permBody.addView(r1);
        permBody.addView(divider());

        // Row 2: Shizuku & Helper
        LinearLayout r2 = optionRow("Helper Injector (--server --hybrid)", "Khởi chạy qua Shizuku uid 2000");
        helperDesc = (TextView) ((LinearLayout) r2.getChildAt(0)).getChildAt(1);
        shizukuBtn = compactButton("Bật Helper", cSub, cText, cBorder);
        shizukuBtn.setOnClickListener(this);
        r2.addView(shizukuBtn);
        permBody.addView(r2);
        permBody.addView(divider());

        // Row 3: HyperOS Autostart
        LinearLayout r3 = optionRow("Tự khởi chạy HyperOS", "Bật Autostart trong Thông tin ứng dụng");
        appInfoBtn = compactButton("Mở cài đặt", cSub, cText, cBorder);
        appInfoBtn.setOnClickListener(this);
        r3.addView(appInfoBtn);
        permBody.addView(r3);
    }

    private void buildGeometrySection(LinearLayout root, SharedPreferences prefs) {
        TextView[] badgeOut = new TextView[1];
        TextView[] chevOut = new TextView[1];
        LinearLayout body = makeCollapsibleSection(root, "Kích thước & Vùng mép",
                "Độ dày dải vuốt và vùng chừa an toàn", badgeOut, chevOut, true);
        geomSummary = badgeOut[0];

        EditText[] etOut = new EditText[1];
        body.addView(stepperRow("Độ dày mép trái / phải", "Vuốt vào để Back (8–120 dp)",
                prefs.getInt("side", OverlayService.DEF_SIDE), 8, 120, 4, etOut));
        sideEt = etOut[0];

        body.addView(divider());
        body.addView(stepperRow("Độ dày mép dưới", "Home / Recents / Đổi app (8–120 dp)",
                prefs.getInt("bottom", OverlayService.DEF_BOTTOM), 8, 120, 2, etOut));
        bottomEt = etOut[0];

        body.addView(divider());
        body.addView(stepperRow("Chừa mép trên (%)", "Tránh kéo nhầm thanh thông báo (0–50%)",
                prefs.getInt("topGuard", OverlayService.DEF_TOP_GUARD), 0, 50, 1, etOut));
        topGuardEt = etOut[0];

        body.addView(divider());
        body.addView(stepperRow("Chừa thêm trên đáy (dp)", "Thu ngắn dải bên, tránh bàn phím (0–400)",
                prefs.getInt("bottomGap", OverlayService.DEF_BOTTOM_GAP), 0, 400, 10, etOut));
        bottomGapEt = etOut[0];

        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.END);
        foot.setPadding(0, dp(8), 0, 0);
        resetBtn = new Button(this);
        resetBtn.setText("Khôi phục mặc định (40 / 24 / 11% / 0)");
        resetBtn.setAllCaps(false);
        resetBtn.setTextSize(12);
        resetBtn.setTextColor(cPrimary);
        resetBtn.setBackgroundColor(Color.TRANSPARENT);
        resetBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        resetBtn.setOnClickListener(this);
        foot.addView(resetBtn);
        body.addView(foot);

        GeometryWatcher watcher = new GeometryWatcher(this);
        sideEt.addTextChangedListener(watcher);
        bottomEt.addTextChangedListener(watcher);
        topGuardEt.addTextChangedListener(watcher);
        bottomGapEt.addTextChangedListener(watcher);
        updateGeometrySummary();
    }

    private void buildVisualSection(LinearLayout root, SharedPreferences prefs) {
        TextView[] badgeOut = new TextView[1];
        TextView[] chevOut = new TextView[1];
        LinearLayout body = makeCollapsibleSection(root, "Hiển thị & Cử chỉ",
                "Màu debug dải mép và bảng cử chỉ", badgeOut, chevOut, false);
        debugSummary = badgeOut[0];

        LinearLayout row = optionRow("Hiện màu dải cảm ứng (Debug)",
                "Tô đỏ / xanh lá / xanh dương để canh mép");
        debugSw = new Switch(this);
        boolean dbg = prefs.getBoolean("debug", OverlayService.DEF_DEBUG);
        debugSw.setChecked(dbg);
        debugSw.setOnCheckedChangeListener(new DebugSwitchListener(this));
        row.addView(debugSw);
        body.addView(row);
        updateDebugBadge(dbg);

        body.addView(vSpace(10));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundRect(cSub, cBorder, 10));
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.addView(gestureLine("Vuốt mép trái / phải vào trong", "Back"));
        box.addView(gestureLine("Vuốt lên từ mép dưới (nhanh)", "Home"));
        box.addView(gestureLine("Vuốt lên giữ ≥ 300ms (rung)", "Recents"));
        box.addView(gestureLine("Vuốt ngang ở mép dưới", "App trước"));
        body.addView(box);
    }

    private void buildDiagnosticsSection(LinearLayout root) {
        TextView[] badgeOut = new TextView[1];
        TextView[] chevOut = new TextView[1];
        LinearLayout body = makeCollapsibleSection(root, "Chẩn đoán & Khẩn cấp",
                "Fallback ngân hàng, lệnh ADB & reboot", badgeOut, chevOut, false);
        badgeOut[0].setText("ADB");

        TextView p1 = text("• Khi app ngân hàng ẩn overlay (HIDE_NON_SYSTEM_OVERLAY_WINDOWS), chế độ --hybrid tự chuyển sang đọc /dev/input.", 12, cMuted, false);
        p1.setPadding(0, dp(2), 0, dp(8));
        body.addView(p1);

        TextView cmd = text(".\\run.ps1 fsg 0   # hiện lại thanh 3 nút khẩn cấp\n.\\run.ps1 grant   # cấp quyền qua ADB", 11, cText, false);
        cmd.setTypeface(Typeface.MONOSPACE);
        cmd.setBackground(roundRect(cSub, cBorder, 8));
        cmd.setPadding(dp(10), dp(8), dp(10), dp(8));
        body.addView(cmd);

        TextView p2 = text("Lưu ý reboot: bật Start on boot trong Shizuku (hoặc mở Shizuku sau khi khởi động lại) để EdgeGest tự chạy Helper.", 12, cMuted, false);
        p2.setPadding(0, dp(8), 0, 0);
        body.addView(p2);
    }

    // ---- Builders ----

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(cCard, cBorder, 16));
        int p = dp(16);
        card.setPadding(p, p, p, p);
        return card;
    }

    private LinearLayout makeCollapsibleSection(LinearLayout root, String titleStr, String subStr,
            TextView[] badgeOut, TextView[] chevOut, boolean expanded) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(cCard, cBorder, 16));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(13), dp(16), dp(13));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text(titleStr, 15, cText, true));
        TextView sub = text(subStr, 12, cMuted, false);
        sub.setPadding(0, dp(1), 0, 0);
        left.addView(sub);
        header.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = pill("", cMuted, cSub);
        badge.setTypeface(Typeface.MONOSPACE);
        header.addView(badge);
        badgeOut[0] = badge;

        TextView chev = text(expanded ? "▾" : "▸", 16, cMuted, true);
        chev.setPadding(dp(8), 0, 0, 0);
        header.addView(chev);
        chevOut[0] = chev;

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(4), dp(16), dp(14));
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);

        header.setOnClickListener(new SectionToggle(body, chev));
        card.addView(header);
        card.addView(body);
        root.addView(card);
        return body;
    }

    private LinearLayout statusCell(String label, TextView[] valOut) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setBackground(roundRect(cSub, cBorder, 12));
        cell.setPadding(dp(12), dp(9), dp(12), dp(9));
        cell.addView(text(label, 11, cMuted, false));
        TextView v = text("...", 13, cText, true);
        v.setPadding(0, dp(2), 0, 0);
        cell.addView(v);
        valOut[0] = v;
        return cell;
    }

    private LinearLayout.LayoutParams rowCellLp(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (left) lp.rightMargin = dp(8);
        return lp;
    }

    private LinearLayout optionRow(String titleStr, String subStr) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(0, 0, dp(10), 0);
        left.addView(text(titleStr, 13, cText, true));
        TextView sub = text(subStr, 11, cMuted, false);
        sub.setPadding(0, dp(1), 0, 0);
        left.addView(sub);
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private LinearLayout stepperRow(String titleStr, String subStr, int val,
            int min, int max, int step, EditText[] outEt) {
        LinearLayout row = optionRow(titleStr, subStr);

        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(val));
        et.setTextSize(13);
        et.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        et.setTextColor(cText);
        et.setGravity(Gravity.CENTER);
        et.setBackground(roundRect(cSub, cBorder, 8));
        et.setPadding(dp(4), dp(4), dp(4), dp(4));

        Button minus = stepButton("−");
        minus.setOnClickListener(new StepAction(et, -step, min, max));
        Button plus = stepButton("+");
        plus.setOnClickListener(new StepAction(et, step, min, max));

        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.HORIZONTAL);
        ctrl.setGravity(Gravity.CENTER_VERTICAL);
        ctrl.addView(minus, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(dp(50), dp(32));
        etLp.leftMargin = dp(4);
        etLp.rightMargin = dp(4);
        ctrl.addView(et, etLp);
        ctrl.addView(plus, new LinearLayout.LayoutParams(dp(32), dp(32)));

        row.addView(ctrl);
        outEt[0] = et;
        return row;
    }

    private LinearLayout gestureLine(String gesture, String action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(3), 0, dp(3));
        TextView l = text(gesture, 12, cMuted, false);
        r.addView(l, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView a = text(action, 12, cText, true);
        a.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        r.addView(a);
        return r;
    }

    private Button actionButton(String label, int bg, int fg, int stroke) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(fg);
        b.setBackground(roundRect(bg, stroke, 12));
        b.setStateListAnimator(null);
        return b;
    }

    private Button compactButton(String label, int bg, int fg, int stroke) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(fg);
        b.setBackground(roundRect(bg, stroke, 10));
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setMinHeight(dp(34));
        b.setMinimumHeight(dp(34));
        b.setStateListAnimator(null);
        return b;
    }

    private Button stepButton(String sym) {
        Button b = new Button(this);
        b.setText(sym);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(cText);
        b.setBackground(roundRect(cSub, cBorder, 8));
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        return b;
    }

    private TextView pill(String s, int fg, int bg) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(fg);
        t.setBackground(roundRect(bg, 0, 99));
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        return t;
    }

    private void setPill(TextView t, String s, int fg, int bg) {
        t.setText(s);
        t.setTextColor(fg);
        t.setBackground(roundRect(bg, 0, 99));
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(cBorder);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        v.setLayoutParams(lp);
        return v;
    }

    private View vSpace(int dpVal) {
        View v = new View(this);
        v.setMinimumHeight(dp(dpVal));
        return v;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    // ---- Actions & State ----

    @Override
    public void onClick(View v) {
        if (v == grantBtn) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else if (v == shizukuBtn) {
            onShizukuAction();
        } else if (v == appInfoBtn) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } else if (v == applyBtn) {
            applyAndStart();
        } else if (v == stopBtn) {
            stopService(new Intent(this, OverlayService.class));
            OverlayService.running = false;
            refresh();
            Toast.makeText(this, "Đã tắt overlay", Toast.LENGTH_SHORT).show();
        } else if (v == resetBtn) {
            sideEt.setText(String.valueOf(OverlayService.DEF_SIDE));
            bottomEt.setText(String.valueOf(OverlayService.DEF_BOTTOM));
            topGuardEt.setText(String.valueOf(OverlayService.DEF_TOP_GUARD));
            bottomGapEt.setText(String.valueOf(OverlayService.DEF_BOTTOM_GAP));
            updateGeometrySummary();
            Toast.makeText(this, "Đã đặt lại mặc định — bấm BẬT / Áp dụng để lưu", Toast.LENGTH_SHORT).show();
        }
    }

    private void onShizukuAction() {
        if (shizukuState == ShizukuStarter.STATE_NOT_RUNNING) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(ShizukuStarter.SHIZUKU_PKG);
            if (launch != null) {
                startActivity(launch);
            } else {
                Toast.makeText(this, "Chưa cài Shizuku", Toast.LENGTH_SHORT).show();
            }
        } else if (shizukuState == ShizukuStarter.STATE_NO_PERMISSION) {
            new Thread(new RequestShizukuPermTask(this), "shizuku-perm").start();
        } else {
            shizukuBtn.setEnabled(false);
            Toast.makeText(this, "Đang bật helper qua Shizuku...", Toast.LENGTH_SHORT).show();
            new Thread(new StartHelperViaShizukuTask(this), "shizuku-start").start();
        }
    }

    private void applyAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Hãy cấp quyền overlay trước", Toast.LENGTH_LONG).show();
            return;
        }
        SharedPreferences.Editor e = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE).edit();
        e.putInt("side", parse(sideEt, OverlayService.DEF_SIDE));
        e.putInt("bottom", parse(bottomEt, OverlayService.DEF_BOTTOM));
        e.putInt("topGuard", parse(topGuardEt, OverlayService.DEF_TOP_GUARD));
        e.putInt("bottomGap", parse(bottomGapEt, OverlayService.DEF_BOTTOM_GAP));
        e.putBoolean("debug", debugSw.isChecked());
        e.apply();

        startOverlayService();
        autoHelperTried = false;
        refresh();
        uiHandler.postDelayed(new RefreshTask(this), 250);
        Toast.makeText(this, "Đã áp dụng cấu hình", Toast.LENGTH_SHORT).show();
    }

    private void startOverlayService() {
        Intent svc = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);
    }

    void refresh() {
        boolean ov = Settings.canDrawOverlays(this);
        boolean svc = OverlayService.running;

        ovVal.setText(ov ? "ĐÃ CẤP ✓" : "CHƯA CẤP ✗");
        ovVal.setTextColor(ov ? cOkFg : cErrFg);

        svcVal.setText(svc ? "ĐANG CHẠY ✓" : "ĐÃ TẮT ✗");
        svcVal.setTextColor(svc ? cOkFg : cWarnFg);

        ovActionLabel.setVisibility(ov ? View.VISIBLE : View.GONE);
        grantBtn.setVisibility(ov ? View.GONE : View.VISIBLE);

        helperVal.setText("Kiểm tra...");
        helperVal.setTextColor(cMuted);
        shzVal.setText("Kiểm tra...");
        shzVal.setTextColor(cMuted);
        new Thread(new HelperProbe(this), "helper-probe").start();
    }

    void applyProbeResult(boolean alive, int shzState) {
        if (isFinishing() || isDestroyed()) return;
        shizukuState = shzState;
        boolean ov = Settings.canDrawOverlays(this);
        boolean svc = OverlayService.running;

        helperVal.setText(alive ? "SỐNG ✓" : "CHƯA CHẠY ✗");
        helperVal.setTextColor(alive ? cOkFg : cErrFg);

        shizukuBtn.setEnabled(true);
        if (shzState == ShizukuStarter.STATE_NOT_RUNNING) {
            shzVal.setText("CHƯA BẬT ✗");
            shzVal.setTextColor(cWarnFg);
            helperDesc.setText("Mở app Shizuku để kích hoạt hoặc chạy qua ADB");
            shizukuBtn.setText("Mở Shizuku");
            shizukuBtn.setVisibility(alive ? View.GONE : View.VISIBLE);
        } else if (shzState == ShizukuStarter.STATE_NO_PERMISSION) {
            shzVal.setText("CHƯA CẤP");
            shzVal.setTextColor(cWarnFg);
            helperDesc.setText("Cần cấp quyền Shizuku cho EdgeGest");
            shizukuBtn.setText("Cấp quyền Shizuku");
            shizukuBtn.setVisibility(View.VISIBLE);
        } else {
            shzVal.setText("SẴN SÀNG ✓");
            shzVal.setTextColor(cOkFg);
            helperDesc.setText("Khởi chạy qua Shizuku uid 2000");
            shizukuBtn.setText(alive ? "Khởi động lại" : "Bật Helper");
            shizukuBtn.setVisibility(View.VISIBLE);
            if (!alive && !autoHelperTried) {
                autoHelperTried = true;
                new Thread(new AutoStartHelperTask(this), "auto-shizuku").start();
            }
        }

        boolean allReady = ov && svc && alive;
        if (allReady) {
            setPill(overallBadge, "Đang hoạt động", cOkFg, cOkBg);
            setPill(permSummary, "4/4 OK", cOkFg, cOkBg);
            if (!autoPermCollapsedOnce) {
                autoPermCollapsedOnce = true;
                permBody.setVisibility(View.GONE);
                permChevron.setText("▸");
            }
        } else {
            setPill(overallBadge, svc ? "Thiếu Helper/Quyền" : "Đã tắt", cWarnFg, cWarnBg);
            setPill(permSummary, "Cần kiểm tra", cWarnFg, cWarnBg);
            permBody.setVisibility(View.VISIBLE);
            permChevron.setText("▾");
        }
    }

    void updateGeometrySummary() {
        int s = parse(sideEt, OverlayService.DEF_SIDE);
        int b = parse(bottomEt, OverlayService.DEF_BOTTOM);
        int tg = parse(topGuardEt, OverlayService.DEF_TOP_GUARD);
        int bg = parse(bottomGapEt, OverlayService.DEF_BOTTOM_GAP);
        geomSummary.setText(s + "·" + b + "·" + tg + "%·" + bg);
    }

    void updateDebugBadge(boolean on) {
        if (on) setPill(debugSummary, "Debug: BẬT", cWarnFg, cWarnBg);
        else setPill(debugSummary, "Debug: TẮT", cMuted, cSub);
    }

    private int parse(EditText et, int def) {
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (Exception e) { return def; }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---- Static nested classes (d8 34.0.0 compatibility) ----

    private static final class InsetPadding implements View.OnApplyWindowInsetsListener {
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

    private static final class SectionToggle implements View.OnClickListener {
        private final LinearLayout body;
        private final TextView chevron;

        SectionToggle(LinearLayout body, TextView chevron) {
            this.body = body;
            this.chevron = chevron;
        }

        @Override
        public void onClick(View v) {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            chevron.setText(show ? "▾" : "▸");
        }
    }

    private static final class StepAction implements View.OnClickListener {
        private final EditText et;
        private final int delta, min, max;

        StepAction(EditText et, int delta, int min, int max) {
            this.et = et;
            this.delta = delta;
            this.min = min;
            this.max = max;
        }

        @Override
        public void onClick(View v) {
            int cur = min;
            try { cur = Integer.parseInt(et.getText().toString().trim()); }
            catch (Exception ignored) {}
            int next = Math.max(min, Math.min(max, cur + delta));
            et.setText(String.valueOf(next));
        }
    }

    private static final class GeometryWatcher implements TextWatcher {
        private final MainActivity activity;

        GeometryWatcher(MainActivity activity) {
            this.activity = activity;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {
            activity.updateGeometrySummary();
        }
    }

    private static final class DebugSwitchListener implements CompoundButton.OnCheckedChangeListener {
        private final MainActivity activity;

        DebugSwitchListener(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            activity.updateDebugBadge(isChecked);
        }
    }

    private static final class HelperProbe implements Runnable {
        private final MainActivity activity;

        HelperProbe(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            boolean alive = ShizukuStarter.isHelperAlive();
            int shz = ShizukuStarter.getState(activity.getApplicationContext());
            activity.uiHandler.post(new HelperProbeResult(activity, alive, shz));
        }
    }

    private static final class HelperProbeResult implements Runnable {
        private final MainActivity activity;
        private final boolean alive;
        private final int shz;

        HelperProbeResult(MainActivity activity, boolean alive, int shz) {
            this.activity = activity;
            this.alive = alive;
            this.shz = shz;
        }

        @Override
        public void run() {
            activity.applyProbeResult(alive, shz);
        }
    }

    private static final class AutoStartHelperTask implements Runnable {
        private final MainActivity activity;

        AutoStartHelperTask(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            if (ShizukuStarter.isHelperAlive()) return;
            if (ShizukuStarter.startHelper(activity.getApplicationContext())) {
                activity.uiHandler.post(new RefreshTask(activity));
            }
        }
    }

    private static final class RequestShizukuPermTask implements Runnable {
        private final MainActivity activity;

        RequestShizukuPermTask(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            ShizukuStarter.requestPermission(activity.getApplicationContext(), 1001);
            activity.uiHandler.postDelayed(new RefreshTask(activity), 600);
        }
    }

    private static final class StartHelperViaShizukuTask implements Runnable {
        private final MainActivity activity;

        StartHelperViaShizukuTask(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            boolean ok = ShizukuStarter.startHelper(activity.getApplicationContext());
            activity.uiHandler.post(new HelperStartDone(activity, ok));
        }
    }

    private static final class HelperStartDone implements Runnable {
        private final MainActivity activity;
        private final boolean ok;

        HelperStartDone(MainActivity activity, boolean ok) {
            this.activity = activity;
            this.ok = ok;
        }

        @Override
        public void run() {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            Toast.makeText(activity,
                    ok ? "Helper server đã chạy" : "Không bật được helper (kiểm tra đã push-helper chưa)",
                    Toast.LENGTH_LONG).show();
            activity.refresh();
        }
    }

    private static final class RefreshTask implements Runnable {
        private final MainActivity activity;

        RefreshTask(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                activity.refresh();
            }
        }
    }

    private static final class BinderArrivedTask implements Runnable {
        private final MainActivity activity;

        BinderArrivedTask(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            activity.uiHandler.post(new RefreshTask(activity));
        }
    }
}
