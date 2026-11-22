package io.github.cesiumdeveloper.soundorbit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 发烧友参数：默认值必须与改动前硬编码值一致，且越界要钳制。 */
public class HrtfParamsTest {

    @Test
    public void defaults_matchLegacyHardcodedValues() {
        // 回归：用户不动参数时听感必须和改动前完全一样
        HrtfParams d = HrtfParams.defaults();
        assertEquals(0.7f, d.maxItdMs, 0.0001f);
        assertEquals(0.55f, d.ildDepth, 0.0001f);
        assertEquals(0.55f, d.shadow, 0.0001f);
        assertEquals(0.8f, d.notchR, 0.0001f);
    }

    @Test
    public void outOfRange_isClamped() {
        HrtfParams p = new HrtfParams(99f, -5f, 99f, 0.01f);
        assertEquals(1.0f, p.maxItdMs, 0.0001f);
        assertEquals(0.0f, p.ildDepth, 0.0001f);
        assertEquals(1.0f, p.shadow, 0.0001f);
        assertEquals(0.5f, p.notchR, 0.0001f);
    }

    @Test
    public void nanAndInfinity_fallBackToDefaults() {
        HrtfParams p = new HrtfParams(Float.NaN, Float.POSITIVE_INFINITY,
                Float.NaN, Float.NEGATIVE_INFINITY);
        HrtfParams d = HrtfParams.defaults();
        assertEquals(d.maxItdMs, p.maxItdMs, 0.0001f);
        assertEquals(d.ildDepth, p.ildDepth, 0.0001f);
        assertEquals(d.shadow, p.shadow, 0.0001f);
        assertEquals(d.notchR, p.notchR, 0.0001f);
    }
}
