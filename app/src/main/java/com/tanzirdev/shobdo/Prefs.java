package com.tanzirdev.shobdo;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("shobdo", Context.MODE_PRIVATE); }
    public static int quality(Context c) { return sp(c).getInt("quality", 0); }
    public static void setQuality(Context c, int q) { sp(c).edit().putInt("quality", q).apply(); }
    public static boolean asked(Context c) { return sp(c).getBoolean("asked", false); }
    public static void setAsked(Context c) { sp(c).edit().putBoolean("asked", true).apply(); }
    public static boolean askedNotif(Context c) { return sp(c).getBoolean("askedNotif", false); }
    public static void setAskedNotif(Context c) { sp(c).edit().putBoolean("askedNotif", true).apply(); }
}
