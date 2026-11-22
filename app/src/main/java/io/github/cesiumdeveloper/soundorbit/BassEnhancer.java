package io.github.cesiumdeveloper.soundorbit;

/**
 * 心理声学低音增强（Virtual/Psychoacoustic Bass），基于"缺失基频"现象：
 * 大脑从谐波推断出低音基频，所以小喇叭放不出 50Hz 也能"听出"低音。
 *
 * 权威方案（Waves Renaissance Bass / MaxxBass / AES Bai&Lin 论文 / oximedia 实现一致）：
 *  1. 低通分离低频（默认分频点 80Hz，Waves 建议 60–100Hz）
 *  2. tanh 波形整形产生谐波（drive 控制谐波量）
 *  3. 谐波 = 整形后 - 原低频（只留新生成的失真分量，避免原低频被加两次）
 *  4. 并联混回原信号（strength 控制强度）
 *
 * 注意：对完整混音可能有互调失真，业界建议用于单独轨（kick/bass）。
 * 故此效果默认关闭（strength=0），由用户自行决定。
 */
public final class BassEnhancer {
    public static final float DEFAULT_CROSSOVER = 80f;
    private final OnePoleLP lowpass = new OnePoleLP();
    private float strength = 0f;
    private float crossover = DEFAULT_CROSSOVER;
    private static final int RATE = 48000;

    public BassEnhancer() {
        setCrossover(DEFAULT_CROSSOVER); // 初始化低通系数，否则 a=0 输出恒为 0
    }

    public void setStrength(float strength) {
        if (Float.isNaN(strength) || Float.isInfinite(strength)) strength = 0f;
        this.strength = Math.max(0f, Math.min(1f, strength));
    }

    public float getStrength() { return strength; }

    public void setCrossover(float hz) {
        if (Float.isNaN(hz) || Float.isInfinite(hz)) hz = DEFAULT_CROSSOVER;
        this.crossover = Math.max(40f, Math.min(200f, hz));
        lowpass.setLowpass(crossover, RATE);
    }

    public float getCrossover() { return crossover; }

    /** 就地处理交错立体声；strength=0 时直接返回（零开销）。 */
    public void process(short[] buf, int frames) {
        if (strength <= 0f) return;
        for (int i = 0; i < frames; i++) {
            float l = buf[i * 2] / 32768f;
            float r = buf[i * 2 + 1] / 32768f;
            int lv = (int) (enhance(l) * 32767f);
            int rv = (int) (enhance(r) * 32767f);
            buf[i * 2] = (short) Math.max(-32768, Math.min(32767, lv));
            buf[i * 2 + 1] = (short) Math.max(-32768, Math.min(32767, rv));
        }
    }

    private float enhance(float v) {
        float bass = lowpass.process(v);
        // 强驱动 tanh：把低频"压弯"产生丰富谐波
        float shaped = (float) Math.tanh(bass * 6.0f) * 0.6f;
        // 谐波 = 整形后 - 原低频。基频被抵消，只剩新生成的高频分量。
        float harmonics = shaped - bass;
        return v + harmonics * strength;
    }

    /** 一阶 RC 低通（RBJ 简化），足够做分频。 */
    private static final class OnePoleLP {
        private float a, y1;
        void setLowpass(float fc, int rate) {
            double dt = 1.0 / rate;
            double rc = 1.0 / (2.0 * Math.PI * fc);
            a = (float) (dt / (rc + dt));
        }
        float process(float x) {
            y1 = a * x + (1f - a) * y1;
            return y1;
        }
    }
}
