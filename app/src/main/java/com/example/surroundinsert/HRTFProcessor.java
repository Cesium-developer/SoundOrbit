package com.example.surroundinsert;

/**
 * 真环绕声（双耳空间化）处理器：把一个单声道源渲染成绕头移动的空间音频。
 *
 * 双耳线索：
 *  - ITD（双耳时间差）：近侧耳先到
 *  - ILD（双耳强度差）：远侧耳衰减
 *  - 头部阴影低通：远侧耳高频衰减
 *
 * 每个实例处理一个声源，内部有独立延迟线状态；立体声直接输入时用两个实例
 * （L/R 各一路，起始方位相差 180°），合成单声道时只用一个。
 *
 * 参数：
 *  - 旋转周期 P（秒/圈）
 *  - 起始方位 azimuth0（度，0=正前，90=右，180=后，270=左）
 *  - 半径 radius（0~1，越小越贴头、ITD/ILD 越强）
 *  - 仰角 elevation（度，-60~60）
 */
public final class HRTFProcessor {
    private static final int RATE = 48000;

    private final float[] delayL;
    private final float[] delayR;
    private int wL;
    private int wR;

    private float lpFarL;
    private float lpFarR;

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
    }

    /**
     * 处理一帧单声道：in（帧数）-> out（交错 L,R，长度 frames*2）。
     * tSec 为该帧起始时间，用来算当前虚拟声源方位。
     */
    public void process(short[] in, short[] out, int frames, double tSec,
                        float periodSec, float azimuth0, float radius, float elevation) {
        double rotationPhase = periodSec > 0f ? (tSec % periodSec) / periodSec : 0.0;
        processByPhase(in, out, frames, rotationPhase, azimuth0, radius, elevation);
    }

    /** 用外部相位 [0,1) 驱动旋转（配合周期随机器） */
    public void processByPhase(short[] in, short[] out, int frames, double rotationPhase,
                               float azimuth0, float radius, float elevation) {
        double azimuth = azimuth0 * Math.PI / 180.0;
        azimuth += 2.0 * Math.PI * rotationPhase;
        double az = normalizePi(azimuth);

        double el = Math.max(-60.0, Math.min(60.0, elevation)) * Math.PI / 180.0;
        double cosEl = Math.cos(el);

        float rad = Math.max(0f, Math.min(1f, radius));
        float strength = 1f - 0.65f * rad;

        double maxItdSamples = 0.0007 * RATE * strength;
        double itd = maxItdSamples * Math.sin(az) * cosEl;
        double itdL = Math.max(0, -itd);
        double itdR = Math.max(0, itd);

        int dL = (int) Math.round(itdL);
        int dR = (int) Math.round(itdR);
        if (dL > 96) dL = 96;
        if (dR > 96) dR = 96;

        double pan = Math.sin(az) * cosEl;
        double ildDepth = 0.55 * strength;
        double gainNear = 1.0;
        double gainFar = 1.0 - ildDepth * Math.abs(pan);
        boolean sourceRight = pan >= 0;

        double shadowAmt = 0.55 * strength * Math.abs(pan);
        double alphaFar = 1.0 - shadowAmt;

        int lenL = delayL.length;
        int lenR = delayR.length;

        for (int i = 0; i < frames; i++) {
            float mono = in[i] / 32768f;

            delayL[wL] = mono;
            delayR[wR] = mono;
            wL = (wL + 1) % lenL;
            wR = (wR + 1) % lenR;

            int rL = (wL - 1 - dL + lenL) % lenL;
            int rR = (wR - 1 - dR + lenR) % lenR;
            float vL = delayL[rL];
            float vR = delayR[rR];

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

            out[i * 2] = clamp16(vL);
            out[i * 2 + 1] = clamp16(vR);
        }
    }

    private static double normalizePi(double a) {
        while (a > Math.PI) a -= 2.0 * Math.PI;
        while (a < -Math.PI) a += 2.0 * Math.PI;
        return a;
    }

    private static short clamp16(float v) {
        if (v > 0.99997f) return 32767;
        if (v < -1f) return -32768;
        return (short) (v * 32767f);
    }
}
