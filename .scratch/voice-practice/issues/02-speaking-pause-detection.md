# Find the speaking pauses in a lesson
Type: prototype
Status: resolved
Blocked by:

## Question

Which silences in `transcripts/<id>.json` are **speaking pauses** (the learner should answer) and which are not (pauses after "Repeat after the speaker", breaks between parts, Whisper timing noise)? Build a rough script that lists the detected pauses for one lesson (start, end, the line before, the speaker's line after), and let the user check it against the audio of I_05. Settle the rule: minimum length, how far into the pause to start and stop keeping audio, and which following line counts as "the speaker's line" for comparison.

## Answer

Resolved 2026-11-21 with the user (HITL), from the prototype and three questions: they echo the speaker's answer after it (yes), they repeat in the gap when a line is said twice (yes), and they manage to answer in 1–2 s gaps (yes).

**The rule.** Look at every silence between two consecutive transcript segments. Classify each line as Russian or English **by alphabet** (any Cyrillic letter means Russian), never by Whisper's `lang` tag, which often marks English as `ru`. A silence is a speaking pause when:

- it is **at least 1.0 s** long, and
- it is not Russian → Russian (that is the narrator moving on), and
- at least **0.5 s** is left after trimming.

**What is kept.** Keep audio from **0.2 s after the line before ends** until **0.1 s before the next line starts**.

**Which line the Attempt is compared with:**

| Before → after | Example | Compared with |
|---|---|---|
| Russian → English | prompt, then the speaker's answer | the line **after** |
| English → same English | the speaker says it twice | the line **after** (the second saying) |
| English → different English | dialogue or build-up | the line **after** |
| English → Russian | echo of the answer just heard | the line **before** |

**Numbers** (all 10 transcripts in `transcripts/`): 245–302 speaking pauses per lesson, 277 on average, and about **13 min** of kept voice per lesson pass. Lowering the minimum below 1.0 s adds almost nothing, because shorter gaps fall under the 0.5 s left-after-trim floor.

**Consequence for other tickets.** One English line can now have **two Attempts**: the answer in the pause before it, and the echo in the pause after it. "Shape the ▶ Me control and the recording dot" and "Decide what a lesson pass means when the learner jumps around" need to account for this.

**Prototype:** `tools/prototype-speaking-pauses.html`. The pure functions `classify` / `findSilences` in its first `<script>` block are the part worth lifting into the app; open the file from `tools/` so it finds the MP3 in `sources/`.
