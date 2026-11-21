package io.github.cesiumdeveloper.soundorbit;

/**
 * 引擎与 UI 之间的只读快照。引擎每帧写入，UI 定时读取，避免跨进程/跨线程耦合。
 */
public final class EffectState {
    public static volatile float tri;
    public static volatile float gainL = 1f;
    public static volatile float gainR = 1f;
    public static volatile float phase;
    public static volatile float depth;
    public static volatile float period;
    public static volatile float ratio = 1f;
    public static volatile boolean active;
    public static volatile String error;
    public static volatile long lastUpdateNs;

    private EffectState() {
    }
}
