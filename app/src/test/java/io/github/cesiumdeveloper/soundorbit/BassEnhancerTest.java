package io.github.cesiumdeveloper.soundorbit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 低音增强：默认关闭零开销、谐波确实生成、钳制。 */
public class BassEnhancerTest {

    @Test
    public void defaultOff_doesNothing() {
        BassEnhancer b = new BassEnhancer();
        assertEquals(0f, b.getStrength(), 0.0001f);
        int n = 256;
        short[] in = new short[n * 2], out = new short[n * 2];
        for (int i = 0; i < n * 2; i++) in[i] = (short) (Math.sin(i) * 10000);
        System.arraycopy(in, 0, out, 0, n * 2);
        b.process(out, n);
        for (int i = 0; i < n * 2; i++) assertEquals(in[i], out[i]);
    }

    @Test
    public void generatesHarmonics() {
        BassEnhancer b = new BassEnhancer();
        b.setStrength(0.8f);
        // 60Hz 低频（低于 crossover 80），小喇叭放不出，但应生成 120/180Hz 谐波
        int n = 4096;
        short[] buf = new short[n * 2];
        for (int i = 0; i < n; i++) {
            short v = (short) (Math.sin(2 * Math.PI * 60 * i / 48000) * 8000);
            buf[i * 2] = v; buf[i * 2 + 1] = v;
        }
        // 先测高通谐波路径确实有输出：处理后信号在 120Hz 处能量应上升
        double before120 = bandEnergy(buf, n, 120);
        b.process(buf, n);
        double after120 = bandEnergy(buf, n, 120);
        assertTrue("120Hz harmonic should increase (before=" + before120
                + " after=" + after120 + ")", after120 > before120 * 1.2);
    }

    /** 简单的 Goertzel 频段能量。 */
    private static double bandEnergy(short[] buf, int n, int freq) {
        double re = 0, im = 0;
        double w = 2 * Math.PI * freq / 48000;
        for (int i = 0; i < n; i++) {
            double v = buf[i * 2] / 32768.0;
            re += v * Math.cos(w * i);
            im += v * Math.sin(w * i);
        }
        return re * re + im * im;
    }

    @Test
    public void clampsStrength() {
        BassEnhancer b = new BassEnhancer();
        b.setStrength(5f);
        assertEquals(1f, b.getStrength(), 0.0001f);
        b.setStrength(-3f);
        assertEquals(0f, b.getStrength(), 0.0001f);
        b.setStrength(Float.NaN);
        assertEquals(0f, b.getStrength(), 0.0001f);
    }
}
