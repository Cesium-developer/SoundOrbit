package io.github.cesiumdeveloper.soundorbit;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 双耳渲染的前后/上下/左右线索回归测试。 */
public class HRTFProcessorTest {

    /** 用一段测试音渲染后返回平均幅度（左右各一）。 */
    private static double[] render(float az, float el) {
        HRTFProcessor p = new HRTFProcessor();
        p.reset();
        int n = 2000;
        short[] in = new short[n];
        short[] out = new short[n * 2];
        for (int i = 0; i < n; i++) {
            in[i] = (short) (Math.sin(2 * Math.PI * 1000 * i / 48000) * 20000);
        }
        p.processByPhase(in, out, n, 0.0, az, 0.25f, el);
        double l = 0, r = 0;
        for (int i = 0; i < n; i++) {
            l += out[i * 2];
            r += out[i * 2 + 1];
        }
        return new double[]{l / n, r / n};
    }

    @Test
    public void frontAndBack_areDistinguishable() {
        // 修复回归：原实现 sin(az) 让 0°/180° 输出完全相同
        double[] front = render(0f, 0f);
        double[] back = render(180f, 0f);
        assertTrue("front/back L differ", Math.abs(front[0] - back[0]) > 1.0);
    }

    @Test
    public void upperAndLower_areDistinguishable() {
        // 修复回归：原实现 el 只进 cos(el)，±60° 输出完全相同
        double[] up = render(90f, 60f);
        double[] down = render(90f, -60f);
        assertTrue("up/down R differ", Math.abs(up[1] - down[1]) > 1.0);
    }

    /** 用 RMS 能量比较左右耳响度（平均幅度/DC 对正弦波不可靠）。 */
    private static double[] renderBoth(float az, double freq) {
        HRTFProcessor p = new HRTFProcessor();
        p.reset();
        int n = 2048;
        short[] in = new short[n], out = new short[n * 2];
        for (int i = 0; i < n; i++) in[i] = (short) (Math.sin(2 * Math.PI * freq * i / 48000) * 20000);
        p.processByPhase(in, out, n, 0.0, az, 0.25f, 0f, HrtfParams.defaults());
        double l = 0, r = 0;
        for (int i = 0; i < n; i++) { l += out[i * 2] * out[i * 2]; r += out[i * 2 + 1] * out[i * 2 + 1]; }
        return new double[]{Math.sqrt(l / n), Math.sqrt(r / n)};
    }

    @Test
    public void leftAndRight_areMirrored() {
        // 90° = 声源在右 -> 右耳近、更响；270° = 声源在左 -> 左耳近、更响。
        double[] sourceRight = renderBoth(90f, 1000);
        double[] sourceLeft = renderBoth(270f, 1000);
        assertTrue("source right: R ear louder (R=" + sourceRight[1] + " L=" + sourceRight[0] + ")",
                sourceRight[1] > sourceRight[0]);
        assertTrue("source left: L ear louder (L=" + sourceLeft[0] + " R=" + sourceLeft[1] + ")",
                sourceLeft[0] > sourceLeft[1]);
    }

    /** 用脉冲测左右声道峰值位置(采样序号越小=越早到)。 */
    private static int[] arrivalPos(float az) {
        HRTFProcessor p = new HRTFProcessor();
        p.reset();
        short[] in = new short[500], out = new short[1000];
        in[100] = 20000;
        p.processByPhase(in, out, 500, 0.0, az, 0.3f, 0f, HrtfParams.defaults());
        int pl = 0, pr = 0;
        float ml = 0, mr = 0;
        for (int i = 0; i < 500; i++) {
            if (Math.abs(out[i * 2]) > ml) { ml = Math.abs(out[i * 2]); pl = i; }
            if (Math.abs(out[i * 2 + 1]) > mr) { mr = Math.abs(out[i * 2 + 1]); pr = i; }
        }
        return new int[]{pl, pr};
    }

    @Test
    public void itd_nearEarArrivesFirst() {
        // 回归：ITD 延迟必须加在远耳。旧写法加在近耳，导致时间线索与 ILD 指向相反。
        int[] rightSrc = arrivalPos(90f);   // 声源在右
        assertTrue("source right: R ear must arrive before L (R=" + rightSrc[1]
                + " L=" + rightSrc[0] + ")", rightSrc[1] < rightSrc[0]);
        int[] leftSrc = arrivalPos(270f);   // 声源在左
        assertTrue("source left: L ear must arrive before R (L=" + leftSrc[0]
                + " R=" + leftSrc[1] + ")", leftSrc[0] < leftSrc[1]);
    }

    /** 测某频点幅度（Goertzel，比全 DFT 轻）。 */
    private static double magnitudeAt(double[] x, int freq) {
        int n = x.length;
        double w = 2 * Math.PI * freq / 48000.0;
        double cw = Math.cos(w), coeff = 2 * cw;
        double s0 = 0, s1 = 0, s2 = 0;
        for (int i = 0; i < n; i++) { s0 = x[i] + coeff * s1 - s2; s2 = s1; s1 = s0; }
        return Math.sqrt(s1 * s1 + s2 * s2 - coeff * s1 * s2);
    }

    private static double[] renderWave(float az, float el, double freq) {
        HRTFProcessor p = new HRTFProcessor();
        p.reset();
        int n = 4096;
        short[] in = new short[n];
        short[] out = new short[n * 2];
        for (int i = 0; i < n; i++) {
            in[i] = (short) (Math.sin(2 * Math.PI * freq * i / 48000) * 20000);
        }
        p.processByPhase(in, out, n, 0.0, az, 0.25f, el);
        double[] wave = new double[n];
        for (int i = 0; i < n; i++) wave[i] = out[i * 2 + 1];
        return wave;
    }

    @Test
    public void frontBack_notchIsAudibleInSpectrum() {
        // 前后必须在某个频带有 >=6dB 的差异（耳廓陷波），否则人耳分不出前后。
        double[] front = renderWave(0f, 0f, 1000);
        double[] back = renderWave(180f, 0f, 1000);
        double mf = magnitudeAt(front, 1000), mb = magnitudeAt(back, 1000);
        double db = 20 * Math.log10((mf + 1) / (mb + 1));
        assertTrue("front/back 1kHz differ " + db + " dB", Math.abs(db) >= 6.0);
    }

    @Test
    public void upDown_notchIsAudibleInSpectrum() {
        double[] up = renderWave(90f, 60f, 2000);
        double[] down = renderWave(90f, -60f, 2000);
        double mu = magnitudeAt(up, 2000), md = magnitudeAt(down, 2000);
        double db = 20 * Math.log10((mu + 1) / (md + 1));
        assertTrue("up/down 2kHz differ " + db + " dB", Math.abs(db) >= 6.0);
    }
}
