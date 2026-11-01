package com.tanzirdev.shobdo;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Decodes any video/audio file to 16 kHz mono float PCM (what Whisper needs). */
public class AudioDecoder {
    private float[] buf = new float[1 << 20];
    private int len = 0;
    private double step = 1, next = 0; private long idx = 0; private float prev = 0;

    private void out(float v) {
        if (len == buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
        buf[len++] = v;
    }

    private void push(float s) {
        while (next <= idx) {
            double frac = next - (idx - 1);
            out((float) (prev + (s - prev) * frac));
            next += step;
        }
        prev = s; idx++;
    }

    public static float[] decode(Context c, Uri uri) throws Exception {
        return new AudioDecoder().run(c, uri);
    }

    private float[] run(Context c, Uri uri) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(c, uri, null);
        int track = -1; MediaFormat fmt = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String m = f.getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("audio/")) { track = i; fmt = f; break; }
        }
        if (track < 0) { ex.release(); throw new Exception("This video has no audio track"); }
        ex.selectTrack(track);
        MediaCodec codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
        codec.configure(fmt, null, null, 0);
        codec.start();

        int sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE), ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        step = sr / 16000.0;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inDone = false, outDone = false;
        while (!outDone) {
            if (!inDone) {
                int ii = codec.dequeueInputBuffer(10000);
                if (ii >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(ii);
                    int sz = ex.readSampleData(ib, 0);
                    if (sz < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true; }
                    else { codec.queueInputBuffer(ii, 0, sz, ex.getSampleTime(), 0); ex.advance(); }
                }
            }
            int oi = codec.dequeueOutputBuffer(info, 10000);
            if (oi >= 0) {
                ByteBuffer ob = codec.getOutputBuffer(oi);
                ob.position(info.offset); ob.limit(info.offset + info.size);
                java.nio.ShortBuffer sb = ob.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer();
                int frames = sb.remaining() / Math.max(1, ch);
                for (int f = 0; f < frames; f++) {
                    float sum = 0;
                    for (int k = 0; k < ch; k++) sum += sb.get() / 32768f;
                    push(sum / ch);
                }
                codec.releaseOutputBuffer(oi, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
            } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat nf = codec.getOutputFormat();
                int nsr = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                if (nsr != sr) { sr = nsr; step = sr / 16000.0; }
            }
        }
        codec.stop(); codec.release(); ex.release();
        return Arrays.copyOf(buf, len);
    }
}
