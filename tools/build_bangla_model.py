#!/usr/bin/env python3
"""
Build-time only: converts a Bangla fine-tuned Whisper model (Hugging Face) to a quantised ggml file
and writes it to app/src/main/assets/models/bangla.bin.

Why: vanilla Whisper has very little Bangla training data and cannot transcribe Bangla well.
A model fine-tuned on Bengali speech can.

Usage: python tools/build_bangla_model.py [hf_repo_id]
"""
import os, shutil, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
CPP = os.path.join(ROOT, "app", "src", "main", "cpp", "whisper.cpp")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "models", "bangla.bin")
WORK = os.path.join(ROOT, "build_bangla")
repo = sys.argv[1] if len(sys.argv) > 1 else "bangla-speech-processing/BanglaASR"


def run(cmd, **kw):
    print("+", " ".join(cmd), flush=True)
    subprocess.check_call(cmd, **kw)


def main():
    os.makedirs(WORK, exist_ok=True)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)

    # 1) python deps (CPU torch is enough for conversion)
    run([sys.executable, "-m", "pip", "install", "-q", "torch", "--index-url", "https://download.pytorch.org/whl/cpu"])
    run([sys.executable, "-m", "pip", "install", "-q", "transformers", "numpy", "huggingface_hub", "safetensors"])
    from huggingface_hub import snapshot_download

    # 2) weights + config from the Bangla model, tokenizer files from openai/whisper-small
    #    (fine-tunes keep the original vocabulary, and the converter needs vocab.json + added_tokens.json)
    model_dir = os.path.join(WORK, "model")
    snapshot_download(repo, local_dir=model_dir,
                      allow_patterns=["config.json", "*.safetensors", "pytorch_model*.bin", "pytorch_model.bin.index.json"])
    tok_dir = os.path.join(WORK, "tok")
    snapshot_download("openai/whisper-small", local_dir=tok_dir, allow_patterns=["vocab.json", "added_tokens.json"])
    for f in ("vocab.json", "added_tokens.json"):
        shutil.copy(os.path.join(tok_dir, f), os.path.join(model_dir, f))
    print("model files:", os.listdir(model_dir), flush=True)

    # 3) mel filters live in the openai/whisper repo
    wh = os.path.join(WORK, "whisper-repo")
    if not os.path.isdir(wh):
        run(["git", "clone", "--depth", "1", "https://github.com/openai/whisper", wh])

    # 4) HF -> ggml (f16)
    out_dir = os.path.join(WORK, "ggml")
    os.makedirs(out_dir, exist_ok=True)
    run([sys.executable, os.path.join(CPP, "models", "convert-h5-to-ggml.py"), model_dir, wh, out_dir])
    f16 = os.path.join(out_dir, "ggml-model.bin")
    if not os.path.isfile(f16) or os.path.getsize(f16) < 50_000_000:
        raise RuntimeError("conversion produced no model")

    # 5) quantise with whisper.cpp's own tool (466 MB -> ~180 MB)
    bld = os.path.join(WORK, "quant-build")
    run(["cmake", "-S", CPP, "-B", bld, "-DCMAKE_BUILD_TYPE=Release", "-DWHISPER_BUILD_EXAMPLES=ON",
         "-DWHISPER_BUILD_TESTS=OFF", "-DWHISPER_BUILD_SERVER=OFF"])
    run(["cmake", "--build", bld, "--target", "whisper-quantize", "-j", "4"])
    exe = None
    for dp, _, fs in os.walk(bld):
        if "whisper-quantize" in fs:
            exe = os.path.join(dp, "whisper-quantize")
            break
    if not exe:
        raise RuntimeError("whisper-quantize was not built")
    run([exe, f16, OUT, "q5_1"])
    print("OK bangla.bin = %.1f MB from %s" % (os.path.getsize(OUT) / 1e6, repo))
    shutil.rmtree(WORK, ignore_errors=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("BANGLA MODEL BUILD FAILED:", e, file=sys.stderr)
        sys.exit(1)
