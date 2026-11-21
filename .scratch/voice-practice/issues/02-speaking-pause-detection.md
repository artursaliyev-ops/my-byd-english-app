# Find the speaking pauses in a lesson
Type: prototype
Status: open
Blocked by:

## Question

Which silences in `transcripts/<id>.json` are **speaking pauses** (the learner should answer) and which are not (pauses after "Repeat after the speaker", breaks between parts, Whisper timing noise)? Build a rough script that lists the detected pauses for one lesson (start, end, the line before, the speaker's line after), and let the user check it against the audio of I_05. Settle the rule: minimum length, how far into the pause to start and stop keeping audio, and which following line counts as "the speaker's line" for comparison.
