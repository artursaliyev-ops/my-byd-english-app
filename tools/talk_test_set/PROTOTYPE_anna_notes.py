"""PROTOTYPE (throwaway): how Anna uses Notes. Ticket: .scratch/anna-notes/issues/03-anna-with-notes.md

Not production. Answers one question: with Notes in her system instruction, does Anna use them lightly,
not ask again what she knows, keep off never-write topics, use a name only now and then, and does the
test set still pass?

Steps (all printed, all audio saved for listening):
  1. Talk A, no Notes: the learner says a name, two kids, a job, a health thing (never write) and a plan.
  2. A stand-in Notes writer (one gemini-3.1-flash-lite call, JSON schema) turns Talk A into Notes.
     How the list is really kept is another ticket; this is only enough to feed Talk B.
  3. Talk B with those Notes, three times: twice with the normal opening (does Anna decide "sometimes"
     herself?) and once with an opening that asks her to bring up one thing from last time.
  4. The test set (machine voices) with the same Notes present.

Usage: ~/talk-probe/cloud/.venv/bin/python -I tools/talk_test_set/PROTOTYPE_anna_notes.py [v1|v2|v3] [--reuse]
Writes tools/out/anna_notes_prototype/ (git-ignored): report.html, notes.json, WAVs.
"""
import asyncio
import datetime
import html
import json
import pathlib
import re
import subprocess
import sys
import wave

from google import genai
from google.genai import types

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import testset  # noqa: E402

REPO = pathlib.Path(__file__).resolve().parents[2]
OUT = REPO / "tools/out/anna_notes_prototype"

# run.py has no import guard: take its turn()/live_config()/save() without running its main().
_src = (pathlib.Path(__file__).parent / "run.py").read_text(encoding="utf-8").replace("asyncio.run(main())", "")
_run = {"__file__": str(pathlib.Path(__file__).parent / "run.py")}
exec(compile(_src, "run.py", "exec"), _run)
turn, live_config, save = _run["turn"], _run["live_config"], _run["save"]

# ---- the draft under test -------------------------------------------------------------------------

# The base prompt says "You don't know the learner's name and you don't ask it." With Notes a name may
# be known, so the draft changes that one sentence.
NAME_OLD = "You don't know the learner's name and you don't ask it."
NAME_NEW = "You never ask the learner's name."

NOTES_BLOCK_V1 = """
Notes from earlier Talks. The app keeps them. In a note, "you" means the learner, not you.
About the learner:
{facts}
Last Talks:
{talks}

How to use the notes:
- You already know these things. Never ask again what a note says (if a note says "You have two kids.", never ask "Do you have kids?").
- Use them lightly, like a friend who remembers: now and then, not in every reply. Most replies are about what the learner says now.
- Bring up a note only when it fits what the learner is talking about. At most one note at a time.
- If the learner says something different from a note, believe the learner.
- If a note gives the learner's name, use the name only sometimes, never in two replies in a row.
- Never say "notes", "the app", "I remember you said" more than once, or that something was written down.
"""

# v2: after v1 used Notes in 4-5 of 7 replies and once made up a memory ("your cat").
NOTES_BLOCK_V2 = """
Notes from earlier Talks. The app keeps them. In a note, "you" means the learner, not you. Today is {today}.
About the learner:
{facts}
Last Talks:
{talks}

How to use the notes:
- You already know these things. Never ask again what a note says, and don't ask to check it (no "You have two kids, right?").
- In the hello, bring up a note only when the app asks for it. Otherwise a normal hello.
- After the hello, bring up a note by yourself at most once in the whole Talk, and only when the learner talks about that same thing. All other replies are only about what the learner says now.
- Only what the notes say. Never make up other things from earlier Talks.
- If the learner says something different from a note, believe the learner.
- If a note gives the learner's name, say it at most once in a Talk.
- Never say "notes" or "the app".
"""
# v3: owner's verdict on v2 (2026-11-22): amount right, app decides the hello, no "..., right?" checks, keep the name.
NOTES_BLOCK_V3 = """
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
"""
DRAFTS = {"v1": NOTES_BLOCK_V1, "v2": NOTES_BLOCK_V2, "v3": NOTES_BLOCK_V3}
DRAFT = "v1"

OPENING_RECALL = "(The learner pressed Talk. Start the Talk. In your hello, ask about one thing from the notes.)"


def prompt_with_notes(base: str, notes: dict | None) -> str:
    p = base.replace(NAME_OLD, NAME_NEW)
    assert NAME_NEW in p, "base prompt changed; update NAME_OLD"
    if not notes or not (notes["facts"] or notes["talks"]):
        return p
    return p.rstrip() + "\n" + DRAFTS[DRAFT].format(today=datetime.date.today().strftime("%A, %-d %b"),
        facts="\n".join(f"- {f}" for f in notes["facts"]) or "- (none yet)",
        talks="\n".join(f"- {t}" for t in notes["talks"]) or "- (none yet)")


# ---- stand-in Notes writer (ticket 01's option b; the real rules are ticket 02's) ---------------------

