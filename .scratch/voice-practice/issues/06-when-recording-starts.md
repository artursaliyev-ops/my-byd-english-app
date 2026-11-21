# Decide when the mic starts and what a lesson resumed from the wheel does
Type: grilling
Status: resolved
Blocked by: 03

## Question

The car check showed the mic only captures if recording starts while the app is on screen; after that it keeps going in the background, but a recording started from the background is muted (see "Check the microphone on the car"). With "Record my answers" on, when exactly does the microphone service start and stop (opening the player, pressing play, the whole app session), and what happens when a lesson is resumed from the steering wheel or the now-playing widget without the screen: record nothing and say so, show a prompt next time the screen opens, or something else? Also settle whether the red dot shows "mic on" or "mic on but muted".

## Answer

Resolved 2026-11-21 with the user (HITL grilling, two rounds; all recommendations accepted, Q3 answered as a fact).

- **Start**: with "Record my answers" on, the microphone service starts when a lesson starts playing while the app is on screen (player, lessons list or Settings), and whenever the app comes on screen while a lesson is playing.
- **While paused** the mic stays open (only speaking-pause audio is ever kept), so a lesson paused and then resumed from the steering wheel or the now-playing widget keeps recording. It closes after **30 min** paused, or when the app is closed.
- **Resumed without the mic** (after those 30 min, or after the app was closed): nothing is recorded and nothing is shown; the mic starts again as soon as the app's screen opens while the lesson plays. No "mic muted" state exists.
- **Car start-up**: the user always opens the app and presses ▶ themselves, so no special rule.
- **Permission**: asked the first time "Record my answers" is turned on in Settings; if denied, the setting turns back off with a short "Microphone permission not granted" line under it.
- **Red dot**: only the states from the "▶ Me" decision (red while recording, pulsing inside a speaking pause, grey while paused, hidden when the setting is off).
- **Toggling mid-lesson**: turning the setting on starts the mic at once (Settings is on screen); turning it off closes the mic at once and the Attempt in progress is not saved.
