"""Play the talk partner test set (spec §4) into Gemini Live as one Talk, with the app's setup.

The opening goes as bare realtime text, as the app sends it. Each learner clip is wrapped in
activityStart / activityEnd (activity detection is off: the head unit decides the end of speech)
and streamed in real time in 100 ms chunks. First-audio time is from activityEnd (from the
opening text for case 1); the app adds its end-of-speech silence window on top.

Usage: <venv>/bin/python -I tools/talk_test_set/run.py CLIPS_FOLDER [--label NAME]
Needs google-genai (see README). Key: GEMINI_API_KEY in android/local.properties, never printed.
Writes tools/out/talk_test_set/<label>/ (git-ignored): results.json and Anna's replies as WAV.
"""
import argparse
import asyncio
import json
import pathlib
import re
import sys
import time
import wave

from google import genai
from google.genai import types

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import testset  # noqa: E402

REPO = pathlib.Path(__file__).resolve().parents[2]
OUT_RATE, CHUNK_MS = 24_000, 100


def live_config(s: testset.AppSetup) -> types.LiveConnectConfig:
    """The same setup as the app's LiveProtocol.setup (no thinking config, like the app)."""
    return types.LiveConnectConfig(
        response_modalities=["AUDIO"],
        system_instruction=s.prompt,
        speech_config=types.SpeechConfig(
            voice_config=types.VoiceConfig(prebuilt_voice_config=types.PrebuiltVoiceConfig(voice_name=s.voice))),
        realtime_input_config=types.RealtimeInputConfig(
            automatic_activity_detection=types.AutomaticActivityDetection(disabled=True)),
        context_window_compression=types.ContextWindowCompressionConfig(sliding_window=types.SlidingWindow()),
        session_resumption=types.SessionResumptionConfig(),
        input_audio_transcription=types.AudioTranscriptionConfig(),
        output_audio_transcription=types.AudioTranscriptionConfig(),
    )


async def turn(session, audio=None, text=None):
    if audio is not None:
        await session.send_realtime_input(activity_start=types.ActivityStart())
        step = testset.IN_RATE * 2 * CHUNK_MS // 1000
        for i in range(0, len(audio), step):
            await session.send_realtime_input(
                audio=types.Blob(data=audio[i:i + step], mime_type=f"audio/pcm;rate={testset.IN_RATE}"))
            await asyncio.sleep(CHUNK_MS / 1000)
        await session.send_realtime_input(activity_end=types.ActivityEnd())
    if text is not None:
        await session.send_realtime_input(text=text)  # never inside an activity (3.1 closes with 1007)
    t0 = time.monotonic()
    rec = {"first_audio_s": None, "heard": "", "said": ""}
    out = bytearray()
    async for msg in session.receive():
        sc = msg.server_content
        if not sc:
            continue
        if sc.input_transcription and sc.input_transcription.text:
            rec["heard"] += sc.input_transcription.text
        if sc.output_transcription and sc.output_transcription.text:
            rec["said"] += sc.output_transcription.text
        for part in (sc.model_turn.parts or []) if sc.model_turn else []:
            if part.inline_data and part.inline_data.data:
                rec["first_audio_s"] = rec["first_audio_s"] or round(time.monotonic() - t0, 2)
                out += part.inline_data.data
        if sc.turn_complete:
            break
    rec["heard"] = rec["heard"].strip()
    rec["said"] = re.sub(r"\s+", " ", rec["said"]).strip()
    return rec, bytes(out)


def save(path, pcm):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1), w.setsampwidth(2), w.setframerate(OUT_RATE)
        w.writeframes(pcm)


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("clips", type=pathlib.Path, help="folder of case_02.wav..case_11.wav, 16 kHz mono")
    ap.add_argument("--label", help="results folder name (default: clips folder name)")
    a = ap.parse_args()
    setup = testset.app_setup(REPO)
    clips = testset.load_clips(a.clips)
    out_dir = REPO / "tools/out/talk_test_set" / (a.label or a.clips.name)
    out_dir.mkdir(parents=True, exist_ok=True)

    client = genai.Client(api_key=testset.api_key(REPO), http_options={"api_version": setup.api_version})
    print(f"{setup.model}, voice {setup.voice}, {setup.api_version}, clips {a.clips}")
    results = []
    async with client.aio.live.connect(model=setup.model, config=live_config(setup)) as session:
        for case in testset.CASES:
            if case.line is None:
                rec, audio = await turn(session, text=setup.opening)
            else:
                rec, audio = await turn(session, audio=clips[case.number])
            save(out_dir / f"anna_{case.number:02d}.wav", audio)
            exact = testset.says_exactly(case, rec["said"])
            results.append({"case": case.number, "line": case.line or setup.opening, "expect": case.expect,
                            "required": case.required, "exact_match": exact, **rec})
            print(f"\n[{case.number}]{' (required)' if case.required else ''} first audio {rec['first_audio_s']} s")
            print(f"  learner: {case.line or setup.opening}")
            if case.line is not None:
                print(f"  heard:   {rec['heard']!r}")
            print(f"  Anna:    {rec['said']}")
            print(f"  expect:  {case.expect}" + ("" if exact is None else f"  [exact reply: {'yes' if exact else 'NO'}]"))
    firsts = sorted(r["first_audio_s"] for r in results if r["first_audio_s"] is not None)
    report = {"model": setup.model, "voice": setup.voice, "api_version": setup.api_version,
              "clips": str(a.clips), "turns": results}
    (out_dir / "results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if firsts:
        print(f"\nfirst audio: min {firsts[0]} / median {firsts[len(firsts) // 2]} / max {firsts[-1]} s")
    print(f"Judge each case against 'expect' (README). Pass: >= {testset.PASS_BAR}/11 with 7, 10, 11. -> {out_dir}")


asyncio.run(main())
