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
import java.util.ArrayList;

public class SettingsActivity extends androidx.appcompat.app.AppCompatActivity {
    private TextView diagText;
    private EditText hrtfMaxItdField, hrtfIldField, hrtfShadowField, hrtfNotchRField;
    private android.view.View dspPanel;
    private EditText dspSoftThresholdField;
    private EditText bassField;
    private final EditText[] eqFields = new EditText[GraphicEQ.BANDS];
    private android.widget.Spinner eqPresetSpinner;
    private android.widget.ArrayAdapter<String> eqPresetAdapter;
    private List<EqPresets.Preset> eqPresetList = new ArrayList<>();
    private List<String> eqSpinnerLabels = new ArrayList<>();
    private List<float[]> eqSpinnerValues = new ArrayList<>();
    private boolean eqPresetInit = true;
    private static final int[] EQ_IDS = {R.id.eq0, R.id.eq1, R.id.eq2, R.id.eq3, R.id.eq4,
            R.id.eq5, R.id.eq6, R.id.eq7, R.id.eq8, R.id.eq9};
    private static final java.util.List<EqPresets.Builtin> BUILTINS = EqPresets.builtins();
    private static final int BUILTIN_COUNT = BUILTINS.size();
    private static final java.util.Map<String,Integer> BUILTIN_NAME_RES = new java.util.HashMap<>();
    static {
        BUILTIN_NAME_RES.put("flat",R.string.eq_builtin_flat);
        BUILTIN_NAME_RES.put("bass",R.string.eq_builtin_bass);
        BUILTIN_NAME_RES.put("treble",R.string.eq_builtin_treble);
        BUILTIN_NAME_RES.put("vocal",R.string.eq_builtin_vocal);
        BUILTIN_NAME_RES.put("podcast",R.string.eq_builtin_podcast);
        BUILTIN_NAME_RES.put("pop",R.string.eq_builtin_pop);
        BUILTIN_NAME_RES.put("rock",R.string.eq_builtin_rock);
        BUILTIN_NAME_RES.put("hiphop",R.string.eq_builtin_hiphop);
        BUILTIN_NAME_RES.put("classical",R.string.eq_builtin_classical);
        BUILTIN_NAME_RES.put("loudness",R.string.eq_builtin_loudness);
        BUILTIN_NAME_RES.put("asmr_whisper",R.string.eq_builtin_asmr_whisper);
        BUILTIN_NAME_RES.put("asmr_tapping",R.string.eq_builtin_asmr_tapping);
    }
    private static final int[] EQ_SEEK_IDS = {R.id.eqSeek0, R.id.eqSeek1, R.id.eqSeek2, R.id.eqSeek3, R.id.eqSeek4,
            R.id.eqSeek5, R.id.eqSeek6, R.id.eqSeek7, R.id.eqSeek8, R.id.eqSeek9};

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

