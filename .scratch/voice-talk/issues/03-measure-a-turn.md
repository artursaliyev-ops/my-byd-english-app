# Measure a Turn on the car with the offline stack
Type: task
Status: resolved
Blocked by: 01, 02

## Question

HITL. Build a throwaway probe (not merged) running the recommended offline stack, install it on the car, and in a parked car hold a few Turns of small talk. Measure, per Turn: end of speech → text, text → first model token, first token → first audio, and the total; recognition errors; answer quality (simple English, recasts), judged against the talk partner test set in [Decide how the talk partner behaves](05-talk-partner.md) run on a computer first; RAM, heat, and whether the lesson app or BYD's apps are killed. Use the car-measurement list at the end of the offline-stack research (branch `research/offline-talk-stack`) as the checklist. Try the runner-up model if the first misses the 3–5 s bar. The result feeds the offline-or-cloud decision.

## Answer

Resolved 2026-11-21 (HITL, car parked on the phone's hotspot, BYD apps running). Instead of a probe app, llama.cpp ran over ADB as a command-line server, and the talk partner test set from [Decide how the talk partner behaves](05-talk-partner.md) was played against it from the Mac. Script, prompt and raw results: local branch `probe/talk-turn`, commit `286b804`, `.scratch/voice-talk/probe/` (never merge). Downloads and builds live outside the repo in `~/talk-probe/`, and binaries and models stay on the car in `/data/local/tmp/talk/`.

**The car while measuring**: `ro.soc.model` is SM7325 (Snapdragon 778G). BYD's own processes (dashcam `com.byd.cdr`, cameraserver, the hardware video codec, `com.byd.autovoice.engine`, `bydextend_helper`) use about 3 of 8 cores the whole time. 4.6 GB was available before the model loaded and 2.5 GB with Qwen3.5-2B loaded, with 0.66 GB already in swap. CPUs stayed at ~56 °C and skin at 27 °C. No BYD app was killed.

**Qwen3.5-2B Q4_K_M** (1.18 GiB, llama.cpp CPU):
- llama-bench, 4 threads: prompt 41–49 tok/s, **generation 3.7 tok/s**. That rises to 5.3 tok/s pinned to the 4 big cores (`-C 0xF0`). 1 thread gives 4.35 and 8 threads 0.47, so it barely scales: the machine is shared and memory-bound, not short of compute. The research estimated 10–12.
- Test set as Turns, KV cache kept: system prompt warm-up 10.8 s. First token 1.0–1.8 s, **first sentence 1.5–3.7 s**, ~4.5 tok/s.
- Quality: **about 2/10 pass**. Most replies start "You can say: …" and three end "What is your name?", against the rules. It echoed the Russian sentence back untranslated, gave no Russian on "in Russian?", didn't decline politics, and made only one recast ("bought"), phrased as a correction.

**Qwen3.5-0.8B Q4_K_M** (0.5 GB, tried as the smaller fallback at the user's choice):
- llama-bench on the big cores: prompt 105 tok/s, **generation 9.3 tok/s**. Warm-up 6.3 s. First token 0.5–1.0 s, **first sentence 0.6–2.2 s**.
- Quality: **about 3/10 pass**. No recast at all. "in Russian?" got just "Понимаю.". Nonsense input and a Russian sentence got "You can say: you go to work now.". **"wait" made it say goodbye**, which would end the Talk.

**End of speech → first answer audio** (estimated; STT and TTS were not run on the car): 1.5 s silence window + ~0.3–0.5 s Moonshine (research) + first sentence + ~0.2–0.3 s Piper (research). That gives **~3.5–6 s (typically ~5 s) for 2B**, at the edge of the 3–5 s bar or past it, and **~3–4.5 s for 0.8B**, inside the bar.

**Verdict for the offline-or-cloud decision**: on this car, no offline model tried is both fast enough and good enough. 2B is borderline on speed and fails the talk partner rules. 0.8B is fast but fails them worse.

**Not measured**: Moonshine/Piper timings and WER on the owner's voice (sherpa-onnx's Android release has no command-line binaries, so a build is needed). The runner-up Gemma 4 E2B (the user chose the smaller model instead; expected even slower here). Prompt tuning. A 20-minute thermal run. Mic bleed during TTS.
