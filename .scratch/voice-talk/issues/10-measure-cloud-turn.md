# Measure a cloud Turn on the car over the hotspot
Type: task
Status: resolved
Blocked by: 09

## Question

HITL. With the chosen provider, run a throwaway probe (not merged) on the car over the iPhone hotspot, parked and then while driving. Measure per Turn: end of speech → first answer audio and its parts, recognition errors on the owner's voice, the talk partner test set pass rate from [Decide how the talk partner behaves](05-talk-partner.md), data used per Talk, whether a 20–30 min Talk survives the 15-min session limit with compression and resumption, whether the free-tier limits are hit, and what happens on weak signal, including whether the 8 s Turn timeout and the 60 s outage rule from [Decide what Talk does when the network drops](11-network-drop.md) feel right. Try a 1.0 s silence window as well as 1.5 s. Confirms whether the chosen option meets the 3–5 s bar or whether the runner-up must be tried.

## Answer

Resolved 2026-11-21, **partly**: by the user's choice, the measurements stop at the Mac. Both Live models ran the whole talk partner test set as one Talk each, over home Wi-Fi, with **machine voices** (macOS `say`: Samantha for English, Milena for Russian) instead of the owner's voice. The car runs did not happen. Script and raw results: local branch `probe/cloud-turn`, commit `2f3761b`, `.scratch/voice-talk/probe-cloud/` (never merge). Setup lives outside the repo in `~/talk-probe/cloud/`.

**Model: `gemini-3.1-flash-live-preview` wins** and becomes the model setting from [Pick the cloud provider, model and key](09-pick-cloud-provider.md).

| | 3.1 flash live | 2.5 flash native audio (12-2025) |
|---|---|---|
| activityEnd → first answer audio | **0.55–1.07 s, median 0.72** | 1.9–4.2 s, median 2.6 |
| Connect | 0.57 s | 0.49 s |
| Heard the line right | 9/10 ("the uh" → "dia") | 4/10 ("in Russian" → "emotion"; "wait", "bye" heard as Vietnamese/Thai) |
| Test set pass (incl. opening) | **~9/11** | ~4/11 |

Each 3.1 run's first audio time is what the app adds to the silence window. With 1.5 s the Turn is ~2–2.6 s, and with 1.0 s ~1.5–2 s, well inside the 3–5 s bar. 3.1 missed two cases. It didn't recast "I go to work now", and it answered the nonsense "dia" instead of "Sorry, can you say that again?". Once it also said "Is this the one? It looks great!", which suggests looking at something. Russian on "in Russian?", "You can say: …", "wait" → "OK.", the politics dodge, "by the way" and "bye" were all right.

**Facts the build needs:**
- google-genai 3.x defaults to API `v1`, where Live isn't served (HTTP 404 on the WebSocket). Use **`v1beta`**.
- With the server's activity detection off (the head unit's VAD ends the Turn), each learner Turn is `activityStart` → 16 kHz PCM in ~100 ms chunks → `activityEnd`. On 3.1, **text inside an activity is rejected** (error 1007 "Precondition check failed"), so the opening ("the learner pressed Talk") goes as bare realtime text. 2.5 behaves the other way: it ignores bare text and needs `clientContent`.
- Compression (sliding window) and session resumption are accepted on both; resumption handles arrive every Turn.
- Data: Anna's audio is ~63 KB/s down (24 kHz PCM in base64 JSON), the learner's ~43 KB/s up. Est. **~40–50 MB per 20-min Talk**, depending on how much each side speaks.
- Free tier: 4 Talks on the probe hit no limit or quota error.

**Not measured** (machine voice and Mac only). These become checks for the first car build, not blockers for the spec:
- recognition on the owner's accented voice and on road noise;
- latency over the iPhone hotspot, parked and driving;
- a 20–30 min Talk across the 15-min session limit with compression and resumption;
- free-tier limits over a real Talk;
- weak signal, and how the 8 s Turn timeout and 60 s outage rule from [Decide what Talk does when the network drops](11-network-drop.md) feel;
- the 1.0 s vs 1.5 s silence window.

If 3.1 fails these in the car, the fallback in [Pick the cloud provider, model and key](09-pick-cloud-provider.md) stands. 2.5 is not a fallback.

Hygiene: the AI Studio key was pasted into the chat, so rotate it after the first car build.
