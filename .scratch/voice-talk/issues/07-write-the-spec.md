# Write the voice talk spec
Type: task
Status: resolved
Blocked by: 04, 05, 06, 09, 10, 11, 12, 13

## Question

AFK. Assemble `.scratch/voice-talk/spec.md` from the map's Notes and every resolved ticket, so a session can build Talk without new decisions: the stack and how it is installed, mic and Turn lifecycle, the talk partner's prompt, the screen, the lesson pause, and what was ruled out of scope. Done when the user has read it and has no open questions.

Include a **first car build checks** section with what [Measure a cloud Turn on the car over the hotspot](10-measure-cloud-turn.md) left unmeasured (owner's voice, hotspot latency parked and driving, a 20–30 min Talk across the 15-min limit, free-tier limits, weak signal, 1.0 vs 1.5 s silence), each with the bar it must meet and the fallback if it fails.

## Answer

Resolved 2026-11-21 (AFK, read and accepted by the user). The spec is at [spec.md](../spec.md): stack, key and install; session protocol; Anna's prompt with the app's bracketed control text and the 11-case test set (pass ≥ 9/11, cases 7, 10, 11 required); the Talk service and mic; Turn lifecycle; the Talk screen; network drops and limits; the lesson around a Talk; calls, focus and media buttons; off screen; and first car build checks, each with a bar and a fallback. The user accepted the defaults no ticket had settled, listed in the spec's §13 (voice `Aoede`, control text, 300 ms echo guard and pre-roll, 30 s utterance cap, Silero via `onnxruntime-android`, Talk stops `RecorderService`, silence window as a code constant).
