# 15: A learner Turn

**What to build:** a real back-and-forth. After Anna finishes, the turn beep plays and the screen goes blue (**You**). The learner speaks; ~1.5 s of silence (a white ring fills over it) ends their part with a soft tick and **Thinking**; Anna answers and the next Turn begins. Talk can now go on Turn after Turn until End Talk. Spec §5, §6 steps 2–5.

**Blocked by:** 14 (Anna says hello)

**Status:** ready-for-agent

- [ ] The Talk service holds **one** `VOICE_COMMUNICATION` 16 kHz mono recording for the whole Talk, started from the visible screen. "Mic closed" drops samples in software; the recording is never stopped and restarted mid-Talk. Silenced capture counts as silence.
- [ ] Silero VAD runs on the head unit (bundled model, `onnxruntime-android`). On speech start: activityStart, a 300 ms pre-roll, then live ~100 ms chunks; after the silence window (`SILENCE_MS`, 1.5 s): activityEnd. An utterance reaching 30 s ends the same way.
- [ ] The mic is closed while Anna speaks and opens 300 ms after `turnComplete` **and** playback has drained.
- [ ] The turn beep and the tick are made on the head unit.
- [ ] The Turn state machine (Connecting, Anna, You, Thinking, plus the later No signal and Paused) is pure Kotlin with no Android types, driven by events, and unit-tested.
- [ ] The current utterance is kept in memory only (ticket 18 resends it).
- [ ] Tapping Talk without `RECORD_AUDIO` asks with the normal prompt; if it's denied: no Talk and "Microphone permission not granted".
- [ ] Starting a Talk stops voice practice's recorder if it's running; it comes back by its own rule.
- [ ] The learner's words are never shown.
