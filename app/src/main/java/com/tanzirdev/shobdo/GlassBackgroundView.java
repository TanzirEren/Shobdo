package com.tanzirdev.shobdo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Deep purple backdrop with slowly drifting colour orbs — the "glass" sits on top of this. */
public class GlassBackgroundView extends View {
    private final Paint bg = new Paint(), orb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shader[] shaders = new Shader[3];
    private final float[] radius = new float[3];
    private float t = 0f;
    private ValueAnimator anim;

    public GlassBackgroundView(Context c) { super(c); }
    public GlassBackgroundView(Context c, AttributeSet a) { super(c, a); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF0B0816, 0xFF150C2E, 0xFF0B0816}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        int[] cols = {0xFF7C4DFF, 0xFFFF4F9A, 0xFF3F6BFF};
        float[] f = {0.75f, 0.60f, 0.65f};
        float[] a = {0.55f, 0.38f, 0.32f};
        for (int i = 0; i < 3; i++) {
            radius[i] = Math.max(w, h) * f[i] * 0.62f;
            int c0 = (cols[i] & 0x00FFFFFF) | ((int) (255 * a[i]) << 24);
            shaders[i] = new RadialGradient(0, 0, radius[i], c0, cols[i] & 0x00FFFFFF, Shader.TileMode.CLAMP);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        anim.setDuration(26000); anim.setRepeatCount(ValueAnimator.INFINITE); anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, bg);
        float[][] pos = {
            {w * (0.15f + 0.10f * (float) Math.sin(t)), h * (0.10f + 0.06f * (float) Math.cos(t))},
            {w * (0.95f + 0.08f * (float) Math.cos(t * 1.0f)), h * (0.45f + 0.08f * (float) Math.sin(t))},
            {w * (0.30f + 0.12f * (float) Math.sin(t + 2f)), h * (0.98f + 0.04f * (float) Math.cos(t + 1f))}
        };
        for (int i = 0; i < 3; i++) {
            if (shaders[i] == null) continue;
            c.save();
            c.translate(pos[i][0], pos[i][1]);
            orb.setShader(shaders[i]);
            c.drawCircle(0, 0, radius[i], orb);
            c.restore();
        }
    }
}
