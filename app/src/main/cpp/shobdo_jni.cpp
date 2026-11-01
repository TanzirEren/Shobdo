// Shobdo JNI bridge: Java <-> whisper.cpp (fully offline speech-to-text)
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cmath>
#include <mutex>
#include <string>
#include <vector>
#include "whisper.h"

static std::atomic<int>  g_progress{0};
static std::atomic<bool> g_abort{false};
static std::atomic<bool> g_suppress{false};
static std::vector<char> g_latin_mask;          // vocab ids that are pure ASCII-letter tokens
static std::mutex g_mu;
static std::string g_partial;

static bool abort_cb(void *) { return g_abort.load(); }
static void progress_cb(struct whisper_context *, struct whisper_state *, int p, void *) { g_progress.store(p); }

// Fast replacement for regex suppression: block Latin-only tokens so Bangla/Hindi/Urdu/Arabic stay in native script.
static void logits_cb(struct whisper_context *, struct whisper_state *, const whisper_token_data *, int, float *logits, void *) {
    if (!g_suppress.load()) return;
    const size_t n = g_latin_mask.size();
    for (size_t i = 0; i < n; i++) if (g_latin_mask[i]) logits[i] = -INFINITY;
}

static void segment_cb(struct whisper_context *ctx, struct whisper_state *, int n_new, void *) {
    int n = whisper_full_n_segments(ctx);
    std::lock_guard<std::mutex> lk(g_mu);
    for (int i = n - n_new; i < n; i++) {
        if (i < 0) continue;
        g_partial += whisper_full_get_segment_text(ctx, i);
        g_partial += " ";
    }
}

static void build_mask(whisper_context *ctx) {
    int nv = whisper_n_vocab(ctx);
    whisper_token eot = whisper_token_eot(ctx);
    g_latin_mask.assign(nv, 0);
    for (int id = 0; id < nv && id < eot; id++) {
        const char *s = whisper_token_to_str(ctx, id);
        if (!s || !*s) continue;
        bool letter = false, ok = true;
        for (const unsigned char *c = (const unsigned char *) s; *c; c++) {
            if (*c >= 0x80) { ok = false; break; }
            if ((*c >= 'A' && *c <= 'Z') || (*c >= 'a' && *c <= 'z')) letter = true;
        }
        if (ok && letter) g_latin_mask[id] = 1;
    }
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativeInit(JNIEnv *env, jclass, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    if (ctx) build_mask(ctx);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativeFree(JNIEnv *, jclass, jlong h) { if (h) whisper_free((whisper_context *) h); }

JNIEXPORT jint JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativeProgress(JNIEnv *, jclass) { return g_progress.load(); }

JNIEXPORT void JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativeAbort(JNIEnv *, jclass) { g_abort.store(true); }

JNIEXPORT jstring JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativePartial(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lk(g_mu);
    return env->NewStringUTF(g_partial.c_str());
}

// Detects the spoken language from a short audio window (no decoding).
JNIEXPORT jstring JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativeDetect(JNIEnv *env, jclass, jlong h, jfloatArray audio, jint threads) {
    auto *ctx = (whisper_context *) h;
    if (!ctx) return env->NewStringUTF("en");
    g_abort.store(false);
    jsize n = env->GetArrayLength(audio);
    jfloat *s = env->GetFloatArrayElements(audio, nullptr);
    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads = threads;
    wp.language = "auto";
    wp.detect_language = true;          // detect only, then return
    wp.print_progress = wp.print_realtime = wp.print_timestamps = wp.print_special = false;
    wp.abort_callback = abort_cb;
    int rc = whisper_full(ctx, wp, s, n);
    env->ReleaseFloatArrayElements(audio, s, JNI_ABORT);
    if (rc != 0) return env->NewStringUTF("en");
    return env->NewStringUTF(whisper_lang_str(whisper_full_lang_id(ctx)));
}

// Output: first line "#lang|xx", then one line per token: "t0_cs|t1_cs|token_text".
JNIEXPORT jstring JNICALL
Java_com_tanzirdev_shobdo_WhisperBridge_nativeTranscribe(JNIEnv *env, jclass, jlong h, jfloatArray audio,
        jstring lang, jboolean translate, jstring prompt, jint threads, jint beam, jboolean suppressLatin) {
    auto *ctx = (whisper_context *) h;
    if (!ctx) return env->NewStringUTF("#error|no context");
    g_abort.store(false);
    g_progress.store(0);
    g_suppress.store(suppressLatin);
    { std::lock_guard<std::mutex> lk(g_mu); g_partial.clear(); }

    jsize n = env->GetArrayLength(audio);
    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);
    const char *l = env->GetStringUTFChars(lang, nullptr);
    const char *pr = env->GetStringUTFChars(prompt, nullptr);
    std::string langs(l), prompts(pr);

    whisper_full_params wp = whisper_full_default_params(beam > 1 ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    wp.n_threads = threads;
    wp.language = langs.c_str();
    wp.detect_language = false;          // true would ONLY detect; "auto" is handled by wp.language
    wp.translate = translate;
    wp.token_timestamps = true;
    wp.print_progress = wp.print_realtime = wp.print_timestamps = wp.print_special = false;
    wp.suppress_nst = true;
    if (beam > 1) wp.beam_search.beam_size = beam; else wp.greedy.best_of = 1;
    wp.no_context = (beam <= 1);
    if (!prompts.empty()) wp.initial_prompt = prompts.c_str();
    wp.progress_callback = progress_cb;
    wp.abort_callback = abort_cb;
    wp.new_segment_callback = segment_cb;
    wp.logits_filter_callback = logits_cb;

    int rc = whisper_full(ctx, wp, samples, n);
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(lang, l);
    env->ReleaseStringUTFChars(prompt, pr);
    g_suppress.store(false);
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
            if (d.id >= eot) continue;
            const char *t = whisper_full_get_token_text(ctx, i, j);
            if (!t || !*t) continue;
            out += std::to_string((long long) d.t0) + "|" + std::to_string((long long) d.t1) + "|";
            for (const char *c = t; *c; c++) out += (*c == '\n') ? ' ' : *c;
            out += "\n";
        }
    }
    g_progress.store(100);
    return env->NewStringUTF(out.c_str());
}

}
