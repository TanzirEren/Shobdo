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

Models (pick when you run the workflow): `base-q5_1` (57 MB, fast, weak Bangla) · **`small-q5_1` (181 MB, default)** · `medium-q5_0` (539 MB, much better Bangla) · `large-v3-turbo-q5_0` (574 MB, best).
You can also import any ggml Whisper model inside the app (Settings -> Speech model).

## Bangla
- Language is detected first from the first spoken 30 s, then transcription runs in that language.
- For Bangla/Hindi/Urdu/Arabic etc. the engine blocks Latin-letter tokens and is primed with a native-script prompt, so output stays in the real script instead of romanised gibberish.
- Pick a Banglish/Hinglish/Urdish mix preset to allow English words in the middle of native speech.
- For the best Bangla use `medium-q5_0` or bigger and the Accurate quality mode.

## Features (28)
1. 100% offline transcription (whisper.cpp via JNI)
2. Word-level timing
3. 30+ languages incl. English, Bangla, Hindi, Arabic, Urdu, Persian, Turkish, Tamil, Nepali…
4. Auto language detection
5. Mixed-language presets: Banglish, Hinglish, Urdish (Urdu+English), Arabizi
6. Roman-script output (Banglish / Hinglish style) via built-in transliteration
7. Translate speech to English (offline)
8. Remove filler words
9. Words-per-caption control (at creation and later)
10. Caption list editor: edit text, retime ±0.1 s, "set to now"
11. Split / merge / delete / add captions
12. Undo / redo (40 steps)
13. Find & replace
14. Live video preview with caption overlay
15. Tap a caption time to seek; list follows playback
16. Playback speed 0.5x–2x
17. 14 style presets
18. 8 fonts, size, outline, shadow, background box + opacity
19. Custom colours (palette + hex) for text, highlight, outline, box
20. Word-by-word highlight
21. 6 animations: none, fade, pop, slide, typewriter, bounce
22. Top / middle / bottom position, UPPERCASE, auto-emoji
23. Export SRT, VTT, TXT + share SRT
24. Export MP4 with burned-in captions (Original / 1080p / 720p / 480p)
25. Auto-saved projects with recent-projects list
26. **Python inside the app (Chaquopy):** ✨ Smart clean — removes repeat loops, fixes capitalisation / spacing / timing overlaps
27. **Python:** 📊 Insights — words per minute, captions too fast to read, longest silence
28. **Python:** ASS karaoke subtitle export (word-by-word colour fill, works in VLC / MX Player / desktop editors)

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
