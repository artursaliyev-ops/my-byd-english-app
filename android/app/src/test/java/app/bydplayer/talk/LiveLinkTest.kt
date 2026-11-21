package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec §3 "Resumption": one Live session at a time, swapped silently on reconnect. */
class LiveLinkTest {
    private val log = mutableListOf<String>()
    private val sessions = mutableListOf<FakeSession>()
    private val events = mutableListOf<LiveEvent>()
    private var lost = 0

    inner class FakeSession(val handle: String?, val onEvent: (LiveEvent) -> Unit, val onLost: (LiveLoss) -> Unit) : LiveConnection {
        val sent = mutableListOf<String>()
        val name = "s${sessions.size + 1}"
        override fun open() { log += "open $name ${handle ?: "-"}" }
        override fun send(message: String) { sent += message }
        override fun close() { log += "close $name" }
    }

    private val link = LiveLink(
        connect = { handle, onEvent, onLost -> FakeSession(handle, onEvent, onLost).also { sessions += it } },
        post = { it() },
        onEvent = { events += it },
        onLost = { lost++ },
    )

    @Test
    fun `reconnecting closes the old session before the new one opens with the handle`() {
        link.open()
        link.open(handle = "h1")
        assertEquals(listOf("open s1 -", "close s1", "open s2 h1"), log)
    }

    @Test
    fun `events and losses from a replaced session are dropped`() {
        link.open()
        val old = sessions[0]
        link.open(handle = "h1")
        old.onEvent(LiveEvent.TurnComplete)
        old.onLost(LiveLoss.Network)
        sessions[1].onEvent(LiveEvent.SetupComplete)
        assertEquals(listOf<LiveEvent>(LiveEvent.SetupComplete), events)
        assertEquals(0, lost)
        sessions[1].onLost(LiveLoss.Network)
        assertEquals(1, lost)
    }

    @Test
    fun `messages sent before the session is set up wait for setupComplete, in order`() {
        link.open(handle = "h1")
        link.send("a")
        link.send("b")
        assertEquals(emptyList<String>(), sessions[0].sent)
        sessions[0].onEvent(LiveEvent.SetupComplete)
        link.send("c")
        assertEquals(listOf("a", "b", "c"), sessions[0].sent)
    }

    @Test
    fun `closing the link closes the session and nothing more is delivered`() {
        link.open()
        link.close()
        sessions[0].onEvent(LiveEvent.SetupComplete)
        sessions[0].onLost(LiveLoss.Network)
        assertEquals(listOf("open s1 -", "close s1"), log)
        assertEquals(emptyList<LiveEvent>(), events)
        assertEquals(0, lost)
    }
}
