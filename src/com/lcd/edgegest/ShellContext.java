package com.lcd.edgegest;

import android.content.Context;
import android.content.ContextWrapper;

/** Context giả mạo package "com.android.shell" để khớp với uid 2000 (shell). */
final class ShellContext extends ContextWrapper {
    static final String PKG = "com.android.shell";

    ShellContext(Context base) {
        super(base);
    }

    @Override
    public String getPackageName() {
        return PKG;
    }

    @Override
    public String getOpPackageName() {
        return PKG;
    }

    // @hide trong Context, override theo chữ ký để ViewRootImpl dùng đúng package.
    public String getBasePackageName() {
        return PKG;
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }
}
