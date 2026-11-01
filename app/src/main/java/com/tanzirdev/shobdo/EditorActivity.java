package com.tanzirdev.shobdo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.*;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import java.io.File;
import java.util.*;

public class EditorActivity extends Activity {
    private Project p;
    private ExoPlayer player;
    private CaptionOverlayView overlay;
    private SeekBar seek;
    private IconView play, bigPlay;
    private TextView time, speed, exportStatus;
    private GradientBarView exportBar;
    private ListView capList;
    private CaptionAdapter adapter;
    private final View[] panels = new View[3];
    private final TextView[] tabViews = new TextView[3];
    private LinearLayout styleBox, exportBox;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private int speedIdx = 2, quality = 0, activeIdx = -1, tab = 0;
    private boolean userSeeking = false, exporting = false, firstFrameDone = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Storage.load(this, getIntent().getStringExtra("id"));
        if (p == null) { finish(); return; }
        setContentView(R.layout.activity_editor);
        View content = findViewById(R.id.content);
        Ui.edgeToEdge(this, findViewById(R.id.root), (top, bot) -> content.setPadding(0, top, 0, bot));

        ((TextView) findViewById(R.id.title)).setText(Storage.cleanTitle(p.title));
        TextView lt = findViewById(R.id.langTag);
        lt.setText(p.lang == null || p.lang.equals("auto") ? "AUTO" : p.lang.toUpperCase());
        lt.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        Ui.setChip(lt, false); lt.setTextColor(Ui.VIOLET2);
        ((IconView) findViewById(R.id.back)).set(IconView.BACK, IconView.BG_GLASS, 0xFFFFFFFF);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        Ui.press(findViewById(R.id.back));

        Uri uri = Uri.parse(p.videoUri);
        if (p.width <= 0) { int[] s = Exporter.videoSize(this, uri); p.width = s[0]; p.height = s[1]; }
        ((AspectRatioFrameLayout) findViewById(R.id.frame)).setAspectRatio(p.width / (float) p.height);

