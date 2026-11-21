# Free (and almost-free) cloud options for Talk

Researched 2026-11-21 against provider pages. Free tiers change often; re-check before building.
Tags: **[Source]** = read on the linked page today, **[Estimate]** = my arithmetic or judgement, **[Unknown]** = not published or not found.

## Workload being sized

| Quantity | Per Talk (≈75 Turns) | Per month (30 Talks) |
|---|---|---|
| Learner audio (75 × ~6 s) | ~7.5 min | ~225 min (3.75 h) [Estimate] |
| Learner audio if every request is billed at a 10 s minimum (Groq) | ~12.5 min | ~375 min [Estimate] |
| Partner text (75 × ~120 chars, 1–2 short sentences) | ~9K chars | ~270K chars [Estimate] |
| Partner audio (75 × ~5 s) | ~6 min | ~190 min [Estimate] |
| LLM input (system prompt + capped history ≈ 2K tokens/Turn) | ~150K tokens | ~4.5M tokens [Estimate] |
| LLM request rate | ~4 requests/min, 75/day | — [Estimate] |
| Realtime session time (speech-to-speech) | ~20 min connected | ~600 min [Estimate] |

---

## 1. Speech-to-speech / realtime

### Gemini Live API (Google AI Studio key): free tier exists
- Free of charge (input and output) on the Standard free tier for `gemini-3.1-flash-live-preview`, `gemini-3.8-live`, `gemini-3.8-live-extended-thinking`, `gemini-2.5-flash-native-audio-preview-12-2025` [Source](https://aistudio.google.com/docs/pricing).
- **Limits are not published.** The rate-limit doc lists no free-tier numbers for Live/Flash/TTS models and says to read your own project's limits in AI Studio; "Specified rate limits are not guaranteed" [Source](https://aistudio.google.com/docs/rate-limits). Limits are per project; RPD resets at midnight Pacific [Source](https://aistudio.google.com/docs/rate-limits).
- Concurrent sessions: a Google staff reply (2026-10-12) confirms "Free tier projects have a limit on concurrent Live API sessions" without a number; a developer saw 5–6 sockets accepted, the next rejected (close 1011, quota message); console showed 1M input TPM and unlimited RPM/RPD for the native-audio model [Source](https://discuss.ai.google.dev/t/native-audio-live-free-tier-setup-quota-rejection-below-visible-tpm-which-admission-metric-applies/186840). One session per day is far below this [Estimate].
- Session limits: audio-only session 15 min without compression; connection ~10 min; context-window compression makes sessions unlimited; resumption tokens valid 2 h [Source](https://aistudio.google.com/docs/live-session). A 20-min Talk therefore **needs compression + session resumption** [Estimate].
- Data terms (Unpaid Services): content is used "to provide, improve, and develop" Google products; "human reviewers may read, annotate, and process your API input and output" (disconnected from account first); "Do not submit sensitive, confidential, or personal information to the Unpaid Services"; pricing table marks "Used to improve our products: Yes" for free, "No" for paid [Source](https://aistudio.google.com/docs/terms), [Source](https://aistudio.google.com/docs/pricing). The EEA/CH/UK exception does not cover Uzbekistan [Source](https://aistudio.google.com/docs/terms).
- Uzbekistan: listed as an available country (Russia is not) [Source](https://aistudio.google.com/docs/available-regions). Must be 18+ [Source](https://aistudio.google.com/docs/terms).
- Card: free tier needs no billing [Estimate, consistent with "Free" usage tier in rate-limit doc].
- Fit: likely fits 1 Talk/day, but no documented daily cap, so it can change without notice [Estimate]. Latency: best of all options, ~1–2.5 s end-of-speech to first audio over a hotspot [Estimate]. Android: WebSocket (BidiGenerateContent), works from OkHttp; no backend needed if the key lives on the head unit [Estimate].
- Paid fallback on the same code: 3.1-flash-live audio $0.005/min in, $0.018/min out [Source](https://aistudio.google.com/docs/pricing) → roughly $0.25–1 per Talk once context re-billing is counted, ~$8–30/month [Estimate].

### OpenAI Realtime: no free option
- Realtime model page shows "Not supported" for Free tier [Source via search snippet](https://platform.openai.com/docs/models/gpt-realtime).
- The complimentary-tokens-for-data-sharing programme needs a positive balance and eligibility, and lists only text model groups (gpt-5.5, gpt-4.1, o3 / gpt-5.4-mini, gpt-4.1-mini, o4-mini); realtime/audio not mentioned [Source](https://help.openai.com/en/articles/10306912-sharing-feedback-evaluation-and-fine-tuning-data-and-api-inputs-and-outputs-with-openai).

### Others
- **Deepgram Voice Agent API**: $0.075/min, payable from the $200 no-card, no-expiry sign-up credit [Source](https://deepgram.com/pricing) → ~600 min/month = $45 → credit lasts ~4.4 months [Estimate].
- **Cartesia voice agents**: $1/month agent credit (~33 min) on Free [Source](https://cartesia.ai/pricing) → ~1.5 Talks. Does not fit.
- **Azure Voice Live**: "Not applicable" on F0 [Source](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-services-quotas-and-limits). Not free.
- **ElevenLabs Agents**: Free plan is 10K credits/month shared across products; agent minutes not stated [Source](https://elevenlabs.io/pricing). Does not fit [Estimate].

---

## 2. Speech to text

| Provider | Free allowance | Fits 3.75 h/month? | Card | Data use |
|---|---|---|---|---|
| **Groq Whisper** (`whisper-large-v3-turbo`, `whisper-large-v3`) | Free plan: 20 RPM, 2K RPD, 7.2K audio-s/hour, 28.8K audio-s/day; 25 MB file; 10 s minimum billed [Source](https://console.groq.com/docs/rate-limits), [Source](https://console.groq.com/docs/speech-to-text) | **Yes, ~38×** (750 s/day vs 28,800) [Estimate] | No [Estimate] | No retention by default; up to 30 days logs for abuse/reliability; ZDR toggle for all customers; stored in US GCP [Source](https://console.groq.com/docs/your-data) |
| **Cloudflare Workers AI** `@cf/openai/whisper-large-v3-turbo` | 10,000 Neurons/day free (reset 00:00 UTC); 46.63 Neurons/audio-min [Source](https://developers.cloudflare.com/workers-ai/platform/pricing/); ASR 720 req/min [Source](https://developers.cloudflare.com/workers-ai/platform/limits/) | **Yes** (~350 Neurons/day) [Estimate] | No [Estimate] | Not used for training; inputs not retained after the request [Source](https://developers.cloudflare.com/workers-ai/platform/data-usage/) |
| **Azure Speech F0** | 5 audio h/month real-time STT; 1 concurrent request; no batch [Source](https://azure.microsoft.com/en-us/pricing/details/cognitive-services/speech-services/), [Source](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-services-quotas-and-limits) | **Yes, narrowly** — only if you send per-Turn audio, not a continuous 20-min stream (20 min × 30 = 10 h) [Estimate] | Yes (Azure account card check; Uzbekistan eligible, Visa/MC) [Source via search](https://learn.microsoft.com/en-us/azure/cost-management-billing/manage/supported-payment-methods) | Real-time: processed in memory, "no data trace" [Source](https://learn.microsoft.com/en-us/azure/ai-foundry/responsible-ai/speech-service/speech-to-text/data-privacy-security) |
| **Google Cloud STT** | 60 min/month free [Source](https://cloud.google.com/speech-to-text/pricing) | **No** (need ~225) | Yes (billing) | Paid service; cheaper "with data logging" SKU is opt-in [Source](https://cloud.google.com/speech-to-text/pricing) |
| **Deepgram Nova-3** | One-off $200 credit, no card, no expiry; $0.0043/min pre-recorded, $0.0048/min streaming [Source](https://deepgram.com/pricing) | Yes: ~$1/month → years [Estimate] | No [Source](https://deepgram.com/pricing) | Model Improvement Program is **on by default**; send `mip_opt_out=true` per request (pricing impact not stated) [Source](https://developers.deepgram.com/docs/the-deepgram-model-improvement-partnership-program) |
| **AssemblyAI** | One-off $50, no card; streaming $0.30/h billed on session time; 5 new streams/min on free [Source](https://www.assemblyai.com/pricing) | Yes: ~$1–3/month [Estimate]; expiry [Unknown] | No | [Unknown] (not checked) |
| **Speechmatics** | One-off $100, no card, valid 12 months; 2 concurrent real-time sessions; access paused when credit ends [Source](https://www.speechmatics.com/pricing) | Yes for 12 months [Estimate] | No | [Unknown] |
| **ElevenLabs Scribe** | Free 10K credits/month; Scribe ~330 credits/min → ~30 min [Source + Estimate](https://elevenlabs.io/pricing) | No | No | [Unknown] |
| **Cartesia Ink** | ~1 h 51 min/month [Source](https://cartesia.ai/pricing) | No | No | [Unknown] |

---

## 3. Text LLM

| Provider | Free limits | Fits ~75 req/day, ~150K tokens/day? | Notes |
|---|---|---|---|
| **Groq** `openai/gpt-oss-20b`, `openai/gpt-oss-120b`, `qwen/qwen3.8-27b` | Free plan each: 30 RPM, 1K RPD, **8K TPM**, 200K TPD [Source](https://console.groq.com/docs/rate-limits) | **Yes, but TPM is the tight limit**: ~4 req/min × 2K tokens ≈ 8K TPM. Cap system prompt + history at ~1.2–1.5K tokens/request and use low reasoning effort [Estimate] | Same data terms as Groq STT [Source](https://console.groq.com/docs/your-data). OpenAI-compatible HTTPS. Very fast TTFT (~0.2–0.5 s) [Estimate] |
| **Cloudflare Workers AI** e.g. `@cf/google/gemma-4-26b-a4b-it` (9,091/27,273 Neurons per M in/out), `@cf/openai/gpt-oss-20b` (18,182/27,273), `@cf/meta/llama-3.3-70b-instruct-fp8-fast` (26,668/204,805) | 10K Neurons/day shared with all Workers AI use; text-gen 300 req/min [Source](https://developers.cloudflare.com/workers-ai/platform/pricing/), [Source](https://developers.cloudflare.com/workers-ai/platform/limits/) | **Yes**: Gemma 4 26B ≈ 1.6K Neurons/day; gpt-oss-20b ≈ 3K; Llama 3.3 70B ≈ 5.5K [Estimate] | No training [Source](https://developers.cloudflare.com/workers-ai/platform/data-usage/). REST: `POST https://api.cloudflare.com/client/v4/accounts/{ACCOUNT_ID}/ai/run/@cf/...` with `Authorization: Bearer` [Source](https://developers.cloudflare.com/workers-ai/get-started/rest-api/) |
| **Gemini API** Flash / Flash-Lite | Free of charge per pricing table (`gemini-3.8-flash`, `gemini-3.5-flash-lite`, `gemini-3.1-flash-lite`, …) [Source](https://aistudio.google.com/docs/pricing); numeric limits unpublished [Source](https://aistudio.google.com/docs/rate-limits). Forum reports: free-tier limit of 20 on all models except `gemini-3.1-flash-lite` (2026-07) [Source](https://discuss.ai.google.dev/t/ai-studio-shows-tier-1-postpay-but-api-returns-generate-content-free-tier-requests-limit-20-on-all-models-except-gemini-3-1-flash-lite/175415) | **Probably not** for Flash (20/day); Flash-Lite [Unknown] | Same training/human-review terms as Live |
| **OpenRouter** `:free` models | 20 RPM; 50 RPD, or 1,000 RPD after buying ≥$10 credits once [Source](https://openrouter.ai/docs/api/reference/limits) | No at 50 RPD; yes after a one-time $10 [Estimate] | Per-model upstream providers may log/train [Unknown] |
| **Cerebras** | Free Trial = $5 credit expiring after 30 days; gpt-oss-120b 5 RPM [Source](https://inference-docs.cerebras.ai/support/rate-limits) | Not a lasting free tier | — |
| **GitHub Models** | Retired 2026-07-30 (API no longer available) [Source](https://docs.github.com/en/github-models/use-github-models/prototyping-with-ai-models) | No | — |
| **Mistral** Experiment plan | Free API access exists; numeric limits not published; training opt-out toggle in Admin > Privacy; Labs models cannot opt out [Source via search](https://help.mistral.ai/en/articles/455207-can-i-opt-out-of-my-input-or-output-data-being-used-for-training) | [Unknown] | Phone verification requirement [Unknown] |

---

## 4. Text to speech

| Provider | Free allowance | Fits ~270K chars/month? | Card | Notes |
|---|---|---|---|---|
| **Google Cloud TTS** | Monthly free: Chirp 3 HD 1M chars; Neural2 1M; WaveNet 4M; Standard 4M; Studio 1M. Gemini-TTS on Cloud: no free usage [Source](https://cloud.google.com/text-to-speech/pricing) | **Yes** (Chirp 3 HD ×3.7; WaveNet ×15) [Estimate] | **Yes** — "You must enable billing" [Source](https://cloud.google.com/text-to-speech/pricing) | Paid Cloud service terms (not the Unpaid Gemini terms). Uzbekistan Cloud usage is charged 12% VAT, implying supported billing [Source via search](https://docs.cloud.google.com/billing/docs/resources/vat-overview). REST `text:synthesize`, plus streaming for Chirp 3 HD [Estimate] |
| **Azure neural TTS F0** | 0.5M chars/month [Source](https://azure.microsoft.com/en-us/pricing/details/cognitive-services/speech-services/); 20 transactions/60 s [Source](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-services-quotas-and-limits) | **Yes** (×1.8) [Estimate]; ~4 req/min fits 20/min | Yes | No data persisted in TTS [Source](https://learn.microsoft.com/en-us/azure/ai-foundry/responsible-ai/speech-service/speech-to-text/data-privacy-security). REST or Speech SDK (Android AAR) |
| **Deepgram Aura-2** | From $200 one-off credit; $0.030/1K chars [Source](https://deepgram.com/pricing) | ~$8/month → ~2 years of credit [Estimate] | No | MIP default on; opt out per request [Source](https://developers.deepgram.com/docs/the-deepgram-model-improvement-partnership-program) |
| **Speechmatics TTS** | "First 1 million characters free" (page does not tie it to the Free plan) [Source](https://www.speechmatics.com/pricing) | Possibly ~3.5 months [Estimate] | No | Conditions [Unknown] |
| **Cloudflare Workers AI** | MeloTTS deprecated, retirement date 2026-11-20 [Source](https://developers.cloudflare.com/workers-ai/models/melotts/). Aura-1 1,363.64 Neurons/1K chars; Aura-2 2,727.27 [Source](https://developers.cloudflare.com/workers-ai/platform/pricing/) | **No**: ~9K chars/day × 1.36 ≈ 12K Neurons > 10K daily [Estimate] | No | — |
| **Gemini TTS** (AI Studio free) | Free of charge per pricing table (`gemini-3.8-flash-tts`, `-lite-tts`) [Source](https://aistudio.google.com/docs/pricing); forum reports of 10 RPD (3.1 TTS) and 100 RPD enforced (3.8 Flash TTS, 2026-10) [Source](https://discuss.ai.google.dev/t/gemini-3-8-flash-tts-api-enforces-100-rpd-despite-ai-studio-showing-10k-rpd/187223) | Borderline/unreliable; also adds a 4th network hop of latency [Estimate] | No | Free-tier training/human review |
| **Groq Orpheus** `canopylabs/orpheus-v1-english` | Free: 10 RPM, 100 RPD, 1.2K TPM, 3.6K TPD; 200 chars/request; WAV only [Source](https://console.groq.com/docs/rate-limits), [Source](https://console.groq.com/docs/text-to-speech/orpheus) | Probably **no** if tokens ≈ chars (3.6K/day vs ~9K) [Estimate; token unit Unknown] | No | — |
| **ElevenLabs** | 10K credits/month (~10K chars), no rollover; commercial licence only from Starter [Source](https://elevenlabs.io/pricing) | No (~1 Talk) | No | — |
| **Cartesia** | 20K credits/month (~20K chars), 2 concurrent [Source](https://cartesia.ai/pricing) | No (~2 Talks) | No | — |

---

## 5. Availability from Uzbekistan
- Gemini API / AI Studio: listed [Source](https://aistudio.google.com/docs/available-regions).
- Groq: a community answer lists restricted regions as Greater China, Russia, Syria, Iran, North Korea, Cuba — Uzbekistan not among them; not confirmed in the Services Agreement [Source, unofficial](https://community.groq.com/t/what-geographic-regions-does-groqcloud-serve/833).
- Azure: Uzbekistan on the free-account list, Visa/Mastercard accepted, prepaid/virtual cards not [Source via search](https://learn.microsoft.com/en-us/azure/cost-management-billing/manage/supported-payment-methods).
- Google Cloud billing: Uzbekistan not on the restricted list; 12% VAT applies [Source via search](https://docs.cloud.google.com/billing/docs/resources/vat-overview).
- Cloudflare, Deepgram, AssemblyAI, Speechmatics: [Unknown]; no restriction seen.

---

## 6. Recommended fully-free combinations

### Option A — Gemini Live free tier (simplest, fastest)
`gemini-3.1-flash-live-preview` (or `gemini-2.5-flash-native-audio-preview-12-2025`) over WebSocket, with context-window compression and session resumption for the 20-min Talk.
- Cost $0. Latency ~1–2.5 s [Estimate]. One WebSocket, same shape as the prior OpenAI Realtime plan.
- Risks: (1) **the owner's voice and conversation may be used for training and read by human reviewers**, and Google's own terms say not to submit personal information to Unpaid Services [Source](https://aistudio.google.com/docs/terms); (2) free limits are undocumented and "not guaranteed", Google has cut free quotas before (forum threads on the 2025 cut to 20 RPD) [Source](https://discuss.ai.google.dev/t/gemini-rate-limit-20-rpd/111274); (3) preview models get renamed/retired; (4) 15-min session cap without compression.
- Escape hatch: same code on the paid tier is ~$8–30/month and switches data use off [Estimate].

### Option B — Free pipeline with no training on the voice
1. On-device VAD (end of speech) → upload the Turn's audio (Opus/M4A, ~50 KB).
2. **Groq `whisper-large-v3-turbo`** (free) → text.
3. **Groq `openai/gpt-oss-20b`** or `qwen/qwen3.8-27b` (free; history capped to fit 8K TPM). Backup: **Cloudflare `@cf/google/gemma-4-26b-a4b-it`** (free 10K Neurons/day), which also backs up STT via `whisper-large-v3-turbo`.
4. **Google Cloud TTS Chirp 3 HD** (1M chars/month free) or **Azure neural TTS F0** (0.5M chars/month).
- Cost $0 at this volume; TTS needs a billing-enabled account with a card (Visa/MC) [Source](https://cloud.google.com/text-to-speech/pricing). Set a budget alert / cap.
- Latency per Turn [Estimate]: VAD tail 0.5–0.7 s + upload 0.2–0.4 s + Whisper 0.2–0.5 s + LLM 0.3–0.8 s + TTS 0.3–0.7 s + first-audio playback 0.1–0.2 s ≈ **1.8–3.3 s** over a hotspot (Groq and Google TTS endpoints are not in Central Asia, so each hop adds ~150–300 ms RTT). Fits the 3–5 s bar.
- Risks: three providers, three keys on the car; Groq free TPM is the tight limit; Groq free terms can change; no SLA; quality of turn-taking is worse than a native speech-to-speech model (no barge-in unless built).
- Card-free variant: replace step 4 with **Deepgram Aura-2** from the $200 no-card credit (~$8/month, ~2 years) and pass `mip_opt_out=true` [Estimate/Source above].

## 7. Cheapest "almost free" if neither fits
- **Gemini Live on the paid tier** (same code as Option A): ~$0.005/min audio in + $0.018/min audio out [Source](https://aistudio.google.com/docs/pricing) → ~$8–30/month [Estimate], with no training use.
- **Pipeline on paid usage**: Groq Whisper turbo $0.04/h [Source](https://console.groq.com/docs/speech-to-text) (~$0.25/month) + Groq LLM (cents) + Google TTS inside the free 1M chars → **under ~$1/month** [Estimate]. This is the cheapest stable option.
- **Deepgram $200 credit for STT + TTS** (Nova-3 + Aura-2, ~$9/month) → ~22 months free, no card [Estimate].

## Open questions to check in the consoles
- Actual Gemini free-tier limits for the Live and Flash-Lite models in AI Studio's rate-limit page [Unknown].
- Whether Groq counts the 10 s minimum against audio-seconds/day (harmless either way) [Unknown].
- Groq Orpheus token unit for TPD [Unknown].
- AssemblyAI / Speechmatics / Deepgram data-training defaults and Uzbekistan sign-up [Unknown].
