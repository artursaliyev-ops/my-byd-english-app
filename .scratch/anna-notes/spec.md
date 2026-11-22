# Anna's notes — spec

**Notes**: Anna keeps a few short facts about the learner and the topics of their recent Talks on the head unit, and uses them lightly in the next Talk. The learner can read them in Settings and wipe them all, but not edit them.

Every decision here comes from the map `.scratch/anna-notes/map.md`; each section names the ticket that holds the detail and the reasoning. Terms (**Talk**, **Turn**, **Talk partner**, **Notes**) are in `GLOSSARY.md`. This builds on [the voice talk spec](../voice-talk/spec.md) and changes its "Nothing on disk" rule (§5 there): Notes are the one thing Talk keeps. Section 10 lists the calls made while writing this spec that no ticket settled.

## 1. Scope

In:
- Keeping both transcripts of a Talk in memory, one text-model call after the Talk that returns the new Notes, one small file on the head unit, a Notes block in Anna's prompt, a hello that sometimes brings up a note, an "Anna's notes" section in Settings with a wipe button, and the tests.

Out (map, Out of scope):
- **Editing single Notes** and a **"forget that" voice command**. Wiping all Notes is the only control; a spoken correction ("No, I have three kids") replaces a fact.
- **Moving Talk to `gemini-3.8-live`** (async tools). Notes don't need it.
- **Tying Notes to the learner's Pimsleur level or lessons.**
- **Anna's medical-advice slip** seen while prototyping ("see a doctor…" to "My back hurts"): a voice talk prompt issue, not Notes.
- Unchanged from voice talk: no audio and no full transcript on disk; logs carry timings and errors only, never Notes text.

## 2. What the Notes are

*From "Shape the Notes list and wipe button in Settings" and "Decide when Notes are written and how the list is kept".*

- **Facts** (at most 20): lasting facts about the learner (job, family, kids, car, likes, plans). One per line, second person, very short A1–A2 English, exactly as Settings shows them: "You have two kids." "You work as a manager in a shop." A name only if the learner said it: "Your name is Rustam." Anna never asks for it.
- **A plan carries its date**: "You are going to your mother's house on Sat 28 Nov.", never "on Saturday". The writer drops it once the date is past (*Prototype how Anna uses Notes*: a dateless "Saturday" plan went stale).
- **Talks** (newest 10): one line per Talk, the date it started (head-unit local time) and two to four topics: shown as "22 Nov — work, the weekend." Two Talks on one day are two lines.
- **Never written, even if said**: health, religion, politics, money, exact addresses (home, work). Nothing only true today ("You are tired today.").

## 3. Transcripts in memory

*From "Find how Notes can be written…" (caveats) and "Decide when Notes are written…".*

- Today Talk keeps only Anna's latest line (`annaLine` in `TalkTurns.kt`). Add a **Talk transcript**: a list of Turns, each the learner's input transcription and Anna's output transcription (`LiveEvent.LearnerSaid` / `LiveEvent.AnnaSaid`), kept for the **whole Talk across reconnects and new sessions**. Anna's lines after control text (the opening, the quiet nudge, the connection came back) go in with no learner line.
- **A complete Turn** = the learner spoke and Anna answered. A Talk with **fewer than 3 complete Turns** adds nothing and makes no call.
- Memory only. Dropped as soon as the Notes call is done (success or failure). Never written to disk, never logged.

## 4. Writing the Notes

*From "Find how Notes can be written…", "Decide when Notes are written…" and "Read the project's free-tier limits…".*

**When.** Every ending counts: "bye", End Talk, the 30-min cap, No signal giving up, Paused timing out, other media taking over, play/pause on the wheel. Right after the Talk ends, in the background while the lesson comes back. It runs in an app-wide scope, not in `TalkService`, which stops with the Talk.

**A new Talk doesn't wait.** It starts with the Notes stored now. The earlier call still finishes and writes. Each call reads the latest stored Notes when it is sent. Writes go through one lock, so two calls never interleave.

**The call.** One `v1beta` `generateContent` request (HTTPS, OkHttp, the same `GEMINI_API_KEY`; never log the URL):
- Model `gemini-3.1-flash-lite` (`TalkConfig.NOTES_MODEL`). Free tier: 15 RPM, 250K TPM, 500 RPD; a Talk makes 1–2 requests.
- `systemInstruction`: the writer instruction, bundled as the asset `talk/notes-writer.txt` (text below).
- `contents`: `Today is Sunday, 22 Nov 2026.`, then `Old facts:` (one per line, or `(none)`), then `Today's Talk:` with lines `Learner: …` / `Anna: …`. Anna's lines are sent too: she often repeats what she heard, which helps with misheard words.
- `generationConfig`: `responseMimeType: "application/json"`, the schema below, `thinkingConfig.thinkingLevel: "low"`. The schema field is `responseJsonSchema` (it worked in the probes). Today's structured-output docs show `responseFormat.text.{mimeType, schema}`: confirm which one `v1beta` accepts before the build (§9).
- Schema: `{"type":"object","properties":{"facts":{"type":"array","items":{"type":"string"},"maxItems":20},"topics":{"type":"string"}},"required":["facts","topics"]}`.

