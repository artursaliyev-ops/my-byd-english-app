"""Unit tests for the test set runner's pure parts. No network.

Run: python3 -I tools/talk_test_set/test_testset.py
"""
import pathlib
import sys
import tempfile
import unittest
import wave

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import testset  # noqa: E402

REPO = pathlib.Path(__file__).resolve().parents[2]


class AppSetupTest(unittest.TestCase):
    def test_reads_the_apps_talk_config_and_prompt_asset(self):
        s = testset.app_setup(REPO)
        self.assertEqual(s.api_version, "v1beta")
        self.assertEqual(s.model, "gemini-3.1-flash-live-preview")
        self.assertEqual(s.voice, "Aoede")
        self.assertEqual(s.opening, "(The learner pressed Talk. Start the Talk.)")
        asset = REPO / "android/app/src/main/assets/talk/anna-prompt.txt"
        self.assertEqual(s.prompt, asset.read_text(encoding="utf-8"))
        self.assertTrue(s.prompt.startswith("You are Anna"))


class ApiKeyTest(unittest.TestCase):
    def test_reads_the_key_from_local_properties(self):
        with tempfile.TemporaryDirectory() as d:
            repo = pathlib.Path(d)
            (repo / "android").mkdir()
            (repo / "android/local.properties").write_text("sdk.dir=/x\nGEMINI_API_KEY = abc123\n")
            self.assertEqual(testset.api_key(repo), "abc123")

    def test_missing_key_stops_without_showing_the_file(self):
        with tempfile.TemporaryDirectory() as d:
            repo = pathlib.Path(d)
            (repo / "android").mkdir()
            (repo / "android/local.properties").write_text("sdk.dir=/secret-path\n")
            with self.assertRaises(SystemExit) as e:
                testset.api_key(repo)
            self.assertNotIn("secret-path", str(e.exception))


def write_wav(path, rate=16000, channels=1, frames=1600):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(channels), w.setsampwidth(2), w.setframerate(rate)
        w.writeframes(b"\x01\x00" * frames * channels)


class ClipsTest(unittest.TestCase):
    def test_the_set_is_the_opening_then_the_learner_lines_with_the_goodbye_last(self):
        self.assertEqual(sorted(c.number for c in testset.CASES), list(range(1, 15)))
        self.assertIsNone(testset.CASES[0].line)
        self.assertEqual(testset.CASES[6].line, "the uh")
        self.assertEqual(testset.CASES[-1].line, "OK, bye!")

    def test_loads_one_16khz_clip_per_learner_line(self):
        with tempfile.TemporaryDirectory() as d:
            folder = pathlib.Path(d)
            for n in range(2, 15):
                write_wav(folder / f"case_{n:02d}.wav")
            clips = testset.load_clips(folder)
            self.assertEqual(sorted(clips), list(range(2, 15)))
            self.assertEqual(len(clips[2]), 3200)

    def test_names_missing_and_wrong_format_clips(self):
        with tempfile.TemporaryDirectory() as d:
            folder = pathlib.Path(d)
            for n in range(2, 15):
                if n != 11:
                    write_wav(folder / f"case_{n:02d}.wav", rate=22050 if n == 5 else 16000)
            with self.assertRaises(SystemExit) as e:
                testset.load_clips(folder)
            self.assertIn("case_11.wav missing", str(e.exception))
            self.assertIn("case_05.wav", str(e.exception))
            self.assertNotIn("case_04.wav", str(e.exception))


class ExactReplyTest(unittest.TestCase):
    bye = testset.CASES[-1]

    def test_the_fixed_reply_matches_despite_transcript_punctuation_and_case(self):
        self.assertTrue(testset.says_exactly(self.bye, "bye, drive safe! Talk to you later"))
        self.assertTrue(testset.says_exactly(testset.CASES[7], "Okay."))

    def test_anything_more_than_the_fixed_reply_fails(self):
        self.assertFalse(testset.says_exactly(self.bye, "Bye! Drive safe. Talk to you later. See you!"))
        self.assertFalse(testset.says_exactly(testset.CASES[6], "Dia? Sorry, can you say that again?"))

    def test_cases_without_a_fixed_reply_have_no_exact_check(self):
        self.assertIsNone(testset.says_exactly(testset.CASES[1], "Nice!"))



class StrongWordsTest(unittest.TestCase):
    def test_mild_swearing_passes_and_stronger_words_are_named(self):
        self.assertEqual(testset.strong_words("Damn, the traffic! What the heck?"), [])
        self.assertEqual(testset.strong_words("Oh shit, the traffic."), ["shit"])


if __name__ == "__main__":
    unittest.main()
