package com.lcd.edgegest;

import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONObject;

/**
 * Xuất / nhập cấu hình JSON chọn lọc theo từng phần (geometry, hold, anim).
 */
final class ConfigBackup {
    private ConfigBackup() {}

    static String formatHex(int color) {
        return String.format("#%06X", color & 0x00FFFFFF);
    }

    static int parseHex(String s, int fallback) {
        if (s == null) return fallback;
        try {
            String t = s.trim();
            if (!t.startsWith("#")) t = "#" + t;
            return Color.parseColor(t) | 0xFF000000;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    static String exportJson(SharedPreferences prefs, boolean incGeom, boolean incHold, boolean incAnim) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            if (incGeom) {
                JSONObject g = new JSONObject();
                g.put("side", prefs.getInt("side", OverlayService.DEF_SIDE));
                g.put("bottom", prefs.getInt("bottom", OverlayService.DEF_BOTTOM));
                g.put("topGuard", prefs.getInt("topGuard", OverlayService.DEF_TOP_GUARD));
                g.put("bottomGap", prefs.getInt("bottomGap", OverlayService.DEF_BOTTOM_GAP));
                root.put("geometry", g);
            }
            if (incHold) {
                JSONObject h = new JSONObject();
                h.put("leftHold", prefs.getString("leftHold", OverlayService.DEF_LEFT_HOLD));
                h.put("rightHold", prefs.getString("rightHold", OverlayService.DEF_RIGHT_HOLD));
                h.put("holdMs", prefs.getInt("holdMs", OverlayService.DEF_HOLD_MS));
                root.put("hold", h);
            }
            if (incAnim) {
                JSONObject a = new JSONObject();
                a.put("anim", prefs.getBoolean("anim", OverlayService.DEF_ANIM));
                a.put("animLeft", prefs.getBoolean("animLeft", OverlayService.DEF_ANIM_LEFT));
                a.put("animRight", prefs.getBoolean("animRight", OverlayService.DEF_ANIM_RIGHT));
                a.put("animBottom", prefs.getBoolean("animBottom", OverlayService.DEF_ANIM_BOTTOM));
                a.put("animColor", formatHex(prefs.getInt("animColor", OverlayService.DEF_ANIM_COLOR)));
                a.put("animWidth", prefs.getInt("animWidth", OverlayService.DEF_ANIM_WIDTH));
                a.put("animLength", prefs.getInt("animLength", OverlayService.DEF_ANIM_LENGTH));
                a.put("animAlpha", prefs.getInt("animAlpha", OverlayService.DEF_ANIM_ALPHA));
                a.put("debug", prefs.getBoolean("debug", OverlayService.DEF_DEBUG));
                root.put("anim", a);
            }
            return root.toString(2);
        } catch (Throwable t) {
            return "{}";
        }
    }

    static String importJson(SharedPreferences prefs, String raw,
            boolean incGeom, boolean incHold, boolean incAnim, boolean vi) throws Exception {
        JSONObject root = new JSONObject(raw);
        SharedPreferences.Editor ed = prefs.edit();
        StringBuilder applied = new StringBuilder();

        if (incGeom && root.has("geometry")) {
            JSONObject g = root.getJSONObject("geometry");
            if (g.has("side")) ed.putInt("side", OverlayService.clamp(g.getInt("side"), 8, 120));
            if (g.has("bottom")) ed.putInt("bottom", OverlayService.clamp(g.getInt("bottom"), 8, 120));
            if (g.has("topGuard")) ed.putInt("topGuard", OverlayService.clamp(g.getInt("topGuard"), 0, 50));
            if (g.has("bottomGap")) ed.putInt("bottomGap", OverlayService.clamp(g.getInt("bottomGap"), 0, 400));
            applied.append(vi ? "Vùng mép, " : "Geometry, ");
        }
        if (incHold && root.has("hold")) {
            JSONObject h = root.getJSONObject("hold");
            if (h.has("leftHold")) ed.putString("leftHold", h.getString("leftHold"));
            if (h.has("rightHold")) ed.putString("rightHold", h.getString("rightHold"));
            if (h.has("holdMs")) ed.putInt("holdMs", OverlayService.clamp(h.getInt("holdMs"), 180, 800));
            applied.append(vi ? "Nhấn giữ, " : "Hold, ");
        }
        if (incAnim && root.has("anim")) {
            JSONObject a = root.getJSONObject("anim");
            boolean al = a.optBoolean("animLeft", prefs.getBoolean("animLeft", OverlayService.DEF_ANIM_LEFT));
            boolean ar = a.optBoolean("animRight", prefs.getBoolean("animRight", OverlayService.DEF_ANIM_RIGHT));
            boolean ab = a.optBoolean("animBottom", prefs.getBoolean("animBottom", OverlayService.DEF_ANIM_BOTTOM));
            ed.putBoolean("animLeft", al);
            ed.putBoolean("animRight", ar);
            ed.putBoolean("animBottom", ab);
            ed.putBoolean("anim", a.optBoolean("anim", al || ar || ab));
            if (a.has("animColor")) {
                ed.putInt("animColor", parseHex(a.getString("animColor"), OverlayService.DEF_ANIM_COLOR));
            }
            if (a.has("animWidth")) ed.putInt("animWidth", OverlayService.clamp(a.getInt("animWidth"), 24, 80));
            if (a.has("animLength")) ed.putInt("animLength", OverlayService.clamp(a.getInt("animLength"), 80, 220));
            if (a.has("animAlpha")) ed.putInt("animAlpha", OverlayService.clamp(a.getInt("animAlpha"), 15, 100));
            if (a.has("debug")) ed.putBoolean("debug", a.getBoolean("debug"));
            applied.append("Animation, ");
        }
        ed.apply();
        if (applied.length() == 0) {
            return vi ? "Không có phần khớp để khôi phục" : "No matching section to restore";
        }
        applied.setLength(applied.length() - 2);
        return (vi ? "Đã khôi phục: " : "Restored: ") + applied;
    }
}
