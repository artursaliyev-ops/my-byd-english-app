"""The talk partner test set (spec §4) and the app's Talk setup, read from the app's own files.

No second copy of anything the app decides: the model, voice, API version, opening text and the
prompt asset's path come from `TalkConfig.kt`, and the prompt from the asset the APK bundles.
"""
from __future__ import annotations

import pathlib
import re
import wave
from dataclasses import dataclass

TALK_CONFIG = "android/app/src/main/java/app/bydplayer/talk/TalkConfig.kt"
ASSETS = "android/app/src/main/assets"


@dataclass(frozen=True)
class AppSetup:
    api_version: str
    model: str
    voice: str
    opening: str
    prompt: str


IN_RATE = 16_000


@dataclass(frozen=True)
class Case:
    number: int
    line: str | None  # what the learner says; None for the opening (app control text)
    expect: str
    exact: str | None = None  # the whole reply, when the prompt fixes it word for word
    required: bool = False  # must pass whatever the score (it drives app behaviour)


# Spec §4 "Test set": one Talk, in this order. Pass bar: >= 9/11, and 7, 10 and 11 must pass.
CASES = [
    Case(1, None, "Short hello + one easy question about day/road/weather"),
    Case(2, "My day is good. I go to work now.", "Recast the tense (\"going to work\"...); ends with a question"),
    Case(3, "Yesterday I buy a new car.", "Recast \"bought\"; no \"mistake\"/\"should say\""),
    Case(4, "I like drive in the morning, road is empty.",
         "Ignores the article slip; may recast \"like driving\"; question"),
    Case(5, "in Russian?", "One short Russian sentence, then the question in simpler English"),
    Case(6, "Я не знаю, как сказать.", "\"You can say: I don't know how to say it.\" then carries on in English"),
    Case(7, "the uh", "Only \"Sorry, can you say that again?\"", exact="Sorry, can you say that again?",
         required=True),
    Case(8, "wait", "Only \"OK.\"", exact="OK."),
    Case(9, "What do you think about the president?", "Declines politics kindly, simple topic, question"),
    Case(10, "By the way, I have two children.", "Not goodbye; reacts to the children; question", required=True),
    Case(11, "OK, bye!", "Only \"Bye! Drive safe. Talk to you later.\"",
         exact="Bye! Drive safe. Talk to you later.", required=True),
]
PASS_BAR = 9


def _words(text: str) -> list:
    return ["ok" if w == "okay" else w for w in re.findall(r"[a-z']+", text.lower())]


def says_exactly(case: Case, said: str) -> bool | None:
    """For a case whose reply the prompt fixes word for word: did Anna say just that? The output
    transcript's punctuation and case vary, so only the words count. None when nothing is fixed."""
    if case.exact is None:
        return None
    return _words(said) == _words(case.exact)


def clip_name(number: int) -> str:
    return f"case_{number:02d}.wav"


def load_clips(folder: pathlib.Path) -> dict:
    """The learner lines' audio (16 kHz mono 16-bit WAV, case_02.wav..case_11.wav) as raw PCM."""
    clips, problems = {}, []
    for case in CASES:
        if case.line is None:
            continue
        path = folder / clip_name(case.number)
        if not path.exists():
            problems.append(f"{path.name} missing")
            continue
        with wave.open(str(path)) as w:
            fmt = (w.getframerate(), w.getnchannels(), w.getsampwidth())
            if fmt != (IN_RATE, 1, 2):
                problems.append(f"{path.name} is {fmt[0]} Hz, {fmt[1]} ch, {fmt[2] * 8} bit (want 16000 Hz mono 16 bit)")
                continue
            clips[case.number] = w.readframes(w.getnframes())
    if problems:
        raise SystemExit(f"Clips in {folder}: " + "; ".join(problems))
    return clips


def api_key(repo: pathlib.Path) -> str:
    """GEMINI_API_KEY from android/local.properties (not in git). Never printed."""
    for line in (repo / "android/local.properties").read_text(encoding="utf-8").splitlines():
        name, _, value = line.partition("=")
        if name.strip() == "GEMINI_API_KEY" and value.strip():
            return value.strip()
    raise SystemExit("GEMINI_API_KEY is missing from android/local.properties")


def _consts(kotlin: str) -> dict:
    """Plain string constants (`const val NAME = "..."`) of a Kotlin file."""
    return dict(re.findall(r'const val (\w+)\s*=\s*"([^"$\\]*)"', kotlin))


def app_setup(repo: pathlib.Path) -> AppSetup:
    consts = _consts((repo / TALK_CONFIG).read_text(encoding="utf-8"))
    try:
        return AppSetup(
            api_version=consts["API_VERSION"],
            model=consts["MODEL"],
            voice=consts["VOICE"],
            opening=consts["OPENING"],
            prompt=(repo / ASSETS / consts["PROMPT_ASSET"]).read_text(encoding="utf-8"),
        )
    except KeyError as e:
        raise SystemExit(f"{TALK_CONFIG} no longer has a plain string constant {e}; update testset.py")
