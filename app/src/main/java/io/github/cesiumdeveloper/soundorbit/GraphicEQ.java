package io.github.cesiumdeveloper.soundorbit;

/**
 * 10 段图示均衡器（Graphic EQ）。
 *
 * 中心频率采用 ISO 266 标准倍频程优选频率：
 *   31.5, 63, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz
 * 每段是 peaking biquad（RBJ Audio EQ Cookbook），Q=1.41（一个倍频程）。
 * 默认全 0 dB = 单位增益，信号原样通过（听感不变）。
 *
 * 术语：
 *  - peaking 滤波器：只在中心频率附近提升/衰减，其余频率不动。
 *  - biquad：二阶 IIR 滤波器，5 个系数 (b0,b1,b2,a1,a2)，逐采样递推。
 *  - dB：增益单位，+6dB ≈ 音量翻倍，-12dB ≈ 降到 1/4。
 */
public final class GraphicEQ {
    public static final int BANDS = 10;
    public static final float[] CENTERS = {
            31.5f, 63f, 125f, 250f, 500f,
            1000f, 2000f, 4000f, 8000f, 16000f
    };
    public static final float Q = 1.41f;
    public static final float MIN_DB = -12f;
    public static final float MAX_DB = 12f;
    private static final int RATE = 48000;

    // 每段的 5 个系数 + 左右声道各自的历史状态（Direct Form I）
    private final float[] b0 = new float[BANDS], b1 = new float[BANDS], b2 = new float[BANDS];
    private final float[] a1 = new float[BANDS], a2 = new float[BANDS];
    private final float[][] x1L = new float[BANDS][], x2L = new float[BANDS][];
    private final float[][] y1L = new float[BANDS][], y2L = new float[BANDS][];
    private final float[][] x1R = new float[BANDS][], x2R = new float[BANDS][];
    private final float[][] y1R = new float[BANDS][], y2R = new float[BANDS][];
    private final float[] gainsDb = new float[BANDS]; // 全 0 = 不处理
    private SoftLimiter limiter; // 可选：输出兜底软限幅，防止 EQ 提升后削波

    public GraphicEQ() {
        for (int i = 0; i < BANDS; i++) {
            x1L[i] = new float[1]; x2L[i] = new float[1];
            y1L[i] = new float[1]; y2L[i] = new float[1];
            x1R[i] = new float[1]; x2R[i] = new float[1];
            y1R[i] = new float[1]; y2R[i] = new float[1];
        }
        recomputeAll();
    }

    /** 绑定输出软限幅（EQ 提升后可能超过 ±1，由它兜底）。 */
    public void setLimiter(SoftLimiter limiter) {
        this.limiter = limiter;
    }

    /** 设置某段增益(dB)，越界钳制；NaN 回 0。 */
    public void setBand(int i, float db) {
        if (i < 0 || i >= BANDS) return;
        if (Float.isNaN(db) || Float.isInfinite(db)) db = 0f;
        gainsDb[i] = Math.max(MIN_DB, Math.min(MAX_DB, db));
        recompute(i);
    }

    public void setBands(float[] db) {
        if (db == null || db.length != BANDS) return;
        for (int i = 0; i < BANDS; i++) setBand(i, db[i]);
    }

    public float getBand(int i) {
        return (i >= 0 && i < BANDS) ? gainsDb[i] : 0f;
    }

    public float[] getBands() {
        return gainsDb.clone();
    }

    public boolean isNeutral() {
        for (float g : gainsDb) if (g != 0f) return false;
        return true;
    }

    public void reset() {
        for (int i = 0; i < BANDS; i++) gainsDb[i] = 0f;
        recomputeAll();
        clearState();
    }

    public void clearState() {
        for (int i = 0; i < BANDS; i++) {
            x1L[i][0] = x2L[i][0] = y1L[i][0] = y2L[i][0] = 0f;
            x1R[i][0] = x2R[i][0] = y1R[i][0] = y2R[i][0] = 0f;
        }
    }

    /** RBJ peaking EQ 系数。A=10^(dB/40)，w0=2πf0/fs，alpha=sin(w0)/(2Q)。 */
    private void recompute(int i) {
        float db = gainsDb[i];
        double A = Math.pow(10.0, db / 40.0);
        double w0 = 2.0 * Math.PI * CENTERS[i] / RATE;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2.0 * Q);
        // RBJ peaking: a0 = 1 + alpha/A（注意是除 A），a2 = 1 - alpha/A。
        // 若误写成 alpha*A，b 与 a 系数会完全相同 -> 全通 H(z)=1，EQ 失效。
        double a0 = 1.0 + alpha / A;
        b0[i] = (float) ((1.0 + alpha * A) / a0);
        b1[i] = (float) (-2.0 * cos / a0);
        b2[i] = (float) ((1.0 - alpha * A) / a0);
        a1[i] = (float) (-2.0 * cos / a0);
        a2[i] = (float) ((1.0 - alpha / A) / a0);
    }

    private void recomputeAll() {
        for (int i = 0; i < BANDS; i++) recompute(i);
    }

    /** 处理一个交错立体声帧（in/out 长度 = frames*2）。 */
    public void process(short[] in, short[] out, int frames) {
        if (isNeutral()) {
            // 全 0dB：直接拷贝，一个采样都不改（保护正常听感 + 省 CPU）
            if (in != out) System.arraycopy(in, 0, out, 0, frames * 2);
            return;
        }
        for (int s = 0; s < frames; s++) {
            float l = in[s * 2] / 32768f;
            float r = in[s * 2 + 1] / 32768f;
            for (int i = 0; i < BANDS; i++) {
                l = processBand(i, l, x1L[i], x2L[i], y1L[i], y2L[i]);
                r = processBand(i, r, x1R[i], x2R[i], y1R[i], y2R[i]);
            }
            if (limiter != null) {
                out[s * 2] = limiter.limitToShort(l);
                out[s * 2 + 1] = limiter.limitToShort(r);
            } else {
                int lv = (int) (l * 32767f), rv = (int) (r * 32767f);
                out[s * 2] = (short) Math.max(-32768, Math.min(32767, lv));
                out[s * 2 + 1] = (short) Math.max(-32768, Math.min(32767, rv));
            }
        }
    }

    private float processBand(int i, float x, float[] x1, float[] x2, float[] y1, float[] y2) {
        float y = b0[i] * x + b1[i] * x1[0] + b2[i] * x2[0] - a1[i] * y1[0] - a2[i] * y2[0];
        x2[0] = x1[0]; x1[0] = x;
        y2[0] = y1[0]; y1[0] = y;
        return y;
    }
}
