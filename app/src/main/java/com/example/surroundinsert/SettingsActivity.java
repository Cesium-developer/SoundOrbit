package com.example.surroundinsert;

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
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.AdapterView;

import java.util.List;

public class SettingsActivity extends androidx.appcompat.app.AppCompatActivity {
    private TextView diagText;

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
                + getString(R.string.diag_global_effect) + "\n"
                + getString(R.string.diag_mono);
        diagText.setText(info);
    }

    private static String format(float v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
