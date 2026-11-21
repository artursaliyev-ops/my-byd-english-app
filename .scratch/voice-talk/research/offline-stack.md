# Offline stack for "Talk" mode on the DiLink head unit

Researched 2026-11-21. Question: which speech-to-text, language model and text-to-speech stack can hold a spoken English small-talk conversation **fully offline** on the head unit, with **end of the learner's speech → first sound of the answer in 3–5 s**?

**Target, as given.** Android 12 (SDK 32), arm64, not Android Automotive, China firmware, probably no Google Play services. SoC assumed (not confirmed) to be Snapdragon 778G/782G class, ~12 GB RAM. Mic gives 16 kHz mono PCM. Install budget ~3 GB, copied once by SD card or ADB.

**How much to trust this.** Nobody publishes benchmarks for a BYD head unit. The numbers below are for phones, a Raspberry Pi 4 or an Apple M3, and they are scaled to a 778G by reasoning. Tags:

- **[Source]**: a project repo, model card, paper or official doc says it
- **[Measured, other device]**: a published benchmark on different hardware
- **[Inference]**: worked out from the above, not stated anywhere
- **[Unknown]**: settle it on the car

---

## 0. The one number that drives everything: memory bandwidth

The only public llama.cpp benchmark on a **Snapdragon 778G** (OPPO Reno7 5G, 8 GB, Android 13) ran Qwen2.5-3B-Instruct Q4_K_M (1.95 GiB) on the CPU ([vovaxa55/snapdragon778g-llama-cpp-opencl-bench](https://github.com/vovaxa55/snapdragon778g-llama-cpp-opencl-bench)). **[Measured, other device]**

| 778G, CPU, 4 threads | Prompt (pp256) | Generation (tg64) |
|---|---|---|
| Cool SoC (36–41 °C) | 40.5 tok/s | 7.5 tok/s |
| Warm / throttled (45–47 °C) | 28.9 tok/s | 7.4 tok/s |

What the same repo found:

- **4 threads is the fastest setting.** 6 or 8 threads get slower.
- **Offloading to the Adreno 642L GPU over OpenCL is slower at every layer count**: −40% prompt and −70% generation with all layers on the GPU. The 778G needed driver patches for the GPU to give correct output at all. Vulkan shaders all failed.
- **Generation is memory-bound** at about **15.6 GB/s** effective. The author says CPU decode is "already at ~91 % of the memory roofline".

**What follows from this [Inference]:** decode speed on this chip is roughly **15 GB/s ÷ bytes of weights read per token**. At 4-bit that comes to:

| Model at ~4-bit | Weights read per token | Expected decode on 778G CPU |
|---|---|---|
| ~0.5 GB (Gemma 3 1B int4, Qwen3.5-0.8B) | ~0.5 GB | ~20–28 tok/s |
| ~1.3 GB (Qwen3.5-2B Q4_K_M) | ~1.2 GB | ~10–12 tok/s |
| ~2 GB (3B-class Q4_K_M) | ~2 GB | ~7.5 tok/s (measured) |
| Gemma 4 E2B (2.3B effective) | ~1.5 GB of active weights | ~8–10 tok/s |
| 3.8–4B class (Phi-4-mini, Qwen3.5-4B, Gemma 4 E4B) | 2.3–2.6 GB | ~5–6 tok/s |

A spoken first sentence is about 10–15 tokens, so at 10 tok/s it takes **1.0–1.5 s** to generate. That fits the budget. At 5–6 tok/s, a 4B model's first sentence alone takes 2–3 s, so 4B-class models are out.

Prompt speed matters less than it looks, as long as the app keeps the KV cache between turns. Each turn then only prefills the new user utterance plus the chat-template tokens (about 25–50 tokens). That costs **0.3–0.8 s** for a 1–2B model at ~60–120 tok/s. **[Inference]** Without cache reuse, re-prefilling a 400-token history at 40 tok/s would take 10 s and miss the budget.

---

## 1. Speech to text

### 1.1 Candidates

| Engine / model | Size | English accuracy (WER, lower is better) | Speed signal | License | Android |
|---|---|---|---|---|---|
| **Moonshine v2 Small** (streaming, 123M) | ~120–250 MB depending on quant [Inference] | Open-ASR avg **7.84%**; LibriSpeech clean 2.49 / other 6.78; AMI 12.54 ([paper Table 3](https://arxiv.org/html/2602.12241v1), [model card](https://huggingface.co/moonshine-ai/moonshine-streaming-small)) | End-of-speech → transcript **148 ms on Apple M3 CPU** vs Whisper Small 1940 ms ([paper Table 2](https://arxiv.org/html/2602.12241v1)) | MIT ([repo](https://github.com/moonshine-ai/moonshine)) | Own library supports Android; sherpa-onnx has v2 C++ and Kotlin/Java APIs ([sherpa_onnx changelog 1.12.39](https://pub.dev/packages/sherpa_onnx/changelog)) |
| Moonshine v2 Tiny (34M) | small | avg 12.01%; Libri other 12.09 | 50 ms on M3 | MIT | same |
| Moonshine v2 Medium (245M) | larger | avg 6.65% | 258 ms on M3 | MIT | same |
| whisper.cpp tiny / base / small | 75 / 142 / 466 MiB on disk; ~273 / 388 / 852 MB RAM ([repo](https://github.com/ggml-org/whisper.cpp)) | (Whisper baselines not tabulated in the v2 paper) | M3 latency: tiny 289 ms, base 553 ms, small 1940 ms ([Moonshine v2 Table 2](https://arxiv.org/html/2602.12241v1)) | MIT | `examples/whisper.android`; Silero VAD built in |
| sherpa-onnx streaming Zipformer en (2023-06-26) | int8 encoder 68 MB + ~1.5 MB ([docs](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html)) | Trained on LibriSpeech only; no WER on that page | RTF 0.062 on one clip | Apache-2.0 | Official streaming APK |
| sherpa-onnx streaming Zipformer en (2023-06-21) | int8 encoder 179 MB | LibriSpeech + GigaSpeech | RTF 0.062 | Apache-2.0 | same |
| Vosk small-en-us-0.15 | 40 MB | Libri clean 9.85, TED-LIUM 10.38 ([models page](https://alphacephei.com/vosk/models)) | Light, for Android/RPi | Apache-2.0 | Yes |
| Vosk en-us-0.22-lgraph | 128 MB | Libri 7.82, TED 8.20 | | Apache-2.0 | Yes |
| Android `SpeechRecognizer` | none | depends on installed service | | | see 1.3 |

**[Source]** for every cell except the Moonshine size.

### 1.2 Latency on the car [Inference]

- An Apple M3 performance core is roughly 2–3× a Cortex-A78 at 2.4 GHz. On the 778G, expect **Moonshine v2 Small about 0.3–0.5 s** from end of speech to final text. Expect **Whisper base about 1.2–1.8 s**, and **Whisper small about 4–6 s**, which would use the whole budget on its own.
- Qualcomm AI Hub runs the Whisper encoder on the NPU/GPU in 0.13–0.16 s (base.en) and 0.46–0.54 s (small.en) on 8 Gen 3 / 8 Elite ([Whisper-Base-En](https://huggingface.co/qualcomm/Whisper-Base-En), [AI Hub job](https://aihub.qualcomm.com/jobs/jgzjv1n6p)). **[Measured, other device]** That is a different runtime on a flagship chip, so it does not transfer to the 778G.
- Moonshine v2's sliding-window encoder works **while the learner is still talking**, with an 80 ms lookahead (model card). So encoding a 3–8 s utterance costs almost nothing after the stop. Whisper has to encode the full padded 30 s window after the stop. Moonshine v1 reported that Whisper tiny.en has a latency floor of about 500 ms on ARM ([arXiv 2410.15608](https://arxiv.org/abs/2410.15608)), via search snippet. **[Source]**

### 1.3 Android `SpeechRecognizer` without Google services

- `createSpeechRecognizer` binds to whatever `RecognitionService` the system has. `createOnDeviceSpeechRecognizer` (API 31) "throws `UnsupportedOperationException` iff `isOnDeviceRecognitionAvailable` is false". Apps targeting 30+ must declare a `<queries>` entry for `android.speech.RecognitionService` ([SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)). The doc itself warns the implementation "is likely to stream audio to remote servers". **[Source]**
- On China firmware without GMS, any recognition service would be BYD's own or a supplier's (BYD's assistant work is reported with Volcano Engine/Doubao ([EqualOcean, Dec 2025](https://equalocean.com/news/2025122621698)) and Cerence for global markets). Nothing says such a service is exported, works offline, or handles English. **[Unknown]** Worth one `isRecognitionAvailable` / `isOnDeviceRecognitionAvailable` call on the car, but don't plan on it.

### 1.4 Accented English (Russian/Uzbek L1)

- No public benchmark covers Russian- or Uzbek-accented English for these models. **[Unknown]**
- EdAcc (40 h of conversations, 51 L1s) shows how hard the task is: the best model in that paper (trained on 680k h, i.e. Whisper-large class) averaged **19.7% WER**, against 2.7% on clean US read speech ([EdAcc, arXiv 2303.18110](https://arxiv.org/abs/2303.18110)). **[Source]** Small models will do worse than that.
- An L2-ARCTIC study found Whisper among the most accurate systems on L2 read speech; Russian is not among its L1 groups ([arXiv 2503.06924](https://export.arxiv.org/pdf/2503.06924)), via search snippet. **[Source]**
- For this use, small-talk errors are cheap. The model can ask "Sorry, did you say…?", and the learner hears the recast anyway. But WER on the owner's own voice is the **first thing to measure** (§7).

### 1.5 STT pick

**Moonshine v2 Small, English, through sherpa-onnx, with Silero VAD.** It has the best published accuracy-per-latency in the list, MIT weights, and streaming encoding. Running through sherpa-onnx means STT, VAD and TTS share one Apache-2.0 native library with a Kotlin API and ready Android APKs ([sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)).

Caveat: sherpa-onnx's Moonshine page lists v2 English packages as `moonshine-tiny-en-quantized-2026-02-27` and `moonshine-base-en-quantized-2026-02-27`, not "small" ([sherpa-onnx Moonshine](https://k2-fsa.github.io/sherpa/onnx/moonshine/index.html)). Check which paper size "base" maps to. If it isn't the 123M model, use the Moonshine library directly or export Small yourself. **[Unknown]**

**Fallback:** whisper.cpp **base.en**, quantized (Q5_0/Q8_0). Expect about 1–1.5 s more latency, but Whisper has a long record on accented speech.

---

## 2. Language model

### 2.1 Candidates (instruct versions)

| Model | Params | ~4-bit file | License | Notes |
|---|---|---|---|---|
| **Qwen3.5-2B** (Mar 2026) | 2B, 24 layers, hybrid Gated DeltaNet + attention | ~1.3 GB Q4_K_M ([unsloth GGUF](https://huggingface.co/unsloth/Qwen3.5-2B-GGUF)) | Apache-2.0 ([card](https://huggingface.co/Qwen/Qwen3.5-2B)) | **Non-thinking by default**; 262K context; has a vision encoder (unused) |
| Qwen3.5-0.8B | 0.8B | ~0.5–0.6 GB [Inference] | Apache-2.0 | litertlm build exists ([litert-community](https://huggingface.co/litert-community/Qwen3.5-0.8B)) |
| Qwen3.5-4B | 4B | ~2.5 GB | Apache-2.0 | Too slow here (§0) |
| **Gemma 4 E2B** | 2.3B effective, 5.1B with per-layer embeddings | **2.58 GB .litertlm** ([LiteRT-LM Gemma 4](https://developers.google.com/edge/litert-lm/models/gemma-4)); GGUF Q4_K_M ~3.1 GB ([unsloth](https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/blob/main/gemma-4-E2B-it-Q4_K_M.gguf)) | Apache-2.0 | MMLU-Pro 60.0%, MMMLU 67.4%; takes **audio input** (≤30 s, ASR) ([card](https://huggingface.co/google/gemma-4-E2B-it)) |
| Gemma 4 E4B | 4.5B effective | 3.65 GB litertlm | Apache-2.0 | Over the install budget and too slow |
| Gemma 3 1B | 1B | **529 MB** int4 QAT .litertlm ([card](https://huggingface.co/litert-community/Gemma3-1B-IT)) | Gemma Terms (gated download) | Very fast; weaker at following teacher-style rules [Inference] |
| Gemma 3n E2B | ~2B effective | | Gemma Terms | Replaced by Gemma 4 E2B |
| Llama 3.2 1B / 3B | 1.24B / 3.2B | 0.73 GB Q4_0 / ~2 GB | Llama 3.2 Community License | 3B too slow; 1B is the same tier as Gemma 3 1B |
| Phi-4-mini | 3.8B | ~2.3 GB | MIT | LiteRT-LM S24 Ultra CPU: 67 prefill / 7 decode tok/s ([LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview)), so about 2 tok/s on a 778G. Out. |
| SmolLM2/3 | 135M–3B | | Apache-2.0 | Not checked in detail; no edge over Qwen3.5 at the same size [Inference] |

### 2.2 Runtimes on a 778G-class chip

| Runtime | Path on this chip | Evidence |
|---|---|---|
| **llama.cpp** (MIT) | **CPU, 4 threads**: the measured-fastest path on 778G. OpenCL on Adreno 642L is slower and needed patches. | [778G bench](https://github.com/vovaxa55/snapdragon778g-llama-cpp-opencl-bench); Android example in repo |
| llama.cpp Hexagon backend | Ships HTP libraries for **v73, v75, v79, v81** only ([docs](https://github.com/ggml-org/llama.cpp/tree/master/docs/backend/snapdragon)). The 778G's Hexagon 770 is v68-class [Inference], so **not usable**. | Llama 3.2 1B Q4_0 on v79: pp128 169 / tg64 52 tok/s |
| **LiteRT-LM** (Apache-2.0; successor to MediaPipe LLM Inference) | Kotlin API "Stable"; `.litertlm` files; CPU (XNNPACK), GPU (OpenCL; declare `libOpenCL.so` and `libvndksupport.so` as `<uses-native-library required=false>`), NPU (no chip list) ([Android guide](https://developers.google.com/edge/litert-lm/android), [overview](https://developers.google.com/edge/litert-lm/overview)). Docs do not mention Play services. | S24 Ultra CPU: Gemma 3 1B 177/33, Gemma 3n E2B 111/16 tok/s. S26 Ultra CPU: Gemma 4 E2B 557/47, GPU 3808/52 |
| MediaPipe LLM Inference | Older API, same models; superseded by LiteRT-LM | Gemma 3 1B int4 QAT on S24 Ultra CPU: 322 prefill / 47 decode, TTFT 3.1 s ([card](https://huggingface.co/litert-community/Gemma3-1B-IT)) |
| Qualcomm Genie / QNN / GenieX | QAIRT 2.33 notes limit Android Genie to 8 Gen 2 / 8 Gen 3 ([release notes](https://docs.qualcomm.com/doc/KBA-250421151446/KBA-250421151446_REV_1_QAIRT_2_33_0_Partner_Release_Notes.pdf)); GenieX's FAQ names 8 Elite ([GenieX](https://geniex.aihub.qualcomm.com/en/get-started/platforms)). **No 7-series support found.** | |
| MLC LLM, ExecuTorch | Both run on Android (OpenCL / XNNPACK). Not benchmarked here; on this chip they hit the same bandwidth wall, and Adreno OpenCL already lost on the 778G. | [Inference] |

**Runtime conclusion:** on a 778G the NPU paths are closed and the GPU is unproven or worse, so this is a **CPU** problem. llama.cpp has the only real 778G measurement, takes 4-bit GGUF (smallest files), and lets the app keep and reuse the KV cache (session state) between turns. LiteRT-LM is the official Google path with a Kotlin API. Its GPU backend *might* speed up prefill on Adreno 642L, but nobody has shown that.

### 2.3 Simple English and recasting

No benchmark measures "keeps to A2–B1 English and recasts the learner's mistake". **[Unknown]** What can be said:

- Instruction-following grows with size. At 1B the model tends to drop a rule over a long chat, e.g. "one short reply, then one question, recast errors without saying 'mistake'". 2B-class models hold such rules better. Gemma 4 E2B's MMLU-Pro 60.0% and Tau2 24.5% are far above what the 1B class reports. **[Inference]** from the [Gemma 4 card](https://huggingface.co/google/gemma-4-E2B-it).
- Reliable recasts need a fixed format the app can check. For example, have the model reply as `RECAST: … / REPLY: … / QUESTION: …`, or use llama.cpp's GBNF grammar to force that shape, so the app speaks the parts in order and can drop a bad recast. **[Inference]**
- Keep replies short (`max_tokens` ~60, stop at the second sentence). That also helps latency.
- Qwen3.5 starts in non-thinking mode, so it doesn't waste tokens on reasoning ([card](https://huggingface.co/Qwen/Qwen3.5-2B)). **[Source]**

### 2.4 LLM pick

**Qwen3.5-2B-Instruct, Q4_K_M (~1.3 GB), on llama.cpp CPU with 4 threads and KV cache kept across turns.** Expect ~10–12 tok/s decode, ~60–100 tok/s prefill, ~2 GB RAM with a 4K context. **[Inference]**

Two risks:

1. The `qwen35` hybrid architecture is new. Check that the pinned llama.cpp build runs it on ARM CPU, and how its recurrent state saves and restores between turns. **[Unknown]**
2. Teaching quality has to be judged by ear (§7).

**Swap-ins without changing the runtime:** Gemma 3 1B or Qwen3.5-0.8B if the car is slower than expected. Llama 3.2 1B is also fine if the Qwen3.5 architecture causes trouble.

---

## 3. Text to speech

### 3.1 Is a usable English engine preinstalled on China DiLink?

**Probably not one you can count on.** **[Inference]**

- AOSP removed its only built-in engine, SVOX Pico ("external/svox is going away", "Remove PicoTTS" in `device/generic/car`) ([AOSP commit](https://android.googlesource.com/platform/build.git/+/ff806ece8da92006a640a62fb1adaaf1cc154723%5E1..ff806ece8da92006a640a62fb1adaaf1cc154723/)). Stock Android 12 without GMS therefore has **no** `TextToSpeech` engine unless the OEM adds one. **[Source]**
- BYD's own voice prompts are surely produced by a supplier engine (Volcano Engine / Cerence / iFlytek-class). There's no evidence it is registered as an Android `TextToSpeech` service, exported to third-party apps, or has an English voice. **[Unknown]**
- Check with `adb shell settings get secure tts_default_synth` and `adb shell cmd package query-services -a android.intent.action.TTS_SERVICE` (§7). If an English engine shows up, it's a free fallback. Don't design around it.

### 3.2 Bundled engines

| Model (via sherpa-onnx) | Size | Speed signal | Naturalness | License |
|---|---|---|---|---|
| **Piper VITS medium** (e.g. `en_US-libritts_r-medium`, `lessac-medium`) | ~60–75 MB per voice [Inference] | RPi 4: RTF **0.36** at 4 threads ([sherpa-onnx VITS](https://csukuangfj.github.io/sherpa/onnx/tts/pretrained_models/vits.html)), via search snippet | Clear, a bit flat | Voice licenses differ per voice (check each MODEL_CARD); Piper's own engine is now GPL-3.0 ([piper1-gpl](https://github.com/OHF-Voice/piper1-gpl)), but sherpa-onnx runs the ONNX voices itself; espeak-ng phonemizer data is GPL [Inference] |
| **Kokoro-82M** (`kokoro-en-v0_19` 330 MB fp32; `multi-lang-v1_1` has int8) | 310–330 MB fp32 + voices ([sherpa-onnx Kokoro](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html)) | RPi 4: RTF **2.77** at 4 threads (slower than real time) | Most natural of the small open models [Inference]; 54 voices in v1.0 | Apache-2.0 ([card](https://huggingface.co/hexgrad/Kokoro-82M)) |
| Matcha (`matcha-icefall-en_US-ljspeech`) + Vocos | 71 MB + 51 MB | RPi 4: RTF 0.41 at 4 threads ([sherpa-onnx Matcha](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/matcha.html)) | Good; one female voice | See model LICENSE |
| KittenTTS nano / 2-mini | 25 MB int8 / 83 MB int8 | "Faster-than-realtime … on a single smartphone CPU thread" (pico) ([repo](https://github.com/KittenML/KittenTTS)) | Newer, less proven | Code Apache-2.0; KittenTTS 2 weights under Stellon Labs Community License |

**[Source]** except where tagged.

### 3.3 Time to first audio [Inference]

A Cortex-A78 at 2.4 GHz is about 2.5–3.5× a Pi 4's Cortex-A72 at 1.5 GHz on this kind of load. Synthesising the first clause (~1.5 s of audio):

- **Piper medium:** RTF ~0.1–0.15, so the first audio arrives in **~0.2–0.3 s**.
- **Matcha:** RTF ~0.12–0.16, also **~0.2–0.3 s**.
- **Kokoro fp32:** RTF ~0.8–1.1, so **~1.2–1.7 s** for the first clause, and it barely keeps up after that. With int8 it might come down to ~0.5–0.8 s. It would also compete with the LLM for the same 4 big cores while both run.

Synthesise **sentence by sentence as tokens arrive** (split on `.`, `?`, `!`, or a comma after ~6 words) and queue the PCM to `AudioTrack`.

### 3.4 TTS pick

**Piper medium VITS through sherpa-onnx** for first release. It is fast enough to stay out of the latency budget and leaves the CPU to the LLM. **Kokoro (int8)** is the quality upgrade, used only if the car measurements show the first clause under ~0.8 s while the LLM is still generating.

---

## 4. Recommended stack

| Stage | Choice | Files |
|---|---|---|
| VAD / endpointing | Silero VAD (sherpa-onnx); end of turn after ~500–600 ms of silence | ~2 MB |
| STT | **Moonshine v2 Small, English, streaming** (sherpa-onnx, Kotlin API) | ~0.15–0.25 GB |
| LLM | **Qwen3.5-2B-Instruct Q4_K_M** on **llama.cpp**, CPU, 4 threads, KV cache kept across turns, short replies, fixed recast/reply/question format | ~1.3 GB |
| TTS | **Piper medium English voice** via sherpa-onnx, synthesised sentence by sentence | ~0.07 GB |
| Native libs | sherpa-onnx + llama.cpp arm64 `.so` in the APK | ~0.05 GB |
| **Total install** | | **≈ 1.6–1.7 GB**, leaving room for a Kokoro voice (+0.35 GB) or a second LLM |

Runtime RAM ≈ 2–3 GB, well within 12 GB. **[Inference]**

Run it **half-duplex**: close or ignore the mic while the answer plays. Speaker bleed and the lack of a known echo canceller on DiLink are covered in [voice-practice/research/dilink-mic-access.md](../../voice-practice/research/dilink-mic-access.md) §2. Barge-in can come later, if AEC turns out to work.

### Runner-up

**Same sherpa-onnx STT/VAD/TTS, with Gemma 4 E2B (2.58 GB `.litertlm`) on LiteRT-LM.**

- **For:** Google's official Kotlin runtime; Apache-2.0; the strongest small model checked (MMLU-Pro 60%); may use the Adreno GPU for prefill.
- **Against:** it nearly fills the 3 GB budget (≈2.9 GB total). Expected CPU decode of only ~8–10 tok/s. And the "E2B" per-layer-embedding design means its memory and speed on a 778G are less predictable than a plain 2B.
- Gemma 4 E2B also accepts audio directly. In a later experiment it could take over STT and fold transcription into one model, but its 30 s audio limit and unknown prefill cost on this chip make that a research spike, not a plan.

If both 2B options turn out too slow on the car, drop to **Gemma 3 1B int4 (529 MB) on LiteRT-LM** or **Qwen3.5-0.8B on llama.cpp**. Both should reach ~20–25 tok/s.

---

## 5. Latency estimate: end of speech → first audio

Recommended stack on a 778G-class CPU. **[Inference]** throughout; every line needs a car measurement.

| Step | Estimate |
|---|---|
| VAD silence window that decides "learner finished" | 0.5–0.6 s |
| Moonshine final pass after endpoint (streaming, mostly done during speech) | 0.3–0.5 s |
| LLM prefill of new turn (~30–50 tokens at 60–100 tok/s, cache reused) | 0.3–0.8 s |
| LLM generates first clause (~10–14 tokens at 10–12 tok/s) | 0.9–1.4 s |
| Piper synthesises first clause | 0.2–0.3 s |
| `AudioTrack` start + DiLink output path | 0.05–0.2 s |
| **Total** | **≈ 2.3–3.8 s** (typical ~3 s) |

That fits the 3–5 s bar, with ~1 s spare for thermal throttling. The prompt-speed drop when hot (−29% measured on 778G) costs ~0.2 s; decode barely changes (−2%).

**Runner-up (Gemma 4 E2B on LiteRT-LM CPU):** about **3–4.5 s**.
**Whisper base.en instead of Moonshine:** add ~1–1.5 s.
**Kokoro fp32 instead of Piper:** add ~1–1.5 s.

One-time costs outside the per-turn budget: loading models (~1.5 GB of mmap) and prefilling the system prompt (~300 tokens, ~3–5 s). Do both when Talk mode opens, while a canned greeting plays.

---

## 6. Install size summary

| Stack | Approx. total |
|---|---|
| Recommended (Moonshine v2 Small + Qwen3.5-2B Q4_K_M + Piper medium) | **≈ 1.6–1.7 GB** |
| + Kokoro int8/fp32 as optional voice | ≈ 1.9–2.1 GB |
| Runner-up (Moonshine + Gemma 4 E2B litertlm + Piper) | ≈ 2.9 GB |
| Speed fallback (Moonshine + Gemma 3 1B int4 + Piper) | ≈ 0.85 GB |

---

## 7. Measure on the car

1. **SoC and memory.** `adb shell getprop ro.soc.model`, `ro.board.platform`, `cat /proc/cpuinfo`, `cat /proc/meminfo`. This confirms or replaces the 778G/12 GB assumption that everything above is scaled from.
2. **LLM throughput.** `llama-bench -t 4 -p 64,256 -n 32` for Qwen3.5-2B Q4_K_M, Gemma 3 1B and Qwen3.5-0.8B: cold, then after 10 minutes of looping (thermal). Also confirm the `qwen35` architecture runs and that turn-to-turn KV/state save and restore work.
3. **LiteRT-LM.** Gemma 4 E2B and Gemma 3 1B on CPU vs GPU (`Backend.GPU()`): does OpenCL load on the head unit, and is prefill faster than CPU?
4. **STT accuracy on the owner's voice.** Record ~30 real utterances in the car (engine noise, fan, road; windows up and down). Hand-transcribe them and score WER for Moonshine v2 Small/Base, Whisper base.en and small.en.
5. **STT latency.** End-of-speech → final transcript, for each of those models.
6. **TTS.** Time to first PCM for a 1.5 s clause with Piper medium and Kokoro, measured **while the LLM is generating** on the same cores. Listen for naturalness through the car speakers.
7. **Preinstalled engines.** `adb shell settings get secure tts_default_synth`; `adb shell cmd package query-services -a android.intent.action.TTS_SERVICE`; `cmd package query-services -a android.speech.RecognitionService`; and from the app, `TextToSpeech.isLanguageAvailable(Locale.US)` and `SpeechRecognizer.isOnDeviceRecognitionAvailable`.
8. **End-to-end.** End of speech → first audible sample over 20 turns. Log each stage's timestamp so a slow stage shows which line of §5 is off.
9. **Mic and audio paths.** That `AudioRecord` at 16 kHz gives real (not silenced) audio while `AudioTrack` plays in the same app. How much TTS bleeds into the mic (to decide half-duplex vs barge-in). Whether opening the mic changes volume or routing. See the [dilink-mic-access](../../voice-practice/research/dilink-mic-access.md) checks.
10. **Thermal and power.** CPU temperature and clock (`/sys/class/thermal/*`, `/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq`) over a 20-minute Talk session. Check that DiLink does not kill or throttle a background-heavy foreground app.
11. **Storage.** Whether app-specific external storage on the SD card or `/sdcard/Android/data/<pkg>` takes a 1.3–2.6 GB file, and how fast mmap loads from it compared with internal storage.
12. **Teaching quality.** 20 scripted turns with planted learner errors (tense, articles, word order). Count how many replies recast correctly, stay at simple-English level, and end with a question. Compare Qwen3.5-2B, Gemma 4 E2B and a 1B model.
