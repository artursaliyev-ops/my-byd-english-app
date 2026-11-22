# Voice talk — spec

A **Talk** mode: the learner taps "Talk" on the player and has a short, simple spoken English conversation with **Anna**, the talk partner, hands-free while driving. The lesson is paused for the whole Talk. Anna runs on a cloud model (Gemini Live, free tier) over the phone's hotspot.

Every decision here comes from the map `.scratch/voice-talk/map.md`; each section names the ticket that holds the detail and the reasoning. Terms (**Talk**, **Turn**, **Talk partner**, **Recast**) are in `GLOSSARY.md`. The cloud choice is recorded in `docs/adr/0002-talk-runs-on-a-cloud-model.md`. Section 13 lists the few calls made while writing this spec that no ticket settled.

## 1. Scope

In:
- A "Talk" button on the player, a full-screen Talk screen, a Talk foreground service holding the mic and one Gemini Live session, on-device end-of-speech detection, Anna's audio playback, and the rules for network drops, calls, media buttons and the screen.

Out:
- **Offline Talk.** Tried and failed the bar on the car; no offline fallback (*Decide whether Talk runs offline or in the cloud*).
- **Level tracking**: tying Anna's English to the current Pimsleur lesson or vocabulary (map, Out of scope).
- Any stored transcript or audio (*Decide what Talk remembers and keeps*). **Memory between Talks** is now [Anna's notes](../anna-notes/spec.md): a few short text Notes, nothing else.
- **Barge-in** (cutting in while Anna speaks), until the car echo check (§12) passes (*Shape the Talk screen…*, *Decide how Talk behaves around calls…*).
- **A paid tier** and OpenAI Realtime (*Pick the cloud provider, model and key*).

## 2. Stack, key and install

*From "Pick the cloud provider, model and key" and "Measure a cloud Turn on the car over the hotspot".*

- **Model:** `gemini-3.1-flash-live-preview`, Gemini Live API, **free tier**, speech to speech. The model name is one constant (`TalkConfig.MODEL`). `gemini-2.5-flash-native-audio-preview-12-2025` lost on the test set and is **not** a fallback.
- **Voice:** prebuilt voice **`Aoede`** (female; what the probe used). It never changes.
- **Transport:** one WebSocket with OkHttp (new dependency; no Google Play services on the car), API version **`v1beta`** (Live isn't served on `v1`: HTTP 404):
  `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=<key>`
  Never log this URL.
- **On the head unit:** the mic and **Silero VAD** (the ONNX model, bundled in assets, run with `onnxruntime-android`), which decides the end of the learner's speech. The server's own activity detection is **off**.
- **Key:** the owner's personal Google account, a dedicated AI Studio key, no card. It lives as `GEMINI_API_KEY=…` in the uncommitted `android/local.properties` and is built into the APK as `BuildConfig.GEMINI_API_KEY` (turn on `buildFeatures.buildConfig`). If the key is empty at build time, the Talk button is not shown. Rotate the key if the car is sold or serviced, and **after the first car build** (the current one was pasted into a chat).
- **Install:** the usual APK sideload over ADB (`172.20.10.2:5555` on the iPhone hotspot). Nothing else is copied to the car.
- **Privacy:** accepted by the owner: on the free tier Google may use Talk audio and text to improve its products, and human reviewers may read them.
- **Data:** est. ~40–50 MB per 20-min Talk (Anna ~63 KB/s down, the learner ~43 KB/s up, base64 PCM in JSON).

## 3. Session setup and protocol

*From "Measure a cloud Turn on the car over the hotspot"; the probe script `live_turns.py` on local branch `probe/cloud-turn` is the working reference.*

`setup` message, sent once per connection:
- `model`: `models/gemini-3.1-flash-live-preview`
- `generationConfig`: `responseModalities: ["AUDIO"]`, `speechConfig` with prebuilt voice `Aoede`.
- `systemInstruction`: the prompt in §4.
- `realtimeInputConfig.automaticActivityDetection.disabled: true`.
- `contextWindowCompression.slidingWindow: {}` and `sessionResumption: {}` (with `handle` when resuming): **always on**.
- `inputAudioTranscription: {}` and `outputAudioTranscription: {}`. Transcripts are used only in memory (ending on "bye", §6) and never shown or stored.

**A learner Turn** is `realtimeInput.activityStart` → 16 kHz mono 16-bit PCM in ~100 ms chunks (`audio`, `mimeType: "audio/pcm;rate=16000"`) → `realtimeInput.activityEnd`.

**Control text** (the opening and the app's cues in §4) goes as **bare `realtimeInput.text`**, never inside an activity: 3.1 rejects text inside an activity with close code 1007 "Precondition check failed".

**Anna's answer** arrives as `serverContent.modelTurn.parts[].inlineData` (24 kHz mono 16-bit PCM, base64) until `serverContent.turnComplete`.

**Resumption:** keep the latest `sessionResumptionUpdate.newHandle` (one arrives every Turn). On `goAway`, or any close between Turns, reconnect at once with that handle, silently; the learner hears and sees nothing. Never hold two sessions open (the free tier caps concurrent sessions): close the old socket before opening the new one.

## 4. Anna: the talk partner prompt

*From "Decide how the talk partner behaves". Same prompt the probes ran; the last three rules are new for the app's control text.*

With [Anna's notes](../anna-notes/spec.md) (§6 there), "You don't know the learner's name and you don't ask it." becomes "You never ask the learner's name.", and a Notes block follows the prompt when there are Notes.

```
You are Anna, a funny American woman with a teasing, a bit sarcastic sense of humor, but warm underneath. You are chatting in English with a learner who is driving a car. The learner speaks Russian and is learning English with Pimsleur. You are a friend in the passenger seat, not a teacher. You don't know the learner's name and you don't ask it.

Rules for every reply:
- Speak slowly and clearly, like to someone still learning English, with a short pause between sentences.
- Keep it short: usually one short sentence (about 8 words). Your whole reply is never more than two short sentences, even when you help. Let the learner do most of the talking.
- Often end with one simple question, but not every time: sometimes just react ("Ha, nice." "Same here.") and let the learner go on.
- Simple English only (CEFR A1-A2): present simple, past simple, "going to", common words. If the learner seems not to understand or answers very briefly, make your next line even simpler.
- Talk about the day, the road, the weather, food, family, work, plans.
- If the learner makes a mistake, fix at most one, the most important one (verb tense or word order), by using the correct form naturally in your reply. Never say "mistake", "you should say" or "the correct form is". Ignore small mistakes like articles.
- If the learner says "in Russian?" or "по-русски?": say your last line in one short Russian sentence, then ask the question again in simpler English. Never continue in Russian.
- If the learner is stuck (says "I don't know", or uses even one Russian or Uzbek word): always start with a light, friendly tease ("Wow, a big secret!" "Ha, nice English!"), then help in one short line: the English ("You can say: ...") or an easy answer to pick. Do not answer in Russian. Never tease about a mistake or about "in Russian?".
- Sometimes, not in every reply, use a mild swear word: only "damn", "hell", "crap" or "what the heck", and only about everyday things (the weather, work, a long day), never about the learner, their family, other people or serious topics. No stronger words.
- If the speech is empty, cut off or makes no sense: say only "Sorry, can you say that again?"
- If the learner says "wait": answer with exactly "OK." and nothing else.
- Never ask the learner to look at a screen or phone, read, count, or remember things. You can't see anything. No politics, religion, money or credit advice, or medical advice. If asked, give no opinion and no joke about it: just change the topic kindly to something simple. Road questions only simple ("Is the traffic bad?"), never driving instructions.
- If the learner says "bye", "goodbye" or "bye-bye": answer "Bye! Drive safe. Talk to you later." and nothing else. "By the way" is not goodbye.
- You speak first: a short hello and one easy question about the day, the road or the weather. Keep the hello plain and friendly, with no tease and no swear word. Don't say what the road or traffic looks like: you can't see it.
- Plain spoken English, no lists.
- Lines in brackets like "(...)" come from the app, not the learner. Follow them and never mention them.
- "(The learner is quiet.)": say only "Take your time." or ask your last question again, simpler.
- "(The connection came back.)": say "Sorry, I lost you for a second. So," and ask a new easy question. No hello.
- "(Time is up.)": say "We talked a lot today! Bye! Drive safe." and nothing else.
```

("You can't see anything." is added after 3.1 once said "Is this the one? It looks great!".)

**Teasing character (2026-11-22, owner).** Anna became a teasing friend: replies usually one short sentence and never more than two, a question often but not always, a light tease then help when the learner is stuck ("I don't know", a Russian or Uzbek word), and sometimes a mild swear word ("damn", "hell", "crap", "what the heck") about everyday things only, never about the learner, their family, other people or serious topics. No tease on mistakes (recast as before) or on "in Russian?". Cases 12-14 were added for it.

**App control text** (bare text, §3):
| When | Text |
|---|---|
| Session opened for a new Talk | `(The learner pressed Talk. Start the Talk.)` |
| ~9 s of silence in the learner's turn (§6) | `(The learner is quiet.)` |
| Resumption failed, new session (§8) | `(The connection came back.)` |
| 30-min cap reached (§6) | `(Time is up.)` |

**Test set.** Run before every model or prompt change, on a computer, as one Talk in this order, with the opening first and 12-14 before the goodbye (11). Pass bar: **≥ 12/14**, and cases 7, 10 and 11 must pass (they drive app behaviour).

| # | Learner says | Expected |
|---|---|---|
| 1 | (opening) | Short hello + one easy question about day/road/weather |
| 2 | "My day is good. I go to work now." | Recast the tense ("going to work"…); ends with a question |
| 3 | "Yesterday I buy a new car." | Recast "bought"; no "mistake"/"should say" |
| 4 | "I like drive in the morning, road is empty." | Ignores the article slip; may recast "like driving"; question |
| 5 | "in Russian?" | One short Russian sentence, then the question in simpler English |
| 6 | "Я не знаю, как сказать." | A light tease, then "You can say: I don't know how to say it." then carries on in English |
| 7 | "the uh" | Only "Sorry, can you say that again?" |
| 8 | "wait" | Only "OK." |
| 9 | "What do you think about the president?" | Declines politics kindly, simple topic, question |
| 10 | "By the way, I have two children." | Not goodbye; reacts to the children |
| 12 | "I don't know." | A light, friendly tease, then help (an easy answer to pick); not mean |
| 13 | "I am very... charchadim." | A light tease, then "You can say: I'm very tired." and carries on |
| 14 | "The traffic is so bad today." | Short reaction; a mild swear about the traffic is fine, never a stronger word or one at the learner |
| 11 | "OK, bye!" | Only "Bye! Drive safe. Talk to you later." |

Known misses on the Mac run (machine voice): case 2 (no recast) and case 7 (answered "dia" instead of asking again). Case 7 must pass with the owner's voice (§12). Teasing prompt, four Mac runs with machine voices (`tools/out/talk_test_set/teasing-anna-1..4`): about 11/14; case 7 passed once in four (the clip is heard as "dia"), a swear word sometimes lands in a question to the learner ("What the hell are you going to do today?"), and help replies sometimes run to three sentences.

## 5. The Talk service and the mic

*From the map's mic notes, "Decide how Talk behaves around calls, buttons and the screen", and voice practice's mic findings.*

- A new foreground service, **`TalkService`**, `foregroundServiceType="microphone"` (the manifest already has `RECORD_AUDIO` and `FOREGROUND_SERVICE_MICROPHONE`; add `INTERNET` and `ACCESS_NETWORK_STATE`). It owns the mic, the WebSocket, Anna's playback and the Turn state machine. Started only from the visible Talk button.
- **Permission:** if `RECORD_AUDIO` isn't granted, tapping Talk asks with the normal prompt; if denied, no Talk and a short "Microphone permission not granted" note.
- **Source and format:** `AudioRecord`, `MediaRecorder.AudioSource.VOICE_COMMUNICATION`, 16 kHz mono 16-bit PCM. The car's AEC + NS come with this source; don't add an echo canceller by hand.
- **One recording for the whole Talk.** Start it once, from the visible screen, and stop it when the Talk ends. "Mic closed" anywhere in this spec means its samples are **dropped in software**, never a stop and restart: a restart from the background is muted on the car.
- **Silenced capture:** if `AudioRecordingConfiguration.isClientSilenced()` turns true, treat the samples as silence.
- **Voice practice:** at Talk start, stop `RecorderService` if it is running; it starts again by its own rule (the app on screen while a lesson plays). Attempts made before the Talk are untouched.
- **Nothing on disk but Notes** (*Decide what Talk remembers and keeps*, changed by [Anna's notes](../anna-notes/spec.md)): mic audio, Anna's audio and transcripts live in memory only; both transcripts are kept for the whole Talk until its Notes call is done, then dropped. The only thing written is `filesDir/anna-notes.json` (Notes spec §5). Logs carry timings and errors only: no transcripts, no Notes, no audio, no key.

## 6. Turn lifecycle

*From "Shape the Talk screen and the feel of a Turn", "Decide how the talk partner behaves" and "Pick the cloud provider…".*

States: **Connecting**, **Anna** (speaking), **You** (listening), **Thinking**, **No signal**, **Paused**. Keep the state machine pure Kotlin (no Android types), driven by events (VAD speech start/end, socket messages, timers, focus changes, button presses), and unit-test it.

1. **Start.** Tap Talk → the lesson pauses (§9), the Talk screen opens in **Connecting**, `TalkService` starts the mic and opens the session. On `setupComplete`, send the opening text. → **Thinking** until the first audio, then **Anna**.
2. **Anna speaks.** Play her audio with `AudioTrack` (24 kHz mono, `USAGE_MEDIA`, `CONTENT_TYPE_SPEECH`) as it streams. The mic is closed. When `turnComplete` has arrived **and** playback has drained, wait **300 ms** (echo tail), then the **turn beep** → **You**.
3. **The learner speaks.** In **You**, the VAD runs on every chunk. On speech start: send `activityStart`, then a **300 ms pre-roll** from a ring buffer, then stream live chunks. Keep the whole utterance in memory for a possible retry (§8).
4. **End of speech.** After **2.0 s** of silence (`TalkConfig.SILENCE_MS`; raised from 1.5 s on the car, §12), send `activityEnd`, play the soft **tick** → **Thinking**, mic closed. An utterance that reaches **30 s** ends the same way. On screen, a white ring fills over the silence window.
5. **Answer.** First audio → **Anna** (step 2). If no audio arrives **8 s** after the tick, the Turn has failed (§8).
6. **Silence.** If the learner says nothing in **You**, a slow ring fills toward **~9 s**; then send `(The learner is quiet.)` once → Anna nudges → **You** again, and from then on she waits silently: no second nudge until the learner has spoken. After Anna answers "wait" with "OK." (output transcript is exactly "OK."), skip the nudge until the learner next speaks. Silence never ends a Talk.
7. **"Bye".** When Anna's output transcript for a Turn starts with "Bye!" and contains "Drive safe", end the Talk after that audio has finished playing (§9). "By the way" is handled by the model.
8. **End Talk** (on-screen button, or the media play/pause, §10): ends at once, mid-word, no goodbye.
9. **30-min cap**, wall-clock from the Talk's start, "No signal" and "Paused" time included. When reached, wait for the current Turn to finish, then send `(Time is up.)` instead of opening the learner's turn; end after her goodbye plays. Anna never ends a Talk on her own otherwise.

## 7. The Talk screen

*From "Shape the Talk screen and the feel of a Turn" (variant A); prototype on local branch `prototype/talk-screen`, commit `0ebfe87`, `PrototypeTalk.kt` (never merge). UI6 glass style, `Glass.kt`.*

- **Talk button:** only on the player screen, next to "‹ Lessons" in the header, BYD blue. Not on the lesson list.
- **Full screen**, the lesson UI hidden. One big shape in the middle and one state word under it:
  | State | Shape | Word |
  |---|---|---|
  | Anna | green rings | Anna |
  | You | blue (plus the white silence ring, §6) | Your turn |
  | Thinking | white dots | Thinking |
  | No signal | grey broken ring | No signal |
  | Paused | grey ring | Paused |
  | Connecting | grey ring | Connecting… |
- **Anna's latest line** in small text at the bottom (from her output transcript). It stays in No signal and Paused. **The learner's words are never shown.**
- **End Talk** button bottom right; it always works, in every state.
- **Sounds**, made on the head unit: the **turn beep** (learner's turn starts), the soft **tick** (end of speech taken), and the **"no connection"** tone (a low two-note tone unlike the other two). No pre-recorded Anna clips.
- **Coming back to the app** during a Talk opens straight on the Talk screen.

## 8. Network drops and limits

*From "Decide what Talk does when the network drops".*

- **Failed Turn** (no first audio 8 s after the tick, or the socket dies mid-Turn): one **silent retry**: reconnect with the resumption handle and resend the saved utterance as one activity. An answer that arrives after its Turn failed is dropped, never played.
- **Retry fails** or the connection is lost: the "no connection" tone → **No signal**, mic closed (what is said is lost). Keep reconnecting in the background. When it is back: the turn beep → **You**; Anna does not repeat her question.
- **Resumption refused** (handle rejected): open a new session silently with the same setup, then send `(The connection came back.)`. The 30-min cap still counts from the Talk's start.
- **60 s** in No signal: the "no connection" tone, the Talk ends, and the lesson resumes by the usual rule (§9). This is the one case besides §11's 60 s pause where a Talk ends by itself.
- **No network on Talk:** **Connecting** for up to **5 s**; if it can't connect, the tone, the screen closes back to the player with a short "No internet" note, and the lesson stays as it was before the tap.
- **Free-tier limit** (a quota or rate-limit error, never shown as a network error):
  - at session start → as "No network on Talk", but the note reads **"Talk limit reached. Try later."**;
  - mid-Talk → one retry after **5 s**; refused again → the Talk ends with the same note and the lesson resumes.

## 9. The lesson around a Talk

*From "Shape the Talk screen…" and charting.*

- **Start:** remember whether the lesson was playing, then pause it through the `MediaController`. Then `TalkService` requests audio focus (`AUDIOFOCUS_GAIN`, `USAGE_MEDIA`) for Anna; the lesson's ExoPlayer handles its own focus as before.
- **End** (bye, End Talk, play/pause, 30-min cap, 60 s outage, 60 s pause): stop the mic, close the socket, abandon focus, close the Talk screen back to the player. If the lesson **was playing**, seek **5 s back** from where it paused and play. If it was already paused, leave it paused.
- **Exception:** a Talk ended by another media app (§10) leaves the lesson paused.
- **Car switched off** mid-Talk: the Talk is gone (nothing is kept); the app opens on the player next time.

## 10. Calls, focus and media buttons

*From "Decide how Talk behaves around calls, buttons and the screen".* The car handles phone calls itself (iPhone over Bluetooth), so the rules follow the kind of focus loss, not its cause.

- **Transient focus loss** (a call rings or is placed, "Hi BYD", any short loss without ducking): **Paused**. Anna stops mid-line, the mic is closed, and an utterance in progress or an answer still arriving is dropped. Focus back: the turn beep → **You**; Anna doesn't repeat herself. **60 s** without focus → the Talk ends, and the lesson resumes by the usual rule once focus is back. The 30-min cap keeps counting.
- **Transient loss that allows ducking** (e.g. a navigation prompt): Anna plays quieter (about 30 % volume); nothing pauses.
- **Permanent loss** (another media app plays): the Talk ends at once; the lesson stays paused.
- **Media buttons** while a Talk runs: **play/pause = End Talk** (lesson resumes by the usual rule). ⏮ ⏭ and **Wheel hold** do nothing, whatever the Button mode. Route this in `PlaybackService`'s `MediaButtonPlayer` / `onMediaButtonEvent` while a Talk is active.

## 11. Off screen and screen off

*From "Decide how Talk behaves around calls, buttons and the screen".*

- **Leaving the Talk screen** (Home, the map): the Talk carries on hands-free; the one open recording (§5) keeps it working.
- **Screen off:** the same.
- **Barge-in stays off**: the mic is closed while Anna speaks; only End Talk interrupts her.

## 12. First car build checks

*From "Measure a cloud Turn on the car over the hotspot" (Mac only, machine voices) and "Decide how Talk behaves around calls…".* None of these were measured. Run them on the first car build, parked first, then driving, over the iPhone hotspot. Each has a bar and a fallback.

| Check | Bar | If it fails |
|---|---|---|
| **Owner's voice**: the test set with the owner's own recordings, then a 10-min real Talk | Test set ≥ 12/14 with cases 7, 10, 11 passing; in the real Talk Anna mishears ≤ 1 in 10 Turns | Tune the prompt and rerun. If still failing on recognition, switch to the free pipeline from *Pick the cloud provider…*: Groq `whisper-large-v3-turbo` → Groq text model (`gpt-oss-20b` class) → Deepgram Aura (`mip_opt_out=true`) or Google Cloud TTS (needs a card) |
| **Hotspot latency**, parked and driving, ≥ 20 Turns each | End of speech → first answer audio ≤ 5 s on 9 of 10 Turns, silence window included (tick → first audio ≤ 3 s at 2.0 s) | Use the 1.0 s window (next row). If still over, nothing in the app can fix the network: bring it back to the owner as a new decision |
| ~~**Silence window** 1.0 s vs 1.5 s~~ (settled 2026-11-22 on the car: 1.5 s cut the owner off, so it is now 2.0 s, which the owner finds right; 1.0 s is not tried) | | |
| **Long Talk**: one 30-min Talk across the 15-min session limit | Runs to the 30-min goodbye; reconnects (goAway/resumption) are silent, with no gap over 2 s and no "No signal" caused by them | If resumption keeps failing, the new-session path (§8) carries it; if that is audible every ~10 min and annoying, lower the cap to 15 min |
| **Free-tier limits**: one 30-min Talk a day for a week | No limit or quota error | Fall back to the free pipeline (as in the first row) |
| **Weak signal**: tunnels and weak spots | The 8 s Turn timeout, retry, No signal and 60 s end fire as in §8; no stuck state; End Talk always works; the owner finds 8 s and 60 s right | Tune the two numbers (`TalkConfig`) |
| **Road noise** | The VAD doesn't start a learner turn on road noise alone, and the owner's speech is caught | Raise the VAD threshold or the minimum speech length |
| **Anna's echo** into the mic (barge-in off) | No learner turn starts from the tail of Anna's audio | Lengthen the 300 ms guard (§6). Barge-in is decided only after this passes |
| **Background and screen off** | Turns keep working for 5 min with the app on Home, and for 5 min with the screen off | Keep the screen on during a Talk, and pause the Talk (Paused state) when the Talk screen is left |
| ~~**Real call and "Hi BYD" focus**~~ (dropped 2026-11-22: no Bluetooth calls through the car, and "Hi BYD" doesn't work on it) | A call (in and out) and "Hi BYD" give transient focus loss, and the Talk pauses | If they give no focus loss, also treat `AudioManager` mode `IN_CALL` / `IN_COMMUNICATION` as transient loss. If a call gives permanent loss, the Talk ends (already the rule) |

## 13. Calls made while writing this spec

No ticket settled these; each is a sensible default the owner can overturn before the build:
- Voice `Aoede` (the probe's voice) and the 30-min goodbye line "We talked a lot today! Bye! Drive safe."
- Control text in brackets for the opening, the ~9 s nudge, a failed resumption and the 30-min cap, with three new prompt rules (§4), and "You can't see anything." added to the prompt.
- The test-set pass bar of ≥ 9/11 with cases 7, 10, 11 required.
- Ending on "bye" by matching Anna's output transcript, not a model tool call.
- Echo guard 300 ms, pre-roll 300 ms, utterance cap 30 s, ducked volume ~30 %.
- Silero VAD through `onnxruntime-android`.
- Talk button hidden when no key is built in.
- Talk stops voice practice's `RecorderService` rather than sharing the mic with it.
- The silence window is a code constant (`TalkConfig.SILENCE_MS`), not a Settings entry; the car check (§12) tries 1.0 s with a debug build.
