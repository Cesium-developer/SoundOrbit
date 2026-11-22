package io.github.cesiumdeveloper.soundorbit;

/**
 * 双耳空间化处理器：把一个单声道源渲染成绕头移动的空间音频。
 *
 * 双耳线索：
 *  - ITD（双耳时间差）：分数延迟 + 两抽头线性插值，近侧耳先到
 *  - ILD（双耳强度差）：远侧耳衰减
 *  - 头部阴影低通：远侧耳高频衰减
 *  - 前后区分：cos(az) 符号驱动高频着色（前方亮、后方暗）
 *  - 仰角区分：sin(el) 符号驱动高频着色（上方亮、下方暗）
 *  - 耳廓陷波：中心频率随方位/仰角移动的窄带陷波
 *
 * 每个实例处理一个声源，内部有独立延迟线状态；立体声直接输入时用两个实例。
 */
public final class HRTFProcessor {
    private static final int RATE = 48000;

    private final float[] delayL;
    private final float[] delayR;
    private int wL;
    private int wR;

    private float lpFarL;
    private float lpFarR;

    // 耳廓陷波 biquad 状态（Direct Form I：x/y 历史分开存），左右耳各一组。
    // 中心频率随方位/仰角移动，模拟耳廓对特定频段的下凹。
    private float nx1L, nx2L, ny1L, ny2L;
    private float nx1R, nx2R, ny1R, ny2R;
    private float nb0, nb1, nb2, na1, na2;

    private final SoftLimiter limiter = new SoftLimiter();

    public void setSoftThreshold(float threshold) {
        limiter.setThreshold(threshold);
    }

    public HRTFProcessor() {
        // 最大 ITD 约 0.7ms @48k ≈ 34 采样，留 2 倍余量
        delayL = new float[128];
        delayR = new float[128];
    }

    public void reset() {
        java.util.Arrays.fill(delayL, 0f);
        java.util.Arrays.fill(delayR, 0f);
        wL = 0;
        wR = 0;
        lpFarL = 0f;
        lpFarR = 0f;
        nx1L = nx2L = ny1L = ny2L = 0f;
        nx1R = nx2R = ny1R = ny2R = 0f;
        nb0 = 1f; nb1 = 0f; nb2 = 0f; na1 = 0f; na2 = 0f;
    }

    /** 处理一帧单声道：in（帧数）-> out（交错 L,R，长度 frames*2） */
    public void process(short[] in, short[] out, int frames, double tSec,
                        float periodSec, float azimuth0, float radius, float elevation) {
        process(in, out, frames, tSec, periodSec, azimuth0, radius, elevation, HrtfParams.defaults());
    }

    public void process(short[] in, short[] out, int frames, double tSec,
                        float periodSec, float azimuth0, float radius, float elevation,
                        HrtfParams params) {
        double rotationPhase = periodSec > 0f ? (tSec % periodSec) / periodSec : 0.0;
        processByPhase(in, out, frames, rotationPhase, azimuth0, radius, elevation, params);
    }

    /** 用外部相位 [0,1) 驱动旋转（配合周期随机器） */
    public void processByPhase(short[] in, short[] out, int frames, double rotationPhase,
                               float azimuth0, float radius, float elevation) {
        processByPhase(in, out, frames, rotationPhase, azimuth0, radius, elevation, HrtfParams.defaults());
    }

