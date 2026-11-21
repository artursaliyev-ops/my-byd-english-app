"""Transcribe every Pimsleur mp3 into transcripts/<lesson-id>.json (ticket 02).

Two passes per file (see ticket 01):
  1. large-v3, language=ru, anti-hallucination settings.
  2. Every gap > GAP_S between pass-1 segments is re-run with language=en,
     because Whisper picks one language per file and skips pure-English stretches.

Resumable: files whose JSON already exists are skipped. Writes are atomic.

Usage: tools/.venv/bin/python tools/transcribe.py SOURCES_DIR OUT_DIR [lesson-id ...]
"""
from __future__ import annotations

import json, re, sys, time
from pathlib import Path

import mlx_whisper
import numpy as np
from mlx_whisper.audio import SAMPLE_RATE, load_audio

MODEL = "mlx-community/whisper-large-v3-mlx"
GAP_S = 3.0
MIN_REL_RMS = 0.08  # segment loudness vs. whole file; silence measured ~0.03
OPTS = dict(path_or_hf_repo=MODEL, condition_on_previous_text=False,
            word_timestamps=True, hallucination_silence_threshold=2.0)
LATIN = re.compile(r"[A-Za-z]")
CYRILLIC = re.compile(r"[А-Яа-яЁё]")
# Stock phrases Whisper invents over music or noise; never spoken in Pimsleur.
STOCK = re.compile(r"(?i)thanks? (you )?for watching|subscribe|subtitles by|amara\.org")
THANKS_ONLY = re.compile(r"(?i)(thank you|thanks)[.!]?")


def lesson_id(path: Path) -> str | None:
    m = re.fullmatch(r"Pimsleur_(I{1,3})_(\d+)\.mp3", path.name)
    if m:
        return f"{m[1]}_{int(m[2]):02d}"
    return None


def segs(result, offset=0.0, lang="ru"):
    out = []
    for s in result["segments"]:
        text = s["text"].strip()
        if text:
            out.append(dict(start=round(offset + s["start"], 2), end=round(offset + s["end"], 2),
                            text=text, lang=lang, no_speech=s["no_speech_prob"]))
    return out


def strip_internal(s):
    s.pop("no_speech", None)
    return s


def transcribe(path: Path) -> dict:
    audio = np.array(load_audio(str(path)))
    duration = len(audio) / SAMPLE_RATE
    first = segs(mlx_whisper.transcribe(audio, language="ru", **OPTS))

    # Gaps: before the first segment, between segments, after the last one.
    edges = [0.0] + [x for s in first for x in (s["start"], s["end"])] + [duration]
    gaps = [(a, b) for a, b in zip(edges[::2], edges[1::2]) if b - a > GAP_S]

    file_rms = float(np.sqrt(np.mean(audio ** 2))) or 1.0

    def rel_rms(s):
        c = audio[int(s["start"] * SAMPLE_RATE):int(s["end"] * SAMPLE_RATE)]
        return float(np.sqrt(np.mean(c ** 2))) / file_rms if len(c) else 0.0

    def plausible(s):
        # Whisper invents "Thank you." in pauses: silent ones are caught by loudness;
        # ones over faint background still carry a high no-speech probability.
        loud = rel_rms(s)
        if loud < MIN_REL_RMS:
            return False
        if THANKS_ONLY.fullmatch(s["text"]) and s["no_speech"] > 0.75 and loud < 0.9:
            return False
        return True

    second = []
    for a, b in gaps:
        clip = audio[int(a * SAMPLE_RATE):int(b * SAMPLE_RATE)]
        for s in segs(mlx_whisper.transcribe(clip, language="en", **OPTS), offset=a, lang="en"):
            # Keep only English text that lies inside the gap; Russian bleeding in
            # from a neighbouring segment comes back Cyrillic and is dropped.
            s["end"] = min(s["end"], b)
            if (LATIN.search(s["text"]) and not CYRILLIC.search(s["text"])
                    and not STOCK.search(s["text"])
                    and s["start"] < b - 0.2 and plausible(s)):
                second.append(s)

    merged = sorted((strip_internal(s) for s in first + second), key=lambda s: s["start"])
    return dict(duration=round(duration, 2), model=MODEL, gaps_rechecked=len(gaps), segments=merged)


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    only = set(sys.argv[3:])
    out.mkdir(parents=True, exist_ok=True)
    files = sorted((lesson_id(p), p) for p in src.rglob("*.mp3") if lesson_id(p))
    todo = [(i, p) for i, p in files if (not only or i in only) and not (out / f"{i}.json").exists()]
    print(f"{len(files)} lessons found, {len(todo)} to do", flush=True)
    for n, (lid, path) in enumerate(todo, 1):
        t0 = time.time()
        data = dict(id=lid, source=path.name, **transcribe(path))
        tmp = out / f"{lid}.json.tmp"
        tmp.write_text(json.dumps(data, ensure_ascii=False, indent=1))
        tmp.rename(out / f"{lid}.json")
        en = sum(s["lang"] == "en" for s in data["segments"])
        print(f"[{n}/{len(todo)}] {lid}: {time.time() - t0:.0f}s, {len(data['segments'])} segments "
              f"({en} from en pass, {data['gaps_rechecked']} gaps)", flush=True)


if __name__ == "__main__":
    main()
