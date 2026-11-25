package com.lcd.edgegest;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Định nghĩa các hành động nhấn giữ cho cử chỉ kéo mép trái / phải (hỗ trợ song ngữ VI / EN).
 * Lưu trong SharedPreferences dưới dạng key chuỗi (vd: "none", "power", "app:com.spotify.music").
 */
final class GestureActions {
    static final String NONE = "none";
    static final String SWITCH = "switch";
    static final String RECENTS = "recents";
    static final String HOME = "home";
    static final String POWER = "power";
    static final String VOLUME = "volume";
    static final String NOTIF = "notif";
    static final String QS = "qs";
    static final String SCREENSHOT = "screenshot";
    static final String LOCK = "lock";
    static final String MEDIA = "media";
    static final String APP_PREFIX = "app:";

    static final String[] KEYS = {
        NONE, SWITCH, RECENTS, HOME, POWER, VOLUME, NOTIF, QS, SCREENSHOT, LOCK, MEDIA, APP_PREFIX
    };

    private static final String[] LABELS_VI = {
        "Không dùng (chỉ Back)",
        "Chuyển app gần nhất",
        "Đa nhiệm (Recents)",
        "Màn hình chính (Home)",
        "Menu Nguồn (Power Dialog)",
        "Bảng Âm lượng",
        "Mở Thông báo",
        "Cài đặt nhanh (Quick Settings)",
        "Chụp màn hình",
        "Khoá màn hình",
        "Phát / Tạm dừng nhạc",
        "Mở một App tuỳ chọn..."
    };

    private static final String[] LABELS_EN = {
        "Disabled (Back only)",
        "Switch to previous app",
        "Recent apps",
        "Home screen",
        "Power menu",
        "Volume panel",
        "Notifications shade",
        "Quick Settings",
        "Take screenshot",
        "Lock screen",
        "Play / Pause media",
        "Launch a specific app..."
    };

    private static final String[] SUBS_VI = {
        "Vuốt mép luôn quay lại",
        "Nhảy nhanh sang app vừa dùng",
        "Mở giao diện đa nhiệm",
        "Về màn hình launcher",
        "Tắt máy / Khởi động lại",
        "Hiện thanh điều chỉnh âm lượng",
        "Kéo bảng thông báo xuống",
        "Mở Wi-Fi / 4G / Đèn pin nhanh",
        "Lưu ảnh chụp màn hình hiện tại",
        "Tắt màn hình ngay lập tức",
        "Điều khiển trình phát hiện tại",
        "Chọn app kèm icon riêng của app"
    };

    private static final String[] SUBS_EN = {
        "Swipe edge only triggers Back",
        "Jump to last active app",
        "Open overview / recents",
        "Go to home screen",
        "Open system power dialog",
        "Show system volume slider",
        "Expand notifications panel",
        "Expand Quick Settings tiles",
        "Capture current screen",
        "Turn screen off immediately",
        "Toggle active media playback",
        "Pick an app with its real icon"
    };

    private static final String[] SHORT_VI = {
        "—", "Đổi app", "Đa nhiệm", "Home", "Nguồn", "Âm lượng",
        "Thông báo", "CĐ nhanh", "Chụp MH", "Khoá", "Nhạc", "App"
    };

    private static final String[] SHORT_EN = {
        "—", "Switch", "Recents", "Home", "Power", "Volume",
        "Notif", "QuickSet", "Shot", "Lock", "Media", "App"
    };

    private GestureActions() {}

    static String labelAt(boolean vi, int idx) {
        return vi ? LABELS_VI[idx] : LABELS_EN[idx];
    }

    static String subLabelAt(boolean vi, int idx) {
        return vi ? SUBS_VI[idx] : SUBS_EN[idx];
    }

    static boolean isEnabled(String action) {
        return action != null && !action.isEmpty() && !NONE.equals(action);
    }

    static String toWireCommand(String action) {
        if (!isEnabled(action)) return null;
        if (action.startsWith(APP_PREFIX) && action.length() > APP_PREFIX.length()) {
            return "O:" + action.substring(APP_PREFIX.length());
        }
        if (SWITCH.equals(action)) return "S";
        if (RECENTS.equals(action)) return "R";
        if (HOME.equals(action)) return "H";
        if (POWER.equals(action)) return "P";
        if (VOLUME.equals(action)) return "V";
        if (NOTIF.equals(action)) return "N";
        if (QS.equals(action)) return "Q";
        if (SCREENSHOT.equals(action)) return "C";
        if (LOCK.equals(action)) return "L";
        if (MEDIA.equals(action)) return "M";
        return null;
    }

    static String shortLabel(Context ctx, String action) {
        boolean vi = Strings.isVi(ctx);
        if (!isEnabled(action)) return "—";
        if (action.startsWith(APP_PREFIX)) {
            String app = appLabel(ctx, action.substring(APP_PREFIX.length()));
            return app != null ? app : "App";
        }
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(action)) return vi ? SHORT_VI[i] : SHORT_EN[i];
        }
        return "—";
    }

    static String describe(Context ctx, String action) {
        boolean vi = Strings.isVi(ctx);
        if (!isEnabled(action)) return vi ? LABELS_VI[0] : LABELS_EN[0];
        if (action.startsWith(APP_PREFIX)) {
            String pkg = action.substring(APP_PREFIX.length());
            String prefix = vi ? "Mở: " : "Launch: ";
            String name = appLabel(ctx, pkg);
            return prefix + (name != null ? name : pkg);
        }
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(action)) return vi ? LABELS_VI[i] : LABELS_EN[i];
        }
        return vi ? LABELS_VI[0] : LABELS_EN[0];
    }

    private static String appLabel(Context ctx, String pkg) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (label != null) return label.toString();
        } catch (Throwable ignored) {}
        return null;
    }
}
