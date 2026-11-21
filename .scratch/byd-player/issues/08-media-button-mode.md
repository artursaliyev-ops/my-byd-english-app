# Button mode setting for the steering-wheel buttons
Type: task
Status: resolved
Blocked by: 06

Add a Settings screen (⚙ in the lesson list header, English UI) with one option, **Button mode** for the media buttons (see `GLOSSARY.md`): "Previous / next lesson" or "−5 s / +5 s". The default is **−5 s / +5 s**. The skip is a fixed 5 s, the same as the on-screen buttons. The choice persists and applies in the background.

Decided in grilling (2026-11-21):
- Only the media buttons follow the mode: steering wheel, the DiLink now-playing widget, and Bluetooth/external keys. The player screen's ⏮ ⏭ and −5/+5 never change. The widget can't be reliably told apart from the wheel, so it follows the mode too.
- If the car sends a distinct **hold** signal, hold does the other action (lesson switch in 5 s mode, and the reverse). That part waits on the car check below.
- The Settings screen gets a **Last button** line showing the last media-button signal the app received, with long-press/repeat info. The user reads it in the car, because ADB to the car hasn't worked.

## Answer

Built in `android/` and checked on the `byd_dilink` emulator (2026-11-21). 16 JVM tests pass, including the new `ButtonModeTest`.

- **Settings screen**: a gear button (vector icon, not a font glyph) at the right of the lesson list header opens it. "‹ Lessons" or Back returns. It has two big options for **Button mode**, "Previous / next lesson" and "−5 s / +5 s" (default). The choice is saved in `SettingsStore` (`ButtonMode.kt`).
- **How the mode applies**: the session's player is a `MediaButtonPlayer` (a `ForwardingPlayer` in `PlaybackService`). It catches ⏮ ⏭ (`seekToPrevious/Next` and the `…MediaItem` variants) and asks `session.controllerForCurrentRequest` who sent them:
  - the app's own screen sets a connection hint (`HINT_APP_UI`) on its controller, so on-screen ⏮ ⏭ always switch lesson
  - calls from the service itself pass through unchanged
  - anything else is a media button: wheel keys (Media3 routes them through its media-notification controller), the DiLink widget or other apps (their own controllers), and Bluetooth keys. These follow the mode, which is read on every press, so it works in the background.
- In lesson mode, ⏮ always goes to the previous lesson. It doesn't restart the current one first. FAST_FORWARD/REWIND keys are left as they were (±5 s).
- **Last button**: lists the last 24 signals, newest first, with millisecond times:
  - every key the screen sees ("via screen"), including non-media keycodes, in case the car sends its own
  - keys that reach the session ("via session, <package>")
  - each remapped command ("cmd next → +5 s · from <package>")
  
  Key lines show the keycode, down/up, repeat count and the long-press flag. One wheel press gives about 4 lines. The list is in memory only.
- **Checked on the emulator** (`adb shell input keyevent`):
  - −5/+5 mode, paused at 2:29: NEXT, NEXT, PREVIOUS ended at 2:34
  - lesson mode with the app in the background (HOME): NEXT went to Lesson 2, PREVIOUS back to Lesson 1
  - back in −5/+5 mode, the on-screen ⏭ still went from Lesson 1 to Lesson 2, and a NEXT key then moved 0:00 → 0:05
  - the Settings log showed the screen → session → cmd chain for each key
- **Not checked**: the car. That covers what the wheel sends, whether a hold gives repeats, a long-press flag or other keycodes, and the DiLink widget's path (it should arrive as a separate controller command with no key line). All of that is Check steering-wheel button signals on the car.
