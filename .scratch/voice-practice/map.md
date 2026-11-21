# Voice practice — map
Label: wayfinder:map

## Destination

A hand-off-ready **spec** at `.scratch/voice-practice/spec.md` for recording the learner's **Attempts** during a lesson and playing each one back next to the speaker's line, so a session can build it without new decisions.

## Notes

- Domain: the Android app in `android/` (Kotlin + Compose + Media3, `PlaybackService.kt` owns playback; transcript panel in `PlayerScreen.kt` / `Transcript.kt`). Today the manifest has no `RECORD_AUDIO`.
- Terms: **Speaking pause** and **Attempt** are in `GLOSSARY.md`. Lesson pattern (I_05): Russian prompt → 2–5 s silence → speaker's English answer; about 240 silences ≥ 2 s per lesson in `transcripts/*.json`.
- Settled in charting (2026-11-21, user):
  - Purpose: compare your pronunciation with the speaker's, tied to the lesson moment. Not a free voice memo.
  - Hands-free while driving: the mic records in the background while a lesson plays; only the parts inside speaking pauses are kept, found from transcript timings. The lesson never pauses for you.
  - Listening back happens when parked: transcript lines that have an Attempt get a "▶ Me" control that plays your Attempt, then the speaker's line. No automatic playback during the lesson.
  - Settings toggle "Record my answers", **off** by default; a red dot on the player screen while the mic is on.
  - Retention: per lesson only the latest pass; at most the last 5 lessons; older ones deleted automatically; stored in app-internal storage, never on the SD card.
- Consult `domain-modeling` for any new term. UI follows the UI6 glass style already in `Glass.kt`.

## Decisions so far

<!-- one line per closed ticket -->

- [Find out whether the app can use the DiLink microphone](issues/01-dilink-mic-access.md): likely yes with a normal mic prompt and no focus change; separate microphone foreground service started from the visible screen; echo cancellation and bleed unknown until the car check. Research on branch `research/dilink-mic-access`.

## Not yet specified

- **Speaker bleed**: the car speakers play the lesson while the mic is open, so the speaker's voice may leak into Attempts at pause edges. What to do about it (echo cancellation, trimming edges, ducking) waits on the mic research and the car mic check.
- **When the mic may start**: per the mic research, Android only lets the recorder start while the player screen is visible; a lesson resumed from the wheel in the background would record nothing. Whether that's acceptable, or recording must be armed from the screen, waits on the car mic check.
- **Recording format and size**: codec, bitrate, one file per Attempt vs one file per lesson with offsets; budget for 5 lessons × ~240 Attempts.
- **Writing the spec**: once the tickets above are resolved, assemble `spec.md`.

## Out of scope

- Automatic pronunciation scoring (Whisper/ASR judging right/wrong): ruled out by the user on 2026-11-21.
- Exporting or sharing recordings: ruled out by the user on 2026-11-21.
- A free voice recorder not tied to lessons: ruled out by the user on 2026-11-21.
