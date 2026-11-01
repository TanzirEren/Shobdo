package com.tanzirdev.shobdo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/** Big animated progress ring: gradient sweep, glowing head, orbiting dots, counting percent. */
public class ProgressRingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG),
            dash = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG), sub = new Paint(Paint.ANTI_ALIAS_FLAG),
            dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float shown = 0f, spin = 0f;
    private String label = "";
    private ValueAnimator spinAnim, valAnim;

    public ProgressRingView(Context c) { super(c); init(); }
    public ProgressRingView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        track.setStyle(Paint.Style.STROKE); track.setColor(0x1AFFFFFF);
        arc.setStyle(Paint.Style.STROKE); arc.setStrokeCap(Paint.Cap.ROUND);
        dash.setStyle(Paint.Style.STROKE); dash.setColor(0x33B89CFF);
        txt.setColor(0xFFFFFFFF); txt.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)); txt.setTextAlign(Paint.Align.CENTER);
        sub.setColor(Ui.SUB); sub.setTextAlign(Paint.Align.CENTER); sub.setTypeface(Ui.medium());
    }

    public void setPercent(int p) {
        float to = Math.max(0, Math.min(100, p));
        if (valAnim != null) valAnim.cancel();
        valAnim = ValueAnimator.ofFloat(shown, to);
        valAnim.setDuration(600); valAnim.setInterpolator(new DecelerateInterpolator());
        valAnim.addUpdateListener(a -> { shown = (float) a.getAnimatedValue(); invalidate(); });
        valAnim.start();
    }

    public void setLabel(String s) { label = s; invalidate(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        spinAnim = ValueAnimator.ofFloat(0f, 360f);
        spinAnim.setDuration(3600); spinAnim.setRepeatCount(ValueAnimator.INFINITE); spinAnim.setInterpolator(new LinearInterpolator());
        spinAnim.addUpdateListener(a -> { spin = (float) a.getAnimatedValue(); invalidate(); });
        spinAnim.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (spinAnim != null) spinAnim.cancel();
        if (valAnim != null) valAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), d = Math.min(w, h), cx = w / 2f, cy = h / 2f;
        float sw = d * 0.065f, r = d / 2f - sw * 1.6f;
        glow.setShader(new RadialGradient(cx, cy, d * 0.6f, 0x338B5CFF, 0x008B5CFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, d * 0.6f, glow);

        track.setStrokeWidth(sw);
        c.drawCircle(cx, cy, r, track);

        // rotating dashed inner ring
        dash.setStrokeWidth(Math.max(1.5f, sw * 0.18f));
        dash.setPathEffect(new DashPathEffect(new float[]{d * 0.012f, d * 0.03f}, 0));
        c.save(); c.rotate(spin, cx, cy);
        c.drawCircle(cx, cy, r - sw * 1.5f, dash);
        c.restore();

        // gradient progress arc
        float sweep = 360f * shown / 100f;
        arc.setStrokeWidth(sw);
        c.save(); c.rotate(-90f, cx, cy);
        arc.setShader(new SweepGradient(cx, cy, new int[]{0xFF7C4DFF, 0xFFFF5FA2, 0xFF7C4DFF}, new float[]{0f, 0.75f, 1f}));
        RectF rect = new RectF(cx - r, cy - r, cx + r, cy + r);
        if (sweep > 0.5f) c.drawArc(rect, 0, sweep, false, arc);
        // glowing head
        double ang = Math.toRadians(sweep);
        float hx = cx + (float) Math.cos(ang) * r, hy = cy + (float) Math.sin(ang) * r;
        dot.setShader(new RadialGradient(hx, hy, sw * 1.8f, 0xFFFFFFFF, 0x00FF5FA2, Shader.TileMode.CLAMP));
        if (sweep > 0.5f) c.drawCircle(hx, hy, sw * 1.8f, dot);
        c.restore();

        // orbiting sparks
        dot.setShader(null);
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(spin * 1.6f + i * 120f);
            float ox = cx + (float) Math.cos(a) * (r + sw * 1.1f), oy = cy + (float) Math.sin(a) * (r + sw * 1.1f);
            dot.setColor(i == 0 ? 0xCCFF5FA2 : 0x88B89CFF);
            c.drawCircle(ox, oy, sw * (i == 0 ? 0.32f : 0.22f), dot);
        }

        // centre text
        String num = String.valueOf(Math.round(shown));
        txt.setTextSize(d * 0.26f);
        float nw = txt.measureText(num);
        c.drawText(num, cx - d * 0.03f, cy + d * 0.07f, txt);
        Paint pc = new Paint(Paint.ANTI_ALIAS_FLAG); pc.setColor(Ui.VIOLET2); pc.setTextSize(d * 0.10f); pc.setTypeface(Ui.medium());
        c.drawText("%", cx - d * 0.03f + nw / 2f + d * 0.01f, cy + d * 0.07f - d * 0.10f, pc);
        sub.setTextSize(d * 0.06f);
        c.drawText(label, cx, cy + d * 0.20f, sub);
    }
}
