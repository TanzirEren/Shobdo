"""Shobdo on-device Python helpers (run through Chaquopy). Pure standard library, fully offline.

Caption JSON format (same as the Java side):
  [{"s": startMs, "e": endMs, "t": "text", "w": [["word", startMs, endMs], ...]}, ...]
"""
import json
import re

SENT_END = tuple(".?!।؟۔")
FONTS = ["Roboto", "Roboto", "Noto Serif", "Roboto Mono", "Roboto Condensed", "Roboto", "Roboto", "Roboto Black"]


def _words_text(words):
    return " ".join(w[0] for w in words)


def _fix_caption(c):
    words = c.get("w") or []
    if words:
        c["s"] = words[0][1]
        c["e"] = max(c["e"], words[-1][2]) if c.get("e") else words[-1][2]
        c["t"] = _words_text(words)
    return c


# --------------------------------------------------------------------------- smart cleanup
def smart_cleanup(caps_json):
    """Fix common Whisper artefacts. Returns JSON: {"caps": [...], "fixed": N}."""
    caps = json.loads(caps_json)
    fixed = 0
    out = []
    prev_end_sentence = True
    for c in caps:
        words = [list(w) for w in (c.get("w") or [])]
        # 1) collapse runaway repeats: same word 3+ times in a row -> once
        cleaned = []
        i = 0
        while i < len(words):
            j = i
            while j + 1 < len(words) and words[j + 1][0].lower() == words[i][0].lower():
                j += 1
            if j - i + 1 >= 3:
                keep = words[i]
                keep[2] = words[j][2]
                cleaned.append(keep)
                fixed += j - i
            else:
                cleaned.extend(words[i:j + 1])
            i = j + 1
        words = cleaned
        # 2) tidy punctuation spacing inside words, standalone "i" -> "I"
        for w in words:
            t = w[0]
            t2 = re.sub(r"\s+([,.!?;:])", r"\1", t)
            if t2.lower() == "i":
                t2 = "I"
            t2 = re.sub(r"^i('(?:m|ll|d|ve))$", lambda m: "I" + m.group(1), t2)
            if t2 != t:
                fixed += 1
                w[0] = t2
        # 3) capitalise the first Latin letter after a sentence end
        if words and prev_end_sentence and re.match(r"^[a-z]", words[0][0]):
            words[0][0] = words[0][0][0].upper() + words[0][0][1:]
            fixed += 1
        if not words:
            fixed += 1
            continue
        prev_end_sentence = words[-1][0].endswith(SENT_END)
        c["w"] = words
        _fix_caption(c)
        out.append(c)
    # 4) timing sanity: min 500 ms, never overlap the next caption
    out.sort(key=lambda x: x["s"])
    for i, c in enumerate(out):
        if c["e"] - c["s"] < 500:
            c["e"] = c["s"] + 500
            fixed += 1
        if i + 1 < len(out) and c["e"] > out[i + 1]["s"]:
            c["e"] = max(c["s"] + 200, out[i + 1]["s"])
            fixed += 1
    return json.dumps({"caps": out, "fixed": fixed}, ensure_ascii=False)


