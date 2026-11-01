package com.tanzirdev.shobdo;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Small UI helpers: dp, press animation, chips, seekbars. */
public class Ui {
    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    /** Soft scale-down while pressed. */
    public static void press(View v) {
        v.setOnTouchListener((x, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN: x.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start(); break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: x.animate().scaleX(1f).scaleY(1f).setDuration(140).start(); break;
            }
            return false;
        });
    }

    public static void setChip(TextView t, boolean sel) {
        t.setSelected(sel);
        t.setTextColor(sel ? 0xFFFFFFFF : 0xFF6B4EFF);
    }

    public static TextView chip(Context c, String text, boolean sel, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(text); t.setTextSize(13);
        t.setBackgroundResource(R.drawable.bg_chip);
        t.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(c, 8), dp(c, 6));
        t.setLayoutParams(lp);
        setChip(t, sel);
        if (l != null) t.setOnClickListener(l);
        press(t);
        return t;
    }

    public static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text); t.setTextSize(12); t.setTextColor(0xFF7A758F);
        t.setPadding(0, dp(c, 14), 0, dp(c, 6));
        return t;
    }

    public static TextView button(Context c, String text, boolean primary, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(text); t.setTextSize(15); t.setGravity(android.view.Gravity.CENTER);
        t.setTextColor(primary ? 0xFFFFFFFF : 0xFF6B4EFF);
        t.setBackgroundResource(primary ? R.drawable.bg_btn : R.drawable.bg_btn_soft);
        t.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 6), 0, dp(c, 6));
        t.setLayoutParams(lp);
        t.setOnClickListener(l);
        press(t);
        return t;
    }

    public interface IntCb { void on(int v); }
    public interface BoolCb { void on(boolean v); }

    public static SeekBar seek(Context c, int max, int value, IntCb cb) {
        SeekBar s = new SeekBar(c);
        s.setMax(max); s.setProgress(value);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) { if (user) cb.on(p); }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        return s;
    }

    public static String clock(long ms) {
        long s = ms / 1000;
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }
}
