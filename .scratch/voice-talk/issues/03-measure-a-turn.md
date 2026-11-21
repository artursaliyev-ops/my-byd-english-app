# Measure a Turn on the car with the offline stack
Type: task
Status: open
Blocked by: 01, 02

## Question

HITL. Build a throwaway probe (not merged) running the recommended offline stack, install it on the car, and in a parked car hold a few Turns of small talk. Measure, per Turn: end of speech → text, text → first model token, first token → first audio, and the total; recognition errors; answer quality (simple English, recasts), judged against the talk partner test set in [Decide how the talk partner behaves](05-talk-partner.md) run on a computer first; RAM, heat, and whether the lesson app or BYD's apps are killed. Use the car-measurement list at the end of the offline-stack research (branch `research/offline-talk-stack`) as the checklist. Try the runner-up model if the first misses the 3–5 s bar. The result feeds the offline-or-cloud decision.
