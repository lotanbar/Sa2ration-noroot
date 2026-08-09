package dev.lotan.sa2ration;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int SHIZUKU_PERMISSION_REQUEST = 42;
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final String SHIZUKU_DOWNLOAD = "https://shizuku.rikka.app/download/";

    private enum SetupState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private SharedPreferences preferences;
    private SeekBar saturation;
    private TextView saturationPercentage;
    private TextView logView;
    private Button start;
    private SetupState setupState;
    private boolean applyScheduled;
    private boolean permissionRequestInFlight;

    private final Runnable liveApply = () -> {
        applyScheduled = false;
        applyLevel(saturation.getProgress(), false);
    };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () ->
            runOnUiThread(() -> {
                addLog("Shizuku service connected");
                checkSetup(false);
            });

    private final Shizuku.OnBinderDeadListener binderDeadListener = () ->
            runOnUiThread(() -> {
                addLog("Shizuku service stopped");
                checkSetup(false);
            });

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_PERMISSION_REQUEST) return;
                runOnUiThread(() -> {
                    permissionRequestInFlight = false;
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        addLog("Permission granted");
                        checkSetup(false);
                        applyLevel(saturation.getProgress(), true);
                    } else {
                        addLog("Permission denied");
                        showMessage(R.string.shizuku_permission_denied);
                        checkSetup(false);
                    }
                });
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(SaturationApplication.PREFS, MODE_PRIVATE);
        saturation = findViewById(R.id.saturation);
        saturationPercentage = findViewById(R.id.saturation_percentage);
        logView = findViewById(R.id.log);
        start = findViewById(R.id.start);
        logView.setMovementMethod(new ScrollingMovementMethod());

        int savedProgress = Math.max(0, Math.min(100,
                Math.round(preferences.getFloat(SaturationApplication.KEY_VALUE, 1.0f) * 100)));
        saturation.setProgress(savedProgress);
        updatePercentage(savedProgress);

        saturation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updatePercentage(progress);
                if (!fromUser) return;
                saveValue();
                scheduleLiveApply();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(liveApply);
                applyScheduled = false;
                saveValue();
                applyLevel(seekBar.getProgress(), true);
            }
        });

        start.setOnClickListener(view -> checkSetup(true));

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        addLog("App started; checking setup");
        checkSetup(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(() -> checkSetup(false), 300);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        super.onDestroy();
    }

    private void scheduleLiveApply() {
        if (applyScheduled) return;
        applyScheduled = true;
        handler.postDelayed(liveApply, 16);
    }

    private void checkSetup(boolean advance) {
        SetupState newState;
        if (!isShizukuInstalled()) {
            newState = SetupState.NOT_INSTALLED;
        } else if (!Shizuku.pingBinder()) {
            newState = SetupState.NOT_RUNNING;
        } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            newState = SetupState.NO_PERMISSION;
        } else {
            newState = SetupState.READY;
        }

        if (newState != setupState) {
            setupState = newState;
            switch (newState) {
                case NOT_INSTALLED:
                    start.setText(R.string.install_shizuku);
                    addLog("Shizuku is not installed");
                    break;
                case NOT_RUNNING:
                    start.setText(R.string.start_shizuku);
                    addLog("Shizuku is installed but not running");
                    break;
                case NO_PERMISSION:
                    start.setText(R.string.grant_access);
                    addLog("Waiting for Shizuku permission");
                    break;
                case READY:
                    start.setText(R.string.ready);
                    addLog("Setup complete; saturation control is ready");
                    applyLevel(saturation.getProgress(), false);
                    break;
            }
        }

        if (!advance) return;
        switch (newState) {
            case NOT_INSTALLED:
                addLog("Opening official Shizuku download");
                showMessage(R.string.installing_shizuku);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD)));
                break;
            case NOT_RUNNING:
                addLog("Opening Shizuku to start its service");
                showMessage(R.string.starting_shizuku);
                Intent launch = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
                if (launch != null) startActivity(launch);
                break;
            case NO_PERMISSION:
                if (!permissionRequestInFlight && !Shizuku.shouldShowRequestPermissionRationale()) {
                    permissionRequestInFlight = true;
                    addLog("Requesting Shizuku permission");
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
                } else {
                    addLog("Permission must be enabled in Shizuku");
                    showMessage(R.string.shizuku_permission_denied);
                }
                break;
            case READY:
                addLog("Everything is ready");
                applyLevel(saturation.getProgress(), true);
                break;
        }
    }

    private boolean isShizukuInstalled() {
        try {
            getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    private void applyLevel(int level, boolean writeLog) {
        if (setupState != SetupState.READY) {
            if (writeLog) addLog("Cannot apply until setup is ready");
            return;
        }
        try {
            SurfaceFlingerController.setSaturation(level / 100.0f);
            if (writeLog) addLog("Saturation applied");
        } catch (Exception exception) {
            String message = exception.getMessage();
            if (message == null || message.isEmpty()) message = exception.getClass().getSimpleName();
            addLog("Apply failed: " + message);
            if (writeLog) showMessage(R.string.saturation_failed);
        }
    }

    private void saveValue() {
        preferences.edit()
                .putFloat(SaturationApplication.KEY_VALUE, saturation.getProgress() / 100.0f)
                .apply();
    }

    private void updatePercentage(int progress) {
        saturationPercentage.setText(getString(R.string.saturation_percentage, progress));
    }

    private void addLog(String message) {
        String line = timeFormat.format(new Date()) + "  " + message;
        if (logView.length() == 0) logView.setText(line);
        else logView.append("\n" + line);
        logView.post(() -> {
            if (logView.getLayout() == null) return;
            int scroll = logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
            logView.scrollTo(0, Math.max(0, scroll));
        });
    }

    private void showMessage(int stringId) {
        Toast.makeText(this, stringId, Toast.LENGTH_LONG).show();
    }
}
