package com.tanzirdev.shobdo;

/** JNI bridge to whisper.cpp (C++). Everything runs on-device. */
public class WhisperBridge {
    static { System.loadLibrary("shobdo_jni"); }
    static native long nativeInit(String modelPath);
    static native void nativeFree(long ctx);
    static native int nativeProgress();
    static native void nativeAbort();
    static native String nativeTranscribe(long ctx, float[] audio, String lang, boolean translate, String prompt, int threads);
}