        // DSP 设置（高级）：单一折叠区，内含输出处理与 HRTF 两个小区
        dspPanel = findViewById(R.id.dspPanel);
        dspSoftThresholdField = findViewById(R.id.dspSoftThreshold);
        hrtfMaxItdField = findViewById(R.id.hrtfMaxItd);
        hrtfIldField = findViewById(R.id.hrtfIld);
        hrtfShadowField = findViewById(R.id.hrtfShadow);
        hrtfNotchRField = findViewById(R.id.hrtfNotchR);
        dspSoftThresholdField.setText(String.valueOf(Prefs.getSoftThreshold(this)));
        bassField = findViewById(R.id.dspBass);
        bassField.setText(String.valueOf(Prefs.getBassStrength(this)));
        loadHrtfParams();
        for (int i = 0; i < GraphicEQ.BANDS; i++) {
            eqFields[i] = findViewById(EQ_IDS[i]);
            eqFields[i].setText("0");
        }
        loadEq();
        bindEqSeeks();
        bindParamSeeks();
        findViewById(R.id.eqReset).setOnClickListener(v -> {
            // 只清零草稿并让下拉显示内置"均衡"(全0)；真正生效仍需点"应用预设"
            for (EditText f : eqFields) f.setText("0");
            refreshEqPresetSpinner(0);
        });
        // EQ 预设：下拉项 = [内置均衡等12套, 用户预设..., 自定义(未保存)]。
        // 选择只填草稿不生效；点"应用预设"才写 Prefs 并据草稿更新下拉显示。
        eqPresetSpinner = findViewById(R.id.eqPresetSpinner);
        eqPresetAdapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new ArrayList<String>());
        eqPresetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        eqPresetSpinner.setAdapter(eqPresetAdapter);
        refreshEqPresetSpinner(-1); // -1 = 自动选中当前生效 EQ 对应的项
        eqPresetSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int pos, long id) {
                if (eqPresetInit) return;
                if (pos >= 0 && pos < eqSpinnerValues.size()) {
                    // 只填入输入框作为草稿，不应用（应用由专门按钮完成）
                    float[] bands = eqSpinnerValues.get(pos);
                    for (int i = 0; i < GraphicEQ.BANDS; i++) {
                        eqFields[i].setText(String.valueOf(bands[i]));
                    }
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        findViewById(R.id.eqPresetApply).setOnClickListener(v -> {
            float[] draft = saveEq(); // 应用草稿为当前生效值
            int target = matchSpinnerIndex(draft);
            if (target < 0) { // 不等于任何项 -> 自定义，更新其值
                int customIdx = eqSpinnerValues.size() - 1;
                eqSpinnerValues.set(customIdx, draft);
                target = customIdx;
            }
            refreshEqPresetSpinner(target);
            android.widget.Toast.makeText(this, R.string.eq_preset_applied,
                    android.widget.Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.eqPresetSave).setOnClickListener(v -> {
            android.widget.EditText nameField = new android.widget.EditText(this);
            nameField.setHint(R.string.eq_preset_name_title);
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.eq_preset_name_title)
                    .setView(nameField)
                    .setPositiveButton(R.string.eq_preset_save, (d, w) -> {
                        String name = nameField.getText().toString();
                        if (name.trim().isEmpty()) name = "预设" + (eqPresetList.size() + 1);
                        float[] bands = new float[GraphicEQ.BANDS];
                        for (int i = 0; i < GraphicEQ.BANDS; i++) bands[i] = parseFloatOr(eqFields[i], 0f);
                        List<EqPresets.Preset> list = EqPresets.parse(Prefs.getEqPresetsRaw(this));
                        list.add(new EqPresets.Preset(name, bands));
                        Prefs.saveEqPresetsRaw(this, EqPresets.serialize(list));
                        saveEq(); // 保存为新预设同时应用当前值
                        // 真实预设从 index 1 开始（index 0 是无预设）
                        refreshEqPresetSpinner(BUILTIN_COUNT + list.size() - 1);
                        android.widget.Toast.makeText(this, R.string.eq_preset_saved,
                                android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
        findViewById(R.id.eqPresetDelete).setOnClickListener(v -> {
            int pos = eqPresetSpinner.getSelectedItemPosition();
            // 用户预设在 index B..B+N-1；0..B-1=内置，末位=自定义，都不可删
            if (pos < BUILTIN_COUNT || pos >= BUILTIN_COUNT + eqPresetList.size()) return;
            List<EqPresets.Preset> list = EqPresets.parse(Prefs.getEqPresetsRaw(this));
            int presetIdx = pos - BUILTIN_COUNT;
            if (presetIdx >= list.size()) return;
            list.remove(presetIdx);
            Prefs.saveEqPresetsRaw(this, EqPresets.serialize(list));
            refreshEqPresetSpinner(Math.max(0, Math.min(pos, list.size())));
            android.widget.Toast.makeText(this, R.string.eq_preset_deleted,
                    android.widget.Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.dspToggle).setOnClickListener(v -> {
            dspPanel.setVisibility(dspPanel.getVisibility() == android.view.View.VISIBLE
                    ? android.view.View.GONE : android.view.View.VISIBLE);
        });
        findViewById(R.id.dspReset).setOnClickListener(v -> {
            dspSoftThresholdField.setText(String.valueOf(SoftLimiter.DEFAULT_THRESHOLD));
            Prefs.saveSoftThreshold(this, SoftLimiter.DEFAULT_THRESHOLD);
        });
        findViewById(R.id.hrtfReset).setOnClickListener(v -> {
            HrtfParams d = HrtfParams.defaults();
            hrtfMaxItdField.setText(String.valueOf(d.maxItdMs));
            hrtfIldField.setText(String.valueOf(d.ildDepth));
            hrtfShadowField.setText(String.valueOf(d.shadow));
            hrtfNotchRField.setText(String.valueOf(d.notchR));
            Prefs.saveHrtfParams(this, d);
        });

        // DSP/HRTF/EQ 参数允许运行中修改：引擎的 set 方法都支持运行中调用，
        // 修改后返回主界面即生效（主界面 onResume → applyParams 推送引擎），无需停止环绕。
        // 旧版 installDspGuard() 已停用（保留方法体以备回退）。

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
        // 注意：EQ 草稿不在这里保存——只有点"应用预设"才生效（用户明确要求）
        float th = parseFloatOr(dspSoftThresholdField, SoftLimiter.DEFAULT_THRESHOLD);
        SoftLimiter probe = new SoftLimiter(th);
        Prefs.saveSoftThreshold(this, probe.getThreshold());
        dspSoftThresholdField.setText(String.valueOf(probe.getThreshold()));
        float bass = parseFloatOr(bassField, 0f);
        if (bass < 0f) bass = 0f;
        if (bass > 1f) bass = 1f;
        Prefs.saveBassStrength(this, bass);
        bassField.setText(String.valueOf(bass));
        super.onPause();
    }

    private long lastGuardToastMs = 0;

    private void installDspGuard() {
        android.view.View.OnTouchListener guard = (v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && EngineGuard.isRunning()) {
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastGuardToastMs > 1000) {
                    lastGuardToastMs = now;
                    android.widget.Toast.makeText(this, R.string.settings_locked_while_running,
                            android.widget.Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        };
        android.view.View[] guarded = {
                dspSoftThresholdField, hrtfMaxItdField, hrtfIldField,
                hrtfShadowField, hrtfNotchRField,
                findViewById(R.id.dspReset), findViewById(R.id.eqReset), findViewById(R.id.hrtfReset),
                eqPresetSpinner, findViewById(R.id.eqPresetApply),
                findViewById(R.id.eqPresetSave), findViewById(R.id.eqPresetDelete),
                bassField
        };
        for (android.view.View v : guarded) if (v != null) v.setOnTouchListener(guard);
        for (EditText f : eqFields) if (f != null) f.setOnTouchListener(guard);
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

    /** 重建下拉：index 0..B-1=内置预设(含全0的"均衡"), B..B+N-1=用户预设, 末位=自定义。 */
    private void refreshEqPresetSpinner(int selectIndex) {
        eqPresetList = EqPresets.parse(Prefs.getEqPresetsRaw(this));
        eqSpinnerLabels.clear();
        eqSpinnerValues.clear();
        for (EqPresets.Builtin b : BUILTINS) {
            Integer res = BUILTIN_NAME_RES.get(b.nameKey);
            eqSpinnerLabels.add(res != null ? getString(res) : b.nameKey);
            eqSpinnerValues.add(b.bands);
        }
        for (EqPresets.Preset p : eqPresetList) {
            eqSpinnerLabels.add(p.name);
            eqSpinnerValues.add(p.bands);
        }
        eqSpinnerLabels.add(getString(R.string.eq_preset_custom));
        eqSpinnerValues.add(Prefs.getEq(this).clone()); // 自定义项=当前生效值
        eqPresetAdapter.clear();
        eqPresetAdapter.addAll(eqSpinnerLabels);
        eqPresetAdapter.notifyDataSetChanged();
        // selectIndex<0：自动选中"当前生效 EQ"对应的项（内置/用户预设/自定义）。
        if (selectIndex < 0) {
            selectIndex = matchSpinnerIndex(Prefs.getEq(this));
        }
        eqPresetInit = true;
        if (selectIndex >= 0 && selectIndex < eqSpinnerLabels.size()) {
            eqPresetSpinner.setSelection(selectIndex);
        }
        eqPresetInit = false;
    }

    /** 当前生效 EQ 是否与某下拉项相同（全 0 匹配到内置"均衡" index 0）；找不到返回 -1。 */
    private int matchSpinnerIndex(float[] bands) {
        for (int i = 0; i < eqSpinnerValues.size(); i++) {
            if (sameBands(bands, eqSpinnerValues.get(i))) return i;
        }
        return -1;
    }

    private boolean sameBands(float[] a, float[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 0.0001f) return false;
        }
        return true;
    }

    private void loadEq() {
        float[] bands = Prefs.getEq(this);
        for (int i = 0; i < GraphicEQ.BANDS; i++) {
            eqFields[i].setText(String.valueOf(bands[i]));
        }
    }

    /** 通用滑块绑定：滑块有范围，数字框无范围（超范围时滑块夹边界、数字框保留） */
    private void bindOneSeek(int seekId, EditText field, float min, float max, int scale) {
        final android.widget.SeekBar bar = findViewById(seekId);
        if (bar == null) return;
        bar.setMax(Math.round((max - min) * scale));
        field.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(android.text.Editable e){
                if (bar.isPressed()) return;
                try {
                    float v = Float.parseFloat(e.toString());
                    float cl = Math.max(min, Math.min(max, v));
                    int p = Math.round((cl - min) * scale);
                    if (bar.getProgress() != p) bar.setProgress(p);
                } catch (Throwable t) {}
            }
        });
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar sb,int progress,boolean fromUser){
                if (!fromUser) return;
                float v = min + progress / (float) scale;
                field.setText(scale >= 100 ? String.format(java.util.Locale.US,"%.2f",v)
                        : String.format(java.util.Locale.US,"%.1f",v));
            }
            public void onStartTrackingTouch(android.widget.SeekBar sb){}
            public void onStopTrackingTouch(android.widget.SeekBar sb){}
        });
        field.post(() -> {
            try {
                float v = Float.parseFloat(field.getText().toString());
                float cl = Math.max(min, Math.min(max, v));
                bar.setProgress(Math.round((cl - min) * scale));
            } catch (Throwable t) {}
        });
    }

    private void bindParamSeeks() {
        bindOneSeek(R.id.softThresholdSeek, dspSoftThresholdField, 0.1f, 0.99f, 100);
        bindOneSeek(R.id.bassSeek, bassField, 0f, 1f, 100);
        bindOneSeek(R.id.maxItdSeek, hrtfMaxItdField, 0.4f, 1.0f, 100);
        bindOneSeek(R.id.ildSeek, hrtfIldField, 0f, 1f, 100);
        bindOneSeek(R.id.shadowSeek, hrtfShadowField, 0f, 1f, 100);
        bindOneSeek(R.id.notchRSeek, hrtfNotchRField, 0.5f, 0.95f, 100);
    }

    /** EQ 滑块 ↔ 数字框双向绑定。0.5dB 分度（scale=2），范围 ±12dB。 */
    private void bindEqSeeks() {
        for (int i = 0; i < GraphicEQ.BANDS; i++) {
            final android.widget.SeekBar bar = findViewById(EQ_SEEK_IDS[i]);
            final EditText field = eqFields[i];
            if (bar == null) continue;
            bar.setMax(Math.round((GraphicEQ.MAX_DB - GraphicEQ.MIN_DB) * 2)); // 0..48
            field.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                public void onTextChanged(CharSequence s,int a,int b,int c){}
                public void afterTextChanged(android.text.Editable e){
                    if (bar.isPressed()) return;
                    try {
                        float v = Float.parseFloat(e.toString());
                        float clamped = Math.max(GraphicEQ.MIN_DB, Math.min(GraphicEQ.MAX_DB, v));
                        int p = Math.round((clamped - GraphicEQ.MIN_DB) * 2);
                        if (bar.getProgress() != p) bar.setProgress(p);
                    } catch (Throwable t) {}
                }
            });
            bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(android.widget.SeekBar sb,int progress,boolean fromUser){
                    if (!fromUser) return;
                    float v = GraphicEQ.MIN_DB + progress / 2f;
                    field.setText(String.format(java.util.Locale.US, "%.1f", v));
                }
                public void onStartTrackingTouch(android.widget.SeekBar sb){}
                public void onStopTrackingTouch(android.widget.SeekBar sb){}
            });
            field.post(() -> {
                try {
                    float v = Float.parseFloat(field.getText().toString());
                    float clamped = Math.max(GraphicEQ.MIN_DB, Math.min(GraphicEQ.MAX_DB, v));
                    bar.setProgress(Math.round((clamped - GraphicEQ.MIN_DB) * 2));
                } catch (Throwable t) {}
            });
        }
    }

    /** 把草稿写入生效值并返回它（越界钳制）。仅"应用预设"时调用。 */
    private float[] saveEq() {
        float[] bands = new float[GraphicEQ.BANDS];
        for (int i = 0; i < GraphicEQ.BANDS; i++) {
            bands[i] = parseFloatOr(eqFields[i], 0f);
            if (bands[i] > GraphicEQ.MAX_DB) bands[i] = GraphicEQ.MAX_DB;
            if (bands[i] < GraphicEQ.MIN_DB) bands[i] = GraphicEQ.MIN_DB;
            eqFields[i].setText(String.valueOf(bands[i]));
        }
        Prefs.saveEq(this, bands);
        return bands;
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
