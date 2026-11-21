package app.bydplayer.talk

/** One Live connection as [LiveLink] drives it; [LiveSession] is the real one. */
interface LiveConnection {
    fun open()
    fun send(message: String)

    /** Closes the socket; no loss is reported for a close the app asked for. */
    fun close()
}

/**
 * The Talk's link to Gemini Live across reconnects (spec §3): holds the one current session and
 * swaps it for a resumed one with [open]. Two sessions are never open at once (the free tier caps
 * concurrent sessions): the old one is closed before the new one opens. Events and losses from a
 * replaced session are dropped, and messages sent before the current session's `setupComplete`
 * wait for it, so a learner who speaks during a reconnect isn't cut.
 *
 * [connect] builds a session whose callbacks may run on any thread; [post] moves them to the
 * thread every other call here is made on (the main thread in [TalkService]).
 */
class LiveLink(
    private val connect: (handle: String?, onEvent: (LiveEvent) -> Unit, onLost: (LiveLoss) -> Unit) -> LiveConnection,
    private val post: (() -> Unit) -> Unit,
    private val onEvent: (LiveEvent) -> Unit,
    private val onLost: (LiveLoss) -> Unit,
) {
    private var current: LiveConnection? = null
    private var generation = 0
    private var ready = false
    private val waiting = ArrayDeque<String>()

    /** Opens a new session, resuming with [handle] if given, after closing the current one. */
    fun open(handle: String? = null) {
        close()
        val mine = generation
        current = connect(
            handle,
            { e -> post { if (generation == mine) deliver(e) } },
            { loss -> post { if (generation == mine) lose(loss) } },
        ).also { it.open() }
    }

    /** Sends one client message built by [LiveProtocol], once the session is set up. */
    fun send(message: String) {
        val c = current ?: return
        if (ready) c.send(message) else waiting.addLast(message)
    }

    /** Closes the current session, if any. Nothing more from it is delivered. */
    fun close() {
        generation++
        ready = false
        waiting.clear()
        current?.close()
        current = null
    }

    private fun deliver(event: LiveEvent) {
        if (event == LiveEvent.SetupComplete && !ready) {
            ready = true
            while (waiting.isNotEmpty()) current?.send(waiting.removeFirst())
        }
        onEvent(event)
    }

    private fun lose(loss: LiveLoss) {
        generation++
        ready = false
        waiting.clear()
        current = null
        onLost(loss)
    }
}
