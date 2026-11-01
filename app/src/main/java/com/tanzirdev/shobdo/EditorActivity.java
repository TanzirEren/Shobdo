package com.tanzirdev.shobdo;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.*;

public class EditorActivity extends AppCompatActivity {
    private Project p;
    private ExoPlayer player;
    private CaptionOverlayView overlay;
    private SeekBar seek;
    private TextView play, time, speed, exportStatus;
    private ProgressBar exportBar;
    private RecyclerView capList;
    private CaptionAdapter adapter;
    private View[] panels = new View[3];
    private TextView[] tabViews = new TextView[3];
    private LinearLayout styleBox, exportBox;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private int speedIdx = 2, quality = 0, activeIdx = -1, tab = 0;
    private boolean userSeeking = false, exporting = false, dirty = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Storage.load(this, getIntent().getStringExtra("id"));
        if (p == null) { finish(); return; }
        setContentView(R.layout.activity_editor);

        ((TextView) findViewById(R.id.title)).setText(p.title);
        ((TextView) findViewById(R.id.langTag)).setText(p.lang.equals("auto") ? "auto" : p.lang);
        findViewById(R.id.back).setOnClickListener(v -> finish());

        Uri uri = Uri.parse(p.videoUri);
        if (p.width <= 0) { int[] s = Exporter.videoSize(this, uri); p.width = s[0]; p.height = s[1]; }
        ((AspectRatioFrameLayout) findViewById(R.id.frame)).setAspectRatio(p.width / (float) p.height);

        player = new ExoPlayer.Builder(this).build();
        ((PlayerView) findViewById(R.id.playerView)).setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();

