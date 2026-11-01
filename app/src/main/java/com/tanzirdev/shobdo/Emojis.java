package com.tanzirdev.shobdo;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny offline keyword -> emoji map (English + Roman Bangla/Hindi words). */
public class Emojis {
    private static final Map<String, String> M = new LinkedHashMap<>();
    static {
        String[][] d = {
            {"love", "❤️"}, {"pyar", "❤️"}, {"bhalobasha", "❤️"}, {"happy", "😊"}, {"khushi", "😊"}, {"sad", "😢"},
            {"funny", "😂"}, {"haha", "😂"}, {"fire", "🔥"}, {"hot", "🔥"}, {"money", "💰"}, {"paisa", "💰"},
            {"taka", "💰"}, {"idea", "💡"}, {"music", "🎵"}, {"song", "🎵"}, {"gaan", "🎵"}, {"time", "⏰"},
            {"star", "⭐"}, {"win", "🏆"}, {"best", "🏆"}, {"wow", "😮"}, {"yes", "✅"}, {"no", "❌"},
            {"food", "🍔"}, {"khana", "🍽️"}, {"home", "🏠"}, {"ghar", "🏠"}, {"phone", "📱"}, {"video", "🎬"},
            {"subscribe", "🔔"}, {"like", "👍"}, {"share", "📤"}, {"world", "🌍"}, {"dua", "🤲"}, {"allah", "🕌"},
            {"success", "🚀"}, {"learn", "📚"}, {"study", "📚"}, {"school", "🏫"}, {"gift", "🎁"}, {"free", "🆓"}
        };
        for (String[] x : d) M.put(x[0], x[1]);
    }

    public static String forText(String text) {
        for (String w : text.toLowerCase().split("[^\\p{L}]+")) {
            String e = M.get(w);
            if (e != null) return e;
        }
        return "";
    }
}
