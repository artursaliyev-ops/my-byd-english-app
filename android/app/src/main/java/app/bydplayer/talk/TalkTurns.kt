package app.bydplayer.talk

/**
 * The Turn state machine of one Talk (spec §6). Pure Kotlin, no Android types: [TalkService]
 * feeds it events (server messages, the [SpeechGate]'s mic events, playback draining, timers it
 * asked for, button presses and focus changes) and carries out the effects it returns. Not
 * thread-safe: call it from one thread.
 *
 * Two conditions interrupt the Turns, separately or both at once: focus lost (Paused, spec §10)
 * and the connection lost (No signal, spec §8). Each has its own 60 s timer, and neither clears
 * the other: reconnecting goes on while paused, the "no connection" tone waits for focus, and the
 * Turns go on (the turn beep and Your turn) only once focus is back and the session is up. Any
 * end while focus is lost gives the lesson back only once focus returns. While both hold, the
 * screen shows Paused.
 *
 * Besides the learner's Turns, the app's control text (spec §4: the opening, the nudge,
 * came-back, time-up) also gets an answer from Anna; this machine runs each such exchange like
 * a Turn, with the same timeout and retry, though the glossary's Turn is the learner's.
 */
class TalkTurns(
    /** The opening control text: the normal one, or the recall one on every second Talk with Notes (Notes spec §6). */
    private val opening: String = TalkConfig.Text.OPENING,
) {
    // ---- What the screen shows ----

    val state: TurnState
        get() = when {
            ended -> TurnState.Ended
            focusLost -> TurnState.Paused
            noSignal -> TurnState.NoSignal
            else -> phase
        }

    /** Anna's latest line, from her output transcript (spec §7). Kept in memory only. */
    var annaLine: String = ""
        private set

    /**
     * The whole Talk's transcript (Notes spec §3), across reconnects: each exchange Anna answered,
     * with what the learner said (null after control text). In memory only, for the Notes call.
     */
    val transcript: List<SaidTurn> get() = said

    private val said = mutableListOf<SaidTurn>()
    private val learnerNow = StringBuilder()
    private val annaNow = StringBuilder()

    /** A new exchange (or its retry) begins: what came before it is not part of it. */
    private fun freshExchange() {
        learnerNow.setLength(0)
        annaNow.setLength(0)
    }

    /** How full the white silence ring is in Your turn, 0 to 1 (spec §6 step 4). */
    var silence: Float = 0f
        private set

    /** The slow quiet ring runs: Your turn, nothing said yet, and the nudge still to come (spec §6 step 6). */
    val quietRing: Boolean get() = listening && !learnerSpoke && !nudgeSpent

    /**
     * The learner's current utterance, pre-roll included: 16 kHz PCM, in memory only (spec §5),
     * kept until the next one starts so a failed Turn can be resent (spec §8).
     */
    val utterance: ByteArray get() = heard.toByteArray()

    /** The newest `sessionResumptionUpdate.newHandle` (spec §3). In memory only. */
    var handle: String? = null
        private set

    // ---- Turn flow ----

    /** Where the Turns are: Connecting, Thinking, Anna or You. Paused and No signal lie over it. */
    private var phase = TurnState.Connecting
    private var ended = false

    /** The opening has gone out. */
    private var opened = false

    /** A Turn is under way: from its control text or the learner's speech start to Anna's turnComplete. */
    private var inTurn = false

    /** Control text that began the current Turn, resent on a retry; null for a learner's Turn. */
    private var controlText: String? = null

    /** The current Turn's answer has begun: its timeout is spent. */
    private var firstAudioCame = false

    /** After Anna's complete turn: her audio draining, then the echo guard. */
    private var tail = AnnaTail.None

    /** Her turn is complete, so the next transcript fragment starts a new line. */
    private var lineDone = false

    /** An answer cut off by a pause: the rest of it is dropped until its turn completes. */
    private var droppingAnswer = false

    private val heard = java.io.ByteArrayOutputStream()

    /** The learner's activity has started and not ended. */
    private var learnerSpeaking = false

    /** The learner has begun to speak in this one of Your turns. */
    private var learnerSpoke = false

    /** No nudge until the learner next speaks: one was sent, or Anna answered "wait" (spec §6 step 6). */
    private var nudgeSpent = false

    /** The 30-min cap is reached (spec §6 step 9): no more of Your turns. */
    private var capReached = false

    /** `(Time is up.)` has gone out: the Talk ends after her goodbye plays. */
    private var timeUpSent = false

    // ---- Reconnect ----

    /** A first session has been set up: Connecting can no longer end in "No internet". */
    private var everSetUp = false

    /** The current session is set up. */
    private var sessionUp = false

    /** A [TalkEffect.Reconnect] or [TalkEffect.NewSession] went out (or a limit retry waits) and the next session isn't set up yet. */
    private var reopening = false

    /** A `goAway` came mid-Turn: reconnect once that Turn completes. */
    private var goAwayPending = false

    /** The current Turn has had its one silent retry (spec §8). */
    private var retried = false

    /** Once the next session is set up, resend the Turn: the utterance, or [controlText]. */
    private var resendOnSetup = false

    /** The session is new, without the Talk so far: say the connection came back (spec §8). */
    private var contextLost = false

    /** A limit refusal mid-Talk has had its one retry; cleared once a session is set up again. */
    private var limitRetried = false

    /** No signal: the connection is lost and reconnecting goes on in the background (spec §8). */
    private var noSignal = false

    // ---- Focus ----

    /** Paused: transient focus loss (spec §10). */
    private var focusLost = false

    /** Anna plays at [TalkConfig.Audio.DUCK_VOLUME] under a navigation prompt. */
    private var ducked = false

    /** No signal began while focus was lost, so its tone plays once focus is back. */
    private var toneOwed = false

    /**
     * Paused or No signal cut the Turns off (or the opening hasn't gone out): they go on with
     * [resumeTurns] once neither holds and the session is up.
     */
    private var awaitingResume = true

    // ---- Timer generations: each bump makes the earlier timers of its kind stale ----

    private var quietGeneration = 0
    private var turnTimeoutGeneration = 0
    private var outageGeneration = 0
    private var pauseGeneration = 0

    // ---- Named conditions ----

    private val interrupted get() = focusLost || noSignal

    /** Your turn, with the mic open. */
    private val listening get() = phase == TurnState.You && !interrupted

    /** Anna is being asked or is answering, and her audio may play. */
    private val answerExpected get() = (phase == TurnState.Thinking || phase == TurnState.Anna) && !interrupted

    fun on(event: TalkEvent): List<TalkEffect> {
        if (ended) return emptyList()
        return when (event) {
            is TalkEvent.Server -> onServer(event.event)
            is TalkEvent.Mic -> onMic(event.event)
            TalkEvent.PlaybackDrained -> onDrained()
            TalkEvent.EchoGuardOver -> onEchoGuardOver()
            // End Talk ends at once, mid-word, no goodbye (spec §6 step 8).
            TalkEvent.EndPressed -> end(TalkEffect.End)
            TalkEvent.SessionLost -> onSessionLost()
            is TalkEvent.Quiet -> onQuiet(event)
            TalkEvent.TimeUp -> onTimeUp()
            TalkEvent.Start -> listOf(TalkEffect.After(TalkConfig.Timing.CONNECT_TIMEOUT_MS, TalkEvent.ConnectTimeout))
            // No network on Talk (spec §8), counted from the tap, paused or not: back to the player, the lesson as it was.
            TalkEvent.ConnectTimeout ->
                if (everSetUp) emptyList() else end(TalkEffect.GiveUp(TalkConfig.Text.NO_INTERNET), tone = true)
            is TalkEvent.TurnTimeout -> onTurnTimeout(event)
            TalkEvent.TryAgain -> onTryAgain()
            is TalkEvent.NoSignalOver ->
                if (noSignal && event.outage == outageGeneration) end(TalkEffect.End, tone = true) else emptyList()
            TalkEvent.ResumeRefused -> onResumeRefused()
            TalkEvent.LimitReached -> onLimit()
            TalkEvent.LimitRetry -> onLimitRetry()
            TalkEvent.FocusPaused -> pause()
            TalkEvent.FocusDucked -> duck()
            TalkEvent.FocusBack -> focusBack()
            is TalkEvent.PauseOver ->
                if (focusLost && event.pause == pauseGeneration) end(TalkEffect.End) else emptyList()
            TalkEvent.FocusGone -> end(TalkEffect.EndLessonPaused)
        }
    }

    /**
     * Ends the Talk with [how], after the "no connection" tone when [tone]. While focus is lost
     * there is no tone, and the lesson comes back only with focus (spec §10), except when
     * another media app took over.
     */
    private fun end(how: TalkEffect, tone: Boolean = false): List<TalkEffect> {
        ended = true
        if (!focusLost || how == TalkEffect.EndLessonPaused) {
            return listOfNotNull(if (tone) TalkEffect.PlayNoConnection else null, how)
        }
        return listOf(
            when (how) {
                is TalkEffect.GiveUp -> TalkEffect.EndWhenFocusBack(how.note, asBefore = true)
                is TalkEffect.EndWithNote -> TalkEffect.EndWhenFocusBack(how.note)
                else -> TalkEffect.EndWhenFocusBack()
            },
        )
    }

    // ---- Anna's turn and Your turn ----

    private fun onDrained(): List<TalkEffect> {
        if (tail != AnnaTail.Draining) return emptyList()
        // Her goodbye has played: the Talk is over (spec §6 step 7).
        if (timeUpSent || isGoodbye(annaLine)) return end(TalkEffect.End)
        tail = AnnaTail.EchoGuard
        return listOf(TalkEffect.After(TalkConfig.Timing.ECHO_GUARD_MS, TalkEvent.EchoGuardOver))
    }

    private fun onEchoGuardOver(): List<TalkEffect> {
        if (tail != AnnaTail.EchoGuard) return emptyList()
        tail = AnnaTail.None
        return if (capReached) sendTimeUp() else startYourTurn()
    }

    /** The turn beep and **You**, with the quiet timer for this one of Your turns (spec §6 step 6). */
    private fun startYourTurn(): List<TalkEffect> {
        phase = TurnState.You
        silence = 0f
        learnerSpoke = false
        quietGeneration++
        // She answered "wait": the learner asked for time, so no nudge (spec §6 step 6).
        if (annaLine.trim() == TalkConfig.Text.WAIT_REPLY) nudgeSpent = true
        return listOfNotNull(
            TalkEffect.PlayBeep,
            TalkEffect.OpenMic,
            if (nudgeSpent) null else TalkEffect.After(TalkConfig.Timing.QUIET_MS, TalkEvent.Quiet(quietGeneration)),
        )
    }

    /**
     * Begins a Turn with control text (spec §4): Thinking until her first audio, with the same
     * 8 s timeout and retry as a learner's Turn (spec §8), so none can sit in Thinking for ever.
     */
    private fun beginControlTurn(text: String): List<TalkEffect> {
        phase = TurnState.Thinking
        inTurn = true
        retried = false
        learnerSpeaking = false
        controlText = text
        return sendControl(text)
    }

    /** Sends [text] with its Turn timeout. Every text but the nudge starts a new session's context afresh. */
    private fun sendControl(text: String): List<TalkEffect> {
        if (text != TalkConfig.Text.QUIET) contextLost = false
        freshExchange()
        return listOf(TalkEffect.SendText(text), armTurnTimeout())
    }

    private fun sendOpening(): List<TalkEffect> {
        opened = true
        return beginControlTurn(opening)
    }

    private fun sendTimeUp(): List<TalkEffect> {
        timeUpSent = true
        return beginControlTurn(TalkConfig.Text.TIME_UP)
    }

    /** A new session without the Talk so far: Anna says sorry and asks a new question (spec §8). */
    private fun cameBack(): List<TalkEffect> = listOf(TalkEffect.CloseMic) + beginControlTurn(TalkConfig.Text.CAME_BACK)

    /** ~9 s of silence in Your turn: one nudge, then she waits silently (spec §6 step 6). */
    private fun onQuiet(event: TalkEvent.Quiet): List<TalkEffect> {
        if (!listening || event.turn != quietGeneration || learnerSpoke || nudgeSpent) return emptyList()
        nudgeSpent = true
        return listOf(TalkEffect.CloseMic) + beginControlTurn(TalkConfig.Text.QUIET)
    }

    /** The current Turn finishes first; a turn of Yours with no speech yet isn't one (spec §6 step 9). */
    private fun onTimeUp(): List<TalkEffect> {
        capReached = true
        return if (listening && !learnerSpoke) listOf(TalkEffect.CloseMic) + sendTimeUp() else emptyList()
    }

    /** Her answer to "bye" (spec §4); "By the way" is left to the model. */
    private fun isGoodbye(line: String) = line.trim().startsWith("Bye!") && line.contains("Drive safe")

    // ---- Paused and No signal ----

    /**
     * What Paused and No signal share: the mic closes, the Turn under way is given up (nothing of
     * it is resent), and no quiet or Turn timer from before fires after.
     */
    private fun interrupt(): List<TalkEffect> {
        awaitingResume = true
        inTurn = false
        learnerSpeaking = false
        resendOnSetup = false
        retried = false
        tail = AnnaTail.None
        silence = 0f
        quietGeneration++
        turnTimeoutGeneration++
        return listOf(TalkEffect.CloseMic)
    }

    /**
     * The Turns go on once focus is back, No signal is over and the session is up: the opening
     * if it hasn't gone out, time-up after the cap, came-back in a new session, else the turn
     * beep and Your turn; Anna doesn't repeat herself (spec §8, §10).
     */
    private fun resumeTurns(): List<TalkEffect> {
        if (!awaitingResume || interrupted || !sessionUp) return emptyList()
        awaitingResume = false
        return when {
            !opened -> sendOpening()
            capReached -> sendTimeUp()
            contextLost -> cameBack()
            else -> startYourTurn()
        }
    }

    /**
     * Transient focus loss (a call, "Hi BYD"): Anna stops mid-line, the mic closes, an
     * utterance in progress or an answer still arriving is dropped. Anna's latest line stays.
     */
    private fun pause(): List<TalkEffect> {
        if (focusLost) return emptyList()
        val effects = mutableListOf<TalkEffect>(TalkEffect.StopAnna)
        if (learnerSpeaking) {
            // Close the activity; the answer to the cut-off words is dropped.
            effects += TalkEffect.EndActivity
            droppingAnswer = true
        } else if (answerExpected) {
            droppingAnswer = tail == AnnaTail.None
        }
        focusLost = true
        effects += interrupt()
        pauseGeneration++
        effects += TalkEffect.After(TalkConfig.Timing.PAUSE_MAX_MS, TalkEvent.PauseOver(pauseGeneration))
        return effects
    }

    private fun duck(): List<TalkEffect> {
        if (ducked) return emptyList()
        ducked = true
        return listOf(TalkEffect.Volume(TalkConfig.Audio.DUCK_VOLUME))
    }

    private fun focusBack(): List<TalkEffect> {
        val effects = mutableListOf<TalkEffect>()
        if (ducked) {
            ducked = false
            effects += TalkEffect.Volume(1f)
        }
        if (!focusLost) return effects
        focusLost = false
        // No signal began during the pause: its tone now, as focus is back.
        if (noSignal && toneOwed) effects += TalkEffect.PlayNoConnection
        toneOwed = false
        return effects + resumeTurns()
    }

    /** The tone, the mic closed, Anna's line kept; reconnecting in the background (spec §8). */
    private fun noSignal(): List<TalkEffect> {
        noSignal = true
        reopening = false
        outageGeneration++
        val tone = if (focusLost) emptyList() else listOf(TalkEffect.PlayNoConnection)
        toneOwed = focusLost
        return tone + interrupt() + listOf(
            TalkEffect.After(TalkConfig.Timing.NO_SIGNAL_MS, TalkEvent.NoSignalOver(outageGeneration)),
            TalkEffect.After(TalkConfig.Timing.RECONNECT_GAP_MS, TalkEvent.TryAgain),
        )
    }

    // ---- Failed Turns and reconnecting ----

    private fun armTurnTimeout(): TalkEffect {
        firstAudioCame = false
        turnTimeoutGeneration++
        return TalkEffect.After(TalkConfig.Timing.TURN_TIMEOUT_MS, TalkEvent.TurnTimeout(turnTimeoutGeneration))
    }

    /** No first audio [TalkConfig.Timing.TURN_TIMEOUT_MS] after the Turn went out: it has failed (spec §8). */
    private fun onTurnTimeout(event: TalkEvent.TurnTimeout): List<TalkEffect> {
        if (event.turn != turnTimeoutGeneration || firstAudioCame || phase != TurnState.Thinking) return emptyList()
        if (reopening || interrupted) return emptyList()
        return failTurn()
    }

    /** A failed Turn gets one silent retry; a second failure is No signal. */
    private fun failTurn(): List<TalkEffect> = if (retried) noSignal() else retry()

    /** Reopen and resend the Turn once the next session is set up. */
    private fun retry(): List<TalkEffect> {
        retried = true
        resendOnSetup = true
        return reopen()
    }

    /**
     * Opens the next session, the old one closed first: resuming with the handle (spec §3), or
     * else a new session without the Talk so far.
     */
    private fun reopen(): List<TalkEffect> {
        goAwayPending = false
        reopening = true
        sessionUp = false
        val resume = handle ?: run {
            contextLost = true
            return listOf(TalkEffect.NewSession)
        }
        return listOf(TalkEffect.Reconnect(resume))
    }

    /**
     * Before any session was set up, or in No signal, the app tries again after a short gap. A
     * close mid-Turn is a failed Turn. A close between Turns resumes silently with the handle
     * (spec §3); with no handle, or before the resumed session is set up, it is No signal.
     */
    private fun onSessionLost(): List<TalkEffect> {
        sessionUp = false
        if (!everSetUp || noSignal) {
            reopening = false
            return listOf(TalkEffect.After(TalkConfig.Timing.RECONNECT_GAP_MS, TalkEvent.TryAgain))
        }
        if (inTurn) {
            reopening = false
            return failTurn()
        }
        if (handle == null || reopening) return noSignal()
        return reopen()
    }

    private fun onTryAgain(): List<TalkEffect> = when {
        !everSetUp -> listOf(TalkEffect.NewSession)
        noSignal && !reopening -> reopen()
        else -> emptyList()
    }

    /** The server refused the handle (spec §8): a new session, silently, with the same setup. */
    private fun onResumeRefused(): List<TalkEffect> {
        if (!reopening) return onSessionLost()
        handle = null
        contextLost = true
        return listOf(TalkEffect.NewSession)
    }

    /**
     * Gemini's free tier refused (spec §8), never told as the network. Before any session was set
     * up: the tone and back to the player with the note, the lesson as it was. Mid-Talk: the
     * screen stays as it is and one retry goes out after [TalkConfig.Timing.LIMIT_RETRY_MS]; refused
     * again, the Talk ends with the note and the lesson resumes by the usual rule.
     */
    private fun onLimit(): List<TalkEffect> {
        if (!everSetUp) return end(TalkEffect.GiveUp(TalkConfig.Text.TALK_LIMIT), tone = true)
        if (limitRetried) return end(TalkEffect.EndWithNote(TalkConfig.Text.TALK_LIMIT))
        limitRetried = true
        // Held as a reopen: a late answer is dropped and timers wait for the retry.
        reopening = true
        sessionUp = false
        return listOf(TalkEffect.After(TalkConfig.Timing.LIMIT_RETRY_MS, TalkEvent.LimitRetry))
    }

    /** The wait after a limit refusal is over: reopen, resending a Turn under way. */
    private fun onLimitRetry(): List<TalkEffect> {
        if (!limitRetried || !reopening) return emptyList()
        resendOnSetup = inTurn
        return reopen()
    }

    private fun onSetUp(): List<TalkEffect> {
        sessionUp = true
        if (!everSetUp) {
            everSetUp = true
            return resumeTurns()
        }
        if (!reopening) return resumeTurns()
        return onReopened()
    }

    /**
     * The next session is set up. A resumed one keeps the Talk, so nothing is sent unless a Turn
     * failed (spec §3, §8); a new one hears that the connection came back. Back from No signal
     * (or a pause), the Turns go on.
     */
    private fun onReopened(): List<TalkEffect> {
        reopening = false
        limitRetried = false
        noSignal = false
        droppingAnswer = false
        if (resendOnSetup) {
            resendOnSetup = false
            return resendTurn()
        }
        if (awaitingResume) return resumeTurns()
        if (contextLost && opened) return cameBack()
        return emptyList()
    }

    /**
     * The failed Turn once more: its control text, or the utterance as one activity. In a new
     * session the utterance (or a nudge) would land in an empty Talk, so Anna hears that the
     * connection came back instead (spec §8).
     */
    private fun resendTurn(): List<TalkEffect> {
        val text = controlText
        if (contextLost && (text == null || text == TalkConfig.Text.QUIET)) return cameBack()
        if (text != null) return sendControl(text)
        freshExchange()
        val resend = listOf(TalkEffect.StartActivity, TalkEffect.SendAudio(utterance))
        // Still speaking: the rest goes out live from here.
        if (phase == TurnState.You) return resend
        return resend + TalkEffect.EndActivity + armTurnTimeout()
    }

    /** Reconnect silently with the newest handle now, or once the current Turn completes. */
    private fun reconnectBetweenTurns(): List<TalkEffect> {
        if (inTurn || handle == null) {
            goAwayPending = true
            return emptyList()
        }
        return reopen()
    }

    // ---- Server and mic ----

    private fun onServer(event: LiveEvent): List<TalkEffect> = when (event) {
        LiveEvent.SetupComplete -> onSetUp()
        // An answer that arrives after its Turn failed is dropped, never played (spec §8).
        is LiveEvent.Audio -> if (!droppingAnswer && !reopening && answerExpected) {
            firstAudioCame = true
            phase = TurnState.Anna
            listOf(TalkEffect.Play(event.pcm))
        } else {
            emptyList()
        }
        is LiveEvent.LearnerSaid -> {
            learnerNow.append(event.text)
            emptyList()
        }
        is LiveEvent.AnnaSaid -> if (droppingAnswer) emptyList() else {
            annaNow.append(event.text)
            annaLine = if (lineDone) event.text.trim() else (annaLine + event.text).trimStart()
            lineDone = false
            emptyList()
        }
        LiveEvent.TurnComplete -> onTurnComplete()
        is LiveEvent.ResumptionHandle -> {
            handle = event.handle
            emptyList()
        }
        LiveEvent.GoAway -> reconnectBetweenTurns()
        else -> emptyList()
    }

    private fun onTurnComplete(): List<TalkEffect> {
        lineDone = true
        inTurn = false
        if (!droppingAnswer) keepExchange()
        val drain = if (droppingAnswer) {
            droppingAnswer = false
            emptyList()
        } else {
            retried = false
            // Anna is done talking once her audio has played out (spec §6 step 2).
            if (answerExpected) {
                tail = AnnaTail.Draining
                listOf(TalkEffect.AwaitDrain)
            } else {
                emptyList()
            }
        }
        // A goAway that came mid-Turn is handled now the Turn is complete (spec §3).
        return drain + if (goAwayPending) reconnectBetweenTurns() else emptyList()
    }

    private fun keepExchange() {
        val anna = annaNow.toString().replace(Regex("\\s+"), " ").trim()
        val learner = learnerNow.toString().replace(Regex("\\s+"), " ").trim()
        if (anna.isNotEmpty()) said += SaidTurn(learner.ifEmpty { null }, anna)
        freshExchange()
    }

    private fun onMic(event: MicEvent): List<TalkEffect> {
        if (!listening) return emptyList()
        return when (event) {
            is MicEvent.SpeechStarted -> {
                learnerSpeaking = true
                inTurn = true
                learnerSpoke = true
                nudgeSpent = false
                retried = false
                controlText = null
                // The new activity takes over from an answer still being dropped.
                droppingAnswer = false
                heard.reset()
                heard.write(event.preRoll)
                freshExchange()
                listOf(TalkEffect.StartActivity, TalkEffect.SendAudio(event.preRoll))
            }
            is MicEvent.Audio -> {
                heard.write(event.pcm)
                // Reopening mid-speech: all of it goes out once the new session is up.
                if (resendOnSetup) emptyList() else listOf(TalkEffect.SendAudio(event.pcm))
            }
            is MicEvent.Silence -> {
                silence = event.fraction
                emptyList()
            }
            MicEvent.SpeechEnded -> {
                phase = TurnState.Thinking
                learnerSpeaking = false
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

/** After Anna's complete turn: [Draining] waits for her audio to play out, then [EchoGuard] for its echo to die. */
private enum class AnnaTail { None, Draining, EchoGuard }

/** What the Talk screen shows (spec §7). Paused shows over No signal while both hold. */
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

    /** [TalkConfig.Timing.QUIET_MS] have passed since Your turn number [turn] began (asked for with [TalkEffect.After]). */
    data class Quiet(val turn: Int) : TalkEvent

    /** [TalkConfig.Timing.TALK_CAP_MS] of wall-clock time since the Talk started. */
    data object TimeUp : TalkEvent

    /** The Talk button was pressed and the first session is opening. */
    data object Start : TalkEvent

    /** [TalkConfig.Timing.CONNECT_TIMEOUT_MS] since [Start]. */
    data object ConnectTimeout : TalkEvent

    /** [TalkConfig.Timing.TURN_TIMEOUT_MS] since Turn number [turn] went out: the tick, its control text, or its resend. */
    data class TurnTimeout(val turn: Int) : TalkEvent

    /** Try connecting again, [TalkConfig.Timing.RECONNECT_GAP_MS] after a failed attempt. */
    data object TryAgain : TalkEvent

    /** [TalkConfig.Timing.NO_SIGNAL_MS] since No signal [outage] began. */
    data class NoSignalOver(val outage: Int) : TalkEvent

    /** The server refused the resumption handle (see [LiveProtocol.classifyLoss]). */
    data object ResumeRefused : TalkEvent

    /** Gemini's free tier refused: a quota or rate limit (see [LiveProtocol.classifyLoss]). */
    data object LimitReached : TalkEvent

    /** [TalkConfig.Timing.LIMIT_RETRY_MS] since a limit refusal mid-Talk: try once more. */
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

    /**
     * End the Talk like [End], but give the lesson back only once audio focus returns: by the
     * usual rule, or as it was before the tap when [asBefore]. [note] shows on the player.
     */
    data class EndWhenFocusBack(val note: String? = null, val asBefore: Boolean = false) : TalkEffect

    /** End the Talk like [End] and show [note] on the player; the lesson resumes by the usual rule. */
    data class EndWithNote(val note: String) : TalkEffect

    /** End the Talk like [End], but leave the lesson paused: another media app has taken over. */
    data object EndLessonPaused : TalkEffect
}
