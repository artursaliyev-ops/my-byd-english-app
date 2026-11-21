# Batch transcription pipeline → per-lesson JSON
Type: task
Status: resolved
Blocked by: 01

Script that runs the chosen Whisper setup over all 111 mp3 files and writes `transcripts/<lesson-id>.json` with `[{start, end, text}]` segments. It must be resumable (skip files that are already done). Lesson id comes from the filename, e.g. `I_01`, `III_30`, `R_05`.

## Comments

**Scope change (2026-11-21):** Reading Lessons are out of scope (see map, Out of scope). Transcribe only the 90 I/II/III lessons and skip the `R_*` files. That's about 10 h instead of about 12 h.

## Answer

Built `tools/transcribe.py` (two-pass, resumable, atomic writes). Per the user (2026-11-21), only **I_01–I_10** were transcribed for now; the remaining 80 I/II/III files run later (Reading Lessons are out of scope) with the same command:

    caffeinate -i tools/.venv/bin/python tools/transcribe.py sources transcripts

Output: `transcripts/<id>.json` (ids `I_01`, `II_15`, `III_30`), about 45 KB each, with shape
`{id, source, duration, model, gaps_rechecked, segments: [{start, end, text, lang: "ru"|"en"}]}`.
Each lesson takes about 3 min on the M4 Max, so the remaining 80 files need about 4 h.

Filters on the English gap pass, found while checking the first run:
- **Silence:** a segment quieter than 8% of the file's average loudness is dropped. This removed most of the invented "Thank you." lines.
- **Stock phrases:** YouTube-style phrases ("Thank you for watching" etc.) are dropped. They appeared over the intro music.
- **Faint background:** a bare "Thank you."/"Thanks." with `no_speech_prob > 0.75` and loudness below 90% is dropped. In I_10 this cut bare "Thank you." from 31 to 4; the 4 left are loud and fit the dialogue.

Known rough edges, accepted:
- English drills are sometimes written in Cyrillic by the Russian pass ("Рашен").
- 0–2 segments per lesson run longer than 15 s.
