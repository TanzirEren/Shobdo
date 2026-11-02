package com.tanzirdev.shobdo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_VIDEO = 1, REQ_MODEL = 2;
    private static final LruCache<String, Bitmap> THUMBS = new LruCache<>(40);
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private FrameLayout root;
    private ScrollView homeScroll, settingsScroll;
    private LinearLayout homeBox, settingsBox, listBox;
    private BottomNavView nav;
    private TextView modelInfo, accessInfo, bannerTv, bgInfo;
    private int page = 0, topInset = 0, bottomInset = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.addView(new GlassBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));

        homeScroll = scroller(); homeBox = column(homeScroll);
        settingsScroll = scroller(); settingsBox = column(settingsScroll);
        settingsScroll.setVisibility(View.GONE);
        root.addView(homeScroll, new FrameLayout.LayoutParams(-1, -1));
        root.addView(settingsScroll, new FrameLayout.LayoutParams(-1, -1));
        buildHome(); buildSettings();

        nav = new BottomNavView(this);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, Ui.dp(this, 68), Gravity.BOTTOM);
        nl.leftMargin = nl.rightMargin = Ui.dp(this, 36);
        nl.bottomMargin = Ui.dp(this, 18);
        root.addView(nav, nl);
        nav.setListener(i -> { if (i == 1) newProject(); else showPage(i); });
        setContentView(root);

        Ui.edgeToEdge(this, root, (top, bot) -> {
            topInset = top; bottomInset = bot;
            ((FrameLayout.LayoutParams) nav.getLayoutParams()).bottomMargin = Ui.dp(this, 18) + bot;
            nav.requestLayout();
            int pad = Ui.dp(this, 20);
            homeBox.setPadding(pad, top + Ui.dp(this, 14), pad, Ui.dp(this, 130) + bot);
            settingsBox.setPadding(pad, top + Ui.dp(this, 14), pad, Ui.dp(this, 130) + bot);
        });
        askStorage();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshList(); refreshSettings(); refreshBanner();
    }

    private void refreshBanner() {
        if (bannerTv == null) return;
        boolean on = WorkService.transcribing;
        bannerTv.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) bannerTv.setText("●  Creating captions  " + WorkService.lastPct + "%   ›  tap to view");
        bannerTv.setTextColor(Ui.VIOLET2);
    }

    private void newProject() {
        if (WorkService.transcribing) { startActivity(new Intent(this, TranscribeActivity.class)); return; }
        pickVideo();
    }

    private ScrollView scroller() {
        ScrollView s = new ScrollView(this);
        s.setVerticalScrollBarEnabled(false); s.setOverScrollMode(View.OVER_SCROLL_NEVER); s.setFillViewport(true);
        return s;
    }

    private LinearLayout column(ScrollView s) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        s.addView(c, new ScrollView.LayoutParams(-1, -2));
        return c;
    }

    private TextView text(String s, float sp, int color, boolean med) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (med) t.setTypeface(Ui.medium());
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(Ui.dp(this, l), Ui.dp(this, t), Ui.dp(this, r), Ui.dp(this, b));
        return p;
    }

    // ===================================================== HOME
    private void buildHome() {
        // header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
        WordmarkView wm = new WordmarkView(this);
        head.addView(wm, new LinearLayout.LayoutParams(-2, Ui.dp(this, 40)));
        View sp = new View(this); head.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView st = text("●  Offline ready", 12, 0xFF7CE3B0, true);
        st.setBackground(Ui.glass(this, 20)); st.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        head.addView(st);
        homeBox.addView(head, lp(-1, -2, 0, 0, 0, 18));

        bannerTv = text("", 13.5f, Ui.TEXT, true);
        bannerTv.setBackground(Ui.glass(this, 20)); bannerTv.setVisibility(View.GONE);
        bannerTv.setPadding(Ui.dp(this, 16), Ui.dp(this, 13), Ui.dp(this, 16), Ui.dp(this, 13));
        bannerTv.setOnClickListener(v -> startActivity(new Intent(this, TranscribeActivity.class)));
        Ui.press(bannerTv);
        homeBox.addView(bannerTv, lp(-1, -2, 0, 0, 0, 12));

        // hero glass card
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.glass(this, 30));
        hero.setPadding(Ui.dp(this, 22), Ui.dp(this, 24), Ui.dp(this, 22), Ui.dp(this, 20));
        TextView h1 = text("Turn speech into", 27, Ui.TEXT, true);
        TextView h2 = text("perfect captions", 27, Ui.VIOLET2, true);
        h2.getPaint().setShader(new LinearGradient(0, 0, Ui.dp(this, 230), 0, Ui.VIOLET2, Ui.PINK, Shader.TileMode.CLAMP));
        TextView sub = text("Bangla, Hindi, Urdu, Arabic, English and more. Everything runs on your phone.", 14, Ui.SUB, false);
        sub.setLineSpacing(Ui.dp(this, 2), 1f);
        hero.addView(h1); hero.addView(h2);
        hero.addView(sub, lp(-2, -2, 0, 8, 0, 0));
        WaveBarsView wave = new WaveBarsView(this); wave.setLevel(0.55f);
        hero.addView(wave, lp(-1, 44, 0, 18, 0, 6));
        hero.addView(Ui.button(this, "Choose a video", true, v -> pickVideo()), lp(-1, -2, 0, 8, 0, 0));
        homeBox.addView(hero, lp(-1, -2, 0, 0, 0, 14));

        // stat chips
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        String[] st3 = {"30+ languages", "100% offline", "SRT · VTT · ASS · MP4"};
        for (int i = 0; i < 3; i++) {
            TextView t = text(st3[i], 11.5f, Ui.SUB, true);
            t.setGravity(Gravity.CENTER); t.setBackground(Ui.glass(this, 16));
            t.setPadding(Ui.dp(this, 6), Ui.dp(this, 11), Ui.dp(this, 6), Ui.dp(this, 11));
            stats.addView(t, lp(0, -2, i == 0 ? 0 : 5, 0, i == 2 ? 0 : 5, 0));
            ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1f;
        }
        homeBox.addView(stats, lp(-1, -2, 0, 0, 0, 22));

        homeBox.addView(text("Recent projects", 17, Ui.TEXT, true), lp(-2, -2, 2, 0, 0, 10));
        listBox = new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL);
        homeBox.addView(listBox, lp(-1, -2, 0, 0, 0, 0));
        staggerIn(homeBox);
    }

    private void staggerIn(LinearLayout box) {
        for (int i = 0; i < box.getChildCount(); i++) {
            View v = box.getChildAt(i);
            v.setAlpha(0f); v.setTranslationY(Ui.dp(this, 22));
            v.animate().alpha(1f).translationY(0f).setStartDelay(2 * 40 + i * 70L).setDuration(520)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        }
    }

    private void refreshList() {
        listBox.removeAllViews();
        List<Project> ps = Storage.list(this);
        if (ps.isEmpty()) {
            LinearLayout e = new LinearLayout(this);
            e.setOrientation(LinearLayout.VERTICAL); e.setGravity(Gravity.CENTER);
            e.setBackground(Ui.glass(this, 24)); e.setPadding(Ui.dp(this, 20), Ui.dp(this, 28), Ui.dp(this, 20), Ui.dp(this, 28));
            WaveBarsView w = new WaveBarsView(this); w.setBars(14); w.setLevel(0.25f);
            e.addView(w, new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 30)));
            e.addView(text("No projects yet", 15, Ui.TEXT, true), lp(-2, -2, 0, 12, 0, 0));
            e.addView(text("Tap + to create your first captions", 13, Ui.SUB, false), lp(-2, -2, 0, 4, 0, 0));
            listBox.addView(e);
            return;
        }
        for (Project p : ps) listBox.addView(projectCard(p), lp(-1, -2, 0, 0, 0, 10));
    }

    private View projectCard(Project p) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.glass(this, 22));
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12));

        ImageView th = new ImageView(this);
        th.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ph = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x558B5CFF, 0x33FF5FA2});
        th.setBackground(ph);
        th.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(MainActivity.this, 14)); }
        });
        th.setClipToOutline(true);
        card.addView(th, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 72)));
        loadThumb(p, th);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(p.title, 15, Ui.TEXT, true); t.setMaxLines(2); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        long sec = p.durationMs / 1000;
        String lang = p.lang == null || p.lang.equals("auto") ? "" : "  ·  " + p.lang.toUpperCase();
        TextView m = text(p.captions.size() + " captions  ·  " + Ui.clock(p.durationMs) + lang, 12, Ui.SUB, false);
        col.addView(t); col.addView(m, lp(-2, -2, 0, 4, 0, 0));
        LinearLayout.LayoutParams cl = lp(0, -2, 14, 0, 6, 0); cl.weight = 1f;
        card.addView(col, cl);

        IconView del = new IconView(this).set(IconView.CLOSE, IconView.BG_NONE, Ui.DIM);
        del.setOnClickListener(v -> new AlertDialog.Builder(this, R.style.GlassDialog).setTitle("Delete project?")
                .setMessage(p.title).setPositiveButton("Delete", (d, w) -> { Storage.delete(this, p.id); refreshList(); })
                .setNegativeButton("Cancel", null).show());
        card.addView(del, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        Ui.press(card);
        card.setOnClickListener(v -> {
            startActivity(new Intent(this, EditorActivity.class).putExtra("id", p.id));
            overridePendingTransition(R.anim.fade_in_up, R.anim.fade_out);
        });
        return card;
    }

    private void loadThumb(Project p, ImageView iv) {
        Bitmap c = THUMBS.get(p.id);
        if (c != null) { iv.setImageBitmap(c); return; }
        io.execute(() -> {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(this, Uri.parse(p.videoUri));
                Bitmap f = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (f != null) {
                    int w = 160, h = Math.max(1, f.getHeight() * w / f.getWidth());
                    Bitmap s = Bitmap.createScaledBitmap(f, w, h, true);
                    THUMBS.put(p.id, s);
                    runOnUiThread(() -> { iv.setImageBitmap(s); iv.setAlpha(0f); iv.animate().alpha(1f).setDuration(300).start(); });
                }
            } catch (Exception ignored) {} finally { try { r.release(); } catch (Exception ignored) {} }
        });
    }

    // ===================================================== SETTINGS
    private void buildSettings() {
        settingsBox.addView(text("Settings", 28, Ui.TEXT, true), lp(-2, -2, 2, 6, 0, 16));

        LinearLayout model = Ui.section(this, "Speech model");
        modelInfo = text("", 14, Ui.TEXT, false);
        model.addView(modelInfo);
        model.addView(text("Bangla needs a Bangla fine-tuned model (the GitHub workflow bundles one). You can also import any ggml Whisper model; it will be used for every language.", 12, Ui.SUB, false), lp(-2, -2, 0, 6, 0, 8));
        model.addView(Ui.button(this, "Import a model file", false, v -> pickModel()));
        model.addView(Ui.button(this, "Use bundled model", false, v -> { new File(getFilesDir(), "custom.bin").delete(); refreshSettings(); }));
        settingsBox.addView(model);

        LinearLayout q = Ui.section(this, "Default quality");
        LinearLayout row = new LinearLayout(this);
        TextView fast = Ui.chip(this, "Fast", Prefs.quality(this) == 0, null), acc = Ui.chip(this, "Accurate", Prefs.quality(this) == 1, null);
        fast.setOnClickListener(v -> { Prefs.setQuality(this, 0); Ui.setChip(fast, true); Ui.setChip(acc, false); });
        acc.setOnClickListener(v -> { Prefs.setQuality(this, 1); Ui.setChip(fast, false); Ui.setChip(acc, true); });
        row.addView(fast); row.addView(acc);
        q.addView(row);
        q.addView(text("Fast is about real-time on most phones. Accurate uses beam search: slower, better for Bangla and noisy audio.", 12, Ui.SUB, false));
        settingsBox.addView(q);

        LinearLayout store = Ui.section(this, "Storage");
        accessInfo = text("", 14, Ui.TEXT, false);
        store.addView(accessInfo);
        store.addView(text(Storage.root().getPath() + "/", 12, Ui.SUB, false), lp(-2, -2, 0, 4, 0, 6));
        store.addView(Ui.button(this, "Allow file access", false, v -> requestStorage()));
        settingsBox.addView(store);

        LinearLayout bgs = Ui.section(this, "Background running");
        bgInfo = text("", 14, Ui.TEXT, false);
        bgs.addView(bgInfo);
        bgs.addView(text("Captioning and video export keep running when you close the app or turn the screen off. Allow unrestricted battery so the phone does not stop it.", 12, Ui.SUB, false), lp(-2, -2, 0, 6, 0, 8));
        bgs.addView(Ui.button(this, "Allow background use", false, v -> allowBackground()));
        bgs.addView(Ui.button(this, "Notification settings", false, v -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()))));
        settingsBox.addView(bgs);

        LinearLayout about = Ui.section(this, "About");
        about.addView(text("Shobdo 1.0", 15, Ui.TEXT, true));
        about.addView(text("whisper.cpp speech engine · Python by Chaquopy · 100% on-device", 12, Ui.SUB, false), lp(-2, -2, 0, 4, 0, 0));
        settingsBox.addView(about);
    }

    private void refreshSettings() {
        if (modelInfo == null) return;
        File custom = new File(getFilesDir(), "custom.bin");
        modelInfo.setText(custom.exists() ? "Custom model · " + custom.length() / 1000000 + " MB (used for every language)"
                : "General model: bundled ✓\nBangla model: " + (Transcriber.hasBanglaModel(this) ? "bundled ✓" : "not installed ✗ (Bangla will not work)"));
        accessInfo.setText(Storage.hasAccess(this) ? "Access granted ✓" : "Access needed to save into Shobdo folder");
        accessInfo.setTextColor(Storage.hasAccess(this) ? 0xFF7CE3B0 : 0xFFFFB86B);
        boolean battery = ((android.os.PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
        boolean notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        bgInfo.setText((battery ? "Battery: unrestricted ✓" : "Battery: may be limited") + "\n" + (notif ? "Notifications: on ✓" : "Notifications: off"));
        bgInfo.setTextColor(battery && notif ? 0xFF7CE3B0 : 0xFFFFB86B);
    }

    private void allowBackground() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT < 33 || Prefs.askedNotif(this)) return;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        Prefs.setAskedNotif(this);
        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2);
    }

    private void showPage(int p) {
        if (p == page) return;
        View in = p == 0 ? homeScroll : settingsScroll, out = p == 0 ? settingsScroll : homeScroll;
        page = p; nav.select(p);
        out.animate().setStartDelay(0).alpha(0f).translationX(p == 0 ? Ui.dp(this, 30) : -Ui.dp(this, 30)).setDuration(180)
                .withEndAction(() -> { out.setVisibility(View.GONE); out.setAlpha(1f); out.setTranslationX(0); }).start();
        in.setAlpha(0f); in.setTranslationX(p == 0 ? -Ui.dp(this, 30) : Ui.dp(this, 30)); in.setVisibility(View.VISIBLE);
        in.animate().alpha(1f).translationX(0f).setStartDelay(120).setDuration(260).start();
        if (p == 2) refreshSettings(); else refreshList();
    }

    // ===================================================== picking
    private void pickVideo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*");
        startActivityForResult(i, REQ_VIDEO);
    }

    private void pickModel() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        startActivityForResult(i, REQ_MODEL);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_VIDEO) onVideo(uri);
        else if (req == REQ_MODEL) importModel(uri);
    }

    private void askStorage() {
        if (Storage.hasAccess(this) || Prefs.asked(this)) { askNotifications(); return; }
        Prefs.setAsked(this);
        new AlertDialog.Builder(this, R.style.GlassDialog).setTitle("Save to your phone")
                .setMessage("Shobdo saves SRT files and videos to\n" + Storage.root().getPath() + "/\n\nAllow file access to turn this on.")
                .setPositiveButton("Allow", (d, w) -> requestStorage()).setNegativeButton("Later", (d, w) -> askNotifications()).show();
    }

    private void requestStorage() {
        if (Build.VERSION.SDK_INT >= 30)
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
        else requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
    }

    private void importModel(Uri uri) {
        Toast.makeText(this, "Importing model…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream o = new FileOutputStream(new File(getFilesDir(), "custom.bin"))) {
                byte[] buf = new byte[1 << 16]; int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                runOnUiThread(() -> { Toast.makeText(this, "Model ready", Toast.LENGTH_SHORT).show(); refreshSettings(); });
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
        }).start();
    }

    // ===================================================== options sheet
    private void onVideo(Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        String name = "Video";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        } catch (Exception ignored) {}
        final String title = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        showOptions(uri, title);
    }

    private void showOptions(Uri uri, String title) {
        Dialog d = new Dialog(this, R.style.SheetDialog);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_sheet);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 22), Ui.dp(this, 22) + bottomInset);

        View handle = new View(this);
        GradientDrawable hg = new GradientDrawable(); hg.setColor(0x44FFFFFF); hg.setCornerRadius(Ui.dp(this, 3));
        handle.setBackground(hg);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 5)); hl.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(handle, hl);
        box.addView(text("New captions", 22, Ui.TEXT, true), lp(-2, -2, 0, 16, 0, 0));
        TextView vt = text(Storage.cleanTitle(title), 13, Ui.SUB, false); vt.setMaxLines(1); vt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(vt, lp(-2, -2, 0, 2, 0, 6));

        final int[] lang = {0}; final boolean[] roman = {false}; final int[] quality = {Prefs.quality(this)};
        final boolean[] translate = {false}, fillers = {false};

        // language row
        LinearLayout lr = new LinearLayout(this);
        lr.setOrientation(LinearLayout.HORIZONTAL); lr.setGravity(Gravity.CENTER_VERTICAL);
        lr.setBackground(Ui.glass(this, 18)); lr.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12));
        LinearLayout lcol = new LinearLayout(this); lcol.setOrientation(LinearLayout.VERTICAL);
        lcol.addView(text("Language", 11, Ui.DIM, true));
        TextView lv = text(Languages.ALL[0].label, 16, Ui.TEXT, true);
        lcol.addView(lv, lp(-2, -2, 0, 2, 0, 0));
        LinearLayout.LayoutParams lcl = new LinearLayout.LayoutParams(0, -2, 1f);
        lr.addView(lcol, lcl);
        lr.addView(new IconView(this).set(IconView.CHEVRON, IconView.BG_NONE, Ui.SUB), new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        Ui.press(lr);
        box.addView(lr, lp(-1, -2, 0, 10, 0, 4));

        // script segmented
        box.addView(Ui.label(this, "Caption script"));
        LinearLayout sr = new LinearLayout(this);
        TextView sNative = Ui.chip(this, "Native script", true, null), sRoman = Ui.chip(this, "Roman letters", false, null);
        sNative.setOnClickListener(v -> { roman[0] = false; Ui.setChip(sNative, true); Ui.setChip(sRoman, false); });
        sRoman.setOnClickListener(v -> { roman[0] = true; Ui.setChip(sNative, false); Ui.setChip(sRoman, true); });
        sr.addView(sNative); sr.addView(sRoman);
        box.addView(sr);

        // language chooser
        lr.setOnClickListener(v -> {
            String[] labels = new String[Languages.ALL.length];
            for (int i = 0; i < labels.length; i++) labels[i] = Languages.ALL[i].label;
            new AlertDialog.Builder(this, R.style.GlassDialog).setTitle("Language or mix")
                    .setSingleChoiceItems(labels, lang[0], (dlg, which) -> {
                        lang[0] = which; lv.setText(labels[which]);
                        roman[0] = Languages.ALL[which].mixed;
                        Ui.setChip(sNative, !roman[0]); Ui.setChip(sRoman, roman[0]);
                        dlg.dismiss();
                    }).show();
        });

        // quality segmented
        box.addView(Ui.label(this, "Quality"));
        LinearLayout qr = new LinearLayout(this);
        TextView qf = Ui.chip(this, "Fast", quality[0] == 0, null), qa = Ui.chip(this, "Accurate", quality[0] == 1, null);
        qf.setOnClickListener(v -> { quality[0] = 0; Ui.setChip(qf, true); Ui.setChip(qa, false); });
        qa.setOnClickListener(v -> { quality[0] = 1; Ui.setChip(qf, false); Ui.setChip(qa, true); });
        qr.addView(qf); qr.addView(qa);
        box.addView(qr);

        box.addView(Ui.switchRow(this, "Translate to English", false, x -> translate[0] = x), lp(-1, -2, 0, 6, 0, 0));
        box.addView(Ui.switchRow(this, "Remove filler words", false, x -> fillers[0] = x));

        box.addView(Ui.button(this, "Generate captions", true, v -> {
            Languages.Entry e = Languages.ALL[lang[0]];
            Intent i = new Intent(this, TranscribeActivity.class);
            i.setData(uri); i.putExtra("title", title); i.putExtra("lang", e.code); i.putExtra("prompt", e.prompt);
            i.putExtra("mixed", e.mixed); i.putExtra("roman", roman[0]); i.putExtra("translate", translate[0]);
            i.putExtra("fillers", fillers[0]); i.putExtra("quality", quality[0]);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            d.dismiss();
            startActivity(i);
            overridePendingTransition(R.anim.fade_in_up, R.anim.fade_out);
        }), lp(-1, -2, 0, 14, 0, 0));

        d.setContentView(box);
        Window w = d.getWindow();
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.BOTTOM);
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.show();
    }
}
