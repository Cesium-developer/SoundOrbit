package io.github.cesiumdeveloper.soundorbit;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.AdapterView;

import java.util.List;

public class SettingsActivity extends androidx.appcompat.app.AppCompatActivity {
    private TextView diagText;
    private android.view.View advancedPanel;
    private EditText hrtfMaxItdField, hrtfIldField, hrtfShadowField, hrtfNotchRField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        diagText = findViewById(R.id.diagText);

        Spinner languageSpinner = findViewById(R.id.languageSpinner);
        languageSpinner.setSelection(Prefs.getLanguage(this));
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int pos, long id) {
                if (pos != Prefs.getLanguage(SettingsActivity.this)) {
                    Prefs.saveLanguage(SettingsActivity.this, pos);
                    androidx.core.os.LocaleListCompat locales;
                    if (pos == 1) {
                        locales = androidx.core.os.LocaleListCompat.forLanguageTags("zh-CN");
                    } else if (pos == 2) {
                        locales = androidx.core.os.LocaleListCompat.forLanguageTags("en");
                    } else {
                        locales = androidx.core.os.LocaleListCompat.getEmptyLocaleList();
                    }
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 发烧友参数：折叠面板 + 默认值填充
        advancedPanel = findViewById(R.id.advancedPanel);
        hrtfMaxItdField = findViewById(R.id.hrtfMaxItd);
        hrtfIldField = findViewById(R.id.hrtfIld);
        hrtfShadowField = findViewById(R.id.hrtfShadow);
        hrtfNotchRField = findViewById(R.id.hrtfNotchR);
        loadHrtfParams();
        findViewById(R.id.advancedToggle).setOnClickListener(v -> {
            advancedPanel.setVisibility(advancedPanel.getVisibility() == android.view.View.VISIBLE
                    ? android.view.View.GONE : android.view.View.VISIBLE);
        });
        findViewById(R.id.hrtfReset).setOnClickListener(v -> {
            HrtfParams d = HrtfParams.defaults();
            hrtfMaxItdField.setText(String.valueOf(d.maxItdMs));
            hrtfIldField.setText(String.valueOf(d.ildDepth));
            hrtfShadowField.setText(String.valueOf(d.shadow));
            hrtfNotchRField.setText(String.valueOf(d.notchR));
            Prefs.saveHrtfParams(this, d);
        });

        findViewById(R.id.battery).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Throwable ignored) {
                    }
                }
            }
        });

        findViewById(R.id.accessibility).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable ignored) {
            }
        });

        findViewById(R.id.diag).setOnClickListener(v -> showDiag());

        findViewById(R.id.copyDiag).setOnClickListener(v -> {
            try {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText(
                            "surround-diag", diagText.getText().toString()));
                }
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showDiag();
    }

    @Override
    protected void onPause() {
        saveHrtfParams();
        super.onPause();
    }

    private void loadHrtfParams() {
        HrtfParams p = Prefs.getHrtfParams(this);
        hrtfMaxItdField.setText(String.valueOf(p.maxItdMs));
        hrtfIldField.setText(String.valueOf(p.ildDepth));
        hrtfShadowField.setText(String.valueOf(p.shadow));
        hrtfNotchRField.setText(String.valueOf(p.notchR));
    }

    private void saveHrtfParams() {
        HrtfParams d = HrtfParams.defaults();
        float itd = parseFloatOr(hrtfMaxItdField, d.maxItdMs);
        float ild = parseFloatOr(hrtfIldField, d.ildDepth);
        float shadow = parseFloatOr(hrtfShadowField, d.shadow);
        float notch = parseFloatOr(hrtfNotchRField, d.notchR);
        Prefs.saveHrtfParams(this, new HrtfParams(itd, ild, shadow, notch));
        // 越界值被构造器 clamp，回写输入框让用户看到实际生效值
        HrtfParams p = Prefs.getHrtfParams(this);
        hrtfMaxItdField.setText(String.valueOf(p.maxItdMs));
        hrtfIldField.setText(String.valueOf(p.ildDepth));
        hrtfShadowField.setText(String.valueOf(p.shadow));
        hrtfNotchRField.setText(String.valueOf(p.notchR));
    }

    private float parseFloatOr(EditText f, float fallback) {
        try {
            float v = Float.parseFloat(f.getText().toString().trim());
            return Float.isNaN(v) || Float.isInfinite(v) ? fallback : v;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private void showDiag() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean batteryIgnored = pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());

        boolean a11yOn = false;
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                List<AccessibilityServiceInfo> list =
                        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
                for (AccessibilityServiceInfo info : list) {
                    if (info.getId() != null
                            && info.getId().contains(getPackageName())
                            && info.getId().contains("KeepAliveAccessibilityService")) {
                        a11yOn = true;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        String info = "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + getString(R.string.diag_device) + Build.BRAND + " " + Build.MODEL + "\n"
                + getString(R.string.diag_effect_running) + EffectState.active + "\n"
                + getString(R.string.diag_current_params) + format(Prefs.getDepth(this))
                + ", period=" + format(Prefs.getPeriod(this))
                + ", ratio=" + format(Prefs.getRatio(this)) + "\n"
                + getString(R.string.diag_battery) + (batteryIgnored ? getString(R.string.diag_joined) : getString(R.string.diag_not_joined)) + "\n"
                + getString(R.string.diag_a11y) + (a11yOn ? getString(R.string.diag_on) : getString(R.string.diag_off)) + "\n"
                + getString(R.string.diag_global_effect);
        diagText.setText(info);
    }

    private static String format(float v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
