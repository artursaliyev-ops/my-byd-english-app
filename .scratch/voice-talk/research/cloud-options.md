# Cloud options for a Talk Turn

Researched 2026-11-21 for [Compare cloud options for a Talk Turn](../issues/08-cloud-options.md).

**Question.** Talk runs on a cloud model over the phone's iPhone hotspot. Which cloud setup gives the best Turn on the DiLink head unit (Android 12, SDK 32)? The bar: end of the learner's speech to the first sound of the answer in **3–5 s**. The talk partner ("Anna") must follow the rules in [05-talk-partner.md](../issues/05-talk-partner.md): A1–A2 English, one recast per Turn, Russian only on "in Russian?".

**Tags used below.**

- **[Source]**: a provider doc, pricing page, model card or terms page says it.
- **[Measured, third party]**: a published benchmark that is not the provider's own.
- **[Estimate]**: my arithmetic or reasoning from the sources. Not measured.
- **[Unknown]**: settle it on the computer or in the car.

---

## 0. Short answer

- **Recommended: OpenAI Realtime, `gpt-realtime-2.1`, low reasoning effort, over a plain WebSocket.** The app keeps its own mic and Silero VAD, ends the learner's part after 1.5 s of silence, then commits the audio itself. Est. **~2.7–3.5 s** to first audio. Est. **~$1–2 per 20-min Talk** when the prompt cache hits (Section 3). Try `gpt-realtime-2.1-mini` on the same code, with no other changes. It costs about a third as much, but its published quality scores are lower.
- **Runner-up: a pipeline.** Deepgram Flux Multilingual (STT) → Claude Haiku 5.5 (text, thinking off) → Cartesia Sonic 3.6 (TTS). Est. **~2.9–4.0 s**, est. **~$0.25–0.45 per Talk**. It is the cheapest option and the easiest to test against the partner test set, because the model works in text. It has three network hops and a weaker path for Russian and Uzbek speech.
- **Close third: Google Gemini 3.8 Live.** Its published quality scores are slightly better than `gpt-realtime-2.1` at similar latency. But each connection lasts ~10 min and an audio-only session ends at 15 min unless context compression is on. It has no input caching, so a long Talk costs est. **~$1.5–2.5**. Swap it in if OpenAI fails the test set.
- **Not chosen: GPT-Live-1.** It is full-duplex (barge-in) and costs $0.05/min plus a separate backend model. Talk is half-duplex for now. Revisit it when barge-in is opened.

---

## 1. Two shapes

| | Speech-to-speech (OpenAI Realtime, Gemini Live) | Pipeline (STT → text model → TTS) |
|---|---|---|
| Hops from the car per Turn | 1 WebSocket | 3 (STT socket, model HTTPS stream, TTS socket) unless a relay server is added |
| Hears the learner's actual audio | Yes. It can react to what it hears, not to an STT guess | No. Recasts depend on the STT keeping the learner's mistakes |
| Russian / Uzbek mixed in | Handled by the model | STT must be multilingual. The TTS needs a Russian voice for "in Russian?" |
| Anna's line as text for the Talk screen | From the output audio transcript | Native |
| Testing the ~10-case set on a computer | Needs audio clips (record or synthesize them) | Text in, text out. Same as the offline probe |
| Cost driver | Audio tokens. All earlier audio is billed again on every Turn | Per-minute STT + per-character TTS. The text model costs almost nothing |

**What can stay on the head unit.**

