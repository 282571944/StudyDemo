package com.luomin.learnbinder;

import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MusicPlayerService extends Binder {
    @Override
    protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
        switch (code){
            case 1000:
                data.enforceInterface("MusicPlayerService");
                String audioPath = data.readString();
                start(audioPath);
                break;
        }

        return super.onTransact(code, data, reply, flags);
    }

    public void start(String audioPath) {

    }

    public void stop() {
    }
}
