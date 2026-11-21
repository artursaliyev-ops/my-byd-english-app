# Shape the "▶ Me" control and the recording dot
Type: prototype
Status: resolved
Blocked by:

## Question

How should an Attempt show and play in the transcript panel, and how should the recording indicator look on the player screen, in the UI6 glass style? Prototype on the emulator: where "▶ Me" sits on a line, what plays (Attempt then speaker's line, gap between them), what happens to the lesson while it plays, how lines without an Attempt look, and the red dot's place and behaviour.

## Comments

- 2026-11-21, from "Find the speaking pauses in a lesson": one English line can have two Attempts, the answer in the pause before it and the echo in the pause after it. The control needs to show and play both, or pick one; decide here.

## Answer

Resolved 2026-11-21 with the user (HITL), on the car with Lesson 1, from a prototype of three variants on the real player screen: A chips on the line, B a "My answers" list behind a Transcript / My answers switch, C a row of its own for each speaking pause. Prototype on branch `prototype/attempt-playback-ui` (also carries the Mic probe; installed on the car). The user picked **A** for both the control and the dot.

- **Control**: a glass chip at the right end of the transcript line the Attempt is compared with. The line text wraps to make room.
- **Two Attempts on one line**: show both as separate chips, **"▶ Me"** (the answer in the pause before the line) and **"▶ Echo"** (the echo in the pause after it), Me first.
- **Lines without an Attempt** (Russian lines, and pauses where the learner stayed silent) show nothing extra.
- **What plays**: the Attempt, a **0.4 s** gap, then the speaker's line from the lesson audio. While it plays the chip turns blue and reads "■ Me…" then "■ Speaker…"; tapping again or using the transport stops it.
- **The lesson while it plays**: pauses; afterwards it goes back to where it was and **stays paused**.
- **Recording dot**: a red dot right of the lesson title. Red and steady while the mic is on (lesson playing with "Record my answers" on), **pulsing** while inside a speaking pause (audio being kept), grey while the lesson is paused, hidden when the setting is off.