WRITER_MODEL = "gemini-3.1-flash-lite"
WRITER_RULES = """You keep short notes about an English learner for Anna, their talk partner. You get the old notes and the transcript of today's Talk (speech recognition, so some words may be wrong). Return the whole new list of facts and the topics of today's Talk.
Facts:
- Lasting facts about the learner only: job, family, kids, car, likes, plans for the next days. The name only if the learner said it.
- Very short, simple English (A1-A2), second person, one fact per line: "You have two kids." "Your name is Rustam."
- Keep the old facts unless today's Talk changes them. A correction replaces the old fact; never keep both.
- Never write anything about health, religion, politics, money or exact addresses, even if the learner said it.
- Nothing that is only true today ("You are tired today.").
- At most 20 facts.
Topics: two to four topics of today's Talk, a few words each, comma separated, like "work, the weekend". No topic about health, religion, politics, money or addresses."""
WRITER_SCHEMA = {
    "type": "object",
    "properties": {"facts": {"type": "array", "items": {"type": "string"}, "maxItems": 20},
                   "topics": {"type": "string"}},
    "required": ["facts", "topics"],
}


def write_notes(client, old: dict, talk: list[dict], date: str) -> tuple[dict, dict]:
    transcript = "\n".join(
        (f"Learner: {r['heard']}\n" if r.get("heard") else "") + f"Anna: {r['said']}" for r in talk)
    old_txt = "\n".join(old["facts"]) or "(none)"
    resp = client.models.generate_content(
        model=WRITER_MODEL,
        contents=f"Old facts:\n{old_txt}\n\nToday's Talk:\n{transcript}",
        config=types.GenerateContentConfig(
            system_instruction=WRITER_RULES, response_mime_type="application/json",
            response_json_schema=WRITER_SCHEMA, thinking_config=types.ThinkingConfig(thinking_level="low")))
    got = json.loads(resp.text)
    facts = [f.strip()[:60] for f in got["facts"] if f.strip()][:20]
    talks = ([f"{date} — {got['topics'].strip().rstrip('.')}."] + old["talks"])[:10]
    return {"facts": facts, "talks": talks}, {"transcript": transcript, "raw": got}


# ---- the Talks ----------------------------------------------------------------------------------

TALK_A = [
    "Hi. My name is Rustam.",
    "I have two kids, a boy and a girl.",
    "I work in a big shop. I am a manager.",
    "My back hurts a lot today.",
    "On Saturday we are going to my mother's house.",
    "OK, bye!",
]
TALK_B = [
    "My day is OK. A lot of work.",
    "I am going home now.",
    "This evening I want to rest.",
    "What about you? What are you doing tonight?",
    "Do you remember me?",
    "OK, bye!",
]
# Earlier Talks, so the list looks like it will in use (the writer adds Talk A's line on top).
OLDER_TALKS = ["20 Nov — the traffic, food.", "18 Nov — the weather, music."]

ASKS_AGAIN = re.compile(r"do you have (any )?(kids|children)|what('s| is) your (job|name)|where do you work|"
                        r"what do you do\b|are you married", re.I)
NEVER_WRITE = re.compile(r"\bback\b|hurt|pain|doctor|sick|church|mosque|pray|money|salary|price|address|street", re.I)


def note_hits(said: str, notes: dict) -> list[str]:
    """Crude: which Notes words show up in a reply (the owner's ear is the real judge)."""
    keys = {"kids": r"kids|children|son|daughter|boy|girl", "shop/manager": r"shop|manager|store",
            "mother/Saturday": r"mother|mom|saturday|weekend", "name": r"rustam",
            "traffic/food/weather/music": r"traffic|food|weather|music"}
    return [k for k, rx in keys.items() if re.search(rx, said, re.I)]


def clip(text: str, path: pathlib.Path) -> bytes:
    if not path.exists():
        subprocess.run(["say", "-v", "Samantha", "-o", str(path), "--file-format=WAVE",
                        "--data-format=LEI16@16000", text], check=True)
    with wave.open(str(path)) as w:
        return w.readframes(w.getnframes())


async def talk(client, setup, prompt: str, opening: str, lines: list[str], tag: str) -> list[dict]:
    cfg = live_config(setup)
    cfg.system_instruction = prompt
    out, recs = OUT / tag, []
    out.mkdir(parents=True, exist_ok=True)
    print(f"\n===== {tag}")
    async with client.aio.live.connect(model=setup.model, config=cfg) as s:
        for i, line in enumerate([None] + lines):
            if line is None:
                rec, audio = await turn(s, text=opening)
            else:
                rec, audio = await turn(s, audio=clip(line, OUT / "clips" / f"{re.sub(r'\\W+', '_', line)[:40]}.wav"))
            wav = out / f"anna_{i:02d}.wav"
            save(wav, audio)
            rec.update(line=line or opening, wav=str(wav.relative_to(OUT)))
            recs.append(rec)
            print(f"[{i}] learner: {rec['line']}" + (f"\n    heard:   {rec['heard']!r}" if line else ""))
            print(f"    Anna:    {rec['said']}")
    return recs


