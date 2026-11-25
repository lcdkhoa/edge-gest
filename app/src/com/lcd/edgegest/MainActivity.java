package com.lcd.edgegest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Màn hình cấu hình theo nhóm options có collapsible.
 * Tuân thủ ràng buộc d8 34.0.0: chỉ dùng static nested class với constructor tường minh,
 * không dùng lambda, anonymous class hay non-static inner class.
 */
public final class MainActivity extends Activity implements View.OnClickListener {
    private static final String CMD_ADB_START = "adb shell sh /data/local/tmp/edgegest/start-server.sh";
    private static final String CMD_ADB_GRANT = "adb shell \"appops set com.lcd.edgegest SYSTEM_ALERT_WINDOW allow; "
            + "pm grant com.lcd.edgegest moe.shizuku.manager.permission.API_V23; "
            + "pm grant com.lcd.edgegest android.permission.POST_NOTIFICATIONS; "
            + "pm grant com.lcd.edgegest android.permission.ACCESS_LOCAL_NETWORK\"";
    private static final String CMD_ADB_FSG0 = "adb shell settings put global force_fsg_nav_bar 0";

    private boolean dark;
    private int cBg, cCard, cSub, cBorder, cText, cMuted, cPrimary;
    private int cOkFg, cOkBg, cWarnFg, cWarnBg, cErrFg, cErrBg;

    private TextView overallBadge;
    private TextView ovVal, svcVal, helperVal, shzVal;
    private TextView permSummary, geomSummary, holdSummary, visualSummary;
    private TextView ovActionLabel, helperDesc;
    private TextView leftHoldDesc, rightHoldDesc;
    private LinearLayout permBody;
    private ChevronView permChevron;

    private EditText sideEt, bottomEt, topGuardEt, bottomGapEt, holdMsEt;
    private Switch animSw, debugSw;
    private Button applyBtn, stopBtn, grantBtn, shizukuBtn, appInfoBtn, resetBtn;
    private Button leftHoldBtn, rightHoldBtn;

    Handler uiHandler;
    String leftHoldAction = GestureActions.NONE;
    String rightHoldAction = GestureActions.NONE;
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
        leftHoldAction = prefs.getString("leftHold", OverlayService.DEF_LEFT_HOLD);
        rightHoldAction = prefs.getString("rightHold", OverlayService.DEF_RIGHT_HOLD);

        ScrollView scroll = new ScrollView(this);
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
        buildHoldActionsSection(root, prefs);
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
        ChevronView[] chevOut = new ChevronView[1];
        permBody = makeCollapsibleSection(root, "Quyền & Helper",
                "Overlay, Shizuku, khởi chạy injector", badgeOut, chevOut, true);
        permSummary = badgeOut[0];
        permChevron = chevOut[0];

        LinearLayout r1 = optionRow("Hiển thị trên ứng dụng khác", "App-op SYSTEM_ALERT_WINDOW");
        ovActionLabel = pill("Đã cấp ✓", cOkFg, cOkBg);
        grantBtn = compactButton("Cấp quyền", cPrimary, Color.WHITE, 0);
        grantBtn.setOnClickListener(this);
        r1.addView(ovActionLabel);
        r1.addView(grantBtn);
        permBody.addView(r1);
        permBody.addView(divider());

        LinearLayout r2 = optionRow("Helper Injector (--server --hybrid)", "Khởi chạy qua Shizuku uid 2000");
        helperDesc = (TextView) ((LinearLayout) r2.getChildAt(0)).getChildAt(1);
        shizukuBtn = compactButton("Bật Helper", cSub, cText, cBorder);
        shizukuBtn.setOnClickListener(this);
        r2.addView(shizukuBtn);
        permBody.addView(r2);
        permBody.addView(divider());

