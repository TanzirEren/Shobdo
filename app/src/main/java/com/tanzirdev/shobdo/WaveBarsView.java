package com.tanzirdev.shobdo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Dancing equaliser bars. level 0..1 controls energy. */
public class WaveBarsView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase = 0f, level = 0.6f;
    private int bars = 28;
    private ValueAnimator anim;

    public WaveBarsView(Context c) { super(c); init(); }
    public WaveBarsView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init() { p.setStrokeCap(Paint.Cap.ROUND); }

    public void setLevel(float l) { level = Math.max(0.1f, Math.min(1f, l)); }
    public void setBars(int n) { bars = n; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        p.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xFF7C4DFF, 0xFFB06BFF, 0xFFFF5FA2}, null, Shader.TileMode.CLAMP));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        anim.setDuration(1500); anim.setRepeatCount(ValueAnimator.INFINITE); anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), step = w / bars, bw = step * 0.46f;
        p.setStrokeWidth(bw);
        for (int i = 0; i < bars; i++) {
            float e = (float) (0.5 + 0.5 * Math.sin(phase * 2 + i * 0.55)) * (float) (0.55 + 0.45 * Math.sin(phase * 3 + i * 1.3));
            float bh = Math.max(bw, h * (0.12f + 0.88f * e * level));
            float x = step * i + step / 2f;
            c.drawLine(x, h / 2f - bh / 2f, x, h / 2f + bh / 2f, p);
        }
    }
}
