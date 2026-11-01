package com.tanzirdev.shobdo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Vertical step tracker: done = check, active = pulsing ring, pending = hollow dot. */
public class StepsView extends View {
    private final String[] titles = {"Extract audio", "Detect language", "Listen & write", "Polish captions"};
    private final String[] notes = {"", "", "", ""};
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG), n = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int cur = 0;
    private float pulse = 0f;
    private ValueAnimator anim;

    public StepsView(Context c) { super(c); init(); }
    public StepsView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init() { t.setTypeface(Ui.medium()); n.setColor(Ui.SUB); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); }

    public void setStep(int i, String note) { cur = i; notes[Math.max(0, Math.min(3, i))] = note == null ? "" : note; invalidate(); }

    @Override protected void onMeasure(int ws, int hs) {
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 300), ws), Ui.dp(getContext(), 44) * 4);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1200); anim.setRepeatCount(ValueAnimator.INFINITE); anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { pulse = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c) {
        Context ctx = getContext();
        float row = Ui.dp(ctx, 44), x = Ui.dp(ctx, 16), rad = Ui.dp(ctx, 9);
        t.setTextSize(Ui.dp(ctx, 15)); n.setTextSize(Ui.dp(ctx, 12));
        for (int i = 0; i < 4; i++) {
            float cy = row * i + row / 2f;
            if (i < 3) {                       // connector
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Ui.dp(ctx, 2));
                p.setColor(i < cur ? 0xFF8B5CFF : 0x22FFFFFF);
                c.drawLine(x, cy + rad + 3, x, cy + row - rad - 3, p);
            }
            if (i < cur) {                     // done
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(x - rad, cy - rad, x + rad, cy + rad, 0xFF7C4DFF, 0xFFFF5FA2, Shader.TileMode.CLAMP));
                c.drawCircle(x, cy, rad, p); p.setShader(null);
                p.setStyle(Paint.Style.STROKE); p.setColor(0xFFFFFFFF); p.setStrokeWidth(Ui.dp(ctx, 2));
                Path ck = new Path(); ck.moveTo(x - rad * 0.4f, cy); ck.lineTo(x - rad * 0.08f, cy + rad * 0.35f); ck.lineTo(x + rad * 0.45f, cy - rad * 0.32f);
                c.drawPath(ck, p);
            } else if (i == cur) {             // active
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Ui.dp(ctx, 2)); p.setColor(0xFFB89CFF);
                c.drawCircle(x, cy, rad, p);
                p.setStyle(Paint.Style.FILL); p.setColor((0x55 + (int) (0x77 * (1 - pulse)) << 24) | 0xB89CFF);
                c.drawCircle(x, cy, rad + pulse * rad * 0.9f, p);
                p.setColor(0xFFFFFFFF); c.drawCircle(x, cy, rad * 0.42f, p);
            } else {                           // pending
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Ui.dp(ctx, 1.5f)); p.setColor(0x44FFFFFF);
                c.drawCircle(x, cy, rad * 0.85f, p);
            }
            t.setColor(i == cur ? 0xFFFFFFFF : (i < cur ? Ui.SUB : Ui.DIM));
            float tx = x + rad + Ui.dp(ctx, 16);
            if (i == cur && !notes[i].isEmpty()) {
                c.drawText(titles[i], tx, cy - Ui.dp(ctx, 1), t);
                c.drawText(notes[i], tx, cy + Ui.dp(ctx, 15), n);
            } else c.drawText(titles[i], tx, cy + Ui.dp(ctx, 5), t);
        }
    }
}
