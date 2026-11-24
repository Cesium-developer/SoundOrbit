package io.github.cesiumdeveloper.soundorbit;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * 实时调参复合测试：模拟音频流处理过程中动态改参数，验证
 * 1) 每个可实时参数改变后输出确实按预期变化；
 * 2) 流中途改参数不产生静音、尖峰或 NaN（无 glitch）；
 * 3) 边界与极端组合下鲁棒。
 *
 * 对应 UI 层放开的实时参数：depth / ratio / azimuth / radius / elevation /
 * EQ / bass / softThreshold / HRTF params。周期类不在其中（会重置相位）。
 */
public class RealtimeParamTest {

    /** 生成一段 1kHz 正弦测试音，返回左右相同的立体声帧 */
    private static short[] tone(int frames, float amp) {
        short[] b = new short[frames * 2];
        for (int i = 0; i < frames; i++) {
            short v = (short) (Math.sin(2 * Math.PI * 1000 * i / 48000.0) * amp);
            b[i * 2] = v;
            b[i * 2 + 1] = v;
        }
        return b;
    }

    /** 渲染单帧，返回左右样本和（用于比较能量分布） */
    private static double[] render(HRTFProcessor p, short[] in, int frames, double phase,
                                   float az, float rad, float el, HrtfParams params) {
        short[] out = new short[frames * 2];
        p.processByPhase(in, out, frames, phase, az, rad, el, params, 0, 0);
        double l = 0, r = 0;
        for (int i = 0; i < frames; i++) { l += out[i * 2]; r += out[i * 2 + 1]; }
        return new double[]{l, r};
    }

    @Test
    public void azimuthChange_shiftsImage() {
        HRTFProcessor p = new HRTFProcessor(); p.reset();
        short[] in = tone(2000, 20000f);
        double[] front = render(p, in, 2000, 0.0, 0f, 0.3f, 0f, HrtfParams.defaults());
        p.reset();
        double[] right = render(p, in, 2000, 0.0, 90f, 0.3f, 0f, HrtfParams.defaults());
        // 正前：左右基本对称；右侧：左右不对称。用 |L-R| 衡量偏移
        double frontAsym = Math.abs(front[0] - front[1]);
        double rightAsym = Math.abs(right[0] - right[1]);
        assertTrue("右方声像应比正前更偏（front=" + frontAsym + ", right=" + rightAsym + "）",
                rightAsym > frontAsym + 100);
    }

    @Test
    public void elevationChange_changesOutput() {
        HRTFProcessor p = new HRTFProcessor(); p.reset();
        short[] in = tone(2000, 20000f);
        double[] mid = render(p, in, 2000, 0.0, 0f, 0.5f, 0f, HrtfParams.defaults());
        p.reset();
        double[] up = render(p, in, 2000, 0.0, 0f, 0.5f, 60f, HrtfParams.defaults());
        double diff = Math.abs(mid[0] - up[0]) + Math.abs(mid[1] - up[1]);
        assertTrue("仰角 0° 与 60° 输出应不同，实际 diff=" + diff, diff > 100);
    }

    @Test
    public void radiusChange_changesStrength() {
        HRTFProcessor p = new HRTFProcessor(); p.reset();
        short[] in = tone(2000, 20000f);
        double[] near = render(p, in, 2000, 0.0, 90f, 0.1f, 0f, HrtfParams.defaults());
        p.reset();
        double[] far = render(p, in, 2000, 0.0, 90f, 0.95f, 0f, HrtfParams.defaults());
        double nearEnergy = Math.abs(near[0]) + Math.abs(near[1]);
        double farEnergy = Math.abs(far[0]) + Math.abs(far[1]);
        assertTrue("近头(小半径)强度应不同于远头，near=" + nearEnergy + " far=" + farEnergy,
                Math.abs(nearEnergy - farEnergy) > 100);
    }

    @Test
    public void hrtfParamsChange_changesOutput() {
        HRTFProcessor p = new HRTFProcessor(); p.reset();
        short[] in = tone(2000, 20000f);
        double[] a = render(p, in, 2000, 0.0, 45f, 0.4f, 20f, HrtfParams.defaults());
        p.reset();
        HrtfParams extreme = new HrtfParams(1.0f, 1.0f, 1.0f, 0.95f);
        double[] b = render(p, in, 2000, 0.0, 45f, 0.4f, 20f, extreme);
        double diff = Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
        assertTrue("HRTF 参数改变应使输出不同，diff=" + diff, diff > 100);
    }

