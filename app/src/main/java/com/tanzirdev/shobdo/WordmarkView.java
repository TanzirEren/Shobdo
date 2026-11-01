package com.tanzirdev.shobdo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/** "shobd" + an animated sound-ring "o" — the Shobdo wordmark. Call playIntro() for the splash reveal. */
public class WordmarkView extends View {
    private static final String WORD = "shobd";
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG), bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase = 0f, intro = 1f;
    private ValueAnimator loop, introAnim;

    public WordmarkView(Context c) { super(c); init(); }
    public WordmarkView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setColor(0xFFF5F2FF);
        text.setLetterSpacing(-0.03f);
        ring.setStyle(Paint.Style.STROKE); ring.setStrokeCap(Paint.Cap.ROUND);
        bar.setStyle(Paint.Style.STROKE); bar.setStrokeCap(Paint.Cap.ROUND);
    }

    public void playIntro() {
        intro = 0f;
        if (introAnim != null) introAnim.cancel();
        introAnim = ValueAnimator.ofFloat(0f, 1f);
        introAnim.setDuration(1300); introAnim.setInterpolator(new DecelerateInterpolator(1.4f));
        introAnim.addUpdateListener(a -> { intro = (float) a.getAnimatedValue(); invalidate(); });
        introAnim.start();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int h = MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED ? Ui.dp(getContext(), 52) : MeasureSpec.getSize(hSpec);
        text.setTextSize(h * 0.78f);
        float w = text.measureText(WORD) + h * 0.62f + h * 0.1f;
        setMeasuredDimension(resolveSize((int) Math.ceil(w), wSpec), h);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loop = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        loop.setDuration(1700); loop.setRepeatCount(ValueAnimator.INFINITE); loop.setInterpolator(new LinearInterpolator());
        loop.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
        loop.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (loop != null) loop.cancel();
        if (introAnim != null) introAnim.cancel();
        super.onDetachedFromWindow();
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    @Override protected void onDraw(Canvas c) {
        float h = getHeight(), base = h * 0.76f;
        for (int i = 0; i < WORD.length(); i++) {
            float a = clamp(intro * 6f - i * 0.7f);
            float x = text.measureText(WORD.substring(0, i));
            text.setAlpha((int) (255 * a));
            c.drawText(WORD.substring(i, i + 1), x, base + (1 - a) * h * 0.25f, text);
        }
        float x = text.measureText(WORD) + h * 0.05f;
        float d = h * 0.50f, cx = x + d / 2f + h * 0.04f, cy = base - d / 2f - h * 0.02f;
        float sw = h * 0.085f;
        float rp = clamp((intro - 0.35f) / 0.65f);
        float sc = rp < 1f ? (float) (1 + 2.2 * Math.pow(rp - 1, 3) + 1.2 * Math.pow(rp - 1, 2)) : 1f;   // overshoot ease
        c.save(); c.translate(cx, cy); c.scale(sc, sc); c.translate(-cx, -cy);
        Shader sh = new LinearGradient(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2, 0xFF8B5CFF, 0xFFFF5FA2, Shader.TileMode.CLAMP);
        ring.setShader(sh); ring.setStrokeWidth(sw);
        RectF rect = new RectF(cx - d / 2f + sw / 2f, cy - d / 2f + sw / 2f, cx + d / 2f - sw / 2f, cy + d / 2f - sw / 2f);
        c.drawArc(rect, -90, 360f * clamp((intro - 0.25f) / 0.5f), false, ring);
        bar.setShader(sh); bar.setStrokeWidth(sw * 0.62f);
        float inner = d * 0.46f, bars = clamp((intro - 0.6f) / 0.4f);
        for (int i = -1; i <= 1; i++) {
            float k = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(phase + i * 1.4f));
            float bh = inner * k * bars, bx = cx + i * sw * 1.05f;
            c.drawLine(bx, cy - bh / 2, bx, cy + bh / 2, bar);
        }
        c.restore();
    }
}
