package com.tanzirdev.shobdo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Whisper language codes, mixed-language presets and native-script prompts. */
public class Languages {
    public static class Entry {
        public final String label, code, prompt; public final boolean mixed;
        Entry(String l, String c, String p, boolean m) { label = l; code = c; prompt = p; mixed = m; }
        @Override public String toString() { return label; }
    }

    private static Entry e(String l, String c) { return new Entry(l, c, "", false); }
    private static Entry m(String l, String c, String p) { return new Entry(l, c, p, true); }

    public static final Entry[] ALL = {
        e("Auto-detect", "auto"),
        m("Banglish · Bangla + English", "bn", "আমি আজকে একটা video বানাচ্ছি, so please like আর subscribe করো।"),
        m("Hinglish · Hindi + English", "hi", "आज हम एक video बनाएंगे, so let's start करते हैं।"),
        m("Urdish · Urdu + English", "ur", "آج ہم ایک video بنائیں گے، so please like اور subscribe کریں۔"),
        m("Arabizi · Arabic + English", "ar", "اليوم رح نعمل video جديد، so please like و subscribe."),
        e("English", "en"), e("Bangla · বাংলা", "bn"), e("Hindi · हिन्दी", "hi"), e("Arabic · العربية", "ar"),
        e("Urdu · اردو", "ur"), e("Persian · فارسی", "fa"), e("Turkish", "tr"), e("Indonesian", "id"),
        e("Malay", "ms"), e("Tamil", "ta"), e("Telugu", "te"), e("Marathi", "mr"), e("Gujarati", "gu"),
        e("Punjabi", "pa"), e("Nepali", "ne"), e("Spanish", "es"), e("French", "fr"), e("German", "de"),
        e("Portuguese", "pt"), e("Russian", "ru"), e("Italian", "it"), e("Japanese", "ja"), e("Korean", "ko"),
        e("Chinese", "zh"), e("Swahili", "sw"), e("Thai", "th"), e("Vietnamese", "vi")
    };

    /** Languages whose captions must be in a non-Latin script (Latin tokens are blocked in pure mode). */
    public static final Set<String> NATIVE = new HashSet<>(Arrays.asList("bn", "hi", "ur", "ar", "fa", "ta", "te", "mr", "gu", "pa", "ne"));

    /** A short prompt in the native script nudges Whisper to write in that script. */
    public static String nativePrompt(String code) {
        switch (code) {
            case "bn": return "আমি আজ আপনাদের সাথে কথা বলব। এটি একটি বাংলা ভিডিও।";
            case "hi": return "आज हम आपसे बात करेंगे। यह एक हिंदी वीडियो है।";
            case "ur": return "آج ہم آپ سے بات کریں گے۔ یہ ایک اردو ویڈیو ہے۔";
            case "ar": return "اليوم سنتحدث معكم. هذا فيديو باللغة العربية.";
            case "fa": return "امروز با شما صحبت می‌کنیم. این یک ویدیو فارسی است.";
            case "ne": return "आज हामी कुरा गर्छौं। यो नेपाली भिडियो हो।";
            case "mr": return "आज आपण बोलणार आहोत. हा मराठी व्हिडिओ आहे.";
            default: return "";
        }
    }

    public static String nameFor(String code) {
        for (Entry x : ALL) if (!x.mixed && x.code.equals(code)) return x.label.split(" · ")[0];
        return code;
    }
}
