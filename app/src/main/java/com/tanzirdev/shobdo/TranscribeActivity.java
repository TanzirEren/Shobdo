package com.tanzirdev.shobdo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;

/** Progress screen. The work itself runs in WorkService, so closing this screen or the app does not stop it. */
public class TranscribeActivity extends Activity {
    private ProgressRingView ring; private StepsView steps; private WaveBarsView wave; private TextView live, elapsed;
    private final Handler h = new Handler(Looper.getMainLooper());
    private String lastLive = "";
    private boolean closed = false;

    private final WorkService.TListener listener = new WorkService.TListener() {
        @Override public void onStep(int step, String text, int pct) { runOnUiThread(() -> applyStep(step, text, pct)); }
        @Override public void onLive(String text) { runOnUiThread(() -> applyLive(text)); }
        @Override public void onDone(Project p) {
            runOnUiThread(() -> {
                ring.setPercent(100); ring.setLabel("Done");
                h.postDelayed(() -> {
                    if (closed) return;
                    startActivity(new Intent(TranscribeActivity.this, EditorActivity.class).putExtra("id", p.id));
                    overridePendingTransition(R.anim.fade_in_up, R.anim.fade_out);
                    finish();
                }, 700);
            });
        }
        @Override public void onFail(String msg) {
            runOnUiThread(() -> {
                if ("cancelled".equals(msg)) { finish(); return; }
                new AlertDialog.Builder(TranscribeActivity.this, R.style.GlassDialog).setTitle("Could not create captions").setMessage(String.valueOf(msg))
                        .setPositiveButton("OK", (d, w) -> finish()).setCancelable(false).show();
            });
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.addView(new GlassBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));

        ScrollView sv = new ScrollView(this); sv.setVerticalScrollBarEnabled(false); sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER_HORIZONTAL);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        Ui.edgeToEdge(this, root, (top, bot) -> col.setPadding(Ui.dp(this, 24), top + Ui.dp(this, 18), Ui.dp(this, 24), bot + Ui.dp(this, 20)));

        col.addView(new WordmarkView(this), new LinearLayout.LayoutParams(-2, Ui.dp(this, 36)));
        ring = new ProgressRingView(this);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(Ui.dp(this, 230), Ui.dp(this, 230)); rl.topMargin = Ui.dp(this, 18);
        col.addView(ring, rl);
        elapsed = new TextView(this); elapsed.setTextColor(Ui.SUB); elapsed.setTextSize(13); elapsed.setTypeface(Ui.medium());
        col.addView(elapsed, new LinearLayout.LayoutParams(-2, -2));

        wave = new WaveBarsView(this); wave.setLevel(0.5f);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(Ui.dp(this, 220), Ui.dp(this, 40)); wl.topMargin = Ui.dp(this, 10);
        col.addView(wave, wl);

        steps = new StepsView(this);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(this, 300), -2); sl.topMargin = Ui.dp(this, 14);
        col.addView(steps, sl);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL); card.setBackground(Ui.glass(this, 22));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 2), Ui.dp(this, 16), Ui.dp(this, 14));
        card.addView(Ui.label(this, "Live transcript"));
        live = new TextView(this); live.setTextSize(15); live.setTextColor(Ui.TEXT); live.setLineSpacing(Ui.dp(this, 3), 1f);
        live.setMinLines(3); live.setMaxLines(4); live.setText("Waiting for the first words…"); live.setAlpha(0.5f);
        card.addView(live);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2); cl.topMargin = Ui.dp(this, 10);
        col.addView(card, cl);

        TextView bg = Ui.button(this, "Keep running in background", true, v -> background());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2); bl.topMargin = Ui.dp(this, 10);
        col.addView(bg, bl);
        col.addView(Ui.button(this, "Cancel", false, v -> cancel()), new LinearLayout.LayoutParams(-1, -2));
        col.addView(smallNote(), new LinearLayout.LayoutParams(-2, -2));
        setContentView(root);

        // start the job once; re-opening this screen only re-attaches to the running job
        if (!WorkService.transcribing) {
            if (getIntent().getData() == null) { finish(); return; }
            WorkService.startTranscribe(this, getIntent());
        } else {
            applyStep(WorkService.lastStep, WorkService.lastText, WorkService.lastPct);
            applyLive(WorkService.lastLive);
        }
        WorkService.tListener = listener;

        h.post(new Runnable() { @Override public void run() {
            if (closed) return;
            elapsed.setText(Ui.clock(System.currentTimeMillis() - WorkService.startedAt)); h.postDelayed(this, 1000);
        }});
    }

    private TextView smallNote() {
        TextView t = new TextView(this);
        t.setText("You can close the app. Shobdo keeps working and notifies you when captions are ready.");
        t.setTextColor(Ui.DIM); t.setTextSize(12); t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), 0);
        return t;
    }

    private void applyStep(int step, String text, int pct) {
        ring.setPercent(pct); ring.setLabel(text);
        steps.setStep(step, text);
        wave.setLevel(step == Transcriber.STEP_LISTEN ? 0.55f + 0.4f * (pct / 100f) : 0.35f);
    }

    private void applyLive(String text) {
        if (text == null || text.trim().isEmpty() || text.equals(lastLive)) return;
        lastLive = text;
        String shown = text.length() > 170 ? "…" + text.substring(text.length() - 170) : text;
        live.setText(shown); live.setAlpha(0.6f); live.animate().alpha(1f).setDuration(260).start();
    }

    private void background() {
        Toast.makeText(this, "Running in background. We'll notify you.", Toast.LENGTH_LONG).show();
        finish();
    }

    private void cancel() {
        if (!WorkService.transcribing) { finish(); return; }
        WorkService.cancelAll(this);
    }

    @Override public void onBackPressed() { background(); }

    @Override protected void onDestroy() {
        closed = true;
        h.removeCallbacksAndMessages(null);
        if (WorkService.tListener == listener) WorkService.tListener = null;
        super.onDestroy();
    }
}
