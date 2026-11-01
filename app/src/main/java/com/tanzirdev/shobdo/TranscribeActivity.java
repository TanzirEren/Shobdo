package com.tanzirdev.shobdo;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class TranscribeActivity extends AppCompatActivity {
    private ProgressBar bar; private TextView percent, stage;
    private volatile boolean done = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_transcribe);
        bar = findViewById(R.id.bar); percent = findViewById(R.id.percent); stage = findViewById(R.id.stage);
        findViewById(R.id.cancel).setOnClickListener(v -> cancel());

        Intent in = getIntent();
        Uri uri = in.getData();
        Transcriber.Options o = new Transcriber.Options();
        o.lang = in.getStringExtra("lang"); o.prompt = in.getStringExtra("prompt");
        o.roman = in.getBooleanExtra("roman", false); o.translate = in.getBooleanExtra("translate", false);
        o.removeFillers = in.getBooleanExtra("fillers", false); o.maxWords = in.getIntExtra("maxWords", 4);
        if (o.prompt == null) o.prompt = "";
        String title = in.getStringExtra("title");

        new Thread(() -> {
            try {
                Project p = Transcriber.run(this, uri, title, o, (s, pct) -> runOnUiThread(() -> {
                    stage.setText(s); percent.setText(pct + "%"); bar.setProgress(pct);
                }));
                int[] sz = Exporter.videoSize(this, uri);
                p.width = sz[0]; p.height = sz[1];
                Storage.save(this, p);
                done = true;
                runOnUiThread(() -> {
                    startActivity(new Intent(this, EditorActivity.class).putExtra("id", p.id));
                    overridePendingTransition(R.anim.fade_in_up, R.anim.fade_out);
                    finish();
                });
            } catch (Throwable e) {
                done = true;
                runOnUiThread(() -> {
                    if ("cancelled".equals(e.getMessage())) { finish(); return; }
                    new AlertDialog.Builder(this).setTitle("Could not create captions").setMessage(String.valueOf(e.getMessage()))
                            .setPositiveButton("OK", (d, w) -> finish()).setCancelable(false).show();
                });
            }
        }).start();
    }

    private void cancel() { if (!done) { Transcriber.cancel(); stage.setText("Cancelling…"); } }

    @Override public void onBackPressed() { cancel(); }
}
