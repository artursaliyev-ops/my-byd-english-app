# Batch transcription pipeline → per-lesson JSON
Type: task
Status: open
Blocked by: 01

Script that runs the chosen Whisper setup over all 111 mp3 files and writes `transcripts/<lesson-id>.json` with `[{start, end, text}]` segments. It must be resumable (skip files that are already done). Lesson id comes from the filename, e.g. `I_01`, `III_30`, `R_05`.
