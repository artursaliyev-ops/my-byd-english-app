"""Ticket 01 prototype: run several Whisper configs on one lesson and save JSON + timing."""
import json, sys, time
from pathlib import Path
import mlx_whisper

AUDIO = sys.argv[1]
OUT = Path(sys.argv[2]); OUT.mkdir(parents=True, exist_ok=True)

CONFIGS = {
    "turbo_auto": dict(path_or_hf_repo="mlx-community/whisper-large-v3-turbo", language=None),
    "turbo_ru": dict(path_or_hf_repo="mlx-community/whisper-large-v3-turbo", language="ru"),
    "large_ru_hst": dict(path_or_hf_repo="mlx-community/whisper-large-v3-mlx", language="ru",
                         word_timestamps=True, hallucination_silence_threshold=2.0),
    "turbo_ru_hst": dict(path_or_hf_repo="mlx-community/whisper-large-v3-turbo", language="ru",
                         word_timestamps=True, hallucination_silence_threshold=2.0),
}
only = sys.argv[3:] or list(CONFIGS)
for name in only:
    t0 = time.time()
    r = mlx_whisper.transcribe(AUDIO, condition_on_previous_text=False, verbose=None, **CONFIGS[name])
    dt = time.time() - t0
    segs = [dict(start=round(s["start"], 2), end=round(s["end"], 2), text=s["text"].strip()) for s in r["segments"]]
    (OUT / f"{name}.json").write_text(json.dumps(dict(seconds=round(dt, 1), language=r.get("language"), segments=segs), ensure_ascii=False, indent=1))
    print(f"{name}: {dt:.0f}s, {len(segs)} segments, lang={r.get('language')}", flush=True)
