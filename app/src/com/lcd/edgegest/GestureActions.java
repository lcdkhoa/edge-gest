package com.lcd.edgegest;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Định nghĩa các hành động nhấn giữ cho cử chỉ kéo mép trái / phải.
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

    static final String[] LABELS = {
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

    private GestureActions() {}

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

    static String shortBadge(String action) {
        if (!isEnabled(action)) return "";
        if (action.startsWith(APP_PREFIX)) return "APP";
        if (SWITCH.equals(action)) return "⇄";
        if (RECENTS.equals(action)) return "▢";
        if (HOME.equals(action)) return "⌂";
        if (POWER.equals(action)) return "⏻";
        if (VOLUME.equals(action)) return "🔊";
        if (NOTIF.equals(action)) return "🔔";
        if (QS.equals(action)) return "⚙";
        if (SCREENSHOT.equals(action)) return "📷";
        if (LOCK.equals(action)) return "🔒";
        if (MEDIA.equals(action)) return "⏯";
        return "•";
    }

    static String describe(Context ctx, String action) {
        if (!isEnabled(action)) return LABELS[0];
        if (action.startsWith(APP_PREFIX)) {
            String pkg = action.substring(APP_PREFIX.length());
            if (ctx != null) {
                try {
                    PackageManager pm = ctx.getPackageManager();
                    CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                    if (label != null) return "Mở: " + label;
                } catch (Throwable ignored) {}
            }
            return "Mở: " + pkg;
        }
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(action)) return LABELS[i];
        }
        return LABELS[0];
    }
}
