package io.github.cesiumdeveloper.soundorbit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.Locale;

public class SurroundService extends Service {
    private static final String TAG = "SurroundService";
    public static final String ACTION_PREPARE = "io.github.cesiumdeveloper.soundorbit.PREPARE";
    public static final String ACTION_START = "io.github.cesiumdeveloper.soundorbit.START";
    public static final String ACTION_STOP = "io.github.cesiumdeveloper.soundorbit.STOP";
    public static final String ACTION_UPDATE = "io.github.cesiumdeveloper.soundorbit.UPDATE";
    public static final String EXTRA_DEPTH = "depth";
    public static final String EXTRA_PERIOD = "period";
    public static final String EXTRA_RATIO = "ratio";
    public static final String EXTRA_RANDOM = "rand_enabled";
    public static final String EXTRA_RANDOM_MIN = "rand_min";
    public static final String EXTRA_RANDOM_MAX = "rand_max";
    private static final String CHANNEL_ID = "surround_channel";

    private static volatile boolean sRunning = false;

    private GlobalEffectEngine engine;
    private float depth = 20f;
    private float period = 20f;
    private float ratio = 1f;
    private boolean randEnabled;
    private float randMin;
    private float randMax;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AudioDeviceCallback deviceCallback;
    private boolean deviceCallbackRegistered;

    private final Runnable rebindTask = new Runnable() {
        @Override
        public void run() {
            if (engine != null && engine.isRunning()) {
                Log.i(TAG, "audio device changed, rebind effect");
                stopEngine();
                startEngine();
            }
        }
    };

