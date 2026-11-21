# BYD Pimsleur Player — map

Goal: a native Android app on the BYD Song Plus head unit (DiLink) for listening to and speaking along with the Pimsleur lessons in `sources/`, with a synced transcript on the main screen.

## Notes

- Content: 90 lessons (I 1-30, II 31-60, III 61-90) of mp3 + .doc each, plus 21 Reading Lessons (mp3) and `Pimsleur_Reading_Lesson_Book.pdf`. Total about 653 MB. Lessons run about 30 min, mp3 at 32 kbps.
- The .doc texts have **no timestamps**, so they can't be used for sync.
- `sources/` is gitignored.
- Dev machine: Apple M4 Max, ffmpeg, Homebrew. mlx-whisper is in `tools/.venv` (gitignored). Android toolchain: JDK 17 (`/opt/homebrew/opt/openjdk@17`), SDK at `/opt/homebrew/share/android-commandlinetools`, emulator AVD `byd_dilink` (Android 13, 1920×1080, touch).

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
  - lesson list grouped I/II/III, auto-advance to the next lesson
  - completed-lesson marks
  - resume at the last lesson and position on app start

- **Whisper setup** (ticket 01): mlx-whisper large-v3, Russian pass, then an English pass over gaps > 3 s, anti-hallucination on. About 6–7 min/lesson, about 12 h total. See [01](issues/01-whisper-prototype.md).
- **[Find out DiLink capabilities on the user's BYD Song Plus](issues/03-dilink-capabilities.md)**: 2025+ Song Plus EV, China firmware with English UI, fixed 15.6" landscape screen. minSdk 28 / targetSdk 33, landscape only, install from USB stick, developer mode on (ADB possible). SAF picker access to USB is still unverified.
- **[Android project skeleton](issues/04-android-skeleton.md)**: `android/` builds a debug APK from the CLI (Compose + Media3 MediaSessionService). Audio access goes through the SAF folder picker: the user has seen it in other DiLink apps, and the whole flow works on the emulator. USB visibility on the car is unchecked, so fallbacks stay ready. Lessons must sit in a subfolder, not the volume root.
- **[Folder picker, library scan, lesson list](issues/05-library-and-lessons.md)**: the app asks once for the lesson folder (SAF, persisted read grant), scans up to 4 levels deep for `Pimsleur_I|II|III_NN.mp3`, and shows I/II/III tabs with a 30-tile grid: ✓ completed, dimmed missing. Tap plays from that lesson on, long-press toggles done. The completed set lives in `LibraryStore` (06 should mark on lesson end). The USB fallback isn't built yet.

## Fog

- (nothing right now)

## Out of scope

- **Reading Lessons** (21 mp3 files plus `Pimsleur_Reading_Lesson_Book.pdf`): ruled out by the user on 2026-11-21. The app covers only the 90 audio lessons in I/II/III. No transcripts, no Reading group in the lesson list, no PDF viewer.
