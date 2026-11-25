package com.lcd.edgegest;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Từ điển đa ngôn ngữ EN / VI cho toàn bộ EdgeGest UI.
 * Lưu tuỳ chọn trong SharedPreferences key "lang" ("system", "vi", "en").
 */
final class Strings {
    static final String LANG_SYSTEM = "system";
    static final String LANG_VI = "vi";
    static final String LANG_EN = "en";

    private Strings() {}

    static String getSavedLang(Context ctx) {
        if (ctx == null) return LANG_SYSTEM;
        return ctx.getSharedPreferences(OverlayService.PREFS, Context.MODE_PRIVATE)
                .getString("lang", LANG_SYSTEM);
    }

    static boolean isVi(Context ctx) {
        String pref = getSavedLang(ctx);
        if (LANG_VI.equals(pref)) return true;
        if (LANG_EN.equals(pref)) return false;
        String sys = Locale.getDefault().getLanguage();
        return "vi".equalsIgnoreCase(sys);
    }

    static void setLang(Context ctx, String lang) {
        SharedPreferences prefs = ctx.getSharedPreferences(OverlayService.PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString("lang", lang).apply();
    }

    static String pick(Context ctx, String vi, String en) {
        return isVi(ctx) ? vi : en;
    }

    static String pick(boolean vi, String viStr, String enStr) {
        return vi ? viStr : enStr;
    }
}
