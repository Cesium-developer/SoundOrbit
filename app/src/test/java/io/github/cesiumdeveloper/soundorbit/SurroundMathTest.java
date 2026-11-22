package io.github.cesiumdeveloper.soundorbit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** SurroundMath 是纯函数，无 Android 依赖，可直接单测。 */
public class SurroundMathTest {

    @Test
    public void gainsAtExtremes_areMirrored() {
        // 三角波 tri=|phase*2-1|：phase=0 与 phase=0.5 是两个极端，左右镜像。
        float[] left = SurroundMath.gainsFromPhase(20f, 1f, 0.0);
        float[] right = SurroundMath.gainsFromPhase(20f, 1f, 0.5);
        assertEquals(1f, left[0], 0.001f);
        assertEquals(1f - 20f, left[1], 0.001f);
        assertEquals(1f - 20f, right[0], 0.001f);
        assertEquals(1f, right[1], 0.001f);
    }

    @Test
    public void captureGain_normalizesToAttenuation() {
        // 修复回归：depth=20 时，近耳(1)->1.0，最远(1-depth)->0，中间->0.5
        assertEquals(1.0f, SurroundMath.toLinearCapture(1f, 20f), 0.001f);
        assertEquals(0.0f, SurroundMath.toLinearCapture(1f - 20f, 20f), 0.001f);
        assertEquals(0.5f, SurroundMath.toLinearCapture(1f - 10f, 20f), 0.001f);
    }

    @Test
    public void captureGain_neverAmplifies() {
        // 任何输入都不放大（<=1），避免削波
        for (float g = -30f; g <= 30f; g += 0.5f) {
            float out = SurroundMath.toLinearCapture(g, 20f);
            assertTrue("gain " + g + " -> " + out, out >= 0f && out <= 1f);
        }
    }

    @Test
    public void toLinear_clampsDbRange() {
        // 轻量引擎：无论多大增益，dB 钳在 [-96,12]
        float big = SurroundMath.toLinear(1e6f);
        float tiny = SurroundMath.toLinear(0f);
        assertTrue(big <= (float) Math.pow(10, 12.0 / 20) + 0.001f);
        assertTrue(tiny >= 0f && tiny < 0.001f);
    }
}
