# How Notes can be written and carried in on the free tier

Answers [01 — Find how Notes can be written and carried in](../issues/01-how-notes-are-written.md).
Docs read **2026-11-22** (the `ai.google.dev/gemini-api/docs/*` URLs now 301 to `aistudio.google.com/docs/*`). Probe run the same day with google-genai 3.6.1, the app's exact Live setup (`v1beta`, `gemini-3.1-flash-live-preview`, voice, prompt and opening read from the repo by `tools/talk_test_set/testset.py`), learner clips from `~/talk-probe/cloud/voice_tts`, from a Mac on home broadband (not the car's hotspot).
Tags: **[Source]** = read on the linked page today, **[Probe]** = seen in today's probe (one or a few runs, not a statistic), **[Estimate]** = my arithmetic or judgement, **[Unknown]**.

## Recommendation

**(b) One text-model call after the Talk**, to `gemini-3.1-flash-lite` (fallback `gemini-3.5-flash-lite`) with a JSON schema, sending the old Notes plus the in-memory input and output transcripts; the app replaces the stored Notes only when a valid reply arrives. **Carry Notes in inside the system instruction** of the next Talk's `setup`.

Why, in one line each:
- (a) is synchronous on 3.1: a tool call holds Anna's audio until the app answers it, adds ~0.65 s to that Turn's first audio even with an instant reply, and the model chose what to save poorly [Source, Probe].
- (c) makes Anna say the Notes aloud (audio-only model), returns them only as an ASR transcript of her own speech, and depends on what is left in the Live context [Source, Probe].
- (b) is one request per Talk on a separate model quota, returns schema-checked JSON, got the correction and all five never-write topics right in 4/4 runs, and its failure mode (no reply) maps directly onto "the Talk is silently not kept" [Source, Probe].

---

## (a) Function call during the Talk (`save_note` tool)

