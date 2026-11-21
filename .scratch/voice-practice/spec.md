# Voice practice — spec

Record the learner's **Attempts** while a lesson plays, and let them play each one back next to the speaker's line. Hands-free while driving; listening back happens when parked.

Every decision here comes from the map `.scratch/voice-practice/map.md`; each section names the ticket that holds the detail and the reasoning. Terms (**Speaking pause**, **Attempt**) are in `GLOSSARY.md`.

## 1. Scope

In:
- A Settings toggle, a microphone service, cutting Attempts out of speaking pauses, storing them, and "▶ Me" / "▶ Echo" playback in the transcript panel, plus the red recording dot.

Out (ruled out by the user on 2026-11-21):
- Automatic pronunciation scoring.
- Exporting or sharing recordings.
- A free voice recorder not tied to lessons.
- Recordings on the SD card. Attempts live in app-internal storage only.

## 2. Settings and permission

*From "Decide when the mic starts and what a lesson resumed from the wheel does".*

- Settings gets a **"Record my answers"** toggle, **off** by default, and a **"Delete my answers"** button.
- The first time the toggle is turned on, ask for `RECORD_AUDIO` with the normal Android prompt. If it is denied, the toggle turns back off and a short line under it says "Microphone permission not granted".
- **Turning it on** while a lesson plays starts the mic at once (Settings is on screen).
- **Turning it off** closes the mic at once. The Attempt in progress is not saved, and existing Attempts stay playable.
- **"Delete my answers"** asks for confirmation, then deletes every Attempt.

## 3. The microphone service

*From "Find out whether the app can use the DiLink microphone", "Check the microphone on the car" and "Decide when the mic starts…".*

- **Its own foreground service** with `foregroundServiceType="microphone"`, separate from `PlaybackService`. The manifest gains `RECORD_AUDIO` and `FOREGROUND_SERVICE_MICROPHONE`.
- **Source and format:** `AudioRecord` with `MediaRecorder.AudioSource.VOICE_COMMUNICATION`, 16 kHz, mono, 16-bit PCM.
  - On the car this source gets the platform's Qualcomm AEC + NS. They remove about 20–25 dB of the lesson coming from the speakers, and the voice stays clear. Don't add `AcousticEchoCanceler` by hand.
  - This supersedes the research ticket's first guess of `VOICE_RECOGNITION` / `MIC`. On the car, those keep the lesson about as loud as the voice.
- **Coexistence:** the car's own voice app records `VOICE_RECOGNITION` from the same mic all the time, and both ran together without silencing. Recording takes no audio focus: the lesson never paused or ducked. While recording, the car's HAL moves media output to `voice-speaker-stereo`; the user heard no difference.
- **When it starts:** with "Record my answers" on, it starts when a lesson starts playing while the app is on screen (player, lessons list or Settings). It also starts whenever the app comes on screen while a lesson is playing.
  - Android only gives mic access to a foreground service started while the app is visible. Started from the background it is allowed to run, but it is muted (`App op 27 missing, silencing record`).
- **While the lesson is paused,** the service stays running, so a lesson resumed from the steering wheel or the now-playing widget keeps recording. It stops after **30 min** paused, or when the app is closed.
- **Resumed without the mic** (after those 30 min, or after the app was closed): record nothing and show nothing. The mic starts again as soon as the app's screen opens while the lesson plays.
- **Silenced capture:** if `AudioRecordingConfiguration.isClientSilenced()` turns true, treat the audio as silence (see §5, "Silence").

## 4. Speaking pauses

*From "Find the speaking pauses in a lesson".*

Computed from the bundled transcript (`assets/transcripts/<id>.json`) of the current lesson. The pure functions `classify` / `findSilences` in `tools/prototype-speaking-pauses.html` (branch `prototype/speaking-pause-detection`) are the reference.

**Language:** a line is **Russian** if it contains any Cyrillic letter, otherwise **English**. Never use Whisper's `lang` tag.

**A silence is a speaking pause** when all three hold:
- the gap between two consecutive segments is **≥ 1.0 s**;
- it is not Russian → Russian;
- at least **0.5 s** is left after trimming.

**Kept window:** from **0.2 s after the line before ends** to **0.1 s before the next line starts**, in lesson time.

**Kind and compared line:**

| Before → after | Kind | Compared with |
|---|---|---|
| Russian → English | answer | line after |
| English → same English (said twice) | answer | line after |
| English → different English | answer | line after |
| English → Russian | echo | line before |

