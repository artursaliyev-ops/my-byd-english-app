# PROTOTYPE: how Anna uses Notes (throwaway branch, never merged)

Ticket: [Prototype how Anna uses Notes](../issues/03-anna-with-notes.md). Run 2026-11-22 from a Mac on home broadband, machine voices (macOS `say`, Samantha), the app's exact Live setup (`tools/talk_test_set/testset.py`). Script: `tools/talk_test_set/PROTOTYPE_anna_notes.py [v1|v2|v3] [--reuse]`. Transcripts per run: `runs/<draft>.html` (audio stayed in git-ignored `tools/out/anna_notes_prototype/`). The owner listened to v1 and v2 and gave the verdict below; v3 applies it.

## Setup

- **Talk A** (no Notes): "Hi. My name is Rustam." / "I have two kids, a boy and a girl." / "I work in a big shop. I am a manager." / "My back hurts a lot today." / "On Saturday we are going to my mother's house." / "OK, bye!"
- **Stand-in writer**: one `gemini-3.1-flash-lite` call, JSON schema, thinking low. It returned `Your name is Russ Tom.` / `You have two kids.` / `You work as a manager in a shop.` / `You are going to your mother's house on Saturday.`; topics `22 Nov — family, work, weekend plans.` (two older fake topic lines added). Nothing about the back pain.
- **Talk B** with those Notes, ×3: twice with the normal opening, once with a recall opening. Lines: "My day is OK. A lot of work." / "I am going home now." / "This evening I want to rest." / "What about you? What are you doing tonight?" / "Do you remember me?" / "OK, bye!"
- **Test set** (14 cases, machine voices) with the same Notes in the prompt.

## Results

| Draft | Notes used in Talk B (of 7 replies) | Asks to check ("…, right?") | Made-up memory | Test set with Notes |
|---|---|---|---|---|
| v1 "now and then" | 4–5, and the model chose the hello | 2 | "you showed me that damn picture of your cat!" | case 7 and 13 fail; Saturday plan pulled into cases 2, 4, 5, 6 |
| v2 rules + app decides hello | 1–2 unprompted | 4 across 6 Talks | none | (run 2) ~10/14, case 7 said "We talked a lot today! Bye! Drive safe." |
| v3 = v2 + "say it as something you know" | 0–1 unprompted, rest when asked | 0 | none | 12/14 by my reading; 7 fails (heard "dia", said "The shop, ha!"), 9 borderline ("Politics are crazy, right?") |

Baseline: case 7 with machine voices already passed only 1 of 4 runs **without** Notes (`tools/out/talk_test_set/teasing-anna-1..4`), so the miss says little; the owner-voice run decides.

Other things seen:
- **Misheard name kept**: "Rustam" was heard "Russ Tom", written as a fact, and used ("Russ Tom", once "Tom").
- **Relative day goes stale**: "on Saturday" stored without a date; on Sunday Anna asked "You're going to your mom's house today, right?".
- **Recall opening**: `(… In your hello, ask about one thing from the notes.)` worked 3/3.
- **Not about Notes**: in Talk A, Anna answered back pain with "see a doctor… This is not medical advice" (base prompt's medical rule broken).

## Verdict (owner, 2026-11-22)

1. v2's amount of use is right.
2. The app decides when the hello brings up a note.
3. No "…, right?" checks.
4. The name stays in Notes.

## Draft v3 Notes block (appended to anna-prompt.txt when there are Notes)

The base prompt's "You don't know the learner's name and you don't ask it." becomes "You never ask the learner's name."

```
Notes from earlier Talks. The app keeps them. In a note, "you" means the learner, not you. Today is {Sunday, 22 Nov}.
About the learner:
- {fact}
Last Talks:
- {22 Nov — work, the weekend.}

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

Recall opening: `(The learner pressed Talk. Start the Talk. In your hello, ask about one thing from the notes.)`
