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
import android.graphics.drawable.Drawable;
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
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
import java.util.Locale;

/**
 * Màn hình cấu hình theo nhóm options có collapsible (hỗ trợ song ngữ VI / EN):
 * - Chuyển ngôn ngữ VI / EN trực tiếp từ Hero Header
 * - Bộ icon vector riêng từng hành động & icon app thật khi chọn app cho cử chỉ nhấn giữ
 * - Apply riêng từng phần hoặc qua thanh Hủy / Áp dụng ghim ở bottom
 * - Sub-screen AnimConfigActivity tuỳ chỉnh animation kéo mép (với Sticky Preview)
 * - Sao lưu / Khôi phục JSON chọn lọc theo từng phần cấu hình
 *
 * Tuân thủ ràng buộc d8 34.0.0: chỉ dùng static nested class với constructor tường minh.
 */
public final class MainActivity extends Activity implements View.OnClickListener {
    private static final int REQ_ANIM = 201;
    private static final int REQ_BACKUP_FILE = 202;
    private static final int REQ_RESTORE_FILE = 203;

    private static final String CMD_ADB_GRANT = "adb shell \"appops set com.lcd.edgegest SYSTEM_ALERT_WINDOW allow; "
            + "pm grant com.lcd.edgegest moe.shizuku.manager.permission.API_V23; "
            + "pm grant com.lcd.edgegest android.permission.POST_NOTIFICATIONS; "
            + "pm grant com.lcd.edgegest android.permission.ACCESS_LOCAL_NETWORK\"";
    private static final String CMD_ADB_FSG0 = "adb shell settings put global force_fsg_nav_bar 0";

    UiKit ui;
    boolean vi;
    private TextView overallBadge;
    private TextView ovVal, svcVal, helperVal, shzVal;
    private TextView ovActionLabel, helperDesc;
    private TextView leftHoldDesc, rightHoldDesc, animSubLine;
    private ActionIcons.ActionIconView leftHoldIcon, rightHoldIcon;
    private View animSwatchDot;
    private LinearLayout permBody;
    private UiKit.ChevronView permChevron;

    private EditText sideEt, bottomEt, topGuardEt, bottomGapEt, holdMsEt;
    private Switch debugSw;
    private CheckBox bkGeomCb, bkHoldCb, bkAnimCb;

    private Button langViBtn, langEnBtn;
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
        vi = Strings.isVi(this);
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

        bottomCancelBtn = ui.actionButton(Strings.pick(vi, "Hủy", "Cancel"), ui.cSub, ui.cText, ui.cBorder);
        bottomCancelBtn.setOnClickListener(this);
        bottomApplyBtn = ui.actionButton(Strings.pick(vi, "Áp dụng tất cả", "Apply all"), ui.cPrimary, Color.WHITE, 0);
        bottomApplyBtn.setOnClickListener(this);
        outer.addView(ui.makeBottomBar(bottomCancelBtn, bottomApplyBtn));

        setContentView(outer);
        loadFromPrefs();

        if (b != null) {
            if (b.containsKey("side")) sideEt.setText(b.getString("side"));
            if (b.containsKey("bottom")) bottomEt.setText(b.getString("bottom"));
            if (b.containsKey("topGuard")) topGuardEt.setText(b.getString("topGuard"));
            if (b.containsKey("bottomGap")) bottomGapEt.setText(b.getString("bottomGap"));
            if (b.containsKey("holdMs")) holdMsEt.setText(b.getString("holdMs"));
            if (b.containsKey("debug")) debugSw.setChecked(b.getBoolean("debug"));
            if (b.containsKey("leftHold")) leftHoldAction = b.getString("leftHold", leftHoldAction);
            if (b.containsKey("rightHold")) rightHoldAction = b.getString("rightHold", rightHoldAction);
            refreshHoldRows();
            updateVisualBadge();
        }

