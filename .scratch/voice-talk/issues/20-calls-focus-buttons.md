# 20: Calls, focus and media buttons

**What to build:** Talk gets out of the way of the car. A phone call or "Hi BYD" pauses the Talk (a grey "Paused" state) and it carries on when they finish; a navigation prompt only makes Anna quieter; another media app ends the Talk. During a Talk, play/pause on the wheel or the now-playing widget ends the Talk, and ⏮ ⏭ and Wheel hold do nothing, so the lesson never moves behind the learner's back. Spec §10.

**Blocked by:** 15 (A learner Turn)

**Status:** done

- [x] Transient focus loss without ducking → **Paused**: Anna stops mid-line, the mic is closed, and an utterance in progress or an answer still arriving is dropped; Anna's latest line stays. Focus back → the turn beep and **You**; Anna doesn't repeat herself.
- [x] 60 s paused → the Talk ends, and the lesson resumes by the usual rule once focus is back. Paused time counts toward the 30-min cap.
- [x] Transient loss that allows ducking → Anna at about 30 % volume; nothing pauses.
- [x] Permanent loss → the Talk ends at once; the lesson stays paused.
- [x] While a Talk is active, media play/pause = End Talk (the lesson resumes by the usual rule), and ⏮ ⏭ and Wheel hold are ignored whatever the Button mode. With no Talk, the media buttons behave exactly as before (existing tests still pass).
- [x] The focus rules are unit-tested in the state machine.
