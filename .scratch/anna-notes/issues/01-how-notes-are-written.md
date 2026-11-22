# Find how Notes can be written and carried in on the free tier
Type: research
Status: resolved
Blocked by:

## Question

AFK. How can Talk produce Notes and give them to the next Talk, on the Gemini free tier with the app's current setup (Live API `v1beta`, `gemini-3.1-flash-live-preview`, audio out, input and output transcription on)? Compare at least:

- (a) **A function call during the Talk**: Anna's Live session calls a tool like `save_note` when she learns something. Does 3.1 flash live support tools with audio out, and does a tool call delay or break her audio Turn?
- (b) **One text-model call after the Talk**: the app sends the in-memory transcripts (input and output transcription) plus the old Notes to a free-tier text model, which returns the updated Notes as JSON. Which model, its free-tier requests per day and per minute, typical latency, and whether a structured (JSON schema) response is available.
- (c) **Asking the Live model itself at the end** (a bracketed control line before closing), reading its reply from the output transcript.

For each: reliability, cost against the free-tier limits Talk already uses, how corrections ("No, I have three kids") and the never-write topics (health, religion, politics, money, addresses) can be enforced, and what happens if the car is switched off before it finishes. Also: the best way to carry Notes into the next Talk (inside the system instruction vs a bracketed text line after setup), and whether ~20 facts plus ~10 topic lines change setup time or token use in a way that matters.

Primary sources: Google AI for Developers docs (Live API tools, session setup, rate limits page, structured output). Record dates read; mark estimates as estimates.

## Answer

Resolved 2026-11-22 (research, AFK). Findings with sources and probe numbers: notes-writing.md on the throwaway branch `research/anna-notes-writing`, at `.scratch/anna-notes/research/notes-writing.md` (never merged). Docs read 2026-11-22; the probe ran from a Mac on home broadband, not the car.

- **Write Notes with one text-model call after the Talk** (option b): `gemini-3.1-flash-lite` (fallback `gemini-3.5-flash-lite`), both free and both taking a JSON schema (`generationConfig.responseJsonSchema` in `v1beta` `generateContent`). Send the old Notes plus the in-memory input and output transcripts; the model returns the whole new list, so a correction replaces the old fact. The app stores the reply only if it is valid. `maxItems` is enforced, `maxLength` is ignored, so the app caps line length itself. Probe: 4 of 4 runs replaced "two kids" with "three kids" and wrote nothing about health, religion, money or the address; 0.95–1.7 s, but each model's first call took 7–8 s, so allow ~20 s.
- **Not (a), a tool call during the Talk:** 3.1 flash live only does synchronous function calling, so Anna stays silent until the app answers (+0.65 s on that Turn even with an instant answer), and the model saved poor facts ("driving to work now"). Writing mid-Talk would also break "a failed Talk is silently not kept".
- **Not (c), asking Anna at the end:** the model can only answer in audio, so she reads the Notes aloud; the text is only a transcript of her speech, and it can't run once the session is lost.
- **Carry Notes in inside the system instruction:** compression always keeps it, and the app already resends the same setup on every reconnect. ~20 facts + 10 topic lines added ~420 tokens and no measurable change to setup (~0.7 s) or the first hello.
- **Car switched off before the reply:** nothing is written and the old Notes stay, which is the agreed rule.
- **Limits:** Google publishes no free-tier request limits for Flash-Lite or Live (read them in the project's AI Studio page); one call per Talk is ~1–3 requests a day. `gemini-3.1-flash-live-preview` is now "legacy"; `gemini-3.8-live` supports async tools.
- **Unmeasured:** text-call latency over the hotspot, this project's real limits, accuracy on a real noisy ~75-Turn transcript, and whether Notes in the prompt change the test set's pass rate (the last is [Prototype how Anna uses Notes](03-anna-with-notes.md)).

### Caveats from a second check (2026-11-22)

Not in notes-writing.md; from a later check of the same docs and developer forums.

- **Tool calls weaker still:** forum users (not Google staff) report Anna's audio cutting off when 3.1 flash live calls a tool, and the model sometimes saying the result instead of calling it; the Live API reference also says a session can't be resumed while a function call is pending. More reason against (a).
- **Retry the text call:** a forum thread from August 2026 reports 429s on `gemini-3.1-flash-lite` far below quota. Retry at least once inside the ~20 s.
- **Schema field to confirm:** the probe used `generationConfig.responseJsonSchema`, but today's structured-output page shows `generationConfig.responseFormat.text.{mimeType, schema}` for REST. The build confirms which one `v1beta` accepts.
- **The app must keep both transcripts:** Talk today keeps only Anna's latest line (`annaLine` in `TalkTurns.kt`). Notes need the learner's and Anna's transcripts for the whole Talk, still in memory only.
- **Mishearing becomes a wrong fact:** the learner's transcript is a separate speech-recognition pass, so a misheard word can turn into a fact. Sending Anna's transcript too helps, since she often repeats what she heard.
