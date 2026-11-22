package io.github.cesiumdeveloper.soundorbit;

/**
 * 发烧友可调的双耳渲染参数。默认值 = 调整前的硬编码值，用户不动就保持原有听感。
 *
 * 注意：这些参数直接影响空间感，调 extremes 可能导致定位错乱或音量异常。
 */
public final class HrtfParams {
    // 默认值 = 旧代码里的硬编码值
    public static final float DEFAULT_MAX_ITD_MS = 0.7f;   // 旧: 0.0007 * RATE
    public static final float DEFAULT_ILD_DEPTH = 0.55f;    // 旧: 0.55 * strength
    public static final float DEFAULT_SHADOW = 0.55f;       // 旧: 0.55 * strength (头部阴影)
    public static final float DEFAULT_NOTCH_R = 0.8f;       // 旧: r = 0.8 (耳廓陷波带宽/深度)

    public final float maxItdMs;
    public final float ildDepth;
    public final float shadow;
    public final float notchR;

    public HrtfParams(float maxItdMs, float ildDepth, float shadow, float notchR) {
        this.maxItdMs = clamp(maxItdMs, 0.4f, 1.0f, DEFAULT_MAX_ITD_MS);
        this.ildDepth = clamp(ildDepth, 0f, 1f, DEFAULT_ILD_DEPTH);
        this.shadow = clamp(shadow, 0f, 1f, DEFAULT_SHADOW);
        this.notchR = clamp(notchR, 0.5f, 0.95f, DEFAULT_NOTCH_R);
    }

    public static HrtfParams defaults() {
        return new HrtfParams(DEFAULT_MAX_ITD_MS, DEFAULT_ILD_DEPTH,
                DEFAULT_SHADOW, DEFAULT_NOTCH_R);
    }

    private static float clamp(float v, float lo, float hi, float fallback) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return fallback;
        return Math.max(lo, Math.min(hi, v));
    }
}