def flags(rec: dict, notes: dict | None) -> list[str]:
    f = []
    if notes:
        f += [f"note: {h}" for h in note_hits(rec["said"], notes)]
    if ASKS_AGAIN.search(rec["said"]):
        f.append("ASKS AGAIN")
    if NEVER_WRITE.search(rec["said"]):
        f.append("NEVER-WRITE WORD")
    return f


def report(sections: list[tuple[str, list[dict], dict | None]], notes: dict, writer: dict, prompt: str):
    h = ["<!doctype html><meta charset=utf-8><title>Anna notes prototype</title>",
         "<style>body{font:15px system-ui;max-width:860px;margin:2em auto;padding:0 16px}"
         "td{vertical-align:top;padding:4px 8px;border-bottom:1px solid #ddd}.f{color:#b00;font-size:13px}"
         "pre{white-space:pre-wrap;background:#f4f4f4;padding:8px}</style>",
         "<h1>PROTOTYPE: how Anna uses Notes</h1><p>Throwaway. Listen to each reply and judge: lightly? "
         "never asks again? no never-write topic? name only sometimes?</p>",
         f"<h2>Notes written from Talk A</h2><pre>{html.escape(json.dumps(notes, ensure_ascii=False, indent=2))}</pre>",
         f"<details><summary>Writer input / raw output</summary><pre>{html.escape(json.dumps(writer, ensure_ascii=False, indent=2))}</pre></details>",
         f"<details><summary>Anna's prompt in Talk B</summary><pre>{html.escape(prompt)}</pre></details>"]
    for title, recs, n in sections:
        h.append(f"<h2>{html.escape(title)}</h2><table>")
        for r in recs:
            fl = ", ".join(flags(r, n))
            h.append(f"<tr><td>{html.escape(r['line'])}<br><small>heard: {html.escape(r.get('heard', ''))}</small></td>"
                     f"<td>{html.escape(r['said'])}<div class=f>{html.escape(fl)}</div>"
                     f"<audio controls preload=none src='{r['wav']}'></audio></td></tr>")
        h.append("</table>")
    (OUT / "report.html").write_text("\n".join(h), encoding="utf-8")


async def main():
    global OUT, DRAFT
    setup = testset.app_setup(REPO)
    (OUT / "clips").mkdir(parents=True, exist_ok=True)
    client = genai.Client(api_key=testset.api_key(REPO), http_options={"api_version": setup.api_version})
    date = datetime.date.today().strftime("%-d %b")

    DRAFT = sys.argv[1] if len(sys.argv) > 1 else "v1"
    base = OUT
    OUT = base / DRAFT
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "clips").mkdir(exist_ok=True)
    if "--reuse" in sys.argv:  # Talk A and its Notes from the v1 run
        saved = json.loads((base / "v1" / "notes.json").read_text())
        a, notes, writer = saved["talk_a"], saved["notes"], saved["writer"]
    else:
        a = await talk(client, setup, prompt_with_notes(setup.prompt, None), setup.opening, TALK_A, "talk_a")
        notes, writer = write_notes(client, {"facts": [], "talks": OLDER_TALKS}, a, date)
    print("\nNotes:", json.dumps(notes, ensure_ascii=False, indent=2))
    (OUT / "notes.json").write_text(json.dumps({"notes": notes, "writer": writer, "talk_a": a}, ensure_ascii=False, indent=2))
    pb = prompt_with_notes(setup.prompt, notes)

    b1 = await talk(client, setup, pb, setup.opening, TALK_B, "talk_b1")
    b2 = await talk(client, setup, pb, setup.opening, TALK_B, "talk_b2")
    b3 = await talk(client, setup, pb, OPENING_RECALL, TALK_B, "talk_b3_recall_opening")

    clips = testset.load_clips(REPO / "tools/out/talk_voices/machine")
    cfg = live_config(setup)
    cfg.system_instruction = pb
    ts, tdir = [], OUT / "testset_with_notes"
    tdir.mkdir(exist_ok=True)
    print("\n===== test set with Notes")
    async with client.aio.live.connect(model=setup.model, config=cfg) as s:
        for c in testset.CASES:
            rec, audio = await (turn(s, text=setup.opening) if c.line is None else turn(s, audio=clips[c.number]))
            save(tdir / f"anna_{c.number:02d}.wav", audio)
            ex = testset.says_exactly(c, rec["said"])
            rec.update(line=f"[{c.number}{'*' if c.required else ''}] {c.line or setup.opening}  (expect: {c.expect})"
                       + ("" if ex is None else f" [exact: {'yes' if ex else 'NO'}]"),
                       wav=f"testset_with_notes/anna_{c.number:02d}.wav")
            ts.append(rec)
            print(f"{rec['line']}\n    Anna: {rec['said']}")

    report([("Talk A (no Notes)", a, None), ("Talk B, run 1 (normal opening)", b1, notes),
            ("Talk B, run 2 (normal opening)", b2, notes), ("Talk B, run 3 (opening asks for one note)", b3, notes),
            ("Test set with Notes (machine voices; * = required)", ts, notes)], notes, writer, pb)
    print(f"\n-> {OUT / 'report.html'}")


asyncio.run(main())
