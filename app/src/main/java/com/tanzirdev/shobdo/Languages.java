package com.tanzirdev.shobdo;

/** Whisper language codes + mixed-language presets (language hint + prompt + default script). */
public class Languages {
    public static class Entry {
        public final String label, code, prompt; public final boolean romanDefault;
        Entry(String l, String c, String p, boolean r) { label = l; code = c; prompt = p; romanDefault = r; }
        @Override public String toString() { return label; }
    }

    private static Entry e(String l, String c) { return new Entry(l, c, "", false); }
    private static Entry m(String l, String c, String p) { return new Entry(l, c, p, true); }

    public static final Entry[] ALL = {
        e("Auto-detect (any language)", "auto"),
        m("Banglish  (Bangla + English)", "bn", "আমি আজকে একটা video বানাচ্ছি, so please like আর subscribe করো।"),
        m("Hinglish  (Hindi + English)", "hi", "आज हम एक video बनाएंगे, so let's start करते हैं।"),
        m("Urdish  (Urdu + English)", "ur", "آج ہم ایک video بنائیں گے، so please like اور subscribe کریں۔"),
        m("Arabizi  (Arabic + English)", "ar", "اليوم رح نعمل video جديد، so please like و subscribe."),
        e("English", "en"), e("Bangla (বাংলা)", "bn"), e("Hindi (हिन्दी)", "hi"), e("Arabic (العربية)", "ar"),
        e("Urdu (اردو)", "ur"), e("Persian (فارسی)", "fa"), e("Turkish", "tr"), e("Indonesian", "id"),
        e("Malay", "ms"), e("Tamil", "ta"), e("Telugu", "te"), e("Marathi", "mr"), e("Gujarati", "gu"),
        e("Punjabi", "pa"), e("Nepali", "ne"), e("Spanish", "es"), e("French", "fr"), e("German", "de"),
        e("Portuguese", "pt"), e("Russian", "ru"), e("Italian", "it"), e("Japanese", "ja"), e("Korean", "ko"),
        e("Chinese", "zh"), e("Swahili", "sw"), e("Thai", "th"), e("Vietnamese", "vi")
    };

    public static final String[] SCRIPT_MODES = {"Native script", "Roman letters (Banglish / Hinglish style)"};
}
