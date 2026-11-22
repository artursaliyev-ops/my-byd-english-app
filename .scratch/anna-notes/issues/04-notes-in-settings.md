# Shape the Notes list and wipe button in Settings
Type: grilling
Status: resolved
Blocked by:

## Question

HITL. How do Notes appear in Settings: where on the screen, how facts and topics are grouped and worded (the learner reads English at A1–A2), what shows when there are none, and whether the list is hidden while driving or only when parked (and how the app knows). How does the wipe button work: its label, a confirmation step, and what the learner sees after. It follows the UI6 glass style in `Glass.kt` and sits with voice practice's "Delete my answers".

## Answer

Resolved 2026-11-22 (grilling with the owner; all recommendations accepted).

- **Place**: a new Settings section **"Anna's notes"**, after "Record my answers" and before "Last button", with a title and a dim explanation like the other sections. The wipe button sits inside it, like "Delete my answers".
- **Explanation text**: "Anna keeps a few short notes about you and your last Talks, so she remembers them next time. On this car only. No sound and no full talk is kept."
- **Driving**: no lock. The app has no parked/driving signal (nothing reads gear or speed) and the rest of Settings is unlocked too, so the list is always visible; "when parked" stays an intent, not a mechanism. Reading the car's speed or gear is not pursued.
- **Groups**:
  - **"About you"**: one fact per line, second person, very short, A1–A2 English: "You have two kids." "You work as a taxi driver." A given name appears here: "Your name is …".
  - **"Your last Talks"**: one line per Talk, newest first: "22 Nov — work, the weekend." Date as "22 Nov", never "Today"/"Yesterday".
- **Stored = shown**: the screen shows each stored line as is, with no rewording, so Notes are written in that shape. This is a constraint on the Notes format in [Prototype how Anna uses Notes](03-anna-with-notes.md).
- **Empty**: "No notes yet." in place of the list.
- **Wipe**: button **"Delete Anna's notes"**, shown only when there are Notes. It opens a confirmation dialog like "Delete my answers": title "Delete Anna's notes?", text "Anna will forget everything in her notes. This can't be undone.", buttons Delete / Cancel. After Delete the section shows "No notes yet." at once.
- Whether wiping during a Talk affects that Talk is left to [Decide when Notes are written and how the list is kept](02-when-notes-are-written.md).
