package dev.lotan.sa2ration;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import rikka.shizuku.Shizuku;

public final class SaturationApplication extends Application {
    static final String PREFS = "saturation";
    static final String KEY_VALUE = "value";

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        SharedPreferences preferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return;

        try {
            SurfaceFlingerController.setSaturation(preferences.getFloat(KEY_VALUE, 1.0f));
        } catch (Exception ignored) {
            // The Activity reports actionable errors when the user opens the app.
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/os/ServiceManager;");
        }
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
    }
}
