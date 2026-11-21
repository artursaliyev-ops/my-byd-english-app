# Decide what Talk remembers and keeps
Type: grilling
Status: resolved
Blocked by:

## Question

HITL. Does Anna remember anything from earlier Talks (nothing; a short summary or a few facts about the learner, such as name, job, family, carried into the next Talk's prompt), and if so, where does it live, how is it made, and how can the learner wipe it? Does the head unit keep any Talk text or audio at all (none; a transcript for the learner to read later; audio like voice practice's Attempts), and under what retention rule (cf. voice practice: last 5 lessons, app-internal storage, "Delete my answers")? Note that on the free tier Google may already keep and review the audio ([Pick the cloud provider, model and key](09-pick-cloud-provider.md)).

## Answer

Resolved 2026-11-21 (grilling with the user).

- **No memory between Talks.** Every Talk starts fresh, with the same system prompt from [Decide how the talk partner behaves](05-talk-partner.md). No facts and no summary are carried over, so no extra model call is needed at the end of a Talk. Remembering facts about the learner may come later as its own effort.
- **Nothing is kept on the head unit.** Mic audio, Anna's audio and any text exist only in memory for the current Turn or session and are never written to disk. That means no retention rule and no "delete" control are needed for Talk. By the same rule, logs carry no transcripts or audio, only timings and errors.
- Google may still keep and review the audio on the free tier, as the user already accepted in [Pick the cloud provider, model and key](09-pick-cloud-provider.md).
