package com.tanzirdev.shobdo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Storage {
    public static final String APP_DIR = "Shobdo";

    public static File root() { return new File(Environment.getExternalStorageDirectory(), APP_DIR); }   // /storage/emulated/0/Shobdo

    public static boolean hasAccess(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /** /storage/emulated/0/Shobdo/<sub>, or the app's own external folder if storage access was not granted. */
    public static File outDir(Context c, String sub) {
        if (hasAccess(c)) {
            File d = new File(root(), sub);
            if (d.exists() || d.mkdirs()) return d;
        }
        File d = new File(c.getExternalFilesDir(null), sub);
        d.mkdirs();
        return d;
    }

    public static File writeText(File f, String text) throws IOException {
        try (OutputStream o = new FileOutputStream(f)) { o.write(text.getBytes(StandardCharsets.UTF_8)); }
        return f;
    }

    public static String safeName(String s) {
        String n = s.replaceAll("[\\\\/:*?\"<>|\\n\\r]", "_").trim();
        return n.isEmpty() ? "captions" : (n.length() > 60 ? n.substring(0, 60) : n);
    }

    /** "my_video_final" -> "my video final" for display. */
    public static String cleanTitle(String s) {
        String t = s == null ? "" : s.replace('_', ' ').replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? "Untitled video" : t;
    }

    // ---- project persistence (private app storage) ----
    private static File projDir(Context c) { File d = new File(c.getFilesDir(), "projects"); d.mkdirs(); return d; }

    public static void save(Context c, Project p) {
        try { writeText(new File(projDir(c), p.id + ".json"), p.toJson().toString()); } catch (Exception ignored) {}
    }

    public static Project load(Context c, String id) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(new File(projDir(c), id + ".json")), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            return Project.fromJson(new JSONObject(sb.toString()));
        } catch (Exception e) { return null; }
    }

    public static List<Project> list(Context c) {
        List<Project> out = new ArrayList<>();
        File[] fs = projDir(c).listFiles();
        if (fs != null) for (File f : fs) {
            Project p = load(c, f.getName().replace(".json", ""));
            if (p != null) out.add(p);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.created, a.created));
        return out;
    }

    public static void delete(Context c, String id) { new File(projDir(c), id + ".json").delete(); }
}
