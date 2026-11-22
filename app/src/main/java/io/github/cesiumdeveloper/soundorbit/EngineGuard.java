package io.github.cesiumdeveloper.soundorbit;

/**
 * 全局引擎运行标志：供首页与设置页统一判断"环绕声是否开启"。
 * 由两个引擎在启动/停止时更新。仅进程内有效。
 */
public final class EngineGuard {
    private static volatile boolean captureRunning = false;

    public static boolean isRunning() {
        return captureRunning || SurroundService.isRunning();
    }

    static void setCaptureRunning(boolean running) {
        captureRunning = running;
    }

    private EngineGuard() {
    }
}
