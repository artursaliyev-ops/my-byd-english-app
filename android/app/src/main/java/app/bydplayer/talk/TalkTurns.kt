package app.bydplayer.talk

/**
 * The Turn state machine of one Talk (spec §6). Pure Kotlin, no Android types: [TalkService]
 * feeds it events (server messages, button presses, and later VAD, timers and focus) and carries
 * out the effects it returns. Not thread-safe: call it from one thread.
 */
class TalkTurns {
    var state: TurnState = TurnState.Connecting
        private set

    /** Anna's latest line, from her output transcript (spec §7). Kept in memory only. */
    var annaLine: String = ""
        private set

    /** Her turn is complete, so the next transcript fragment starts a new line. */
    private var lineDone = false

    fun on(event: TalkEvent): List<TalkEffect> {
        if (state == TurnState.Ended) return emptyList()
        return when (event) {
            is TalkEvent.Server -> onServer(event.event)
            // End Talk ends at once, mid-word, no goodbye (spec §6 step 8). Until ticket 18 adds
            // reconnecting and No signal, a lost session ends the Talk the same way.
            TalkEvent.EndPressed, TalkEvent.SessionLost -> end()
        }
    }

    private fun end(): List<TalkEffect> {
        state = TurnState.Ended
        return listOf(TalkEffect.End)
    }

    private fun onServer(event: LiveEvent): List<TalkEffect> = when (event) {
        LiveEvent.SetupComplete -> if (state == TurnState.Connecting) {
            state = TurnState.Thinking
            listOf(TalkEffect.SendText(TalkConfig.OPENING))
        } else {
            emptyList()
        }
        is LiveEvent.Audio -> if (state == TurnState.Thinking || state == TurnState.Anna) {
            state = TurnState.Anna
            listOf(TalkEffect.Play(event.pcm))
        } else {
            emptyList()
        }
        is LiveEvent.AnnaSaid -> {
            annaLine = if (lineDone) event.text.trim() else (annaLine + event.text).trimStart()
            lineDone = false
            emptyList()
        }
        LiveEvent.TurnComplete -> {
            lineDone = true
            emptyList()
        }
        else -> emptyList()
    }
}

/** What the Talk screen shows (spec §7). You, No signal and Paused come with later tickets. */
enum class TurnState { Connecting, Thinking, Anna, Ended }

sealed interface TalkEvent {
    /** A message from the Live session. */
    data class Server(val event: LiveEvent) : TalkEvent

    /** The on-screen End Talk button. */
    data object EndPressed : TalkEvent

    /** The socket failed or closed. */
    data object SessionLost : TalkEvent
}

/** What [TalkTurns] asks the service to do. */
sealed interface TalkEffect {
    /** Send control text as bare realtime text (spec §3). */
    data class SendText(val text: String) : TalkEffect

    /** Queue a piece of Anna's audio for playback as it streams. */
    class Play(val pcm: ByteArray) : TalkEffect {
        override fun equals(other: Any?) = other is Play && pcm.contentEquals(other.pcm)
        override fun hashCode() = pcm.contentHashCode()
    }

    /** End the Talk: close the session, stop Anna, give the lesson back (spec §9). */
    data object End : TalkEffect
}
