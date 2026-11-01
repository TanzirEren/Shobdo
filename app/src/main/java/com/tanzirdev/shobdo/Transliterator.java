package com.tanzirdev.shobdo;

import java.util.HashMap;
import java.util.Map;

/**
 * Offline Roman transliteration (Banglish / Hinglish / Urdish style).
 * Bangla + Devanagari use schwa rules; Arabic/Urdu is a readable approximation (no short vowels in the script).
 * Latin letters pass through unchanged, so mixed text like "ami video banai" stays natural.
 */
public class Transliterator {
    private static final Map<Character, String> BN_C = new HashMap<>(), BN_V = new HashMap<>(), BN_S = new HashMap<>();
    private static final Map<Character, String> HI_C = new HashMap<>(), HI_V = new HashMap<>(), HI_S = new HashMap<>();
    private static final Map<Character, String> AR = new HashMap<>();

    private static void put(Map<Character, String> m, String chars, String... vals) {
        for (int i = 0; i < chars.length(); i++) m.put(chars.charAt(i), vals[i]);
    }

    static {
        // Bangla
        put(BN_C, "কখগঘঙচছজঝঞটঠডঢণতথদধনপফবভমযরলশষসহ\u09CE",
            "k","kh","g","gh","ng","ch","chh","j","jh","n","t","th","d","dh","n","t","th","d","dh","n","p","f","b","bh","m","j","r","l","sh","sh","s","h","t");
        put(BN_V, "অআইঈউঊঋএঐওঔ", "o","a","i","i","u","u","ri","e","oi","o","ou");
        put(BN_S, "ািীুূৃেৈোৌ", "a","i","i","u","u","ri","e","oi","o","ou");
        // Devanagari
        put(HI_C, "कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसह",
            "k","kh","g","gh","n","ch","chh","j","jh","n","t","th","d","dh","n","t","th","d","dh","n","p","f","b","bh","m","y","r","l","v","sh","sh","s","h");
        put(HI_V, "अआइईउऊऋएऐओऔ", "a","a","i","i","u","u","ri","e","ai","o","au");
        put(HI_S, "ािीुूृेैोौॅ", "a","i","i","u","u","ri","e","ai","o","au","e");
        // Arabic / Urdu / Persian (consonants; vowels handled in code)
        put(AR, "بپتٹثجچحخدڈذرڑزژسشصضطظغفقکكگلمنںہھةء",
            "b","p","t","t","s","j","ch","h","kh","d","d","z","r","r","z","zh","s","sh","s","z","t","z","gh","f","q","k","k","g",
            "l","m","n","n","h","h","h","'");
    }

    public static String toRoman(String s) {
        StringBuilder out = new StringBuilder();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c >= 0x0980 && c <= 0x09FF) { i = indic(s, i, out, true); }
            else if (c >= 0x0900 && c <= 0x097F) { i = indic(s, i, out, false); }
            else if ((c >= 0x0600 && c <= 0x06FF) || (c >= 0xFB50 && c <= 0xFEFF)) { i = arabic(s, i, out); }
            else { out.append(c); i++; }
        }
        return out.toString();
    }

    private static boolean isIndicLetter(char c) { return (c >= 0x0980 && c <= 0x09FF) || (c >= 0x0900 && c <= 0x097F); }

    private static int indic(String s, int i, StringBuilder out, boolean bn) {
        Map<Character, String> C = bn ? BN_C : HI_C, V = bn ? BN_V : HI_V, S = bn ? BN_S : HI_S;
        String schwa = bn ? "o" : "a";
        char virama = bn ? '\u09CD' : '\u094D';
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (C.containsKey(c)) {
                out.append(C.get(c)); i++;
                if (i < n && s.charAt(i) == (bn ? '\u09BC' : '\u093C')) {            // nukta: modify consonant
                    i++;
                    String base = C.get(c);
                    String fixed = bn ? ("d".equals(base) ? "r" : "dh".equals(base) ? "rh" : "j".equals(base) ? "y" : null)
                                      : ("k".equals(base) ? "q" : "j".equals(base) ? "z" : "d".equals(base) ? "r"
                                         : "dh".equals(base) ? "rh" : null);
                    if (fixed != null) out.replace(out.length() - base.length(), out.length(), fixed);
                }
                if (i < n && s.charAt(i) == virama) { i++; continue; }              // conjunct: no vowel
                if (i < n && S.containsKey(s.charAt(i))) { out.append(S.get(s.charAt(i))); i++; }
                else if (i < n && isIndicLetter(s.charAt(i)) && C.containsKey(s.charAt(i))) out.append(schwa);
                else if (i < n && isIndicLetter(s.charAt(i)) && !isMark(s.charAt(i))) out.append(schwa);
                // word end -> schwa dropped
            } else if (V.containsKey(c)) { out.append(V.get(c)); i++; }
            else if (c == 'ং' || c == 'ं' || c == 'ँ' || c == 'ঁ') { out.append(bn && c == 'ং' ? "ng" : "n"); i++; }
            else if (c == 'ঃ' || c == 'ः') { out.append('h'); i++; }
            else if (c == '।' || c == '॥') { out.append('.'); i++; }
            else if (c >= '০' && c <= '৯') { out.append((char) ('0' + (c - '০'))); i++; }
            else if (c >= '०' && c <= '९') { out.append((char) ('0' + (c - '०'))); i++; }
            else if (isIndicLetter(c)) { i++; }                                    // stray marks
            else break;                                                            // back to normal text
        }
        return i;
    }

    private static boolean isMark(char c) { return c == 'ং' || c == 'ं' || c == 'ँ' || c == 'ঁ' || c == 'ঃ' || c == 'ः'; }

    private static boolean isArLetter(char c) { return AR.containsKey(c) || c == 'ا' || c == 'آ' || c == 'و' || c == 'ی' || c == 'ي' || c == 'ے' || c == 'ئ' || c == 'ؤ' || c == 'ى'; }

    private static int arabic(String s, int i, StringBuilder out) {
        int n = s.length(); boolean prevConsonant = false;
        while (i < n) {
            char c = s.charAt(i);
            boolean nextLetter = i + 1 < n && isArLetter(s.charAt(i + 1));
            if (AR.containsKey(c)) {
                if (prevConsonant) out.append('a');
                out.append(AR.get(c)); prevConsonant = !(c == 'ء'); i++; continue;
            }
            prevConsonant = false;
            switch (c) {
                case 'ا': out.append('a'); break;
                case 'آ': out.append("aa"); break;
                case 'و': out.append(nextLetter ? "o" : "o"); break;
                case 'ی': case 'ي': case 'ى': out.append(nextLetter ? "i" : "i"); break;
                case 'ے': out.append('e'); break;
                case 'ئ': out.append('y'); break;
                case 'ؤ': out.append('o'); break;
                case '\u064E': out.append('a'); break;
                case '\u0650': out.append('i'); break;
                case '\u064F': out.append('u'); break;
                case '۔': case '.': out.append('.'); break;
                case '\u060C': out.append(','); break;
                case '؟': out.append('?'); break;
                default:
                    if (c >= '٠' && c <= '٩') out.append((char) ('0' + (c - '٠')));
                    else if (c >= '۰' && c <= '۹') out.append((char) ('0' + (c - '۰')));
                    else if ((c >= 0x0600 && c <= 0x06FF) || (c >= 0xFB50 && c <= 0xFEFF)) { /* skip marks */ }
                    else return i;
            }
            i++;
            if (c == 'و' || c == 'ی' || c == 'ي') prevConsonant = nextLetter;
        }
        return i;
    }
}
