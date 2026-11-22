"""The two-Talk test for Anna's notes (Notes spec §8), with the app's own text.

Talk A (no Notes): the learner says "I have two kids" and a couple of other facts. Then the real
Notes call (the app's writer instruction, model and schema), then Talk B with those Notes in
Anna's prompt: once with the normal opening, once with the recall opening. Prints every line,
flags what the bar forbids, and saves the Notes so `run.py --notes` can run the test set with them.

Usage: <venv>/bin/python -I tools/talk_test_set/two_talks.py [--label NAME]
Needs google-genai (see README). Key: GEMINI_API_KEY in android/local.properties, never printed.
Writes tools/out/two_talks/<label>/ (git-ignored): notes.json, results.json and Anna's replies as WAV.

Judge (the owner listens): the Notes have "You have two kids." and nothing about health, religion,
politics, money or an address; in Talk B Anna never asks whether the learner has kids or a job,
never checks a note ("..., right?"), brings up a note by herself at most once after the hello, and
makes nothing up. The normal hello is plain; the recall hello uses one note.
"""
import argparse
import asyncio
import dataclasses
import datetime
import json
import pathlib
import re
import subprocess
import sys

from google import genai
from google.genai import types

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import run  # noqa: E402
import testset  # noqa: E402

REPO = pathlib.Path(__file__).resolve().parents[2]

TALK_A = [
    "Hi. My name is Rustam.",
    "I have two kids, a boy and a girl.",
    "I work in a big shop. I am a manager.",
    "My back hurts a lot today.",
    "OK, bye!",
]
TALK_B = [
    "My day is OK. A lot of work.",
    "I am going home now.",
    "This evening I want to rest.",
    "What about you? What are you doing tonight?",
    "OK, bye!",
]
ASKS_AGAIN = re.compile(r"do you have (any )?(kids|children)|what('s| is) your (job|name)|where do you work|what do you do\b", re.I)
CHECKS = re.compile(r"(kids|children|manager|shop|name|rustam)[^.?!]*,\s*right\?", re.I)
NOTE_WORDS = re.compile(r"kids|children|son|daughter|shop|manager|rustam", re.I)
NEVER_WRITE = re.compile(r"\bback\b|hurt|pain|doctor|money|salary|address|street|church|mosque|pray", re.I)


def clip(text: str, folder: pathlib.Path) -> bytes:
    path = folder / (re.sub(r"\W+", "_", text)[:40] + ".wav")
    if not path.exists():
        subprocess.run(["say", "-v", "Samantha", "-o", str(path), "--file-format=WAVE",
                        "--data-format=LEI16@16000", text], check=True)
    import wave
    with wave.open(str(path)) as w:
        return w.readframes(w.getnframes())


async def talk(client, setup, opening, lines, out, tag):
    out.mkdir(parents=True, exist_ok=True)
    recs = []
    print(f"\n===== {tag}")
    async with client.aio.live.connect(model=setup.model, config=run.live_config(setup)) as s:
        for i, line in enumerate([None] + lines):
            rec, audio = await (run.turn(s, text=opening) if line is None else run.turn(s, audio=clip(line, out.parent / "clips")))
            run.save(out / f"anna_{i:02d}.wav", audio)
            rec["line"] = line or opening
            recs.append(rec)
            print(f"[{i}] {rec['line']}" + (f"\n    heard: {rec['heard']!r}" if line else ""))
            print(f"    Anna:  {rec['said']}")
    return recs


def write_notes(client, setup, recs, today):
    """The app's Notes call (`Notes.request`), on `responseJsonSchema`."""
    transcript = "\n".join((f"Learner: {r['heard']}\n" if r["line"] in TALK_A else "") + f"Anna: {r['said']}" for r in recs)
    text = f"Today is {today.strftime('%A')}, {today.day} {today.strftime('%b %Y')}.\n\nOld facts:\n(none)\n\nToday's Talk:\n{transcript}"
    schema = {"type": "object", "properties": {"facts": {"type": "array", "items": {"type": "string"}, "maxItems": 20},
                                               "topics": {"type": "string"}}, "required": ["facts", "topics"]}
    resp = client.models.generate_content(model=setup.notes_model, contents=text, config=types.GenerateContentConfig(
        system_instruction=setup.notes_writer.strip(), response_mime_type="application/json",
        response_json_schema=schema, thinking_config=types.ThinkingConfig(thinking_level="low")))
    got = json.loads(resp.text)
    facts = [f.strip()[:60].strip() for f in got["facts"] if f.strip()][:20]
    return {"facts": facts, "talks": [testset.talk_line(today, got["topics"][:60])]}


def judge(notes, b_runs):
    problems = []
    if not any(re.search(r"two (kids|children)", f, re.I) for f in notes["facts"]):
        problems.append("Notes lack the two kids")
    problems += [f"never-write in Notes: {f}" for f in notes["facts"] if NEVER_WRITE.search(f)]
    for tag, recs in b_runs:
        for i, r in enumerate(recs):
            if ASKS_AGAIN.search(r["said"]):
                problems.append(f"{tag}[{i}] asks again: {r['said']}")
            if CHECKS.search(r["said"]):
                problems.append(f"{tag}[{i}] checks a note: {r['said']}")
            if NEVER_WRITE.search(r["said"]):
                problems.append(f"{tag}[{i}] never-write topic: {r['said']}")
        # Unprompted uses after the hello, outside the learner's own lines about it (rough: the owner judges).
        uses = sum(1 for r in recs[1:] if NOTE_WORDS.search(r["said"]) and not NOTE_WORDS.search(r["line"]))
        if uses > 1:
            problems.append(f"{tag}: {uses} unprompted uses of Notes after the hello (bar: at most 1)")
    return problems


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", default=datetime.datetime.now().strftime("%Y%m%d-%H%M"))
    a = ap.parse_args()
    setup = testset.app_setup(REPO)
    out = REPO / "tools/out/two_talks" / a.label
    (out / "clips").mkdir(parents=True, exist_ok=True)
    client = genai.Client(api_key=testset.api_key(REPO), http_options={"api_version": setup.api_version})
    today = datetime.date.today()

    a_recs = await talk(client, setup, setup.opening, TALK_A, out / "talk_a", "Talk A (no Notes)")
    notes = write_notes(client, setup, a_recs, today)
    print("\nNotes:", json.dumps(notes, ensure_ascii=False, indent=2))
    (out / "notes.json").write_text(json.dumps(notes, ensure_ascii=False, indent=2), encoding="utf-8")

    with_notes = dataclasses.replace(setup, prompt=testset.prompt_with_notes(setup, notes["facts"], notes["talks"], today))
    b1 = await talk(client, with_notes, setup.opening, TALK_B, out / "talk_b", "Talk B (normal opening)")
    b2 = await talk(client, with_notes, setup.opening_recall, TALK_B, out / "talk_b_recall", "Talk B (recall opening)")

    problems = judge(notes, [("B", b1), ("B recall", b2)])
    (out / "results.json").write_text(json.dumps({"notes": notes, "talk_a": a_recs, "talk_b": b1, "talk_b_recall": b2,
                                                  "flags": problems}, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\nFlags (a script's rough view; the owner listens and decides):")
    print("\n".join(f"  - {p}" for p in problems) or "  none")
    print(f"\nTest set with these Notes: <venv>/bin/python -I tools/talk_test_set/run.py CLIPS --notes {out / 'notes.json'}")
    print(f"-> {out}")


if __name__ == "__main__":
    asyncio.run(main())
