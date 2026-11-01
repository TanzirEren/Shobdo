package com.tanzirdev.shobdo;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Design system: dark purple glass. Colours, shapes and small builders shared by every screen. */
public class Ui {
    public static final int BG = 0xFF0B0816, TEXT = 0xFFF5F2FF, SUB = 0xFFA9A3C7, DIM = 0xFF6F6A8F;
    public static final int VIOLET = 0xFF8B5CFF, VIOLET2 = 0xFFB89CFF, PINK = 0xFFFF5FA2;

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    public static Typeface medium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }

    /** Frosted-glass surface: soft white gradient + hairline border. */
    public static GradientDrawable glass(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x2BFFFFFF, 0x0FFFFFFF});
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, 0.8f)), 0x33FFFFFF);
        return g;
    }

    public static GradientDrawable gradient(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF7C4DFF, 0xFFB06BFF});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static void press(View v) {
        v.setOnTouchListener((x, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN: x.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start(); break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: x.animate().scaleX(1f).scaleY(1f).setDuration(160).start(); break;
            }
            return false;
        });
    }

    public static void setChip(TextView t, boolean sel) {
        Context c = t.getContext();
        t.setBackground(sel ? gradient(c, 20) : glass(c, 20));
        t.setTextColor(sel ? 0xFFFFFFFF : SUB);
        t.setSelected(sel);
    }

    public static TextView chip(Context c, String text, boolean sel, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(text); t.setTextSize(13); t.setTypeface(medium()); t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 15), dp(c, 9), dp(c, 15), dp(c, 9));
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
        t.setText(text.toUpperCase()); t.setTextSize(11); t.setTextColor(DIM); t.setTypeface(medium()); t.setLetterSpacing(0.12f);
        t.setPadding(0, dp(c, 12), 0, dp(c, 8));
        return t;
    }

    public static TextView button(Context c, String text, boolean primary, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(text); t.setTextSize(15); t.setTypeface(medium()); t.setGravity(Gravity.CENTER);
        t.setTextColor(primary ? 0xFFFFFFFF : TEXT);
        t.setBackground(primary ? gradient(c, 18) : glass(c, 18));
        t.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 5), 0, dp(c, 5));
        t.setLayoutParams(lp);
        t.setOnClickListener(l);
        press(t);
        return t;
    }

    /** Glass card with a title; add your controls to the returned layout. */
    public static LinearLayout section(Context c, String title) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(glass(c, 22));
        box.setPadding(dp(c, 16), dp(c, 4), dp(c, 16), dp(c, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 12));
        box.setLayoutParams(lp);
        if (title != null) box.addView(label(c, title));
        return box;
    }

    public interface IntCb { void on(int v); }
    public interface BoolCb { void on(boolean v); }

    public static SeekBar seek(Context c, int max, int value, IntCb cb) {
        SeekBar s = new SeekBar(c);
        s.setMax(max); s.setProgress(value);
        tint(s);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) { if (user) cb.on(p); }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        return s;
    }

    public static void tint(SeekBar s) {
        s.setProgressTintList(ColorStateList.valueOf(VIOLET));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        s.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
    }

    public static LinearLayout switchRow(Context c, String text, boolean on, BoolCb cb) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 6), 0, dp(c, 6));
        TextView t = new TextView(c); t.setText(text); t.setTextSize(14); t.setTextColor(TEXT);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(c);
        sw.setChecked(on);
        sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0xFFFFFFFF, SUB}));
        sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{VIOLET, 0x33FFFFFF}));
        sw.setOnCheckedChangeListener((b, v) -> cb.on(v));
        row.addView(t); row.addView(sw);
        return row;
    }

    public interface InsetsCb { void on(int top, int bottom); }

    /** Draw behind the status and navigation bars; report the insets so layouts can pad themselves. */
    public static void edgeToEdge(Activity a, View root, InsetsCb cb) {
        a.getWindow().setStatusBarColor(0);
        a.getWindow().setNavigationBarColor(0);
        if (Build.VERSION.SDK_INT >= 30) a.getWindow().setDecorFitsSystemWindows(false);
        else root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root.setOnApplyWindowInsetsListener((v, in) -> { cb.on(in.getSystemWindowInsetTop(), in.getSystemWindowInsetBottom()); return in; });
        root.requestApplyInsets();
    }

    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
