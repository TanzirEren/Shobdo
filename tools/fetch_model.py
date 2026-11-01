#!/usr/bin/env python3
"""Downloads a multilingual whisper.cpp model into app assets (build-time only)."""
import os, sys, time, urllib.request

name = sys.argv[1] if len(sys.argv) > 1 else "base-q5_1"
url = f"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-{name}.bin"
out_dir = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "models")
os.makedirs(out_dir, exist_ok=True)
for old in os.listdir(out_dir):
    if old.endswith(".bin"):
        os.remove(os.path.join(out_dir, old))
out = os.path.join(out_dir, "model.bin")

for attempt in range(1, 5):
    try:
        print(f"[{attempt}/4] downloading {url}")
        urllib.request.urlretrieve(url, out)
        size = os.path.getsize(out)
        if size < 10_000_000:
            raise RuntimeError(f"file too small: {size} bytes")
        print(f"OK model.bin = {size/1e6:.1f} MB")
        sys.exit(0)
    except Exception as e:
        print("retry after error:", e)
        time.sleep(5 * attempt)
sys.exit("model download failed")
