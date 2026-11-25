package com.lcd.edgegest;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Màn hình cấu hình: hiển thị trạng thái live, cấp quyền overlay, Shizuku helper, tự bật và chỉnh dải mép.
 * Tuân thủ ràng buộc d8: chỉ dùng static nested class, không lambda/anonymous/inner class.
 */
public final class MainActivity extends Activity implements View.OnClickListener {
    private TextView status;
    private TextView helperStatus;
    private TextView shizukuStatus;
    private EditText sideEt, bottomEt, topGuardEt, bottomGapEt;
    private CheckBox debugCb;
    private Button grant, shizukuBtn, start, stop;
    Handler uiHandler;
    private int shizukuState = ShizukuStarter.STATE_NOT_RUNNING;
    private boolean autoHelperTried;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        uiHandler = new Handler(Looper.getMainLooper());
        SharedPreferences prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        if (Build.VERSION.SDK_INT >= 20) {
            scroll.setOnApplyWindowInsetsListener(new InsetPadding());
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("EdgeGest");
        title.setTextSize(22);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(15);
        root.addView(status);

        helperStatus = new TextView(this);
        helperStatus.setTextSize(14);
        helperStatus.setPadding(0, dp(4), 0, 0);
        root.addView(helperStatus);

        shizukuStatus = new TextView(this);
        shizukuStatus.setTextSize(14);
        shizukuStatus.setPadding(0, dp(4), 0, 0);
        root.addView(shizukuStatus);

        root.addView(space());

        grant = new Button(this);
        grant.setText("Cấp quyền hiển thị trên app khác");
        grant.setOnClickListener(this);
        root.addView(grant);

        shizukuBtn = new Button(this);
        shizukuBtn.setText("Shizuku / Bật Helper");
        shizukuBtn.setOnClickListener(this);
        root.addView(shizukuBtn);

        root.addView(label("Độ dày dải mép trái/phải (dp):"));
        sideEt = numField(prefs.getInt("side", OverlayService.DEF_SIDE));
        root.addView(sideEt);

        root.addView(label("Độ dày dải mép dưới (dp):"));
        bottomEt = numField(prefs.getInt("bottom", OverlayService.DEF_BOTTOM));
        root.addView(bottomEt);

        root.addView(label("Chừa mép trên tránh status bar (% chiều cao, 0–50):"));
        topGuardEt = numField(prefs.getInt("topGuard", OverlayService.DEF_TOP_GUARD));
        root.addView(topGuardEt);

        root.addView(label("Chừa thêm trên dải đáy cho dải bên (dp, tránh nút/bàn phím):"));
        bottomGapEt = numField(prefs.getInt("bottomGap", OverlayService.DEF_BOTTOM_GAP));
        root.addView(bottomGapEt);

        debugCb = new CheckBox(this);
        debugCb.setText("Hiện màu dải (debug)");
        debugCb.setChecked(prefs.getBoolean("debug", OverlayService.DEF_DEBUG));
        root.addView(debugCb);

        root.addView(space());

        start = new Button(this);
        start.setText("BẬT / Áp dụng");
        start.setOnClickListener(this);
        root.addView(start);

        stop = new Button(this);
        stop.setText("TẮT");
        stop.setOnClickListener(this);
        root.addView(stop);

        TextView note = new TextView(this);
        note.setText("\nLưu ý: cần helper injector đang chạy (127.0.0.1:47820 qua ADB/Shizuku) thì phím Back/Home/Recents mới hoạt động.");
        note.setTextSize(12);
        root.addView(note);

        scroll.addView(root);
        setContentView(scroll);

        // Tự động bật service khi mở app nếu đã có quyền overlay mà service chưa chạy.
        if (Settings.canDrawOverlays(this) && !OverlayService.running) {
            startOverlayService();
            uiHandler.postDelayed(new RefreshTask(this), 250);
        }
        // Nếu helper chưa chạy và Shizuku đã cấp quyền sẵn, tự khởi chạy helper.
        ShizukuStarter.setOnBinderListener(new BinderArrivedTask(this));
        new Thread(new AutoStartHelperTask(this), "auto-shizuku").start();
    }

    @Override
    protected void onDestroy() {
        ShizukuStarter.setOnBinderListener(null);
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (v == grant) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } else if (v == shizukuBtn) {
            onShizukuAction();
        } else if (v == start) {
            applyAndStart();
        } else if (v == stop) {
            stopService(new Intent(this, OverlayService.class));
            OverlayService.running = false;
            refresh();
            Toast.makeText(this, "Đã tắt overlay", Toast.LENGTH_SHORT).show();
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
        e.putBoolean("debug", debugCb.isChecked());
        e.apply();

        startOverlayService();
        autoHelperTried = false;
        refresh();
        uiHandler.postDelayed(new RefreshTask(this), 250);
        Toast.makeText(this, "Đã bật overlay", Toast.LENGTH_SHORT).show();
    }

    private void startOverlayService() {
        Intent svc = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    void refresh() {
        boolean ov = Settings.canDrawOverlays(this);
        boolean svc = OverlayService.running;
        status.setText("Quyền overlay: " + (ov ? "ĐÃ CẤP ✓" : "CHƯA CẤP ✗")
                + "\nOverlayService: " + (svc ? "ĐANG CHẠY ✓" : "ĐÃ TẮT ✗"));
        grant.setVisibility(ov ? View.GONE : View.VISIBLE);
        helperStatus.setText("Helper server (127.0.0.1:47820): đang kiểm tra...");
        shizukuStatus.setText("Shizuku: đang kiểm tra...");
        new Thread(new HelperProbe(this), "helper-probe").start();
    }

    void applyProbeResult(boolean alive, int shzState) {
        if (isFinishing() || isDestroyed()) return;
        shizukuState = shzState;
        helperStatus.setText("Helper server (127.0.0.1:47820): "
                + (alive ? "ĐANG CHẠY ✓" : "CHƯA CHẠY ✗"));
        shizukuBtn.setEnabled(true);
        if (shzState == ShizukuStarter.STATE_NOT_RUNNING) {
            shizukuStatus.setText("Shizuku: CHƯA CHẠY ✗");
            shizukuBtn.setText("Mở Shizuku để kích hoạt");
            shizukuBtn.setVisibility(alive ? View.GONE : View.VISIBLE);
        } else if (shzState == ShizukuStarter.STATE_NO_PERMISSION) {
            shizukuStatus.setText("Shizuku: ĐÃ CHẠY — CHƯA CẤP QUYỀN");
            shizukuBtn.setText("Cấp quyền Shizuku cho EdgeGest");
            shizukuBtn.setVisibility(View.VISIBLE);
        } else {
            shizukuStatus.setText("Shizuku: SẴN SÀNG ✓");
            shizukuBtn.setText(alive ? "Khởi động lại Helper qua Shizuku" : "Bật Helper qua Shizuku");
            shizukuBtn.setVisibility(View.VISIBLE);
            if (!alive && !autoHelperTried) {
                autoHelperTried = true;
                new Thread(new AutoStartHelperTask(this), "auto-shizuku").start();
            }
        }
    }

    private int parse(EditText et, int def) {
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (Exception e) { return def; }
    }

    private EditText numField(int val) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(val));
        return et;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setPadding(0, dp(12), 0, 0);
        return t;
    }

    private View space() {
        View v = new View(this);
        v.setMinimumHeight(dp(12));
        return v;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
