# Prototype how Anna uses Notes
Type: prototype
Status: resolved
Blocked by: 01

## Question

HITL. Draft the Notes format and the prompt text that carries them into a Talk, then run it on the Mac: a two-Talk test (Talk A: the learner says "I have two kids" and a couple of other facts; Talk B with the Notes from A: Anna may bring one up, doesn't ask again what she knows), plus the current test set with some Notes present, which must still pass (≥ 12/14, cases 7, 10, 11). Check that she uses Notes "lightly" (not in every reply), never brings up a never-write topic, and uses the name only if one was given. The owner listens to the replies and decides whether it feels right. Notes are stored as the lines Settings shows (short, second person, A1–A2: "You have two kids."; Talks as "22 Nov — work, the weekend."); see [Shape the Notes list and wipe button in Settings](04-notes-in-settings.md).

The call's reply shape is settled in [Decide when Notes are written and how the list is kept](02-when-notes-are-written.md): the whole fact list (≤ 20) plus only this Talk's topics line; the app adds the date and keeps the newest 10 Talks.

## Answer

Resolved 2026-11-22 (prototype, with the owner). Prototype, transcripts and the full v3 prompt text: `.scratch/anna-notes/prototype/anna-with-notes.md` on the throwaway branch `prototype/anna-notes-use` (never merged). Run from a Mac with machine voices; the owner listened to the replies.

- **Notes block in the system instruction**, appended to `anna-prompt.txt` only when there are Notes: a framed list ("In a note, 'you' means the learner, not you. Today is Sunday, 22 Nov."), "About the learner" facts and "Last Talks" lines exactly as Settings shows them, then short rules. Today's weekday and date go in the block.
- **Rules that worked (draft v3)**: never ask again what a note says; use a note as something known, never as a check ("How are the kids?", never "You have two kids, right?"); after the hello, bring up a note by herself at most once a Talk and only when the learner talks about that thing; nothing made up beyond the notes; believe the learner over a note; the name at most once a Talk; never say "notes" or "the app".
- **The app decides the hello**, not Anna: on every second Talk that has Notes, the opening is `(The learner pressed Talk. Start the Talk. In your hello, ask about one thing from the notes.)`; otherwise the normal opening and a plain hello. Left to herself (v1) Anna brought Notes up in almost every hello. "Every second Talk" is a default the spec may change.
- **Base prompt change**: "You don't know the learner's name and you don't ask it." becomes "You never ask the learner's name."
- **Owner's verdict**: the amount in v2/v3 is right (0–2 uses a Talk outside the hello); no "…, right?" checks; the name stays in Notes.
- **Seen**: v1's loose "now and then" wording used Notes in 4–5 of 7 replies and invented a memory ("that damn picture of your cat"); v3 had no checks, no invented memory, plain hellos 3/3 and the recall hello 3/3. Anna never brought up the back pain.
- **Test set with Notes (machine voices)**: v3 12/14 by my reading, but required case 7 fails. Without Notes, machine voices also fail case 7 in 3 of 4 runs, so this is **unverified**. The build must run the test set with the owner's voice and Notes present (≥ 12/14, cases 7, 10, 11) before it ships.

For [Decide when Notes are written and how the list is kept](02-when-notes-are-written.md):
- A plan stored as "on Saturday" goes stale: on Sunday Anna asked about "today". The writer should store plans with a date ("on Sat 28 Nov") and drop them once past.
- A misheard name becomes a fact ("Rustam" became "Your name is Russ Tom."), and Anna uses it. The owner keeps the name anyway; a voice correction replaces it.

Not about Notes: in Talk A, Anna answered "My back hurts" with "see a doctor… This is not medical advice", which breaks the base prompt's no-medical-advice rule.
