package com.example.surroundinsert;

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

    public static void saveHrtf(Context c, float az, float rad, float el) {
        SharedPreferences.Editor e = c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit();
        e.putFloat(KEY_HRTF_AZ, az);
        e.putFloat(KEY_HRTF_RAD, rad);
        e.putFloat(KEY_HRTF_EL, el);
        e.apply();
    }
}
