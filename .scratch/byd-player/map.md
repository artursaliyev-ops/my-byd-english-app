# BYD Pimsleur Player — map

Goal: a native Android app on the BYD Song Plus head unit (DiLink) for listening to and speaking along with the Pimsleur lessons in `sources/`, with a synced transcript on the main screen.

## Notes

- Content: 90 lessons (I 1-30, II 31-60, III 61-90) of mp3 + .doc each, plus 21 Reading Lessons (mp3) and `Pimsleur_Reading_Lesson_Book.pdf`. Total about 653 MB. Lessons run about 30 min, mp3 at 32 kbps.
- The .doc texts have **no timestamps**, so they can't be used for sync.
- `sources/` is gitignored.
- Dev machine: Apple M4 Max, ffmpeg, Homebrew and Java are installed. mlx-whisper is in `tools/.venv` (gitignored). No Android SDK yet.

## Decisions so far

- **Target device**: install the APK directly on the BYD DiLink screen (landscape). No Android Auto.
- **Audio delivery**: not bundled in the APK. The user copies files to USB or device storage, and on first launch the app asks for the folder (SAF tree picker).
- **Transcript**: generate it with Whisper. The app shows **Whisper's own text** (mixed ru/en) with segment timestamps, not the .doc text. It highlights the current segment and auto-scrolls. Transcript JSON is small enough to bundle in the APK.
- **Stack**: Kotlin + Jetpack Compose + Media3 (ExoPlayer + MediaSession).
- **UI language**: English.
- **Design**: dark theme and large touch targets for use while driving.
- **Player features**:
  - play/pause
  - ±5 s skip
  - prev/next lesson
  - volume
  - speed 0.75x–1.5x
  - A-B repeat
  - tap a transcript line to seek
  - lesson list grouped I/II/III/Reading, auto-advance to the next lesson
  - completed-lesson marks
  - resume at the last lesson and position on app start

- **Whisper setup** (ticket 01): mlx-whisper large-v3, Russian pass, then an English pass over gaps > 3 s, anti-hallucination on. About 6–7 min/lesson, about 12 h total. See [01](issues/01-whisper-prototype.md).
- **[Find out DiLink capabilities on the user's BYD Song Plus](issues/03-dilink-capabilities.md)**: 2025+ Song Plus EV, China firmware with English UI, fixed 15.6" landscape screen. minSdk 28 / targetSdk 33, landscape only, install from USB stick, developer mode on (ADB possible). SAF picker access to USB is still unverified.

## Fog

- Whether the system folder picker (SAF) exists and can see a USB stick. If it can't, how the audio gets to the app. First checked in Android project skeleton.
- How the reading lessons relate to the PDF (show PDF pages? out of scope for v1?).
