package com.tanzirdev.shobdo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/** Pill progress bar with gradient fill and a travelling shimmer. Supports indeterminate mode. */
public class GradientBarView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float shown = 0f, shim = 0f;
    private boolean indeterminate = false;
    private ValueAnimator loop, valAnim;

    public GradientBarView(Context c) { super(c); init(); }
    public GradientBarView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init() { track.setColor(0x1FFFFFFF); }

    public void setIndeterminate(boolean b) { indeterminate = b; invalidate(); }

    public void setProgress(int p) {
        if (valAnim != null) valAnim.cancel();
        valAnim = ValueAnimator.ofFloat(shown, Math.max(0, Math.min(100, p)));
        valAnim.setDuration(500); valAnim.setInterpolator(new DecelerateInterpolator());
        valAnim.addUpdateListener(a -> { shown = (float) a.getAnimatedValue(); invalidate(); });
        valAnim.start();
    }

    @Override protected void onMeasure(int ws, int hs) { setMeasuredDimension(resolveSize(Ui.dp(getContext(), 200), ws), Ui.dp(getContext(), 8)); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loop = ValueAnimator.ofFloat(0f, 1f);
        loop.setDuration(1400); loop.setRepeatCount(ValueAnimator.INFINITE); loop.setInterpolator(new LinearInterpolator());
        loop.addUpdateListener(a -> { shim = (float) a.getAnimatedValue(); invalidate(); });
        loop.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (loop != null) loop.cancel();
        if (valAnim != null) valAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), r = h / 2f;
        c.drawRoundRect(0, 0, w, h, r, r, track);
        float x0, x1;
        if (indeterminate) { float seg = w * 0.35f; x0 = (w + seg) * shim - seg; x1 = x0 + seg; }
        else { x0 = 0; x1 = w * shown / 100f; }
        x0 = Math.max(0, x0); x1 = Math.min(w, x1);
        if (x1 - x0 < 1f) return;
        fill.setShader(new LinearGradient(0, 0, w, 0, 0xFF7C4DFF, 0xFFFF5FA2, Shader.TileMode.CLAMP));
        c.drawRoundRect(x0, 0, x1, h, r, r, fill);
        if (!indeterminate) {
            float sx = (x1 - x0) * shim + x0;
            shine.setShader(new LinearGradient(sx - 40, 0, sx + 40, 0, new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.save(); c.clipRect(x0, 0, x1, h); c.drawRoundRect(x0, 0, x1, h, r, r, shine); c.restore();
        }
    }
}
