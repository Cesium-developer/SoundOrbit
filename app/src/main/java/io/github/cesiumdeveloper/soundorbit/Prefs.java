package io.github.cesiumdeveloper.soundorbit;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String NAME = "surround_prefs";
    private static final String KEY_DEPTH = "depth";
    private static final String KEY_PERIOD = "period";
    private static final String KEY_RATIO = "ratio";
    private static final String KEY_HRTF_AZ = "hrtf_az";
    private static final String KEY_HRTF_RAD = "hrtf_rad";
    private static final String KEY_HRTF_EL = "hrtf_el";
    private static final String KEY_RAND = "period_random";
    private static final String KEY_RAND_MIN = "period_rand_min";
    private static final String KEY_RAND_MAX = "period_rand_max";
    private static final String KEY_LANGUAGE = "language_mode";
    private static final String KEY_MAX_ITD = "hrtf_max_itd";
    private static final String KEY_ILD = "hrtf_ild";
    private static final String KEY_SHADOW = "hrtf_shadow";
    private static final String KEY_NOTCH_R = "hrtf_notch_r";
    private static final String KEY_SOFT_THRESHOLD = "soft_limit_threshold";
    private static final String KEY_EQ_BANDS = "eq_bands";
    private static final String KEY_EQ_PRESETS = "eq_presets";
    private static final String KEY_BASS_STRENGTH = "bass_strength";

    private Prefs() {
    }

    public static float getDepth(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(KEY_DEPTH, 20f);
    }

    public static float getPeriod(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(KEY_PERIOD, 20f);
    }

    public static float getRatio(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(KEY_RATIO, 1f);
    }

    public static float getHrtfAzimuth(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(KEY_HRTF_AZ, 0f);
    }

    public static float getHrtfRadius(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(KEY_HRTF_RAD, 0.5f);
    }

    public static float getHrtfElevation(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(KEY_HRTF_EL, 0f);
    }

    public static HrtfParams getHrtfParams(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        return new HrtfParams(
                p.getFloat(KEY_MAX_ITD, HrtfParams.DEFAULT_MAX_ITD_MS),
                p.getFloat(KEY_ILD, HrtfParams.DEFAULT_ILD_DEPTH),
                p.getFloat(KEY_SHADOW, HrtfParams.DEFAULT_SHADOW),
                p.getFloat(KEY_NOTCH_R, HrtfParams.DEFAULT_NOTCH_R));
    }

    public static void saveHrtfParams(Context c, HrtfParams v) {
        SharedPreferences.Editor e = c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit();
        e.putFloat(KEY_MAX_ITD, v.maxItdMs);
        e.putFloat(KEY_ILD, v.ildDepth);
        e.putFloat(KEY_SHADOW, v.shadow);
        e.putFloat(KEY_NOTCH_R, v.notchR);
        e.apply();
    }

    public static boolean getPeriodRandom(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_RAND, false);
    }

    public static float getPeriodRandomMin(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(KEY_RAND_MIN, 5f);
    }

    public static float getPeriodRandomMax(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(KEY_RAND_MAX, 30f);
    }

    /** 语言模式：0=跟随系统, 1=中文, 2=英文 */
    public static int getLanguage(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_LANGUAGE, 0);
    }

    public static void saveLanguage(Context c, int mode) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putInt(KEY_LANGUAGE, mode).apply();
    }

    public static void savePeriodRandom(Context c, boolean enabled, float min, float max) {
        SharedPreferences.Editor e = c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit();
        e.putBoolean(KEY_RAND, enabled);
        e.putFloat(KEY_RAND_MIN, min);
        e.putFloat(KEY_RAND_MAX, max);
        e.apply();
    }

    public static void save(Context c, float depth, float period, float ratio) {
        SharedPreferences.Editor e = c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit();
        e.putFloat(KEY_DEPTH, depth);
        e.putFloat(KEY_PERIOD, period);
        e.putFloat(KEY_RATIO, ratio);
        e.apply();
    }

    public static float getSoftThreshold(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_SOFT_THRESHOLD, SoftLimiter.DEFAULT_THRESHOLD);
    }

    public static void saveSoftThreshold(Context c, float threshold) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putFloat(KEY_SOFT_THRESHOLD, threshold).apply();
    }

    public static float[] getEq(Context c) {
        String raw = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getString(KEY_EQ_BANDS, "");
        float[] bands = new float[GraphicEQ.BANDS];
        if (raw == null || raw.isEmpty()) return bands;
        String[] parts = raw.split(",");
        for (int i = 0; i < Math.min(parts.length, bands.length); i++) {
            try { bands[i] = Float.parseFloat(parts[i]); } catch (Throwable ignored) {}
        }
        return bands;
    }

    public static float getBassStrength(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_BASS_STRENGTH, 0f);
    }
    public static void saveBassStrength(Context c, float v) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putFloat(KEY_BASS_STRENGTH, v).apply();
    }

    public static String getEqPresetsRaw(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getString(KEY_EQ_PRESETS, "");
    }

    public static void saveEqPresetsRaw(Context c, String raw) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_EQ_PRESETS, raw).apply();
    }

    public static void saveEq(Context c, float[] bands) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bands.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(bands[i]);
        }
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_EQ_BANDS, sb.toString()).apply();
    }

    public static void saveHrtf(Context c, float az, float rad, float el) {
        SharedPreferences.Editor e = c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit();
        e.putFloat(KEY_HRTF_AZ, az);
        e.putFloat(KEY_HRTF_RAD, rad);
        e.putFloat(KEY_HRTF_EL, el);
        e.apply();
    }
}
