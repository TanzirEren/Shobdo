package com.tanzirdev.shobdo;

/** JNI bridge to whisper.cpp (C++). Everything runs on-device. */
public class WhisperBridge {
    static { System.loadLibrary("shobdo_jni"); }
    static native long nativeInit(String modelPath);
    static native void nativeFree(long ctx);
    static native int nativeProgress();
    static native void nativeAbort();
    static native byte[] nativePartial();
    static native String nativeDetect(long ctx, float[] audio, int threads);
    static native byte[] nativeTranscribe(long ctx, float[] audio, String lang, boolean translate, String prompt,
                                          int threads, int beam, boolean suppressLatin);

    /** Live text so far, decoded safely (a half-written character at the end is dropped). */
    static String partial() {
        byte[] b = nativePartial();
        if (b == null) return "";
        return new String(b, java.nio.charset.StandardCharsets.UTF_8).replace("\uFFFD", "");
    }
}
