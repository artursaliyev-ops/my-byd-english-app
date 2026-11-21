# Whisper quality prototype on one lesson
Type: prototype
Status: resolved

Question: which Whisper setup (whisper.cpp vs mlx-whisper, model size, language=auto vs ru) gives usable segment text and timestamps for `Pimsleur_I_01.mp3` on an M4 Max? Check for hallucinated text in silent pauses and how long one 30-minute lesson takes.

## Answer

Tested on `Pimsleur_I_01.mp3` (29.5 min), Apple M4 Max, `mlx-whisper`, `condition_on_previous_text=False` everywhere. Script: `tools/whisper_proto.py`, outputs in `tools/out/I_01/` (gitignored).

| Config | Time | Result |
|---|---|---|
| turbo, language auto | 167 s (incl. model download) | Detected `ru`; same as turbo_ru |
| turbo, `ru` | 20 s | 40 empty segments, 9 segments > 15 s (sync breaks), drops repeated English drills |
| turbo, `ru` + word_timestamps + `hallucination_silence_threshold=2` | 55 s | Clean timing, but misses some drills and writes English in Cyrillic ("РАСИН") |
| **large-v3, `ru` + word_timestamps + `hallucination_silence_threshold=2`** | 335 s | Best: no empty or overlong segments, English drills kept in Latin |

No hallucinated text in silent pauses with `hallucination_silence_threshold`. Without it, turbo emits empty filler segments.

**Gap, all configs:** Whisper picks one language per file, so pure-English stretches with no Russian (e.g. the opening dialogue at 0:11–0:23) are skipped. Re-transcribing such a gap with `language="en"` recovers it exactly ("Excuse me, miss. Do you understand Russian?" …). Silent learner-answer pauses come back empty, so the English pass is safe to run on every gap.

**Decision:** two-pass pipeline.
1. large-v3 with `language="ru"` and the settings above.
2. For each gap > 3 s between pass-1 segments, cut the clip with ffmpeg and run large-v3 with `language="en"`. Offset its segments into the gap and drop any that duplicate a pass-1 neighbour.

Estimated cost is about 6–7 min per lesson, so about 12 h for all 111 files. Run it overnight; the script must be resumable (ticket 02). Fallback if that's too slow: turbo for pass 2 only.
