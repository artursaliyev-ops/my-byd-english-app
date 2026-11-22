# Read the project's free-tier limits in AI Studio
Type: task
Status: resolved
Blocked by:

## Question

HITL. Google doesn't publish free-tier request limits for `gemini-3.1-flash-lite`, `gemini-3.5-flash-lite` or `gemini-3.1-flash-live-preview` ([Find how Notes can be written and carried in on the free tier](01-how-notes-are-written.md)). The owner opens the AI Studio rate-limits page for the Talk key's project and reads the requests per minute, requests per day and tokens per minute for those three models; the answer records them. Done when the numbers are written here, so the spec can say whether one Notes call per Talk fits.

## Answer

Resolved 2026-11-22: read in the built-in browser from AI Studio → Rate Limit, project `gen-lang-client-0535124439` ("Gemini Project", **Free tier**), the project that carries this app's Flash-Lite probes and Live usage. Peak usage is over the last 28 days.

| Model (as AI Studio names it) | RPM | TPM | RPD | Peak use, 28 days |
|---|---|---|---|---|
| Gemini 3.1 Flash Lite (`gemini-3.1-flash-lite`) | 15 | 250K | 500 | 2 RPM, 814 TPM, 3 RPD |
| Gemini 3.5 Flash Lite (`gemini-3.5-flash-lite`) | 15 | 250K | 500 | 2 RPM, 716 TPM, 2 RPD |
| Gemini 3 Flash Live (Talk's `gemini-3.1-flash-live-preview`) | Unlimited | 65K | Unlimited | 7 RPM, 4.02K TPM, 21 RPD |

- **One Notes call per Talk fits easily**: ~1–3 calls a day against 500 RPD and 15 RPM, so even a retry inside the 20 s would fit. The limit is not why a call fails; the August 2026 reports of 429s far below quota still apply.
- **Notes in the Live system instruction fit**: ~420 more tokens per session setup against 65K TPM, with today's peak at 4.02K.
- The fallback `gemini-3.5-flash-lite` has its own separate 15 / 250K / 500 limits.
- AI Studio shows only "Gemini 3 Flash Live" as a 3.x Live model with usage. Mapping it to `gemini-3.1-flash-live-preview` is an inference from the usage, not a label on the page.
