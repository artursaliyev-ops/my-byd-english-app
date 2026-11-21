# Compare cloud options for a Talk Turn
Type: research
Status: resolved
Blocked by:

## Question

AFK. Talk runs on a cloud model over the phone's iPhone hotspot ([Decide whether Talk runs offline or in the cloud](04-offline-or-cloud.md)). From primary sources (provider docs, pricing pages, published latency figures), compare the realistic options for one Turn on an Android 12 (SDK 32) head unit:

- **Shape**: a realtime speech-to-speech API (audio in, audio out over WebSocket/WebRTC) vs a pipeline (speech to text → text model streaming → text to speech), and which parts could stay on the head unit (the offline research's Moonshine/Silero and Piper on branch `research/offline-talk-stack`).
- **Candidates**: at least OpenAI Realtime / gpt-4o-mini class, Google Gemini Live / Flash class, Anthropic Claude Haiku class (text only, paired with speech services), and one cheap speech-to-text and text-to-speech pair (e.g. Deepgram, ElevenLabs/Cartesia, Google Cloud).
- **Per option**: expected end of speech → first answer audio (against the 3–5 s bar, allowing for mobile hotspot round trips), cost of a 20-minute Talk and of daily use, whether it can follow the talk partner rules (recasts, A1–A2, Russian only on "in Russian?"), how well it recognizes Russian/Uzbek-accented English, Android/Kotlin client support on SDK 32, how an API key is held in a single-user sideloaded app, and the data-retention policy for audio and text.

Recommend one option plus a runner-up, and list what must be measured in the car.

## Answer

Resolved 2026-11-21 (AFK research). Findings on branch `research/cloud-talk-options` (pushed, not merged), file `.scratch/voice-talk/research/cloud-options.md`. All latency and cost numbers are estimates from provider docs and pricing pages; nothing has run in the car.

- **Recommended**: OpenAI Realtime `gpt-realtime-2.1` at low reasoning effort, over one WebSocket (OkHttp, no Google Play services). The app keeps its own `VOICE_COMMUNICATION` 16 kHz mic and Silero VAD on the head unit and sends the audio itself after the 1.5 s silence. Audio in, so it hears the learner's real mistakes, which recasts need; handles Russian and Uzbek. Cheaper fallback on the same code: `gpt-realtime-2.1-mini`.
- **Runner-up**: a pipeline: Deepgram Flux Multilingual (speech to text) → Claude Haiku 5.5 (text) → Cartesia Sonic 3.6 (voice). Cheapest, and the easiest to test against the partner set (all text), but three network hops, recasts depend on the speech to text keeping mistakes, and no Uzbek.
- **Close third**: Gemini 3.8 Live: slightly better published quality, but ~10 min connections / 15 min sessions without compression, no input caching, ~$1.5–2.5 per Talk. Free tier trains on audio and has human review, so it is not for the owner's voice.
- **Latency (est.)**: end of speech → first audio ~2.7–3.5 s OpenAI, ~2.9–3.7 s Gemini, ~2.9–4.0 s pipeline, with an assumed 100–300 ms hotspot round trip. The 1.5 s silence window is the largest single part.
- **Cost (est., ~75 Turns per 20-min Talk, one Talk a day)**: `gpt-realtime-2.1` ~$1–2 per Talk ($30–60/month); mini ~$0.40 ($12–25/month); pipeline ~$0.25–0.45 ($8–13/month). Risk: realtime APIs bill all earlier audio again each Turn; if OpenAI's prompt cache misses, a full-model Talk is ~$15.
- **Key**: build it into the app from an uncommitted `local.properties`, one project or key per provider with a monthly budget cap. Ephemeral tokens add nothing for a single-user sideloaded app. Uzbekistan is supported by OpenAI, Google and Anthropic; Russia by none.
- **Retention**: OpenAI abuse logs up to 30 days, no training; Anthropic deleted within 30 days, no training; Gemini paid "limited period"; Deepgram keeps samples unless `mip_opt_out=true` is sent.
- **Open**: OpenAI's maximum session length (one forum report says 30 min); Haiku 5.5 time to first token and Sonic 3.6 time to first audio (unpublished); Gemini billing with compression; Cartesia/ElevenLabs retention; no published accuracy for Russian- or Uzbek-accented English.
- **Measure on a computer first**: the partner test set on clips of the owner's voice for all four candidates; whether Flux keeps grammar mistakes word for word. **In the car**: hotspot round trip per provider; end of speech → first sound over ~20 Turns; recognition errors on the owner's voice; real tokens and cache share in a 20-min Talk; a Talk longer than 20 min; hotspot dropping mid-Talk; CPU and heat with BYD's apps up; Anna's voice leaking into the mic. These feed [Pick the cloud provider, model and key](09-pick-cloud-provider.md) and [Measure a cloud Turn on the car over the hotspot](10-measure-cloud-turn.md).
