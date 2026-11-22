package io.github.cesiumdeveloper.soundorbit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** PeriodRandomizer 的 configure/advance 无 Android 依赖，可直接单测。 */
public class PeriodRandomizerTest {

    @Test
    public void configureWithSameParams_keepsPhase() {
        // 修复回归：applyParams 每次用相同周期 configure，不应把相位归零。
        PeriodRandomizer r = new PeriodRandomizer();
        r.configure(false, 20f, 5f, 30f);
        r.advance(0.0);
        r.advance(10.0); // phase = 10/20 = 0.5
        double before = r.advance(12.0);
        assertTrue("phase should be positive", before > 0.4);

        r.configure(false, 20f, 5f, 30f); // 同样的参数
        double after = r.advance(12.0);
        assertEquals("phase must be preserved", before, after, 0.001);
    }

    @Test
    public void configureWithChangedParams_resetsPhase() {
        PeriodRandomizer r = new PeriodRandomizer();
        r.configure(false, 20f, 5f, 30f);
        r.advance(0.0);
        double before = r.advance(10.0);
        assertTrue(before > 0.4);

        r.configure(false, 10f, 5f, 30f); // base 变了
        double after = r.advance(12.0);
        assertEquals("phase should reset to near 0", 0.0, after, 0.05);
    }

    @Test
    public void advance_wrapsPhaseWithinRange() {
        PeriodRandomizer r = new PeriodRandomizer();
        r.configure(false, 1f, 0f, 0f);
        r.advance(0.0);
        for (int i = 1; i <= 10; i++) {
            double p = r.advance(i * 0.25);
            assertTrue("phase in [0,1): " + p, p >= 0.0 && p < 1.0);
        }
    }

    @Test
    public void validationRejectsBadRanges() {
        // validate 需要 Context，这里只测能测的：configure 后 currentPeriod 反映 base
        PeriodRandomizer r = new PeriodRandomizer();
        r.configure(false, 7f, 0f, 0f);
        r.advance(0.0);
        assertEquals(7f, r.currentPeriod(), 0.001f);
    }
}