Expect about 277 speaking pauses (245–302) and about 13 min of kept voice per lesson.

**Two Attempts per line:** one English line can carry both an answer Attempt (the pause before it) and an echo Attempt (the pause after it).

## 5. Cutting an Attempt

*From "Decide what a lesson pass means when the learner jumps around".*

- **What is written:** the mic runs continuously while the service is up, but only audio inside a kept window is ever written. Everything else is discarded in memory.
- **Complete pass required:** an Attempt is kept only if the **whole kept window played without a seek**. A seek, a ±5 s skip (from the screen or the media buttons), a lesson change, or starting "▶ Me" inside the window discards it.
- **Pausing inside a window** (Pimsleur's "pause if you need more time"):
  - Keep capturing while the lesson is paused, up to **10 s** of paused audio.
  - On resume, continue to the window's end.
  - The Attempt is the heard part plus the paused audio.
  - Only a seek or a lesson change counts as an interruption.
- **Speed:** recording works at any speed. Windows are in lesson time.
- **Silence:** an Attempt with no voice is not saved and does not replace an older one.
  - Starting value for "voice": at least 300 ms of 100 ms frames above −50 dBFS. Tune it on the car. With `VOICE_COMMUNICATION` the car's noise floor was about −70 dBFS and the voice p90 about −36 dBFS.
- **Replacement:** each speaking pause keeps only its **newest** voiced Attempt. A new one overwrites the old one; pauses not answered again keep theirs. There is no lesson-wide "pass", and one lesson can hold Attempts from different days.

## 6. Storage

*From "Choose how Attempts are stored".*

- **Format:** AAC-LC, 32 kbit/s, 16 kHz mono, `.m4a`, one file per Attempt (MediaCodec + MediaMuxer).
  - Budget: 5 lessons ≈ 65 min ≈ 15 MB.
- **Layout:** one folder per lesson under app-internal storage, named by transcript id: `files/attempts/I_05/`.
  - Each file is named by kind and the speaking pause's start in lesson ms, zero-padded: `answer-0123450.m4a`, `echo-0125600.m4a`.
  - Write to a temp file and rename over the old one, so a crash never leaves half an Attempt.
- **No database.** The folder and file names are the index.
- **Retention:** keep the **5 lessons with the most recently recorded Attempts** (the folders with the newest file).
  - When a 6th lesson gets its first Attempt, delete the oldest folder.
  - Opening or listening without recording doesn't count.
- **Transcript changes:** each lesson folder holds a short fingerprint (a hash) of the transcript its Attempts were cut against. If the bundled transcript no longer matches, delete that folder.

## 7. "▶ Me" and "▶ Echo" in the transcript panel

*From "Shape the "▶ Me" control and the recording dot". Prototype: branch `prototype/attempt-playback-ui`, variant A.*

- **Chips:** a glass chip sits at the **right end of the line the Attempt is compared with**; the line text wraps to make room.
  - **"▶ Me"** is the answer Attempt and **"▶ Echo"** is the echo Attempt.
  - Both show when both exist, Me first.
  - Lines without an Attempt (Russian lines, and pauses where the learner stayed silent) show nothing extra.
  - No date or age is shown.
- **Tapping a chip:**
  1. The lesson pauses.
  2. The Attempt plays, then a **0.4 s** gap, then the speaker's line from the lesson audio (segment start to end, at the current speed).
  3. The lesson goes back to where it was and **stays paused**.
- **While it plays,** the chip turns blue and reads "■ Me…" / "■ Echo…", then "■ Speaker…".
  - Tapping it again, another chip, a transcript line or any transport control stops playback and restores the position.
- The playback position is restored by seeking, so any Attempt being cut is discarded (§5).
- The style is UI6 glass: `GlassButton` in `Glass.kt`, blue when active.

## 8. The recording dot

*From "Shape the "▶ Me" control…" and "Decide when the mic starts…".*

- **Where:** a red dot to the right of the lesson title on the player screen.
- **States:**
  - **steady red:** the mic is recording (lesson playing, setting on);
  - **pulsing red:** inside a speaking pause's kept window (audio being kept), at about a 0.5 s rate;
  - **grey:** the lesson is paused or "▶ Me" is playing;
  - **hidden:** the setting is off.
- There is no "mic muted" state: when the screen is visible, the mic always runs.

## 9. Not tested on the car

These cases were never tried on the car. Check them after the first build:
- the screen turned off while recording;
- a phone call while recording (no phone was paired);
- road noise while driving;
- the silence threshold in §5.
