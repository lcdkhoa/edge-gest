package com.lcd.edgegest;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import moe.shizuku.api.BinderContainer;

/**
 * ContentProvider nhận Binder từ shizuku_server ("sendBinder").
 * Khai báo trong AndroidManifest với authorities="com.lcd.edgegest.shizuku",
 * exported=true, multiprocess=false, permission=INTERACT_ACROSS_USERS_FULL.
 */
public final class ShizukuProvider extends ContentProvider {
    private static final String METHOD_SEND_BINDER = "sendBinder";
    private static final String EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER";

    @Override
    public boolean onCreate() {
        // Thử hỏi Sui nếu có sẵn; với Shizuku thường, shizuku_server sẽ tự gọi "sendBinder".
        ShizukuStarter.tryAcquireSuiBinder();
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_SEND_BINDER.equals(method) && extras != null) {
            try {
                extras.setClassLoader(BinderContainer.class.getClassLoader());
                BinderContainer bc = extras.getParcelable(EXTRA_BINDER);
                if (bc != null && bc.binder != null) {
                    Log.i(OverlayService.TAG, "ShizukuProvider nhận binder từ server");
                    ShizukuStarter.onBinderReceived(bc.binder);
                }
            } catch (Throwable t) {
                Log.w(OverlayService.TAG, "ShizukuProvider sendBinder lỗi: " + t);
            }
            return new Bundle();
        }
        return new Bundle();
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] sa, String so) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] sa) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] sa) { return 0; }
}
