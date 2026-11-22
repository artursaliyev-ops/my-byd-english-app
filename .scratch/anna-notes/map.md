# Anna's notes — map
Label: wayfinder:map

## Destination

A hand-off-ready **spec** at `.scratch/anna-notes/spec.md` for **Notes**: Anna keeps a few short facts about the learner and the topics of recent Talks, and uses them in the next Talk, so a session can build it without new decisions.

## Notes

- Domain: the Android app in `android/`; Talk lives in `android/app/src/main/java/app/bydplayer/talk/` and is specced in [the voice talk spec](../voice-talk/spec.md). Terms **Talk**, **Turn**, **Talk partner** and **Notes** are in `GLOSSARY.md`. Consult `domain-modeling` for new terms.
- This reverses part of [Decide what Talk remembers and keeps](../voice-talk/issues/12-memory-and-retention.md): the head unit now keeps Notes as text. Everything else there still holds: no audio, no full transcript, logs carry timings and errors only.
- Car: DiLink 5.0, Android 12; the model is Gemini Live (`gemini-3.1-flash-live-preview`, API `v1beta`) on the **free tier**, key from `local.properties`. Anna's prompt is `android/app/src/main/assets/talk/anna-prompt.txt`; the test set runner is `tools/talk_test_set/`.
- Settled in charting (2026-11-22, owner):
  - Destination is a spec, not code.
  - Only short text Notes are stored on the head unit: no audio, no transcript.
  - Notes hold both facts about the learner (job, family, kids, car, likes; about 20 at most) and the topics of the last ~10 Talks with their dates; older ones drop off.
  - Anna uses them lightly: sometimes one thing from last time in her hello, sometimes a reference mid-Talk, and she doesn't ask again what she already knows. Not every time.
  - Name: kept only if the learner says it, used sometimes; Anna never asks for it.
  - Never written, even if said: health, religion, politics, money, and exact addresses (home, work).
  - Corrections by voice replace the old fact ("No, I have three kids"). No "forget that" voice command.
  - A Talk with fewer than 3 Turns adds nothing.
  - If writing Notes fails (no network, a limit, the car switched off), that Talk is silently not kept and the old Notes stay as they were.
  - Settings shows the Notes as a read-only list (when parked) and has a button that wipes them all. No editing.
  - A two-Talk test is added (say "I have two kids" in one, Anna remembers it in the next and doesn't ask again), and the current test set must still pass.
- UI follows the UI6 glass style in `Glass.kt`.

## Decisions so far

<!-- one line per closed ticket -->

- [Find how Notes can be written and carried in on the free tier](issues/01-how-notes-are-written.md): one free text-model call after the Talk (`gemini-3.1-flash-lite`, JSON schema) returns the whole new list from the old Notes plus the in-memory transcripts; Notes go into the next Talk's system instruction (~420 tokens, no slower hello); no tool calls mid-Talk, no asking Anna at the end. Research on branch `research/anna-notes-writing`.
- [Shape the Notes list and wipe button in Settings](issues/04-notes-in-settings.md): an "Anna's notes" section after "Record my answers", always visible (no driving signal), "About you" facts in short second-person lines and "Your last Talks" by date, shown exactly as stored; "Delete Anna's notes" with a confirm dialog.
- [Decide when Notes are written and how the list is kept](issues/02-when-notes-are-written.md): every ending with ≥ 3 Turns gets one call after the Talk, 20 s in all with one retry; a new Talk doesn't wait; the app keeps the newest 10 Talk lines, the model returns ≤ 20 facts with corrections in place; one `filesDir/anna-notes.json` written atomically; a wipe deletes it and the Talk in progress writes nothing.
- [Read the project's free-tier limits in AI Studio](issues/06-read-free-tier-limits.md): free tier gives `gemini-3.1-flash-lite` and `3.5-flash-lite` 15 RPM / 250K TPM / 500 RPD each, Live unlimited requests at 65K TPM; ~1–3 Notes calls a day and ~420 extra setup tokens fit with room to spare.
- [Prototype how Anna uses Notes](issues/03-anna-with-notes.md): a framed Notes block (with today's date) at the end of the system instruction, with rules: never ask again or check ("…, right?"), at most one unprompted use a Talk, nothing made up, name at most once; the app asks for a note in the hello on every second Talk; owner approved the amount. Owner-voice test set with Notes still unverified. Prototype on branch `prototype/anna-notes-use`.
- [Write the Notes spec](issues/05-write-the-spec.md): the hand-off spec at [spec.md](spec.md); the voice talk spec points to it for "Nothing on disk but Notes"; §10 lists seven defaults no ticket settled.

## Not yet specified

<!-- nothing open: setup cost and free-tier numbers are both answered -->

## Out of scope

- **Editing single Notes** and a **"forget that" voice command**: ruled out in charting; wiping all Notes in Settings is the only control.
- **Moving Talk to `gemini-3.8-live`** (3.1 flash live is now "legacy"; 3.8 has async tools): a model change for Talk as a whole, not part of Notes.
- **Anna giving medical advice** ("see a doctor… This is not medical advice" to "My back hurts", seen in [Prototype how Anna uses Notes](issues/03-anna-with-notes.md)): a break of the base prompt's rule, for voice talk, not Notes.
- **Tying Notes to the learner's Pimsleur level or lessons**: as in voice talk, Anna keeps one fixed A1–A2 rule.
