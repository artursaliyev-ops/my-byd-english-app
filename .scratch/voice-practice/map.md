# Voice practice — map
Label: wayfinder:map

## Destination

A hand-off-ready **spec** at `.scratch/voice-practice/spec.md` for recording the learner's **Attempts** during a lesson and playing each one back next to the speaker's line, so a session can build it without new decisions.

## Notes

- Domain: the Android app in `android/` (Kotlin + Compose + Media3, `PlaybackService.kt` owns playback; transcript panel in `PlayerScreen.kt` / `Transcript.kt`). Today the manifest has no `RECORD_AUDIO`.
- Terms: **Speaking pause** and **Attempt** are in `GLOSSARY.md`. Lesson pattern (I_05): Russian prompt → 2–5 s silence → speaker's English answer; about 280 speaking pauses per lesson by the settled rule in `transcripts/*.json`.
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
- [Find the speaking pauses in a lesson](issues/02-speaking-pause-detection.md): every silence ≥ 1.0 s except Russian → Russian; Russian vs English by alphabet; keep +0.2 s to −0.1 s; compare with the line after, except an echo after the answer is compared with the line before. About 277 pauses and 13 min of voice per lesson. Prototype on branch `prototype/speaking-pause-detection`.
- [Check the microphone on the car](issues/03-car-mic-check.md): record with `VOICE_COMMUNICATION`; the car's AEC + NS cut the lesson by about 20–25 dB and the voice is clear; runs alongside the BYD voice app, no focus change; a recording started from the screen keeps going in the background, one started from the background is muted. Probe on branch `task/car-mic-probe`.
- [Shape the "▶ Me" control and the recording dot](issues/04-attempt-playback-ui.md): chips at the end of the compared line, "▶ Me" and "▶ Echo" when there are two; plays Attempt, 0.4 s, speaker's line with the lesson paused, then back to its place, still paused; red dot by the title, pulsing inside speaking pauses. Prototype on branch `prototype/attempt-playback-ui`.

## Not yet specified

- **Recording format and size**: codec, bitrate, one file per Attempt vs one file per lesson with offsets; budget for 5 lessons × ~280 Attempts ≈ 65 min of kept voice (from the speaking-pause rule). The car records 16 kHz mono PCM from `VOICE_COMMUNICATION`.
- **Writing the spec**: once the tickets above are resolved, assemble `spec.md`.

## Out of scope

- Automatic pronunciation scoring (Whisper/ASR judging right/wrong): ruled out by the user on 2026-11-21.
- Exporting or sharing recordings: ruled out by the user on 2026-11-21.
- A free voice recorder not tied to lessons: ruled out by the user on 2026-11-21.
