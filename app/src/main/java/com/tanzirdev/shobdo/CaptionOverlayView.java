package com.tanzirdev.shobdo;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Live caption layer drawn over the video preview. */
public class CaptionOverlayView extends View {
    public interface Clock { long nowMs(); boolean playing(); }

    private final CaptionRenderer renderer = new CaptionRenderer();
    private List<Caption> captions = new ArrayList<>();
    private CaptionStyle style = new CaptionStyle();
    private Clock clock;

    public CaptionOverlayView(Context c, AttributeSet a) { super(c, a); }

    public void bind(List<Caption> caps, CaptionStyle st, Clock ck) { captions = caps; style = st; clock = ck; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        if (clock == null) return;
        long t = clock.nowMs();
        Caption hit = null;
        for (Caption c : captions) if (t >= c.start && t < c.end) { hit = c; break; }
        renderer.draw(canvas, getWidth(), getHeight(), hit, t, style);
        if (clock.playing()) postInvalidateOnAnimation();
    }
}
