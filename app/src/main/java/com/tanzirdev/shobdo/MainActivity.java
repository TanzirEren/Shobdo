package com.tanzirdev.shobdo;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView list; private TextView empty, modelInfo;
    private ProjectAdapter adapter;

    private final ActivityResultLauncher<String[]> pickVideo = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) onVideo(uri); });
    private final ActivityResultLauncher<String[]> pickModel = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) importModel(uri); });

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        list = findViewById(R.id.projects); empty = findViewById(R.id.empty); modelInfo = findViewById(R.id.modelInfo);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProjectAdapter(); list.setAdapter(adapter);

        View card = findViewById(R.id.startCard);
        Ui.press(card);
        card.setOnClickListener(v -> pickVideo.launch(new String[]{"video/*"}));
        // gentle breathing animation on the call-to-action
        View btn = findViewById(R.id.startBtn);
        btn.animate().translationX(10f).setDuration(900).withEndAction(new Runnable() {
            boolean fwd = false;
            @Override public void run() { btn.animate().translationX(fwd ? 10f : 0f).setDuration(900).withEndAction(this).start(); fwd = !fwd; }
        }).start();

        TextView mb = findViewById(R.id.modelBtn);
        mb.setOnClickListener(v -> modelDialog());
        askStorage();
    }

    @Override protected void onResume() {
        super.onResume();
        adapter.set(Storage.list(this));
        list.scheduleLayoutAnimation();
        try {
            File custom = new File(getFilesDir(), "custom.bin");
            modelInfo.setText(custom.exists() ? "Model: custom (" + custom.length() / 1000000 + " MB)" : "Model: bundled offline model");
        } catch (Exception ignored) {}
    }

    // ---------- storage permission (for /storage/emulated/0/Shobdo/) ----------
    private void askStorage() {
        if (Storage.hasAccess(this)) return;
        SharedPreferences sp = getSharedPreferences("s", MODE_PRIVATE);
        if (sp.getBoolean("asked", false)) return;
        sp.edit().putBoolean("asked", true).apply();
        new AlertDialog.Builder(this).setTitle("Save to your phone")
                .setMessage("Shobdo saves your SRT files and videos to\n" + Storage.root().getPath() + "/\n\nAllow file access to enable this.")
                .setPositiveButton("Allow", (d, w) -> {
                    if (Build.VERSION.SDK_INT >= 30)
                        startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
                    else requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                }).setNegativeButton("Later", null).show();
    }

    // ---------- pick video -> options ----------
    private void onVideo(Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        String name = "Video";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        } catch (Exception ignored) {}
        final String title = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_options, null);
        Spinner spLang = v.findViewById(R.id.spLang), spScript = v.findViewById(R.id.spScript);
        spLang.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Languages.ALL));
        spScript.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Languages.SCRIPT_MODES));
        spLang.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View x, int pos, long id) { spScript.setSelection(Languages.ALL[pos].romanDefault ? 1 : 0); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        TextView wl = v.findViewById(R.id.wordsLabel); SeekBar sw = v.findViewById(R.id.seekWords);
        sw.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { wl.setText("Words per caption: " + (p + 2)); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        v.findViewById(R.id.go).setOnClickListener(x -> {
            Languages.Entry e = Languages.ALL[spLang.getSelectedItemPosition()];
            Intent i = new Intent(this, TranscribeActivity.class);
            i.setData(uri); i.putExtra("title", title); i.putExtra("lang", e.code); i.putExtra("prompt", e.prompt);
            i.putExtra("roman", spScript.getSelectedItemPosition() == 1);
            i.putExtra("translate", ((CheckBox) v.findViewById(R.id.cbTranslate)).isChecked());
            i.putExtra("fillers", ((CheckBox) v.findViewById(R.id.cbFillers)).isChecked());
            i.putExtra("maxWords", sw.getProgress() + 2);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            sheet.dismiss();
            startActivity(i);
            overridePendingTransition(R.anim.fade_in_up, R.anim.fade_out);
        });
        sheet.setContentView(v); sheet.show();
    }

    // ---------- model import ----------
    private void modelDialog() {
        new AlertDialog.Builder(this).setTitle("Speech model")
                .setMessage("A small multilingual model is bundled. For better accuracy (especially Bangla / Urdu), import a bigger ggml Whisper model file (e.g. ggml-small-q5_1.bin).")
                .setPositiveButton("Import model file", (d, w) -> pickModel.launch(new String[]{"*/*"}))
                .setNeutralButton("Use bundled", (d, w) -> { new File(getFilesDir(), "custom.bin").delete(); onResume(); })
                .setNegativeButton("Close", null).show();
    }

    private void importModel(Uri uri) {
        Toast.makeText(this, "Importing model…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream o = new FileOutputStream(new File(getFilesDir(), "custom.bin"))) {
                byte[] buf = new byte[1 << 16]; int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                runOnUiThread(() -> { Toast.makeText(this, "Model ready", Toast.LENGTH_SHORT).show(); onResume(); });
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
        }).start();
    }

    // ---------- recent projects ----------
    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.VH> {
        private List<Project> items = new java.util.ArrayList<>();
        void set(List<Project> l) { items = l; notifyDataSetChanged(); empty.setVisibility(l.isEmpty() ? View.VISIBLE : View.GONE); }
        class VH extends RecyclerView.ViewHolder {
            TextView t, m, d;
            VH(View v) { super(v); t = v.findViewById(R.id.pTitle); m = v.findViewById(R.id.pMeta); d = v.findViewById(R.id.pDelete); }
        }
        @Override public VH onCreateViewHolder(ViewGroup p, int vt) { return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_project, p, false)); }
        @Override public void onBindViewHolder(VH h, int i) {
            Project p = items.get(i);
            h.t.setText(p.title);
            h.m.setText(p.captions.size() + " captions  •  " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(p.created)));
            Ui.press(h.itemView);
            h.itemView.setOnClickListener(v -> {
                Intent it = new Intent(MainActivity.this, EditorActivity.class).putExtra("id", p.id);
                startActivity(it); overridePendingTransition(R.anim.fade_in_up, R.anim.fade_out);
            });
            h.d.setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this).setMessage("Delete this project?")
                    .setPositiveButton("Delete", (d, w) -> { Storage.delete(MainActivity.this, p.id); set(Storage.list(MainActivity.this)); })
                    .setNegativeButton("Cancel", null).show());
        }
        @Override public int getItemCount() { return items.size(); }
    }
}
