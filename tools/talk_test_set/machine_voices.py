"""Make the learner lines with macOS `say` (a smoke test; the owner's voice is the real check).

Usage: python3 -I tools/talk_test_set/machine_voices.py [FOLDER]
Default folder: tools/out/talk_voices/machine (git-ignored).
"""
import pathlib
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import testset  # noqa: E402

REPO = pathlib.Path(__file__).resolve().parents[2]
CYRILLIC = set("абвгдеёжзийклмнопрстуфхцчшщъыьэюя")

folder = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else REPO / "tools/out/talk_voices/machine"
folder.mkdir(parents=True, exist_ok=True)
for case in testset.CASES:
    if case.line is None:
        continue
    voice = "Milena" if CYRILLIC & set(case.line.lower()) else "Samantha"
    out = folder / testset.clip_name(case.number)
    subprocess.run(["say", "-v", voice, "-o", str(out), "--file-format=WAVE", "--data-format=LEI16@16000",
                    case.line], check=True)
    print(f"{out.name}  ({voice})  {case.line}")
testset.load_clips(folder)  # checks the format
print(f"-> {folder}")
