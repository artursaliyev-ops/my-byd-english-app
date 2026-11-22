# Decide when Notes are written and how the list is kept
Type: grilling
Status: resolved
Blocked by: 01

## Question

HITL. Given how Notes can be written, at which moments are they written (on "bye", End Talk, the 30-min cap, a Talk ended by No signal or Paused timing out, other media taking over, play/pause on the wheel), and does a Talk that ends badly still count? How is the list kept to ~20 facts and ~10 topics: which drop first, does a fact that comes up again move up, and how does a correction replace an old fact? Where do the Notes live on the head unit and in what shape, and what exactly does the wipe button remove? Does wiping during a Talk affect that Talk?

## Answer

Resolved 2026-11-22 (grilling with the owner; all recommendations accepted).

- **Every ending counts.** A Talk with at least 3 complete Turns (the learner spoke and Anna answered) gets a Notes call however it ended: "bye", End Talk, the 30-min cap, No signal giving up, Paused timing out, other media taking over, play/pause on the wheel.
- **One call, right after the Talk ends**, in the background while the lesson comes back: 20 s in all, with **one retry** inside those 20 s if the first try fails (changed 2026-11-22 by the owner, after [Read the project's free-tier limits in AI Studio](06-read-free-tier-limits.md) showed a retry fits the limits easily and the research had seen 429s far below quota). If both fail, that Talk is silently not kept. The input and output transcripts of the whole Talk (across reconnects) are kept in memory only until the call is done, then dropped. They are never written to disk, so a later retry is not possible.
- **A new Talk doesn't wait** for the last Talk's call: it starts with the Notes stored now. The earlier call still finishes and writes. The new Talk's own call reads the latest stored Notes when it ends.
- **Keeping the list:**
  - *Talks (10)*: the model returns only this Talk's topics line ("work, the weekend"). The app adds it with the date the Talk started (head-unit local time) and keeps the newest 10. Two Talks on one day are two lines.
  - *Facts (≤ 20)*: the model returns the whole new fact list from the old facts plus the transcripts (`maxItems: 20`). A correction replaces the old fact in place. A new fact goes at the end. A fact said again stays where it is and doesn't move up. Past 20, the model drops the fact that says least about who the learner is. The app cuts each line to ~60 characters.
- **Storage**: one file, `filesDir/anna-notes.json`: `facts` (the lines as shown) and `talks` (`{"date": "2026-11-22", "topics": "work, the weekend"}`), shown in Settings as "22 Nov — work, the weekend." It is written to a temp file and renamed, so a car switched off mid-write leaves the old Notes whole. (Default, not grilled: a file that can't be read counts as no Notes.)
- **Wipe** deletes the file: facts and Talks. If it happens during a Talk, or while that Talk's call is still out, that Talk writes nothing and any reply is thrown away, so wiped Notes can't come back through the call. The running Talk keeps the Notes it started with, reconnects included.
