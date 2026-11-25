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
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Màn hình cấu hình theo nhóm options có collapsible:
 * - Apply riêng từng phần hoặc qua thanh Hủy / Áp dụng ghim ở bottom
 * - Sub-screen AnimConfigActivity tuỳ chỉnh animation kéo mép
 * - Sao lưu / Khôi phục JSON chọn lọc theo từng phần cấu hình
 *
 * Tuân thủ ràng buộc d8 34.0.0: chỉ dùng static nested class với constructor tường minh.
 */
public final class MainActivity extends Activity implements View.OnClickListener {
    private static final int REQ_ANIM = 201;
    private static final int REQ_BACKUP_FILE = 202;
    private static final int REQ_RESTORE_FILE = 203;

    private static final String CMD_ADB_START = "adb shell sh /data/local/tmp/edgegest/start-server.sh";
    private static final String CMD_ADB_GRANT = "adb shell \"appops set com.lcd.edgegest SYSTEM_ALERT_WINDOW allow; "
            + "pm grant com.lcd.edgegest moe.shizuku.manager.permission.API_V23; "
            + "pm grant com.lcd.edgegest android.permission.POST_NOTIFICATIONS; "
            + "pm grant com.lcd.edgegest android.permission.ACCESS_LOCAL_NETWORK\"";
    private static final String CMD_ADB_FSG0 = "adb shell settings put global force_fsg_nav_bar 0";

    private UiKit ui;
    private TextView overallBadge;
    private TextView ovVal, svcVal, helperVal, shzVal;
    private TextView permSummary, geomSummary, holdSummary, visualSummary;
    private TextView ovActionLabel, helperDesc;
    private TextView leftHoldDesc, rightHoldDesc, animSubLine;
    private View animSwatchDot;
    private LinearLayout permBody;
    private UiKit.ChevronView permChevron;

    private EditText sideEt, bottomEt, topGuardEt, bottomGapEt, holdMsEt;
    private Switch debugSw;
    private CheckBox bkGeomCb, bkHoldCb, bkAnimCb;

    private Button svcOnBtn, svcOffBtn, grantBtn, shizukuBtn, appInfoBtn;
    private Button resetGeomBtn, applyGeomBtn, applyHoldBtn, applyVisualBtn;
    private Button leftHoldBtn, rightHoldBtn, openAnimBtn;
    private Button exportFileBtn, importFileBtn, copyJsonBtn, pasteJsonBtn;
    private Button bottomCancelBtn, bottomApplyBtn;

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
        ui = new UiKit(this);
        uiHandler = new Handler(Looper.getMainLooper());

