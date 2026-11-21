package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec §3 "Resumption": a long Talk reconnects silently with the latest handle. */
class SilentReconnectTest {
    private val talk = TalkTurns()

    private fun server(e: LiveEvent) = talk.on(TalkEvent.Server(e))

    /** Opening and Anna's hello, with the handle that comes every Turn: the learner's turn begins. */
    private fun toYou(handle: String = "h1") {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.ResumptionHandle(handle))
        server(LiveEvent.TurnComplete)
        talk.on(TalkEvent.PlaybackDrained)
        talk.on(TalkEvent.EchoGuardOver)
    }

    @Test
    fun `goAway between Turns reconnects at once with the newest handle, screen unchanged`() {
        toYou(handle = "h1")
        server(LiveEvent.ResumptionHandle("h2"))
        assertEquals(listOf(TalkEffect.Reconnect("h2")), server(LiveEvent.GoAway))
        assertEquals(TurnState.You, talk.state)
    }

    @Test
    fun `goAway mid-Turn waits for that Turn to complete, then reconnects once`() {
        toYou(handle = "h1")
        talk.on(TalkEvent.Mic(MicEvent.SpeechStarted(byteArrayOf(1))))
        assertEquals(emptyList<TalkEffect>(), server(LiveEvent.GoAway))
        talk.on(TalkEvent.Mic(MicEvent.SpeechEnded))
        server(LiveEvent.Audio(byteArrayOf(2)))
        server(LiveEvent.ResumptionHandle("h2"))
        assertEquals(listOf(TalkEffect.AwaitDrain, TalkEffect.Reconnect("h2")), server(LiveEvent.TurnComplete))
        assertEquals(TurnState.Anna, talk.state)
        talk.on(TalkEvent.PlaybackDrained)
        assertEquals(listOf(TalkEffect.PlayBeep, TalkEffect.OpenMic), talk.on(TalkEvent.EchoGuardOver))
        server(LiveEvent.ResumptionHandle("h3"))
        talk.on(TalkEvent.Mic(MicEvent.SpeechStarted(byteArrayOf(1))))
        talk.on(TalkEvent.Mic(MicEvent.SpeechEnded))
        assertEquals(listOf(TalkEffect.AwaitDrain), server(LiveEvent.TurnComplete))
    }

    @Test
    fun `a socket close between Turns reconnects silently and the new session sends no opening`() {
        toYou(handle = "h1")
        assertEquals(listOf(TalkEffect.Reconnect("h1")), talk.on(TalkEvent.SessionLost))
        assertEquals(TurnState.You, talk.state)
        assertEquals(emptyList<TalkEffect>(), server(LiveEvent.SetupComplete))
        assertEquals(TurnState.You, talk.state)
    }

    @Test
    fun `a socket close while Anna's audio still plays out after her complete turn is between Turns`() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.ResumptionHandle("h1"))
        server(LiveEvent.TurnComplete)
        assertEquals(listOf(TalkEffect.Reconnect("h1")), talk.on(TalkEvent.SessionLost))
        assertEquals(TurnState.Anna, talk.state)
    }

    @Test
    fun `a socket close mid-Turn still ends the Talk until ticket 18`() {
        toYou(handle = "h1")
        talk.on(TalkEvent.Mic(MicEvent.SpeechStarted(byteArrayOf(1))))
        assertEquals(listOf(TalkEffect.End), talk.on(TalkEvent.SessionLost))
    }

    @Test
    fun `a close before the resumed session is set up doesn't loop reconnecting`() {
        toYou(handle = "h1")
        talk.on(TalkEvent.SessionLost)
        assertEquals(listOf(TalkEffect.End), talk.on(TalkEvent.SessionLost))
    }

    @Test
    fun `once resumed, a later close between Turns reconnects again`() {
        toYou(handle = "h1")
        talk.on(TalkEvent.SessionLost)
        server(LiveEvent.SetupComplete)
        server(LiveEvent.ResumptionHandle("h2"))
        assertEquals(listOf(TalkEffect.Reconnect("h2")), talk.on(TalkEvent.SessionLost))
    }

    @Test
    fun `goAway during the opening waits for Anna's hello to complete`() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.ResumptionHandle("h1"))
        assertEquals(emptyList<TalkEffect>(), server(LiveEvent.GoAway))
        server(LiveEvent.Audio(byteArrayOf(1)))
        assertEquals(listOf(TalkEffect.AwaitDrain, TalkEffect.Reconnect("h1")), server(LiveEvent.TurnComplete))
    }
}
