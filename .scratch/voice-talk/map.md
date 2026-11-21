# Voice talk — map
Label: wayfinder:map

## Destination

A hand-off-ready **spec** at `.scratch/voice-talk/spec.md` for a **Talk** mode: simple spoken English small talk with a model while driving, so a session can build it without new decisions. Offline (on the head unit) was tried first and failed the bar, so the spec is written for a cloud model (Gemini Live, free tier) over the phone's hotspot.

## Notes

- Domain: the Android app in `android/` (Kotlin + Compose + Media3; `PlaybackService.kt` owns playback). Terms **Talk** and **Turn** are in `GLOSSARY.md`.
- Car: 2025+ Song Plus EV, DiLink 5.0, Android 12 (SDK 32), not AAOS. Read on the car: Qualcomm QCM6490 (782G-class CPU), 11 GiB RAM with ~4.4 GiB free, no English TTS voice (see the car hardware ticket). The car joins the phone's hotspot; ADB at `172.20.10.2:5555` on the iPhone hotspot (was `192.168.215.182` once; scan port 5555 if it moves). The Mac must be on the same hotspot.
- Mic facts carried over from voice practice ([car mic check](../voice-practice/issues/03-car-mic-check.md)): record `VOICE_COMMUNICATION` (car AEC + NS cut the speakers by ~20–25 dB); 16 kHz mono works; the mic must start from the visible screen (a background start is muted); no audio-focus change from recording.
- Settled in charting (2026-11-21, user):
  - Offline first. If it doesn't pass, fall back to a cloud model over the hotspot.
  - Latency bar: from the end of the learner's speech to the first sound of the answer, **3–5 s** for now; to be tried in the car.
  - Topic: free simple small talk (day, road, weather), in plain A1–A2 English (see the talk partner ticket).
  - Mistakes: **recast** only: the talk partner uses the correct form in its reply and carries on; no stopping to explain.
  - When stuck: simpler English; one short Russian hint only if the learner asks ("in Russian?").
  - A separate mode: the lesson is paused for the whole Talk.
  - Turn-taking: start with an on-screen "Talk" button, then hands-free; ~1.5 s of silence ends the learner's part of a Turn; end by saying "bye" or tapping the button again.
  - Model install (offline only, moot since Talk went cloud): a file up to ~3 GB copied once via SD card or ADB; no in-app download.
- Consult `domain-modeling` for new terms. UI follows the UI6 glass style in `Glass.kt`.

## Decisions so far

<!-- one line per closed ticket -->

- [Find an offline speech, model and voice stack for the head unit](issues/01-offline-stack.md): likely feasible: Moonshine + Silero (sherpa-onnx) → Qwen3.5-2B Q4 on llama.cpp CPU → Piper, est. ~3 s to first audio and ~1.7 GB; runner-up Gemma 4 E2B on LiteRT-LM. Estimates only; research on branch `research/offline-talk-stack`.
- [Read the car's hardware for an on-device model](issues/02-car-hardware.md): QCM6490 (1×2.71 + 3×2.40 GHz A78), 11 GiB RAM / ~4.4 GiB free with BYD apps up, 173 GB internal + 15 GB SD free, OpenCL public but no QNN (NPU unusable for now), only a Chinese iFlytek TTS, so Talk bundles its own English voice.
- [Decide how the talk partner behaves](issues/05-talk-partner.md): "Anna", a friendly acquaintance; speaks first; 1–2 short A1–A2 sentences plus a question; one recast per Turn; Russian only on "in Russian?"; never ends a Talk itself; a ~10-case test set for every model.
- [Shape the Talk screen and the feel of a Turn](issues/06-talk-screen.md): full-screen big state shape (green Anna / blue you / dots thinking) + Anna's latest line, learner's words never shown; Talk button on the player only; no barge-in yet; beep + tick; lesson resumes 5 s back only if it was playing. Prototype on local branch `prototype/talk-screen`.
- [Measure a Turn on the car with the offline stack](issues/03-measure-a-turn.md): BYD's dashcam/cameras keep ~3 cores busy; Qwen3.5-2B gets ~4.5 tok/s (est. ~5 s to first audio) and passes ~2/10 of the partner test set; 0.8B gets ~9 tok/s (~3–4.5 s) but ~3/10. No offline model tried is both fast and good enough. Probe on local branch `probe/talk-turn`.
- [Decide whether Talk runs offline or in the cloud](issues/04-offline-or-cloud.md): offline fails the bar, so Talk runs on a cloud model over the phone's hotspot, with no offline fallback; the 3–5 s bar and the partner and screen decisions still hold.
- [Compare cloud options for a Talk Turn](issues/08-cloud-options.md): recommend OpenAI Realtime `gpt-realtime-2.1` (mini as cheaper fallback) with on-device Silero VAD, est. ~2.7–3.5 s and ~$1–2 per 20-min Talk; runner-up Deepgram Flux → Claude Haiku 5.5 → Cartesia Sonic 3.6, ~$0.25–0.45; key built in from `local.properties` with a budget cap. Estimates only; research on branch `research/cloud-talk-options`.
- [Pick the cloud provider, model and key](issues/09-pick-cloud-provider.md): Gemini Live **free tier** (owner accepts training/human review), speech-to-speech, mic + Silero VAD on the head unit; best of two free Live models by the test set; key built in from `local.properties`, no card; compression + resumption, 30-min Talk cap; no paid tier: fallback is a free Groq → Groq → Google TTS/Deepgram pipeline. Free-tier check in `research/free-tiers.md`.
- [Decide what Talk does when the network drops](issues/11-network-drop.md): 8 s per Turn, then one silent retry (resume + resend the saved utterance); a head-unit "no connection" tone and a grey "No signal" state with the mic closed; 60 s offline ends the Talk and resumes the lesson; no network on "Talk" → 5 s "Connecting…" then back to the player; free-tier limits get their own "Talk limit reached" note; failed resumption → a silent new session with no fresh hello; 30-min cap is wall-clock.

## Not yet specified

- **Memory between Talks**: whether the talk partner remembers earlier Talks, and whether Talk text or audio is kept at all (privacy, retention like voice practice's 5-lesson rule).
- **Real driving conditions**: road noise vs speech recognition, screen off, a phone call arriving mid-Talk (its audio and audio focus; its network side is settled), what the media buttons do during a Talk; whether barge-in can be turned on once echo from Anna's voice into the mic is measured on the car.

## Out of scope

- **Level tracking**: tying the partner's English to the learner's Pimsleur lesson or vocabulary; ruled out because [Decide how the talk partner behaves](issues/05-talk-partner.md) settled on one fixed A1–A2 rule, so the spec doesn't need it.
