package io.github.cesiumdeveloper.soundorbit;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    private static final int REQ_POST_NOTIFICATIONS = 3003;

    private TextView status;
    private EditText depthField;
    private EditText periodField;
    private EditText ratioField;
    private Button toggle;
    private RadioGroup engineGroup;
    private Spinner channelSpinner;
    private TextView captureStatus;
    private Button captureAuth;
    private android.widget.LinearLayout hrtfParams;
    private EditText hrtfAzimuthField;
    private EditText hrtfRadiusField;
    private EditText hrtfElevationField;
    private android.widget.CheckBox periodRandomCheck;
    private android.widget.LinearLayout periodRandomRange;
    private EditText periodRandomMinField;
    private EditText periodRandomMaxField;

    private CaptureEngine captureEngine;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable statusRefresh = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            uiHandler.postDelayed(this, 300);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        depthField = findViewById(R.id.depth);
        periodField = findViewById(R.id.period);
        ratioField = findViewById(R.id.ratio);
        toggle = findViewById(R.id.toggle);
        engineGroup = findViewById(R.id.engineGroup);
        channelSpinner = findViewById(R.id.channelSpinner);
        captureStatus = findViewById(R.id.shizukuStatus);
        captureAuth = findViewById(R.id.shizukuAuth);
        hrtfParams = findViewById(R.id.hrtfParams);
        hrtfAzimuthField = findViewById(R.id.hrtfAzimuth);
        hrtfRadiusField = findViewById(R.id.hrtfRadius);
        hrtfElevationField = findViewById(R.id.hrtfElevation);
        periodRandomCheck = findViewById(R.id.periodRandomCheck);
        periodRandomRange = findViewById(R.id.periodRandomRange);
        periodRandomMinField = findViewById(R.id.periodRandomMin);
        periodRandomMaxField = findViewById(R.id.periodRandomMax);

        captureEngine = new CaptureEngine(this);
        captureEngine.setAuthCallback(this::refreshStatus);

        depthField.setText(Float.toString(Prefs.getDepth(this)));
        periodField.setText(Float.toString(Prefs.getPeriod(this)));
        ratioField.setText(Float.toString(Prefs.getRatio(this)));
        hrtfAzimuthField.setText(Float.toString(Prefs.getHrtfAzimuth(this)));
        hrtfRadiusField.setText(Float.toString(Prefs.getHrtfRadius(this)));
        hrtfElevationField.setText(Float.toString(Prefs.getHrtfElevation(this)));
        periodRandomCheck.setChecked(Prefs.getPeriodRandom(this));
        periodRandomMinField.setText(Float.toString(Prefs.getPeriodRandomMin(this)));
        periodRandomMaxField.setText(Float.toString(Prefs.getPeriodRandomMax(this)));
        periodRandomRange.setVisibility(periodRandomCheck.isChecked()
                ? android.view.View.VISIBLE : android.view.View.GONE);
        periodRandomCheck.setOnCheckedChangeListener((button, checked) ->
                periodRandomRange.setVisibility(checked
                        ? android.view.View.VISIBLE : android.view.View.GONE));

        channelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int pos, long id) {
                hrtfParams.setVisibility(CaptureEngine.isHrtf(pos)
                        ? android.view.View.VISIBLE : android.view.View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        toggle.setOnClickListener(v -> toggleEngine());

        findViewById(R.id.settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.githubRepo).setOnClickListener(v -> {
            try {
                Intent browser = new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/Cesium-developer/SoundOrbit"));
                browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (browser.resolveActivity(getPackageManager()) != null) {
                    startActivity(browser);
                } else {
                    android.widget.Toast.makeText(this, R.string.no_browser,
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable t) {
                android.widget.Toast.makeText(this, R.string.no_browser,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        captureAuth.setOnClickListener(v -> {
            if (captureEngine.isAuthorized()) {
                captureStatus.setText(R.string.capture_authorized);
            } else {
                captureEngine.requestAuthorization(this);
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CaptureEngine.REQ_CAPTURE) {
            captureEngine.onAuthorizationResult(this, resultCode, data);
            refreshStatus();
        }
    }

    private boolean isCaptureMode() {
        return engineGroup.getCheckedRadioButtonId() == R.id.engineShizuku;
    }

    private int channelMode() {
        return channelSpinner.getSelectedItemPosition();
    }

    private boolean randomEnabled() {
        return periodRandomCheck.isChecked();
    }

    /** 校验周期范围，合法返回 true，不合法弹提示并把焦点交给对应输入框 */
    private boolean validatePeriodRange() {
        if (!randomEnabled()) return true;
        String err = PeriodRandomizer.validate(this,
                periodRandomMinField.getText().toString(),
                periodRandomMaxField.getText().toString());
        if (err != null) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.period_random_bad)
                    .setMessage(err + getString(R.string.period_random_reenter))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return false;
        }
        return true;
    }

    private float randomMin() {
        return parseField(periodRandomMinField, Prefs.getPeriodRandomMin(this));
    }

    private float randomMax() {
        return parseField(periodRandomMaxField, Prefs.getPeriodRandomMax(this));
    }

    private void toggleEngine() {
        boolean nativeRunning = SurroundService.isRunning();
        boolean captureRunning = captureEngine.isRunning();
        if (nativeRunning || captureRunning) {
            stopAll();
        } else {
            if (!validatePeriodRange()) return;
            applyParams();
            checkOutputDevice();
            if (isCaptureMode()) {
                captureEngine.configureRandom(randomEnabled(), randomMin(), randomMax());
                captureEngine.setHrtfParams(Prefs.getHrtfParams(this));
                int res = captureEngine.start(currentDepth(), currentPeriod(),
                        currentRatio(), channelMode(),
                        currentHrtfAzimuth(), currentHrtfRadius(), currentHrtfElevation());
                if (res != 0) {
                    status.setText(getString(R.string.status_advanced_failed,
                            captureEngine.getLastError() != null
                                    ? captureEngine.getLastError() : String.valueOf(res)));
                }
            } else {
                SurroundService.start(this, currentDepth(), currentPeriod(), currentRatio(),
                        randomEnabled(), randomMin(), randomMax());
            }
        }
        uiHandler.postDelayed(this::refreshStatus, 300);
    }

    private void checkOutputDevice() {
        if (AudioDeviceHelper.hasStereoOutput(this)) return;
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            String channelId = "surround_warning";
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new android.app.NotificationChannel(
                        channelId, getString(R.string.warn_channel), android.app.NotificationManager.IMPORTANCE_HIGH));
            }
            android.app.Notification n = new android.app.Notification.Builder(this, channelId)
                    .setSmallIcon(R.drawable.ic_surround)
                    .setContentTitle(getString(R.string.warn_not_stereo_title))
                    .setContentText(getString(R.string.warn_not_stereo_text))
                    .setAutoCancel(true)
                    .build();
            nm.notify(99, n);
        } catch (Throwable ignored) {
        }
    }

    private void stopAll() {
        SurroundService.stop(this);
        captureEngine.stop();
    }

    private float parseField(EditText field, float fallback) {
        try {
            return Float.parseFloat(field.getText().toString().trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private float currentDepth() {
        return parseField(depthField, Prefs.getDepth(this));
    }

    private float currentPeriod() {
        return parseField(periodField, Prefs.getPeriod(this));
    }

    private float currentRatio() {
        return parseField(ratioField, Prefs.getRatio(this));
    }

    private float currentHrtfAzimuth() {
        return parseField(hrtfAzimuthField, Prefs.getHrtfAzimuth(this));
    }

    private float currentHrtfRadius() {
        return parseField(hrtfRadiusField, Prefs.getHrtfRadius(this));
    }

    private float currentHrtfElevation() {
        return parseField(hrtfElevationField, Prefs.getHrtfElevation(this));
    }

    private void applyParams() {
        float d = currentDepth();
        float p = currentPeriod();
        float r = currentRatio();
        Prefs.save(this, d, p, r);
        float az = currentHrtfAzimuth();
        float rad = currentHrtfRadius();
        float el = currentHrtfElevation();
        Prefs.saveHrtf(this, az, rad, el);
        boolean rand = randomEnabled();
        float rmin = randomMin();
        float rmax = randomMax();
        Prefs.savePeriodRandom(this, rand, rmin, rmax);
        if (SurroundService.isRunning()) {
            SurroundService.updateParams(this, d, p, r, rand, rmin, rmax);
        }
        if (captureEngine.isRunning()) {
            captureEngine.configureRandom(rand, rmin, rmax);
            captureEngine.setHrtfParams(Prefs.getHrtfParams(this));
            captureEngine.update(d, p, r, channelMode(), az, rad, el);
        }
    }

    private void refreshStatus() {
        boolean nativeRunning = SurroundService.isRunning();
        boolean captureRunning = captureEngine.isRunning();
        boolean any = nativeRunning || captureRunning;

        if (captureRunning) {
            status.setText(R.string.status_advanced_running);
        } else if (nativeRunning) {
            status.setText(R.string.status_running);
        } else {
            String err = SurroundService.lastError();
            if (err != null && !err.isEmpty()) {
                status.setText(getString(R.string.status_failed) + "：" + err);
            } else {
                status.setText(R.string.status_idle);
            }
        }
        toggle.setText(any ? R.string.disable : R.string.enable);
        captureStatus.setText(captureEngine.isAuthorized()
                ? R.string.capture_authorized : R.string.capture_unauthorized);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        uiHandler.post(statusRefresh);
    }

    @Override
    protected void onPause() {
        applyParams();
        uiHandler.removeCallbacks(statusRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        captureEngine.release();
        super.onDestroy();
    }
}
