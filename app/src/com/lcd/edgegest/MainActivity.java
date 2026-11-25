package com.lcd.edgegest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Màn hình cấu hình: cấp quyền overlay, bật/tắt, chỉnh độ dày dải. */
public final class MainActivity extends Activity implements View.OnClickListener {
    private TextView status;
    private EditText sideEt, bottomEt;
    private CheckBox debugCb;
    private Button grant, start, stop;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setTextSize(15);
        root.addView(status);

        root.addView(space());

        grant = new Button(this);
        grant.setText("Cấp quyền hiển thị trên app khác");
        grant.setOnClickListener(this);
        root.addView(grant);

        root.addView(label("Độ dày dải mép trái/phải (dp):"));
        sideEt = numField(prefs.getInt("side", OverlayService.DEF_SIDE));
        root.addView(sideEt);

        root.addView(label("Độ dày dải mép dưới (dp):"));
        bottomEt = numField(prefs.getInt("bottom", OverlayService.DEF_BOTTOM));
        root.addView(bottomEt);

        debugCb = new CheckBox(this);
        debugCb.setText("Hiện màu dải (debug)");
        debugCb.setChecked(prefs.getBoolean("debug", false));
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
        note.setText("\nLưu ý: cần helper injector đang chạy (qua ADB/Shizuku) thì phím Back/Home/Recents mới hoạt động. Xem README.");
        note.setTextSize(12);
        root.addView(note);

        setContentView(root);
    }

    @Override
    public void onClick(View v) {
        if (v == grant) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } else if (v == start) {
            applyAndStart();
        } else if (v == stop) {
            stopService(new Intent(this, OverlayService.class));
            refresh();
            Toast.makeText(this, "Đã tắt overlay", Toast.LENGTH_SHORT).show();
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
        e.putBoolean("debug", debugCb.isChecked());
        e.apply();

        Intent svc = new Intent(this, OverlayService.class);
        stopService(svc);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);
        refresh();
        Toast.makeText(this, "Đã bật overlay", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean ov = Settings.canDrawOverlays(this);
        status.setText("Quyền overlay: " + (ov ? "ĐÃ CẤP ✓" : "CHƯA CẤP ✗"));
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
}
