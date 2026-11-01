package com.tanzirdev.shobdo;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.media3.transformer.Transformer;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Foreground service: captioning and video export keep running when the app is closed or the screen is off.
 * Screens attach through the static listeners; if none is attached a notification reports the result.
 */
public class WorkService extends Service {
    public static final String ACTION_TRANSCRIBE = "shobdo.TRANSCRIBE", ACTION_EXPORT = "shobdo.EXPORT", ACTION_CANCEL = "shobdo.CANCEL";
    private static final String CH_WORK = "shobdo_work", CH_DONE = "shobdo_done";
    private static final int ID_WORK = 11, ID_DONE = 12;

    public interface TListener { void onStep(int step, String text, int pct); void onLive(String text); void onDone(Project p); void onFail(String msg); }
    public interface EListener { void onProgress(int pct); void onDone(File f); void onFail(String msg); }

    public static volatile TListener tListener;
    public static volatile EListener eListener;
    public static volatile boolean transcribing, exporting;
    public static volatile int lastStep, lastPct, exportPct;
    public static volatile String lastText = "", lastLive = "";
    public static volatile long startedAt;

    private static final AtomicBoolean tRunning = new AtomicBoolean(false);
    private final Handler main = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wl;
    private NotificationManager nm;
    private Transformer transformer;
    private long lastNotify = 0;
    private String workTitle = "Shobdo", workText = "";
    private int workPct = 0;

    // ---------------------------------------------------------------- entry points
    public static void startTranscribe(Context c, Intent src) {
        transcribing = true; startedAt = System.currentTimeMillis();
        lastStep = 0; lastPct = 0; lastText = "Starting"; lastLive = "";
        Intent i = new Intent(c, WorkService.class).setAction(ACTION_TRANSCRIBE).setData(src.getData());
        if (src.getExtras() != null) i.putExtras(src.getExtras());
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startForegroundService(i);
    }

    public static void startExport(Context c, String projectId, int height) {
        exporting = true; exportPct = 0;
        c.startForegroundService(new Intent(c, WorkService.class).setAction(ACTION_EXPORT).putExtra("id", projectId).putExtra("h", height));
    }

    public static void cancelAll(Context c) {
        c.startService(new Intent(c, WorkService.class).setAction(ACTION_CANCEL));
    }

