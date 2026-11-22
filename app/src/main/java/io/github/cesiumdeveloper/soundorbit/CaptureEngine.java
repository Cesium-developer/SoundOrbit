package io.github.cesiumdeveloper.soundorbit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.Log;

/**
 * 高级引擎：用 AudioPlaybackCapture 抓其他 App 正在播放的音频（与输出设备无关，蓝牙也能抓），
 * 在 App 进程内做声道处理 + 反相三角波增益，再用 AudioTrack 播回。
 * 启动时静音 STREAM_MUSIC 掐掉原声（实测静音不影响捕获），只播处理后的音频，因此无双份声音；
 * 关闭时恢复原音量。
 */
public final class CaptureEngine {
    private static final String TAG = "CaptureEngine";
    public static final int REQ_CAPTURE = 7002;
    private static final int STREAM_ASSISTANT = 11; // 隐藏常量，公开的 AudioManager 未暴露

    // 模式值必须与 spinner 位置一致（最终 7 项）
    public static final int MODE_TAKE_LEFT = 0;    // 伪环绕：取左 → 双耳
    public static final int MODE_TAKE_RIGHT = 1;   // 伪环绕：取右 → 双耳
    public static final int MODE_TAKE_MIX = 2;     // 伪环绕：L+R 合成单声道 → 双耳
    public static final int MODE_HRTF_STEREO = 3;  // 真环绕：立体声直接输入（L/R 各自独立绕头）
    public static final int MODE_HRTF_LEFT = 4;    // 真环绕：取左声道源绕头
    public static final int MODE_HRTF_RIGHT = 5;   // 真环绕：取右声道源绕头
    public static final int MODE_HRTF_MIX = 6;     // 真环绕：L+R 合成单声道绕头

    private final Context app;
    private MediaProjection projection;
    private AudioRecord rec;
    private AudioTrack track;
    private Thread worker;
    private volatile boolean running;

    private volatile float depth = 20f;
    private volatile float period = 20f;
    private volatile float ratio = 1f;
    private volatile int mode = MODE_TAKE_LEFT;

    // 真环绕声（HRTF）专属参数
    private volatile float hrtfAzimuth = 0f;    // 起始方位，度，0=正前
    private volatile float hrtfRadius = 0.5f;   // 半径 0~1，越小越贴头
    private volatile float hrtfElevation = 0f;  // 仰角，度

    private volatile String lastError;
    private HRTFProcessor hrtf;
    private HRTFProcessor hrtfR; // 立体声直接输入时右路独立实例
    private final PeriodRandomizer randomizer = new PeriodRandomizer();
    private boolean randEnabled;
    private float randMin;
    private float randMax;
    private Runnable authCallback;

    private android.media.AudioManager audioManager;
    private int savedMusicVolume;
    private int savedAssistantVolume;
    private boolean musicMuted;