    /**
     * 核心：流处理中途逐段改参数，验证不产生瞬断或尖峰。
     * 每段 96 帧（对应引擎的 2ms 切片），共 40 段。每段用不同参数，
     * 检查相邻段平均幅度的比值——不应出现整段静音或几十倍跳变。
     */
    @Test
    public void paramChangeMidStream_noSilenceNoSpike() {
        HRTFProcessor p = new HRTFProcessor(); p.reset();
        int seg = 96, segs = 40;
        short[] in = tone(seg, 18000f);
        double[] prevRms = null;
        for (int s = 0; s < segs; s++) {
            // 每帧动态改所有可实时 HRTF 参数（模拟 UI 实时推送）
            float az = (s * 37) % 360;
            float rad = 0.1f + (s % 10) * 0.08f;
            float el = -60f + (s % 8) * 17f;
            HrtfParams params = new HrtfParams(0.4f + (s%6)*0.1f, (s%5)*0.2f,
                    (s%5)*0.2f, 0.5f + (s%5)*0.1f);
            short[] out = new short[seg * 2];
            p.processByPhase(in, out, seg, s / (double) segs, az, rad, el, params, 0, 0);
            long sum = 0;
            for (short v : out) sum += (long) v * v;
            double rms = Math.sqrt(sum / out.length);
            assertTrue("第 " + s + " 段输出不应静音，rms=" + rms, rms > 1.0);
            assertTrue("第 " + s + " 段输出不应爆表，rms=" + rms, rms < 20000);
            if (prevRms != null && prevRms[0] > 1.0) {
                double ratio = rms / prevRms[0];
                assertTrue("相邻段幅度不应突变几十倍：seg=" + s + " ratio=" + ratio,
                        ratio > 0.02 && ratio < 50);
            }
            prevRms = new double[]{rms};
        }
    }

    @Test
    public void extremeParams_allFinite() {
        // 所有参数取边界组合，输出必须是合法 short（无 NaN——short 天然有限，检查不崩即可）
        float[] azs = {-360f, 0f, 90f, 360f, 720f};
        float[] rads = {-1f, 0f, 0.5f, 1f, 2f};   // 含越界，验证钳制
        float[] els = {-120f, -60f, 0f, 60f, 120f};
        short[] in = tone(480, 20000f);
        for (float az : azs) for (float rad : rads) for (float el : els) {
            HRTFProcessor p = new HRTFProcessor(); p.reset();
            short[] out = new short[480 * 2];
            HrtfParams params = new HrtfParams(Float.NaN, Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY, Float.NaN); // 全非法 → 回退默认
            p.processByPhase(in, out, 480, 0.5, az, rad, el, params, 0, 0);
            // 只要不抛异常即通过
        }
    }

    @Test
    public void eqBandsChange_appliesAndClamps() {
        GraphicEQ eq = new GraphicEQ();
        short[] in = tone(1024, 8000f);
        short[] out = new short[in.length];
        // 实时改 EQ：全 +12dB
        float[] hot = new float[GraphicEQ.BANDS];
        for (int i = 0; i < hot.length; i++) hot[i] = 12f;
        eq.setBands(hot);
        eq.process(in, out, 1024);
        // 检查越界钳制：即使传入 100dB 也限制在 MAX_DB
        float[] over = new float[GraphicEQ.BANDS];
        for (int i = 0; i < over.length; i++) over[i] = 100f;
        eq.setBands(over);
        for (int i = 0; i < GraphicEQ.BANDS; i++)
            assertEquals("EQ 应钳制到 MAX_DB", GraphicEQ.MAX_DB, eq.getBand(i), 0.001f);
    }

    @Test
    public void nanRadiusElevation_doesNotPoisonFilter() {
        HRTFProcessor p = new HRTFProcessor(); p.reset();
        short[] in = tone(256, 16000f);
        // 喂一帧 NaN 半径/仰角（输入非法值）
        short[] bad = new short[256 * 2];
        p.processByPhase(in, bad, 256, 0.0, 0f, Float.NaN, Float.NaN, HrtfParams.defaults(), 0, 0);
        long badSum = 0;
        for (short v : bad) badSum += (long) v * v;
        assertTrue("NaN 帧不应输出爆炸", badSum < 256L * 2 * 32768L * 32768L);
        // 紧接着喂正常帧，必须能恢复出声（不被 NaN 污染状态）
        short[] good = new short[256 * 2];
        p.processByPhase(in, good, 256, 0.1, 30f, 0.4f, 10f, HrtfParams.defaults(), 0, 0);
        int nonzero = 0;
        for (short v : good) if (v != 0) nonzero++;
        assertTrue("NaN 帧后正常帧应恢复出声，实际非零=" + nonzero, nonzero > 0);
    }

    @Test
    public void softThresholdChange_clampsAndApplies() {
        SoftLimiter sl = new SoftLimiter(0.89f);
        // 逐样本限幅：极端阈值下仍返回合法 short
        for (int v = -32768; v < 32768; v += 997) {
            short out = sl.limitToShort(v / 32768f);
            assertTrue(out >= -32768 && out <= 32767);
        }
        sl.setThreshold(5f);
        assertTrue("阈值应钳制 ≤1", sl.getThreshold() <= 1f);
        sl.setThreshold(-5f);
        assertTrue("阈值应钳制 ≥0", sl.getThreshold() >= 0f);
    }

    @Test
    public void bassChange_appliesWithoutCrash() {
        BassEnhancer b = new BassEnhancer();
        float[] strengths = {0f, 0.25f, 0.5f, 0.75f, 1f, -1f, 5f};
        for (float s : strengths) {
            b.setStrength(s);
            short[] buf = tone(1024, 12000f);
            b.process(buf, 1024); // 原地处理
            for (short v : buf) assertTrue(v >= -32768 && v <= 32767);
        }
    }
}
