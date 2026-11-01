package com.tanzirdev.shobdo;

import android.graphics.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;

/** Draws one caption (word highlight + animation) on a Canvas. Used by BOTH live preview and video export. */
public class CaptionRenderer {
    private final TextPaint fill = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint stroke = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float backOut(float t) { float s = 1.70158f; t -= 1; return t * t * ((s + 1) * t + s) + 1; }

    /** Cheap key so exporters can skip re-drawing identical frames. */
    public static long frameKey(Caption c, long t, CaptionStyle s, int index) {
        if (c == null) return -1;
        long k = index * 1000003L;
        int aw = activeWord(c, t);
        k += aw * 131L;
        if (s.anim != CaptionStyle.ANIM_NONE && t - c.start < 300) k += 7 * (1 + (t - c.start) / 16);
        if (s.anim == CaptionStyle.ANIM_BOUNCE && aw >= 0 && t - c.words.get(aw).start < 180) k += 3 * (1 + (t - c.words.get(aw).start) / 16);
        if (s.anim == CaptionStyle.ANIM_FADE && c.end - t < 200) k += 11 * (1 + (c.end - t) / 16);
        return k;
    }

    public static int activeWord(Caption c, long t) {
        int idx = -1;
        for (int i = 0; i < c.words.size(); i++) if (c.words.get(i).start <= t) idx = i;
        return idx;
    }

    public void draw(Canvas cv, int W, int H, Caption cap, long t, CaptionStyle s) {
        if (cap == null || cap.words.isEmpty()) return;
        int active = activeWord(cap, t);
        int shown = cap.words.size();
        if (s.anim == CaptionStyle.ANIM_TYPE) shown = Math.max(1, active + 1);

        StringBuilder sb = new StringBuilder();
        int hiStart = -1, hiEnd = -1;
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(' ');
            String w = cap.words.get(i).text;
            if (s.upper) w = w.toUpperCase();
            if (i == active) hiStart = sb.length();
            sb.append(w);
            if (i == active) hiEnd = sb.length();
        }
        if (s.emoji) { String e = Emojis.forText(cap.text); if (!e.isEmpty()) sb.append(' ').append(e); }

        float base = Math.min(W, H);
        float size = base * s.size;
        Typeface tf = s.typeface();
        fill.setTypeface(tf); fill.setTextSize(size); fill.setColor(s.textColor);
        fill.clearShadowLayer();
        if (s.shadow) fill.setShadowLayer(size * 0.08f, 0, size * 0.05f, 0xAA000000);
        stroke.setTypeface(tf); stroke.setTextSize(size); stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND); stroke.setColor(s.strokeColor);
        stroke.setStrokeWidth(size * s.strokeW / 40f);

        int width = (int) (W * 0.86f);
        SpannableString sp = new SpannableString(sb.toString());
        if (s.wordHi && hiStart >= 0) sp.setSpan(new ForegroundColorSpan(s.hiColor), hiStart, hiEnd, 0);
        StaticLayout fl = StaticLayout.Builder.obtain(sp, 0, sp.length(), fill, width).setAlignment(Layout.Alignment.ALIGN_CENTER).build();
        StaticLayout sl = StaticLayout.Builder.obtain(sb.toString(), 0, sb.length(), stroke, width).setAlignment(Layout.Alignment.ALIGN_CENTER).build();

        float h = fl.getHeight();
        float top = s.position == 0 ? H * 0.08f : s.position == 1 ? (H - h) / 2f : H * 0.88f - h;

        // animation
        float p = clamp((t - cap.start) / 260f), scale = 1f, dy = 0f; int alpha = 255;
        switch (s.anim) {
            case CaptionStyle.ANIM_FADE:
                alpha = (int) (255 * Math.min(p, clamp((cap.end - t) / 200f))); break;
            case CaptionStyle.ANIM_POP: scale = 0.7f + 0.3f * backOut(p); alpha = (int) (255 * clamp(p * 2)); break;
            case CaptionStyle.ANIM_SLIDE: dy = (1 - p) * H * 0.04f; alpha = (int) (255 * p); break;
            case CaptionStyle.ANIM_BOUNCE:
                if (active >= 0) { float q = clamp((t - cap.words.get(active).start) / 180f); scale = 1f + 0.10f * (float) Math.sin(Math.PI * q); }
                break;
            default: break;
        }

        cv.save();
        cv.translate(W / 2f, top + h / 2f + dy);
        cv.scale(scale, scale);
        cv.translate(-W / 2f, -(top + h / 2f));
        if (s.bgAlpha > 0) {
            float pad = size * 0.4f; float maxLine = 0;
            for (int i = 0; i < fl.getLineCount(); i++) maxLine = Math.max(maxLine, fl.getLineWidth(i));
            box.setColor((s.bgColor & 0x00FFFFFF) | ((s.bgAlpha * alpha / 255) << 24));
            cv.drawRoundRect((W - maxLine) / 2f - pad, top - pad * 0.5f, (W + maxLine) / 2f + pad, top + h + pad * 0.5f, size * 0.35f, size * 0.35f, box);
        }
        cv.translate((W - width) / 2f, top);
        fill.setAlpha(alpha); stroke.setAlpha(alpha);
        if (s.strokeW > 0) sl.draw(cv);
        fl.draw(cv);
        cv.restore();
    }
}
