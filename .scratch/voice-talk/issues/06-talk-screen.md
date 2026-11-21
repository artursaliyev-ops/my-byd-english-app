# Shape the Talk screen and the feel of a Turn
Type: prototype
Status: resolved
Blocked by:

## Question

HITL. What does the learner see and hear during a Talk? The "Talk" button and where it lives, the states (listening / thinking / speaking) and how they show at a glance while driving, whether the learner's and the partner's words appear as text, whether the learner can cut in while the partner speaks (barge-in), the end-of-speech silence (~1.5 s) and what happens on a longer pause, how the paused lesson comes back after "bye". Use a rough prototype in the UI6 glass style.

## Answer

Resolved 2026-11-21 (prototype with the user on the emulator). Prototype: local branch `prototype/talk-screen`, commit `0ebfe87` (`PrototypeTalk.kt`; never merge). It offered A Orb (one big state shape, no text), B Captions (state left, the Talk's words where the transcript is) and C Lesson bar (lesson stays, dimmed, with a bottom glass bar).

- **Screen**: variant A, plus Anna's latest line in small text at the bottom. One big shape in the middle and one state word below it: green rings = Anna is speaking, blue = your turn / listening, white dots = thinking. The Talk takes the whole screen. An **End Talk** button sits bottom right.
- **Talk button**: only on the player screen, next to "‹ Lessons", BYD blue. Not on the lesson list.
- **Learner's words**: not shown at all. Only Anna's latest line appears. Reading your own mistakes while driving distracts, and the recast reaches you by ear.
- **Barge-in**: off for now. The mic is closed while Anna speaks, because echo on the car isn't checked yet. Only End Talk interrupts her.
- **Beeps**: a short beep when the learner's turn starts, and a soft tick when the end of speech is detected and the words are taken.
- **Silence**: end of the learner's part after ~1.5 s of silence; on screen a white ring fills over those 1.5 s. If nothing is said, a slow ring fills toward Anna's single nudge at ~9 s ("Take your time…"), then she waits silently. The Talk never ends by itself.
- **Lesson after the Talk**: on "bye" or End Talk, a lesson that was playing before the Talk resumes 5 s before where it paused. A lesson that was already paused stays paused.
