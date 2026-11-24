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
    private Spinner channelTypeSpinner;
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
        channelTypeSpinner = findViewById(R.id.channelTypeSpinner);
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

        // 守护：环绕声运行中，所有影响音效的设置不可更改（保活设置不在此列）。
        installSettingGuard();

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

        // 滑块与数字框双向绑定（滑块有范围、数字框无范围）
        bindAllSeeks();
        // 可实时调整的参数：变化即推送引擎（防抖，避免滑块拖动时频繁写 Prefs）
        installLiveParamWatchers();

        android.widget.AdapterView.OnItemSelectedListener typeListener =
                new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int pos, long id) {
                // 类型 0=伪环绕 1=真环绕；真环绕才显示 HRTF 参数区
                hrtfParams.setVisibility(pos == 1
                        ? android.view.View.VISIBLE : android.view.View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        };
        channelTypeSpinner.setOnItemSelectedListener(typeListener);

        toggle.setOnClickListener(v -> toggleEngine());

        findViewById(R.id.settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.githubRepo).setOnClickListener(v -> {
            // 直接交给系统用默认浏览器打开；不加 resolveActivity 检查——
            // 那玩意在 Android 11+ 包可见性限制下会误判成"没浏览器"，把本来能跳的挡掉。
            try {
                Intent browser = new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/Cesium-developer/SoundOrbit"));
                startActivity(browser);
            } catch (android.content.ActivityNotFoundException t) {
                android.widget.Toast.makeText(this, R.string.no_browser,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        captureAuth.setOnClickListener(v -> {
            if (captureEngine.isAuthorized()) {
                // 已授权：提供 App 内停止入口，避免只能去通知栏划掉（那会导致授权状态死锁）
                captureEngine.release();
                refreshStatus();
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

    private long lastGuardToastMs = 0;

    private boolean isAnyEngineRunning() {
        return EngineGuard.isRunning();
    }

    private void warnLocked() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastGuardToastMs > 1000) {
            lastGuardToastMs = now;
            android.widget.Toast.makeText(this, R.string.settings_locked_while_running,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** 给音效控件装触摸守护：运行中拦截并提示，否则放行。 */
    private void installSettingGuard() {
        android.view.View.OnTouchListener guard = (v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && isAnyEngineRunning()) {
                warnLocked();
                return true; // 吞掉事件，控件不响应
            }
            return false;
        };
        // 实时可调的参数不拦：depth/ratio/方位/半径/仰角，引擎每帧读 volatile，改了立即生效。
        // 仍需停止才能改的：周期(改周期会重置相位导致声源瞬移)、周期随机开关/上下限(同理)、
        // 引擎切换、取源方式(会重建延迟线)。
        android.view.View[] guarded = {
                periodField, periodRandomCheck, periodRandomMinField, periodRandomMaxField,
                findViewById(R.id.engineNative), findViewById(R.id.engineShizuku)
        };
        for (android.view.View v : guarded) if (v != null) v.setOnTouchListener(guard);
        // 取源/类型 spinner 仍需停止才能改（切换会重置 HRTF 延迟线）
        if (channelSpinner != null) channelSpinner.setOnTouchListener(guard);
        if (channelTypeSpinner != null) channelTypeSpinner.setOnTouchListener(guard);
    }

    private boolean isCaptureMode() {
        return engineGroup.getCheckedRadioButtonId() == R.id.engineShizuku;
    }

    private int channelMode() {
        boolean hrtf = channelTypeSpinner.getSelectedItemPosition() == 1;
        int source = channelSpinner.getSelectedItemPosition();
        return CaptureEngine.modeFromSelection(hrtf, source);
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
                captureEngine.setSoftThreshold(Prefs.getSoftThreshold(this));
                captureEngine.setEqBands(Prefs.getEq(this));
                captureEngine.setBassStrength(Prefs.getBassStrength(this));
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
            captureEngine.setSoftThreshold(Prefs.getSoftThreshold(this));
            captureEngine.setEqBands(Prefs.getEq(this));
            captureEngine.setBassStrength(Prefs.getBassStrength(this));
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
        boolean authorized = captureEngine.isAuthorized();
        captureStatus.setText(authorized ? R.string.capture_authorized : R.string.capture_unauthorized);
        captureAuth.setText(authorized ? R.string.capture_stop : R.string.shizuku_auth);
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
    /**
     * 滑块 ↔ 数字框双向绑定。
     * 滑块用整数 progress，scale=100 表示一位以上小数（如 0.5 → 50）。
     * 拖滑块：写入数字框（引擎读数字框）。
     * 改数字框：值在 [min,max] 内则同步滑块；超出范围则滑块夹到边界，数字框保持用户输入（不限制自由输入）。
     */
    private void bindAllSeeks() {
        bindSeek(R.id.depthSeek, depthField, 0f, 100f, 1);
        bindSeek(R.id.periodSeek, periodField, 1f, 60f, 1);
        bindSeek(R.id.ratioSeek, ratioField, 0.1f, 5f, 10);
        bindSeek(R.id.azimuthSeek, hrtfAzimuthField, 0f, 360f, 1);
        bindSeek(R.id.radiusSeek, hrtfRadiusField, 0f, 1f, 100);
        bindSeek(R.id.elevationSeek, hrtfElevationField, -60f, 60f, 1);
        bindSeek(R.id.randMinSeek, periodRandomMinField, 1f, 60f, 1);
        bindSeek(R.id.randMaxSeek, periodRandomMaxField, 1f, 60f, 1);
    }

    private final android.os.Handler liveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable liveApply = new Runnable() {
        public void run() { applyParams(); }
    };

    /** 可实时参数变化时，防抖推送引擎（周期类不在其中——改周期会重置相位） */
    private void installLiveParamWatchers() {
        android.text.TextWatcher w = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(android.text.Editable e){
                if (!EngineGuard.isRunning()) return; // 没在跑不用推
                liveHandler.removeCallbacks(liveApply);
                liveHandler.postDelayed(liveApply, 150);
            }
        };
        depthField.addTextChangedListener(w);
        ratioField.addTextChangedListener(w);
        hrtfAzimuthField.addTextChangedListener(w);
        hrtfRadiusField.addTextChangedListener(w);
        hrtfElevationField.addTextChangedListener(w);
    }

    private void bindSeek(int barId, EditText field, final float min, final float max, final int scale) {
        final android.widget.SeekBar bar = findViewById(barId);
        if (bar == null) return;
        bar.setMax(Math.round((max - min) * scale));

        final android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) {
                if (bar.isPressed()) return; // 正在拖滑块，避免回写打架
                float v;
                try { v = Float.parseFloat(e.toString()); } catch (Throwable t) { return; }
                float clamped = Math.max(min, Math.min(max, v));
                int p = Math.round((clamped - min) * scale);
                if (bar.getProgress() != p) bar.setProgress(p);
            }
        };
        field.addTextChangedListener(watcher);

        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                float v = min + progress / (float) scale;
                String text = (scale == 100) ? String.format(java.util.Locale.US, "%.2f", v)
                        : (scale == 10 ? String.format(java.util.Locale.US, "%.1f", v)
                        : Integer.toString(Math.round(v)));
                field.setText(text);
            }
            public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });

        // 初始化：按当前数字框的值设滑块位置（超范围夹到边界）
        field.post(() -> {
            float v;
            try { v = Float.parseFloat(field.getText().toString()); } catch (Throwable t) { return; }
            float clamped = Math.max(min, Math.min(max, v));
            bar.setProgress(Math.round((clamped - min) * scale));
        });
    }

}
