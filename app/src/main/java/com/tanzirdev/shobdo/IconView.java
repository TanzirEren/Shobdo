package com.tanzirdev.shobdo;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/** Crisp vector-style icons drawn with Canvas (no image assets). */
public class IconView extends View {
    public static final int BACK = 0, PLAY = 1, PAUSE = 2, CLOSE = 3, CHEVRON = 4;
    public static final int BG_NONE = 0, BG_GLASS = 1, BG_GRADIENT = 2;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), bgp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int type = BACK, bg = BG_NONE, color = 0xFFFFFFFF;
    private float scale = 0.42f;

    public IconView(Context c) { super(c); init(); }
    public IconView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init() { p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); }

    public IconView set(int type, int bg, int color) { this.type = type; this.bg = bg; this.color = color; invalidate(); return this; }
    public IconView setType(int type) { this.type = type; invalidate(); return this; }
    public IconView scale(float s) { scale = s; invalidate(); return this; }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, d = Math.min(w, h);
        if (bg == BG_GLASS) {
            bgp.setShader(null); bgp.setColor(0x26FFFFFF); c.drawCircle(cx, cy, d / 2f, bgp);
            bgp.setStyle(Paint.Style.STROKE); bgp.setStrokeWidth(Ui.dp(getContext(), 0.8f)); bgp.setColor(0x33FFFFFF);
            c.drawCircle(cx, cy, d / 2f - 0.5f, bgp); bgp.setStyle(Paint.Style.FILL);
        } else if (bg == BG_GRADIENT) {
            bgp.setStyle(Paint.Style.FILL);
            bgp.setShader(new LinearGradient(0, 0, w, h, 0xFF7C4DFF, 0xFFFF5FA2, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, d / 2f, bgp);
        }
        float r = d * scale;
        p.setColor(color); p.setShader(null);
        p.setStrokeWidth(Math.max(2f, d * 0.065f));
        switch (type) {
            case PLAY: {
                p.setStyle(Paint.Style.FILL);
                Path t = new Path();
                t.moveTo(cx - r * 0.45f, cy - r * 0.8f); t.lineTo(cx - r * 0.45f, cy + r * 0.8f); t.lineTo(cx + r * 0.85f, cy); t.close();
                c.drawPath(t, p); break;
            }
            case PAUSE: {
                p.setStyle(Paint.Style.FILL);
                float bw = r * 0.38f;
                c.drawRoundRect(cx - r * 0.7f, cy - r * 0.8f, cx - r * 0.7f + bw, cy + r * 0.8f, bw / 2, bw / 2, p);
                c.drawRoundRect(cx + r * 0.7f - bw, cy - r * 0.8f, cx + r * 0.7f, cy + r * 0.8f, bw / 2, bw / 2, p); break;
            }
            case CLOSE: {
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(cx - r * 0.6f, cy - r * 0.6f, cx + r * 0.6f, cy + r * 0.6f, p);
                c.drawLine(cx + r * 0.6f, cy - r * 0.6f, cx - r * 0.6f, cy + r * 0.6f, p); break;
            }
            case CHEVRON: {
                p.setStyle(Paint.Style.STROKE);
                Path t = new Path(); t.moveTo(cx - r * 0.35f, cy - r * 0.8f); t.lineTo(cx + r * 0.45f, cy); t.lineTo(cx - r * 0.35f, cy + r * 0.8f);
                c.drawPath(t, p); break;
            }
            default: {
                p.setStyle(Paint.Style.STROKE);
                Path t = new Path(); t.moveTo(cx + r * 0.35f, cy - r * 0.8f); t.lineTo(cx - r * 0.45f, cy); t.lineTo(cx + r * 0.35f, cy + r * 0.8f);
                c.drawPath(t, p);
            }
        }
    }
}
