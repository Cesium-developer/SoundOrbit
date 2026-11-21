package io.github.cesiumdeveloper.soundorbit;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * 可选的无障碍保活服务。默认关闭，需用户自行到系统设置开启。
 * 本身不做任何自动化操作，只是让系统把进程当作"活跃服务"，降低被杀概率。
 */
public class KeepAliveAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
