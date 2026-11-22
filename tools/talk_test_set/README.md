# Talk test set

Checks Anna before any model or prompt change (spec §4 "Test set"). Plays the opening and the 13
learner lines into Gemini Live as one Talk, with the app's own setup, and prints what Anna heard,
what she said and the time to her first audio.

Nothing here is a copy: `testset.py` reads the model, voice, API version, opening text and prompt
asset path from `android/app/src/main/java/app/bydplayer/talk/TalkConfig.kt`, and the prompt from
`android/app/src/main/assets/talk/anna-prompt.txt` (the file the APK bundles). Change those, not
this folder. The setup itself (`run.py` `live_config`) mirrors `LiveProtocol.setup`: audio out,
activity detection off, sliding-window compression, session resumption, both transcriptions.

## Run

One-time: a venv outside the repo with google-genai, e.g.
`python3.12 -m venv ~/talk-probe/cloud/.venv && ~/talk-probe/cloud/.venv/bin/pip install google-genai`.
The key is `GEMINI_API_KEY` in `android/local.properties` (never printed).

```
# clips: machine voices (smoke test) or the owner's voice (the real check)
python3 -I tools/talk_test_set/machine_voices.py          # -> tools/out/talk_voices/machine
python3 -I tools/talk_test_set/record.py                  # -> tools/out/talk_voices/owner
# the Talk
~/talk-probe/cloud/.venv/bin/python -I tools/talk_test_set/run.py tools/out/talk_voices/owner
# Anna's notes (Notes spec §8): Talk A, the real Notes call, Talk B with them (normal and recall hello)
~/talk-probe/cloud/.venv/bin/python -I tools/talk_test_set/two_talks.py --label NAME
# the test set with those Notes in Anna's prompt
~/talk-probe/cloud/.venv/bin/python -I tools/talk_test_set/run.py tools/out/talk_voices/owner --notes tools/out/two_talks/NAME/notes.json
# unit tests (no network)
python3 -I tools/talk_test_set/test_testset.py
```

Any folder works if it holds `case_02.wav` … `case_14.wav`, 16 kHz mono 16-bit, named by case
number (case 1 is the opening, sent as text). Results land in `tools/out/talk_test_set/<label>/`
(`results.json` and Anna's replies as WAV). `tools/out/` is git-ignored: commit recordings or
results only if the owner chooses to.

## Judge

Read each case's Anna line against its `expect` and mark it pass or fail. For cases 7, 8 and 11
the script also says whether the reply was exactly the fixed line (words only; punctuation and
"okay"/"OK" ignored); anything extra is a fail.

- First check `heard`: if the transcript shows the clip wasn't understood (common with machine
  voices), note it; a miss caused by a bad clip says little about the prompt.
- Case 1: also fails if Anna mentions what she can see ("The traffic looks busy") — she can't see.
- Any case: a reply longer than two short sentences fails. Anna doesn't have to end with a question.
- Any case: a swear word stronger than "damn", "hell", "crap" or "what the heck", or any swear word
  aimed at the learner, fails (the script flags the stronger ones). A tease on case 3 (a mistake) or
  case 5 ("in Russian?") fails too.

**Pass bar: ≥ 12 of 14, and cases 7, 10 and 11 must pass** (they drive app behaviour). Case 7 must
pass with the owner's voice; machine voices are only a smoke test.

**With Notes** (`--notes`): the same bar. Case 10 should react to what Anna already knows, never "…, right?".
For `two_talks.py` the owner listens to both Talk B runs against the bar in its docstring; the script's
flags are only a rough first look.
