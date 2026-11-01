package com.tanzirdev.shobdo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Floating glass pill (max radius) with Home · Create(+) · Settings. */
public class BottomNavView extends View {
    public interface Listener { void onTab(int index); }   // 0 home, 1 create, 2 settings
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), ic = new Paint(Paint.ANTI_ALIAS_FLAG),
            glow = new Paint(Paint.ANTI_ALIAS_FLAG), hi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int selected = 0;
    private float indicatorX = -1f, plusScale = 1f;
    private Listener listener;
    private ValueAnimator moveAnim, plusAnim;

    public BottomNavView(Context c) { super(c); init(); }
    public BottomNavView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        ic.setStyle(Paint.Style.STROKE); ic.setStrokeCap(Paint.Cap.ROUND); ic.setStrokeJoin(Paint.Join.ROUND);
        line.setStyle(Paint.Style.STROKE); line.setColor(0x38FFFFFF);
        setClickable(true);
    }

    public void setListener(Listener l) { listener = l; }

    public void select(int i) {
        if (i == selected) return;
        selected = i;
        animateIndicator(centerX(i));
        invalidate();
    }

    private float centerX(int i) { return getWidth() * (2 * i + 1) / 6f; }

    private void animateIndicator(float to) {
        if (moveAnim != null) moveAnim.cancel();
        moveAnim = ValueAnimator.ofFloat(indicatorX, to);
        moveAnim.setDuration(380); moveAnim.setInterpolator(new OvershootInterpolator(0.9f));
        moveAnim.addUpdateListener(a -> { indicatorX = (float) a.getAnimatedValue(); invalidate(); });
        moveAnim.start();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (indicatorX < 0) indicatorX = centerX(selected);
        fill.setShader(new LinearGradient(0, 0, w, h, 0x40FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
        line.setStrokeWidth(Ui.dp(getContext(), 0.9f));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) { return true; }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int i = (int) (e.getX() / (getWidth() / 3f));
            i = Math.max(0, Math.min(2, i));
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (i == 1) pulsePlus();
            if (listener != null) listener.onTab(i);
            return true;
        }
        return super.onTouchEvent(e);
    }

    private void pulsePlus() {
        if (plusAnim != null) plusAnim.cancel();
        plusAnim = ValueAnimator.ofFloat(0.82f, 1f);
        plusAnim.setDuration(420); plusAnim.setInterpolator(new OvershootInterpolator(2.2f));
        plusAnim.addUpdateListener(a -> { plusScale = (float) a.getAnimatedValue(); invalidate(); });
        plusAnim.start();
    }

    @Override protected void onDraw(Canvas c) {
        Context ctx = getContext();
        float w = getWidth(), h = getHeight(), r = h / 2f;     // max radius
        RectF pill = new RectF(1, 1, w - 1, h - 1);
        c.drawRoundRect(pill, r, r, fill);
        c.drawRoundRect(pill, r, r, line);

        // moving highlight under the active side tab
        if (selected != 1) {
            float hw = Ui.dp(ctx, 54), hh = Ui.dp(ctx, 40);
            hi.setShader(new LinearGradient(indicatorX - hw / 2, 0, indicatorX + hw / 2, 0, 0x558B5CFF, 0x33FF5FA2, Shader.TileMode.CLAMP));
            c.drawRoundRect(indicatorX - hw / 2, h / 2f - hh / 2, indicatorX + hw / 2, h / 2f + hh / 2, hh / 2, hh / 2, hi);
        }

        drawHome(c, centerX(0), h / 2f, selected == 0);
        drawSettings(c, centerX(2), h / 2f, selected == 2);

        // centre create button
        float pr = Ui.dp(ctx, 27) * plusScale, cx = centerX(1), cy = h / 2f;
        glow.setShader(new RadialGradient(cx, cy, pr * 1.9f, 0x668B5CFF, 0x008B5CFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, pr * 1.9f, glow);
        fill.setShader(null);
        Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
        b.setShader(new LinearGradient(cx - pr, cy - pr, cx + pr, cy + pr, 0xFF7C4DFF, 0xFFFF5FA2, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, pr, b);
        ic.setColor(0xFFFFFFFF); ic.setStrokeWidth(Ui.dp(ctx, 2.6f));
        float k = pr * 0.42f;
        c.drawLine(cx - k, cy, cx + k, cy, ic);
        c.drawLine(cx, cy - k, cx, cy + k, ic);
        fill.setShader(new LinearGradient(0, 0, w, h, 0x40FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
    }

    private void drawHome(Canvas c, float cx, float cy, boolean on) {
        float s = Ui.dp(getContext(), 1f);
        ic.setColor(on ? 0xFFFFFFFF : Ui.SUB); ic.setStrokeWidth(2f * s);
        Path p = new Path();
        p.moveTo(cx - 10 * s, cy - 1 * s); p.lineTo(cx, cy - 10 * s); p.lineTo(cx + 10 * s, cy - 1 * s);
        c.drawPath(p, ic);
        Path q = new Path();
        q.moveTo(cx - 7 * s, cy - 3.5f * s); q.lineTo(cx - 7 * s, cy + 9 * s); q.lineTo(cx + 7 * s, cy + 9 * s); q.lineTo(cx + 7 * s, cy - 3.5f * s);
        c.drawPath(q, ic);
        Path d = new Path();
        d.moveTo(cx - 2.5f * s, cy + 9 * s); d.lineTo(cx - 2.5f * s, cy + 3 * s); d.lineTo(cx + 2.5f * s, cy + 3 * s); d.lineTo(cx + 2.5f * s, cy + 9 * s);
        c.drawPath(d, ic);
    }

    private void drawSettings(Canvas c, float cx, float cy, boolean on) {
        float s = Ui.dp(getContext(), 1f);
        int col = on ? 0xFFFFFFFF : Ui.SUB;
        ic.setColor(col); ic.setStrokeWidth(2f * s);
        float[] ys = {-7 * s, 0, 7 * s}, ks = {-4 * s, 5 * s, -1 * s};
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG); dot.setColor(0xFF150C2E);
        for (int i = 0; i < 3; i++) {
            c.drawLine(cx - 10 * s, cy + ys[i], cx + 10 * s, cy + ys[i], ic);
            c.drawCircle(cx + ks[i], cy + ys[i], 3.2f * s, dot);
            c.drawCircle(cx + ks[i], cy + ys[i], 3.2f * s, ic);
        }
    }
}
