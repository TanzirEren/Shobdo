package com.tanzirdev.shobdo;

import android.graphics.Typeface;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CaptionStyle {
    public static final String[] FONT_NAMES = {"Bold", "Medium", "Serif", "Mono", "Condensed", "Casual", "Cursive", "Black"};
    public static final String[] ANIM_NAMES = {"None", "Fade", "Pop", "Slide", "Typewriter", "Bounce"};
    public static final int ANIM_NONE = 0, ANIM_FADE = 1, ANIM_POP = 2, ANIM_SLIDE = 3, ANIM_TYPE = 4, ANIM_BOUNCE = 5;
    public static final int[] PALETTE = {0xFFFFFFFF, 0xFF000000, 0xFFFFD60A, 0xFFFF7A59, 0xFFFF3B6B, 0xFF6B4EFF,
            0xFF00C2FF, 0xFF2EC4B6, 0xFF7CFF4F, 0xFFFFB6D9};

    public String preset = "Classic";
    public int font = 0;
    public float size = 0.06f;            // fraction of the shorter video side
    public int textColor = 0xFFFFFFFF, hiColor = 0xFFFFD60A, strokeColor = 0xFF000000, bgColor = 0xFF000000;
    public int strokeW = 6, bgAlpha = 0;
    public int position = 2;              // 0 top, 1 middle, 2 bottom
    public boolean upper = false, wordHi = true, shadow = true, emoji = false;
    public int anim = CaptionStyle.ANIM_POP;

    public Typeface typeface() {
        switch (font) {
            case 1: return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            case 2: return Typeface.create("serif", Typeface.BOLD);
            case 3: return Typeface.create("monospace", Typeface.BOLD);
            case 4: return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case 5: return Typeface.create("casual", Typeface.NORMAL);
            case 6: return Typeface.create("cursive", Typeface.BOLD);
            case 7: return Typeface.create("sans-serif-black", Typeface.NORMAL);
            default: return Typeface.create("sans-serif", Typeface.BOLD);
        }
    }

    private static CaptionStyle p(String name, int font, float size, int text, int hi, int stroke, int sw,
                                  int bg, int bgA, int pos, boolean up, boolean wh, boolean sh, int anim) {
        CaptionStyle s = new CaptionStyle();
        s.preset = name; s.font = font; s.size = size; s.textColor = text; s.hiColor = hi; s.strokeColor = stroke;
        s.strokeW = sw; s.bgColor = bg; s.bgAlpha = bgA; s.position = pos; s.upper = up; s.wordHi = wh; s.shadow = sh; s.anim = anim;
        return s;
    }

    public static List<CaptionStyle> presets() {
        List<CaptionStyle> l = new ArrayList<>();
        l.add(p("Classic", 0, 0.060f, 0xFFFFFFFF, 0xFFFFD60A, 0xFF000000, 6, 0xFF000000, 0, 2, false, true, true, ANIM_POP));
        l.add(p("Hype", 7, 0.075f, 0xFFFFFFFF, 0xFF7CFF4F, 0xFF000000, 9, 0xFF000000, 0, 1, true, true, true, ANIM_BOUNCE));
        l.add(p("Neon", 0, 0.062f, 0xFFFFFFFF, 0xFF00F5D4, 0xFF7B2FF7, 8, 0xFF000000, 0, 2, false, true, true, ANIM_POP));
        l.add(p("Minimal", 1, 0.050f, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000, 0, 0xFF000000, 0, 2, false, false, true, ANIM_FADE));
        l.add(p("Boxed", 0, 0.052f, 0xFFFFFFFF, 0xFFFFD60A, 0xFF000000, 0, 0xFF000000, 190, 2, false, true, false, ANIM_FADE));
        l.add(p("Sunset", 0, 0.062f, 0xFFFFFFFF, 0xFFFF7A59, 0xFF3D1E6D, 8, 0xFF000000, 0, 2, false, true, true, ANIM_POP));
        l.add(p("Typewriter", 3, 0.048f, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000, 0, 0xFF000000, 160, 2, false, false, false, ANIM_TYPE));
        l.add(p("Serif", 2, 0.056f, 0xFFFFFFFF, 0xFFFFB6D9, 0xFF000000, 3, 0xFF000000, 0, 2, false, true, true, ANIM_FADE));
        l.add(p("Comic", 5, 0.068f, 0xFFFFEB3B, 0xFFFFFFFF, 0xFF000000, 9, 0xFF000000, 0, 2, false, true, true, ANIM_BOUNCE));
        l.add(p("Mint", 0, 0.060f, 0xFFFFFFFF, 0xFF2EC4B6, 0xFF0B3D3A, 7, 0xFF000000, 0, 2, false, true, true, ANIM_SLIDE));
        l.add(p("Rose", 6, 0.064f, 0xFFFFFFFF, 0xFFFF3B6B, 0xFF4A0E22, 6, 0xFF000000, 0, 2, false, true, true, ANIM_POP));
        l.add(p("Cinema", 4, 0.052f, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000, 2, 0xFF000000, 0, 2, true, false, true, ANIM_FADE));
        l.add(p("Karaoke", 0, 0.066f, 0xFFFFFFFF, 0xFFFF3B30, 0xFF000000, 8, 0xFF000000, 0, 1, false, true, true, ANIM_NONE));
        l.add(p("Top Note", 1, 0.050f, 0xFF1B1B2F, 0xFF6B4EFF, 0xFFFFFFFF, 0, 0xFFFFFFFF, 220, 0, false, true, false, ANIM_SLIDE));
        return l;
    }

    public CaptionStyle copy() { return fromJson(toJson()); }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("preset", preset); o.put("font", font); o.put("size", size);
            o.put("tc", textColor); o.put("hc", hiColor); o.put("sc", strokeColor); o.put("bc", bgColor);
            o.put("sw", strokeW); o.put("ba", bgAlpha); o.put("pos", position);
            o.put("up", upper); o.put("wh", wordHi); o.put("sh", shadow); o.put("em", emoji); o.put("an", anim);
            return o;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static CaptionStyle fromJson(JSONObject o) {
        CaptionStyle s = new CaptionStyle();
        if (o == null) return s;
        s.preset = o.optString("preset", "Classic"); s.font = o.optInt("font", 0); s.size = (float) o.optDouble("size", 0.06);
        s.textColor = o.optInt("tc", s.textColor); s.hiColor = o.optInt("hc", s.hiColor);
        s.strokeColor = o.optInt("sc", s.strokeColor); s.bgColor = o.optInt("bc", s.bgColor);
        s.strokeW = o.optInt("sw", 6); s.bgAlpha = o.optInt("ba", 0); s.position = o.optInt("pos", 2);
        s.upper = o.optBoolean("up"); s.wordHi = o.optBoolean("wh", true); s.shadow = o.optBoolean("sh", true);
        s.emoji = o.optBoolean("em"); s.anim = o.optInt("an", ANIM_POP);
        return s;
    }
}