        View stage = findViewById(R.id.stage);
        stage.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(EditorActivity.this, 24)); }
        });
        stage.setClipToOutline(true);

        player = new ExoPlayer.Builder(this).build();
        PlayerView pv = findViewById(R.id.playerView);
        pv.setShutterBackgroundColor(Color.TRANSPARENT);
        pv.setKeepContentOnPlayerReset(true);
        pv.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();

        overlay = findViewById(R.id.overlay);
        bindOverlay();
        seek = findViewById(R.id.seek); play = findViewById(R.id.play); bigPlay = findViewById(R.id.bigPlay);
        time = findViewById(R.id.time); speed = findViewById(R.id.speed);
        play.set(IconView.PLAY, IconView.BG_GRADIENT, 0xFFFFFFFF).scale(0.34f);
        bigPlay.set(IconView.PLAY, IconView.BG_GLASS, 0xFFFFFFFF).scale(0.30f);
        findViewById(R.id.controls).setBackground(Ui.glass(this, 28));
        Ui.tint(seek);
        speed.setBackground(Ui.glass(this, 16)); speed.setTextColor(Ui.VIOLET2);
        speed.setPadding(Ui.dp(this, 11), Ui.dp(this, 6), Ui.dp(this, 11), Ui.dp(this, 6));
        seek.setMax((int) Math.max(1000, p.durationMs));

        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean pl) {
                play.setType(pl ? IconView.PAUSE : IconView.PLAY);
                bigPlay.animate().alpha(pl ? 0f : 1f).setDuration(200).start();
                overlay.invalidate();
            }
            @Override public void onPlaybackStateChanged(int st) {
                if (st == Player.STATE_READY) {
                    if (player.getDuration() != C.TIME_UNSET) seek.setMax((int) player.getDuration());
                    if (!firstFrameDone) {      // show a real frame (some clips start black)
                        firstFrameDone = true;
                        long at = p.captions.isEmpty() ? 100 : p.captions.get(0).start + 250;
                        player.seekTo(Math.max(100, at));
                        overlay.postInvalidateDelayed(120);
                    }
                }
            }
            @Override public void onPositionDiscontinuity(Player.PositionInfo o, Player.PositionInfo n, int r) { overlay.invalidate(); }
        });
        View.OnClickListener toggle = v -> { if (player.isPlaying()) player.pause(); else player.play(); };
        play.setOnClickListener(toggle); stage.setOnClickListener(toggle);
        Ui.press(play);
        speed.setOnClickListener(v -> {
            speedIdx = (speedIdx + 1) % SPEEDS.length;
            player.setPlaybackSpeed(SPEEDS[speedIdx]);
            speed.setText(SPEEDS[speedIdx] + "x");
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean user) { if (user) { player.seekTo(pr); overlay.postInvalidateDelayed(60); } }
            @Override public void onStartTrackingTouch(SeekBar s) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) { userSeeking = false; }
        });

        // segmented tabs
        LinearLayout tabs = findViewById(R.id.tabs);
        tabs.setBackground(Ui.glass(this, 26));
        panels[0] = findViewById(R.id.panelCaptions); panels[1] = findViewById(R.id.panelStyle); panels[2] = findViewById(R.id.panelExport);
        String[] names = {"Captions", "Style", "Export"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView t = new TextView(this);
            t.setText(names[i]); t.setTextSize(14); t.setTypeface(Ui.medium()); t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            t.setOnClickListener(v -> showTab(k));
            tabs.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            tabViews[i] = t;
        }
        styleTabs();
        styleBox = findViewById(R.id.styleBox); exportBox = findViewById(R.id.exportBox);

        capList = findViewById(R.id.capList);
        adapter = new CaptionAdapter();
        capList.setAdapter(adapter);
        capList.setOnItemClickListener((a, v, pos, id) -> editDialog(pos));
        capList.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 24));
        buildTools(); buildStyle(); buildExport();
        WorkService.eListener = exportListener;
        if (WorkService.exporting) {      // re-attach to an export that kept running in the background
            exporting = true; tab = -1; showTab(2);
            exportBar.setVisibility(View.VISIBLE); exportBar.setProgress(WorkService.exportPct);
            exportStatus.setText("Rendering video… " + WorkService.exportPct + "%");
        }
        h.post(tick);
    }

    private void styleTabs() {
        for (int i = 0; i < 3; i++) {
            boolean sel = i == tab;
            tabViews[i].setBackground(sel ? Ui.gradient(this, 22) : null);
            tabViews[i].setTextColor(sel ? 0xFFFFFFFF : Ui.SUB);
        }
    }

    private void bindOverlay() {
        overlay.bind(p.captions, p.style, new CaptionOverlayView.Clock() {
            @Override public long nowMs() { return player.getCurrentPosition(); }
            @Override public boolean playing() { return player.isPlaying(); }
        });
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            long pos = player.getCurrentPosition();
            long dur = player.getDuration() == C.TIME_UNSET ? p.durationMs : player.getDuration();
            time.setText(Ui.clock(pos) + " / " + Ui.clock(dur));
            if (!userSeeking) seek.setProgress((int) pos);
            int idx = -1;
            for (int i = 0; i < p.captions.size(); i++) { Caption c = p.captions.get(i); if (pos >= c.start && pos < c.end) { idx = i; break; } }
            if (idx != activeIdx) {
                activeIdx = idx; adapter.notifyDataSetChanged();
                if (player.isPlaying() && idx >= 0 && tab == 0) capList.smoothScrollToPosition(idx);
            }
            h.postDelayed(this, 120);
        }
    };

    private void showTab(int i) {
        if (i == tab) return;
        tab = i; styleTabs();
        for (int k = 0; k < 3; k++) panels[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
        panels[i].setAlpha(0f); panels[i].setTranslationY(Ui.dp(this, 14));
        panels[i].animate().alpha(1f).translationY(0f).setDuration(260).start();
        if (i == 1) previewSeek();
    }

    /** Make sure the preview shows a caption while styling. */
    private void previewSeek() {
        if (player.isPlaying() || p.captions.isEmpty()) return;
        long pos = player.getCurrentPosition();
        for (Caption c : p.captions) if (pos >= c.start && pos < c.end) { overlay.invalidate(); return; }
        Caption c = p.captions.get(0);
        player.seekTo(c.start + Math.min(500, (c.end - c.start) / 2));
        overlay.postInvalidateDelayed(80);
    }

    // ================= captions tab =================
    private void buildTools() {
        LinearLayout t = findViewById(R.id.capTools);
        t.addView(Ui.chip(this, "+ Add", false, v -> addCaption()));
        t.addView(Ui.chip(this, "Undo", false, v -> undoRedo(true)));
        t.addView(Ui.chip(this, "Redo", false, v -> undoRedo(false)));
        t.addView(Ui.chip(this, "Smart clean", false, v -> smartClean()));
        t.addView(Ui.chip(this, "Find & replace", false, v -> findReplace()));
        t.addView(Ui.chip(this, "Words per line", false, v -> wordsPerCaption()));
        t.addView(Ui.chip(this, "Insights", false, v -> insights()));
        t.addView(Ui.chip(this, "Copy all", false, v -> copyAll()));
    }

    private void snap() {
        undo.push(Caption.listToJson(p.captions).toString());
        while (undo.size() > 40) undo.removeLast();
        redo.clear();
    }

    private void restore(String json) {
        try { p.captions = Caption.listFromJson(new org.json.JSONArray(json)); } catch (Exception e) { return; }
        changed();
    }

    private void undoRedo(boolean isUndo) {
        ArrayDeque<String> from = isUndo ? undo : redo, to = isUndo ? redo : undo;
        if (from.isEmpty()) { Toast.makeText(this, isUndo ? "Nothing to undo" : "Nothing to redo", Toast.LENGTH_SHORT).show(); return; }
        to.push(Caption.listToJson(p.captions).toString());
        restore(from.pop());
    }

    private void changed() {
        Collections.sort(p.captions, (a, b) -> Long.compare(a.start, b.start));
        bindOverlay(); activeIdx = -1;
        adapter.notifyDataSetChanged();
        Storage.save(this, p);
    }

    private void addCaption() {
        snap();
        long pos = player.getCurrentPosition();
        Caption c = new Caption(pos, pos + 2000, "New caption");
        p.captions.add(c);
        changed();
        editDialog(p.captions.indexOf(c));
    }

    private AlertDialog.Builder dialog(String title) { return new AlertDialog.Builder(this, R.style.GlassDialog).setTitle(title); }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setTextColor(Ui.TEXT); e.setHintTextColor(Ui.DIM); e.setTextSize(16);
        e.setBackground(Ui.glass(this, 14));
        e.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = Ui.dp(this, 10);
        e.setLayoutParams(lp);
        return e;
    }

    private void findReplace() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20); box.setPadding(pad, pad / 2, pad, 0);
        EditText f = field("Find"), r = field("Replace with");
        box.addView(f); box.addView(r);
        dialog("Find & replace").setView(box)
            .setPositiveButton("Replace all", (d, w) -> {
                String a = f.getText().toString(), b = r.getText().toString();
                if (a.isEmpty()) return;
                snap(); int n = 0;
                for (Caption c : p.captions) {
                    if (a.contains(" ")) {
                        if (c.text.contains(a)) { c.setText(c.text.replace(a, b)); n++; }
                    } else {
                        boolean hit = false;
                        for (Caption.Word x : c.words) if (x.text.contains(a)) { x.text = x.text.replace(a, b); hit = true; n++; }
                        if (hit) c.syncText();
                    }
                }
                changed();
                Toast.makeText(this, n + " replaced", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Cancel", null).show();
    }

    private void wordsPerCaption() {
        String[] items = {"2 words", "3 words", "4 words", "5 words", "6 words", "8 words", "10 words"};
        int[] vals = {2, 3, 4, 5, 6, 8, 10};
        dialog("Words per caption").setItems(items, (d, i) -> {
            snap(); p.captions = Transcriber.regroup(p.captions, vals[i]); changed();
        }).show();
    }

    /** Python (Chaquopy): fixes repeats, capitalisation, spacing and timing overlaps. */
    private void smartClean() {
        snap();
        Toast.makeText(this, "Cleaning captions…", Toast.LENGTH_SHORT).show();
        final String json = Caption.listToJson(p.captions).toString();
        new Thread(() -> {
            try {
                org.json.JSONObject o = new org.json.JSONObject(PyBridge.call(this, "smart_cleanup", json));
                List<Caption> l = Caption.listFromJson(o.getJSONArray("caps"));
                int n = o.optInt("fixed");
                runOnUiThread(() -> { p.captions = l; changed(); Toast.makeText(this, n + " fixes applied", Toast.LENGTH_SHORT).show(); });
            } catch (Throwable e) {
                runOnUiThread(() -> Toast.makeText(this, "Python error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void insights() {
        final String json = Caption.listToJson(p.captions).toString();
        new Thread(() -> {
            try {
                String r = PyBridge.call(this, "insights", json);
                runOnUiThread(() -> dialog("Caption insights").setMessage(r).setPositiveButton("OK", null).show());
            } catch (Throwable e) {
                runOnUiThread(() -> Toast.makeText(this, "Python error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void saveAss() {
        final String json = Caption.listToJson(p.captions).toString(), st = p.style.toJson().toString();
        final int w = p.width > 0 ? p.width : 1080, hgt = p.height > 0 ? p.height : 1920;
        new Thread(() -> {
            try {
                String ass = PyBridge.call(this, "to_ass", json, w, hgt, st);
                runOnUiThread(() -> saveText("ASS", ".ass", ass));
            } catch (Throwable e) {
                runOnUiThread(() -> exportStatus.setText("ASS export failed: " + e.getMessage()));
            }
        }).start();
    }

    private void copyAll() {
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("captions", SrtWriter.txt(p.captions)));
        Toast.makeText(this, "Transcript copied", Toast.LENGTH_SHORT).show();
    }

    private void timeRow(LinearLayout parent, String name, long[] val) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView out = new TextView(this); out.setTextSize(13); out.setTextColor(Ui.TEXT); out.setTypeface(Ui.medium());
        out.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        Runnable upd = () -> out.setText(name + "  " + SrtWriter.ts(val[0], '.'));
        upd.run();
        row.addView(out);
        String[] lb = {"−0.1", "+0.1", "now"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            row.addView(Ui.chip(this, lb[i], false, v -> {
                if (k == 0) val[0] = Math.max(0, val[0] - 100);
                else if (k == 1) val[0] += 100;
                else val[0] = player.getCurrentPosition();
                upd.run();
            }));
        }
        parent.addView(row);
    }

    private void editDialog(int idx) {
        if (idx < 0 || idx >= p.captions.size()) return;
        Caption c = p.captions.get(idx);
        long[] st = {c.start}, en = {c.end};
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20); box.setPadding(pad, pad / 2, pad, 0);
        EditText et = field("Caption text");
        et.setText(c.text); et.setMinLines(2); et.setTextSize(17);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(et);
        timeRow(box, "Start", st); timeRow(box, "End    ", en);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        AlertDialog[] dlg = new AlertDialog[1];
        Runnable apply = () -> {
            String nt = et.getText().toString().replace('\n', ' ').trim();
            if (nt.isEmpty()) return;
            if (!nt.equals(c.text)) { c.start = st[0]; c.end = Math.max(en[0], st[0] + 200); c.setText(nt); }
            else if (st[0] != c.start || en[0] != c.end) c.retime(st[0], en[0]);
        };
        row.addView(Ui.chip(this, "Split here", false, v -> {
            snap(); apply.run();
            int sel = Math.max(0, et.getSelectionStart());
            String before = et.getText().toString().substring(0, Math.min(sel, et.getText().length())).trim();
            int k = before.isEmpty() ? 0 : before.split("\\s+").length;
            if (k < 1 || k >= c.words.size()) { Toast.makeText(this, "Put the cursor between two words", Toast.LENGTH_SHORT).show(); return; }
            p.captions.add(idx + 1, c.splitAt(k));
            dlg[0].dismiss(); changed();
        }));
        row.addView(Ui.chip(this, "Merge next", false, v -> {
            if (idx + 1 >= p.captions.size()) { Toast.makeText(this, "No next caption", Toast.LENGTH_SHORT).show(); return; }
            snap(); apply.run();
            c.mergeWith(p.captions.remove(idx + 1));
            dlg[0].dismiss(); changed();
        }));
        row.addView(Ui.chip(this, "Delete", false, v -> { snap(); p.captions.remove(idx); dlg[0].dismiss(); changed(); }));
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false); hs.addView(row); box.addView(hs);
        dlg[0] = dialog("Edit caption").setView(box)
            .setPositiveButton("Save", (d, w) -> { snap(); apply.run(); changed(); })
            .setNegativeButton("Cancel", null).create();
        dlg[0].show();
    }

    private class CaptionAdapter extends BaseAdapter {
        @Override public int getCount() { return p.captions.size(); }
        @Override public Object getItem(int i) { return p.captions.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int i, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(EditorActivity.this).inflate(R.layout.item_caption, parent, false);
            Caption c = p.captions.get(i);
            TextView t = v.findViewById(R.id.cTime), x = v.findViewById(R.id.cText);
            t.setText(Ui.clock(c.start)); x.setText(c.text);
            v.setActivated(i == activeIdx);
            t.setOnClickListener(a -> { player.seekTo(c.start + 1); overlay.postInvalidateDelayed(60); });
            return v;
        }
    }

    // ================= style tab =================
    private LinearLayout hrow(ViewGroup parent) {
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(r); parent.addView(hs);
        return r;
    }

    private void styleChanged() { overlay.invalidate(); previewSeek(); Storage.save(this, p); }

    private void buildStyle() {
        styleBox.removeAllViews();
        CaptionStyle s = p.style;

        LinearLayout a = Ui.section(this, "Presets");
        LinearLayout r = hrow(a);
        for (CaptionStyle pr : CaptionStyle.presets()) {
            r.addView(Ui.chip(this, pr.preset, pr.preset.equals(s.preset), v -> {
                boolean em = p.style.emoji; p.style = pr.copy(); p.style.emoji = em;
                bindOverlay(); styleChanged(); buildStyle();
            }));
        }
        styleBox.addView(a);

        LinearLayout t = Ui.section(this, "Text");
        r = hrow(t);
        for (int i = 0; i < CaptionStyle.FONT_NAMES.length; i++) {
            final int k = i;
            r.addView(Ui.chip(this, CaptionStyle.FONT_NAMES[i], s.font == i, v -> { p.style.font = k; styleChanged(); buildStyle(); }));
        }
        t.addView(Ui.label(this, "Size"));
        t.addView(Ui.seek(this, 90, Math.round((s.size - 0.03f) * 1000), v -> { p.style.size = 0.03f + v / 1000f; styleChanged(); }));
        t.addView(Ui.switchRow(this, "UPPERCASE", s.upper, v -> { p.style.upper = v; styleChanged(); }));
        t.addView(Ui.switchRow(this, "Highlight spoken word", s.wordHi, v -> { p.style.wordHi = v; styleChanged(); }));
        t.addView(Ui.switchRow(this, "Auto emoji", s.emoji, v -> { p.style.emoji = v; styleChanged(); }));
        styleBox.addView(t);

        LinearLayout m = Ui.section(this, "Motion and position");
        r = hrow(m);
        for (int i = 0; i < CaptionStyle.ANIM_NAMES.length; i++) {
            final int k = i;
            r.addView(Ui.chip(this, CaptionStyle.ANIM_NAMES[i], s.anim == i, v -> { p.style.anim = k; styleChanged(); buildStyle(); }));
        }
        m.addView(Ui.label(this, "Position"));
        r = hrow(m);
        String[] pos = {"Top", "Middle", "Bottom"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            r.addView(Ui.chip(this, pos[i], s.position == i, v -> { p.style.position = k; styleChanged(); buildStyle(); }));
        }
        styleBox.addView(m);

        LinearLayout c = Ui.section(this, "Colours");
        colorRow(c, "Text", s.textColor, col -> p.style.textColor = col);
        colorRow(c, "Highlight", s.hiColor, col -> p.style.hiColor = col);
        colorRow(c, "Outline", s.strokeColor, col -> p.style.strokeColor = col);
        colorRow(c, "Box", s.bgColor, col -> p.style.bgColor = col);
        styleBox.addView(c);

        LinearLayout o = Ui.section(this, "Outline and box");
        o.addView(Ui.label(this, "Outline width"));
        o.addView(Ui.seek(this, 16, s.strokeW, v -> { p.style.strokeW = v; styleChanged(); }));
        o.addView(Ui.label(this, "Box opacity"));
        o.addView(Ui.seek(this, 255, s.bgAlpha, v -> { p.style.bgAlpha = v; styleChanged(); }));
        o.addView(Ui.switchRow(this, "Shadow", s.shadow, v -> { p.style.shadow = v; styleChanged(); }));
        styleBox.addView(o);

        View sp = new View(this); styleBox.addView(sp, new LinearLayout.LayoutParams(1, Ui.dp(this, 24)));
    }

    private void colorRow(LinearLayout parent, String name, int current, Ui.IntCb cb) {
        parent.addView(Ui.label(this, name));
        LinearLayout r = hrow(parent);
        for (int col : CaptionStyle.PALETTE) {
            View sw = new View(this);
            GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(col);
            boolean sel = col == current;
            g.setStroke(Ui.dp(this, sel ? 3 : 1), sel ? 0xFFFFFFFF : 0x44FFFFFF);
            sw.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36));
            lp.setMargins(0, 0, Ui.dp(this, 10), Ui.dp(this, 4)); sw.setLayoutParams(lp);
            sw.setOnClickListener(v -> { cb.on(col); styleChanged(); buildStyle(); });
            r.addView(sw);
        }
        r.addView(Ui.chip(this, "Custom", false, v -> {
            EditText e = field("#RRGGBB");
            LinearLayout box = new LinearLayout(this); box.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), 0); box.addView(e);
            dialog("Custom colour").setView(box).setPositiveButton("Apply", (d, w) -> {
                try { cb.on(Color.parseColor(e.getText().toString().trim())); styleChanged(); buildStyle(); }
                catch (Exception ex) { Toast.makeText(this, "Invalid colour", Toast.LENGTH_SHORT).show(); }
            }).setNegativeButton("Cancel", null).show();
        }));
    }

    // ================= export tab =================
    private LinearLayout pair(TextView a, TextView b) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        for (TextView t : new TextView[]{a, b}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(t == b ? Ui.dp(this, 5) : 0, Ui.dp(this, 5), t == a ? Ui.dp(this, 5) : 0, Ui.dp(this, 5));
            t.setLayoutParams(lp);
            row.addView(t);
        }
        return row;
    }

    private void buildExport() {
        exportBox.removeAllViews();
        LinearLayout files = Ui.section(this, "Subtitle files");
        files.addView(pair(Ui.button(this, "SRT", true, v -> saveText("SRT", ".srt", SrtWriter.srt(p.captions))),
                           Ui.button(this, "VTT", false, v -> saveText("VTT", ".vtt", SrtWriter.vtt(p.captions)))));
        files.addView(pair(Ui.button(this, "TXT", false, v -> saveText("Text", ".txt", SrtWriter.txt(p.captions))),
                           Ui.button(this, "ASS karaoke", false, v -> saveAss())));
        files.addView(Ui.button(this, "Share SRT", false, v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SrtWriter.srt(p.captions));
            startActivity(Intent.createChooser(i, "Share SRT"));
        }));
        exportBox.addView(files);

        LinearLayout vid = Ui.section(this, "Video with captions");
        LinearLayout r = hrow(vid);
        String[] q = {"Original", "1080p", "720p", "480p"};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            r.addView(Ui.chip(this, q[i], quality == i, v -> { quality = k; buildExport(); }));
        }
        vid.addView(Ui.button(this, "Export video", true, v -> exportVideo()));
        exportBar = new GradientBarView(this); exportBar.setVisibility(exporting ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 8)); bl.topMargin = Ui.dp(this, 8);
        vid.addView(exportBar, bl);
        exportStatus = new TextView(this); exportStatus.setTextSize(13); exportStatus.setTextColor(Ui.SUB);
        exportStatus.setPadding(0, Ui.dp(this, 8), 0, 0);
        vid.addView(exportStatus);
        exportBox.addView(vid);

        TextView note = new TextView(this); note.setTextSize(12); note.setTextColor(Ui.DIM);
        note.setText("Saved to " + Storage.root().getPath() + "/");
        note.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 28));
        exportBox.addView(note);
    }

    private void saveText(String sub, String ext, String content) {
        try {
            File f = Storage.writeText(new File(Storage.outDir(this, sub), Storage.safeName(p.title) + ext), content);
            exportStatus.setText("Saved\n" + f.getPath());
            Toast.makeText(this, "Saved to " + f.getParent(), Toast.LENGTH_LONG).show();
        } catch (Exception e) { exportStatus.setText("Could not save: " + e.getMessage()); }
    }

    private final WorkService.EListener exportListener = new WorkService.EListener() {
        @Override public void onProgress(int pct) {
            runOnUiThread(() -> { exporting = true; exportBar.setVisibility(View.VISIBLE); exportBar.setProgress(pct); exportStatus.setText("Rendering video… " + pct + "%"); });
        }
        @Override public void onDone(File f) {
            runOnUiThread(() -> {
                exporting = false; getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                exportBar.setProgress(100);
                exportBar.postDelayed(() -> exportBar.setVisibility(View.GONE), 700);
                exportStatus.setText("Saved\n" + f.getPath());
                Toast.makeText(EditorActivity.this, "Video saved", Toast.LENGTH_LONG).show();
            });
        }
        @Override public void onFail(String msg) {
            runOnUiThread(() -> {
                exporting = false; getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                exportBar.setVisibility(View.GONE);
                exportStatus.setText("cancelled".equals(msg) ? "Export cancelled" : "Export failed: " + msg);
            });
        }
    };

    private void exportVideo() {
        if (WorkService.exporting) { Toast.makeText(this, "An export is already running", Toast.LENGTH_SHORT).show(); return; }
        if (p.captions.isEmpty()) { Toast.makeText(this, "No captions to export", Toast.LENGTH_SHORT).show(); return; }
        player.pause(); exporting = true;
        Storage.save(this, p);
        exportBar.setVisibility(View.VISIBLE); exportBar.setProgress(0);
        exportStatus.setText("Rendering video. You can leave the app; it keeps running and notifies you.");
        int[] hs = {0, 1080, 720, 480};
        try {
            WorkService.eListener = exportListener;
            WorkService.startExport(this, p.id, hs[quality]);
        } catch (Throwable e) {
            exporting = false; exportBar.setVisibility(View.GONE); exportStatus.setText("Export failed: " + e.getMessage());
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
        if (p != null) Storage.save(this, p);
    }

    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (WorkService.eListener == exportListener) WorkService.eListener = null;
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