        if (Settings.canDrawOverlays(this) && !OverlayService.running) {
            startOverlayService();
            uiHandler.postDelayed(new RefreshTask(this), 250);
        }
        ShizukuStarter.setOnBinderListener(new BinderArrivedTask(this));
        new Thread(new AutoStartHelperTask(this), "auto-shizuku").start();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (sideEt == null) return;
        out.putString("side", sideEt.getText().toString());
        out.putString("bottom", bottomEt.getText().toString());
        out.putString("topGuard", topGuardEt.getText().toString());
        out.putString("bottomGap", bottomGapEt.getText().toString());
        out.putString("holdMs", holdMsEt.getText().toString());
        out.putBoolean("debug", debugSw.isChecked());
        out.putString("leftHold", leftHoldAction);
        out.putString("rightHold", rightHoldAction);
    }

    @Override
    protected void onDestroy() {
        ShizukuStarter.setOnBinderListener(null);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vi != Strings.isVi(this)) {
            recreate();
            return;
        }
        refresh();
    }

    private void buildHeroCard(LinearLayout root) {
        LinearLayout card = ui.makeCard();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        header.addView(ui.text("EdgeGest", 20, ui.cText, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button[] viOut = new Button[1];
        Button[] enOut = new Button[1];
        LinearLayout langPill = ui.makeLangPill(vi, viOut, enOut, this);
        langViBtn = viOut[0];
        langEnBtn = enOut[0];
        LinearLayout.LayoutParams lpLang = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpLang.rightMargin = ui.dp(6);
        header.addView(langPill, lpLang);

        overallBadge = ui.pill(Strings.pick(vi, "Kiểm tra", "Checking"), ui.cMuted, ui.cSub);
        header.addView(overallBadge);
        card.addView(header);
        card.addView(ui.vSpace(10));

        TextView[] out = new TextView[1];
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(statusCell("Overlay", out), rowCellLp(true));
        ovVal = out[0];
        row1.addView(statusCell(Strings.pick(vi, "Dải mép", "Edge Strips"), out), rowCellLp(false));
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

        svcOnBtn = ui.actionButton(Strings.pick(vi, "Bật Overlay", "Enable Overlay"), ui.cPrimary, Color.WHITE, 0);
        svcOnBtn.setOnClickListener(this);
        LinearLayout.LayoutParams onLp = new LinearLayout.LayoutParams(0, ui.dp(40), 1f);
        onLp.rightMargin = ui.dp(8);
        actions.addView(svcOnBtn, onLp);

        svcOffBtn = ui.actionButton(Strings.pick(vi, "Tắt Overlay", "Disable"), ui.cSub, ui.cText, ui.cBorder);
        svcOffBtn.setOnClickListener(this);
        actions.addView(svcOffBtn, new LinearLayout.LayoutParams(0, ui.dp(40), 1f));

        card.addView(actions);
        root.addView(card);
    }

    private void buildPermissionsSection(LinearLayout root) {
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        permBody = makeCollapsibleSection(root,
                Strings.pick(vi, "Quyền & Helper", "Permissions & Helper"),
                chevOut, true);
        permChevron = chevOut[0];

        LinearLayout r1 = ui.optionRow(
                Strings.pick(vi, "Hiển thị trên ứng dụng khác", "Display over other apps"),
                null);
        ovActionLabel = ui.pill(Strings.pick(vi, "Đã cấp ✓", "Granted ✓"), ui.cOkFg, ui.cOkBg);
        grantBtn = ui.compactButton(Strings.pick(vi, "Cấp quyền", "Grant"), ui.cPrimary, Color.WHITE, 0);
        grantBtn.setOnClickListener(this);
        r1.addView(ovActionLabel);
        r1.addView(grantBtn);
        permBody.addView(r1);
        permBody.addView(ui.divider());

        LinearLayout r2 = ui.optionRow("Helper Injector (--server --hybrid)",
                Strings.pick(vi, "Khởi chạy qua Shizuku uid 2000", "Start via Shizuku uid 2000"));
        helperDesc = (TextView) ((LinearLayout) r2.getChildAt(0)).getChildAt(1);
        shizukuBtn = ui.compactButton(Strings.pick(vi, "Bật Helper", "Start Helper"), ui.cSub, ui.cText, ui.cBorder);
        shizukuBtn.setOnClickListener(this);
        r2.addView(shizukuBtn);
        permBody.addView(r2);
        permBody.addView(ui.divider());

        LinearLayout r3 = ui.optionRow(
                Strings.pick(vi, "Tự khởi chạy HyperOS", "HyperOS Autostart"),
                null);
        appInfoBtn = ui.compactButton(Strings.pick(vi, "Mở cài đặt", "App info"), ui.cSub, ui.cText, ui.cBorder);
        appInfoBtn.setOnClickListener(this);
        r3.addView(appInfoBtn);
        permBody.addView(r3);
    }

    private void buildGeometrySection(LinearLayout root, SharedPreferences prefs) {
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root,
                Strings.pick(vi, "Kích thước & Vùng mép", "Strip Size & Safe Areas"),
                chevOut, true);

        EditText[] etOut = new EditText[1];
        body.addView(stepperRow(
                Strings.pick(vi, "Độ dày mép trái / phải (dp)", "Left / right edge width (dp)"),
                null,
                prefs.getInt("side", OverlayService.DEF_SIDE), 8, 120, 4, etOut));
        sideEt = etOut[0];

        body.addView(ui.divider());
        body.addView(stepperRow(
                Strings.pick(vi, "Độ dày mép dưới (dp)", "Bottom edge height (dp)"),
                null,
                prefs.getInt("bottom", OverlayService.DEF_BOTTOM), 8, 120, 2, etOut));
        bottomEt = etOut[0];

        body.addView(ui.divider());
        body.addView(stepperRow(
                Strings.pick(vi, "Chừa mép trên (%)", "Top safe margin (%)"),
                null,
                prefs.getInt("topGuard", OverlayService.DEF_TOP_GUARD), 0, 50, 1, etOut));
        topGuardEt = etOut[0];

        body.addView(ui.divider());
        body.addView(stepperRow(
                Strings.pick(vi, "Chừa thêm trên đáy (dp)", "Bottom gap (dp)"),
                null,
                prefs.getInt("bottomGap", OverlayService.DEF_BOTTOM_GAP), 0, 400, 10, etOut));
        bottomGapEt = etOut[0];

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(0, ui.dp(10), 0, 0);

        resetGeomBtn = new Button(this);
        resetGeomBtn.setText(Strings.pick(vi, "Mặc định (40/24/11%/0)", "Defaults (40/24/11%/0)"));
        resetGeomBtn.setAllCaps(false);
        resetGeomBtn.setTextSize(12);
        resetGeomBtn.setTextColor(ui.cMuted);
        resetGeomBtn.setBackgroundColor(Color.TRANSPARENT);
        resetGeomBtn.setPadding(0, ui.dp(4), ui.dp(8), ui.dp(4));
        resetGeomBtn.setOnClickListener(this);
        foot.addView(resetGeomBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        applyGeomBtn = ui.compactButton(Strings.pick(vi, "Áp dụng mục này", "Apply section"), ui.cSub, ui.cPrimary, ui.cPrimary);
        applyGeomBtn.setOnClickListener(this);
        foot.addView(applyGeomBtn);
        body.addView(foot);
    }

    private void buildHoldActionsSection(LinearLayout root, SharedPreferences prefs) {
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root,
                Strings.pick(vi, "Cử chỉ & Nhấn giữ", "Gestures & Long-Hold"),
                chevOut, false);

        ActionIcons.ActionIconView[] iconOut = new ActionIcons.ActionIconView[1];
        TextView[] descOut = new TextView[1];

        LinearLayout rLeft = makeHoldRow(Strings.pick(vi, "Nhấn giữ mép trái", "Left edge hold"),
                iconOut, descOut);
        leftHoldIcon = iconOut[0];
        leftHoldDesc = descOut[0];
        leftHoldBtn = ui.compactButton(Strings.pick(vi, "Chọn", "Choose"), ui.cSub, ui.cText, ui.cBorder);
        leftHoldBtn.setOnClickListener(this);
        rLeft.addView(leftHoldBtn);
        body.addView(rLeft);
        body.addView(ui.divider());

        LinearLayout rRight = makeHoldRow(Strings.pick(vi, "Nhấn giữ mép phải", "Right edge hold"),
                iconOut, descOut);
        rightHoldIcon = iconOut[0];
        rightHoldDesc = descOut[0];
        rightHoldBtn = ui.compactButton(Strings.pick(vi, "Chọn", "Choose"), ui.cSub, ui.cText, ui.cBorder);
        rightHoldBtn.setOnClickListener(this);
        rRight.addView(rightHoldBtn);
        body.addView(rRight);
        body.addView(ui.divider());

        EditText[] etOut = new EditText[1];
        body.addView(stepperRow(
                Strings.pick(vi, "Thời gian nhấn giữ (ms)", "Hold trigger delay (ms)"),
                null,
                prefs.getInt("holdMs", OverlayService.DEF_HOLD_MS), 180, 800, 20, etOut));
        holdMsEt = etOut[0];

        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.END);
        foot.setPadding(0, ui.dp(10), 0, 0);
        applyHoldBtn = ui.compactButton(Strings.pick(vi, "Áp dụng mục này", "Apply section"), ui.cSub, ui.cPrimary, ui.cPrimary);
        applyHoldBtn.setOnClickListener(this);
        foot.addView(applyHoldBtn);
        body.addView(foot);
    }

    private LinearLayout makeHoldRow(String title, ActionIcons.ActionIconView[] iconOut, TextView[] descOut) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(6), 0, ui.dp(6));

        FrameLayout box = new FrameLayout(this);
        box.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 10));
        int boxPx = ui.dp(36);
        ActionIcons.ActionIconView iv = new ActionIcons.ActionIconView(this, ui.cText, getResources().getDisplayMetrics().density);
        box.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(boxPx, boxPx);
        boxLp.rightMargin = ui.dp(10);
        row.addView(box, boxLp);

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(0, 0, ui.dp(8), 0);
        txt.addView(ui.text(title, 13, ui.cText, true));
        TextView desc = ui.text("", 11, ui.cMuted, false);
        desc.setPadding(0, ui.dp(1), 0, 0);
        txt.addView(desc);
        row.addView(txt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        iconOut[0] = iv;
        descOut[0] = desc;
        return row;
    }

    private void buildVisualSection(LinearLayout root, SharedPreferences prefs) {
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root,
                Strings.pick(vi, "Hiển thị & Hiệu ứng", "Visual & Animation"),
                chevOut, false);

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
        animTxt.setPadding(0, 0, ui.dp(8), 0);
        animTxt.addView(ui.text(Strings.pick(vi, "Tuỳ chỉnh Animation kéo mép", "Customize Swipe Animation"),
                13, ui.cText, true));
        animSubLine = ui.text("", 11, ui.cMuted, false);
        animTxt.addView(animSubLine);
        animCard.addView(animTxt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        openAnimBtn = ui.compactButton(Strings.pick(vi, "Mở ›", "Open ›"), ui.cCard, ui.cPrimary, ui.cBorder);
        openAnimBtn.setOnClickListener(this);
        animCard.addView(openAnimBtn);
        animCard.setOnClickListener(this);
        body.addView(animCard);

        body.addView(ui.vSpace(8));
        LinearLayout rDbg = ui.optionRow(
                Strings.pick(vi, "Hiện màu dải cảm ứng (Debug)", "Show touch strips tint (Debug)"),
                null);
        debugSw = new Switch(this);
        debugSw.setChecked(prefs.getBoolean("debug", OverlayService.DEF_DEBUG));
        debugSw.setOnCheckedChangeListener(new VisualSwitchListener(this));
        rDbg.addView(debugSw);
        body.addView(rDbg);

        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.END);
        foot.setPadding(0, ui.dp(6), 0, 0);
        applyVisualBtn = ui.compactButton(Strings.pick(vi, "Áp dụng mục này", "Apply section"), ui.cSub, ui.cPrimary, ui.cPrimary);
        applyVisualBtn.setOnClickListener(this);
        foot.addView(applyVisualBtn);
        body.addView(foot);

        body.addView(ui.vSpace(10));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 10));
        box.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        box.addView(gestureLine(Strings.pick(vi, "Vuốt mép trái / phải (nhanh)", "Swipe left / right edge (quick)"), "Back"));
        box.addView(gestureLine(Strings.pick(vi, "Vuốt mép trái / phải rồi giữ", "Swipe left / right & hold"),
                Strings.pick(vi, "Hành động phụ", "Custom action")));
        box.addView(gestureLine(Strings.pick(vi, "Vuốt lên từ mép dưới (nhanh)", "Swipe up from bottom (quick)"), "Home"));
        box.addView(gestureLine(Strings.pick(vi, "Vuốt lên giữ (rung nhẹ)", "Swipe up & hold (haptic)"), "Recents"));
        box.addView(gestureLine(Strings.pick(vi, "Vuốt ngang ở mép dưới", "Swipe horizontally at bottom"),
                Strings.pick(vi, "App trước", "Previous app")));
        body.addView(box);
    }

    private void buildBackupSection(LinearLayout root) {
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root,
                Strings.pick(vi, "Sao lưu & Khôi phục", "Backup & Restore"),
                chevOut, false);

        bkGeomCb = makeCheck(Strings.pick(vi, "Kích thước & Vùng mép", "Strip Size & Safe Areas"), true);
        bkHoldCb = makeCheck(Strings.pick(vi, "Cử chỉ & Nhấn giữ", "Gestures & Long-Hold"), true);
        bkAnimCb = makeCheck(Strings.pick(vi, "Hiệu ứng Animation & Debug", "Animation & Debug Tint"), true);
        body.addView(bkGeomCb);
        body.addView(bkHoldCb);
        body.addView(bkAnimCb);
        body.addView(ui.vSpace(8));

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        exportFileBtn = ui.compactButton(Strings.pick(vi, "Xuất file (.json)", "Export (.json)"),
                ui.cSub, ui.cText, ui.cBorder);
        exportFileBtn.setOnClickListener(this);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ui.dp(36), 1f);
        l1.rightMargin = ui.dp(8);
        r1.addView(exportFileBtn, l1);

        importFileBtn = ui.compactButton(Strings.pick(vi, "Nhập file (.json)", "Import (.json)"),
                ui.cSub, ui.cText, ui.cBorder);
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

        pasteJsonBtn = ui.compactButton(Strings.pick(vi, "Dán JSON...", "Paste JSON..."),
                ui.cSub, ui.cPrimary, ui.cBorder);
        pasteJsonBtn.setOnClickListener(this);
        r2.addView(pasteJsonBtn, new LinearLayout.LayoutParams(0, ui.dp(36), 1f));
        body.addView(r2);

        TextView note = ui.text(Strings.pick(vi,
                "TODO: đồng bộ tài khoản Google (Drive AppData).",
                "TODO: Google account sync (Drive AppData)."),
                11, ui.cMuted, false);
        note.setPadding(0, ui.dp(8), 0, 0);
        body.addView(note);
    }

    private void buildDiagnosticsSection(LinearLayout root) {
        UiKit.ChevronView[] chevOut = new UiKit.ChevronView[1];
        LinearLayout body = makeCollapsibleSection(root,
                Strings.pick(vi, "Chẩn đoán & Lệnh ADB", "Diagnostics & ADB Commands"),
                chevOut, false);

        TextView p1 = ui.text(Strings.pick(vi,
                "• App ngân hàng ẩn overlay: --hybrid tự chuyển sang /dev/input.\n"
                        + "• Chưa bật Shizuku: chạy lệnh ADB bên dưới:",
                "• Banking apps hiding overlays: --hybrid falls back to /dev/input.\n"
                        + "• Without Shizuku: run ADB commands below:"),
                12, ui.cMuted, false);
        p1.setPadding(0, ui.dp(2), 0, ui.dp(10));
        body.addView(p1);

        body.addView(adbCommandCard(Strings.pick(vi,
                "1. Bật Helper không cần Shizuku (tự chạy từ APK):",
                "1. Start Helper without Shizuku (runs from APK):"), buildAdbStartCommand()));
        body.addView(ui.vSpace(8));
        body.addView(adbCommandCard(Strings.pick(vi,
                "2. Cấp toàn bộ quyền APK qua ADB (1 lần):",
                "2. Grant all APK permissions via ADB (once):"), CMD_ADB_GRANT));
        body.addView(ui.vSpace(8));
        body.addView(adbCommandCard(Strings.pick(vi,
                "3. Khẩn cấp hiện lại thanh 3 nút:",
                "3. Emergency restore 3-button nav bar:"), CMD_ADB_FSG0));
    }

    private String buildAdbStartCommand() {
        String apkPath = getApplicationInfo().sourceDir;
        if (apkPath == null || apkPath.isEmpty()) {
            return "adb shell sh /data/local/tmp/edgegest/start-server.sh";
        }
        return "adb shell \"killall edgegest 2>/dev/null; sleep 0.3; mkdir -p /data/local/tmp/edgegest; "
                + "setsid sh -c 'CLASSPATH=" + apkPath + " app_process /system/bin --nice-name=edgegest "
                + "com.lcd.edgegest.Main --server --hybrid >/data/local/tmp/edgegest/edgegest.log 2>&1; "
                + "cmd role get-role-holders android.app.role.HOME | grep -q com.miui.home || "
                + "cmd settings put global force_fsg_nav_bar 0' </dev/null >/dev/null 2>&1 &\"";
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

    private LinearLayout makeCollapsibleSection(LinearLayout root, String titleStr,
            UiKit.ChevronView[] chevOut, boolean expanded) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ui.roundRect(ui.cCard, ui.cBorder, 16));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));

        header.addView(ui.text(titleStr, 15, ui.cText, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        UiKit.ChevronView chev = new UiKit.ChevronView(this, ui.cMuted, ui.dp(18), ui.dp(2));
        chev.setExpanded(expanded, false);
        LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18));
        chevLp.leftMargin = ui.dp(10);
        header.addView(chev, chevLp);
        chevOut[0] = chev;

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.dp(14), ui.dp(2), ui.dp(14), ui.dp(12));
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
        copy.setOnClickListener(new CopyAction(this, cmd, vi));
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
        debugSw.setChecked(prefs.getBoolean("debug", OverlayService.DEF_DEBUG));

        refreshHoldRows();
        updateVisualBadge();
    }

    void refreshHoldRows() {
        leftHoldDesc.setText(GestureActions.describe(this, leftHoldAction));
        rightHoldDesc.setText(GestureActions.describe(this, rightHoldAction));
        leftHoldIcon.setAction(leftHoldAction, null);
        rightHoldIcon.setAction(rightHoldAction, null);
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
            Toast.makeText(this,
                    Strings.pick(vi, "Đã lưu (hãy cấp quyền overlay để bật dải)", "Saved (grant overlay permission to enable)"),
                    Toast.LENGTH_LONG).show();
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
        if (v == langViBtn) {
            switchLang(Strings.LANG_VI);
        } else if (v == langEnBtn) {
            switchLang(Strings.LANG_EN);
        } else if (v == grantBtn) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else if (v == shizukuBtn) {
            onShizukuAction();
        } else if (v == appInfoBtn) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } else if (v == svcOnBtn) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this,
                        Strings.pick(vi, "Hãy cấp quyền overlay trước", "Please grant overlay permission first"),
                        Toast.LENGTH_LONG).show();
                return;
            }
            startOverlayService();
            autoHelperTried = false;
            refresh();
            uiHandler.postDelayed(new RefreshTask(this), 250);
            Toast.makeText(this, Strings.pick(vi, "Đã bật overlay", "Overlay started"), Toast.LENGTH_SHORT).show();
        } else if (v == svcOffBtn) {
            stopService(new Intent(this, OverlayService.class));
            OverlayService.running = false;
            refresh();
            Toast.makeText(this, Strings.pick(vi, "Đã tắt overlay", "Overlay stopped"), Toast.LENGTH_SHORT).show();
        } else if (v == leftHoldBtn) {
            showHoldActionPicker(true);
        } else if (v == rightHoldBtn) {
            showHoldActionPicker(false);
        } else if (v == resetGeomBtn) {
            sideEt.setText(String.valueOf(OverlayService.DEF_SIDE));
            bottomEt.setText(String.valueOf(OverlayService.DEF_BOTTOM));
            topGuardEt.setText(String.valueOf(OverlayService.DEF_TOP_GUARD));
            bottomGapEt.setText(String.valueOf(OverlayService.DEF_BOTTOM_GAP));
        } else if (v == applyGeomBtn) {
            saveSectionGeometry();
            restartServiceIfActive(Strings.pick(vi, "Đã áp dụng Kích thước & Vùng mép", "Applied Size & Safe Area"));
        } else if (v == applyHoldBtn) {
            saveSectionHold();
            restartServiceIfActive(Strings.pick(vi, "Đã áp dụng Cử chỉ & Nhấn giữ", "Applied Gestures & Hold"));
        } else if (v == applyVisualBtn) {
            saveSectionVisual();
            restartServiceIfActive(Strings.pick(vi, "Đã áp dụng Hiển thị & Hiệu ứng", "Applied Visual & Effects"));
        } else if (v == openAnimBtn || v == openAnimBtn.getParent()) {
            startActivityForResult(new Intent(this, AnimConfigActivity.class), REQ_ANIM);
        } else if (v == bottomCancelBtn) {
            loadFromPrefs();
            Toast.makeText(this, Strings.pick(vi, "Đã hủy thay đổi chưa áp dụng", "Reverted unapplied edits"),
                    Toast.LENGTH_SHORT).show();
        } else if (v == bottomApplyBtn) {
            saveSectionGeometry();
            saveSectionHold();
            saveSectionVisual();
            restartServiceIfActive(Strings.pick(vi, "Đã áp dụng toàn bộ cấu hình", "Applied all settings"));
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
                Toast.makeText(this, Strings.pick(vi, "Đã copy JSON backup", "Copied JSON backup"),
                        Toast.LENGTH_SHORT).show();
            }
        } else if (v == pasteJsonBtn) {
            if (!anyBackupSelected()) return;
            showPasteJsonDialog();
        }
    }

    private void switchLang(String lang) {
        boolean nextVi = Strings.LANG_VI.equals(lang);
        if (nextVi == vi) return;
        Strings.setLang(this, lang);
        if (OverlayService.running && Settings.canDrawOverlays(this)) {
            startOverlayService();
        }
        recreate();
    }

    private boolean anyBackupSelected() {
        if (bkGeomCb.isChecked() || bkHoldCb.isChecked() || bkAnimCb.isChecked()) return true;
        Toast.makeText(this,
                Strings.pick(vi, "Hãy chọn ít nhất 1 phần cấu hình", "Select at least one section"),
                Toast.LENGTH_SHORT).show();
        return false;
    }

    private void showPasteJsonDialog() {
        EditText et = new EditText(this);
        et.setHint(Strings.pick(vi, "Dán nội dung JSON vào đây...", "Paste JSON content here..."));
        et.setMinLines(4);
        et.setMaxLines(8);
        new AlertDialog.Builder(this)
                .setTitle(Strings.pick(vi, "Khôi phục từ JSON", "Restore from JSON"))
                .setView(et)
                .setPositiveButton(Strings.pick(vi, "Khôi phục", "Restore"), new PasteJsonListener(this, et))
                .setNegativeButton(Strings.pick(vi, "Hủy", "Cancel"), null)
                .show();
    }

    void applyJsonString(String raw) {
        try {
            String msg = ConfigBackup.importJson(getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE),
                    raw, bkGeomCb.isChecked(), bkHoldCb.isChecked(), bkAnimCb.isChecked(), vi);
            loadFromPrefs();
            if (OverlayService.running && Settings.canDrawOverlays(this)) startOverlayService();
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this,
                    Strings.pick(vi, "JSON không hợp lệ: ", "Invalid JSON: ") + t.getMessage(),
                    Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, Strings.pick(vi, "Đã xuất file backup JSON", "Exported JSON backup file"),
                        Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(this,
                        Strings.pick(vi, "Xuất file lỗi: ", "Export error: ") + t.getMessage(),
                        Toast.LENGTH_LONG).show();
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
                Toast.makeText(this,
                        Strings.pick(vi, "Đọc file lỗi: ", "Read error: ") + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showHoldActionPicker(boolean isLeft) {
        String current = isLeft ? leftHoldAction : rightHoldAction;
        ListView lv = new ListView(this);
        lv.setDivider(null);
        ActionListAdapter adapter = new ActionListAdapter(this, current);
        lv.setAdapter(adapter);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(isLeft
                        ? Strings.pick(vi, "Nhấn giữ mép trái", "Left edge hold action")
                        : Strings.pick(vi, "Nhấn giữ mép phải", "Right edge hold action"))
                .setView(lv)
                .setNegativeButton(Strings.pick(vi, "Đóng", "Close"), null)
                .create();
        lv.setOnItemClickListener(new ActionItemClick(this, isLeft, dlg));
        dlg.show();
    }

    void onHoldActionPicked(boolean isLeft, int index, AlertDialog dialog) {
        dialog.dismiss();
        String key = GestureActions.KEYS[index];
        if (GestureActions.APP_PREFIX.equals(key)) {
            Toast.makeText(this, Strings.pick(vi, "Đang tải danh sách app...", "Loading apps..."),
                    Toast.LENGTH_SHORT).show();
            new Thread(new LoadAppsTask(this, isLeft), "load-apps").start();
            return;
        }
        setHoldAction(isLeft, key);
    }

    void setHoldAction(boolean isLeft, String action) {
        if (isLeft) leftHoldAction = action;
        else rightHoldAction = action;
        refreshHoldRows();
    }

    void showAppPicker(boolean isLeft, AppEntry[] entries) {
        if (isFinishing() || isDestroyed()) return;
        if (entries.length == 0) {
            Toast.makeText(this, Strings.pick(vi, "Không tìm thấy ứng dụng nào", "No apps found"),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), 0);

        EditText searchEt = new EditText(this);
        searchEt.setHint(Strings.pick(vi, "Tìm tên app hoặc package...", "Search app name or package..."));
        searchEt.setSingleLine(true);
        searchEt.setTextSize(13);
        searchEt.setTextColor(ui.cText);
        searchEt.setHintTextColor(ui.cMuted);
        searchEt.setBackground(ui.roundRect(ui.cSub, ui.cBorder, 10));
        searchEt.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        wrap.addView(searchEt, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        wrap.addView(ui.vSpace(6));

        String current = isLeft ? leftHoldAction : rightHoldAction;
        AppListAdapter adapter = new AppListAdapter(this, entries, current);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setAdapter(adapter);
        wrap.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(340)));

        searchEt.addTextChangedListener(new AppSearchWatcher(adapter));

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(isLeft
                        ? Strings.pick(vi, "Chọn app cho mép trái", "Choose app for left hold")
                        : Strings.pick(vi, "Chọn app cho mép phải", "Choose app for right hold"))
                .setView(wrap)
                .setNegativeButton(Strings.pick(vi, "Hủy", "Cancel"), null)
                .create();
        lv.setOnItemClickListener(new AppItemClick(this, isLeft, adapter, dlg));
        dlg.show();
    }

    private void onShizukuAction() {
        if (shizukuState == ShizukuStarter.STATE_NOT_RUNNING) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(ShizukuStarter.SHIZUKU_PKG);
            if (launch != null) {
                startActivity(launch);
            } else {
                Toast.makeText(this, Strings.pick(vi, "Chưa cài Shizuku", "Shizuku not installed"),
                        Toast.LENGTH_SHORT).show();
            }
        } else if (shizukuState == ShizukuStarter.STATE_NO_PERMISSION) {
            new Thread(new RequestShizukuPermTask(this), "shizuku-perm").start();
        } else {
            shizukuBtn.setEnabled(false);
            Toast.makeText(this, Strings.pick(vi, "Đang bật helper qua Shizuku...", "Starting helper via Shizuku..."),
                    Toast.LENGTH_SHORT).show();
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

        ovVal.setText(ov ? Strings.pick(vi, "ĐÃ CẤP ✓", "GRANTED ✓") : Strings.pick(vi, "CHƯA CẤP ✗", "MISSING ✗"));
        ovVal.setTextColor(ov ? ui.cOkFg : ui.cErrFg);

        svcVal.setText(svc ? Strings.pick(vi, "ĐANG CHẠY ✓", "RUNNING ✓") : Strings.pick(vi, "ĐÃ TẮT ✗", "STOPPED ✗"));
        svcVal.setTextColor(svc ? ui.cOkFg : ui.cWarnFg);

        ovActionLabel.setVisibility(ov ? View.VISIBLE : View.GONE);
        grantBtn.setVisibility(ov ? View.GONE : View.VISIBLE);

        String checking = Strings.pick(vi, "Kiểm tra...", "Checking...");
        helperVal.setText(checking);
        helperVal.setTextColor(ui.cMuted);
        shzVal.setText(checking);
        shzVal.setTextColor(ui.cMuted);
        new Thread(new HelperProbe(this), "helper-probe").start();
    }

    void applyProbeResult(boolean alive, int shzState) {
        if (isFinishing() || isDestroyed()) return;
        shizukuState = shzState;
        boolean ov = Settings.canDrawOverlays(this);
        boolean svc = OverlayService.running;

        helperVal.setText(alive
                ? Strings.pick(vi, "SỐNG ✓", "ALIVE ✓")
                : Strings.pick(vi, "CHƯA CHẠY ✗", "DOWN ✗"));
        helperVal.setTextColor(alive ? ui.cOkFg : ui.cErrFg);

        shizukuBtn.setEnabled(true);
        if (shzState == ShizukuStarter.STATE_NOT_RUNNING) {
            shzVal.setText(Strings.pick(vi, "CHƯA BẬT ✗", "OFF ✗"));
            shzVal.setTextColor(ui.cWarnFg);
            helperDesc.setText(Strings.pick(vi, "Mở Shizuku hoặc chạy lệnh ADB bên dưới", "Open Shizuku or run ADB command below"));
            shizukuBtn.setText(Strings.pick(vi, "Mở Shizuku", "Open Shizuku"));
            shizukuBtn.setVisibility(alive ? View.GONE : View.VISIBLE);
        } else if (shzState == ShizukuStarter.STATE_NO_PERMISSION) {
            shzVal.setText(Strings.pick(vi, "CHƯA CẤP", "DENIED"));
            shzVal.setTextColor(ui.cWarnFg);
            helperDesc.setText(Strings.pick(vi, "Cần cấp quyền Shizuku cho EdgeGest", "Grant Shizuku permission to EdgeGest"));
            shizukuBtn.setText(Strings.pick(vi, "Cấp quyền Shizuku", "Grant Shizuku"));
            shizukuBtn.setVisibility(View.VISIBLE);
        } else {
            shzVal.setText(Strings.pick(vi, "SẴN SÀNG ✓", "READY ✓"));
            shzVal.setTextColor(ui.cOkFg);
            helperDesc.setText(Strings.pick(vi, "Khởi chạy qua Shizuku uid 2000", "Launch via Shizuku uid 2000"));
            shizukuBtn.setText(alive ? Strings.pick(vi, "Khởi động lại", "Restart") : Strings.pick(vi, "Bật Helper", "Start Helper"));
            shizukuBtn.setVisibility(View.VISIBLE);
            if (!alive && !autoHelperTried) {
                autoHelperTried = true;
                new Thread(new AutoStartHelperTask(this), "auto-shizuku").start();
            }
        }

        boolean allReady = ov && svc && alive;
        if (allReady) {
            ui.setPill(overallBadge, Strings.pick(vi, "Hoạt động", "Active"), ui.cOkFg, ui.cOkBg);
            if (!autoPermCollapsedOnce) {
                autoPermCollapsedOnce = true;
                permBody.setVisibility(View.GONE);
                permChevron.setExpanded(false, false);
            }
        } else {
            ui.setPill(overallBadge,
                    svc ? Strings.pick(vi, "Thiếu Helper/Quyền", "Missing Setup") : Strings.pick(vi, "Đã tắt", "Stopped"),
                    ui.cWarnFg, ui.cWarnBg);
            permBody.setVisibility(View.VISIBLE);
            permChevron.setExpanded(true, false);
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

        animSwatchDot.setBackground(ui.roundRect(col, ui.cBorder, 99));
        animSwatchDot.setAlpha(Math.max(0.2f, alpha / 100f));

        StringBuilder edges = new StringBuilder();
        if (al) edges.append(Strings.pick(vi, "Trái, ", "Left, "));
        if (ar) edges.append(Strings.pick(vi, "Phải, ", "Right, "));
        if (ab) edges.append(Strings.pick(vi, "Dưới, ", "Bottom, "));
        String eText = edges.length() > 0
                ? edges.substring(0, edges.length() - 2)
                : Strings.pick(vi, "Tắt hết", "All off");
        animSubLine.setText(eText + " · " + aw + "dp · " + len + "dp · " + alpha + "%");
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
        private final boolean vi;

        CopyAction(Context ctx, String cmd, boolean vi) {
            this.ctx = ctx;
            this.cmd = cmd;
            this.vi = vi;
        }

        @Override
        public void onClick(View v) {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("EdgeGest ADB", cmd));
                Toast.makeText(ctx,
                        Strings.pick(vi, "Đã copy lệnh vào bộ nhớ đệm", "Copied command to clipboard"),
                        Toast.LENGTH_SHORT).show();
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

    private static final class ActionListAdapter extends BaseAdapter {
        private final MainActivity act;
        private final String current;
        private final float density;

        ActionListAdapter(MainActivity act, String current) {
            this.act = act;
            this.current = current != null ? current : GestureActions.NONE;
            this.density = act.getResources().getDisplayMetrics().density;
        }

        @Override public int getCount() { return GestureActions.KEYS.length; }
        @Override public Object getItem(int position) { return GestureActions.KEYS[position]; }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            UiKit ui = act.ui;
            String key = GestureActions.KEYS[position];
            boolean selected = key.equals(current)
                    || (GestureActions.APP_PREFIX.equals(key) && current.startsWith(GestureActions.APP_PREFIX));

            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ui.dp(14), ui.dp(9), ui.dp(14), ui.dp(9));
            if (selected) row.setBackgroundColor(ui.dark ? 0x223B82F6 : 0x182563EB);

            FrameLayout box = new FrameLayout(act);
            box.setBackground(ui.roundRect(ui.cSub, selected ? ui.cPrimary : ui.cBorder, 10));
            ActionIcons.ActionIconView iv = new ActionIcons.ActionIconView(
                    act, selected ? ui.cPrimary : ui.cText, density);
            Drawable appD = GestureActions.APP_PREFIX.equals(key) && current.startsWith(GestureActions.APP_PREFIX)
                    ? ActionIcons.loadAppIcon(act, current.substring(GestureActions.APP_PREFIX.length()))
                    : null;
            iv.setAction(key, appD);
            box.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(ui.dp(34), ui.dp(34));
            bLp.rightMargin = ui.dp(12);
            row.addView(box, bLp);

            LinearLayout txt = new LinearLayout(act);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.addView(ui.text(GestureActions.labelAt(act.vi, position), 13,
                    selected ? ui.cPrimary : ui.cText, true));
            txt.addView(ui.text(GestureActions.subLabelAt(act.vi, position), 11, ui.cMuted, false));
            row.addView(txt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (selected) row.addView(ui.text("✓", 15, ui.cPrimary, true));
            return row;
        }
    }

    private static final class ActionItemClick implements AdapterView.OnItemClickListener {
        private final MainActivity act;
        private final boolean isLeft;
        private final AlertDialog dialog;

        ActionItemClick(MainActivity act, boolean isLeft, AlertDialog dialog) {
            this.act = act;
            this.isLeft = isLeft;
            this.dialog = dialog;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            act.onHoldActionPicked(isLeft, position, dialog);
        }
    }

    static final class AppEntry {
        final String label;
        final String pkg;
        final Drawable icon;

        AppEntry(String label, String pkg, Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.icon = icon;
        }
    }

    private static final class AppListAdapter extends BaseAdapter {
        private final MainActivity act;
        private final AppEntry[] all;
        private final List<AppEntry> filtered = new ArrayList<>();
        private final String currentPkg;

        AppListAdapter(MainActivity act, AppEntry[] all, String currentAction) {
            this.act = act;
            this.all = all;
            this.currentPkg = currentAction != null && currentAction.startsWith(GestureActions.APP_PREFIX)
                    ? currentAction.substring(GestureActions.APP_PREFIX.length()) : "";
            for (AppEntry e : all) filtered.add(e);
        }

        void filter(String q) {
            filtered.clear();
            String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            for (AppEntry e : all) {
                if (needle.isEmpty()
                        || e.label.toLowerCase(Locale.ROOT).contains(needle)
                        || e.pkg.toLowerCase(Locale.ROOT).contains(needle)) {
                    filtered.add(e);
                }
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            UiKit ui = act.ui;
            AppEntry e = filtered.get(position);
            boolean selected = e.pkg.equals(currentPkg);

            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
            if (selected) row.setBackgroundColor(ui.dark ? 0x223B82F6 : 0x182563EB);

            ImageView iv = new ImageView(act);
            if (e.icon != null) iv.setImageDrawable(e.icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36));
            iLp.rightMargin = ui.dp(12);
            row.addView(iv, iLp);

            LinearLayout txt = new LinearLayout(act);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.addView(ui.text(e.label, 13, selected ? ui.cPrimary : ui.cText, true));
            txt.addView(ui.text(e.pkg, 10, ui.cMuted, false));
            row.addView(txt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (selected) row.addView(ui.text("✓", 15, ui.cPrimary, true));
            return row;
        }
    }

    private static final class AppSearchWatcher implements TextWatcher {
        private final AppListAdapter adapter;

        AppSearchWatcher(AppListAdapter adapter) {
            this.adapter = adapter;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {
            adapter.filter(s.toString());
        }
    }

    private static final class AppItemClick implements AdapterView.OnItemClickListener {
        private final MainActivity act;
        private final boolean isLeft;
        private final AppListAdapter adapter;
        private final AlertDialog dialog;

        AppItemClick(MainActivity act, boolean isLeft, AppListAdapter adapter, AlertDialog dialog) {
            this.act = act;
            this.isLeft = isLeft;
            this.adapter = adapter;
            this.dialog = dialog;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            AppEntry e = (AppEntry) adapter.getItem(position);
            dialog.dismiss();
            act.setHoldAction(isLeft, GestureActions.APP_PREFIX + e.pkg);
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
                Drawable icon = null;
                try { icon = ri.loadIcon(pm); } catch (Throwable ignored) {}
                entries.add(new AppEntry(lbl != null ? lbl.toString() : pkg, pkg, icon));
            }
            Collections.sort(entries, new AppEntryComparator());
            AppEntry[] arr = entries.toArray(new AppEntry[0]);
            activity.uiHandler.post(new ShowAppPickerTask(activity, isLeft, arr));
        }
    }

    private static final class ShowAppPickerTask implements Runnable {
        private final MainActivity activity;
        private final boolean isLeft;
        private final AppEntry[] entries;

        ShowAppPickerTask(MainActivity activity, boolean isLeft, AppEntry[] entries) {
            this.activity = activity;
            this.isLeft = isLeft;
            this.entries = entries;
        }

        @Override
        public void run() {
            activity.showAppPicker(isLeft, entries);
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
            String msg = ok
                    ? Strings.pick(activity.vi, "Helper server đã chạy", "Helper server started")
                    : Strings.pick(activity.vi,
                            "Không bật được helper qua Shizuku (thử lệnh ADB bên dưới)",
                            "Failed to start helper via Shizuku (try ADB command below)");
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
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
