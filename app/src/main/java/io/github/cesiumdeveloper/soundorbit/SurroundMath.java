package io.github.cesiumdeveloper.soundorbit;

/** 两个引擎共用的增益算法：反相三角波 + 时间比变形。改这里 = 两个引擎一起改。 */
public final class SurroundMath {
    private SurroundMath() {
    }

    /** 返回 [gL, gR, tri, phase]，与轻量引擎完全相同的公式 */
    public static float[] gains(float depth, float period, float ratio, double tSec) {
        double phase = period == 0f ? 0.0 : (tSec % period) / period;
        return gainsFromPhase(depth, ratio, phase);
    }

    /** 用显式相位算增益（配合周期随机器，避免切周期时相位跳变） */
    public static float[] gainsFromPhase(float depth, float ratio, double phase) {
        double tri = Math.abs(phase * 2.0 - 1.0);
        if (ratio > 0f && ratio != 1f) {
            double signed = tri * 2.0 - 1.0;
            double warped = Math.copySign(Math.pow(Math.abs(signed), 1.0 / ratio), signed);
            tri = (warped + 1.0) / 2.0;
        }
        float gL = 1f - depth * (1f - (float) tri);
        float gR = 1f - depth * (float) tri;
        return new float[]{gL, gR, (float) tri, (float) phase};
    }

    /** 轻量引擎用：增益取绝对值后按 dB 钳制到 [-96, 12]，返回线性值，与 setBandGain 一致 */
    public static float toLinear(float gain) {
        float magnitude = Math.abs(gain);
        float db = magnitude <= 0f ? -96f : (float) (20.0 * Math.log10(magnitude));
        if (db < -96f) db = -96f;
        if (db > 12f) db = 12f;
        return (float) Math.pow(10.0, db / 20.0);
    }

    /**
     * 捕获引擎用：把增益映射到 [0,1] 的衰减系数（只衰减不放大），避免把已-normalize
     * 的音频放大到削波。增益原始区间是 [1-depth, 1]（depth>1 时会越过 0 变负），
     * 旧的 abs+clamp(-1,1) 会把 1-depth 这类负值取绝对值后钳到 1，导致左右全程
     * 满幅、摆动失效。这里改为把 [1-depth, 1] 线性归一化到 [0,1]：
     * 近耳(1)->1 不衰减，最远(1-depth)->0 全衰减，中间平滑过渡。
     */
    public static float toLinearCapture(float gain, float depth) {
        if (depth <= 0f) return 1f;
        float coefficient = 1f + (gain - 1f) / depth;
        if (coefficient < 0f) coefficient = 0f;
        if (coefficient > 1f) coefficient = 1f;
        return coefficient;
    }

    /** 兼容旧签名（depth 未知时按 1 处理，仅作保底，正常路径请用带 depth 的版本） */
    public static float toLinearCapture(float gain) {
        return toLinearCapture(gain, 1f);
    }
}