    public void processByPhase(short[] in, short[] out, int frames, double rotationPhase,
                               float azimuth0, float radius, float elevation, HrtfParams params) {
        if (params == null) params = HrtfParams.defaults();
        double azimuth = azimuth0 * Math.PI / 180.0;
        azimuth += 2.0 * Math.PI * rotationPhase;
        double az = normalizePi(azimuth);

        double el = Math.max(-60.0, Math.min(60.0, elevation)) * Math.PI / 180.0;
        double sinEl = Math.sin(el);
        double cosEl = Math.cos(el);

        float rad = Math.max(0f, Math.min(1f, radius));
        float strength = 1f - 0.65f * rad;

        // ITD 用分数延迟 + 线性插值，不做整数取整：旋转时不再跳档、无颗粒感。
        double maxItdSamples = (params.maxItdMs / 1000.0) * RATE * strength;
        double itd = maxItdSamples * Math.sin(az) * cosEl;
        // sin(az)>0 = 声源在右 => 右耳先到，应延迟【左】声道（远耳）。
        // 反过来则延迟右声道。旧写法把延迟加在近耳，导致 ITD 与 ILD 指向相反、声像混乱偏侧。
        double itdL = Math.max(0, itd);
        double itdR = Math.max(0, -itd);
        int dLi = (int) Math.floor(itdL);
        int dRi = (int) Math.floor(itdR);
        float dLf = (float) (itdL - dLi);
        float dRf = (float) (itdR - dRi);
        if (dLi > 96) dLi = 96;
        if (dRi > 96) dRi = 96;

        double pan = Math.sin(az) * cosEl;
        double ildDepth = params.ildDepth * strength;
        double gainNear = 1.0;
        double gainFar = 1.0 - ildDepth * Math.abs(pan);
        boolean sourceRight = pan >= 0;

        double shadowAmt = params.shadow * strength * Math.abs(pan);
        double alphaFar = 1.0 - shadowAmt;

        // 耳廓陷波：中心频率随前后(cos az)与仰角(sin el)移动。真人耳廓的陷波在
        // 1–5 kHz：前方偏低、后方偏高、上方更高、下方更低，给前后/上下一个可辨的
        // 频谱下凹。旧的"整体提亮/压暗"只差 1–2 dB，人耳听不出，故改用窄带陷波。
        double frontBack = Math.cos(az);
        double notchFreq = 2700.0
                - 1200.0 * frontBack      // 前(~1.5k) / 后(~3.9k)
                + 900.0 * sinEl;           // 上更高、下更低
        if (notchFreq < 1200) notchFreq = 1200;
        if (notchFreq > 5000) notchFreq = 5000;
        double w0 = 2.0 * Math.PI * notchFreq / RATE;
        double r = params.notchR;              // 窄带深陷波(可调)
        double cw = Math.cos(w0);
        nb0 = 1f;
        nb1 = (float) (-2.0 * cw);
        nb2 = 1f;
        na1 = (float) (-2.0 * r * cw);
        na2 = (float) (r * r);

        int lenL = delayL.length;
        int lenR = delayR.length;

        for (int i = 0; i < frames; i++) {
            float mono = in[i] / 32768f;

            delayL[wL] = mono;
            delayR[wR] = mono;
            wL = (wL + 1) % lenL;
            wR = (wR + 1) % lenR;

            int rL0 = (wL - 1 - dLi + lenL) % lenL;
            int rL1 = (rL0 - 1 + lenL) % lenL;
            int rR0 = (wR - 1 - dRi + lenR) % lenR;
            int rR1 = (rR0 - 1 + lenR) % lenR;
            float vL = delayL[rL0] * (1f - dLf) + delayL[rL1] * dLf;
            float vR = delayR[rR0] * (1f - dRf) + delayR[rR1] * dRf;

            vL *= sourceRight ? gainFar : gainNear;
            vR *= sourceRight ? gainNear : gainFar;

            if (sourceRight) {
                lpFarL += (float) alphaFar * (vL - lpFarL);
                vL = lpFarL;
                lpFarR = vR;
            } else {
                lpFarR += (float) alphaFar * (vR - lpFarR);
                vR = lpFarR;
                lpFarL = vL;
            }

            // 耳廓陷波滤波（Direct Form I），左右耳各维护 x/y 历史。
            float yL = (float) (nb0 * vL + nb1 * nx1L + nb2 * nx2L - na1 * ny1L - na2 * ny2L);
            nx2L = nx1L; nx1L = vL; ny2L = ny1L; ny1L = yL;
            float yR = (float) (nb0 * vR + nb1 * nx1R + nb2 * nx2R - na1 * ny1R - na2 * ny2R);
            nx2R = nx1R; nx1R = vR; ny2R = ny1R; ny1R = yR;
            vL = yL;
            vR = yR;

            out[i * 2] = clamp16(vL);
            out[i * 2 + 1] = clamp16(vR);
        }
    }

    private static double normalizePi(double a) {
        while (a > Math.PI) a -= 2.0 * Math.PI;
        while (a < -Math.PI) a += 2.0 * Math.PI;
        return a;
    }

    private short clamp16(float v) {
        return limiter.limitToShort(v);
    }
}
