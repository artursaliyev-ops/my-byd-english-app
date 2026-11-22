# Find how Notes can be written and carried in on the free tier
Type: research
Status: open
Blocked by:

## Question

AFK. How can Talk produce Notes and give them to the next Talk, on the Gemini free tier with the app's current setup (Live API `v1beta`, `gemini-3.1-flash-live-preview`, audio out, input and output transcription on)? Compare at least:

- (a) **A function call during the Talk**: Anna's Live session calls a tool like `save_note` when she learns something. Does 3.1 flash live support tools with audio out, and does a tool call delay or break her audio Turn?
- (b) **One text-model call after the Talk**: the app sends the in-memory transcripts (input and output transcription) plus the old Notes to a free-tier text model, which returns the updated Notes as JSON. Which model, its free-tier requests per day and per minute, typical latency, and whether a structured (JSON schema) response is available.
- (c) **Asking the Live model itself at the end** (a bracketed control line before closing), reading its reply from the output transcript.

For each: reliability, cost against the free-tier limits Talk already uses, how corrections ("No, I have three kids") and the never-write topics (health, religion, politics, money, addresses) can be enforced, and what happens if the car is switched off before it finishes. Also: the best way to carry Notes into the next Talk (inside the system instruction vs a bracketed text line after setup), and whether ~20 facts plus ~10 topic lines change setup time or token use in a way that matters.

Primary sources: Google AI for Developers docs (Live API tools, session setup, rate limits page, structured output). Record dates read; mark estimates as estimates.
