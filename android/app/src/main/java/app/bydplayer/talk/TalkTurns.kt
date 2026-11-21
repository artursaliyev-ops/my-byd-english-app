package app.bydplayer.talk

/**
 * The Turn state machine of one Talk (spec §6). Pure Kotlin, no Android types: [TalkService]
 * feeds it events (server messages, the [SpeechGate]'s mic events, playback draining, timers it
 * asked for, button presses, and later focus) and carries out the effects it returns. Not
 * thread-safe: call it from one thread.
 */
class TalkTurns {
    var state: TurnState = TurnState.Connecting
        private set

    /** Anna's latest line, from her output transcript (spec §7). Kept in memory only. */
    var annaLine: String = ""
        private set

    /** Her turn is complete, so the next transcript fragment starts a new line. */
    private var lineDone = false

    /** Her turn is complete and the service is waiting for her audio to drain. */
    private var awaitingDrain = false

    /** Her audio has drained after her complete turn: the echo guard is running. */
    private var drained = false

    /** How full the white silence ring is in Your turn, 0 to 1 (spec §6 step 4). */
    var silence: Float = 0f
        private set

    private val heard = java.io.ByteArrayOutputStream()

    /**
     * The learner's current utterance, pre-roll included: 16 kHz PCM, in memory only (spec §5),
     * kept until the next one starts so a failed Turn can be resent (ticket 18).
     */
    val utterance: ByteArray get() = heard.toByteArray()

    /** The newest `sessionResumptionUpdate.newHandle` (spec §3). In memory only. */
    var handle: String? = null
        private set

    /** A Turn is under way: from the opening or the learner's speech start to Anna's turnComplete. */
    private var inTurn = false

    /** A `goAway` came mid-Turn: reconnect once that Turn completes. */
    private var goAwayPending = false

    /** A [TalkEffect.Reconnect] went out and the new session isn't set up yet. */
    private var reconnecting = false

    fun on(event: TalkEvent): List<TalkEffect> {
        if (state == TurnState.Ended) return emptyList()
        return when (event) {
            is TalkEvent.Server -> onServer(event.event)
            is TalkEvent.Mic -> onMic(event.event)
            TalkEvent.PlaybackDrained -> if (awaitingDrain) {
                awaitingDrain = false
                drained = true
                listOf(TalkEffect.After(TalkConfig.ECHO_GUARD_MS, TalkEvent.EchoGuardOver))
            } else {
                emptyList()
            }
            TalkEvent.EchoGuardOver -> if (drained) {
                drained = false
                state = TurnState.You
                silence = 0f
                listOf(TalkEffect.PlayBeep, TalkEffect.OpenMic)
            } else {
                emptyList()
            }
            // End Talk ends at once, mid-word, no goodbye (spec §6 step 8).
            TalkEvent.EndPressed -> end()
            TalkEvent.SessionLost -> onSessionLost()
        }
    }

    /**
     * A close between Turns resumes silently with the handle (spec §3). Until ticket 18 adds
     * retrying and No signal, a close mid-Turn, with no handle yet, or before the resumed session
     * is set up ends the Talk.
     */
    private fun onSessionLost(): List<TalkEffect> {
        val h = handle
        if (inTurn || h == null || reconnecting) return end()
        return reconnect(h)
    }

    private fun reconnect(h: String): List<TalkEffect> {
        goAwayPending = false
        reconnecting = true
        return listOf(TalkEffect.Reconnect(h))
    }

    private fun end(): List<TalkEffect> {
        state = TurnState.Ended
        return listOf(TalkEffect.End)
    }

