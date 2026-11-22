package io.github.cesiumdeveloper.soundorbit;

/**
 * 软限幅器（Soft Limiter / Soft Clipper）。
 *
 * 作用：替代硬裁剪（clamp），防止信号超过 ±1 时波形被"切平"产生削波杂音。
 * 原理：阈值以下原样通过；阈值以上用 tanh 平滑压缩，输出永远落在 [-1, 1]。
 *
 * 数值约定：输入/输出是归一化 float，1.0 = 满幅 = short 32767。
 * threshold 默认 0.89 = -1 dBTP（流媒体/广播母带通用上限）。
 */
public final class SoftLimiter {
    public static final float DEFAULT_THRESHOLD = 0.89f;
    public static final float MIN_THRESHOLD = 0.1f;
    public static final float MAX_THRESHOLD = 0.99f;

    private float threshold = DEFAULT_THRESHOLD;

    public SoftLimiter() {
    }

    public SoftLimiter(float threshold) {
        setThreshold(threshold);
    }

    public void setThreshold(float threshold) {
        if (Float.isNaN(threshold) || Float.isInfinite(threshold)) {
            this.threshold = DEFAULT_THRESHOLD;
            return;
        }
        this.threshold = Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, threshold));
    }

    public float getThreshold() {
        return threshold;
    }

    /**
     * 对一个采样做软限幅，返回 [-1, 1] 的 float。
     *
     * 曲线分两段：
     *  - |v| <= threshold：原样返回，正常信号一个采样都不动。
     *  - |v| >  threshold：令 x = (|v|-t)/(1-t) ∈ [0,∞)，
     *    out = t + (1-t)*tanh(x)。
     *    |v|==t 时 out=t（与直线连续衔接）；|v|→∞ 时 out→1（天花板，永不硬削波）。
     */
    public float limit(float v) {
        float t = threshold;
        float a = Math.abs(v);
        if (a <= t) return v;
        float x = (a - t) / (1.0f - t);
        float mag = t + (1.0f - t) * (float) Math.tanh(x);
        return v >= 0f ? mag : -mag;
    }

    /** 软限幅后直接转成 16-bit PCM short。 */
    public short limitToShort(float v) {
        return (short) (limit(v) * 32767f);
    }
}
