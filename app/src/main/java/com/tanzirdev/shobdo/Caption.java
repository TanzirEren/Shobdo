package com.tanzirdev.shobdo;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** One subtitle block with word-level timing. Times are milliseconds. */
public class Caption {
    public static class Word {
        public String text; public long start, end;
        public Word(String t, long s, long e) { text = t; start = s; end = e; }
    }

    public long start, end;
    public String text = "";
    public List<Word> words = new ArrayList<>();

    public Caption() {}
    public Caption(long s, long e, String t) { start = s; end = e; setText(t); }

    public void setText(String t) { text = t.trim(); rebuildWords(); }

    /** Spread words across [start,end] proportionally to their length. */
    public void rebuildWords() {
        words.clear();
        if (text.isEmpty()) return;
        String[] parts = text.split("\\s+");
        int total = 0;
        for (String p : parts) total += Math.max(1, p.length());
        long dur = Math.max(1, end - start), cur = start;
        for (int i = 0; i < parts.length; i++) {
            long e = (i == parts.length - 1) ? end : cur + dur * Math.max(1, parts[i].length()) / total;
            words.add(new Word(parts[i], cur, e));
            cur = e;
        }
    }

    public void syncText() {
        StringBuilder sb = new StringBuilder();
        for (Word w : words) { if (sb.length() > 0) sb.append(' '); sb.append(w.text); }
        text = sb.toString();
    }

    /** Change caption times and retime its words proportionally. */
    public void retime(long ns, long ne) {
        if (ne <= ns) ne = ns + 200;
        long od = Math.max(1, end - start), nd = ne - ns;
        for (Word w : words) {
            w.start = ns + (w.start - start) * nd / od;
            w.end = ns + (w.end - start) * nd / od;
        }
        start = ns; end = ne;
    }

    /** Splits before word k; this keeps words [0,k), returned caption gets [k,end). */
    public Caption splitAt(int k) {
        Caption b = new Caption();
        b.words.addAll(words.subList(k, words.size()));
        words = new ArrayList<>(words.subList(0, k));
        b.start = b.words.get(0).start; b.end = end;
        end = words.get(words.size() - 1).end;
        syncText(); b.syncText();
        return b;
    }

    public void mergeWith(Caption b) {
        words.addAll(b.words); end = b.end; syncText();
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("s", start); o.put("e", end); o.put("t", text);
            JSONArray w = new JSONArray();
            for (Word x : words) { JSONArray a = new JSONArray(); a.put(x.text); a.put(x.start); a.put(x.end); w.put(a); }
            o.put("w", w);
            return o;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static Caption fromJson(JSONObject o) {
        Caption c = new Caption();
        c.start = o.optLong("s"); c.end = o.optLong("e"); c.text = o.optString("t");
        JSONArray w = o.optJSONArray("w");
        if (w != null) for (int i = 0; i < w.length(); i++) {
            JSONArray a = w.optJSONArray(i);
            c.words.add(new Word(a.optString(0), a.optLong(1), a.optLong(2)));
        }
        return c;
    }

    public static JSONArray listToJson(List<Caption> l) {
        JSONArray a = new JSONArray();
        for (Caption c : l) a.put(c.toJson());
        return a;
    }

    public static List<Caption> listFromJson(JSONArray a) {
        List<Caption> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(fromJson(a.optJSONObject(i)));
        return l;
    }
}