    public CaptureEngine(Context c) {
        this.app = c.getApplicationContext();
        this.audioManager = (android.media.AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
    }

    /** 从 App 自己的 Activity 发起授权 */
    public void requestAuthorization(Activity a) {
        MediaProjectionManager mpm =
                (MediaProjectionManager) a.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        a.startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    public void setAuthCallback(Runnable r) {
        authCallback = r;
    }

    /** 在 Activity.onActivityResult 里调用 */
    public void onAuthorizationResult(Context c, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            lastError = app.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_capture_denied);
            if (authCallback != null) authCallback.run();
            return;
        }
        // 先启动 mediaProjection 类型前台服务
        Intent fg = new Intent(c, CaptureFgService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            c.startForegroundService(fg);
        } else {
            c.startService(fg);
        }
        final int rc = resultCode;
        final Intent d = data;
        // 关键：必须等前台服务真正 startForeground 后才能 getMediaProjection，
        // 否则抛 SecurityException（表现为"第一次授权不生效、要授权第二次"）。
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                MediaProjectionManager mpm =
                        (MediaProjectionManager) c.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                projection = mpm.getMediaProjection(rc, d);
                lastError = null;
                Log.i(TAG, "media projection acquired");
            } catch (Throwable t) {
                lastError = app.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_get_capture_failed, String.valueOf(t.getMessage()));
                Log.e(TAG, "getMediaProjection failed", t);
            }
            if (authCallback != null) authCallback.run();
        }, 600);
    }

    public boolean isAuthorized() {
        return projection != null;
    }

    public String getLastError() {
        return lastError;
    }

    /** 返回 0 成功，负数失败（旧签名，保留兼容） */
    public synchronized int start(float d, float p, float r, int m) {
        return start(d, p, r, m, 0f, 0.5f, 0f);
    }

    /** 返回 0 成功，负数失败（含真环绕声参数） */
    public synchronized int start(float d, float p, float r, int m,
                                   float az, float rad, float el) {
        if (running) {
            update(d, p, r, m, az, rad, el);
            return 0;
        }
        if (projection == null) {
            lastError = app.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_capture_not_authorized);
            return -1;
        }
        depth = d;
        period = p;
        ratio = r;
        mode = m;
        hrtfAzimuth = az;
        hrtfRadius = rad;
        hrtfElevation = el;
        randomizer.configure(randEnabled, period, randMin, randMax);
        try {
            int rate = 48000;
            int chIn = AudioFormat.CHANNEL_IN_STEREO;
            int fmt = AudioFormat.ENCODING_PCM_16BIT;
            int frames = rate * 40 / 1000;
            int byteBuf = frames * 2 * 2;

            AudioPlaybackCaptureConfiguration cfg =
                    new AudioPlaybackCaptureConfiguration.Builder(projection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            // 排除自己，防止播回的声音被再次捕获 -> 递归啸叫。
                            // 不用 ALLOW_CAPTURE_BY_NONE（那会让本 ROM 的 track 直接不发声）。
                            .excludeUid(android.os.Process.myUid())
                            .build();
            rec = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(rate).setChannelMask(chIn).setEncoding(fmt).build())
                    .setBufferSizeInBytes(byteBuf)
                    .setAudioPlaybackCaptureConfig(cfg)
                    .build();
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                lastError = app.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_record_init_failed);
                rec.release();
                rec = null;
                return -2;
            }

            // 播回用 USAGE_ASSISTANT + CONTENT_TYPE_SPEECH：必须配 SPEECH。
            // 在部分 ROM（如一加 ColorOS / Android 15）上，CONTENT_TYPE_MUSIC + USAGE_ASSISTANT
            // 会被 audio policy 归到 STRATEGY_MEDIA 的 STREAM_MUSIC 组，导致
            // setStreamMute(STREAM_MUSIC) 把播回一起静音 -> 完全没声音。
            // 而 SPEECH + USAGE_ASSISTANT 映射到独立的 AUDIO_STREAM_ASSISTANT，不受影响。
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(fmt).build())
                    .setBufferSizeInBytes(byteBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                lastError = app.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_track_init_failed);
                rec.release();
                rec = null;
                track.release();
                track = null;
                return -3;
            }

            if (isHrtf(mode)) {
                hrtf = new HRTFProcessor();
                hrtf.reset();
                if (mode == MODE_HRTF_STEREO) {
                    hrtfR = new HRTFProcessor();
                    hrtfR.reset();
                }
            }
            // 静音媒体 stream 掐掉原声（实测静音不影响 capture），只播处理后的 ASSISTANT 流
            muteMedia(true);
            running = true;
            worker = new Thread(() -> loop(frames), "capture-engine");
            worker.start();
            rec.startRecording();
            track.play();
            lastError = null;
            Log.i(TAG, "started mode=" + mode);
            // 启动 1 秒后写自检到文件，避免 logcat 被冲
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                selfCheck();
            }, 1000);
            return 0;
        } catch (Throwable t) {
            lastError = app.getString(io.github.cesiumdeveloper.soundorbit.R.string.err_start_failed, String.valueOf(t.getMessage()));
            Log.e(TAG, "start failed", t);
            muteMedia(false);
            return -1;
        }
    }

    public synchronized void stop() {
        running = false;
        EffectState.active = false;
        Thread w = worker;
        worker = null;
        if (w != null) {
            try {
                w.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
        if (rec != null) {
            try { rec.stop(); } catch (Throwable ignored) {}
            try { rec.release(); } catch (Throwable ignored) {}
            rec = null;
        }
        if (track != null) {
            try { track.stop(); } catch (Throwable ignored) {}
            try { track.release(); } catch (Throwable ignored) {}
            track = null;
        }
        muteMedia(false);
        try {
            app.stopService(new Intent(app, CaptureFgService.class));
        } catch (Throwable ignored) {
        }
        Log.i(TAG, "stopped");
    }

    /** 启动后自检：把各 stream 实时音量、track 状态写入文件 */
    private void selfCheck() {
        try {
            StringBuilder b = new StringBuilder();
            b.append("running=").append(running).append("\n");
            b.append("musicVol=").append(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)).append("\n");
            b.append("assistantVol=").append(audioManager.getStreamVolume(STREAM_ASSISTANT))
                    .append("/max").append(audioManager.getStreamMaxVolume(STREAM_ASSISTANT)).append("\n");
            b.append("assistantMuted=").append(audioManager.isStreamMute(STREAM_ASSISTANT)).append("\n");
            b.append("musicMuted=").append(audioManager.isStreamMute(android.media.AudioManager.STREAM_MUSIC)).append("\n");
            if (track != null) {
                b.append("trackState=").append(track.getState())
                        .append(" playState=").append(track.getPlayState())
                        .append(" headPos=").append(track.getPlaybackHeadPosition()).append("\n");
            } else {
                b.append("track=null\n");
            }
            if (rec != null) {
                b.append("recState=").append(rec.getState())
                        .append(" recSession=").append(rec.getAudioSessionId()).append("\n");
            }
            writeFile("capture_selfcheck.txt", b.toString());
            Log.i(TAG, "selfCheck:\n" + b);
        } catch (Throwable t) {
            writeFile("capture_selfcheck.txt", "selfCheck FAILED " + t + "\n");
        }
    }

    private void writeFile(String name, String text) {
        try {
            java.io.File f = new java.io.File(app.getExternalFilesDir(null), name);
            java.io.FileWriter w = new java.io.FileWriter(f, false);
            w.write(text);
            w.close();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 静音/恢复媒体 stream，掐掉原声，只留处理后的播回。
     * 实测（OPPO/ColorOS）：setStreamVolume(MUSIC,0) 会连带掐掉 ASSISTANT 播回；
     * 而 setStreamMute(MUSIC,true) 只静音 MUSIC、播回照常出声。所以用后者。
     */
    private void muteMedia(boolean mute) {
        if (audioManager == null) return;
        try {
            if (mute) {
                if (!musicMuted) {
                    audioManager.setStreamMute(android.media.AudioManager.STREAM_MUSIC, true);
                    musicMuted = true;
                    Log.i(TAG, "media muted via setStreamMute");
                }
            } else {
                if (musicMuted) {
                    audioManager.setStreamMute(android.media.AudioManager.STREAM_MUSIC, false);
                    musicMuted = false;
                    Log.i(TAG, "media unmuted");
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "muteMedia failed", t);
        }
    }

    public void release() {
        stop();
        if (projection != null) {
            try { projection.stop(); } catch (Throwable ignored) {}
            projection = null;
        }
    }

    public void update(float d, float p, float r, int m) {
        update(d, p, r, m, hrtfAzimuth, hrtfRadius, hrtfElevation);
    }

    public void update(float d, float p, float r, int m, float az, float rad, float el) {
        depth = d;
        period = p;
        ratio = r;
        mode = m;
        hrtfAzimuth = az;
        hrtfRadius = rad;
        hrtfElevation = el;
        randomizer.configure(randEnabled, period, randMin, randMax);
    }

    /** 设置周期随机配置（start 前或运行中均可） */
    public void configureRandom(boolean enabled, float min, float max) {
        randEnabled = enabled;
        randMin = min;
        randMax = max;
        if (running) {
            randomizer.configure(enabled, period, min, max);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public static boolean isHrtf(int m) {
        return m == MODE_HRTF_STEREO || m == MODE_HRTF_LEFT
                || m == MODE_HRTF_RIGHT || m == MODE_HRTF_MIX;
    }

    private void loop(int frames) {
        short[] buf = new short[frames * 2];
        short[] monoL = new short[frames];
        short[] monoR = new short[frames];
        short[] outR = new short[frames * 2];
        long startNs = System.nanoTime();
        long diagAt = 0;
        int diagInPeak = 0;
        int diagOutPeak = 0;
        int diagWrites = 0;
        try {
            while (running) {
                int n = rec.read(buf, 0, buf.length);
                if (n <= 0) continue;
                int sampleFrames = n / 2;

                double t = (System.nanoTime() - startNs) / 1_000_000_000.0;
                double phase = randomizer.advance(t);

                if (isHrtf(mode)) {
                    // 分离左右输入
                    for (int i = 0; i < sampleFrames; i++) {
                        monoL[i] = buf[i * 2];
                        monoR[i] = buf[i * 2 + 1];
                    }
                    if (mode == MODE_HRTF_STEREO) {
                        // 立体声直接输入：L/R 两个独立声源，起始方位相差 180°，各自绕头后叠加
                        hrtf.processByPhase(monoL, buf, sampleFrames, phase,
                                hrtfAzimuth, hrtfRadius, hrtfElevation);
                        hrtfR.processByPhase(monoR, outR, sampleFrames, phase,
                                hrtfAzimuth + 180f, hrtfRadius, hrtfElevation);
                        for (int i = 0; i < sampleFrames * 2; i++) {
                            int sum = buf[i] + outR[i];
                            buf[i] = clampSample(sum * 0.5f);
                        }
                    } else {
                        // 取左 / 取右 / 合成单声道：一个声源绕头
                        short[] src;
                        if (mode == MODE_HRTF_LEFT) {
                            src = monoL;
                        } else if (mode == MODE_HRTF_RIGHT) {
                            src = monoR;
                        } else { // MODE_HRTF_MIX：L+R 合成单声道
                            for (int i = 0; i < sampleFrames; i++) {
                                monoL[i] = clampSample((monoL[i] + monoR[i]) * 0.5f);
                            }
                            src = monoL;
                        }
                        hrtf.processByPhase(src, buf, sampleFrames, phase,
                                hrtfAzimuth, hrtfRadius, hrtfElevation);
                    }
                    // 可视化：真环绕声用相位表达旋转进度，增益固定 1
                    EffectState.phase = (float) phase;
                    EffectState.tri = (float) phase;
                    EffectState.gainL = 1f;
                    EffectState.gainR = 1f;
                    EffectState.depth = depth;
                    EffectState.period = period;
                    EffectState.ratio = ratio;
                    EffectState.active = true;
                    EffectState.lastUpdateNs = System.nanoTime();
                    track.write(buf, 0, sampleFrames * 2);
                    continue;
                }

                // 与轻量引擎共用同一份算法（相位来自周期随机器）
                float[] g = SurroundMath.gainsFromPhase(depth, ratio, phase);
                float linL = SurroundMath.toLinearCapture(g[0]);
                float linR = SurroundMath.toLinearCapture(g[1]);

                // 更新可视化快照（与音频同帧，比 20ms 轮询更准）
                EffectState.tri = g[2];
                EffectState.phase = g[3];
                EffectState.gainL = linL;
                EffectState.gainR = linR;
                EffectState.depth = depth;
                EffectState.period = period;
                EffectState.ratio = ratio;
                EffectState.active = true;
                EffectState.lastUpdateNs = System.nanoTime();

                // 诊断：捕获进来的峰值（每 16 个采样取一个）
                for (int i = 0; i < n; i += 16) {
                    int a = Math.abs((int) buf[i]);
                    if (a > diagInPeak) diagInPeak = a;
                }
                for (int i = 0; i < sampleFrames; i++) {
                    short l = buf[i * 2];
                    short r = buf[i * 2 + 1];
                    if (mode == MODE_TAKE_LEFT) {
                        r = l;
                    } else if (mode == MODE_TAKE_RIGHT) {
                        l = r;
                    } else if (mode == MODE_TAKE_MIX) {
                        // 双声道合成一个声道后再用伪环绕算法处理
                        l = r = clampSample((l + r) * 0.5f);
                    }
                    buf[i * 2] = clampSample(l * linL);
                    buf[i * 2 + 1] = clampSample(r * linR);
                }
                // 诊断：处理后写出的峰值
                for (int i = 0; i < sampleFrames * 2; i += 16) {
                    int a = Math.abs((int) buf[i]);
                    if (a > diagOutPeak) diagOutPeak = a;
                }
                diagWrites++;
                track.write(buf, 0, sampleFrames * 2);

                long now = System.currentTimeMillis();
                if (now - diagAt >= 2000) {
                    writeFile("capture_peak.txt", "inPeak=" + diagInPeak
                            + " outPeak=" + diagOutPeak
                            + " writes=" + diagWrites
                            + " linL=" + linL + " linR=" + linR
                            + " headPos=" + track.getPlaybackHeadPosition() + "\n");
                    diagAt = now;
                    diagInPeak = 0;
                    diagOutPeak = 0;
                    diagWrites = 0;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "loop failed", t);
            writeFile("capture_peak.txt", "loop FAILED " + t + "\n");
        }
    }

    private static short clampSample(float v) {
        if (v > 32767f) return 32767;
        if (v < -32768f) return -32768;
        return (short) v;
    }
}
