package com.example.surroundinsert;

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
 * 实时可视化：三角波/弯曲后的包络、左右增益条、声像指针、相位圆环。
 * 只读 EffectState 快照，不参与音频线程。
 */
public class SurroundVisualizerView extends View implements Choreographer.FrameCallback {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint puckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curve = new Path();
    private final RectF ring = new RectF();
    private float[] history = new float[240];
    private int head;

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
            head = (head + 1) % history.length;
            history[head] = EffectState.tri;
        }
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }

        int chartH = (int) (h * 0.55f);
        int ringY = chartH + (h - chartH) / 2;
        int cx = w / 2;

        // 网格
        for (int i = 1; i < 4; i++) {
            float y = chartH * i / 4f;
            c.drawLine(0, y, w, y, gridPaint);
        }
        c.drawLine(cx, 0, cx, chartH, gridPaint);

        // 包络历史曲线
        curve.reset();
        int n = history.length;
        for (int i = 0; i < n; i++) {
            int idx = (head + 1 + i) % n;
            float x = i * w / (float) (n - 1);
            float y = chartH - history[idx] * chartH;
            if (i == 0) {
                curve.moveTo(x, y);
            } else {
                curve.lineTo(x, y);
            }
        }
        c.drawPath(curve, curvePaint);

        // 当前相位圆环
        float radius = Math.min((h - chartH) / 2f - 16f, 70f);
        ring.set(cx - radius, ringY - radius, cx + radius, ringY + radius);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(6f);
        c.drawArc(ring, -90f, 360f, false, gridPaint);
        float sweep = EffectState.active ? EffectState.phase * 360f : 0f;
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeWidth(6f);
        c.drawArc(ring, -90f, sweep, false, barPaint);

        // 声像指针：tri=1 左，tri=0 右
        float panX = cx + (EffectState.tri - 0.5f) * (w - 32f);
        c.drawCircle(panX, ringY, 12f, puckPaint);
        c.drawText("L", 8, ringY + 8, textPaint);
        c.drawText("R", w - 26, ringY + 8, textPaint);

        // 增益数值
        String label = String.format(java.util.Locale.US,
                "L %.2f   R %.2f", EffectState.gainL, EffectState.gainR);
        c.drawText(label, cx - 100, h - 8, textPaint);
    }
}