- **Silero VAD (sherpa-onnx) for end of speech: yes, in every option.** The app keeps the settled 1.5 s rule and its own state shapes (blue "you" → dots). It stops sending silence, which costs money on per-minute STT. Both realtime APIs let the client end the turn itself. OpenAI: VAD "is optional and can be turned off" in speech-to-speech sessions ([VAD guide](https://developers.openai.com/api/docs/guides/realtime-vad)). Gemini: set `automaticActivityDetection.disabled` and send `activityStart`/`activityEnd` yourself ([capabilities](https://aistudio.google.com/docs/live-api/capabilities)). **[Source]** Silero is ~2 MB and light, but it shares the ~5 free cores with BYD's apps. **[Estimate]**
- **Piper TTS: only in the pipeline.** On the car it is est. ~0.2–0.3 s to first audio (offline research, branch `research/offline-talk-stack`). That is about the same as a cloud TTS plus one round trip. It would save ~$0.15–0.35 per Talk. But the voice is flatter, and it needs a second voice (Piper has Russian voices) for the Russian hint. **[Estimate]** Speech-to-speech models make their own voice, so Piper has no role there.
- **Moonshine STT: no.** It is English-only. It would turn "по-русски?" and a whole Russian sentence into English-looking noise. Its accuracy on accented English is unknown.

---

## 2. Latency: end of speech → first answer audio

All rows include the settled **1.5 s silence window** before the app knows the learner has stopped. Phone hotspot round trip (RTT) is taken as **100–300 ms** to the provider. That figure is an **[Estimate]**: no provider publishes it for Uzbek mobile networks, and the inference regions are not stated.

**Published model latency (time to first audio, TTFA)** from Artificial Analysis, averaged over the Big Bench Audio set, measured from their own servers ([speech-to-speech](https://artificialanalysis.ai/speech-to-speech)). **[Measured, third party]**

| Model (reasoning setting) | TTFA | Speech-to-Speech Index | Conversational dynamics | Task success |
|---|---|---|---|---|
| GPT-Realtime-2.1, Minimal | 0.97 s | 70.7 | 92.7% | 89.4% |
| GPT-Realtime-2.1, High | 1.21 s | 75.2 | 95.7% | 91.5% |
| GPT-Realtime-2.1 Mini, Minimal | 0.85 s | 54.4 | 91.8% | 76.7% |
| GPT-Realtime-2.1 Mini, High | 4.28 s | – | 91.7% | – |
| Gemini 3.8 Live | 1.18 s | 74.5 | 96.1% | 93.2% |
| Gemini 3.1 Flash Live, Minimal | 0.96 s | 64.2 | 72.3% | 74.6% |
| GPT-Live-1 (Sol, low) | 1.24 s | 80.1 | 97.3% | 90.9% |

Note the Mini at High effort: 4.28 s. Reasoning effort must stay low.

**Turn budget [Estimate]:**

| Step | OpenAI Realtime 2.1 | Gemini 3.8 Live | Pipeline (Flux → Haiku 5.5 → Sonic) |
|---|---|---|---|
| Silence window (Silero, on device) | 1.5 s | 1.5 s | 1.5 s |
| Last audio + commit/activityEnd reach server | 0.05–0.15 s | 0.05–0.15 s | 0.05–0.15 s |
| Server: final transcript | – | – | ~0.1–0.3 s (Flux claims "~260ms end-of-turn detection" ([Flux](https://developers.deepgram.com/docs/flux/quickstart)) **[Source]**) |
| Server: model to first audio / first text | 0.85–1.2 s (TTFA above) | ~1.2 s | Haiku 5.5 first sentence: no published figure. Est. 0.4–0.8 s incl. one RTT |
| TTS to first audio | in model | in model | Sonic: no figure in Cartesia's docs; third parties report ~90–190 ms. ElevenLabs Flash "~75ms" ([pricing](https://elevenlabs.io/pricing/api)) **[Source]**. Plus one RTT: 0.2–0.5 s |
| First audio back to car + start playback | 0.1–0.3 s | 0.1–0.3 s | 0.1–0.2 s |
| **Total** | **~2.7–3.5 s** | **~2.9–3.7 s** | **~2.9–4.0 s** |

All three fit the 3–5 s bar on paper. The pipeline has the least margin, because each hop pays the hotspot round trip again. A small relay server near the providers would remove two car round trips but adds something to run. **[Estimate]**

The 1.5 s silence window is the largest single item. Both realtime APIs advise less: Gemini recommends 500–800 ms of silence ([capabilities](https://aistudio.google.com/docs/live-api/capabilities)) **[Source]**. That is a Talk-screen decision, not this ticket's, but it is the cheapest second to win back.

---

## 3. Cost

**Assumed Talk [Estimate]:** 20 min ≈ 75 Turns. Each Turn ≈ 6 s of learner speech + 5 s of Anna speech (1–2 short sentences, ~70 characters). System prompt ≈ 1,000 text tokens. Daily use = one such Talk per day, 30 per month.

### 3.1 Prices [Source]

| Item | Price |
|---|---|
| `gpt-realtime-2.1` | audio in $32 / cached in $0.40 / out $64 per 1M tokens; text in $4 / out $24 ([pricing](https://developers.openai.com/api/docs/pricing)) |
| `gpt-realtime-2.1-mini` | audio in $10 / cached in $0.30 / out $20 per 1M; text in $0.60 / out $2.40 |
| OpenAI audio tokens | learner audio "1 token per 100 ms", model audio "1 token per 50ms". Each Response re-sends the whole conversation. Caching is "automatic and best-effort" ([costs](https://developers.openai.com/api/docs/guides/realtime-costs)) |
| `gemini-3.8-live` (paid tier) | audio in $3.00 / out $12.00 per 1M; text in $0.75 / out $4.50. Free tier: no charge ([pricing](https://aistudio.google.com/docs/pricing)) |
| Gemini audio tokens | "approximately 25 tokens per second". "The API charges you per turn for all tokens present in the session context window" ([best practices](https://aistudio.google.com/docs/live-api/best-practices)). A third-party page says caching is unsupported on 3.8 Live ([cekura.ai](https://www.cekura.ai/blogs/gemini-live-api-pricing)) |
| GPT-Live-1 | "$0.05 per minute for the front-end voice layer", backend model billed separately ([announcement](https://openai.com/index/introducing-gpt-live-1-in-the-api/)) |
| Deepgram Flux streaming | English $0.0065/min, multilingual $0.0078/min (promotional). Aura-2 TTS $0.030 per 1k characters. $200 free credit ([pricing](https://deepgram.com/pricing)) |
| Claude Haiku 5.5 (`claude-haiku-5-5`) | $0.10 in / $0.50 out per 1M; cache reads $0.01 (prompts ≤100k tokens) ([pricing](https://platform.claude.com/docs/en/about-claude/pricing)) |
| Cartesia Sonic | 1 credit per character. Pro $5/mo for 100k credits, overage $65 per 1M ([pricing](https://cartesia.ai/pricing)) |
| ElevenLabs Flash TTS | $0.04 per 1k characters ([pricing](https://elevenlabs.io/pricing/api)) |

### 3.2 Per Talk and per month [Estimate]

| Option | One 20-min Talk | 30 Talks / month | Main uncertainty |
|---|---|---|---|
| `gpt-realtime-2.1` | ~$1.0 with good cache hits; ~$2 at moderate hits | ~$30–60 | With **no** cache hits the history re-billing alone is ~$15 per Talk. Watch `cached_tokens` |
| `gpt-realtime-2.1-mini` | ~$0.40 with good cache hits | ~$12–25 | No cache: ~$4.7 per Talk |
| `gemini-3.8-live` (paid) | ~$1.5 with context compression; ~$2.5 without | ~$45–75 | Exact billing formula is not published. Free tier: $0, but see Section 6 |
| GPT-Live-1 | $1.00 + backend model | $30 + backend | Full-duplex; not needed yet |
| Pipeline: Flux multi + Haiku 5.5 + Sonic | ~$0.25–0.45 (Flux ~$0.08–0.16, Haiku ~$0.01–0.03, Sonic ~$0.15–0.35) | ~$8–13 | Flux bills streamed minutes, so stream only during the learner's part |

How the realtime numbers come out. Each Turn adds ~160 OpenAI audio tokens (60 learner + 100 Anna), or ~275 Gemini tokens. Over 75 Turns the history re-sent adds up to ~450k OpenAI input tokens and ~780k Gemini tokens. On OpenAI almost all of that can be cache reads, at 1/80 of the price. On Gemini, compression only shortens the history that gets re-sent.

**Cost levers.**

- **OpenAI.** Never edit history or instructions mid-Talk. That "busts" the cache from that point ([costs](https://developers.openai.com/api/docs/guides/realtime-costs)). Keep `retention_ratio` truncation for very long Talks.
- **Gemini.** Turn on context window compression. It is required anyway past 15 min ([session management](https://aistudio.google.com/docs/live-session)).
- **Any provider.** Set a monthly budget cap on the account.

---

## 4. Following the talk partner rules

- **No provider guarantees rule-following.** Only the ~10-case test set can show it. It must run on a computer before the car, as with the offline models.
- **Text models are easiest to test and to steer.** Claude Haiku 5.5 is the "Fastest" current Claude ([models overview](https://platform.claude.com/docs/en/about-claude/models/overview)). Thinking defaults to adaptive, effort `medium`. It can be turned off at effort `high` or below, which keeps it fast. **[Source]** Running the test set is a few cents. **[Estimate]**
- **Speech-to-speech models take a system prompt** ("instructions" on OpenAI, system instruction on Gemini). Gemini advises ordering it as persona → conversational rules → guardrails, and stressing key rules ([best practices](https://aistudio.google.com/docs/live-api/best-practices)). **[Source]** On the published scores in Section 2, `gpt-realtime-2.1` and Gemini 3.8 Live are close. The Mini scores clearly lower on reasoning (62.7% vs 86.8% at Minimal). **[Measured, third party]**
- **Recasts need the learner's real mistakes.** A speech-to-speech model hears the audio directly. A pipeline sees only the STT text, and some STT models tidy grammar. Whether Flux keeps "Yesterday I go to work" as spoken is **[Unknown]**.
- **"In Russian?" / a Russian sentence.**
  - OpenAI and Gemini understand and speak Russian natively. The Live API lists 99 languages, and native audio "choose[s] the language automatically" ([capabilities](https://aistudio.google.com/docs/live-api/capabilities)). **[Source]**
  - In the pipeline, Flux Multilingual covers 10 languages including Russian, but not Uzbek ([Flux Multilingual](https://deepgram.com/blog/introducing-flux-multilingual)). Sonic 3.6 lists Russian among 44 languages ([Cartesia models](https://docs.cartesia.ai/build-with-cartesia/tts-models/latest)). **[Source]**
- **Gemini 3.8 Live specifics.** "Proactive audio is permanently enabled" ([model page](https://aistudio.google.com/docs/models/gemini-3.8-live.md.txt)). **[Source]** The model may decide not to answer. That suits "wait", but it may also leave the learner in silence. **[Unknown]**

---

## 5. Russian/Uzbek-accented English

- **No provider publishes recognition accuracy for Russian- or Uzbek-accented English.** **[Unknown]**
- **OpenAI** says only that its transcribe models suit "accents", with no numbers. It tells developers to test on their own audio ([realtime transcription](https://developers.openai.com/api/docs/guides/realtime-transcription.md)). **[Source]**
- **Deepgram Nova-3.** A competitor's benchmark gives it 14.4% mean accented-English WER. ElevenLabs Scribe v2 gets 7.0% on the same set, which has no Russian L1 speakers ([AssemblyAI benchmarks](https://assemblyai.com/benchmarks)). **[Measured, third party]**
- **Flux.** No accent figures found.
- **General picture.** On EdAcc (51 L1s), even a Whisper-large-class model averaged 19.7% WER ([arXiv 2303.18110](https://arxiv.org/abs/2303.18110), from the offline research). **[Source]** Expect real errors on the owner's voice from every option.
- **Speech-to-speech degrades more gently.** A wrong word does not become fixed text before the model replies. **[Estimate]**

---

## 6. Android client, API key and data retention

### 6.1 Client on SDK 32

| Option | Transport | Library | Notes |
|---|---|---|---|
| OpenAI Realtime | WebSocket `wss://api.openai.com/v1/realtime?model=gpt-realtime-2.1`, `Authorization: Bearer` ([WebSocket guide](https://developers.openai.com/api/docs/guides/realtime-websocket)) | OkHttp WebSocket (no Google Play services) | OpenAI recommends WebRTC for mobile ([WebRTC guide](https://developers.openai.com/api/docs/guides/realtime-webrtc)). WebSocket is still the better fit here: the app keeps its proven `VOICE_COMMUNICATION` 16 kHz recorder and its own VAD. WebRTC (`io.getstream:stream-webrtc-android`, minSdk 21, Apache-2.0 ([repo](https://github.com/GetStream/webrtc-android))) would take over capture and echo cancellation. The Realtime input sample rate was not confirmed in the pages read; the app may need to resample to 24 kHz **[Unknown]**. Play output with `AudioTrack` |
| Gemini Live | WebSocket, raw 16-bit PCM 16 kHz in (`audio/pcm;rate=16000`), 24 kHz out ([capabilities](https://aistudio.google.com/docs/live-api/capabilities)) | OkHttp WebSocket | Firebase AI Logic has a Kotlin Live API, but it is Preview and does not list `gemini-3.8-live` ([Firebase](https://firebase.google.com/docs/ai-logic/live-api)). The app must handle GoAway and reconnect with a resumption handle every ~10 min |
| Pipeline | Deepgram `wss://api.deepgram.com/v2/listen` (16 kHz linear16, 80 ms chunks); Anthropic Messages streaming over HTTPS; Cartesia WebSocket | OkHttp for all three, or the Anthropic Java SDK for Kotlin | Three connections to keep warm. Open them when Talk starts |

All three providers list **Uzbekistan** as supported. **Russia** is listed by none of them ([OpenAI](https://developers.openai.com/api/docs/supported-countries), [Gemini](https://aistudio.google.com/docs/available-regions), [Anthropic](https://www.anthropic.com/supported-countries)). **[Source]** The phone's SIM country is the IP the providers see. **[Estimate]**

### 6.2 Holding the API key

- **Ephemeral tokens.** Both realtime providers offer them, but a server with the real key must mint them. OpenAI client secrets default to 600 s, maximum 7,200 s ([reference](https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create.md)). Gemini tokens allow 1 min to start a session and 30 min of messages ([ephemeral tokens](https://aistudio.google.com/docs/ephemeral-tokens)). **[Source]**
- **Minting in the app gains nothing.** The app would need the real key to mint them.
- **For a single-user sideloaded app: build the key in.** Read it from an uncommitted `local.properties` into `BuildConfig`. Use a dedicated project or key per provider with a **monthly budget cap**, and rotate it if the car is sold or serviced. **[Estimate]**
- **The risk is limited.** Anyone with the APK or ADB access to the car can extract the key. Google says the same of ephemeral tokens: they "can be extracted from client-side applications". **[Source]** The APK is never published.
- **Later.** Add a tiny token relay only if the app is ever shared.

### 6.3 Data retention [Source]

| Provider | Training | Retention |
|---|---|---|
| OpenAI API | "not used to train or improve OpenAI models" unless you opt in | `/v1/realtime`: abuse-monitoring logs up to 30 days, no application state. Zero Data Retention eligible, by approval ([your data](https://developers.openai.com/api/docs/guides/your-data)) |
| Gemini API, paid tier | Prompts and responses not used to improve products | Logged "for a limited period of time" for abuse detection. The period is not stated ([terms](https://aistudio.google.com/docs/terms)) |
| Gemini API, free tier | Used to "improve, and develop" products; "human reviewers may read" input and output | Google says not to send personal information. **The owner's voice would be in it**, so use the paid tier |
| Anthropic API | "Anthropic may not train models on Customer Content" ([commercial terms](https://www.anthropic.com/legal/commercial-terms)) | Inputs and outputs deleted within 30 days ([privacy](https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data)) |
| Deepgram | Stores "fractional increments of data" for model improvement by default. `mip_opt_out=true` limits retention to "the duration necessary to process the request" ([MIP](https://developers.deepgram.com/docs/the-deepgram-model-improvement-partnership-program)) | Always send `mip_opt_out=true` |
| Cartesia, ElevenLabs | Not checked in this pass | **[Unknown]** |

---

## 7. Why this ranking

1. **`gpt-realtime-2.1`.** One socket, so it is the least exposed to hotspot round trips. Published TTFA is ~1 s. It handles Russian and Uzbek code-switching natively and hears recast-worthy mistakes directly. Caching makes long Talks affordable, and no connection cap is documented. The Mini runs on the same code as a cheaper fallback.
2. **Pipeline (Flux multi + Haiku 5.5 + Sonic 3.6).** It is the cheapest at ~$10/month. It fails differently from speech-to-speech, so it is the right hedge if both realtime models break the partner rules. Text makes the test set trivial to run. The costs: the thinnest latency margin, recasts that depend on STT, and no Uzbek.
3. **Gemini 3.8 Live.** Its quality scores are as good or better, and the free tier is tempting. But it has the 10-min connection limit and the 15-min session cap without compression. Without caching, cost grows with Talk length, and proactive audio is always on.

---

## 8. Measure before and in the car

On a computer first:

1. Run the ~10-case partner test set on `gpt-realtime-2.1` (low effort), `gpt-realtime-2.1-mini`, Gemini 3.8 Live and Haiku 5.5. Feed audio clips of the owner's voice, including the Russian sentence and "по-русски?". Score pass/fail as for the offline models.
2. For the pipeline, check whether Flux keeps the learner's grammar mistakes verbatim (needed for recasts).

In the parked car, on the iPhone hotspot, per Turn:

3. Hotspot RTT to `api.openai.com`, `generativelanguage.googleapis.com`, `api.deepgram.com`, `api.anthropic.com`, `api.cartesia.ai` (TCP/TLS connect time, 20 samples each).
4. End of speech (Silero fires) → commit sent → first audio byte received → first sound from the speakers. Report the median and worst of ~20 Turns against 3–5 s.
5. Recognition errors on the owner's voice: the server's input transcript vs what was said. Also with the engine running and road noise when driving later.
6. Real token use for one full 20-min Talk: `cached_tokens` share on OpenAI, `usageMetadata` on Gemini. Compare it with the console bill to check the Section 3 estimates.
7. One Talk past 20 min: OpenAI session stays up? Gemini GoAway/resumption with no audible gap?
8. Drop and restore the hotspot mid-Talk (walk out of range, or toggle the hotspot) to see the failure and recovery path.
9. CPU and heat with BYD's apps running: Silero + OkHttp + `AudioTrack` should be small next to the dashcam load. Confirm the lesson app and BYD apps are not killed.
10. Mic bleed: does Anna's voice from the car speakers reach the input buffer while the mic is closed (half-duplex) or right after?

## 9. Open questions

- The OpenAI Realtime maximum session length is not in the pages read. A forum report says 30 min. **[Unknown]**
- No published TTFT for Haiku 5.5 or TTFA for Sonic 3.6 in Cartesia's own docs.
- The exact Gemini Live billing formula with compression is not published.
- Retention terms for Cartesia and ElevenLabs.
- Which provider regions serve requests from Uzbekistan, and so the real RTT.
