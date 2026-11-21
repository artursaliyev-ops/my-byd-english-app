# Find an offline speech, model and voice stack for the head unit
Type: research
Status: resolved
Blocked by:

## Question

AFK. What is the best offline stack for a Talk on an Android 12 (SDK 32) arm64 head unit, assuming a Snapdragon 778G-class SoC with ~12 GB RAM (unconfirmed, see the car hardware ticket)? Compare, from primary sources (project repos, model cards, published benchmarks):

- **Speech to text**: e.g. whisper.cpp (tiny/base/small, English-only), sherpa-onnx / streaming Zipformer, Vosk, Android's on-device `SpeechRecognizer` (does Android 12 without Google services have one?). Accuracy on accented English, latency for a 3–8 s utterance, size.
- **Model**: small instruct LLMs (~0.5–4B: Gemma 3 1B/4B, Qwen2.5/Qwen3 1.5–3B, Llama 3.2 1B/3B, Phi-3.5/4-mini, SmolLM) and runtimes (llama.cpp, MediaPipe LLM Inference, MLC LLM, ExecuTorch, Qualcomm QNN/Genie). Tokens/s for prompt and generation on Snapdragon 7-series/8-series, RAM, file size, license, and how well each keeps to simple English and recasting.
- **Text to speech**: Android `TextToSpeech` engines that work offline (is one likely preinstalled on China DiLink firmware, with English?), Piper / sherpa-onnx VITS / Kokoro. Naturalness, latency to first audio, size.

End with one recommended stack plus a runner-up, an estimated time from end of speech to first answer audio (target 3–5 s), total install size (budget ~3 GB), and what must be measured on the car.

## Answer

Resolved 2026-11-21 (AFK research). Findings on branch `research/offline-talk-stack` (pushed, not merged), file `.scratch/voice-talk/research/offline-stack.md`. All numbers are scaled from other devices to a Snapdragon 778G; nothing has run on the car.

- On the 778G the CPU (4 threads) is fastest; the GPU is slower in the one public test, and the NPU isn't supported by llama.cpp or Genie. Memory bandwidth (~15.6 GB/s) sets the speed: ~10–12 tok/s for a 4-bit 2B model, ~7.5 for 3B; 4B is too slow.
- **Recommended**: Moonshine v2 Small + Silero VAD via sherpa-onnx (speech to text) → Qwen3.5-2B-Instruct Q4 (~1.3 GB) on llama.cpp, CPU, with the KV cache kept between Turns → Piper medium English voice via sherpa-onnx, spoken sentence by sentence. ~2.3–3.8 s end of speech → first audio; ~1.6–1.7 GB installed.
- **Runner-up**: the same speech parts with Gemma 4 E2B on LiteRT-LM (~2.9 GB, ~3–4.5 s). Fallback models if both are slow: Gemma 3 1B or Qwen3.5-0.8B. Kokoro is the voice upgrade if fast enough (+~1 s).
- Don't count on a preinstalled English TTS or recognizer on China DiLink. Run half-duplex (mic off while the answer plays) until echo on the car is checked.
- Open: whether sherpa-onnx "base" is Moonshine Small; whether Qwen3.5 runs on llama.cpp CPU and keeps state; no data on Russian/Uzbek-accented English or on recast quality.
- The research's 12-item car-measurement list is the checklist for the car hardware and Turn-measuring tickets.
