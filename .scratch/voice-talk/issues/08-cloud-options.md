# Compare cloud options for a Talk Turn
Type: research
Status: open
Blocked by:

## Question

AFK. Talk runs on a cloud model over the phone's iPhone hotspot ([Decide whether Talk runs offline or in the cloud](04-offline-or-cloud.md)). From primary sources (provider docs, pricing pages, published latency figures), compare the realistic options for one Turn on an Android 12 (SDK 32) head unit:

- **Shape**: a realtime speech-to-speech API (audio in, audio out over WebSocket/WebRTC) vs a pipeline (speech to text → text model streaming → text to speech), and which parts could stay on the head unit (the offline research's Moonshine/Silero and Piper on branch `research/offline-talk-stack`).
- **Candidates**: at least OpenAI Realtime / gpt-4o-mini class, Google Gemini Live / Flash class, Anthropic Claude Haiku class (text only, paired with speech services), and one cheap speech-to-text and text-to-speech pair (e.g. Deepgram, ElevenLabs/Cartesia, Google Cloud).
- **Per option**: expected end of speech → first answer audio (against the 3–5 s bar, allowing for mobile hotspot round trips), cost of a 20-minute Talk and of daily use, whether it can follow the talk partner rules (recasts, A1–A2, Russian only on "in Russian?"), how well it recognizes Russian/Uzbek-accented English, Android/Kotlin client support on SDK 32, how an API key is held in a single-user sideloaded app, and the data-retention policy for audio and text.

Recommend one option plus a runner-up, and list what must be measured in the car.
