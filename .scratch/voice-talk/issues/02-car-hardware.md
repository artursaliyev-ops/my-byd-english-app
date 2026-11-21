# Read the car's hardware for an on-device model
Type: task
Status: resolved
Blocked by:

## Question

HITL (needs the car on the phone's hotspot) / AFK over ADB. Confirm on the owner's car, read-only: SoC (`ro.soc.model`, `ro.board.platform`, `/proc/cpuinfo`), total and free RAM (`/proc/meminfo`) with BYD's apps running, free internal and SD storage, GPU/NPU (Adreno model, whether `libQnnHtp`/`libcdsprpc` and OpenCL are present for apps), installed `TextToSpeech` engines and whether any has an offline English voice. Record the facts in the answer.

## Answer

Read 2026-11-21 over ADB from the laptop (read-only), car parked and on, on the phone's hotspot. **ADB is now at `192.168.215.182:5555`** (the hotspot subnet changed from `172.20.10.x`; scan port 5555 if it moves again). The laptop had no `adb`; it was installed with `brew install android-platform-tools`.

**SoC: Qualcomm QCM6490**, not a phone 778G/782G but its IoT sibling (same "yupik" die): `ro.soc.model=SM7325`, `ro.board.platform=lahaina`, `/sys/devices/soc0` `machine=YUPIK-IOT`, `soc_id=497`. CPU: 1× Cortex-A78 @ 2.71 GHz + 3× A78 @ 2.40 GHz + 4× A55 @ 1.96 GHz (`CPU part 0xd41` ×4, `0xd05` ×4). arm64-v8a. Android 12, SDK 32, build `SKQ1.230128.001`. For CPU inference this is the 782G-class figure the research assumed (prime core 2.7 GHz).

**RAM: 11.0 GiB total** (`MemTotal 11491980 kB`), **~4.4 GiB available** with BYD's apps running (`MemAvailable ≈ 4.5–4.6 GB` on two reads); plus 4 GiB zram swap, ~0.7 GiB of it used. lmkd runs with minfree levels. Biggest residents (PSS): BYD voice engine `com.byd.autovoice.engine` 559 MB, `com.byd.sr` (cluster) 522 MB, system 350 MB, wallpaper/home 264 MB, launcher map 237 MB, dashcam `com.byd.cdr` 198 MB. So a ~1.7 GB stack fits in what is free, but with ~2.7 GB headroom, not 10 GB; the RAM/kill check in the car measurement still matters.

**Storage:** internal `/data` 193 GB, **173 GB free**. SD card mounted at `/storage/677A-84B6` (`/sdcard` links to it), 29 GB, **15 GB free**. Room for a ~3 GB model file either place.

**GPU:** Adreno 643 v1, max 812 MHz, OpenGL ES 3.2, Vulkan (`ro.hardware.vulkan=adreno`). `libOpenCL.so` is in `/vendor/lib64` **and listed in `/vendor/etc/public.libraries.txt`**, so an app can load OpenCL.

**NPU (Hexagon HTP v68):** `libQnnHtp*` is **absent** (0 hits). Qualcomm **SNPE** is present and public to apps (`libSNPE.so`, `libSnpeHtpV68Stub/Skel`, `libSnpeHtpPrepare`, plus BYD's `libBydSNPENeuroNetwork.so`); `libcdsprpc.so`/`libadsprpc.so` are public too. The only FastRPC node is `/dev/adsprpc-smd` (`crw-rw-r-- system system`, label `vendor_qdsp_device`), SELinux Enforcing. Whether an untrusted app may actually open it is **unconfirmed**; an NPU path would need its own probe and an SNPE/QNN build matched to v68. Treat the NPU as not available for the plan; CPU (and maybe OpenCL GPU) is the realistic path, as the research assumed.

**Text-to-speech:** one engine, BYD's `com.byd.autovoice` (service `MyTextToSpeechService`; resources in `com.byd.autovoice.tts`, iFlytek xTTS `BYD_XTTS_ALL_cst1018`). Its voices are Chinese (doudou, lingfeichen, lingfeizhe, lingxiaoqi, lingxiaoyao, lingxiaoyun, lingyouyou, nannan, 24 kHz). **No English voice**, and no default TTS is set (`tts_default_synth` null). Google TTS is not installed. So Talk must **bundle its own English voice** (Piper, per the research).

**Temperatures at rest** (parked, ~20:45): CPU 50–53 °C, GPU ~47 °C, NSP ~44 °C. Baseline for the heat check in the car measurement.