        LinearLayout r3 = optionRow("Tự khởi chạy HyperOS", "Bật Autostart trong Thông tin ứng dụng");
        appInfoBtn = compactButton("Mở cài đặt", cSub, cText, cBorder);
        appInfoBtn.setOnClickListener(this);
        r3.addView(appInfoBtn);
        permBody.addView(r3);
    }

    private void buildGeometrySection(LinearLayout root, SharedPreferences prefs) {
        TextView[] badgeOut = new TextView[1];
        ChevronView[] chevOut = new ChevronView[1];
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

    private void buildHoldActionsSection(LinearLayout root, SharedPreferences prefs) {
        TextView[] badgeOut = new TextView[1];
        ChevronView[] chevOut = new ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root, "Cử chỉ & Nhấn giữ",
                "Kéo giữ mép trái / phải: mở app, nguồn, âm lượng...", badgeOut, chevOut, false);
        holdSummary = badgeOut[0];

        LinearLayout rLeft = optionRow("Nhấn giữ mép trái", GestureActions.describe(this, leftHoldAction));
        leftHoldDesc = (TextView) ((LinearLayout) rLeft.getChildAt(0)).getChildAt(1);
        leftHoldBtn = compactButton("Chọn", cSub, cText, cBorder);
        leftHoldBtn.setOnClickListener(this);
        rLeft.addView(leftHoldBtn);
        body.addView(rLeft);
        body.addView(divider());

        LinearLayout rRight = optionRow("Nhấn giữ mép phải", GestureActions.describe(this, rightHoldAction));
        rightHoldDesc = (TextView) ((LinearLayout) rRight.getChildAt(0)).getChildAt(1);
        rightHoldBtn = compactButton("Chọn", cSub, cText, cBorder);
        rightHoldBtn.setOnClickListener(this);
        rRight.addView(rightHoldBtn);
        body.addView(rRight);
        body.addView(divider());

        EditText[] etOut = new EditText[1];
        body.addView(stepperRow("Thời gian nhấn giữ (ms)", "Mốc kích hoạt hành động phụ & Recents (180–800)",
                prefs.getInt("holdMs", OverlayService.DEF_HOLD_MS), 180, 800, 20, etOut));
        holdMsEt = etOut[0];
        updateHoldSummary();
    }

    private void buildVisualSection(LinearLayout root, SharedPreferences prefs) {
        TextView[] badgeOut = new TextView[1];
        ChevronView[] chevOut = new ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root, "Hiển thị & Hiệu ứng",
                "Animation khi vuốt, màu debug & bảng cử chỉ", badgeOut, chevOut, false);
        visualSummary = badgeOut[0];

        LinearLayout rAnim = optionRow("Hiệu ứng animation khi vuốt",
                "Hiện dải cong & biểu tượng khi kéo mép");
        animSw = new Switch(this);
        boolean anim = prefs.getBoolean("anim", OverlayService.DEF_ANIM);
        animSw.setChecked(anim);
        animSw.setOnCheckedChangeListener(new VisualSwitchListener(this));
        rAnim.addView(animSw);
        body.addView(rAnim);
        body.addView(divider());

        LinearLayout rDbg = optionRow("Hiện màu dải cảm ứng (Debug)",
                "Tô đỏ / xanh lá / xanh dương cố định để canh mép");
        debugSw = new Switch(this);
        boolean dbg = prefs.getBoolean("debug", OverlayService.DEF_DEBUG);
        debugSw.setChecked(dbg);
        debugSw.setOnCheckedChangeListener(new VisualSwitchListener(this));
        rDbg.addView(debugSw);
        body.addView(rDbg);
        updateVisualBadge();

        body.addView(vSpace(10));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundRect(cSub, cBorder, 10));
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.addView(gestureLine("Vuốt mép trái / phải (nhanh)", "Back"));
        box.addView(gestureLine("Vuốt mép trái / phải rồi giữ", "Mở hành động phụ"));
        box.addView(gestureLine("Vuốt lên từ mép dưới (nhanh)", "Home"));
        box.addView(gestureLine("Vuốt lên giữ (rung nhẹ)", "Recents"));
        box.addView(gestureLine("Vuốt ngang ở mép dưới", "App trước"));
        body.addView(box);
    }

    private void buildDiagnosticsSection(LinearLayout root) {
        TextView[] badgeOut = new TextView[1];
        ChevronView[] chevOut = new ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root, "Chẩn đoán & Lệnh ADB",
                "Chạy không cần Shizuku, copy lệnh nhanh & khẩn cấp", badgeOut, chevOut, false);
        badgeOut[0].setText("ADB");

        TextView p1 = text("• Trong app ngân hàng ẩn overlay, chế độ --hybrid tự chuyển sang đọc /dev/input.\n• Nếu máy chưa bật Shizuku, cắm USB hoặc ADB Wi-Fi và chạy lệnh bên dưới:", 12, cMuted, false);
        p1.setPadding(0, dp(2), 0, dp(10));
        body.addView(p1);

        body.addView(adbCommandCard("1. Bật Helper không cần Shizuku (chạy sau khi reboot):", CMD_ADB_START));
        body.addView(vSpace(8));
        body.addView(adbCommandCard("2. Cấp toàn bộ quyền APK qua ADB (1 lần):", CMD_ADB_GRANT));
        body.addView(vSpace(8));
        body.addView(adbCommandCard("3. Khẩn cấp hiện lại thanh 3 nút:", CMD_ADB_FSG0));
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
            TextView[] badgeOut, ChevronView[] chevOut, boolean expanded) {
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

        ChevronView chev = new ChevronView(this, cMuted, dp(18), dp(2));
        chev.setExpanded(expanded, false);
        LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        chevLp.leftMargin = dp(10);
        header.addView(chev, chevLp);
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

    private LinearLayout adbCommandCard(String label, String cmd) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(roundRect(cSub, cBorder, 10));
        wrap.setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(label, 11, cMuted, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button copy = compactButton("Copy", cCard, cPrimary, cBorder);
        copy.setOnClickListener(new CopyAction(this, cmd));
        top.addView(copy);
        wrap.addView(top);

        TextView code = text(cmd, 11, cText, false);
        code.setTypeface(Typeface.MONOSPACE);
        code.setPadding(0, dp(6), 0, 0);
        wrap.addView(code);
        return wrap;
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
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(dp(52), dp(32));
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
        b.setPadding(dp(12), dp(5), dp(12), dp(5));
        b.setMinHeight(dp(32));
        b.setMinimumHeight(dp(32));
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
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(dpVal)));
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
        } else if (v == leftHoldBtn) {
            showHoldActionPicker(true);
        } else if (v == rightHoldBtn) {
            showHoldActionPicker(false);
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

    private void showHoldActionPicker(boolean isLeft) {
        String current = isLeft ? leftHoldAction : rightHoldAction;
        int checked = 0;
        for (int i = 0; i < GestureActions.KEYS.length; i++) {
            String k = GestureActions.KEYS[i];
            if (k.equals(current) || (GestureActions.APP_PREFIX.equals(k)
                    && current != null && current.startsWith(GestureActions.APP_PREFIX))) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(isLeft ? "Nhấn giữ mép trái" : "Nhấn giữ mép phải")
                .setSingleChoiceItems(GestureActions.LABELS, checked, new ActionPickListener(this, isLeft))
                .setNegativeButton("Đóng", null)
                .show();
    }

    void onHoldActionPicked(boolean isLeft, int index, DialogInterface dialog) {
        dialog.dismiss();
        String key = GestureActions.KEYS[index];
        if (GestureActions.APP_PREFIX.equals(key)) {
            Toast.makeText(this, "Đang tải danh sách app...", Toast.LENGTH_SHORT).show();
            new Thread(new LoadAppsTask(this, isLeft), "load-apps").start();
            return;
        }
        setHoldAction(isLeft, key);
    }

    void setHoldAction(boolean isLeft, String action) {
        if (isLeft) leftHoldAction = action;
        else rightHoldAction = action;
        leftHoldDesc.setText(GestureActions.describe(this, leftHoldAction));
        rightHoldDesc.setText(GestureActions.describe(this, rightHoldAction));
        updateHoldSummary();
    }

    void showAppPicker(boolean isLeft, String[] labels, String[] pkgs) {
        if (isFinishing() || isDestroyed()) return;
        if (pkgs.length == 0) {
            Toast.makeText(this, "Không tìm thấy ứng dụng nào", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(isLeft ? "Chọn app cho mép trái" : "Chọn app cho mép phải")
                .setItems(labels, new AppPickListener(this, isLeft, pkgs))
                .setNegativeButton("Huỷ", null)
                .show();
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
        e.putInt("holdMs", parse(holdMsEt, OverlayService.DEF_HOLD_MS));
        e.putBoolean("anim", animSw.isChecked());
        e.putBoolean("debug", debugSw.isChecked());
        e.putString("leftHold", leftHoldAction);
        e.putString("rightHold", rightHoldAction);
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
            helperDesc.setText("Mở Shizuku hoặc chạy lệnh ADB bên dưới");
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
                permChevron.setExpanded(false, false);
            }
        } else {
            setPill(overallBadge, svc ? "Thiếu Helper/Quyền" : "Đã tắt", cWarnFg, cWarnBg);
            setPill(permSummary, "Cần kiểm tra", cWarnFg, cWarnBg);
            permBody.setVisibility(View.VISIBLE);
            permChevron.setExpanded(true, false);
        }
    }

    void updateGeometrySummary() {
        int s = parse(sideEt, OverlayService.DEF_SIDE);
        int b = parse(bottomEt, OverlayService.DEF_BOTTOM);
        int tg = parse(topGuardEt, OverlayService.DEF_TOP_GUARD);
        int bg = parse(bottomGapEt, OverlayService.DEF_BOTTOM_GAP);
        geomSummary.setText(s + "·" + b + "·" + tg + "%·" + bg);
    }

    void updateHoldSummary() {
        boolean l = GestureActions.isEnabled(leftHoldAction);
        boolean r = GestureActions.isEnabled(rightHoldAction);
        if (!l && !r) {
            setPill(holdSummary, "Chỉ Back", cMuted, cSub);
        } else {
            String lb = l ? GestureActions.shortBadge(leftHoldAction) : "—";
            String rb = r ? GestureActions.shortBadge(rightHoldAction) : "—";
            setPill(holdSummary, "L:" + lb + " R:" + rb, cPrimary, cSub);
        }
    }

    void updateVisualBadge() {
        boolean a = animSw.isChecked();
        boolean d = debugSw.isChecked();
        if (d) {
            setPill(visualSummary, (a ? "Anim · " : "") + "Debug BẬT", cWarnFg, cWarnBg);
        } else if (a) {
            setPill(visualSummary, "Anim: BẬT", cOkFg, cOkBg);
        } else {
            setPill(visualSummary, "Tắt hết", cMuted, cSub);
        }
    }

    private int parse(EditText et, int def) {
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (Exception e) { return def; }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---- Static nested classes (d8 34.0.0 compatibility) ----

    /** Vẽ icon mũi tên chevron nét stroke tròn đầu giống design (xoay mượt 0° mở ↔ -90° đóng). */
    private static final class ChevronView extends View {
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
        private final ChevronView chevron;

        SectionToggle(LinearLayout body, ChevronView chevron) {
            this.body = body;
            this.chevron = chevron;
        }

        @Override
        public void onClick(View v) {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            chevron.setExpanded(show, true);
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

    private static final class CopyAction implements View.OnClickListener {
        private final Context ctx;
        private final String cmd;

        CopyAction(Context ctx, String cmd) {
            this.ctx = ctx;
            this.cmd = cmd;
        }

        @Override
        public void onClick(View v) {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("EdgeGest ADB", cmd));
                Toast.makeText(ctx, "Đã copy lệnh vào bộ nhớ đệm", Toast.LENGTH_SHORT).show();
            }
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

    private static final class VisualSwitchListener implements CompoundButton.OnCheckedChangeListener {
        private final MainActivity activity;

        VisualSwitchListener(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            activity.updateVisualBadge();
        }
    }

    private static final class ActionPickListener implements DialogInterface.OnClickListener {
        private final MainActivity activity;
        private final boolean isLeft;

        ActionPickListener(MainActivity activity, boolean isLeft) {
            this.activity = activity;
            this.isLeft = isLeft;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            activity.onHoldActionPicked(isLeft, which, dialog);
        }
    }

    private static final class AppPickListener implements DialogInterface.OnClickListener {
        private final MainActivity activity;
        private final boolean isLeft;
        private final String[] pkgs;

        AppPickListener(MainActivity activity, boolean isLeft, String[] pkgs) {
            this.activity = activity;
            this.isLeft = isLeft;
            this.pkgs = pkgs;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            activity.setHoldAction(isLeft, GestureActions.APP_PREFIX + pkgs[which]);
        }
    }

    private static final class AppEntry {
        final String label;
        final String pkg;

        AppEntry(String label, String pkg) {
            this.label = label;
            this.pkg = pkg;
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class AppEntryComparator implements Comparator {
        AppEntryComparator() {}

        @Override
        public int compare(Object o1, Object o2) {
            AppEntry a = (AppEntry) o1;
            AppEntry b = (AppEntry) o2;
            return a.label.compareToIgnoreCase(b.label);
        }
    }

    private static final class LoadAppsTask implements Runnable {
        private final MainActivity activity;
        private final boolean isLeft;

        LoadAppsTask(MainActivity activity, boolean isLeft) {
            this.activity = activity;
            this.isLeft = isLeft;
        }

        @Override
        public void run() {
            PackageManager pm = activity.getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            List<AppEntry> entries = new ArrayList<>();
            for (ResolveInfo ri : list) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                CharSequence lbl = ri.loadLabel(pm);
                entries.add(new AppEntry(lbl != null ? lbl.toString() : pkg, pkg));
            }
            Collections.sort(entries, new AppEntryComparator());
            String[] labels = new String[entries.size()];
            String[] pkgs = new String[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                AppEntry e = entries.get(i);
                labels[i] = e.label + "  (" + e.pkg + ")";
                pkgs[i] = e.pkg;
            }
            activity.uiHandler.post(new ShowAppPickerTask(activity, isLeft, labels, pkgs));
        }
    }

    private static final class ShowAppPickerTask implements Runnable {
        private final MainActivity activity;
        private final boolean isLeft;
        private final String[] labels;
        private final String[] pkgs;

        ShowAppPickerTask(MainActivity activity, boolean isLeft, String[] labels, String[] pkgs) {
            this.activity = activity;
            this.isLeft = isLeft;
            this.labels = labels;
            this.pkgs = pkgs;
        }

        @Override
        public void run() {
            activity.showAppPicker(isLeft, labels, pkgs);
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
