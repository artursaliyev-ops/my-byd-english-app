# Synced transcript panel
Type: task
Status: resolved
Blocked by: 02, 06

Load the bundled transcript JSON for the current lesson. Highlight the active segment and auto-scroll to it; tap a segment to seek. Show the panel next to the player on the landscape screen.

## Comments

**From Main player screen and resume (2026-11-21):** Fill `TranscriptArea` in `PlayerScreen.kt` (right ~54% of the screen, `surface` background). The screen already polls the controller every 250 ms into `PlayerUi.positionMs`, so use that for the active segment. Use `controller.seekTo` for tap-to-seek. The current lesson number is `mediaId` of the current item. The bundled JSON can be keyed by it: I_01 = 1, II_01 = 31.

## Answer

Built in `android/` and checked on the `byd_dilink` emulator (2026-11-21). 11 JVM tests pass (new: `TranscriptTest`).

- **Bundling**: a Gradle `copyTranscripts` Sync task (runs before `preBuild`) copies the repo's `transcripts/*.json` into `assets/transcripts/`. The 80 transcripts still to come land in the APK on the next build, with no code change. Ten files add about 450 KB.
- **Loading** (`Transcript.kt`): `transcriptId(n)` maps course number to file id (1 → `I_01`, 31 → `II_01`, 90 → `III_30`). The JSON is parsed with `org.json` off the main thread whenever the lesson changes. A lesson with no file keeps the old message: "Transcript isn't available for this lesson yet."
- **Active segment**: the last segment that has started (binary search over the 250 ms position poll). In a pause between segments the earlier one stays lit, and nothing is lit before the first segment.
- **Panel**: a `LazyColumn` in the right ~54%. Each row shows the start time and the text at 26 sp, with rows at least 64 dp tall as touch targets. English lines are bright and medium weight, Russian narration is dimmed, and the active line has a `primary` background.
- **Auto-scroll**: keeps one earlier line above the active one, so the reader sees what came before. If the user drags the list, following pauses for 6 s and then picks up again.
- **Tap a line**: seeks to its start and plays if paused (the playback-store logic in the service saves the resume point as before). Following picks up right away.
- **Checked on the emulator**: Lesson 35 (no transcript) shows the message. Lesson 1 highlights and follows. A manual scroll held its place for 3 s, then following came back on its own. Tapping 2:08 jumped to 2:08.
- **Not checked**: the car's screen. Whisper's `lang` tag is sometimes off (a lone "Russian." can be tagged `ru` and shown dimmed). That's transcript data, not the panel.
