package io.github.cesiumdeveloper.soundorbit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 10 段图示均衡器：频率响应、默认中性、越界钳制。 */
public class GraphicEQTest {

    private static double rms(short[] x) {
        double s = 0;
        for (short v : x) s += v * v;
        return Math.sqrt(s / x.length);
    }

    private static short[] stereo(int freq) {
        int n = 4096;
        short[] b = new short[n * 2];
        for (int i = 0; i < n; i++) {
            short v = (short) (Math.sin(2 * Math.PI * freq * i / 48000) * 8000);
            b[i * 2] = v;
            b[i * 2 + 1] = v;
        }
        return b;
    }

    @Test
    public void isoFrequencies() {
        float[] expect = {31.5f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f};
        for (int i = 0; i < GraphicEQ.BANDS; i++) {
            assertEquals(expect[i], GraphicEQ.CENTERS[i], 0.01f);
        }
    }

    @Test
    public void defaultNeutral_passesUnchanged() {
        GraphicEQ eq = new GraphicEQ();
        assertTrue(eq.isNeutral());
        short[] in = stereo(1000);
        short[] out = new short[in.length];
        eq.process(in, out, in.length / 2);
        for (int i = 0; i < in.length; i++) assertEquals(in[i], out[i]);
    }

    @Test
    public void boostAtCenterBoostsOnlyThatBand() {
        // 回归：曾把 a0/a2 误写成 alpha*A，导致 b==a、H(z)=1、EQ 全通失效。
        GraphicEQ eq = new GraphicEQ();
        eq.setBand(6, 12f); // index 6 = 2000 Hz
        assertFalse(eq.isNeutral());
        short[] in2k = stereo(2000), out2k = new short[in2k.length];
        eq.clearState();
        eq.process(in2k, out2k, in2k.length / 2);
        double gain2k = 20 * Math.log10(rms(out2k) / rms(in2k));
        assertTrue("2kHz should boost ~12dB, got " + gain2k, gain2k > 9.0 && gain2k < 13.0);

        short[] in125 = stereo(125), out125 = new short[in125.length];
        eq.clearState();
        eq.process(in125, out125, in125.length / 2);
        double gain125 = 20 * Math.log10(rms(out125) / rms(in125));
        assertTrue("125Hz should be unaffected, got " + gain125, Math.abs(gain125) < 1.0);
    }

    @Test
    public void cutReducesLevel() {
        GraphicEQ eq = new GraphicEQ();
        eq.setBand(5, -12f); // 1000 Hz
        short[] in = stereo(1000), out = new short[in.length];
        eq.clearState();
        eq.process(in, out, in.length / 2);
        double g = 20 * Math.log10(rms(out) / rms(in));
        assertTrue("1kHz cut should reduce, got " + g, g < -6.0);
    }

    @Test
    public void clampsAndReset() {
        GraphicEQ eq = new GraphicEQ();
        eq.setBand(0, 30f);
        assertEquals(GraphicEQ.MAX_DB, eq.getBand(0), 0.0001f);
        eq.setBand(0, -30f);
        assertEquals(GraphicEQ.MIN_DB, eq.getBand(0), 0.0001f);
        eq.setBand(0, Float.NaN);
        assertEquals(0f, eq.getBand(0), 0.0001f);
        eq.setBand(3, 6f);
        eq.reset();
        assertTrue(eq.isNeutral());
    }
}
