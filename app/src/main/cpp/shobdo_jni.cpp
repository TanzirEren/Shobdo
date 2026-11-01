// Shobdo JNI bridge: Java <-> whisper.cpp (fully offline speech-to-text)
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <string>
#include <vector>
#include <cstring>
#include "whisper.h"

#define TAG "ShobdoJNI"
static std::atomic<int>  g_progress{0};
static std::atomic<bool> g_abort{false};

static bool abort_cb(void *) { return g_abort.load(); }
static void progress_cb(struct whisper_context *, struct whisper_state *, int p, void *) { g_progress.store(p); }

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativeInit(JNIEnv *env, jclass, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativeFree(JNIEnv *, jclass, jlong h) {
    if (h) whisper_free((whisper_context *) h);
}

JNIEXPORT jint JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativeProgress(JNIEnv *, jclass) { return g_progress.load(); }

JNIEXPORT void JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativeAbort(JNIEnv *, jclass) { g_abort.store(true); }

// Returns one string. Each line: "t0_cs|t1_cs|token_text" (tokens carry their leading space).
// First line: "#lang|<detected language code>"
JNIEXPORT jstring JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativeTranscribe(JNIEnv *env, jclass, jlong h, jfloatArray audio,
        jstring lang, jboolean translate, jstring prompt, jint threads) {
    auto *ctx = (whisper_context *) h;
    if (!ctx) return env->NewStringUTF("#error|no context");
    g_abort.store(false);
    g_progress.store(0);

    jsize n = env->GetArrayLength(audio);
    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);
    const char *l = env->GetStringUTFChars(lang, nullptr);
    const char *pr = env->GetStringUTFChars(prompt, nullptr);
    std::string langs(l), prompts(pr);

    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads = threads;
    wp.language = langs.c_str();
    wp.detect_language = false;   // true would ONLY detect the language; "auto" is handled by wp.language
    wp.translate = translate;
    wp.token_timestamps = true;
    wp.print_progress = false;
    wp.print_realtime = false;
    wp.print_timestamps = false;
    wp.print_special = false;
    wp.suppress_nst = true;
    if (!prompts.empty()) wp.initial_prompt = prompts.c_str();
    wp.progress_callback = progress_cb;
    wp.abort_callback = abort_cb;

    int rc = whisper_full(ctx, wp, samples, n);
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(lang, l);
    env->ReleaseStringUTFChars(prompt, pr);
    if (rc != 0) return env->NewStringUTF(g_abort.load() ? "#error|cancelled" : "#error|whisper_full failed");

    std::string out = "#lang|";
    out += whisper_lang_str(whisper_full_lang_id(ctx));
    out += "\n";
    const whisper_token eot = whisper_token_eot(ctx);
    int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; i++) {
        int nt = whisper_full_n_tokens(ctx, i);
        for (int j = 0; j < nt; j++) {
            whisper_token_data d = whisper_full_get_token_data(ctx, i, j);
            if (d.id >= eot) continue;                    // skip special tokens
            const char *t = whisper_full_get_token_text(ctx, i, j);
            if (!t || !*t) continue;
            out += std::to_string((long long) d.t0) + "|" + std::to_string((long long) d.t1) + "|";
            for (const char *c = t; *c; c++) { if (*c == '\n') out += ' '; else out += *c; }
            out += "\n";
        }
    }
    g_progress.store(100);
    return env->NewStringUTF(out.c_str());
}

}
