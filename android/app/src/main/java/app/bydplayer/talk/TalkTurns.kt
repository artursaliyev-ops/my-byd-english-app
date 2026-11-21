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

    /** Which of Your turns this is, so a quiet timer from an earlier one is ignored. */
    private var yourTurn = 0

    /** The learner has begun to speak in this one of Your turns. */
    private var spoke = false

    /** The 30-min cap is reached (spec §6 step 9): no more of Your turns. */
    private var timeUp = false

    /** `(Time is up.)` has gone out: the Talk ends after her goodbye plays. */
    private var timeUpSent = false

    /** No nudge until the learner next speaks: one was sent, or Anna answered "wait" (spec §6 step 6). */
    private var nudgeSpent = false

    /**
     * The learner's current utterance, pre-roll included: 16 kHz PCM, in memory only (spec §5),
     * kept until the next one starts so a failed Turn can be resent (ticket 18).
     */
    val utterance: ByteArray get() = heard.toByteArray()

    fun on(event: TalkEvent): List<TalkEffect> {
        if (state == TurnState.Ended) return emptyList()
        return when (event) {
            is TalkEvent.Server -> onServer(event.event)
            is TalkEvent.Mic -> onMic(event.event)
            TalkEvent.PlaybackDrained -> if (awaitingDrain && (timeUpSent || isGoodbye(annaLine))) {
                // Her goodbye has played: the Talk is over (spec §6 step 7).
                end()
            } else if (awaitingDrain) {
                awaitingDrain = false
                drained = true
                listOf(TalkEffect.After(TalkConfig.ECHO_GUARD_MS, TalkEvent.EchoGuardOver))
            } else {
                emptyList()
            }
            TalkEvent.EchoGuardOver -> if (drained && timeUp) {
                drained = false
                sendTimeUp()
            } else if (drained) {
                drained = false
                state = TurnState.You
                silence = 0f
                spoke = false
                yourTurn++
                // She answered "wait": the learner asked for time, so no nudge (spec §6 step 6).
                if (annaLine.trim() == TalkConfig.WAIT_REPLY) nudgeSpent = true
                listOfNotNull(
                    TalkEffect.PlayBeep,
                    TalkEffect.OpenMic,
                    if (nudgeSpent) null else TalkEffect.After(TalkConfig.QUIET_MS, TalkEvent.Quiet(yourTurn)),
                )
            } else {
                emptyList()
            }
            // End Talk ends at once, mid-word, no goodbye (spec §6 step 8). Until ticket 18 adds
            // reconnecting and No signal, a lost session ends the Talk the same way.
            TalkEvent.EndPressed, TalkEvent.SessionLost -> end()
            is TalkEvent.Quiet -> onQuiet(event)
            TalkEvent.TimeUp -> onTimeUp()
        }
    }

    /** The current Turn finishes first; a turn of Yours with no speech yet isn't one (spec §6 step 9). */
    private fun onTimeUp(): List<TalkEffect> {
        timeUp = true
        return if (state == TurnState.You && !spoke) listOf(TalkEffect.CloseMic) + sendTimeUp() else emptyList()
    }

    private fun sendTimeUp(): List<TalkEffect> {
        timeUpSent = true
        state = TurnState.Thinking
        return listOf(TalkEffect.SendText(TalkConfig.TIME_UP))
    }

    /** ~9 s of silence in Your turn: one nudge, then she waits silently (spec §6 step 6). */
    private fun onQuiet(event: TalkEvent.Quiet): List<TalkEffect> {
        if (state != TurnState.You || event.turn != yourTurn || spoke || nudgeSpent) return emptyList()
        nudgeSpent = true
        state = TurnState.Thinking
        return listOf(TalkEffect.CloseMic, TalkEffect.SendText(TalkConfig.QUIET))
    }

    /** Her answer to "bye" (spec §4); "By the way" is left to the model. */
    private fun isGoodbye(line: String) = line.trim().startsWith("Bye!") && line.contains("Drive safe")

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
            // Anna is done talking once her audio has played out (spec §6 step 2).
            if (state == TurnState.Anna || state == TurnState.Thinking) {
                awaitingDrain = true
                listOf(TalkEffect.AwaitDrain)
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }

    private fun onMic(event: MicEvent): List<TalkEffect> {
        if (state != TurnState.You) return emptyList()
        return when (event) {
            is MicEvent.SpeechStarted -> {
                spoke = true
                nudgeSpent = false
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

    /** [TalkConfig.QUIET_MS] have passed since Your [turn] began (asked for with [TalkEffect.After]). */
    data class Quiet(val turn: Int) : TalkEvent

    /** [TalkConfig.TALK_CAP_MS] of wall-clock time since the Talk started. */
    data object TimeUp : TalkEvent
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

    /** End the Talk: close the session, stop Anna, give the lesson back (spec §9). */
    data object End : TalkEffect
}
