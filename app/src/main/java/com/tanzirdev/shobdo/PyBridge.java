package com.tanzirdev.shobdo;

import android.content.Context;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/** Runs the on-device Python helpers (app/src/main/python/shobdo_py.py) through Chaquopy. Call from a background thread. */
public class PyBridge {
    private static synchronized Python py(Context c) {
        if (!Python.isStarted()) Python.start(new AndroidPlatform(c.getApplicationContext()));
        return Python.getInstance();
    }

    public static String call(Context c, String fn, Object... args) {
        return py(c).getModule("shobdo_py").callAttr(fn, args).toString();
    }
}
