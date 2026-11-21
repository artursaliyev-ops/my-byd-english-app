# 18: Network drops

**What to build:** Talk behaves well on a weak or lost hotspot. A late Turn gets one silent retry; if that fails, a low two-note "no connection" tone plays and the screen shows a grey broken ring with "No signal", and the mic is closed. When the network is back, the turn beep and **You**. After 60 s with no connection, the Talk ends and the lesson resumes. Tapping Talk with no network shows "Connecting…" for up to 5 s, then the tone and "No internet" back on the player. Spec §8 (all but limits), §7 sounds.

**Blocked by:** 17 (Silent reconnect for a long Talk)

**Status:** done

- [x] No first audio 8 s after the tick (or the socket dies mid-Turn) → the Turn has failed: one silent retry (reconnect with the handle, resend the saved utterance as one activity). An answer that arrives after its Turn failed is dropped, never played.
- [x] Retry fails or the connection is lost → the tone and **No signal** (Anna's latest line stays), the mic closed, reconnecting in the background. Back → the turn beep and **You**; Anna doesn't repeat her question.
- [x] A refused resumption handle opens a new session silently with the same setup, then sends `(The connection came back.)`.
- [x] 60 s in No signal → the tone, the Talk ends, and the lesson resumes by the usual rule.
- [x] No network on Talk → **Connecting** for up to 5 s, then the tone, back to the player with a short "No internet" note, and the lesson as it was before the tap.
- [x] No signal time counts toward the 30-min cap. End Talk always works.
- [x] The timeouts are constants, and the transitions are unit-tested in the state machine.
