package io.github.cesiumdeveloper.soundorbit;

import android.media.audiofx.DynamicsProcessing;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * 直接在系统输出混音链（session 0）上按时间改左右声道增益。
 * 不抓音频、不重播，因此没有“原声 + 副本”叠加造成的重音。
 */
public class GlobalEffectEngine {
    private static final String TAG = "GlobalEffect";
    private static final int CHANNELS = 2;

    private DynamicsProcessing effect;
    private HandlerThread thread;
    private Handler handler;
    private long startNs;
    private volatile boolean running;
    private volatile float depth;
    private volatile float period;
    private volatile float ratio = 1f;
    private final PeriodRandomizer randomizer = new PeriodRandomizer();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running || effect == null) {
                return;
            }
            double t = (System.nanoTime() - startNs) / 1_000_000_000.0;
            double phase = randomizer.advance(t);
            float[] g = SurroundMath.gainsFromPhase(depth, ratio, phase);
            float gL = g[0];
            float gR = g[1];

            EffectState.tri = g[2];
            EffectState.phase = g[3];
            EffectState.gainL = gL;
            EffectState.gainR = gR;
            EffectState.depth = depth;
            EffectState.period = period;
            EffectState.ratio = ratio;
            EffectState.active = running;
            EffectState.lastUpdateNs = System.nanoTime();

            setBandGain(0, gL);
            setBandGain(1, gR);

            handler.postDelayed(this, 20);
        }
    };

    public synchronized void start(float uiDepth, float uiPeriod, float uiRatio) {
        start(uiDepth, uiPeriod, uiRatio, false, 0f, 0f);
    }

    public synchronized void start(float uiDepth, float uiPeriod, float uiRatio,
                                    boolean randEnabled, float randMin, float randMax) {
        if (running) {
            setAll(uiDepth, uiPeriod, uiRatio, randEnabled, randMin, randMax);
            return;
        }
        depth = uiDepth;
        period = uiPeriod;
        ratio = uiRatio;
        randomizer.configure(randEnabled, period, randMin, randMax);
        try {
            DynamicsProcessing.Channel channel = new DynamicsProcessing.Channel(
                    1f, false, 0, false, 0, false, 0, false);
            DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    CHANNELS, false, 0, false, 0, false, 0, false);
            builder.setAllChannelsTo(channel);

            effect = new DynamicsProcessing(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 0, builder.build());
            effect.setEnabled(true);

            thread = new HandlerThread("surround-global-effect");
            thread.start();
            handler = new Handler(thread.getLooper());
            startNs = System.nanoTime();
            running = true;
            handler.post(tick);
            Log.i(TAG, "started on session 0, depth=" + depth + " period=" + period + " ratio=" + ratio);
        } catch (Throwable t) {
            Log.e(TAG, "start failed", t);
            releaseQuietly();
            running = false;
            throw new RuntimeException("GlobalEffect start failed: " + t.getMessage(), t);
        }
    }

    private int tickCount;

    private void setBandGain(int channelIndex, float gain) {
        float magnitude = Math.abs(gain);
        float db = magnitude <= 0f ? -96f : (float) (20.0 * Math.log10(magnitude));
        if (db < -96f) {
            db = -96f;
        }
        if (db > 12f) {
            db = 12f;
        }
        effect.setInputGainbyChannel(channelIndex, db);
        tickCount++;
        if (tickCount % 20 == 1) {
            Log.i(TAG, "gain ch=" + channelIndex + " linear=" + gain + " db=" + db
                    + " readback=" + effect.getInputGainByChannelIndex(channelIndex));
        }
    }

    public synchronized void setParams(float uiDepth, float uiPeriod, float uiRatio) {
        setAll(uiDepth, uiPeriod, uiRatio,
                randomizer.isEnabled(), randomizer.getMin(), randomizer.getMax());
    }

    /** 一次性设置基础参数 + 周期随机配置（更新时也保持随机设置） */
    public synchronized void setAll(float uiDepth, float uiPeriod, float uiRatio,
                                     boolean randEnabled, float randMin, float randMax) {
        depth = uiDepth;
        period = uiPeriod;
        ratio = uiRatio;
        randomizer.configure(randEnabled, period, randMin, randMax);
    }

    public synchronized void stop() {
        running = false;
        if (handler != null) {
            handler.removeCallbacks(tick);
        }
        releaseQuietly();
        Log.i(TAG, "stopped");
    }

    private void releaseQuietly() {
        try {
            if (effect != null) {
                effect.setEnabled(false);
                effect.release();
            }
        } catch (Throwable ignored) {
        }
        effect = null;
        if (thread != null) {
            thread.quit();
            thread = null;
        }
        handler = null;
    }

    public boolean isRunning() {
        return running;
    }
}
