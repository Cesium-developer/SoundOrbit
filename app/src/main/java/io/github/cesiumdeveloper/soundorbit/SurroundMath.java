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
     * 捕获引擎用：增益上限 1.0（只衰减不放大），避免把已-normalize 的音频放大到削波。
     * 捕获到的音频峰值已接近满幅，再乘 >1 就会削波失真。
     */
    public static float toLinearCapture(float gain) {
        float magnitude = Math.abs(gain);
        if (magnitude > 1f) magnitude = 1f;
        return magnitude;
    }
}
