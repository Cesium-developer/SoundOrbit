package io.github.cesiumdeveloper.soundorbit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

/**
 * 实时可视化，双模式：
 *  - 伪环绕：包络历史曲线 + 相位圆环 + 声像指针 + L/R 增益（原来的画法）。
 *  - 真环绕(HRTF)：俯视头部图——声源绕头方位、仰角、双声源、运动拖尾。
 * 只读 EffectState 快照，不参与音频线程。
 *
 * 术语：azimuth 方位角(0前/90右/180后/270左)、elevation 仰角(正上负下)、
 * 俯视投影(top-down projection)、拖尾(trail)。
 */
public class SurroundVisualizerView extends View implements Choreographer.FrameCallback {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint puckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint srcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint src2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curve = new Path();
    private final RectF ring = new RectF();
    private float[] history = new float[240];
    private int head;

    // 真环绕声源拖尾（最近 N 帧的方位角，画渐淡的圆点）
    private final float[] trailAz = new float[24];
    private final float[] trailEl = new float[24];
    private int trailHead = 0;
    private int trailCount = 0;

    public SurroundVisualizerView(Context c) {
        super(c);
        init();
    }

    public SurroundVisualizerView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        gridPaint.setColor(Color.parseColor("#E3E8F0"));
        gridPaint.setStrokeWidth(1f);
        curvePaint.setColor(Color.parseColor("#2962FF"));
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(3f);
        fillPaint.setColor(Color.parseColor("#1A2962FF"));
        fillPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.parseColor("#5F6B7A"));
        textPaint.setTextSize(22f);
        puckPaint.setColor(Color.parseColor("#D32F2F"));
        puckPaint.setStyle(Paint.Style.FILL);
        barPaint.setColor(Color.parseColor("#2962FF"));
        headPaint.setColor(Color.parseColor("#37474F"));
        headPaint.setStyle(Paint.Style.FILL);
        srcPaint.setColor(Color.parseColor("#D32F2F"));
        srcPaint.setStyle(Paint.Style.FILL);
        src2Paint.setColor(Color.parseColor("#2962FF"));
        src2Paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (EffectState.active && EffectState.lastUpdateNs > 0) {
            if (EffectState.hrtfMode) {
                trailHead = (trailHead + 1) % trailAz.length;
                trailAz[trailHead] = EffectState.srcAzimuth;
                trailEl[trailHead] = EffectState.srcElevation;
                if (trailCount < trailAz.length) trailCount++;
            } else {
                head = (head + 1) % history.length;
                history[head] = EffectState.tri;
            }
        }
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        if (EffectState.hrtfMode) {
            drawHrtf(c, w, h);
        } else {
            drawPanning(c, w, h);
        }
    }

    /** 伪环绕：原包络曲线 + 相位圆环 + 声像指针 + 增益数值。 */
    private void drawPanning(Canvas c, int w, int h) {
        int chartH = (int) (h * 0.55f);
        int ringY = chartH + (h - chartH) / 2;
        int cx = w / 2;

        for (int i = 1; i < 4; i++) {
            float y = chartH * i / 4f;
            c.drawLine(0, y, w, y, gridPaint);
        }
        c.drawLine(cx, 0, cx, chartH, gridPaint);

        curve.reset();
        int n = history.length;
        for (int i = 0; i < n; i++) {
            int idx = (head + 1 + i) % n;
            float x = i * w / (float) (n - 1);
            float y = chartH - history[idx] * chartH;
            if (i == 0) curve.moveTo(x, y); else curve.lineTo(x, y);
        }
        c.drawPath(curve, curvePaint);

        float radius = Math.min((h - chartH) / 2f - 16f, 70f);
        ring.set(cx - radius, ringY - radius, cx + radius, ringY + radius);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(6f);
        c.drawArc(ring, -90f, 360f, false, gridPaint);
        float sweep = EffectState.active ? EffectState.phase * 360f : 0f;
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeWidth(6f);
        c.drawArc(ring, -90f, sweep, false, barPaint);

        float panX = cx + (EffectState.tri - 0.5f) * (w - 32f);
        c.drawCircle(panX, ringY, 12f, puckPaint);
        c.drawText("L", 8, ringY + 8, textPaint);
        c.drawText("R", w - 26, ringY + 8, textPaint);

        String label = String.format(java.util.Locale.US,
                "L %.2f   R %.2f", EffectState.gainL, EffectState.gainR);
        c.drawText(label, cx - 100, h - 8, textPaint);
    }

    /**
     * 真环绕：俯视头部图。圆心=听者头部，上方=正前(0°)，右=90°，下=180°，左=270°。
     * 声源点按方位角映射到圆周；仰角用点的大小表达；双声源画两个点。
     */
    private void drawHrtf(Canvas c, int w, int h) {
        int cx = w / 2;
        int cy = h / 2;
        float radius = Math.min(w, h) / 2f - 30f;
        if (radius < 20f) return;

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
        c.drawCircle(cx, cy, radius, gridPaint);
        gridPaint.setStrokeWidth(1f);
        c.drawCircle(cx, cy, radius * 0.42f, gridPaint);
        c.drawLine(cx, cy - radius, cx, cy + radius, gridPaint);
        c.drawLine(cx - radius, cy, cx + radius, cy, gridPaint);

        c.drawCircle(cx, cy, radius * 0.28f, headPaint);
        c.drawCircle(cx, cy - radius * 0.30f, 5f, headPaint); // 鼻尖朝上=正前
        c.drawText("前", cx - 11, cy - radius - 8, textPaint);

        if (trailCount > 0) {
            for (int k = 0; k < trailCount; k++) {
                int idx = (trailHead - k + trailAz.length) % trailAz.length;
                float age = 1f - k / (float) trailAz.length;
                srcPaint.setAlpha((int) (120 * age));
                float dotR = dotRadius(trailEl[idx], radius) * (0.4f + 0.6f * age);
                drawSource(c, cx, cy, radius, trailAz[idx], dotR, srcPaint);
            }
            srcPaint.setAlpha(255);
        }

        float az = EffectState.srcAzimuth;
        float el = EffectState.srcElevation;
        float dotR = dotRadius(el, radius);
        drawSource(c, cx, cy, radius, az, dotR, srcPaint);
        if (EffectState.dualSource) {
            drawSource(c, cx, cy, radius, az + 180f, dotR, src2Paint);
        }

        float norm = ((az % 360f) + 360f) % 360f;
        String label = String.format(java.util.Locale.US,
                "方位 %3.0f°   仰角 %+3.0f°", norm, el);
        c.drawText(label, 12, h - 12, textPaint);
    }

    /** 方位角映射到圆周：0°上、90°右、180°下、270°左。 */
    private void drawSource(Canvas c, int cx, int cy, float radius,
                            float azimuth, float dotR, Paint paint) {
        double rad = Math.toRadians(azimuth);
        float x = cx + (float) Math.sin(rad) * radius;
        float y = cy - (float) Math.cos(rad) * radius;
        c.drawCircle(x, y, dotR, paint);
    }

    /** 仰角越高点越大，给二维俯视图一个仰角维度。 */
    private float dotRadius(float elevation, float orbitRadius) {
        float t = (Math.max(-60f, Math.min(60f, elevation)) + 60f) / 120f;
        return Math.max(5f, orbitRadius * (0.05f + 0.07f * t));
    }
}
