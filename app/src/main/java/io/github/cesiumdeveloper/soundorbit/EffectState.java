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
    // 真环绕可视化：当前是否 HRTF 模式、声源方位/仰角(度)、是否双声源(立体声直接输入)
    public static volatile boolean hrtfMode;
    public static volatile float srcAzimuth;
    public static volatile float srcElevation;
    public static volatile boolean dualSource;
    public static volatile String error;
    public static volatile long lastUpdateNs;

    private EffectState() {
    }
}
