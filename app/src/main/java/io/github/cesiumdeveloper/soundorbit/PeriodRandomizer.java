package io.github.cesiumdeveloper.soundorbit;

import java.util.Random;

/**
 * 周期随机器：可选地把固定周期变成 [min, max] 内随机。
 * 每个周期结束时抽一个新周期，输出连续相位 phase∈[0,1)，避免切周期时相位跳变。
 *
 * enabled=false 时按固定 base 周期推进相位。
 */
public final class PeriodRandomizer {
    private final Random rnd = new Random();
    private boolean enabled;
    private float base;
    private float min;
    private float max;
    private float currentPeriod;
    private double lastTSec;
    private double phase; // 0..1
    private boolean primed;

    public void configure(boolean enabled, float base, float min, float max) {
        // 只有当周期配置真正变化时才重置相位。applyParams 每次 onPause 都用相同
        // 周期值调 configure，若无条件 phase=0 会让三角波跳回起点（位置瞬移）。
        boolean changed = this.enabled != enabled || this.base != base
                || this.min != min || this.max != max;
        this.enabled = enabled;
        this.base = base;
        this.min = min;
        this.max = max;
        if (changed || !primed) {
            this.primed = false;
            this.phase = 0.0;
        }
    }

    /** 用当前 tSec 推进并返回相位 [0,1)；tSec 单调（引擎累计秒数） */
    public double advance(double tSec) {
        if (!primed) {
            lastTSec = tSec;
            currentPeriod = enabled ? draw() : (base > 0f ? base : 0f);
            primed = true;
            return 0.0;
        }
        double dt = tSec - lastTSec;
        lastTSec = tSec;
        if (dt < 0) dt = 0;
        if (currentPeriod <= 0f) {
            return 0.0;
        }
        phase += dt / currentPeriod;
        while (phase >= 1.0) {
            phase -= 1.0;
            if (enabled) {
                currentPeriod = draw();
            }
        }
        return phase;
    }

    /** 供 UI/诊断读当前周期 */
    public float currentPeriod() {
        return currentPeriod;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    private float draw() {
        if (max <= min) return min;
        return min + rnd.nextFloat() * (max - min);
    }

    /** 校验范围是否合法 */
    public static String validate(android.content.Context c, String minText, String maxText) {
        float lo, hi;
        try {
            lo = Float.parseFloat(minText.trim());
        } catch (Throwable t) {
            return c.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_min_not_number);
        }
        try {
            hi = Float.parseFloat(maxText.trim());
        } catch (Throwable t) {
            return c.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_max_not_number);
        }
        if (lo <= 0f) return c.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_min_not_positive);
        if (hi <= 0f) return c.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_max_not_positive);
        if (hi < lo) return c.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_max_less_than_min);
        return null;
    }
}
