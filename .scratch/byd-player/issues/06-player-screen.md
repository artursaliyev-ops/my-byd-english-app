# Main player screen and resume
Type: task
Status: resolved
Blocked by: 05

Controls: play/pause, ±5 s, prev/next, volume, speed 0.75–1.5x, A-B repeat. Auto-advance to the next lesson. Save the lesson and position (periodically and on pause) and resume from there on launch. Mark a lesson completed when it ends.

## Answer

Built in `android/` and checked on the `byd_dilink` emulator (2026-11-21). 8 JVM tests pass (`AbLoopTest`, `FormatTest`, `LessonFileNameTest`).

- **Two screens** over one `PlaybackService`: the lesson list and the player (`PlayerScreen.kt`). Tapping a lesson queues every found lesson from 1–90, starts the tapped one, and opens the player. "‹ Lessons" (or system Back) returns to the list. The list then shows a "▶ Lesson N" button back to the player. Library state (folder, scan, completed set) moved up into `LibraryModel`, owned by the app, so the scan survives the trip and resume can use it.
- **Player layout** (landscape): the controls take the left ~46%. The right ~54% is a `TranscriptArea` placeholder ("Transcript isn't available for this lesson yet.") for the Synced transcript panel to fill.
- **Controls**:
  - seek slider with elapsed and total time
  - prev/next lesson (`seekToPrevious/NextMediaItem`, disabled at the queue ends)
  - −5 s / +5 s (the player's seek increments are set to 5 s, so steering-wheel and notification seek match)
  - play/pause (140 dp)
  - speed buttons 0.75 / 0.9 / 1 / 1.1 / 1.25 / 1.5x
  - volume −/+ with a level bar. It moves the **system media volume** (`setDeviceVolumeControlEnabled`) and falls back to the player's own gain if the session doesn't expose device volume.
  - A-B: one button. First tap sets A, second sets B and loops, third clears. B must be ≥ 1 s after A, otherwise the tap moves A instead. Switching lesson clears the loop.
- **Background behaviour lives in the service**, so it all works with the screen off:
  - the A-B loop is checked every 100 ms while playing, and its state goes to the UI through session extras (custom commands `AB_MARK` / `AB_CLEAR`)
  - the resume point (`PlaybackStore`: lesson + position) is saved every 5 s while playing, on pause, on seek while paused, on lesson change, and on service destroy
  - speed is saved whenever it changes and restored on service start
  - a lesson is marked completed (`LibraryStore.setCompleted`) when it auto-advances to the next one, or when the last queued lesson ends (resume then points at that lesson's start). The lesson list updates right away through a prefs listener.
- **Resume on launch**: if the service is still alive, the app opens straight on the player. Otherwise, once the scan finds the saved lesson, it queues the course at that lesson and position **paused** and opens the player. Not auto-playing is deliberate: audio shouldn't start by itself when the car's screen turns on, and one tap plays. If the saved lesson isn't in the folder, the app stays on the list.
- **Checked on the emulator**:
  - Lesson 3 → speed 1.25x, +5 s ×3, volume 5→6/15
  - A-B 0:27–0:30 looped, including after HOME
  - seeking near the end auto-advanced to Lesson 35 and marked 3 ✓, with Next disabled at the queue's end
  - pausing at 0:33 saved it. After force-stop and relaunch, the app opened on Lesson 35 at 0:33, paused, still at 1.25x.
- **Not checked**: the car itself (steering-wheel buttons, whether DiLink routes system media volume to the speakers), and a real 30-minute auto-advance (the test seeked to the end).
