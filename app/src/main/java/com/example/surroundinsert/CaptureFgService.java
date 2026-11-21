package com.example.surroundinsert;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** MediaProjection 必需的 mediaProjection 类型前台服务 */
public class CaptureFgService extends Service {
    private static final String CHANNEL = "capture_fg";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, getString(com.example.surroundinsert.R.string.capture_channel), NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_surround)
                .setContentTitle(getString(com.example.surroundinsert.R.string.capture_notif_title))
                .setContentText(getString(com.example.surroundinsert.R.string.capture_notif_text))
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
        stopForeground(true);
        super.onDestroy();
    }
}