        SharedPreferences prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(ui.cBg);
        if (Build.VERSION.SDK_INT >= 20) {
            outer.setOnApplyWindowInsetsListener(new UiKit.InsetPadding());
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = ui.dp(14);
        root.setPadding(pad, pad, pad, ui.dp(18));

        buildHeroCard(root);
        root.addView(ui.vSpace(10));
        buildPermissionsSection(root);
        root.addView(ui.vSpace(10));
        buildGeometrySection(root, prefs);
        root.addView(ui.vSpace(10));
        buildHoldActionsSection(root, prefs);
        root.addView(ui.vSpace(10));
        buildVisualSection(root, prefs);
        root.addView(ui.vSpace(10));
        buildBackupSection(root);
        root.addView(ui.vSpace(10));
        buildDiagnosticsSection(root);

        scroll.addView(root);
        outer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        View botDiv = new View(this);
        botDiv.setBackgroundColor(ui.cBorder);
        outer.addView(botDiv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, ui.dp(1))));

        bottomCancelBtn = ui.actionButton("Hủy", ui.cSub, ui.cText, ui.cBorder);
        bottomCancelBtn.setOnClickListener(this);
        bottomApplyBtn = ui.actionButton("Áp dụng tất cả", ui.cPrimary, Color.WHITE, 0);
        bottomApplyBtn.setOnClickListener(this);
        outer.addView(ui.makeBottomBar(bottomCancelBtn, bottomApplyBtn));

        setContentView(outer);
        loadFromPrefs();

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

    private void buildHeroCard(LinearLayout root) {
        LinearLayout card = ui.makeCard();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(ui.text("EdgeGest", 20, ui.cText, true));
        TextView sub = ui.text("Điều hướng mép · Không Root / Accessibility", 12, ui.cMuted, false);
        sub.setPadding(0, ui.dp(2), 0, 0);
        titleBox.addView(sub);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        overallBadge = ui.pill("Đang kiểm tra", ui.cMuted, ui.cSub);
        header.addView(overallBadge);
        card.addView(header);
        card.addView(ui.vSpace(10));

        TextView[] out = new TextView[1];
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(statusCell("Overlay", out), rowCellLp(true));
        ovVal = out[0];
        row1.addView(statusCell("Dải mép", out), rowCellLp(false));
        svcVal = out[0];
        card.addView(row1);
        card.addView(ui.vSpace(8));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(statusCell("Helper :47820", out), rowCellLp(true));
        helperVal = out[0];
        row2.addView(statusCell("Shizuku", out), rowCellLp(false));
        shzVal = out[0];
        card.addView(row2);
        card.addView(ui.vSpace(12));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        svcOnBtn = ui.actionButton("Bật Overlay", ui.cPrimary, Color.WHITE, 0);
        svcOnBtn.setOnClickListener(this);
        LinearLayout.LayoutParams onLp = new LinearLayout.LayoutParams(0, ui.dp(40), 1f);
        onLp.rightMargin = ui.dp(8);
        actions.addView(svcOnBtn, onLp);

        svcOffBtn = ui.actionButton("Tắt Overlay", ui.cSub, ui.cText, ui.cBorder);
        svcOffBtn.setOnClickListener(this);
        actions.addView(svcOffBtn, new LinearLayout.LayoutParams(0, ui.dp(40), 1f));

        card.addView(actions);
        root.addView(card);
    }

    private void buildPermissionsSection(LinearLayout root) {
        TextView[] badgeOut = new TextView[1];
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        permBody = makeCollapsibleSection(root, "Quyền & Helper",
                "Overlay, Shizuku, khởi chạy injector", badgeOut, chevOut, true);
        permSummary = badgeOut[0];
        permChevron = chevOut[0];

        LinearLayout r1 = ui.optionRow("Hiển thị trên ứng dụng khác", "App-op SYSTEM_ALERT_WINDOW");
        ovActionLabel = ui.pill("Đã cấp ✓", ui.cOkFg, ui.cOkBg);
        grantBtn = ui.compactButton("Cấp quyền", ui.cPrimary, Color.WHITE, 0);
        grantBtn.setOnClickListener(this);
        r1.addView(ovActionLabel);
        r1.addView(grantBtn);
        permBody.addView(r1);
        permBody.addView(ui.divider());

        LinearLayout r2 = ui.optionRow("Helper Injector (--server --hybrid)", "Khởi chạy qua Shizuku uid 2000");
        helperDesc = (TextView) ((LinearLayout) r2.getChildAt(0)).getChildAt(1);
        shizukuBtn = ui.compactButton("Bật Helper", ui.cSub, ui.cText, ui.cBorder);
        shizukuBtn.setOnClickListener(this);
        r2.addView(shizukuBtn);
        permBody.addView(r2);
        permBody.addView(ui.divider());

        LinearLayout r3 = ui.optionRow("Tự khởi chạy HyperOS", "Bật Autostart trong Thông tin ứng dụng");
        appInfoBtn = ui.compactButton("Mở cài đặt", ui.cSub, ui.cText, ui.cBorder);
        appInfoBtn.setOnClickListener(this);
        r3.addView(appInfoBtn);
        permBody.addView(r3);
    }

    private void buildGeometrySection(LinearLayout root, SharedPreferences prefs) {
        TextView[] badgeOut = new TextView[1];
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root, "Kích thước & Vùng mép",
                "Độ dày dải vuốt và vùng chừa an toàn", badgeOut, chevOut, true);
        geomSummary = badgeOut[0];

        EditText[] etOut = new EditText[1];
        body.addView(stepperRow("Độ dày mép trái / phải", "Vuốt vào để Back (8–120 dp)",
                prefs.getInt("side", OverlayService.DEF_SIDE), 8, 120, 4, etOut));
        sideEt = etOut[0];

        body.addView(ui.divider());
        body.addView(stepperRow("Độ dày mép dưới", "Home / Recents / Đổi app (8–120 dp)",
                prefs.getInt("bottom", OverlayService.DEF_BOTTOM), 8, 120, 2, etOut));
        bottomEt = etOut[0];

        body.addView(ui.divider());
        body.addView(stepperRow("Chừa mép trên (%)", "Tránh kéo nhầm thanh thông báo (0–50%)",
                prefs.getInt("topGuard", OverlayService.DEF_TOP_GUARD), 0, 50, 1, etOut));
        topGuardEt = etOut[0];

        body.addView(ui.divider());
        body.addView(stepperRow("Chừa thêm trên đáy (dp)", "Thu ngắn dải bên, tránh bàn phím (0–400)",
                prefs.getInt("bottomGap", OverlayService.DEF_BOTTOM_GAP), 0, 400, 10, etOut));
        bottomGapEt = etOut[0];

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(0, ui.dp(10), 0, 0);

        resetGeomBtn = new Button(this);
        resetGeomBtn.setText("Mặc định (40/24/11%/0)");
        resetGeomBtn.setAllCaps(false);
        resetGeomBtn.setTextSize(12);
        resetGeomBtn.setTextColor(ui.cMuted);
        resetGeomBtn.setBackgroundColor(Color.TRANSPARENT);
        resetGeomBtn.setPadding(0, ui.dp(4), ui.dp(8), ui.dp(4));
        resetGeomBtn.setOnClickListener(this);
        foot.addView(resetGeomBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        applyGeomBtn = ui.compactButton("Áp dụng mục này", ui.cOkBg, ui.cPrimary, 0);
        applyGeomBtn.setBackground(ui.roundRect(ui.cSub, ui.cPrimary, 10));
        applyGeomBtn.setOnClickListener(this);
        foot.addView(applyGeomBtn);
        body.addView(foot);

        GeometryWatcher watcher = new GeometryWatcher(this);
        sideEt.addTextChangedListener(watcher);
        bottomEt.addTextChangedListener(watcher);
        topGuardEt.addTextChangedListener(watcher);
        bottomGapEt.addTextChangedListener(watcher);
    }

    private void buildHoldActionsSection(LinearLayout root, SharedPreferences prefs) {
        TextView[] badgeOut = new TextView[1];
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root, "Cử chỉ & Nhấn giữ",
                "Kéo giữ mép trái / phải: mở app, nguồn, âm lượng...", badgeOut, chevOut, false);
        holdSummary = badgeOut[0];

        LinearLayout rLeft = ui.optionRow("Nhấn giữ mép trái", "");
        leftHoldDesc = (TextView) ((LinearLayout) rLeft.getChildAt(0)).getChildAt(1);
        leftHoldBtn = ui.compactButton("Chọn", ui.cSub, ui.cText, ui.cBorder);
        leftHoldBtn.setOnClickListener(this);
        rLeft.addView(leftHoldBtn);
        body.addView(rLeft);
        body.addView(ui.divider());

        LinearLayout rRight = ui.optionRow("Nhấn giữ mép phải", "");
        rightHoldDesc = (TextView) ((LinearLayout) rRight.getChildAt(0)).getChildAt(1);
        rightHoldBtn = ui.compactButton("Chọn", ui.cSub, ui.cText, ui.cBorder);
        rightHoldBtn.setOnClickListener(this);
        rRight.addView(rightHoldBtn);
        body.addView(rRight);
        body.addView(ui.divider());

        EditText[] etOut = new EditText[1];
        body.addView(stepperRow("Thời gian nhấn giữ (ms)", "Mốc kích hoạt hành động phụ & Recents (180–800)",
                prefs.getInt("holdMs", OverlayService.DEF_HOLD_MS), 180, 800, 20, etOut));
        holdMsEt = etOut[0];

        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.END);
        foot.setPadding(0, ui.dp(10), 0, 0);
        applyHoldBtn = ui.compactButton("Áp dụng mục này", ui.cSub, ui.cPrimary, ui.cPrimary);
        applyHoldBtn.setOnClickListener(this);
        foot.addView(applyHoldBtn);
        body.addView(foot);
    }

    private void buildVisualSection(LinearLayout root, SharedPreferences prefs) {
        TextView[] badgeOut = new TextView[1];
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root, "Hiển thị & Hiệu ứng",
                "Sub-screen Animation kéo mép, màu debug & bảng cử chỉ", badgeOut, chevOut, false);
        visualSummary = badgeOut[0];

        // Sub-screen launch card
        LinearLayout animCard = new LinearLayout(this);
        animCard.setOrientation(LinearLayout.HORIZONTAL);
        animCard.setGravity(Gravity.CENTER_VERTICAL);
        animCard.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 12));
        animCard.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));

        animSwatchDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20));
        dotLp.rightMargin = ui.dp(10);
        animCard.addView(animSwatchDot, dotLp);

        LinearLayout animTxt = new LinearLayout(this);
        animTxt.setOrientation(LinearLayout.VERTICAL);
        animTxt.addView(ui.text("Tuỳ chỉnh Animation kéo mép →", 13, ui.cText, true));
        animSubLine = ui.text("", 11, ui.cMuted, false);
        animTxt.addView(animSubLine);
        animCard.addView(animTxt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        openAnimBtn = ui.compactButton("Mở", ui.cCard, ui.cPrimary, ui.cBorder);
        openAnimBtn.setOnClickListener(this);
        animCard.addView(openAnimBtn);
        animCard.setOnClickListener(this);
        body.addView(animCard);

        body.addView(ui.vSpace(8));
        LinearLayout rDbg = ui.optionRow("Hiện màu dải cảm ứng (Debug)",
                "Tô đỏ / xanh lá / xanh dương cố định để canh mép");
        debugSw = new Switch(this);
        debugSw.setChecked(prefs.getBoolean("debug", OverlayService.DEF_DEBUG));
        debugSw.setOnCheckedChangeListener(new VisualSwitchListener(this));
        rDbg.addView(debugSw);
        body.addView(rDbg);

        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.END);
        foot.setPadding(0, ui.dp(6), 0, 0);
        applyVisualBtn = ui.compactButton("Áp dụng mục này", ui.cSub, ui.cPrimary, ui.cPrimary);
        applyVisualBtn.setOnClickListener(this);
        foot.addView(applyVisualBtn);
        body.addView(foot);

        body.addView(ui.vSpace(10));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 10));
        box.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        box.addView(gestureLine("Vuốt mép trái / phải (nhanh)", "Back"));
        box.addView(gestureLine("Vuốt mép trái / phải rồi giữ", "Hành động phụ"));
        box.addView(gestureLine("Vuốt lên từ mép dưới (nhanh)", "Home"));
        box.addView(gestureLine("Vuốt lên giữ (rung nhẹ)", "Recents"));
        box.addView(gestureLine("Vuốt ngang ở mép dưới", "App trước"));
        body.addView(box);
    }

    private void buildBackupSection(LinearLayout root) {
        TextView[] badgeOut = new TextView[1];
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root, "Sao lưu & Khôi phục",
                "Export / Import JSON theo từng phần cấu hình", badgeOut, chevOut, false);
        badgeOut[0].setText("JSON");

        body.addView(ui.text("Chọn các phần muốn sao lưu hoặc khôi phục:", 12, ui.cMuted, false));
        body.addView(ui.vSpace(4));

        bkGeomCb = makeCheck("Kích thước & Vùng mép", true);
        bkHoldCb = makeCheck("Cử chỉ & Nhấn giữ", true);
        bkAnimCb = makeCheck("Hiệu ứng Animation & Debug", true);
        body.addView(bkGeomCb);
        body.addView(bkHoldCb);
        body.addView(bkAnimCb);
        body.addView(ui.vSpace(8));

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        exportFileBtn = ui.compactButton("Xuất file (.json)", ui.cSub, ui.cText, ui.cBorder);
        exportFileBtn.setOnClickListener(this);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ui.dp(36), 1f);
        l1.rightMargin = ui.dp(8);
        r1.addView(exportFileBtn, l1);

        importFileBtn = ui.compactButton("Nhập file (.json)", ui.cSub, ui.cText, ui.cBorder);
        importFileBtn.setOnClickListener(this);
        r1.addView(importFileBtn, new LinearLayout.LayoutParams(0, ui.dp(36), 1f));
        body.addView(r1);
        body.addView(ui.vSpace(6));

        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        copyJsonBtn = ui.compactButton("Copy JSON", ui.cSub, ui.cPrimary, ui.cBorder);
        copyJsonBtn.setOnClickListener(this);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ui.dp(36), 1f);
        l2.rightMargin = ui.dp(8);
        r2.addView(copyJsonBtn, l2);

        pasteJsonBtn = ui.compactButton("Dán JSON...", ui.cSub, ui.cPrimary, ui.cBorder);
        pasteJsonBtn.setOnClickListener(this);
        r2.addView(pasteJsonBtn, new LinearLayout.LayoutParams(0, ui.dp(36), 1f));
        body.addView(r2);

        TextView note = ui.text("Dự kiến TODO: đồng bộ sao lưu lên tài khoản Google cá nhân (Drive AppData) khi được cấp quyền.", 11, ui.cMuted, false);
        note.setPadding(0, ui.dp(8), 0, 0);
        body.addView(note);
    }

    private void buildDiagnosticsSection(LinearLayout root) {
        TextView[] badgeOut = new TextView[1];
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root, "Chẩn đoán & Lệnh ADB",
                "Chạy không cần Shizuku, copy lệnh nhanh & khẩn cấp", badgeOut, chevOut, false);
        badgeOut[0].setText("ADB");

        TextView p1 = ui.text("• Trong app ngân hàng ẩn overlay, chế độ --hybrid tự chuyển sang đọc /dev/input.\n• Nếu máy chưa bật Shizuku, cắm USB hoặc ADB Wi-Fi và chạy lệnh bên dưới:", 12, ui.cMuted, false);
        p1.setPadding(0, ui.dp(2), 0, ui.dp(10));
        body.addView(p1);

        body.addView(adbCommandCard("1. Bật Helper không cần Shizuku (chạy sau khi reboot):", CMD_ADB_START));
        body.addView(ui.vSpace(8));
        body.addView(adbCommandCard("2. Cấp toàn bộ quyền APK qua ADB (1 lần):", CMD_ADB_GRANT));
        body.addView(ui.vSpace(8));
        body.addView(adbCommandCard("3. Khẩn cấp hiện lại thanh 3 nút:", CMD_ADB_FSG0));
    }

    // ---- Builders ----

    private CheckBox makeCheck(String title, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(title);
        cb.setTextSize(12);
        cb.setTextColor(ui.cText);
        cb.setChecked(checked);
        return cb;
    }

    private LinearLayout makeCollapsibleSection(LinearLayout root, String titleStr, String subStr,
            TextView[] badgeOut, UiKit.ChevronView[] chevOut, boolean expanded) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ui.roundRect(ui.cCard, ui.cBorder, 16));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(ui.text(titleStr, 15, ui.cText, true));
        TextView sub = ui.text(subStr, 11, ui.cMuted, false);
        sub.setPadding(0, ui.dp(1), 0, 0);
        left.addView(sub);
        header.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = ui.pill("", ui.cMuted, ui.cSub);
        badge.setTypeface(Typeface.MONOSPACE);
        header.addView(badge);
        badgeOut[0] = badge;

        UiKit.ChevronView chev = new UiKit.ChevronView(this, ui.cMuted, ui.dp(18), ui.dp(2));
        chev.setExpanded(expanded, false);
        LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18));
        chevLp.leftMargin = ui.dp(10);
        header.addView(chev, chevLp);
        chevOut[0] = chev;

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(12));
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
        wrap.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 10));
        wrap.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(ui.text(label, 11, ui.cMuted, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button copy = ui.compactButton("Copy", ui.cCard, ui.cPrimary, ui.cBorder);
        copy.setOnClickListener(new CopyAction(this, cmd));
        top.addView(copy);
        wrap.addView(top);

        TextView code = ui.text(cmd, 11, ui.cText, false);
        code.setTypeface(Typeface.MONOSPACE);
        code.setPadding(0, ui.dp(6), 0, 0);
        wrap.addView(code);
        return wrap;
    }

    private LinearLayout statusCell(String label, TextView[] valOut) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 12));
        cell.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        cell.addView(ui.text(label, 11, ui.cMuted, false));
        TextView v = ui.text("...", 13, ui.cText, true);
        v.setPadding(0, ui.dp(2), 0, 0);
        cell.addView(v);
        valOut[0] = v;
        return cell;
    }

    private LinearLayout.LayoutParams rowCellLp(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (left) lp.rightMargin = ui.dp(8);
        return lp;
    }

    private LinearLayout stepperRow(String titleStr, String subStr, int val,
            int min, int max, int step, EditText[] outEt) {
        LinearLayout row = ui.optionRow(titleStr, subStr);

        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(val));
        et.setTextSize(13);
        et.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        et.setTextColor(ui.cText);
        et.setGravity(Gravity.CENTER);
        et.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 8));
        et.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));

        Button minus = ui.stepButton("−");
        minus.setOnClickListener(new StepAction(et, -step, min, max));
        Button plus = ui.stepButton("+");
        plus.setOnClickListener(new StepAction(et, step, min, max));

        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.HORIZONTAL);
        ctrl.setGravity(Gravity.CENTER_VERTICAL);
        ctrl.addView(minus, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(ui.dp(52), ui.dp(32));
        etLp.leftMargin = ui.dp(4);
        etLp.rightMargin = ui.dp(4);
        ctrl.addView(et, etLp);
        ctrl.addView(plus, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));

        row.addView(ctrl);
        outEt[0] = et;
        return row;
    }

    private LinearLayout gestureLine(String gesture, String action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, ui.dp(3), 0, ui.dp(3));
        r.addView(ui.text(gesture, 12, ui.cMuted, false),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView a = ui.text(action, 12, ui.cText, true);
        a.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        r.addView(a);
        return r;
    }

    // ---- Load / Apply / Backup ----

    void loadFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE);
        sideEt.setText(String.valueOf(prefs.getInt("side", OverlayService.DEF_SIDE)));
        bottomEt.setText(String.valueOf(prefs.getInt("bottom", OverlayService.DEF_BOTTOM)));
        topGuardEt.setText(String.valueOf(prefs.getInt("topGuard", OverlayService.DEF_TOP_GUARD)));
        bottomGapEt.setText(String.valueOf(prefs.getInt("bottomGap", OverlayService.DEF_BOTTOM_GAP)));

        leftHoldAction = prefs.getString("leftHold", OverlayService.DEF_LEFT_HOLD);
        rightHoldAction = prefs.getString("rightHold", OverlayService.DEF_RIGHT_HOLD);
        holdMsEt.setText(String.valueOf(prefs.getInt("holdMs", OverlayService.DEF_HOLD_MS)));
        leftHoldDesc.setText(GestureActions.describe(this, leftHoldAction));
        rightHoldDesc.setText(GestureActions.describe(this, rightHoldAction));

        debugSw.setChecked(prefs.getBoolean("debug", OverlayService.DEF_DEBUG));
        updateGeometrySummary();
        updateHoldSummary();
        updateVisualBadge();
    }

    private void saveSectionGeometry() {
        getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE).edit()
                .putInt("side", parse(sideEt, OverlayService.DEF_SIDE))
                .putInt("bottom", parse(bottomEt, OverlayService.DEF_BOTTOM))
                .putInt("topGuard", parse(topGuardEt, OverlayService.DEF_TOP_GUARD))
                .putInt("bottomGap", parse(bottomGapEt, OverlayService.DEF_BOTTOM_GAP))
                .apply();
    }

    private void saveSectionHold() {
        getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE).edit()
                .putString("leftHold", leftHoldAction)
                .putString("rightHold", rightHoldAction)
                .putInt("holdMs", parse(holdMsEt, OverlayService.DEF_HOLD_MS))
                .apply();
    }

    private void saveSectionVisual() {
        getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE).edit()
                .putBoolean("debug", debugSw.isChecked())
                .apply();
    }

    private void restartServiceIfActive(String msg) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Đã lưu (hãy cấp quyền overlay để bật dải)", Toast.LENGTH_LONG).show();
            return;
        }
        startOverlayService();
        autoHelperTried = false;
        refresh();
        uiHandler.postDelayed(new RefreshTask(this), 250);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

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
        } else if (v == svcOnBtn) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Hãy cấp quyền overlay trước", Toast.LENGTH_LONG).show();
                return;
            }
            startOverlayService();
            autoHelperTried = false;
            refresh();
            uiHandler.postDelayed(new RefreshTask(this), 250);
            Toast.makeText(this, "Đã bật overlay", Toast.LENGTH_SHORT).show();
        } else if (v == svcOffBtn) {
            stopService(new Intent(this, OverlayService.class));
            OverlayService.running = false;
            refresh();
            Toast.makeText(this, "Đã tắt overlay", Toast.LENGTH_SHORT).show();
        } else if (v == leftHoldBtn) {
            showHoldActionPicker(true);
        } else if (v == rightHoldBtn) {
            showHoldActionPicker(false);
        } else if (v == resetGeomBtn) {
            sideEt.setText(String.valueOf(OverlayService.DEF_SIDE));
            bottomEt.setText(String.valueOf(OverlayService.DEF_BOTTOM));
            topGuardEt.setText(String.valueOf(OverlayService.DEF_TOP_GUARD));
            bottomGapEt.setText(String.valueOf(OverlayService.DEF_BOTTOM_GAP));
            updateGeometrySummary();
        } else if (v == applyGeomBtn) {
            saveSectionGeometry();
            restartServiceIfActive("Đã áp dụng Kích thước & Vùng mép");
        } else if (v == applyHoldBtn) {
            saveSectionHold();
            restartServiceIfActive("Đã áp dụng Cử chỉ & Nhấn giữ");
        } else if (v == applyVisualBtn) {
            saveSectionVisual();
            restartServiceIfActive("Đã áp dụng Hiển thị & Hiệu ứng");
        } else if (v == openAnimBtn || v == openAnimBtn.getParent()) {
            startActivityForResult(new Intent(this, AnimConfigActivity.class), REQ_ANIM);
        } else if (v == bottomCancelBtn) {
            loadFromPrefs();
            Toast.makeText(this, "Đã hủy thay đổi chưa áp dụng", Toast.LENGTH_SHORT).show();
        } else if (v == bottomApplyBtn) {
            saveSectionGeometry();
            saveSectionHold();
            saveSectionVisual();
            restartServiceIfActive("Đã áp dụng toàn bộ cấu hình");
        } else if (v == exportFileBtn) {
            if (!anyBackupSelected()) return;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "edgegest-backup.json");
            startActivityForResult(i, REQ_BACKUP_FILE);
        } else if (v == importFileBtn) {
            if (!anyBackupSelected()) return;
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_RESTORE_FILE);
        } else if (v == copyJsonBtn) {
            if (!anyBackupSelected()) return;
            String json = ConfigBackup.exportJson(getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE),
                    bkGeomCb.isChecked(), bkHoldCb.isChecked(), bkAnimCb.isChecked());
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("EdgeGest Config", json));
                Toast.makeText(this, "Đã copy JSON backup", Toast.LENGTH_SHORT).show();
            }
        } else if (v == pasteJsonBtn) {
            if (!anyBackupSelected()) return;
            showPasteJsonDialog();
        }
    }

    private boolean anyBackupSelected() {
        if (bkGeomCb.isChecked() || bkHoldCb.isChecked() || bkAnimCb.isChecked()) return true;
        Toast.makeText(this, "Hãy chọn ít nhất 1 phần cấu hình", Toast.LENGTH_SHORT).show();
        return false;
    }

    private void showPasteJsonDialog() {
        EditText et = new EditText(this);
        et.setHint("Dán nội dung JSON vào đây...");
        et.setMinLines(4);
        et.setMaxLines(8);
        new AlertDialog.Builder(this)
                .setTitle("Khôi phục từ JSON")
                .setView(et)
                .setPositiveButton("Khôi phục", new PasteJsonListener(this, et))
                .setNegativeButton("Hủy", null)
                .show();
    }

    void applyJsonString(String raw) {
        try {
            String msg = ConfigBackup.importJson(getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE),
                    raw, bkGeomCb.isChecked(), bkHoldCb.isChecked(), bkAnimCb.isChecked());
            loadFromPrefs();
            if (OverlayService.running && Settings.canDrawOverlays(this)) startOverlayService();
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "JSON không hợp lệ: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ANIM && resultCode == RESULT_OK) {
            loadFromPrefs();
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_BACKUP_FILE) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                String json = ConfigBackup.exportJson(getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE),
                        bkGeomCb.isChecked(), bkHoldCb.isChecked(), bkAnimCb.isChecked());
                if (os != null) os.write(json.getBytes("UTF-8"));
                Toast.makeText(this, "Đã xuất file backup JSON", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(this, "Xuất file lỗi: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_RESTORE_FILE) {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) return;
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                applyJsonString(sb.toString());
            } catch (Throwable t) {
                Toast.makeText(this, "Đọc file lỗi: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
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
                .setNegativeButton("Hủy", null)
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

    private void startOverlayService() {
        Intent svc = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);
    }

    void refresh() {
        boolean ov = Settings.canDrawOverlays(this);
        boolean svc = OverlayService.running;

        ovVal.setText(ov ? "ĐÃ CẤP ✓" : "CHƯA CẤP ✗");
        ovVal.setTextColor(ov ? ui.cOkFg : ui.cErrFg);

        svcVal.setText(svc ? "ĐANG CHẠY ✓" : "ĐÃ TẮT ✗");
        svcVal.setTextColor(svc ? ui.cOkFg : ui.cWarnFg);

        ovActionLabel.setVisibility(ov ? View.VISIBLE : View.GONE);
        grantBtn.setVisibility(ov ? View.GONE : View.VISIBLE);

        helperVal.setText("Kiểm tra...");
        helperVal.setTextColor(ui.cMuted);
        shzVal.setText("Kiểm tra...");
        shzVal.setTextColor(ui.cMuted);
        new Thread(new HelperProbe(this), "helper-probe").start();
    }

    void applyProbeResult(boolean alive, int shzState) {
        if (isFinishing() || isDestroyed()) return;
        shizukuState = shzState;
        boolean ov = Settings.canDrawOverlays(this);
        boolean svc = OverlayService.running;

        helperVal.setText(alive ? "SỐNG ✓" : "CHƯA CHẠY ✗");
        helperVal.setTextColor(alive ? ui.cOkFg : ui.cErrFg);

        shizukuBtn.setEnabled(true);
        if (shzState == ShizukuStarter.STATE_NOT_RUNNING) {
            shzVal.setText("CHƯA BẬT ✗");
            shzVal.setTextColor(ui.cWarnFg);
            helperDesc.setText("Mở Shizuku hoặc chạy lệnh ADB bên dưới");
            shizukuBtn.setText("Mở Shizuku");
            shizukuBtn.setVisibility(alive ? View.GONE : View.VISIBLE);
        } else if (shzState == ShizukuStarter.STATE_NO_PERMISSION) {
            shzVal.setText("CHƯA CẤP");
            shzVal.setTextColor(ui.cWarnFg);
            helperDesc.setText("Cần cấp quyền Shizuku cho EdgeGest");
            shizukuBtn.setText("Cấp quyền Shizuku");
            shizukuBtn.setVisibility(View.VISIBLE);
        } else {
            shzVal.setText("SẴN SÀNG ✓");
            shzVal.setTextColor(ui.cOkFg);
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
            ui.setPill(overallBadge, "Đang hoạt động", ui.cOkFg, ui.cOkBg);
            ui.setPill(permSummary, "4/4 OK", ui.cOkFg, ui.cOkBg);
            if (!autoPermCollapsedOnce) {
                autoPermCollapsedOnce = true;
                permBody.setVisibility(View.GONE);
                permChevron.setExpanded(false, false);
            }
        } else {
            ui.setPill(overallBadge, svc ? "Thiếu Helper/Quyền" : "Đã tắt", ui.cWarnFg, ui.cWarnBg);
            ui.setPill(permSummary, "Cần kiểm tra", ui.cWarnFg, ui.cWarnBg);
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
            ui.setPill(holdSummary, "Chỉ Back", ui.cMuted, ui.cSub);
        } else {
            String lb = l ? GestureActions.shortBadge(leftHoldAction) : "—";
            String rb = r ? GestureActions.shortBadge(rightHoldAction) : "—";
            ui.setPill(holdSummary, "L:" + lb + " R:" + rb, ui.cPrimary, ui.cSub);
        }
    }

    void updateVisualBadge() {
        SharedPreferences prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE);
        boolean al = prefs.getBoolean("animLeft", OverlayService.DEF_ANIM_LEFT);
        boolean ar = prefs.getBoolean("animRight", OverlayService.DEF_ANIM_RIGHT);
        boolean ab = prefs.getBoolean("animBottom", OverlayService.DEF_ANIM_BOTTOM);
        int col = prefs.getInt("animColor", OverlayService.DEF_ANIM_COLOR);
        int aw = prefs.getInt("animWidth", OverlayService.DEF_ANIM_WIDTH);
        int len = prefs.getInt("animLength", OverlayService.DEF_ANIM_LENGTH);
        int alpha = prefs.getInt("animAlpha", OverlayService.DEF_ANIM_ALPHA);
        boolean d = debugSw.isChecked();

        animSwatchDot.setBackground(ui.roundRect(col, ui.cBorder, 99));
        animSwatchDot.setAlpha(Math.max(0.2f, alpha / 100f));

        StringBuilder edges = new StringBuilder();
        if (al) edges.append("Trái, ");
        if (ar) edges.append("Phải, ");
        if (ab) edges.append("Dưới, ");
        String eText = edges.length() > 0 ? edges.substring(0, edges.length() - 2) : "Tắt hết";
        animSubLine.setText(eText + " · Rộng " + aw + "dp · Dài " + len + "dp · " + alpha + "%");

        StringBuilder shortE = new StringBuilder();
        if (al) shortE.append("L·");
        if (ar) shortE.append("R·");
        if (ab) shortE.append("B·");
        String se = shortE.length() > 0 ? shortE.substring(0, shortE.length() - 1) : "TẮT";

        if (d) ui.setPill(visualSummary, se + " · Dbg", ui.cWarnFg, ui.cWarnBg);
        else if (al || ar || ab) ui.setPill(visualSummary, se, ui.cOkFg, ui.cOkBg);
        else ui.setPill(visualSummary, "Tắt hết", ui.cMuted, ui.cSub);
    }

    private int parse(EditText et, int def) {
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (Exception e) { return def; }
    }

    // ---- Static nested classes (d8 34.0.0 compatibility) ----

    private static final class SectionToggle implements View.OnClickListener {
        private final LinearLayout body;
        private final UiKit.ChevronView chevron;

        SectionToggle(LinearLayout body, UiKit.ChevronView chevron) {
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

    private static final class PasteJsonListener implements DialogInterface.OnClickListener {
        private final MainActivity activity;
        private final EditText et;

        PasteJsonListener(MainActivity activity, EditText et) {
            this.activity = activity;
            this.et = et;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            activity.applyJsonString(et.getText().toString());
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
