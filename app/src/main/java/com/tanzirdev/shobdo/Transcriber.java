package com.tanzirdev.shobdo;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.*;

/** Offline pipeline: video -> PCM -> language detection -> whisper.cpp -> word timing -> caption blocks. */
public class Transcriber {
    public static final int STEP_AUDIO = 0, STEP_LANG = 1, STEP_LISTEN = 2, STEP_POLISH = 3;

    public interface Callback {
        void onStep(int step, String text, int percent);
        void onLive(String text);
    }

    public static class Options {
        public String lang = "auto", prompt = "";
        public boolean translate = false, roman = false, removeFillers = false, mixed = false;
        public int maxWords = 4;
        public int quality = 0;      // 0 = fast (greedy), 1 = accurate (beam search)
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
            } catch (IOException e) { f.delete(); throw new IOException("Speech model missing. Rebuild with a model or import one in Settings."); }
        }
        return f;
    }

    private static int threads() {
        int n = Runtime.getRuntime().availableProcessors();
        return Math.max(2, Math.min(6, n >= 8 ? n - 3 : n - 2));
    }

    /** Peak-normalise so quiet videos are recognised as well as loud ones. */
    private static void normalise(float[] a) {
        float peak = 0;
        for (float v : a) { float x = Math.abs(v); if (x > peak) peak = x; }
        if (peak < 0.002f) return;
        float g = Math.min(6f, 0.9f / peak);
        if (g > 1.05f) for (int i = 0; i < a.length; i++) a[i] *= g;
    }

    /** Index of the first 100 ms frame that clearly contains sound. */
    private static int firstVoice(float[] a) {
        int frame = 1600;
        for (int i = 0; i + frame < a.length; i += frame) {
            double s = 0;
            for (int k = i; k < i + frame; k += 4) s += a[k] * a[k];
            if (Math.sqrt(s / (frame / 4.0)) > 0.02) return i;
        }
        return 0;
    }

    public static Project run(Context c, Uri video, String title, Options o, Callback cb) throws Exception {
        cancelled = false;
        cb.onStep(STEP_AUDIO, "Reading audio", 2);
        File model = modelFile(c);
        float[] audio = AudioDecoder.decode(c, video);
        if (cancelled) throw new Exception("cancelled");
        if (audio.length < 8000) throw new Exception("This video has almost no audio.");
        normalise(audio);
        cb.onStep(STEP_AUDIO, "Audio ready", 10);

        cb.onStep(STEP_LANG, "Loading speech engine", 12);
        long ctx = WhisperBridge.nativeInit(model.getAbsolutePath());
        if (ctx == 0) throw new Exception("Could not load the speech model file.");
        int th = threads();
        try {
            String code = o.lang;
            if (code.equals("auto")) {
                cb.onStep(STEP_LANG, "Detecting language", 14);
                int from = Math.max(0, firstVoice(audio) - 8000);
                int to = Math.min(audio.length, from + 16000 * 30);
                code = WhisperBridge.nativeDetect(ctx, Arrays.copyOfRange(audio, from, to), th);
                if (cancelled) throw new Exception("cancelled");
            }
            cb.onStep(STEP_LANG, "Language: " + Languages.nameFor(code), 18);

            boolean nativeScript = Languages.NATIVE.contains(code);
            boolean suppressLatin = nativeScript && !o.mixed && !o.translate;
            String prompt = !o.prompt.isEmpty() ? o.prompt : (nativeScript && !o.translate ? Languages.nativePrompt(code) : "");
            int beam = o.quality == 1 ? 5 : 1;

            final boolean[] running = {true};
            final String showCode = code;
            Thread poll = new Thread(() -> {
                while (running[0]) {
                    int p = WhisperBridge.nativeProgress();
                    String live = WhisperBridge.nativePartial();
                    if (o.roman) live = Transliterator.toRoman(live);
                    cb.onStep(STEP_LISTEN, "Writing captions", 20 + (int) (p * 0.75));
                    cb.onLive(live);
                    try { Thread.sleep(350); } catch (InterruptedException e) { return; }
                }
            });
            poll.start();
            String raw;
            try {
                raw = WhisperBridge.nativeTranscribe(ctx, audio, showCode, o.translate, prompt, th, beam, suppressLatin);
            } finally { running[0] = false; poll.interrupt(); }
            if (raw.startsWith("#error")) throw new Exception(raw.substring(raw.indexOf('|') + 1));

            cb.onStep(STEP_POLISH, "Polishing captions", 96);
            Project p = new Project();
            p.id = String.valueOf(System.currentTimeMillis());
            p.title = Storage.cleanTitle(title);
            p.videoUri = video.toString();
            p.created = System.currentTimeMillis();
            p.durationMs = (long) (audio.length / 16.0);
            p.lang = code;
            p.captions = build(raw, o);
            if (p.captions.isEmpty()) throw new Exception("No speech was found. Try the Accurate mode or a bigger model in Settings.");
            cb.onStep(STEP_POLISH, "Done", 100);
            return p;
        } finally {
            WhisperBridge.nativeFree(ctx);
        }
    }

    private static final Set<String> FILLERS = new HashSet<>(Arrays.asList("um", "uh", "umm", "uhh", "hmm", "mm", "er", "erm", "ah", "eh"));

    static List<Caption> build(String raw, Options o) {
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
            if (cur == null || space) { cur = new Caption.Word(clean, t0, Math.max(t1, t0)); words.add(cur); }
            else { cur.text += clean; cur.end = Math.max(cur.end, t1); }
        }
        List<Caption.Word> ws = new ArrayList<>();
        for (Caption.Word w : words) {
            if (w.text.matches("^[\\[(].*[\\])]$") && w.text.toLowerCase().contains("blank")) continue;
            if (o.removeFillers && FILLERS.contains(w.text.toLowerCase().replaceAll("[^\\p{L}]", ""))) continue;
            if (o.roman) w.text = Transliterator.toRoman(w.text);
            ws.add(w);
        }
        long prevEnd = 0;
        for (Caption.Word w : ws) {
            if (w.start < prevEnd) w.start = prevEnd;
            if (w.end < w.start + 90) w.end = w.start + 90;
            prevEnd = w.end;
        }
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
