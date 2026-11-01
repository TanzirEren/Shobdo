package com.tanzirdev.shobdo;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.TextureOverlay;
import androidx.media3.transformer.*;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Burns captions into the video (H.264) fully offline using Media3 Transformer. */
@UnstableApi
public class Exporter {
    public interface Listener { void onProgress(int percent); void onDone(File f); void onError(String msg); }

    public static int[] videoSize(Context c, Uri uri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, uri);
            int w = Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h = Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            String rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            int deg = rot == null ? 0 : Integer.parseInt(rot);
            return (deg == 90 || deg == 270) ? new int[]{h, w} : new int[]{w, h};
        } catch (Exception e) { return new int[]{1080, 1920}; }
        finally { try { r.release(); } catch (Exception ignored) {} }
    }

    /** targetHeight 0 = keep original. Must be called on the main thread. */
    public static void export(Context c, Project p, int targetHeight, Listener l) {
        Uri uri = Uri.parse(p.videoUri);
        int[] sz = videoSize(c, uri);
        int ow = sz[0], oh = sz[1];
        int outH = (targetHeight > 0 && targetHeight < Math.min(ow, oh)) ? targetHeight : 0;
        // overlay bitmap size = output frame size, capped for memory
        float scale = outH > 0 ? (float) outH / Math.min(ow, oh) : 1f;
        int fw = Math.round(ow * scale), fh = Math.round(oh * scale);
        float cap = 1080f / Math.min(fw, fh);
        if (cap < 1f) { fw = Math.round(fw * cap); fh = Math.round(fh * cap); }

        List<Effect> fx = new ArrayList<>();
        if (outH > 0) fx.add(Presentation.createForHeight(ow < oh ? Math.round(outH * (float) oh / ow) : outH));
        fx.add(new OverlayEffect(ImmutableList.<TextureOverlay>of(new ExportOverlay(p.captions, p.style, fw, fh))));

        EditedMediaItem item = new EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(new Effects(new ArrayList<>(), fx)).build();

        File out = new File(Storage.outDir(c, "Videos"), Storage.safeName(p.title) + "_captioned_" + System.currentTimeMillis() % 100000 + ".mp4");
        Handler main = new Handler(Looper.getMainLooper());
        final boolean[] finished = {false};
        Transformer t = new Transformer.Builder(c)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(new Transformer.Listener() {
                    @Override public void onCompleted(Composition comp, ExportResult r) { finished[0] = true; l.onDone(out); }
                    @Override public void onError(Composition comp, ExportResult r, ExportException e) { finished[0] = true; l.onError(String.valueOf(e.getMessage())); }
                }).build();
        t.start(item, out.getAbsolutePath());

        ProgressHolder ph = new ProgressHolder();
        Runnable poll = new Runnable() {
            @Override public void run() {
                int s = t.getProgress(ph);
                if (s == Transformer.PROGRESS_STATE_AVAILABLE) l.onProgress(ph.progress);
                if (!finished[0]) main.postDelayed(this, 400);
            }
        };
        main.postDelayed(poll, 400);
    }
}
