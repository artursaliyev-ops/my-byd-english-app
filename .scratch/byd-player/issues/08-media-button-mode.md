# Button mode setting for the steering-wheel buttons
Type: task
Status: open
Blocked by: 06

Add a Settings screen (⚙ in the lesson list header, English UI) with one option, **Button mode** for the media buttons (see `GLOSSARY.md`): "Previous / next lesson" or "−5 s / +5 s". The default is **−5 s / +5 s**. The skip is a fixed 5 s, the same as the on-screen buttons. The choice persists and applies in the background.

Decided in grilling (2026-11-21):
- Only the media buttons follow the mode: steering wheel, the DiLink now-playing widget, and Bluetooth/external keys. The player screen's ⏮ ⏭ and −5/+5 never change. The widget can't be reliably told apart from the wheel, so it follows the mode too.
- If the car sends a distinct **hold** signal, hold does the other action (lesson switch in 5 s mode, and the reverse). That part waits on the car check below.
- The Settings screen gets a **Last button** line showing the last media-button signal the app received, with long-press/repeat info. The user reads it in the car, because ADB to the car hasn't worked.
