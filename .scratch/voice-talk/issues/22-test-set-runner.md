# 22: Test set runner from the app's prompt

**What to build:** a repeatable way to check Anna before any model or prompt change. The talk partner prompt lives in one file that both the app and a Mac script read, so they can't drift. The script, grown from the throwaway probe on local branch `probe/cloud-turn` (never merged; copy what's needed), plays the 11 test set cases as one Talk, from machine-voice or the owner's recordings, prints what Anna heard and said and the time to first audio, and reports against the bar. Spec §4 "Test set".

**Blocked by:** 14 (Anna says hello)

**Status:** done

- [x] The app's prompt comes from a single text file that the APK bundles and the script reads; there's no second copy.
- [x] The script uses the same setup as the app (v1beta, model, voice, activity detection off, the bare-text opening) and reads the key from `local.properties` without printing it.
- [x] It runs the opening plus the 10 learner lines in order as one Talk, from a chosen folder of 16 kHz WAV clips (machine voice or the owner's).
- [x] The output lists each case's heard text, Anna's reply and first-audio time, and a short README says how to judge the pass bar: ≥ 9/11, with cases 7, 10 and 11 required.
- [x] No recordings or results are committed unless the owner chooses to.