    // ---------------------------------------------------------------- lifecycle
    @Override public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH_WORK, "Working in background", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_DONE, "Captions ready", NotificationManager.IMPORTANCE_DEFAULT));
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent in, int flags, int startId) {
        if (in == null || in.getAction() == null) { stopSelf(); return START_NOT_STICKY; }
        String a = in.getAction();
        if (ACTION_CANCEL.equals(a)) {
            Transcriber.cancel();
            if (transformer != null) { try { transformer.cancel(); } catch (Throwable ignored) {} }
            if (exporting) { exporting = false; EListener l = eListener; if (l != null) l.onFail("cancelled"); finishIfIdle(); }
            return START_NOT_STICKY;
        }
        workTitle = ACTION_EXPORT.equals(a) ? "Exporting video" : "Creating captions";
        workText = "Starting"; workPct = 0;
        startFg(buildWork());
        acquireWake();
        if (ACTION_TRANSCRIBE.equals(a)) runTranscribe(in);
        else if (ACTION_EXPORT.equals(a)) runExport(in);
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() { releaseWake(); super.onDestroy(); }

    private void startFg(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID_WORK, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(ID_WORK, n);
    }

    private void acquireWake() {
        if (wl != null && wl.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shobdo:work");
        wl.acquire(3 * 60 * 60 * 1000L);
    }

    private void releaseWake() { try { if (wl != null && wl.isHeld()) wl.release(); } catch (Throwable ignored) {} }

    private void finishIfIdle() {
        main.post(() -> {
            if (!transcribing && !exporting) {
                releaseWake();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });
    }

    // ---------------------------------------------------------------- notifications
    private PendingIntent openIntent() {
        Intent i = new Intent(this, transcribing ? TranscribeActivity.class : MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification buildWork() {
        PendingIntent cancel = PendingIntent.getService(this, 2, new Intent(this, WorkService.class).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CH_WORK)
                .setSmallIcon(R.drawable.ic_stat).setContentTitle(workTitle).setContentText(workText)
                .setProgress(100, workPct, workPct <= 0).setOngoing(true).setOnlyAlertOnce(true)
                .setColor(0xFF8B5CFF).setContentIntent(openIntent())
                .addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat), "Cancel", cancel).build()).build();
    }

    private void pushProgress(String text, int pct) {
        workText = text; workPct = pct;
        long now = System.currentTimeMillis();
        if (now - lastNotify < 700 && pct < 100) return;
        lastNotify = now;
        try { nm.notify(ID_WORK, buildWork()); } catch (Throwable ignored) {}
    }

    private void postDone(String title, String text, Intent open, int req) {
        PendingIntent pi = PendingIntent.getActivity(this, req, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH_DONE).setSmallIcon(R.drawable.ic_stat).setContentTitle(title)
                .setContentText(text).setColor(0xFF8B5CFF).setContentIntent(pi).setAutoCancel(true).build();
        try { nm.notify(ID_DONE, n); } catch (Throwable ignored) {}
    }

    // ---------------------------------------------------------------- transcribe
    private void runTranscribe(Intent in) {
        if (!tRunning.compareAndSet(false, true)) return;
        final Uri uri = in.getData();
        final Transcriber.Options o = new Transcriber.Options();
        o.lang = in.getStringExtra("lang") == null ? "auto" : in.getStringExtra("lang");
        o.prompt = in.getStringExtra("prompt") == null ? "" : in.getStringExtra("prompt");
        o.mixed = in.getBooleanExtra("mixed", false);
        o.roman = in.getBooleanExtra("roman", false); o.translate = in.getBooleanExtra("translate", false);
        o.removeFillers = in.getBooleanExtra("fillers", false); o.quality = in.getIntExtra("quality", 0);
        final String title = in.getStringExtra("title");

        new Thread(() -> {
            try {
                Project p = Transcriber.run(this, uri, title, o, new Transcriber.Callback() {
                    @Override public void onStep(int step, String text, int pct) {
                        lastStep = step; lastText = text; lastPct = pct;
                        pushProgress(text, pct);
                        TListener l = tListener; if (l != null) l.onStep(step, text, pct);
                    }
                    @Override public void onLive(String text) {
                        lastLive = text;
                        TListener l = tListener; if (l != null) l.onLive(text);
                    }
                });
                int[] sz = Exporter.videoSize(this, uri);
                p.width = sz[0]; p.height = sz[1];
                Storage.save(this, p);
                transcribing = false;
                TListener l = tListener;
                if (l != null) l.onDone(p);
                else postDone("Captions ready", p.captions.size() + " captions · " + Storage.cleanTitle(p.title),
                        new Intent(this, EditorActivity.class).putExtra("id", p.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), p.id.hashCode());
            } catch (Throwable e) {
                transcribing = false;
                boolean cancelled = "cancelled".equals(e.getMessage());
                TListener l = tListener;
                if (l != null) l.onFail(e.getMessage());
                else if (!cancelled) postDone("Captioning failed", String.valueOf(e.getMessage()),
                        new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 99);
            } finally {
                tRunning.set(false);
                finishIfIdle();
            }
        }).start();
    }

    // ---------------------------------------------------------------- export (Transformer needs the main thread)
    private void runExport(Intent in) {
        final String id = in.getStringExtra("id");
        final int h = in.getIntExtra("h", 0);
        final Project p = Storage.load(this, id);
        if (p == null) { exporting = false; EListener l = eListener; if (l != null) l.onFail("Project not found"); finishIfIdle(); return; }
        try {
            transformer = Exporter.export(this, p, h, new Exporter.Listener() {
                @Override public void onProgress(int pct) {
                    exportPct = pct; pushProgress("Rendering " + pct + "%", pct);
                    EListener l = eListener; if (l != null) l.onProgress(pct);
                }
                @Override public void onDone(File f) {
                    exporting = false; transformer = null;
                    MediaScannerConnection.scanFile(WorkService.this, new String[]{f.getPath()}, null, null);
                    EListener l = eListener;
                    if (l != null) l.onDone(f);
                    else postDone("Video exported", f.getName(), new Intent(WorkService.this, EditorActivity.class).putExtra("id", p.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), p.id.hashCode() + 1);
                    finishIfIdle();
                }
                @Override public void onError(String msg) {
                    exporting = false; transformer = null;
                    EListener l = eListener;
                    if (l != null) l.onFail(msg);
                    else postDone("Export failed", msg, new Intent(WorkService.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 98);
                    finishIfIdle();
                }
            });
        } catch (Throwable e) {
            exporting = false;
            EListener l = eListener; if (l != null) l.onFail(String.valueOf(e.getMessage()));
            finishIfIdle();
        }
    }
}
