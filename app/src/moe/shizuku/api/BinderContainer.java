package moe.shizuku.api;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Container Parcelable tương thích với moe.shizuku.api.BinderContainer trong Shizuku Manager,
 * giúp nhận IBinder từ ContentProvider mà không cần phụ thuộc thư viện Gradle ngoài.
 */
public final class BinderContainer implements Parcelable {
    public final IBinder binder;

    public static final Creator<BinderContainer> CREATOR = new BinderContainerCreator();

    public BinderContainer(IBinder binder) {
        this.binder = binder;
    }

    BinderContainer(Parcel in) {
        this.binder = in.readStrongBinder();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(binder);
    }
}
