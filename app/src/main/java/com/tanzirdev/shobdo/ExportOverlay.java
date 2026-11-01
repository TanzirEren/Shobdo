package com.tanzirdev.shobdo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import java.util.List;

/** Media3 Transformer overlay: renders the same captions frame-by-frame into the exported video. */
@UnstableApi
public class ExportOverlay extends BitmapOverlay {
    private final List<Caption> caps; private final CaptionStyle style; private final int w, h;
    private final CaptionRenderer renderer = new CaptionRenderer();
    private final Bitmap empty = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
    private Bitmap last; private long lastKey = Long.MIN_VALUE;
    private int hintIndex = 0;

    public ExportOverlay(List<Caption> caps, CaptionStyle style, int w, int h) {
        this.caps = caps; this.style = style; this.w = w; this.h = h;
    }

    @Override public Bitmap getBitmap(long presentationTimeUs) {
        long t = presentationTimeUs / 1000;
        int idx = -1;
        if (hintIndex < caps.size() && t >= caps.get(hintIndex).start && t < caps.get(hintIndex).end) idx = hintIndex;
        else for (int i = 0; i < caps.size(); i++) if (t >= caps.get(i).start && t < caps.get(i).end) { idx = i; hintIndex = i; break; }
        if (idx < 0) { lastKey = -1; return empty; }
        Caption c = caps.get(idx);
        long key = CaptionRenderer.frameKey(c, t, style, idx);
        if (key == lastKey && last != null) return last;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        renderer.draw(new Canvas(b), w, h, c, t, style);
        last = b; lastKey = key;
        return b;
    }
}
