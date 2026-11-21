"""Record the owner's voice for the test set, one trimmed 16 kHz mono WAV per learner line.

Usage: python3 -I tools/talk_test_set/record.py [FOLDER]          (records the missing lines)
       python3 -I tools/talk_test_set/record.py [FOLDER] --case 7 (re-records case 7)
Default folder: tools/out/talk_voices/owner (git-ignored). Needs ffmpeg; Enter starts and stops.
"""
import argparse
import pathlib
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import testset  # noqa: E402

REPO = pathlib.Path(__file__).resolve().parents[2]
FFMPEG = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
PCM16K = ["-ac", "1", "-ar", "16000", "-sample_fmt", "s16"]


def record(folder, case):
    raw = folder / f"case_{case.number:02d}.raw.wav"
    wav = folder / testset.clip_name(case.number)
    input(f"\n[{case.number}] Say: «{case.line}»\n    Enter to start...")
    p = subprocess.Popen(FFMPEG + ["-f", "avfoundation", "-i", ":0"] + PCM16K + [str(raw)], stdin=subprocess.PIPE)
    input("    Recording... Enter to stop.")
    p.communicate(b"q")
    # Trim silence at both ends: timing starts where the app's end-of-speech would close the Turn.
    trim = "silenceremove=start_periods=1:start_threshold=-45dB,areverse"
    subprocess.run(FFMPEG + ["-i", str(raw), "-af", f"{trim},{trim}"] + PCM16K + [str(wav)], check=True)
    raw.unlink()
    subprocess.run(["afplay", str(wav)])
    if input("    Keep it? [Y/n] ").strip().lower() == "n":
        record(folder, case)


ap = argparse.ArgumentParser()
ap.add_argument("folder", nargs="?", type=pathlib.Path, default=REPO / "tools/out/talk_voices/owner")
ap.add_argument("--case", type=int)
a = ap.parse_args()
a.folder.mkdir(parents=True, exist_ok=True)
for case in testset.CASES:
    if case.line is None or (a.case is not None and case.number != a.case):
        continue
    if a.case is None and (a.folder / testset.clip_name(case.number)).exists():
        continue
    record(a.folder, case)
print("\nDone:", sorted(p.name for p in a.folder.glob("case_*.wav")))