    private final Runnable notificationTask = new Runnable() {
        @Override
        public void run() {
            if (engine != null && engine.isRunning()) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.notify(1, buildNotification());
                }
                mainHandler.postDelayed(this, 500);
            }
        }
    };

    public static boolean isRunning() {
        return sRunning;
    }

    public static String lastError() {
        return EffectState.error;
    }

    public static void clearError() {
        EffectState.error = null;
    }

    public static void prepare(Context c) {
    }

    public static void start(Context c, float d, float p, float r) {
        start(c, d, p, r, false, 0f, 0f);
    }

    public static void start(Context c, float d, float p, float r,
                             boolean rand, float randMin, float randMax) {
        Intent i = new Intent(c, SurroundService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_DEPTH, d);
        i.putExtra(EXTRA_PERIOD, p);
        i.putExtra(EXTRA_RATIO, r);
        i.putExtra(EXTRA_RANDOM, rand);
        i.putExtra(EXTRA_RANDOM_MIN, randMin);
        i.putExtra(EXTRA_RANDOM_MAX, randMax);
        c.startForegroundService(i);
    }

    public static void updateParams(Context c, float d, float p, float r) {
        updateParams(c, d, p, r, false, 0f, 0f);
    }

    public static void updateParams(Context c, float d, float p, float r,
                                    boolean rand, float randMin, float randMax) {
        Intent i = new Intent(c, SurroundService.class);
        i.setAction(ACTION_UPDATE);
        i.putExtra(EXTRA_DEPTH, d);
        i.putExtra(EXTRA_PERIOD, p);
        i.putExtra(EXTRA_RATIO, r);
        i.putExtra(EXTRA_RANDOM, rand);
        i.putExtra(EXTRA_RANDOM_MIN, randMin);
        i.putExtra(EXTRA_RANDOM_MAX, randMax);
        c.startService(i);
    }

    public static void stop(Context c) {
        Intent i = new Intent(c, SurroundService.class);
        i.setAction(ACTION_STOP);
        c.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        registerDeviceCallback();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopEngine();
            sRunning = false;
            mainHandler.removeCallbacks(notificationTask);
            stopForeground(true);
            stopSelf();
            updateTile();
            return START_NOT_STICKY;
        }

        startForeground(1, buildNotification());

        if (ACTION_PREPARE.equals(action)) {
            return START_STICKY;
        }

        if (intent != null) {
            depth = intent.getFloatExtra(EXTRA_DEPTH, Prefs.getDepth(this));
            period = intent.getFloatExtra(EXTRA_PERIOD, Prefs.getPeriod(this));
            ratio = intent.getFloatExtra(EXTRA_RATIO, Prefs.getRatio(this));
            randEnabled = intent.getBooleanExtra(EXTRA_RANDOM, Prefs.getPeriodRandom(this));
            randMin = intent.getFloatExtra(EXTRA_RANDOM_MIN, Prefs.getPeriodRandomMin(this));
            randMax = intent.getFloatExtra(EXTRA_RANDOM_MAX, Prefs.getPeriodRandomMax(this));
            Prefs.save(this, depth, period, ratio);
        } else {
            depth = Prefs.getDepth(this);
            period = Prefs.getPeriod(this);
            ratio = Prefs.getRatio(this);
            randEnabled = Prefs.getPeriodRandom(this);
            randMin = Prefs.getPeriodRandomMin(this);
            randMax = Prefs.getPeriodRandomMax(this);
        }

        if (ACTION_UPDATE.equals(action) && engine != null) {
            engine.setAll(depth, period, ratio, randEnabled, randMin, randMax);
        } else {
            startEngine();
        }
        return START_STICKY;
    }

    private void startEngine() {
        if (engine != null && engine.isRunning()) {
            engine.setAll(depth, period, ratio, randEnabled, randMin, randMax);
            return;
        }
        try {
            engine = new GlobalEffectEngine();
            engine.start(depth, period, ratio, randEnabled, randMin, randMax);
            sRunning = engine.isRunning();
            if (sRunning) {
                EffectState.error = null;
                mainHandler.removeCallbacks(notificationTask);
                mainHandler.post(notificationTask);
            }
        } catch (Throwable t) {
            Log.e(TAG, "startEngine failed", t);
            EffectState.error = friendlyError(this, t);
            sRunning = false;
            engine = null;
        }
        updateTile();
    }

    private static String friendlyError(android.content.Context c, Throwable t) {
        String m = t == null ? "" : String.valueOf(t.getMessage());
        if (m.contains("Cannot initialize effect")) {
            return c.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_no_global_effect);
        }
        if (m.contains("SecurityException") || m.contains("permission")) {
            return c.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_no_audio_permission);
        }
        return m.isEmpty() ? c.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_unknown) : m;
    }

    private void stopEngine() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        mainHandler.removeCallbacks(notificationTask);
    }

    private void registerDeviceCallback() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return;
            }
            deviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    scheduleRebind();
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    scheduleRebind();
                }
            };
            am.registerAudioDeviceCallback(deviceCallback, mainHandler);
            deviceCallbackRegistered = true;
        } catch (Throwable t) {
            Log.w(TAG, "registerAudioDeviceCallback failed", t);
        }
    }

    private void unregisterDeviceCallback() {
        if (!deviceCallbackRegistered || deviceCallback == null) {
            return;
        }
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.unregisterAudioDeviceCallback(deviceCallback);
            }
        } catch (Throwable ignored) {
        }
        deviceCallbackRegistered = false;
    }

    private void scheduleRebind() {
        mainHandler.removeCallbacks(rebindTask);
        mainHandler.postDelayed(rebindTask, 600);
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text;
        if (sRunning) {
            String gains = String.format(Locale.US, "L %.2f  R %.2f",
                    EffectState.gainL, EffectState.gainR);
            String params = String.format(Locale.US, getString(R.string.notification_params),
                    fmt(depth), fmt(period), fmt(ratio));
            text = gains + "\n" + params;
        } else {
            String err = EffectState.error;
            text = err == null ? getString(R.string.notification_text) : getString(R.string.notification_failed, err);
        }

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_surround)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(text.replace("\n", " | "))
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private static String fmt(float v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private void updateTile() {
        SurroundTileService.requestUpdate(this);
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(rebindTask);
        mainHandler.removeCallbacks(notificationTask);
        unregisterDeviceCallback();
        stopEngine();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
