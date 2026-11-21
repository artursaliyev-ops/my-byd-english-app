# 17: Silent reconnect for a long Talk

**What to build:** a Talk that runs past Gemini's connection and session limits without the learner noticing. The app keeps the latest resumption handle and, on `goAway` or a close between Turns, reconnects with it at once; the learner hears and sees nothing. Demo: a Talk that runs past 15 minutes. Spec §3 "Resumption".

**Blocked by:** 15 (A learner Turn)

**Status:** ready-for-agent

- [ ] The newest `sessionResumptionUpdate.newHandle` is kept in memory.
- [ ] On `goAway`, or a socket close between Turns, the app reconnects with the handle without changing the screen state or playing a sound.
- [ ] A `goAway` that arrives mid-Turn is handled once that Turn completes.
- [ ] Two sessions are never open at once: the old socket is closed before the new one opens.
- [ ] Reconnect logic is covered by unit tests against a fake session.