Facts:
- `gemini-3.1-flash-live-preview` supports function calling [Source](https://aistudio.google.com/docs/models/gemini-3.1-flash-live-preview), but **synchronous only**: async (`behavior: NON_BLOCKING`, `scheduling` INTERRUPT / WHEN_IDLE / SILENT) is listed for `gemini-3.8-live` and `gemini-2.5-flash-live-preview`, not 3.1. For 3.1, "execution pauses until you send the tool response" [Source](https://aistudio.google.com/docs/live-api/tools); the model page: it "will not start responding until you've sent the tool response" [Source](https://aistudio.google.com/docs/models/gemini-3.1-flash-live-preview).
- The Live API has no automatic tool handling; the client must answer each `toolCall` with `toolResponse` by `id` [Source](https://aistudio.google.com/docs/live-api/tools). `toolCallCancellation` arrives only when the client interrupts a server turn [Source](https://aistudio.google.com/api-reference/live).
- Probe, three Turns with a `save_note(fact)` tool and one added prompt rule [Probe]:

  | Turn | Tool call at | Response sent | First audio | Note |
  |---|---|---|---|---|
  | opening (no call) | — | — | 0.61 s | baseline |
  | "My day is good. I go to work now." | 0.64 s, before any audio | after a deliberate 1.5 s wait, at 2.14 s | **2.75 s** | audio waited the whole time |
  | "By the way, I have two children." | 0.58 s, before any audio | at once, 0.58 s | **1.26 s** | +0.65 s vs baseline |

  It saved "The learner is driving to work now." (not a lasting fact) and "The learner has two children." (third person, not the second-person lines Settings shows, see [04](../issues/04-notes-in-settings.md)).
- Prompt tokens grew faster in the tool session (2,615 → 3,135 by Turn 3, vs ~1,090 after the opening) [Probe]; tool declarations and call/response parts stay in context [Estimate].

Assessment:
- **Reliability:** works, but adds latency on exactly the Turns where the learner shares something, and any bug in answering the call freezes Anna (no audio until the response) [Probe, Estimate]. The model decides what is a fact; no central place to apply the never-write list. Adding a tool and a rule to Anna's prompt also means the whole test set must be re-run [Estimate].
- **Free-tier cost:** no extra requests (same session); more context tokens per Turn [Probe]. Live free limits are unpublished [Source](https://aistudio.google.com/docs/rate-limits).
- **Corrections / never-write:** only by prompt rules inside Anna's prompt plus app-side filtering of each `fact` string; a correction needs a second tool (`replace_note`) or a merge step later anyway [Estimate].
- **Car switched off:** facts saved before the switch-off survive if the app writes each one at once, which **conflicts** with the settled rule "if writing fails, that Talk is silently not kept" (partial Talks would leak in) unless the app buffers them and commits at the end, which then has the same switch-off loss as (b) [Estimate].

## (b) One text-model call after the Talk

Facts:
- Free-of-charge on the free tier, Standard: `gemini-3.1-flash-lite` (Stable), `gemini-3.5-flash-lite` (Stable), `gemini-3.8-flash`, `gemini-3.6-flash`, `gemini-4-hydrogen`; the free tier is "Used to improve our products: Yes" [Source](https://aistudio.google.com/docs/pricing), [Source](https://aistudio.google.com/docs/models).
- Both Flash-Lite models: structured outputs supported, thinking supported, 1M input tokens, text out [Source](https://aistudio.google.com/docs/models/gemini-3.1-flash-lite), [Source](https://aistudio.google.com/docs/models/gemini-3.5-flash-lite).
- **Free-tier RPM/RPD for these models are not published**; the page says to read your project's limits in AI Studio, limits are "per project, not per API key", RPD resets at midnight Pacific [Source](https://aistudio.google.com/docs/rate-limits). A 2026-07 forum report says free requests are limited to 20 on all models except `gemini-3.1-flash-lite` [Source, unofficial](https://discuss.ai.google.dev/t/ai-studio-shows-tier-1-postpay-but-api-returns-generate-content-free-tier-requests-limit-20-on-all-models-except-gemini-3-1-flash-lite/175415). One call per Talk is ~1–3 requests/day either way [Estimate]. Read the real numbers in AI Studio before building [Unknown].
- Structured output: `responseMimeType: application/json` + a JSON Schema; supported keywords include `required`, `enum`, `items`, and `minItems`/`maxItems` (**enforced**); `minLength`/`maxLength` are **ignored** (validate in the app); "Schema-valid output can still be semantically wrong" [Source](https://aistudio.google.com/docs/structured-output). In `v1beta` `generateContent`, the field is `generationConfig.responseJsonSchema`, and `thinkingConfig.thinkingLevel` is the Gemini 3 control [Source](https://aistudio.google.com/api-reference/generate-content). The structured-output page itself now shows the Interactions API (`response_format`); `generateContent` with `responseJsonSchema` worked in the probe [Probe].
- Probe: a 13-line transcript with a correction ("No, I have three kids, not two"), back pain, Friday prayer, a street address and a phone price/salary; old facts "taxi driver / two kids / BYD Song Plus"; schema `{facts: string[] maxItems 20, talk_topics: string}` [Probe]:

  | Model, thinking | Latency | Correction | Never-write topics | Tokens in / out (+thoughts) |
  |---|---|---|---|---|
  | 3.1-flash-lite, minimal | 7.40 s (first call) | replaced | none written | 407 / 54 |
  | 3.1-flash-lite, low | 1.70 s | replaced | none written | 407 / 81 (+118) |
  | 3.5-flash-lite, minimal | 8.49 s (first call) | replaced | none written | 407 / 74 |
  | 3.5-flash-lite, low | 0.95 s | replaced | none written | 407 / 57 |

  The two slow runs were each model's first call of the session; whether that is a cold start or chance is [Unknown]. Treat latency as **1–9 s, with a timeout of ~20 s** [Estimate].

Assessment:
- **Reliability:** highest of the three. One request, JSON parsed against a schema, the app validates lengths and count and can reject the whole reply. The model sees the full transcript the app kept, not a compressed Live context [Probe, Estimate].
- **Free-tier cost:** one `generateContent` call per Talk on a text-model quota, separate from the Live model's [Source: limits vary by model](https://aistudio.google.com/docs/rate-limits). Input for a ~20-min Talk: ~75 Turns × ~2 lines × ~25 tokens ≈ **4–8K tokens** plus ~400 for old Notes [Estimate].
- **Corrections / never-write:** in the instruction ("replace, never keep both"; "never write health, religion, politics, money, exact addresses") and the model returns the **whole new list**, so a correction is a replacement, not an append. The app can add a cheap safety net (drop facts matching digits-plus-street words, currency, a few keywords) [Estimate]. Only the old Notes and today's transcript are sent; nothing is stored apart from the reply.
- **Car switched off:** the call happens after the Talk ends (seconds). If power is lost before the reply, nothing is written and old Notes stay, which is exactly the settled failure rule; the transcripts are in memory only, so that Talk is lost [Estimate]. Sending the request at the earliest end signal ("bye", End Talk, the cap) shortens the window; when to write is [02](../issues/02-when-notes-are-written.md).
- **Data note:** the transcripts already go to Google during the Talk (input/output transcription); this sends the same text once more under the same Unpaid Services terms [Source](https://aistudio.google.com/docs/pricing).

## (c) Asking the Live model at the end

Facts:
- Native audio Live models "only support `AUDIO` response modality. If you need the model response as text, use the output audio transcription feature." [Source](https://aistudio.google.com/docs/live-api/capabilities).
- Probe: after "By the way, I have two children.", the bare text `(The Talk is over. List the new facts the learner told you today, one short line each, starting with 'NOTE:'.)` gave output transcript "NOTE: They have two children." with **2.4 s of spoken audio** [Probe].
- Sliding-window compression keeps the system instruction and drops the oldest turns once context passes `triggerTokens` (default 80% of the 131K window) [Source](https://aistudio.google.com/api-reference/live), [Source](https://aistudio.google.com/docs/models/gemini-3.1-flash-live-preview). A 20–30 min Talk probably stays below that (~30–50K tokens) [Estimate], but a resumed or new session after No signal loses the earlier turns entirely [Estimate].

Assessment:
- **Reliability:** lowest. The output is ASR of Anna's own speech (wording and punctuation vary), no schema, one more audio Turn that the app must mute, and it relies on Anna's prompt obeying a non-spoken task without breaking the "never mention brackets" rule. After "bye" the learner hears Anna go quiet or, if not muted, hears her read the Notes [Probe, Estimate].
- **Free-tier cost:** no extra request; one more Live Turn of audio output [Estimate].
- **Corrections / never-write:** only via Anna's prompt; old Notes are in her system instruction so she could return the merged list, but output length grows with ~20 facts read aloud (~15–25 s of audio) [Estimate].
- **Car switched off:** same as (b), lost; but it can only run while the Live session is still open, so a Talk ended by No signal can never be written [Estimate].

---

## Carrying Notes into the next Talk

Probe: same prompt with ~20 facts + 10 dated topic lines (~1.2 KB) appended [Probe]:

| Setup | connect → `setupComplete` | Opening first audio | Prompt tokens after opening |
|---|---|---|---|
| base prompt (×2) | 0.72 / 0.66 s | 0.69 / 0.90 s | 965 |
| Notes in system instruction (×2) | 0.68 / 0.68 s | 0.71 / 0.65 s | 1,387 |
| Notes as bare text line before the opening | 0.66 s | 0.76 s | 1,386 |

- **Setup time and first hello: no measurable change**; ~**420 tokens** more context. That is re-read every Turn: 75 Turns × 420 ≈ **~32K extra input tokens per Talk** [Estimate], small next to the audio context; Live free token limits are unpublished [Source](https://aistudio.google.com/docs/rate-limits).
- **Recommend the system instruction**, because: sliding-window compression always keeps it at the start [Source](https://aistudio.google.com/api-reference/live), while a text turn can be dropped; the app already resends the same `setup` on every reconnect (spec §3), so Notes come back on resumption and on a new session after No signal with no extra step; and a bare `realtimeInput.text` is a user turn that 3.1 may answer by itself (the one run greeted "nice to see you", unclear if it replied to the Notes line) [Probe, Estimate]. `clientContent` history seeding exists but needs `history_config.initial_history_in_client_content` on 3.1 [Source](https://aistudio.google.com/docs/live-api/capabilities); nothing gained over the system instruction [Estimate].
- Keep the Notes block clearly framed (e.g. "Notes from earlier Talks. Use them lightly; don't ask again what you know.") and in the same second-person lines Settings shows. How Anna uses them is [03](../issues/03-anna-with-notes.md).

## Limits that matter

- Free-tier RPM/RPD for Flash-Lite and Live are **unpublished** and "not guaranteed" [Source](https://aistudio.google.com/docs/rate-limits); check the project's AI Studio limits page.
- `gemini-3.1-flash-live-preview` is "legacy"; Google recommends `gemini-3.8-live`, which **does** support async (NON_BLOCKING) function calling [Source](https://aistudio.google.com/docs/models/gemini-3.1-flash-live-preview), [Source](https://aistudio.google.com/docs/live-api/tools). That would reopen (a), but not its prompt and partial-Talk problems.
- `maxLength` is ignored in schemas: the app must cap fact length itself [Source](https://aistudio.google.com/docs/structured-output).
- Free-tier data may be used for training and read by reviewers; Notes are personal facts [Source](https://aistudio.google.com/docs/pricing).

## Still unmeasured

- Text-call latency over the car's hotspot, and whether the 7–8 s first calls repeat [Unknown].
- The real free-tier limits on this project for `gemini-3.1-flash-lite` / `gemini-3.5-flash-lite` [Unknown].
- Accuracy on a real ~75-Turn transcript with noisy ASR, Russian/Uzbek lines and several corrections; today's test was one short scripted transcript [Unknown].
- Whether Notes in the prompt change the test set's pass rate (not run with Notes) [Unknown].
- Probe script: `notes_probe.py` in the session scratchpad (not committed); Live sessions were 3 + 5 + 1, text calls 4.