# --------------------------------------------------------------------------- insights
def insights(caps_json):
    caps = json.loads(caps_json)
    if not caps:
        return "No captions yet."
    n_words = sum(len(c.get("w") or []) for c in caps)
    speech_ms = sum(max(0, c["e"] - c["s"]) for c in caps)
    total_ms = max(1, caps[-1]["e"] - caps[0]["s"])
    wpm = n_words / (speech_ms / 60000.0) if speech_ms else 0
    fast = 0
    longest = caps[0]
    max_gap = 0
    for i, c in enumerate(caps):
        dur = max(1, c["e"] - c["s"]) / 1000.0
        if len(c["t"]) / dur > 20:
            fast += 1
        if len(c["t"]) > len(longest["t"]):
            longest = c
        if i:
            max_gap = max(max_gap, c["s"] - caps[i - 1]["e"])
    lines = [
        "Captions: %d" % len(caps),
        "Words: %d" % n_words,
        "Speaking time: %d:%02d of %d:%02d" % (speech_ms // 60000, speech_ms // 1000 % 60, total_ms // 60000, total_ms // 1000 % 60),
        "Speed: %d words/min" % round(wpm),
        "Too fast to read (>20 chars/sec): %d" % fast,
        "Longest caption: %d characters" % len(longest["t"]),
        "Longest silence: %.1f s" % (max_gap / 1000.0),
    ]
    return "\n".join(lines)


# --------------------------------------------------------------------------- ASS (karaoke) export
def _ass_time(ms):
    ms = max(0, int(ms))
    cs = ms // 10
    return "%d:%02d:%02d.%02d" % (cs // 360000, cs // 6000 % 60, cs // 100 % 60, cs % 100)


def _ass_color(argb, alpha=None):
    a = (argb >> 24) & 0xFF if alpha is None else alpha
    r, g, b = (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF
    return "&H%02X%02X%02X%02X" % (255 - a, b, g, r)


def _signed(v):
    return v - (1 << 32) if v >= (1 << 31) else v


def to_ass(caps_json, width, height, style_json):
    """Karaoke ASS subtitles (word-by-word colour fill) that work in VLC, MX Player, CapCut desktop, etc."""
    caps = json.loads(caps_json)
    st = json.loads(style_json)
    width, height = int(width), int(height)
    base = min(width, height)
    size = max(12, int(base * float(st.get("size", 0.06))))
    tc = _signed(int(st.get("tc", -1)))
    hc = _signed(int(st.get("hc", -10742)))
    sc = _signed(int(st.get("sc", -16777216)))
    bc = _signed(int(st.get("bc", -16777216)))
    ba = int(st.get("ba", 0))
    border_style = 3 if ba > 0 else 1
    outline = max(0, round(size * int(st.get("sw", 6)) / 40.0))
    shadow = round(size * 0.05) if st.get("sh", True) else 0
    align = {0: 8, 1: 5, 2: 2}.get(int(st.get("pos", 2)), 2)
    font = FONTS[int(st.get("font", 0)) % len(FONTS)]
    upper = bool(st.get("up", False))
    hi = bool(st.get("wh", True))
    margin_v = int(height * 0.08)
    out_color = _ass_color(bc, ba) if border_style == 3 else _ass_color(sc)

    lines = [
        "[Script Info]", "ScriptType: v4.00+", "PlayResX: %d" % width, "PlayResY: %d" % height,
        "WrapStyle: 0", "ScaledBorderAndShadow: yes", "",
        "[V4+ Styles]",
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, "
        "Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
        "Style: Default,%s,%d,%s,%s,%s,%s,-1,0,0,0,100,100,0,0,%d,%d,%d,%d,40,40,%d,1" % (
            font, size, _ass_color(hc if hi else tc), _ass_color(tc), out_color, _ass_color(0xAA000000),
            border_style, outline if border_style == 1 else max(8, size // 5), shadow, align, margin_v),
        "", "[Events]", "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
    ]
    for c in caps:
        words = c.get("w") or []
        if not words:
            continue
        start, end = c["s"], c["e"]
        parts = []
        for i, w in enumerate(words):
            nxt = words[i + 1][1] if i + 1 < len(words) else end
            dur_cs = max(1, (max(nxt, w[2]) - w[1]) // 10)
            txt = w[0].upper() if upper else w[0]
            txt = txt.replace("{", "(").replace("}", ")")
            parts.append(("{\\kf%d}" % dur_cs if hi else "") + txt)
        lines.append("Dialogue: 0,%s,%s,Default,,0,0,0,,%s" % (_ass_time(start), _ass_time(end), " ".join(parts)))
    return "\n".join(lines) + "\n"
