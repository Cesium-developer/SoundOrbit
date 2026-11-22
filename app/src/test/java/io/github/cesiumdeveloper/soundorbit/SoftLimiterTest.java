package io.github.cesiumdeveloper.soundorbit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 软限幅：阈值以下不动、永不超 1、单调、对称。 */
public class SoftLimiterTest {

    @Test
    public void defaultIsMinusOneDbTps() {
        assertEquals(0.89f, SoftLimiter.DEFAULT_THRESHOLD, 0.0001f);
    }

    @Test
    public void belowThreshold_passesUnchanged() {
        SoftLimiter l = new SoftLimiter(0.89f);
        for (float v = -0.89f; v <= 0.89f; v += 0.07f) {
            assertEquals("v=" + v, v, l.limit(v), 0.00001f);
        }
    }

    @Test
    public void neverExceedsUnity() {
        SoftLimiter l = new SoftLimiter();
        for (float v = -100f; v <= 100f; v += 0.5f) {
            assertTrue("v=" + v + " -> " + l.limit(v), Math.abs(l.limit(v)) <= 1.0f);
        }
    }

    @Test
    public void monotonic() {
        SoftLimiter l = new SoftLimiter(0.5f);
        float prev = -Float.MAX_VALUE;
        for (float v = -3f; v <= 3f; v += 0.01f) {
            float out = l.limit(v);
            assertTrue("not monotonic at " + v, out >= prev - 0.000001f);
            prev = out;
        }
    }

    @Test
    public void symmetric() {
        SoftLimiter l = new SoftLimiter();
        for (float v = 0.9f; v <= 3f; v += 0.1f) {
            assertEquals("v=" + v, l.limit(v), -l.limit(-v), 0.00001);
        }
    }

    @Test
    public void clampsThresholdRange() {
        SoftLimiter low = new SoftLimiter(0.001f);
        assertEquals(SoftLimiter.MIN_THRESHOLD, low.getThreshold(), 0.0001f);
        SoftLimiter high = new SoftLimiter(5f);
        assertEquals(SoftLimiter.MAX_THRESHOLD, high.getThreshold(), 0.0001f);
        SoftLimiter nan = new SoftLimiter(Float.NaN);
        assertEquals(SoftLimiter.DEFAULT_THRESHOLD, nan.getThreshold(), 0.0001f);
    }
}
