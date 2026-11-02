# Shobdo — offline captions in every language

Light, clean Android app that turns any video into word-timed captions **fully offline**
(Whisper running on-device through C++), lets you edit them like a pro, and exports
**SRT / VTT / TXT** or a **video with burned-in animated captions**.

Saved files: `/storage/emulated/0/Shobdo/` (`SRT/`, `VTT/`, `Text/`, `Videos/`)

## Build (GitHub Actions)
1. Create a new GitHub repo and upload everything in this folder (keep `.github/`).
2. Open the **Actions** tab -> **Build Shobdo APK** -> **Run workflow** (pick the bundled model).
3. When it finishes, download the **Shobdo-APK** artifact, unzip, install `app-release.apk`.

The workflow clones whisper.cpp (C++), downloads the model with `tools/fetch_model.py` (Python),
then builds with Gradle + NDK. Python 3.13 is bundled into the APK with Chaquopy 17 (free, MIT). The APK is signed with the debug key so it installs directly.

The APK bundles TWO speech models:
- **General model** (`small-q5_1` by default, 181 MB): English, Hindi, Urdu, Arabic and the other languages.
- **Bangla model** (converted automatically in the workflow from `bangla-speech-processing/BanglaASR`, ~180 MB): used whenever the language is Bangla or Banglish.
Type another Hugging Face repo id (a Whisper-small-sized Bengali fine-tune) in the workflow's *bangla_model* field to try a different one, or `none` to skip. If the conversion fails the APK still builds and shows a warning.
You can also import any ggml Whisper model in the app (Settings -> Speech model); it is then used for every language.

## Bangla
Standard Whisper was trained on very little Bangla, so it writes phonetic Latin letters or random scripts instead of Bangla. Shobdo therefore uses a Bengali fine-tuned model for Bangla, and refuses to show captions that come out in the wrong script (it tells you why instead of showing garbage).
Pick **Bangla** or **Banglish** (mixed). For Roman letters ("ami tomake bhalobashi") use the Roman script option.

## Background running
Captioning and video export run in a foreground service, so they keep going when you close the app or turn the screen off. A progress notification (with Cancel) shows while working and a "Captions ready" notification opens the editor. Settings -> Background running lets you allow unrestricted battery use. Some phones (Xiaomi, Oppo, Vivo, Infinix) also need "Autostart / no restrictions" for Shobdo in system settings.

## Design
Dark purple glass UI: animated splash, glass home with floating bottom nav (Home, +, Settings), live-transcript progress screen, glass editor.

## Layout
- `app/src/main/cpp/` — C++ JNI bridge + CMake (whisper.cpp is cloned here by the workflow)
- `app/src/main/java/com/tanzirdev/shobdo/` — Java app code
- `app/src/main/res/` — XML layouts, themes, drawables, animations
- `app/src/main/python/shobdo_py.py` — Python that runs on the phone (Chaquopy)
- `tools/fetch_model.py` — Python build-time model downloader

## Honest notes
- arm64 phones only (almost every phone since 2017).
- Bangla/Urdu accuracy with `base` is modest; use `small` for better results.
- Roman output is rule-based: Bangla and Hindi come out readable, Urdu/Arabic is an approximation (the script has no short vowels).
- Saving to `/storage/emulated/0/Shobdo/` needs "All files access" (the app asks once). Without it, files go to the app's own folder.
- Video export re-encodes on the phone (H.264), so long videos take a while.
