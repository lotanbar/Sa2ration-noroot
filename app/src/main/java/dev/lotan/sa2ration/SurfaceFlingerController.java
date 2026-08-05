package dev.lotan.sa2ration;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.IOException;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

final class SurfaceFlingerController {
    private static final String SERVICE = "color_display";
    private static final String DESCRIPTOR = "android.hardware.display.IColorDisplayManager";
    private static final int TRANSACTION_SET_SATURATION = 2;

    private SurfaceFlingerController() {}

    static void setSaturation(float value) throws IOException, RemoteException {
        int level = Math.round(Math.max(0.0f, Math.min(value, 1.0f)) * 100.0f);
        IBinder systemBinder = SystemServiceHelper.getSystemService(SERVICE);
        if (systemBinder == null) {
            throw new IOException("Color display service was not found");
        }

        IBinder privilegedBinder = new ShizukuBinderWrapper(systemBinder);
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(level);
            if (!privilegedBinder.transact(TRANSACTION_SET_SATURATION, data, reply, 0)) {
                throw new IOException("Color display service rejected the request");
            }
            reply.readException();
            if (reply.readInt() == 0) {
                throw new IOException("The saturation change was not applied");
            }
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
