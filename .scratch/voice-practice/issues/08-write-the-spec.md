# Write the voice practice spec
Type: task
Status: resolved
Blocked by: 07

## Question

AFK. Assemble `.scratch/voice-practice/spec.md` from the map's Notes and every resolved ticket, so a session can build voice practice without new decisions: settings and permission, the microphone service lifecycle, the speaking-pause rule, which audio is kept, Attempt replacement and retention, storage, the "▶ Me" / "▶ Echo" chips and playback, the red dot, and what was ruled out of scope. Done when the user has read it and has no open questions.

## Answer

Done 2026-11-21. `.scratch/voice-practice/spec.md` assembles the Notes and all seven resolved tickets into nine sections (scope, settings and permission, microphone service, speaking pauses, cutting an Attempt, storage, "▶ Me" / "▶ Echo", the recording dot, not tested). The user read it and had no questions. Details added while writing, not decided separately: a −50 dBFS / 300 ms starting value for "voice" (tune on the car), temp-file-then-rename writes, any transport or transcript tap stops "▶ Me" playback, and `VOICE_COMMUNICATION` supersedes the research ticket's first guess.
