package com.tanzirdev.shobdo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** "shobd" + an animated sound-ring "o" — the Shobdo wordmark. */
public class WordmarkView extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG), bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase = 0f;
    private ValueAnimator anim;

    public WordmarkView(Context c, AttributeSet a) {
        super(c, a);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setColor(0xFF1C1B2E);
        text.setLetterSpacing(-0.03f);
        ring.setStyle(Paint.Style.STROKE);
        bar.setStyle(Paint.Style.STROKE); bar.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int h = MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED ? Ui.dp(getContext(), 52) : MeasureSpec.getSize(hSpec);
        text.setTextSize(h * 0.78f);
        float w = text.measureText("shobd") + h * 0.62f + h * 0.1f;
        setMeasuredDimension(resolveSize((int) Math.ceil(w), wSpec), h);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        anim.setDuration(1700); anim.setRepeatCount(ValueAnimator.INFINITE); anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c) {
        float h = getHeight();
        float base = h * 0.76f;
        c.drawText("shobd", 0, base, text);
        float x = text.measureText("shobd") + h * 0.05f;
        float d = h * 0.50f;                         // ring diameter ~ x-height of the font
        float cx = x + d / 2f + h * 0.04f, cy = base - d / 2f - h * 0.02f;
        float sw = h * 0.085f;
        ring.setStrokeWidth(sw);
        ring.setShader(new LinearGradient(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2, 0xFF6B4EFF, 0xFFFF7A59, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, d / 2f - sw / 2f, ring);
        bar.setStrokeWidth(sw * 0.62f);
        bar.setShader(ring.getShader());
        float inner = d * 0.46f;
        for (int i = -1; i <= 1; i++) {
            float k = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(phase + i * 1.4f));
            float bh = inner * k;
            float bx = cx + i * sw * 1.05f;
            c.drawLine(bx, cy - bh / 2, bx, cy + bh / 2, bar);
        }
    }
}
