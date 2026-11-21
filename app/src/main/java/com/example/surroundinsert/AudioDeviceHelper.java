package com.example.surroundinsert;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

/** 启动时检测当前输出设备是否支持双声道 */
public final class AudioDeviceHelper {
    private AudioDeviceHelper() {
    }

    /** 当前活跃的输出设备中，是否至少有一个支持 2 声道 */
    public static boolean hasStereoOutput(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return true; // 查不到就不打扰用户
            AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo d : devices) {
                if (!d.isSink()) continue;
                int[] counts = d.getChannelCounts();
                if (counts == null) continue;
                for (int ch : counts) {
                    if (ch >= 2) return true;
                }
            }
            // 没有显式声明声道数的设备（多数蓝牙/有线耳机）默认按双声道处理
            for (AudioDeviceInfo d : devices) {
                if (!d.isSink()) continue;
                int[] counts = d.getChannelCounts();
                if (counts == null || counts.length == 0) return true;
            }
        } catch (Throwable ignored) {
            return true;
        }
        return false;
    }
}
