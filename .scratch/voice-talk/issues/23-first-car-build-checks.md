# 23: First car build checks

**What to build:** nothing; this ticket is for the owner. Run the spec's §12 checks with the first Talk build on the car over the iPhone hotspot, parked first, then driving, and record each result here against its bar. A failed bar triggers its fallback from §12.

**Blocked by:** 14, 15, 16, 17, 18, 19, 20, 21, 22 (every Talk build ticket)

**Status:** ready-for-human

- [ ] Owner's voice: the test set with the owner's recordings (≥ 9/11; cases 7, 10, 11 pass) and a 10-min real Talk (≤ 1 mishearing in 10 Turns)
- [ ] Hotspot latency, parked and driving, ≥ 20 Turns each: end of speech → first audio ≤ 5 s on 9 of 10 Turns
- [ ] Silence window 1.0 s vs 1.5 s: 1.0 s cuts off ≤ 1 in 10 Turns, else keep 1.5 s
- [ ] Long Talk: 30 min across the 15-min limit, with silent reconnects and no gap over 2 s
- [ ] Free-tier limits: one 30-min Talk a day for a week with no limit error
- [ ] Weak signal: the 8 s, retry, No signal and 60 s rules fire as specced; no stuck state; the owner finds 8 s and 60 s right
- [ ] Road noise doesn't start a learner turn; the owner's speech is caught
- [ ] Anna's echo doesn't start a learner turn (then decide on barge-in)
- [ ] Turns work for 5 min on Home and 5 min with the screen off
- [ ] A real call (in and out) and "Hi BYD" pause the Talk
- [ ] Rotate the AI Studio key afterwards
