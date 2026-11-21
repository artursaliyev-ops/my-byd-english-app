# Decide what Talk does when the network drops
Type: grilling
Status: resolved
Blocked by: 09

## Question

HITL. The hotspot can drop or slow down mid-Talk (tunnels, weak signal, phone call on the iPhone). What does the learner see and hear when a Turn's answer is late or fails: how long before Talk gives up on a Turn, whether it retries, what Anna's state shape shows, whether Talk ends by itself and resumes the lesson, what happens if there is no network when "Talk" is tapped, and what happens when Gemini's free-tier limit refuses a session or a Turn (rate-limit error) or a session resumption fails.

## Answer

Resolved 2026-11-21 (grilling with the user). Builds on [Pick the cloud provider, model and key](09-pick-cloud-provider.md) and [Shape the Talk screen and the feel of a Turn](06-talk-screen.md). Anna's voice comes from the cloud and the car has no English TTS, so every "no connection" cue is made by the head unit itself.

- **Late Turn**: after the end-of-speech tick, Talk waits **8 s** for the first answer audio. After that the Turn has failed; an answer that arrives later is dropped, never played.
- **Retry**: one silent automatic retry: reconnect with session resumption and resend the learner's saved last utterance once. If that fails too, the learner says it again (after the cue below).
- **Sound**: one distinct "no connection" sound (a low two-note tone, unlike the turn beep and the end-of-speech tick). No pre-recorded Anna clips.
- **Screen**: a fourth state: a grey broken ring with the word "No signal"; Anna's latest line stays. The mic is closed in this state; what is said is lost. When the connection is back: the turn beep and the blue (your turn) state; Anna does not repeat her question. End Talk always works.
- **Long outage**: after **60 s** without a connection, the Talk ends by itself: the "no connection" sound, and the lesson resumes by the usual rule (5 s back only if it was playing). This is the one exception to "the Talk never ends by itself"; the silence rule is unchanged.
- **No network on "Talk"**: the Talk screen opens in a grey "Connecting…" state for up to 5 s; if it can't connect, the "no connection" sound, the screen closes back to the player with a short "No internet" note, and the lesson returns to how it was.
- **Free-tier limit**: refused at session start → as above, but the note reads "Talk limit reached. Try later." Rate-limited mid-Talk → one retry after 5 s; refused again → the Talk ends with the same note and the lesson resumes. Limit errors are never shown as network errors.
- **Resumption fails**: a new session opens silently; Anna is told to carry on naturally, with no fresh hello ("Sorry, I lost you for a second. So, …" plus a new easy question). The 30-min cap still counts from the Talk's start.
- **30-min cap**: wall-clock from the Talk's start, "No signal" time included.
- **Phone call on the iPhone**: only its network side is covered here (the rules above apply). The call's audio and audio focus stay in the map's "Real driving conditions" fog.