**Time and retry.** 20 s in all (`TalkConfig.NOTES_BUDGET_MS`). The first try gets up to 10 s. If it fails (timeout, network, 429, 5xx, a reply that doesn't validate), make **one retry** with the rest of the 20 s, on the fallback `gemini-3.5-flash-lite` (`TalkConfig.NOTES_FALLBACK_MODEL`, its own 15 / 250K / 500 limits). If both fail, **that Talk is silently not kept** and the old Notes stay. No sound, no message.

**Validate, then store.** The reply must parse and match the schema. The app trims each fact and cuts it to 60 characters, drops empty ones, keeps the first 20, and cuts `topics` to 60 characters. An empty `topics` fails validation. The app builds the new Talk line from the Talk's start date and `topics`, puts it first and keeps the newest 10. The model's `facts` replace the old facts whole.

**Writer instruction** (`talk/notes-writer.txt`), from the probe and the prototype plus the list rules of *Decide when Notes are written…*:

```
You keep short notes about an English learner for Anna, their talk partner. You get today's date, the old facts and the transcript of today's Talk (speech recognition, so some words may be wrong). Return the whole new list of facts and the topics of today's Talk.
Facts:
- Lasting facts about the learner only: job, family, kids, car, likes, plans. The name only if the learner said it.
- Very short, simple English (A1-A2), second person, one fact per line: "You have two kids." "Your name is Rustam."
- Keep the old facts in the same order unless today's Talk changes them. A correction replaces the old fact in the same place; never keep both. A new fact goes at the end. A fact said again stays where it is.
- A plan gets its date, like "on Sat 28 Nov", never "on Saturday". Drop a plan once its date is past.
- Never write anything about health, religion, politics, money or exact addresses, even if the learner said it.
- Nothing that is only true today ("You are tired today.").
- At most 20 facts. If there are more, drop the ones that say least about who the learner is.
Topics: two to four topics of today's Talk, a few words each, comma separated, like "work, the weekend". No topic about health, religion, politics, money or addresses.
```

## 5. Storage and wipe

*From "Decide when Notes are written…" and "Shape the Notes list…".*

- **One file**, `filesDir/anna-notes.json`:
  ```json
  {"facts": ["You have two kids."], "talks": [{"date": "2026-11-22", "topics": "work, the weekend"}]}
  ```
  `talks` newest first. Written to a temp file in the same folder and renamed, so a car switched off mid-write leaves the old Notes whole. A file that is missing or can't be read counts as no Notes.
- **One small class** (say `NotesStore`) owns the file, the lock and a **wipe generation** counter (in memory). Every Talk records the generation when it starts. A reply is stored only if the generation hasn't changed since then.
- **Wipe** deletes the file and bumps the generation. If it happens during a Talk, or while that Talk's call is still out, that Talk writes nothing and any reply is thrown away. The running Talk keeps the Notes it started with, reconnects included.

## 6. Notes in the Talk

*From "Prototype how Anna uses Notes" (draft v3, approved by the owner) and "Find how Notes can be written…".*

**In the system instruction.** When a Talk starts, the app reads the Notes once and builds the prompt: `anna-prompt.txt`, plus the Notes block when there is at least one fact or Talk line. The same prompt goes in every `setup` of that Talk (reconnects and new sessions), so Notes come back with no extra step. Notes add ~420 tokens and no measurable setup or hello time.

**Base prompt change** in `anna-prompt.txt`: "You don't know the learner's name and you don't ask it." becomes **"You never ask the learner's name."**

**The Notes block**, bundled as the asset `talk/anna-notes-block.txt`, so the Mac test tools read the same text. `{today}` is like "Sunday, 22 Nov" (head-unit local time); `{facts}` and `{talks}` are lines starting `- `, the Talks shown as in Settings ("22 Nov — work, the weekend."), and `- (none yet)` when a group is empty:

```
Notes from earlier Talks. The app keeps them. In a note, "you" means the learner, not you. Today is {today}.
About the learner:
{facts}
Last Talks:
{talks}

How to use the notes:
- You already know these things. Never ask again what a note says.
- When you use a note, say it as something you know, never as a check. Never "You have two kids, right?" or "You are a manager, right?". Say "How are the kids?" or "Busy day at the shop?".
- In the hello, use a note only when the app asks for it. Otherwise a normal hello.
- After the hello, bring up a note by yourself at most once in the whole Talk, and only when the learner talks about that same thing. All other replies are only about what the learner says now.
- Only what the notes say. Never make up other things from earlier Talks.
- If the learner says something different from a note, believe the learner.
- If a note gives the learner's name, say it at most once in a Talk.
- Never say "notes" or "the app".
```

**The hello: the app decides.** On **every second Talk that has Notes**, the opening text is `TalkConfig.Text.OPENING_RECALL` = `(The learner pressed Talk. Start the Talk. In your hello, ask about one thing from the notes.)`; otherwise the normal `OPENING`. A boolean in the app's preferences flips at each Talk start that has Notes; a Talk with no Notes leaves it alone. In the prototype, left to herself Anna brought Notes into almost every hello; with the app deciding, plain hellos were plain and recall hellos used one note, 3 of 3 each.

## 7. Settings

*From "Shape the Notes list and wipe button in Settings".* UI6 glass style (`Glass.kt`), in `SettingsScreen.kt`.

- A new section **"Anna's notes"**, after "Record my answers" and before "Last button", with a title and a dim explanation: "Anna keeps a few short notes about you and your last Talks, so she remembers them next time. On this car only. No sound and no full talk is kept."
- **"About you"**: the facts, one per line, as stored.
- **"Your last Talks"**: one line per Talk, newest first, "22 Nov — work, the weekend." (date as "22 Nov", never "Today"/"Yesterday").
- **Empty**: "No notes yet." in place of both groups.
- **Always visible.** The app has no parked/driving signal, and the rest of Settings is unlocked. Read-only, no editing.
- **"Delete Anna's notes"**, shown only when there are Notes, opens a dialog like "Delete my answers": title "Delete Anna's notes?", text "Anna will forget everything in her notes. This can't be undone.", buttons Delete / Cancel. After Delete the section shows "No notes yet." at once.
- The section updates when a background call writes new Notes while Settings is open.

## 8. Tests

**Unit tests** (pure Kotlin, no network):
- Prompt building: no block with no Notes; block with facts only, Talks only, both; date formats; `(none yet)`.
- Hello choice: the flag flips only on Talks with Notes.
- Complete-Turn counting and the < 3 rule; the transcript survives a reconnect.
- Reply validation: bad JSON, missing fields, > 20 facts, long lines, empty topics.
- Keeping the list: the new Talk line goes first, the 11th drops, two Talks on one day.
- Storage: atomic write; a broken file reads as no Notes; a wipe during a Talk or during the call throws the reply away.
- Retry: first try fails → fallback within the budget; both fail → old Notes untouched.

**On the Mac** (`tools/talk_test_set/`, reading the prompt, Notes block, writer instruction and openings from the app's files, like `testset.py`):
- **Two-Talk test**: Talk A, where the learner says "I have two kids" and a couple of other facts, then the real writer call, then Talk B with those Notes. Pass: the Notes have "You have two kids." and nothing never-write; in Talk B Anna never asks whether the learner has kids, never asks "…, right?" about a note, brings up a note by herself at most once after the hello, and invents nothing. Run it once with the normal opening and once with `OPENING_RECALL` (the recall hello must use one note). The throwaway prototype on branch `prototype/anna-notes-use` (`tools/talk_test_set/PROTOTYPE_anna_notes.py`) is the working reference.
- **The test set with Notes present** (the two-Talk Notes in the prompt): the same bar as voice talk, **≥ 12/14 with cases 7, 10 and 11 passing**. Case 10 ("I have two children") must react naturally to what she already knows, without "…, right?". With machine voices the prototype scored 12/14 but missed case 7, which machine voices also miss without Notes (1 of 4), so the owner's voice decides (§9).

## 9. First build checks

Unmeasured. Run them before the build ships; each has a bar and a fallback.

| Check | Bar | If it fails |
|---|---|---|
| ~~**Schema field**~~ (settled 2026-11-22 from the Mac: `responseJsonSchema` returned schema-valid JSON in 2.0 s; `responseFormat.text.mimeType` got HTTP 400) | | |
| **Owner's voice test set with Notes** (Mac, then car) | ≥ 12/14 with cases 7, 10, 11 | Tune the Notes block and rerun; the base prompt must still pass without Notes |
| **Writer on a real Talk**: three real 10–20 min Talks over the hotspot | Each call returns within 20 s; Notes are right, A1–A2, nothing never-write, corrections replace | Over 20 s: raise `NOTES_BUDGET_MS`. Wrong facts: tune `notes-writer.txt` |
| **Car switched off** right after "bye" | Old Notes stay whole and readable | The temp-and-rename write is wrong; fix it |
| **Two Talks back to back** (the second starts before the first call returns) | Both Talk lines end up stored, nothing lost | Fix the lock / re-read in §4 |
| **Notes with a misheard name** (owner, real Talk) | A wrong name can be fixed by saying it ("No, my name is Rustam") | Accepted risk: wipe all Notes |

## 10. Calls made while writing this spec

No ticket settled these; each is a sensible default the owner can overturn before the build:
- The retry goes to the fallback model `gemini-3.5-flash-lite` rather than the same model (separate quota; the 429 reports were on 3.1). The first try gets 10 s of the 20 s.
- "Every second Talk that has Notes" for the recall hello, kept as a flipping boolean in preferences.
- No app-side keyword filter for never-write topics: the writer got them all right in every probe and prototype run.
- The writer instruction and the Notes block live in assets (`talk/notes-writer.txt`, `talk/anna-notes-block.txt`), so the Mac tools read the app's own text.
- `thinkingLevel: "low"` for the writer call.
- The writer gets today's date so it can date plans and drop past ones.
- The wipe generation lives in memory only; after a process restart there is no call left to guard.
