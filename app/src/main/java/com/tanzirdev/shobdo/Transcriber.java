package com.tanzirdev.shobdo;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.*;

/** Full offline pipeline: video -> PCM -> whisper.cpp -> word timing -> caption blocks. */
public class Transcriber {
    public interface Callback { void onStage(String text, int percent); }

    public static class Options {
        public String lang = "auto", prompt = "";
        public boolean translate = false, roman = false, removeFillers = false;
        public int maxWords = 4;
    }

    private static volatile boolean cancelled = false;
    public static void cancel() { cancelled = true; WhisperBridge.nativeAbort(); }

    public static File modelFile(Context c) throws IOException {
        File custom = new File(c.getFilesDir(), "custom.bin");
        if (custom.exists() && custom.length() > 10_000_000) return custom;
        File f = new File(c.getFilesDir(), "model.bin");
        long assetLen = -1;
        try (android.content.res.AssetFileDescriptor fd = c.getAssets().openFd("models/model.bin")) { assetLen = fd.getLength(); }
        catch (Exception ignored) {}
        if (!f.exists() || (assetLen > 0 && f.length() != assetLen)) {
            try (InputStream in = c.getAssets().open("models/model.bin"); OutputStream o = new FileOutputStream(f)) {
                byte[] b = new byte[1 << 16]; int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
            } catch (IOException e) { f.delete(); throw new IOException("Model missing. Build with tools/fetch_model.py or import a model."); }
        }
        return f;
    }

    public static Project run(Context c, Uri video, String title, Options o, Callback cb) throws Exception {
        cancelled = false;
        cb.onStage("Preparing offline model", 2);
        File model = modelFile(c);

        cb.onStage("Reading audio from video", 6);
        float[] audio = AudioDecoder.decode(c, video);
        if (cancelled) throw new Exception("cancelled");
        if (audio.length < 16000) throw new Exception("Audio is too short");

        cb.onStage("Loading speech engine", 12);
        long ctx = WhisperBridge.nativeInit(model.getAbsolutePath());
        if (ctx == 0) throw new Exception("Could not load model file");

        final boolean[] running = {true};
        Thread poll = new Thread(() -> {
            while (running[0]) {
                int p = WhisperBridge.nativeProgress();
                cb.onStage("Listening & writing captions", 15 + (int) (p * 0.8));
                try { Thread.sleep(400); } catch (InterruptedException e) { return; }
            }
        });
        poll.start();
        String raw;
        try {
            int threads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() - 1));
            raw = WhisperBridge.nativeTranscribe(ctx, audio, o.lang, o.translate, o.prompt, threads);
        } finally {
            running[0] = false; poll.interrupt();
            WhisperBridge.nativeFree(ctx);
        }
        if (raw.startsWith("#error")) throw new Exception(raw.substring(raw.indexOf('|') + 1));

        cb.onStage("Polishing captions", 97);
        Project p = new Project();
        p.id = String.valueOf(System.currentTimeMillis());
        p.title = title;
        p.videoUri = video.toString();
        p.created = System.currentTimeMillis();
        p.durationMs = (long) (audio.length / 16.0);
        p.lang = o.lang;
        p.captions = build(raw, o);
        return p;
    }

    private static final Set<String> FILLERS = new HashSet<>(Arrays.asList("um", "uh", "umm", "uhh", "hmm", "mm", "er", "erm", "ah", "eh"));

    static List<Caption> build(String raw, Options o) {
        // 1) tokens -> words
        List<Caption.Word> words = new ArrayList<>();
        Caption.Word cur = null;
        for (String line : raw.split("\n")) {
            if (line.startsWith("#") || line.isEmpty()) continue;
            int a = line.indexOf('|'), b = line.indexOf('|', a + 1);
            if (a < 0 || b < 0) continue;
            long t0 = Long.parseLong(line.substring(0, a)) * 10, t1 = Long.parseLong(line.substring(a + 1, b)) * 10;
            String tok = line.substring(b + 1);
            boolean space = tok.startsWith(" ");
            String clean = tok.trim();
            if (clean.isEmpty()) continue;
            if (cur == null || space) {
                cur = new Caption.Word(clean, t0, Math.max(t1, t0));
                words.add(cur);
            } else {
                cur.text += clean; cur.end = Math.max(cur.end, t1);
            }
        }
        // 2) clean + transliterate
        List<Caption.Word> ws = new ArrayList<>();
        for (Caption.Word w : words) {
            if (w.text.matches("^[\\[(].*[\\])]$") && w.text.toLowerCase().contains("blank")) continue;
            if (o.removeFillers && FILLERS.contains(w.text.toLowerCase().replaceAll("[^\\p{L}]", ""))) continue;
            if (o.roman) w.text = Transliterator.toRoman(w.text);
            ws.add(w);
        }
        // 3) repair timing: monotonic, minimum length
        long prevEnd = 0;
        for (Caption.Word w : ws) {
            if (w.start < prevEnd) w.start = prevEnd;
            if (w.end < w.start + 90) w.end = w.start + 90;
            prevEnd = w.end;
        }
        // 4) group into caption blocks
        List<Caption> caps = new ArrayList<>();
        Caption c = null; int chars = 0;
        for (Caption.Word w : ws) {
            boolean newBlock = c == null;
            if (!newBlock) {
                Caption.Word last = c.words.get(c.words.size() - 1);
                boolean sentenceEnd = last.text.matches(".*[.?!।؟۔]$");
                newBlock = c.words.size() >= o.maxWords || chars + w.text.length() > 34 || w.start - last.end > 700 || sentenceEnd;
            }
            if (newBlock) { c = new Caption(); caps.add(c); chars = 0; }
            c.words.add(w); chars += w.text.length() + 1;
        }
        for (int i = 0; i < caps.size(); i++) {
            Caption x = caps.get(i);
            x.start = x.words.get(0).start;
            x.end = x.words.get(x.words.size() - 1).end;
            long limit = i + 1 < caps.size() ? caps.get(i + 1).words.get(0).start : x.end + 400;
            x.end = Math.min(x.end + 300, Math.max(x.end, limit));
            x.syncText();
        }
        return caps;
    }

    /** Re-group existing captions to a new words-per-caption value (keeps word timing). */
    public static List<Caption> regroup(List<Caption> in, int maxWords) {
        List<Caption.Word> ws = new ArrayList<>();
        for (Caption c : in) ws.addAll(c.words);
        StringBuilder sb = new StringBuilder();
        Options o = new Options(); o.maxWords = maxWords;
        for (Caption.Word w : ws) sb.append(w.start / 10).append('|').append(w.end / 10).append('|').append(' ').append(w.text).append('\n');
        return build(sb.toString(), o);
    }
}