        overlay = findViewById(R.id.overlay);
        bindOverlay();
        seek = findViewById(R.id.seek); play = findViewById(R.id.play); time = findViewById(R.id.time); speed = findViewById(R.id.speed);
        seek.setMax((int) Math.max(1000, p.durationMs));
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean pl) { play.setText(pl ? "❚❚" : "▶"); overlay.invalidate(); }
            @Override public void onPlaybackStateChanged(int st) {
                if (st == Player.STATE_READY && player.getDuration() != C.TIME_UNSET) seek.setMax((int) player.getDuration());
            }
            @Override public void onPositionDiscontinuity(Player.PositionInfo o, Player.PositionInfo n, int r) { overlay.invalidate(); }
        });
        play.setOnClickListener(v -> { if (player.isPlaying()) player.pause(); else player.play(); });
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

        // tabs
        panels[0] = findViewById(R.id.panelCaptions); panels[1] = findViewById(R.id.panelStyle); panels[2] = findViewById(R.id.panelExport);
        LinearLayout tabs = findViewById(R.id.tabs);
        String[] names = {"Captions", "Style", "Export"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            tabViews[i] = Ui.chip(this, names[i], i == 0, v -> showTab(k));
            tabs.addView(tabViews[i]);
        }
        styleBox = findViewById(R.id.styleBox); exportBox = findViewById(R.id.exportBox);

        // captions list + tools
        capList = findViewById(R.id.capList);
        capList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CaptionAdapter(); capList.setAdapter(adapter);
        buildTools(); buildStyle(); buildExport();
        h.post(tick);
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
            time.setText(Ui.clock(pos) + "/" + Ui.clock(dur));
            if (!userSeeking) seek.setProgress((int) pos);
            int idx = -1;
            for (int i = 0; i < p.captions.size(); i++) { Caption c = p.captions.get(i); if (pos >= c.start && pos < c.end) { idx = i; break; } }
            if (idx != activeIdx) {
                adapter.setActive(idx);
                if (player.isPlaying() && idx >= 0 && tab == 0) capList.smoothScrollToPosition(idx);
            }
            h.postDelayed(this, 120);
        }
    };

    private void showTab(int i) {
        tab = i;
        for (int k = 0; k < 3; k++) { panels[k].setVisibility(k == i ? View.VISIBLE : View.GONE); Ui.setChip(tabViews[k], k == i); }
        panels[i].setAlpha(0f); panels[i].animate().alpha(1f).setDuration(220).start();
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
        t.addView(Ui.chip(this, "＋ Add", false, v -> addCaption()));
        t.addView(Ui.chip(this, "↶ Undo", false, v -> undoRedo(true)));
        t.addView(Ui.chip(this, "↷ Redo", false, v -> undoRedo(false)));
        t.addView(Ui.chip(this, "Find & replace", false, v -> findReplace()));
        t.addView(Ui.chip(this, "Words / caption", false, v -> wordsPerCaption()));
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
        bindOverlay(); adapter.setActive(-1); activeIdx = -1;
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

    private void findReplace() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20); box.setPadding(pad, pad / 2, pad, 0);
        EditText f = new EditText(this), r = new EditText(this);
        f.setHint("Find"); r.setHint("Replace with");
        box.addView(f); box.addView(r);
        new AlertDialog.Builder(this).setTitle("Find & replace").setView(box)
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
        new AlertDialog.Builder(this).setTitle("Words per caption").setItems(items, (d, i) -> {
            snap(); p.captions = Transcriber.regroup(p.captions, vals[i]); changed();
        }).show();
    }

    private void copyAll() {
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("captions", SrtWriter.txt(p.captions)));
        Toast.makeText(this, "Transcript copied", Toast.LENGTH_SHORT).show();
    }

    private TextView timeRow(LinearLayout parent, String name, long[] val) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView out = new TextView(this); out.setTextSize(13); out.setTypeface(android.graphics.Typeface.MONOSPACE);
        out.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Runnable upd = () -> out.setText(name + " " + SrtWriter.ts(val[0], '.'));
        upd.run();
        row.addView(out);
        String[] lb = {"−0.1s", "+0.1s", "now"};
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
        return out;
    }

    private void editDialog(int idx) {
        if (idx < 0 || idx >= p.captions.size()) return;
        Caption c = p.captions.get(idx);
        long[] st = {c.start}, en = {c.end};
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20); box.setPadding(pad, pad / 2, pad, 0);
        EditText et = new EditText(this);
        et.setText(c.text); et.setMinLines(2); et.setTextSize(17);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(et);
        timeRow(box, "Start", st); timeRow(box, "End  ", en);
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
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.addView(row); box.addView(hs);
        dlg[0] = new AlertDialog.Builder(this).setTitle("Edit caption").setView(box)
            .setPositiveButton("Save", (d, w) -> { snap(); apply.run(); changed(); })
            .setNegativeButton("Cancel", null).create();
        dlg[0].show();
    }

    private class CaptionAdapter extends RecyclerView.Adapter<CaptionAdapter.VH> {
        int active = -1;
        void setActive(int i) {
            int old = active; active = i; activeIdx = i;
            if (old >= 0) notifyItemChanged(old);
            if (i >= 0) notifyItemChanged(i);
        }
        class VH extends RecyclerView.ViewHolder {
            TextView t, x;
            VH(View v) { super(v); t = v.findViewById(R.id.cTime); x = v.findViewById(R.id.cText); }
        }
        @Override public VH onCreateViewHolder(ViewGroup g, int vt) { return new VH(LayoutInflater.from(g.getContext()).inflate(R.layout.item_caption, g, false)); }
        @Override public void onBindViewHolder(VH v, int i) {
            if (i >= p.captions.size()) return;
            Caption c = p.captions.get(i);
            v.t.setText(Ui.clock(c.start)); v.x.setText(c.text);
            v.itemView.setActivated(i == active);
            v.t.setOnClickListener(x -> { player.seekTo(c.start + 1); overlay.postInvalidateDelayed(60); });
            v.itemView.setOnClickListener(x -> editDialog(v.getBindingAdapterPosition()));
        }
        @Override public int getItemCount() { return p.captions.size(); }
    }

    // ================= style tab =================
    private LinearLayout hrow(ViewGroup parent) {
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(r); parent.addView(hs);
        return r;
    }

    private void styleChanged() { overlay.invalidate(); dirty = true; previewSeek(); }

    private void buildStyle() {
        styleBox.removeAllViews();
        CaptionStyle s = p.style;
        styleBox.addView(Ui.label(this, "STYLE PRESETS"));
        LinearLayout r = hrow(styleBox);
        for (CaptionStyle pr : CaptionStyle.presets()) {
            r.addView(Ui.chip(this, pr.preset, pr.preset.equals(s.preset), v -> {
                boolean em = p.style.emoji; p.style = pr.copy(); p.style.emoji = em;
                bindOverlay(); styleChanged(); buildStyle();
            }));
        }
        styleBox.addView(Ui.label(this, "FONT"));
        r = hrow(styleBox);
        for (int i = 0; i < CaptionStyle.FONT_NAMES.length; i++) {
            final int k = i;
            r.addView(Ui.chip(this, CaptionStyle.FONT_NAMES[i], s.font == i, v -> { p.style.font = k; styleChanged(); buildStyle(); }));
        }
        styleBox.addView(Ui.label(this, "ANIMATION"));
        r = hrow(styleBox);
        for (int i = 0; i < CaptionStyle.ANIM_NAMES.length; i++) {
            final int k = i;
            r.addView(Ui.chip(this, CaptionStyle.ANIM_NAMES[i], s.anim == i, v -> { p.style.anim = k; styleChanged(); buildStyle(); }));
        }
        styleBox.addView(Ui.label(this, "POSITION"));
        r = hrow(styleBox);
        String[] pos = {"Top", "Middle", "Bottom"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            r.addView(Ui.chip(this, pos[i], s.position == i, v -> { p.style.position = k; styleChanged(); buildStyle(); }));
        }
        styleBox.addView(Ui.label(this, "OPTIONS"));
        r = hrow(styleBox);
        r.addView(toggle("UPPERCASE", s.upper, v -> p.style.upper = v));
        r.addView(toggle("Word highlight", s.wordHi, v -> p.style.wordHi = v));
        r.addView(toggle("Shadow", s.shadow, v -> p.style.shadow = v));
        r.addView(toggle("Auto emoji", s.emoji, v -> p.style.emoji = v));

        styleBox.addView(Ui.label(this, "TEXT SIZE"));
        styleBox.addView(Ui.seek(this, 90, Math.round((s.size - 0.03f) * 1000), v -> { p.style.size = 0.03f + v / 1000f; styleChanged(); }));
        styleBox.addView(Ui.label(this, "OUTLINE"));
        styleBox.addView(Ui.seek(this, 16, s.strokeW, v -> { p.style.strokeW = v; styleChanged(); }));
        styleBox.addView(Ui.label(this, "BOX OPACITY"));
        styleBox.addView(Ui.seek(this, 255, s.bgAlpha, v -> { p.style.bgAlpha = v; styleChanged(); }));

        colorRow("TEXT COLOR", s.textColor, c -> p.style.textColor = c);
        colorRow("HIGHLIGHT COLOR", s.hiColor, c -> p.style.hiColor = c);
        colorRow("OUTLINE COLOR", s.strokeColor, c -> p.style.strokeColor = c);
        colorRow("BOX COLOR", s.bgColor, c -> p.style.bgColor = c);
    }

    private TextView toggle(String name, boolean on, Ui.BoolCb cb) {
        TextView[] t = new TextView[1];
        boolean[] state = {on};
        t[0] = Ui.chip(this, name, on, v -> { state[0] = !state[0]; Ui.setChip(t[0], state[0]); cb.on(state[0]); styleChanged(); });
        return t[0];
    }

    private void colorRow(String name, int current, Ui.IntCb cb) {
        styleBox.addView(Ui.label(this, name));
        LinearLayout r = hrow(styleBox);
        for (int col : CaptionStyle.PALETTE) {
            View sw = new View(this);
            GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(col);
            boolean sel = col == current;
            g.setStroke(Ui.dp(this, sel ? 3 : 1), sel ? 0xFF6B4EFF : 0xFFD8D2E8);
            sw.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
            lp.setMargins(0, 0, Ui.dp(this, 10), 0); sw.setLayoutParams(lp);
            sw.setOnClickListener(v -> { cb.on(col); styleChanged(); buildStyle(); });
            r.addView(sw);
        }
        r.addView(Ui.chip(this, "#hex", false, v -> {
            EditText e = new EditText(this); e.setHint("#RRGGBB");
            new AlertDialog.Builder(this).setTitle("Custom colour").setView(e).setPositiveButton("Apply", (d, w) -> {
                try { cb.on(Color.parseColor(e.getText().toString().trim())); styleChanged(); buildStyle(); }
                catch (Exception ex) { Toast.makeText(this, "Invalid colour", Toast.LENGTH_SHORT).show(); }
            }).setNegativeButton("Cancel", null).show();
        }));
    }

    // ================= export tab =================
    private void buildExport() {
        exportBox.removeAllViews();
        exportBox.addView(Ui.label(this, "Saved to " + Storage.root().getPath() + "/"));
        exportBox.addView(Ui.button(this, "Download SRT", true, v -> saveText("SRT", ".srt", SrtWriter.srt(p.captions))));
        exportBox.addView(Ui.button(this, "Download VTT", false, v -> saveText("VTT", ".vtt", SrtWriter.vtt(p.captions))));
        exportBox.addView(Ui.button(this, "Download plain text", false, v -> saveText("Text", ".txt", SrtWriter.txt(p.captions))));
        exportBox.addView(Ui.button(this, "Share SRT", false, v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SrtWriter.srt(p.captions));
            startActivity(Intent.createChooser(i, "Share SRT"));
        }));
        exportBox.addView(Ui.label(this, "VIDEO QUALITY"));
        LinearLayout r = hrow(exportBox);
        String[] q = {"Original", "1080p", "720p", "480p"};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            r.addView(Ui.chip(this, q[i], quality == i, v -> { quality = k; buildExport(); }));
        }
        exportBox.addView(Ui.button(this, "Export video with captions", true, v -> exportVideo()));
        exportBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        exportBar.setMax(100); exportBar.setVisibility(exporting ? View.VISIBLE : View.GONE);
        exportBox.addView(exportBar);
        exportStatus = new TextView(this); exportStatus.setTextSize(13); exportStatus.setPadding(0, Ui.dp(this, 8), 0, 0);
        exportBox.addView(exportStatus);
    }

    private void saveText(String sub, String ext, String content) {
        try {
            File f = Storage.writeText(new File(Storage.outDir(this, sub), Storage.safeName(p.title) + ext), content);
            exportStatus.setText("Saved:\n" + f.getPath());
            Toast.makeText(this, "Saved to " + f.getParent(), Toast.LENGTH_LONG).show();
        } catch (Exception e) { exportStatus.setText("Could not save: " + e.getMessage()); }
    }

    private void exportVideo() {
        if (exporting) return;
        if (p.captions.isEmpty()) { Toast.makeText(this, "No captions to export", Toast.LENGTH_SHORT).show(); return; }
        player.pause(); exporting = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        exportBar.setVisibility(View.VISIBLE); exportBar.setProgress(0); exportStatus.setText("Rendering video… keep the app open");
        int[] hs = {0, 1080, 720, 480};
        try {
            Exporter.export(this, p, hs[quality], new Exporter.Listener() {
                @Override public void onProgress(int pct) { exportBar.setProgress(pct); exportStatus.setText("Rendering video… " + pct + "%"); }
                @Override public void onDone(File f) {
                    exporting = false; getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    exportBar.setVisibility(View.GONE); exportStatus.setText("Saved:\n" + f.getPath());
                    MediaScannerConnection.scanFile(EditorActivity.this, new String[]{f.getPath()}, null, null);
                    Toast.makeText(EditorActivity.this, "Video saved", Toast.LENGTH_LONG).show();
                }
                @Override public void onError(String msg) {
                    exporting = false; getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    exportBar.setVisibility(View.GONE); exportStatus.setText("Export failed: " + msg);
                }
            });
        } catch (Throwable e) {
            exporting = false; exportBar.setVisibility(View.GONE); exportStatus.setText("Export failed: " + e.getMessage());
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (player != null && !exporting) player.pause();
        if (p != null) Storage.save(this, p);
    }

    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
