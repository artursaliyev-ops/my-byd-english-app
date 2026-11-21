# 14: Anna says hello

**What to build:** the thinnest Talk from end to end, without the mic. On the player, a BYD-blue "Talk" button next to "‹ Lessons" pauses the lesson and opens the full-screen Talk screen in **Connecting**. A Talk foreground service opens one Gemini Live session, sends the opening, and plays Anna's hello: **Thinking** (white dots) until her first audio, then **Anna** (green rings) with her latest line in small text at the bottom. **End Talk** (bottom right) ends at once: the session closes, the screen goes back to the player, and the lesson resumes 5 s back if it was playing before the Talk, or stays paused if it wasn't. Spec §2, §3, §4, §7, §9.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] The API key is built in from the uncommitted `GEMINI_API_KEY` in `local.properties`; with no key the Talk button isn't shown. The key and the WebSocket URL never appear in logs.
- [x] The session uses API `v1beta`, model `gemini-3.1-flash-live-preview` and voice `Aoede` (each one constant), the §4 prompt, server activity detection off, compression and resumption on, and input and output transcription on.
- [x] The opening goes as bare realtime text, never inside an activity.
- [x] Anna's 24 kHz PCM plays as it streams, through `USAGE_MEDIA` / `CONTENT_TYPE_SPEECH`, with Talk holding audio focus while it runs.
- [x] The Talk button lives only on the player screen, not the lesson list; the screen follows the UI6 glass style.
- [x] End Talk works in every state shown so far, and the lesson-resume rule (5 s back only if it was playing) is covered by a unit test.
- [x] Nothing from the Talk is written to disk; logs carry timings and errors only.
