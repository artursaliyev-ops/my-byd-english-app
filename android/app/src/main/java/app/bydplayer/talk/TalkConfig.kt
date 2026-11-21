package app.bydplayer.talk

/**
 * Every Talk constant in one place (spec §2, §3, §9). Pure Kotlin: the state machine and the
 * protocol read these, and so do the plain JUnit tests.
 */
object TalkConfig {
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

    /** App control text (spec §4), sent as bare realtime text. */
    const val OPENING = "(The learner pressed Talk. Start the Talk.)"

    /** Anna's audio: 24 kHz mono 16-bit PCM. */
    const val OUTPUT_RATE = 24_000

    /** After a Talk, a lesson that was playing resumes this far back (spec §9). */
    const val RESUME_BACK_MS = 5_000L
}

/**
 * Where the lesson goes after a Talk (spec §9): the position to seek to and play from, or null
 * to leave it paused because it wasn't playing when the Talk started.
 */
fun resumeAfterTalk(wasPlaying: Boolean, pausedAtMs: Long): Long? =
    if (wasPlaying) (pausedAtMs - TalkConfig.RESUME_BACK_MS).coerceAtLeast(0L) else null
