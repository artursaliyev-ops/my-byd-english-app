# Decide whether Talk runs offline or in the cloud
Type: grilling
Status: resolved
Blocked by: 03

## Question

HITL. Given the car measurements, does the offline stack pass the user's bar (3–5 s to the first answer audio, understandable and natural enough for small talk)? If yes, lock the stack. If no, Talk goes to a cloud model over the hotspot, and the cloud fog on the map graduates into tickets.

## Answer

Resolved 2026-11-21 (HITL grilling, user). **Offline fails the bar, so Talk runs on a cloud model over the phone's hotspot.** There is no offline fallback.

Why, from [Measure a Turn on the car with the offline stack](03-measure-a-turn.md): no offline model tried is both fast and good enough. Qwen3.5-2B reaches the first answer audio in ~5 s, at the edge of the 3–5 s bar or past it, because BYD's dashcam and cameras hold ~3 of the 8 cores. It passes ~2/10 of the talk partner test set (no recasts, no Russian hint, ignores the rules). Qwen3.5-0.8B is fast at ~3–4.5 s but passes only ~3/10, and "wait" makes it say goodbye. The quality gap comes from model size, so prompt tuning is unlikely to close it, and the speed limit comes from BYD's own load, which Talk can't change.

Options considered and rejected:
- One more offline try (prompt tuning on 2B, Gemma 4 E2B): rejected for the reasons above. E2B was expected to be slower on this car.
- Cloud first with offline 0.8B as a no-hotspot fallback: rejected. It means keeping a second stack that still talks badly.

Still holding from charting: the 3–5 s bar (end of speech → first answer audio) and everything in [Decide how the talk partner behaves](05-talk-partner.md) and [Shape the Talk screen and the feel of a Turn](06-talk-screen.md). The mic facts in the map's Notes are unchanged. Whether speech recognition and the voice also go to the cloud, or stay on the head unit, is left to the cloud tickets.

ADR: [Talk runs on a cloud model over the phone's hotspot](../../../docs/adr/0002-talk-runs-on-a-cloud-model.md).
