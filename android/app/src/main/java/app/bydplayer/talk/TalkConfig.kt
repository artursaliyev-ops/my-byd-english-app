package app.bydplayer.talk

/**
 * Every Talk constant in one place (spec §2, §3, §9), grouped by concern. Pure Kotlin: the state
 * machine and the protocol read these, and so do the plain JUnit tests. The test-set runner in
 * `tools/talk_test_set/` reads the plain string constants from this file by name.
 */
object TalkConfig {
    /** The Gemini Live session (spec §2, §3). */
    object Protocol {
        /** Live isn't served on `v1` (HTTP 404). */
        const val API_VERSION = "v1beta"
        const val MODEL = "gemini-3.1-flash-live-preview"
        const val VOICE = "Aoede"

        /** The Live WebSocket, without the key. The full URL (with the key) is never logged. */
        const val LIVE_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.$API_VERSION" +
                ".GenerativeService.BidiGenerateContent"

        /** The talk partner prompt (spec §4), bundled as one text asset. */
        const val PROMPT_ASSET = "talk/anna-prompt.txt"
    }

    /** Anna's Notes (Notes spec §4, §6). */
    object NotesCall {
        /** The writer call: one `v1beta` `generateContent` request, then one retry on the fallback. */
        const val ENDPOINT = "https://generativelanguage.googleapis.com/${Protocol.API_VERSION}/models"
        const val NOTES_MODEL = "gemini-3.1-flash-lite"
        const val NOTES_FALLBACK_MODEL = "gemini-3.5-flash-lite"

        /** The whole call, retry included; the first try gets at most [FIRST_TRY_MS]. */
        const val BUDGET_MS = 20_000L
        const val FIRST_TRY_MS = 10_000L

        const val WRITER_ASSET = "talk/notes-writer.txt"
        const val BLOCK_ASSET = "talk/anna-notes-block.txt"
        const val NOTES_FILE = "anna-notes.json"
    }

    /** Words: the app's control text (spec §4), Anna's fixed reply, and the player's notes. */
    object Text {
        /** App control text (spec §4), sent as bare realtime text. */
        const val OPENING = "(The learner pressed Talk. Start the Talk.)"

        /** The opening on every second Talk that has Notes (Notes spec §6). */
        const val OPENING_RECALL = "(The learner pressed Talk. Start the Talk. In your hello, ask about one thing from the notes.)"
        const val QUIET = "(The learner is quiet.)"
        const val TIME_UP = "(Time is up.)"

        /** Sent in a new session after the handle was refused (spec §8). */
        const val CAME_BACK = "(The connection came back.)"

        /** Anna's whole answer to "wait" (spec §4): after it, no nudge until the learner speaks. */
        const val WAIT_REPLY = "OK."

        /** The note on the player when Talk can't connect (spec §8). */
        const val NO_INTERNET = "No internet"

        /** The note on the player when Gemini's free tier refuses (spec §8). */
        const val TALK_LIMIT = "Talk limit reached. Try later."
    }

    /** Sound in and out: formats, the VAD, the app's own sounds and volume. */
    object Audio {
        /** Anna's audio: 24 kHz mono 16-bit PCM. */
        const val OUTPUT_RATE = 24_000

        /** The learner's mic: 16 kHz mono 16-bit PCM (spec §5). */
        const val INPUT_RATE = 16_000

        /** One VAD frame: Silero v4 at 16 kHz takes 512 samples (32 ms). */
        const val VAD_FRAME = 512

        /** The bundled Silero VAD model (v4, 16 kHz branch, MIT). */
        const val VAD_ASSET = "talk/silero_vad.onnx"

        /** A frame at or above this speech probability starts the learner's speech (spec §12: road noise). */
        const val SPEECH_START = 0.5f

        /** Once speaking, a frame below this counts toward the silence window. */
        const val SPEECH_END = 0.35f

        /** Audio kept from just before speech start and sent first (spec §6 step 3). */
        const val PRE_ROLL_MS = 300

        /** Live learner audio goes out in pieces of about this length. */
        const val CHUNK_MS = 100L

        /** The turn beep: a clear short tone (spec §7). */
        const val BEEP_HZ = 880
        const val BEEP_MS = 140

        /** The soft tick: higher, very short and quiet. */
        const val TICK_HZ = 1_600
        const val TICK_MS = 35

        /** The "no connection" tone: low, two notes going down, unlike the beep and tick (spec §7). */
        const val NO_CONNECTION_HZ_1 = 440
        const val NO_CONNECTION_HZ_2 = 330
        const val NO_CONNECTION_NOTE_MS = 180

        /** Anna's volume under a navigation prompt (spec §10). */
        const val DUCK_VOLUME = 0.3f
    }

    /** How long things take and wait (spec §6, §8, §9, §10). */
    object Timing {
        /** Silence that ends the learner's speech (spec §6 step 4). Raised from 1.5 s to 2.0 s on 2026-11-22: 1.5 s cut the owner off in the car (§12). */
        const val SILENCE_MS = 2_000L

        /** An utterance this long ends as if the learner had gone quiet. */
        const val MAX_UTTERANCE_MS = 30_000L

        /** After Anna's audio has drained, the mic waits this long for her echo to die (spec §6 step 2). */
        const val ECHO_GUARD_MS = 300L

        /** Silence in Your turn before the one nudge (spec §6 step 6). */
        const val QUIET_MS = 9_000L

        /** No first audio this long after a Turn went out: it has failed (spec §8). */
        const val TURN_TIMEOUT_MS = 8_000L

        /** No signal this long: the Talk ends (spec §8). */
        const val NO_SIGNAL_MS = 60_000L

        /** Connecting this long on Talk with no session: "No internet" (spec §8). */
        const val CONNECT_TIMEOUT_MS = 5_000L

        /** The gap between connection attempts while Connecting or in No signal. */
        const val RECONNECT_GAP_MS = 2_000L

        /** The wait before the one retry after a limit refusal mid-Talk (spec §8). */
        const val LIMIT_RETRY_MS = 5_000L

        /** A Talk's wall-clock cap from its start, No signal and Paused included (spec §6 step 9). */
        const val TALK_CAP_MS = 30 * 60 * 1000L

        /** A pause (call, "Hi BYD") this long ends the Talk (spec §10). */
        const val PAUSE_MAX_MS = 60_000L

        /** After a Talk, a lesson that was playing resumes this far back (spec §9). */
        const val RESUME_BACK_MS = 5_000L
    }
}

/**
 * Where the lesson goes after a Talk (spec §9): the position to seek to and play from, or null
 * to leave it paused because it wasn't playing when the Talk started.
 */
fun resumeAfterTalk(wasPlaying: Boolean, pausedAtMs: Long): Long? =
    if (wasPlaying) (pausedAtMs - TalkConfig.Timing.RESUME_BACK_MS).coerceAtLeast(0L) else null
