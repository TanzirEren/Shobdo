package com.tanzirdev.shobdo;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class Project {
    public String id, title, videoUri, lang = "auto";
    public long durationMs, created;
    public int width, height;
    public List<Caption> captions = new ArrayList<>();
    public CaptionStyle style = new CaptionStyle();

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("title", title); o.put("uri", videoUri); o.put("lang", lang);
            o.put("dur", durationMs); o.put("created", created); o.put("w", width); o.put("h", height);
            o.put("caps", Caption.listToJson(captions)); o.put("style", style.toJson());
            return o;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static Project fromJson(JSONObject o) {
        Project p = new Project();
        p.id = o.optString("id"); p.title = o.optString("title"); p.videoUri = o.optString("uri");
        p.lang = o.optString("lang", "auto"); p.durationMs = o.optLong("dur"); p.created = o.optLong("created");
        p.width = o.optInt("w"); p.height = o.optInt("h");
        p.captions = Caption.listFromJson(o.optJSONArray("caps"));
        p.style = CaptionStyle.fromJson(o.optJSONObject("style"));
        return p;
    }
}
