package io.github.cesiumdeveloper.soundorbit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** MediaProjection 必需的 mediaProjection 类型前台服务 */
public class CaptureFgService extends Service {
    private static final String CHANNEL = "capture_fg";
    // 引擎引用：服务被用户从通知栏划掉时，通知引擎同步清理授权状态
    static volatile CaptureEngine boundEngine;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, getString(io.github.cesiumdeveloper.soundorbit.R.string.capture_channel), NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_surround)
                .setContentTitle(getString(io.github.cesiumdeveloper.soundorbit.R.string.capture_notif_title))
                .setContentText(getString(io.github.cesiumdeveloper.soundorbit.R.string.capture_notif_text))
                .build();
        startForeground(2001, n);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        // 用户从通知栏划掉服务 = 捕获授权失效。通知引擎清状态、刷 UI，
        // 避免"实际没授权但显示已授权"必须杀进程的死锁。
        CaptureEngine e = boundEngine;
        if (e != null) e.notifyServiceStopped();
        stopForeground(true);
        super.onDestroy();
    }
}
