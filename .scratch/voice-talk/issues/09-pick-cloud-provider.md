# Pick the cloud provider, model and key
Type: grilling
Status: resolved
Blocked by: 08

## Question

HITL. Given the cloud comparison, which provider and model does Talk use, in which shape (realtime speech-to-speech or a pipeline, and which parts stay on the head unit)? Whose account and key, how the key gets onto the car (alongside the SD card/ADB install path) and where it is stored, what monthly cost is acceptable, and whether a usage cap is needed.

## Answer

Resolved 2026-11-21 (grilling with the user). Builds on [Compare cloud options for a Talk Turn](08-cloud-options.md) and a follow-up free-tier check, [free-tiers.md](../research/free-tiers.md) (prices and limits read from provider pages 2026-11-21; latencies are estimates).

- **Provider and shape**: Gemini Live API, **free tier**, speech-to-speech over one WebSocket (OkHttp). The head unit keeps the mic (`VOICE_COMMUNICATION`, 16 kHz) and Silero VAD, and ends the learner's part itself. Est. ~1–2.5 s from end of speech to first audio.
- **Model**: run the talk partner test set on `gemini-3.1-flash-live-preview` and `gemini-2.5-flash-native-audio-preview-12-2025` (clips of the owner's voice, on a computer first); the better one wins. The model name is one setting.
- **Privacy**: accepted by the user: on the free tier Google may use the audio and text to improve its products and human reviewers may read them.
- **Account and key**: the owner's personal Google account, a dedicated AI Studio key for Talk, no card. The key is built into the APK from the uncommitted `android/local.properties` via `BuildConfig`; rotate it if the car is sold or serviced.
- **Length**: context window compression and session resumption always on (audio sessions otherwise end at 15 min). A Talk lasts at most **30 min**; then Anna says goodbye and the lesson resumes.
- **No paid tier for now.** If the free tier disappears, its limits bite, or Gemini fails the test set, fall back to the free pipeline: Groq `whisper-large-v3-turbo` → Groq text model (`gpt-oss-20b` class) → Google Cloud TTS (card needed) or Deepgram Aura with `mip_opt_out=true`. OpenAI Realtime is dropped (no free tier).
- **Silence window**: stays 1.5 s, as a setting; try 1.0 s in [Measure a cloud Turn on the car over the hotspot](10-measure-cloud-turn.md).
- **Unknown**: Google publishes no free-tier numbers for these Live models (limits "not guaranteed"; a concurrent-session cap exists). Measure in the car.
