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

    /** The newest `sessionResumptionUpdate.newHandle` (spec §3). In memory only. */
    var handle: String? = null
        private set

    /** A Turn is under way: from the opening or the learner's speech start to Anna's turnComplete. */
    private var inTurn = false

    /** A `goAway` came mid-Turn: reconnect once that Turn completes. */
    private var goAwayPending = false

    /** A [TalkEffect.Reconnect] went out and the new session isn't set up yet. */
    private var reconnecting = false

    /** Which Turn timeout is current (spec §8), so a timer from an earlier Turn is ignored. */
    private var turnNo = 0

    /** The current Turn's answer has begun: its timeout is spent. */
    private var answered = false

    /** The current Turn has had its one silent retry (spec §8). */
    private var retried = false

    /** Once the next session is set up, resend the Turn: the utterance, or [lastText]. */
    private var resendOnSetup = false

    /** Control text that began the current Turn, resent on a retry; null for a learner's Turn. */
    private var lastText: String? = null

    /** Which No signal spell this is, so a 60 s timer from an earlier one is ignored. */
    private var outage = 0

    /** A new session without the old conversation: once it is set up, say the connection came back. */
    private var cameBack = false

    /** A limit refusal mid-Talk has had its one retry; cleared once a session is set up again. */
    private var limitRetried = false

    fun on(event: TalkEvent): List<TalkEffect> {
        if (state == TurnState.Ended) return emptyList()
        return when (event) {
            is TalkEvent.Server -> onServer(event.event)
            is TalkEvent.Mic -> onMic(event.event)
            TalkEvent.PlaybackDrained -> if (state == TurnState.NoSignal) {
                emptyList()
            } else if (awaitingDrain && (timeUpSent || isGoodbye(annaLine))) {
                // Her goodbye has played: the Talk is over (spec §6 step 7).
                end()
            } else if (awaitingDrain) {
                awaitingDrain = false
                drained = true
                listOf(TalkEffect.After(TalkConfig.ECHO_GUARD_MS, TalkEvent.EchoGuardOver))
            } else {
                emptyList()
            }
            TalkEvent.EchoGuardOver -> if (state == TurnState.NoSignal) {
                emptyList()
            } else if (drained && timeUp) {
                drained = false
                sendTimeUp()
            } else if (drained) {
                drained = false
                startYourTurn()
            } else {
                emptyList()
            }
            // End Talk ends at once, mid-word, no goodbye (spec §6 step 8).
            TalkEvent.EndPressed -> end()
            TalkEvent.SessionLost -> onSessionLost()
            is TalkEvent.Quiet -> onQuiet(event)
            TalkEvent.TimeUp -> onTimeUp()
            TalkEvent.Start -> listOf(TalkEffect.After(TalkConfig.CONNECT_TIMEOUT_MS, TalkEvent.ConnectTimeout))
            TalkEvent.ConnectTimeout -> if (state == TurnState.Connecting) {
                // No network on Talk (spec §8): back to the player, the lesson as it was.
                state = TurnState.Ended
                listOf(TalkEffect.PlayNoConnection, TalkEffect.GiveUp(TalkConfig.NO_INTERNET))
            } else {
                emptyList()
            }
            is TalkEvent.TurnTimeout -> onTurnTimeout(event)
            TalkEvent.TryAgain -> onTryAgain()
            is TalkEvent.NoSignalOver -> if (state == TurnState.NoSignal && event.outage == outage) {
                state = TurnState.Ended
                listOf(TalkEffect.PlayNoConnection, TalkEffect.End)
            } else {
                emptyList()
            }
            TalkEvent.ResumeRefused -> onResumeRefused()
            TalkEvent.LimitReached -> onLimit()
            TalkEvent.LimitRetry -> onLimitRetry()
            TalkEvent.FocusPaused -> pause()
            TalkEvent.FocusDucked -> if (ducked) emptyList() else {
                ducked = true
                listOf(TalkEffect.Volume(TalkConfig.DUCK_VOLUME))
            }
            TalkEvent.FocusBack -> focusBack()
            is TalkEvent.PauseOver -> if (state == TurnState.Paused && event.pause == pauses) {
                state = TurnState.Ended
                listOf(TalkEffect.EndWhenFocusBack)
            } else {
                emptyList()
            }
            TalkEvent.FocusGone -> {
                state = TurnState.Ended
                listOf(TalkEffect.EndLessonPaused)
            }
        }
    }

    /** Anna plays at [TalkConfig.DUCK_VOLUME] under a navigation prompt. */
    private var ducked = false

    /** Counts pauses, so an earlier pause's timer can't end a later one. */
    private var pauses = 0

    /** The opening has gone out; before that a pause goes back to it (spec §10). */
    private var opened = false

    /** The session is ready, though the opening may still wait for focus. */
    private var setUp = false

    /** The learner's activity has started and not ended. */
    private var speaking = false

    /** An answer cut off by a pause: the rest of it is dropped until its turn completes. */
    private var dropping = false

    /**
     * Transient focus loss (a call, "Hi BYD"): Anna stops mid-line, the mic closes, an
     * utterance in progress or an answer still arriving is dropped. Anna's latest line stays.
     */
    private fun pause(): List<TalkEffect> {
        if (state == TurnState.Paused) return emptyList()
        val effects = mutableListOf<TalkEffect>(TalkEffect.StopAnna, TalkEffect.CloseMic)
        if (speaking) {
            // Close the activity; the answer to the cut-off words is dropped.
            effects += TalkEffect.EndActivity
            speaking = false
            dropping = true
        }
        if (state == TurnState.Thinking || state == TurnState.Anna) dropping = !awaitingDrain && !drained
        awaitingDrain = false
        drained = false
        silence = 0f
        state = TurnState.Paused
        // A quiet timer from before the pause must not fire after it.
        yourTurn++
        pauses++
        effects += TalkEffect.After(TalkConfig.PAUSE_MAX_MS, TalkEvent.PauseOver(pauses))
        return effects
    }

    /** The turn beep and **You**, with the quiet timer for this one of Your turns (spec §6 step 6). */
    private fun startYourTurn(): List<TalkEffect> {
        state = TurnState.You
        silence = 0f
        spoke = false
        yourTurn++
        // She answered "wait": the learner asked for time, so no nudge (spec §6 step 6).
        if (annaLine.trim() == TalkConfig.WAIT_REPLY) nudgeSpent = true
        return listOfNotNull(
            TalkEffect.PlayBeep,
            TalkEffect.OpenMic,
            if (nudgeSpent) null else TalkEffect.After(TalkConfig.QUIET_MS, TalkEvent.Quiet(yourTurn)),
        )
    }

    private fun focusBack(): List<TalkEffect> {
        val effects = mutableListOf<TalkEffect>()
        if (ducked) {
            ducked = false
            effects += TalkEffect.Volume(1f)
        }
        if (state != TurnState.Paused) return effects
        when {
            // The cap came while paused, or her goodbye was cut off: say goodbye now (spec §6 step 9).
            opened && timeUp -> effects += sendTimeUp()
            opened -> effects += startYourTurn()
            setUp -> effects += sendOpening()
            else -> state = TurnState.Connecting
        }
        return effects
    }

    private fun sendOpening(): List<TalkEffect> {
        opened = true
        state = TurnState.Thinking
        inTurn = true
        retried = false
        lastText = TalkConfig.OPENING
        return listOf(TalkEffect.SendText(TalkConfig.OPENING))
    }

    /** No first audio [TalkConfig.TURN_TIMEOUT_MS] after the tick: the Turn has failed (spec §8). */
    private fun onTurnTimeout(event: TalkEvent.TurnTimeout): List<TalkEffect> {
        if (event.turn != turnNo || answered || state != TurnState.Thinking || reconnecting) return emptyList()
        return failTurn()
    }

    /** A failed Turn gets one silent retry; a second failure is No signal. */
    private fun failTurn(): List<TalkEffect> = if (retried) noSignal() else retry()

    /** Reconnect with the handle (or a new session) and resend the Turn once it is set up. */
    private fun retry(): List<TalkEffect> {
        retried = true
        resendOnSetup = true
        return reopen()
    }

    /**
     * Opens the next session: resuming with the handle, or else a new one. A new one that isn't
     * resending a Turn then says the connection came back.
     */
    private fun reopen(): List<TalkEffect> {
        goAwayPending = false
        reconnecting = true
        val h = handle ?: run {
            cameBack = !resendOnSetup
            return listOf(TalkEffect.NewSession)
        }
        return listOf(TalkEffect.Reconnect(h))
    }

    /** The tone, the mic closed, Anna's line kept; reconnecting in the background (spec §8). */
    private fun noSignal(): List<TalkEffect> {
        state = TurnState.NoSignal
        inTurn = false
        reconnecting = false
        resendOnSetup = false
        cameBack = false
        retried = false
        awaitingDrain = false
        drained = false
        silence = 0f
        outage++
        return listOf(
            TalkEffect.PlayNoConnection,
            TalkEffect.CloseMic,
            TalkEffect.After(TalkConfig.NO_SIGNAL_MS, TalkEvent.NoSignalOver(outage)),
            TalkEffect.After(TalkConfig.RECONNECT_GAP_MS, TalkEvent.TryAgain),
        )
    }

    private fun onTryAgain(): List<TalkEffect> = when {
        state == TurnState.Connecting -> listOf(TalkEffect.NewSession)
        state == TurnState.NoSignal && !reconnecting -> reopen()
        else -> emptyList()
    }

    /** The server refused the handle (spec §8): a new session, silently, with the same setup. */
    private fun onResumeRefused(): List<TalkEffect> {
        if (!reconnecting) return onSessionLost()
        handle = null
        resendOnSetup = false
        cameBack = true
        return listOf(TalkEffect.NewSession)
    }

    /**
     * Gemini's free tier refused (spec §8), never told as the network. Before any session was set
     * up: the tone and back to the player with the note, the lesson as it was. Mid-Talk: the
     * screen stays as it is and one retry goes out after [TalkConfig.LIMIT_RETRY_MS]; refused
     * again, the Talk ends with the note and the lesson resumes by the usual rule.
     */
    private fun onLimit(): List<TalkEffect> {
        if (!setUp) {
            state = TurnState.Ended
            return listOf(TalkEffect.PlayNoConnection, TalkEffect.GiveUp(TalkConfig.TALK_LIMIT))
        }
        if (limitRetried) {
            state = TurnState.Ended
            return listOf(TalkEffect.EndWithNote(TalkConfig.TALK_LIMIT))
        }
        limitRetried = true
        // Held as a reconnect: a late answer is dropped and timers wait for the retry.
        reconnecting = true
        return listOf(TalkEffect.After(TalkConfig.LIMIT_RETRY_MS, TalkEvent.LimitRetry))
    }

    /** The wait after a limit refusal is over: reopen, resending a Turn under way. */
    private fun onLimitRetry(): List<TalkEffect> {
        if (!limitRetried || !reconnecting) return emptyList()
        resendOnSetup = inTurn
        return reopen()
    }

    /** The next session is set up: say it came back, come back from No signal, resend a failed Turn, or nothing. */
    private fun onReconnected(): List<TalkEffect> {
        reconnecting = false
        limitRetried = false
        if (cameBack) {
            cameBack = false
            resendOnSetup = false
            retried = false
            state = TurnState.Thinking
            inTurn = true
            lastText = TalkConfig.CAME_BACK
            return listOf(TalkEffect.CloseMic, TalkEffect.SendText(TalkConfig.CAME_BACK))
        }
        if (state == TurnState.NoSignal) return comeBack()
        if (!resendOnSetup) return emptyList()
        resendOnSetup = false
        lastText?.let { return listOf(TalkEffect.SendText(it)) }
        val resend = listOf(TalkEffect.StartActivity, TalkEffect.SendAudio(utterance))
        // Still speaking: the rest goes out live from here.
        if (state == TurnState.You) return resend
        return resend + TalkEffect.EndActivity + armTurnTimeout()
    }

    /** Back from No signal: the turn beep and Your turn; Anna doesn't repeat her question. */
    private fun comeBack(): List<TalkEffect> = if (timeUp) sendTimeUp() else startYourTurn()

    private fun armTurnTimeout(): TalkEffect {
        answered = false
        turnNo++
        return TalkEffect.After(TalkConfig.TURN_TIMEOUT_MS, TalkEvent.TurnTimeout(turnNo))
    }

    /**
     * A close between Turns resumes silently with the handle (spec §3). A close mid-Turn is a
     * failed Turn; one before the resumed session is set up, or with no handle, is No signal; while
     * Connecting or in No signal, the app tries again after a short gap (spec §8).
     */
    private fun onSessionLost(): List<TalkEffect> {
        if (state == TurnState.Connecting || state == TurnState.NoSignal) {
            reconnecting = false
            return listOf(TalkEffect.After(TalkConfig.RECONNECT_GAP_MS, TalkEvent.TryAgain))
        }
        if (inTurn) {
            // The retry's own session was lost before it was set up: the retry has failed.
            if (reconnecting && retried) return noSignal()
            reconnecting = false
            return failTurn()
        }
        val h = handle
        if (h == null || reconnecting) return noSignal()
        return reconnect(h)
    }

    private fun reconnect(h: String): List<TalkEffect> {
        goAwayPending = false
        reconnecting = true
        return listOf(TalkEffect.Reconnect(h))
    }

    /** The current Turn finishes first; a turn of Yours with no speech yet isn't one (spec §6 step 9). */
    private fun onTimeUp(): List<TalkEffect> {
        timeUp = true
        return if (state == TurnState.You && !spoke) listOf(TalkEffect.CloseMic) + sendTimeUp() else emptyList()
    }

    private fun sendTimeUp(): List<TalkEffect> {
        timeUpSent = true
        state = TurnState.Thinking
        inTurn = true
        retried = false
        lastText = TalkConfig.TIME_UP
        return listOf(TalkEffect.SendText(TalkConfig.TIME_UP))
    }

    /** ~9 s of silence in Your turn: one nudge, then she waits silently (spec §6 step 6). */
    private fun onQuiet(event: TalkEvent.Quiet): List<TalkEffect> {
        if (state != TurnState.You || event.turn != yourTurn || spoke || nudgeSpent) return emptyList()
        nudgeSpent = true
        state = TurnState.Thinking
        inTurn = true
        retried = false
        lastText = TalkConfig.QUIET
        return listOf(TalkEffect.CloseMic, TalkEffect.SendText(TalkConfig.QUIET))
    }

    /** Her answer to "bye" (spec §4); "By the way" is left to the model. */
    private fun isGoodbye(line: String) = line.trim().startsWith("Bye!") && line.contains("Drive safe")

    private fun end(): List<TalkEffect> {
        state = TurnState.Ended
        return listOf(TalkEffect.End)
    }

    private fun onServer(event: LiveEvent): List<TalkEffect> = when (event) {
        // Resumed: the server keeps the conversation, so nothing to send unless a Turn failed (spec §3, §8).
        LiveEvent.SetupComplete -> if (reconnecting) {
            onReconnected()
        } else {
            setUp = true
            if (state == TurnState.Connecting) sendOpening() else emptyList()
        }
        // An answer that arrives after its Turn failed is dropped, never played (spec §8).
        is LiveEvent.Audio -> if (!dropping && !reconnecting && (state == TurnState.Thinking || state == TurnState.Anna)) {
            answered = true
            state = TurnState.Anna
            listOf(TalkEffect.Play(event.pcm))
        } else {
            emptyList()
        }
        is LiveEvent.AnnaSaid -> if (dropping) emptyList() else {
            annaLine = if (lineDone) event.text.trim() else (annaLine + event.text).trimStart()
            lineDone = false
            emptyList()
        }
        LiveEvent.TurnComplete -> if (dropping) {
            dropping = false
            lineDone = true
            inTurn = false
            if (goAwayPending) reconnectBetweenTurns() else emptyList()
        } else {
            lineDone = true
            inTurn = false
            retried = false
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
                speaking = true
                inTurn = true
                spoke = true
                nudgeSpent = false
                retried = false
                lastText = null
                // The new activity takes over from an answer still being dropped.
                dropping = false
                heard.reset()
                heard.write(event.preRoll)
                listOf(TalkEffect.StartActivity, TalkEffect.SendAudio(event.preRoll))
            }
            is MicEvent.Audio -> {
                heard.write(event.pcm)
                // Reconnecting mid-speech: all of it goes out once the new session is up.
                if (resendOnSetup) emptyList() else listOf(TalkEffect.SendAudio(event.pcm))
            }
            is MicEvent.Silence -> {
                silence = event.fraction
                emptyList()
            }
            MicEvent.SpeechEnded -> {
                state = TurnState.Thinking
                speaking = false
                silence = 0f
                if (resendOnSetup) {
                    listOf(TalkEffect.PlayTick, TalkEffect.CloseMic)
                } else {
                    listOf(TalkEffect.EndActivity, TalkEffect.PlayTick, TalkEffect.CloseMic, armTurnTimeout())
                }
            }
        }
    }
}

