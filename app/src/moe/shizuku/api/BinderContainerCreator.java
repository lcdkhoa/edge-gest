package moe.shizuku.api;

import android.os.Parcel;
import android.os.Parcelable;

@SuppressWarnings("rawtypes")
public final class BinderContainerCreator implements Parcelable.Creator {
    @Override
    public Object createFromParcel(Parcel in) {
        return new BinderContainer(in);
    }

    @Override
    public Object[] newArray(int size) {
        return new BinderContainer[size];
    }
}
