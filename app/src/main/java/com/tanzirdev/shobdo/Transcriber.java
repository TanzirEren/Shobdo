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
        public String stripPrompt = "";   // prompt text that must never appear as a caption
    }

    private static volatile boolean cancelled = false;
    public static void cancel() { cancelled = true; WhisperBridge.nativeAbort(); }

    private static boolean hasAsset(Context c, String name) {
        try { String[] l = c.getAssets().list("models"); if (l != null) for (String s : l) if (s.equals(name)) return true; }
        catch (IOException ignored) {}
        return false;
    }

    private static File copyAsset(Context c, String asset, String outName) throws IOException {
        File f = new File(c.getFilesDir(), outName);
        long assetLen = -1;
        try (android.content.res.AssetFileDescriptor fd = c.getAssets().openFd("models/" + asset)) { assetLen = fd.getLength(); }
        catch (Exception ignored) {}
        if (!f.exists() || (assetLen > 0 && f.length() != assetLen)) {
            try (InputStream in = c.getAssets().open("models/" + asset); OutputStream o = new FileOutputStream(f)) {
                byte[] b = new byte[1 << 16]; int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
            } catch (IOException e) { f.delete(); throw new IOException("Speech model missing (" + asset + "). Rebuild the app or import a model in Settings."); }
        }
        return f;
    }

    public static boolean hasBanglaModel(Context c) { return hasAsset(c, "bangla.bin"); }

    /** An imported model (Settings) overrides everything. */
    private static File custom(Context c) {
        File f = new File(c.getFilesDir(), "custom.bin");
        return f.exists() && f.length() > 10_000_000 ? f : null;
    }

    /** General multilingual model. */
    public static File generalModel(Context c) throws IOException {
        File cu = custom(c);
        if (cu != null) return cu;
        return copyAsset(c, hasAsset(c, "general.bin") ? "general.bin" : "model.bin", "general.bin");
    }

    /** Bangla is routed to the Bengali fine-tuned model when it is installed. */
    public static File modelFor(Context c, String lang) throws IOException {
        File cu = custom(c);
        if (cu != null) return cu;
        if (lang.equals("bn") && hasBanglaModel(c)) return copyAsset(c, "bangla.bin", "bangla.bin");
        return generalModel(c);
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

    private static Character.UnicodeScript scriptFor(String code) {
        switch (code) {
            case "bn": return Character.UnicodeScript.BENGALI;
            case "hi": case "mr": case "ne": return Character.UnicodeScript.DEVANAGARI;
            case "ur": case "ar": case "fa": return Character.UnicodeScript.ARABIC;
            case "ta": return Character.UnicodeScript.TAMIL;
            case "te": return Character.UnicodeScript.TELUGU;
            case "gu": return Character.UnicodeScript.GUJARATI;
            case "pa": return Character.UnicodeScript.GURMUKHI;
            default: return null;
        }
    }

    /** True when the text is mostly in a script that does not belong to the spoken language (= model could not do it). */
    static boolean wrongScript(String code, List<Caption> caps) {
        Character.UnicodeScript want = scriptFor(code);
        if (want == null) return false;
        int letters = 0, ok = 0;
        for (Caption c : caps) {
            for (int i = 0; i < c.text.length(); ) {
                int cp = c.text.codePointAt(i); i += Character.charCount(cp);
                if (!Character.isLetter(cp)) continue;
                letters++;
                if (Character.UnicodeScript.of(cp) == want) ok++;
            }
        }
        return letters >= 20 && ok < letters * 0.5;
    }

    public static Project run(Context c, Uri video, String title, Options o, Callback cb) throws Exception {
        cancelled = false;
        cb.onStep(STEP_AUDIO, "Preparing speech model", 2);
        String wanted = o.lang;
        File first = wanted.equals("auto") ? generalModel(c) : modelFor(c, wanted);
        cb.onStep(STEP_AUDIO, "Reading audio", 5);
        float[] audio = AudioDecoder.decode(c, video);
        if (cancelled) throw new Exception("cancelled");
        if (audio.length < 8000) throw new Exception("This video has almost no audio.");
        normalise(audio);
        cb.onStep(STEP_AUDIO, "Audio ready", 10);

        cb.onStep(STEP_LANG, "Loading speech engine", 12);
        long ctx = WhisperBridge.nativeInit(first.getAbsolutePath());
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
                File better = modelFor(c, code);               // Bangla -> Bengali model
                if (!better.equals(first)) {
                    cb.onStep(STEP_LANG, "Loading " + Languages.nameFor(code) + " model", 16);
                    WhisperBridge.nativeFree(ctx); ctx = 0;
                    ctx = WhisperBridge.nativeInit(better.getAbsolutePath());
                    if (ctx == 0) throw new Exception("Could not load the " + Languages.nameFor(code) + " model.");
                }
            }
            cb.onStep(STEP_LANG, "Language: " + Languages.nameFor(code), 18);

            // The mixed-language presets use a prompt that shows Whisper English words are welcome. No other prompt,
            // no token blocking: forcing a weak model into a script it cannot write only produces garbage.
            String prompt = o.prompt;
            int beam = o.quality == 1 ? 5 : 1;

            final boolean[] running = {true};
            final long fctx = ctx;
            Thread poll = new Thread(() -> {
                while (running[0]) {
                    int p = WhisperBridge.nativeProgress();
                    String live = WhisperBridge.partial();
                    if (o.roman) live = Transliterator.toRoman(live);
                    cb.onStep(STEP_LISTEN, "Writing captions", 20 + (int) (p * 0.75));
                    cb.onLive(live);
                    try { Thread.sleep(350); } catch (InterruptedException e) { return; }
                }
            });
            poll.start();
            o.stripPrompt = prompt;
            byte[] raw;
            try {
                raw = WhisperBridge.nativeTranscribe(fctx, audio, code, o.translate, prompt, th, beam, false);
            } finally { running[0] = false; poll.interrupt(); }
            if (raw == null) throw new Exception("The speech engine returned nothing.");
            if (raw.length > 6 && raw[0] == '#' && raw[1] == 'e') {
                String msg = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
                throw new Exception(msg.substring(msg.indexOf('|') + 1).trim());
            }

            cb.onStep(STEP_POLISH, "Polishing captions", 96);
            // Safety net: never show captions in the wrong script as if they were a result.
            if (!o.mixed && !o.translate) {
                Options probe = new Options(); probe.stripPrompt = prompt; probe.maxWords = o.maxWords;
                List<Caption> native_ = build(raw, probe);
                if (wrongScript(code, native_)) {
                    boolean bn = code.equals("bn");
                    throw new Exception(bn && !hasBanglaModel(c)
                        ? "This build has no Bangla model. Standard Whisper cannot transcribe Bangla (it writes the wrong script). Rebuild with the Bangla model option in the GitHub workflow, or import a Bangla ggml model in Settings."
                        : "The speech model could not write " + Languages.nameFor(code) + " properly. Try Accurate mode or a bigger / fine-tuned model in Settings.");
                }
            }
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
            if (ctx != 0) WhisperBridge.nativeFree(ctx);
        }
    }

    private static final Set<String> FILLERS = new HashSet<>(Arrays.asList("um", "uh", "umm", "uhh", "hmm", "mm", "er", "erm", "ah", "eh"));

    private static int idx(byte[] b, char c, int from, int to) {
        for (int i = from; i < to; i++) if (b[i] == (byte) c) return i;
        return -1;
    }

    private static long num(byte[] b, int from, int to) {
        long v = 0; boolean neg = false;
        for (int i = from; i < to; i++) {
            if (b[i] == '-') neg = true;
            else if (b[i] >= '0' && b[i] <= '9') v = v * 10 + (b[i] - '0');
        }
        return neg ? -v : v;
    }

    private static void flush(java.io.ByteArrayOutputStream cur, long s, long e, List<Caption.Word> out) {
        if (cur == null) return;
        String txt = new String(cur.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).replace("\uFFFD", "").trim();
        if (!txt.isEmpty()) out.add(new Caption.Word(txt, s, e));
    }

    /** Test/regroup helper. */
    static List<Caption> build(String raw, Options o) { return build(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8), o); }

    /**
     * Parses "t0|t1|token" lines from raw BYTES. Tokens can be partial UTF-8 sequences, so bytes of one word are
     * joined first and decoded only once the whole word is known.
     */
    static List<Caption> build(byte[] raw, Options o) {
        List<Caption.Word> words = new ArrayList<>();
        java.io.ByteArrayOutputStream cur = null;
        long cs = 0, ce = 0;
        int i = 0, n = raw.length;
        while (i < n) {
            int j = i;
            while (j < n && raw[j] != '\n') j++;
            if (j > i && raw[i] != '#') {
                int a = idx(raw, '|', i, j), b = a < 0 ? -1 : idx(raw, '|', a + 1, j);
                if (a > i && b > a) {
                    long t0 = num(raw, i, a) * 10, t1 = num(raw, a + 1, b) * 10;
                    int ts = b + 1;
                    boolean space = ts < j && raw[ts] == ' ';
                    int s = ts, e = j;
                    while (s < e && raw[s] == ' ') s++;
                    while (e > s && raw[e - 1] == ' ') e--;
                    if (e > s) {
                        if (cur == null || space) {
                            flush(cur, cs, ce, words);
                            cur = new java.io.ByteArrayOutputStream();
                            cs = t0; ce = Math.max(t1, t0);
                        } else ce = Math.max(ce, t1);
                        cur.write(raw, s, e - s);
                    }
                }
            }
            i = j + 1;
        }
        flush(cur, cs, ce, words);
        List<Caption.Word> ws = new ArrayList<>();
        for (Caption.Word w : words) {
            if (w.text.matches("^[\\[(].*[\\])]$") && w.text.toLowerCase().contains("blank")) continue;
            if (o.removeFillers && FILLERS.contains(w.text.toLowerCase().replaceAll("[^\\p{L}]", ""))) continue;
            if (o.roman) w.text = Transliterator.toRoman(w.text);
            ws.add(w);
        }
        // collapse runaway repeats (same word 4+ times in a row is a Whisper loop)
        List<Caption.Word> dd = new ArrayList<>();
        for (int k = 0; k < ws.size(); ) {
            int r = k;
            while (r + 1 < ws.size() && ws.get(r + 1).text.equalsIgnoreCase(ws.get(k).text)) r++;
            if (r - k + 1 >= 4) { ws.get(k).end = ws.get(r).end; dd.add(ws.get(k)); }
            else for (int q = k; q <= r; q++) dd.add(ws.get(q));
            k = r + 1;
        }
        ws = dd;
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
        // drop captions that are just the priming prompt leaking into silence
        if (!o.stripPrompt.isEmpty()) {
            String pn = o.stripPrompt.replaceAll("[^\\p{L}\\p{N}\\p{M}]", "");
            for (Iterator<Caption> it = caps.iterator(); it.hasNext(); ) {
                Caption x = it.next(); x.syncText();
                String xn = x.text.replaceAll("[^\\p{L}\\p{N}\\p{M}]", "");
                if (xn.length() >= 6 && pn.contains(xn)) it.remove();
            }
        }
        for (int ci = 0; ci < caps.size(); ci++) {
            Caption x = caps.get(ci);
            x.start = x.words.get(0).start;
            x.end = x.words.get(x.words.size() - 1).end;
            long limit = ci + 1 < caps.size() ? caps.get(ci + 1).words.get(0).start : x.end + 400;
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
