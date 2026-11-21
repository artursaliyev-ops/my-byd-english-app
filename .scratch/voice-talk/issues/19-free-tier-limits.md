# 19: Free-tier limits

**What to build:** when Gemini's free tier refuses, the learner is told plainly, never shown a network error. Refused at the start: back to the player with "Talk limit reached. Try later." Rate-limited mid-Talk: one retry after 5 s; refused again, the Talk ends with the same note and the lesson resumes. Spec §8 "Free-tier limit".

**Blocked by:** 18 (Network drops)

**Status:** done

- [x] Quota and rate-limit errors (setup refusal, close codes or error messages) are told apart from network errors in one place, with unit tests using sample error payloads.
- [x] At session start → the "no connection" tone, back to the player with "Talk limit reached. Try later.", and the lesson as it was.
- [x] Mid-Talk → one retry after 5 s; refused again → the Talk ends with the same note and the lesson resumes by the usual rule.
- [x] A limit error never shows "No signal" or "No internet".
