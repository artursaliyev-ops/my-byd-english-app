# Write the Notes spec
Type: task
Status: resolved
Blocked by: 01, 02, 03, 04, 06

## Question

AFK. Collapse the map's decisions into `.scratch/anna-notes/spec.md`, hand-off ready like [the voice talk spec](../voice-talk/spec.md): what is written and when, the storage and wipe, the prompt change, the Settings list, the two-Talk test, and any first-car-build checks with a bar and a fallback. Update the voice talk spec's "Nothing on disk" rule to point here.

Carry in from [Prototype how Anna uses Notes](03-anna-with-notes.md) (not covered by [Decide when Notes are written and how the list is kept](02-when-notes-are-written.md)): the writer stores a plan with its date ("on Sat 28 Nov", not "on Saturday") and drops it once past (default, not grilled); and a first build check: the test set with the owner's voice and Notes present, ≥ 12/14 with cases 7, 10, 11.

## Answer

Resolved 2026-11-22 (task, AFK): [the Notes spec](../spec.md).

- Covers the Notes format, keeping both transcripts in memory, the writer call (model, schema, 20 s with one retry on the fallback, validation), the file and the wipe generation, the Notes block and base prompt change, the app-chosen recall hello, the Settings section, unit and Mac tests, and six first-build checks with bars and fallbacks.
- The voice talk spec now points here: §1 Out, §4 (the name sentence) and §5 "Nothing on disk but Notes".
- Seven calls no ticket settled are listed in the spec's §10, among them the retry going to `gemini-3.5-flash-lite`, the recall hello as a flipping preference, and no app-side never-write filter.