    private fun onServer(event: LiveEvent): List<TalkEffect> = when (event) {
        LiveEvent.SetupComplete -> if (reconnecting) {
            // Resumed: the server keeps the conversation, so nothing to send (spec §3).
            reconnecting = false
            emptyList()
        } else if (state == TurnState.Connecting) {
            state = TurnState.Thinking
            inTurn = true
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
            inTurn = false
            // Anna is done talking once her audio has played out (spec §6 step 2).
            val drain = if (state == TurnState.Anna || state == TurnState.Thinking) {
                awaitingDrain = true
                listOf(TalkEffect.AwaitDrain)
            } else {
                emptyList()
            }
            // A goAway that came mid-Turn is handled now the Turn is complete (spec §3).
            drain + if (goAwayPending) reconnectBetweenTurns() else emptyList()
        }
        is LiveEvent.ResumptionHandle -> {
            handle = event.handle
            emptyList()
        }
        LiveEvent.GoAway -> reconnectBetweenTurns()
        else -> emptyList()
    }

    /** Reconnect silently with the newest handle now, or once the current Turn completes. */
    private fun reconnectBetweenTurns(): List<TalkEffect> {
        val h = handle
        if (inTurn || h == null) {
            goAwayPending = true
            return emptyList()
        }
        return reconnect(h)
    }

    private fun onMic(event: MicEvent): List<TalkEffect> {
        if (state != TurnState.You) return emptyList()
        return when (event) {
            is MicEvent.SpeechStarted -> {
                inTurn = true
                heard.reset()
                heard.write(event.preRoll)
                listOf(TalkEffect.StartActivity, TalkEffect.SendAudio(event.preRoll))
            }
            is MicEvent.Audio -> {
                heard.write(event.pcm)
                listOf(TalkEffect.SendAudio(event.pcm))
            }
            is MicEvent.Silence -> {
                silence = event.fraction
                emptyList()
            }
            MicEvent.SpeechEnded -> {
                state = TurnState.Thinking
                silence = 0f
                listOf(TalkEffect.EndActivity, TalkEffect.PlayTick, TalkEffect.CloseMic)
            }
        }
    }
}

/** What the Talk screen shows (spec §7). No signal and Paused come with later tickets. */
enum class TurnState { Connecting, Thinking, Anna, You, Ended }

sealed interface TalkEvent {
    /** A message from the Live session. */
    data class Server(val event: LiveEvent) : TalkEvent

    /** The on-screen End Talk button. */
    data object EndPressed : TalkEvent

    /** The socket failed or closed. */
    data object SessionLost : TalkEvent

    /** What the [SpeechGate] heard on the mic. */
    data class Mic(val event: MicEvent) : TalkEvent

    /** Everything queued of Anna's audio has played (asked for with [TalkEffect.AwaitDrain]). */
    data object PlaybackDrained : TalkEvent

    /** The echo guard after Anna's audio is over. */
    data object EchoGuardOver : TalkEvent
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

    /** Send `activityStart`: the learner has begun to speak. */
    data object StartActivity : TalkEffect

    /** Send a piece of the learner's speech. */
    class SendAudio(val pcm: ByteArray) : TalkEffect {
        override fun equals(other: Any?) = other is SendAudio && pcm.contentEquals(other.pcm)
        override fun hashCode() = pcm.contentHashCode()
    }

    /** Send `activityEnd`: the learner's speech is over. */
    data object EndActivity : TalkEffect

    /** Report [TalkEvent.PlaybackDrained] once Anna's queued audio has played. */
    data object AwaitDrain : TalkEffect

    /** Feed [event] back after [ms]. */
    data class After(val ms: Long, val event: TalkEvent) : TalkEffect

    /** Let the mic's frames through to the [SpeechGate] (the recording itself always runs). */
    data object OpenMic : TalkEffect

    /** Drop the mic's frames again. */
    data object CloseMic : TalkEffect

    /** The turn beep: the learner's turn starts. */
    data object PlayBeep : TalkEffect

    /** The soft tick: the end of the learner's speech is taken. */
    data object PlayTick : TalkEffect

    /**
     * Close the current session, then open a new one resuming with [handle] (spec §3). Silent:
     * the screen state stays and no sound plays.
     */
    data class Reconnect(val handle: String) : TalkEffect

    /** End the Talk: close the session, stop Anna, give the lesson back (spec §9). */
    data object End : TalkEffect
}