/** What the Talk screen shows (spec §7). */
enum class TurnState { Connecting, Thinking, Anna, You, NoSignal, Paused, Ended }

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

    /** The Talk button was pressed and the first session is opening. */
    data object Start : TalkEvent

    /** [TalkConfig.CONNECT_TIMEOUT_MS] since [Start]. */
    data object ConnectTimeout : TalkEvent

    /** [TalkConfig.TURN_TIMEOUT_MS] since the tick of Turn [turn] (or its resend). */
    data class TurnTimeout(val turn: Int) : TalkEvent

    /** Try connecting again, [TalkConfig.RECONNECT_GAP_MS] after a failed attempt. */
    data object TryAgain : TalkEvent

    /** [TalkConfig.NO_SIGNAL_MS] since No signal [outage] began. */
    data class NoSignalOver(val outage: Int) : TalkEvent

    /** The server refused the resumption handle (see [LiveProtocol.classifyLoss]). */
    data object ResumeRefused : TalkEvent

    /** Gemini's free tier refused: a quota or rate limit (see [LiveProtocol.classifyLoss]). */
    data object LimitReached : TalkEvent

    /** [TalkConfig.LIMIT_RETRY_MS] since a limit refusal mid-Talk: try once more. */
    data object LimitRetry : TalkEvent

    /** Transient focus loss without ducking: a call, "Hi BYD" (spec §10). */
    data object FocusPaused : TalkEvent

    /** Transient focus loss that allows ducking: a navigation prompt. */
    data object FocusDucked : TalkEvent

    /** Focus is back after a transient loss. */
    data object FocusBack : TalkEvent

    /** Permanent focus loss: another media app plays. */
    data object FocusGone : TalkEvent

    /** The 60 s limit of pause number [pause] is up (asked for with [TalkEffect.After]). */
    data class PauseOver(val pause: Int) : TalkEvent
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

    /** The low two-note "no connection" tone (spec §7). */
    data object PlayNoConnection : TalkEffect

    /** Close the current session and open a new one with the same setup, no handle (spec §8). */
    data object NewSession : TalkEffect

    /**
     * The Talk never got going: close it, show [note] on the player, and leave the lesson as it
     * was before the tap (spec §8), not by the resume rule.
     */
    data class GiveUp(val note: String) : TalkEffect
    /** Stop Anna mid-line and drop what is queued of her audio; the Talk goes on. */
    data object StopAnna : TalkEffect

    /** Play Anna at this fraction of full volume. */
    data class Volume(val level: Float) : TalkEffect

    /** End the Talk like [End], but give the lesson back only once audio focus returns. */
    data object EndWhenFocusBack : TalkEffect

    /** End the Talk like [End] and show [note] on the player; the lesson resumes by the usual rule. */
    data class EndWithNote(val note: String) : TalkEffect

    /** End the Talk like [End], but leave the lesson paused: another media app has taken over. */
    data object EndLessonPaused : TalkEffect
}
