package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §8 (all but limits): a late or lost Turn, No signal, a refused handle, no network on Talk. */
class NetworkDropTest {
    private val talk = TalkTurns()

    private fun server(e: LiveEvent) = talk.on(TalkEvent.Server(e))
    private fun mic(e: MicEvent) = talk.on(TalkEvent.Mic(e))

    /** The timer [effects] asked for whose event is a [T], if any. */
    private inline fun <reified T : TalkEvent> timer(effects: List<TalkEffect>): TalkEffect.After? =
        effects.filterIsInstance<TalkEffect.After>().singleOrNull { it.event is T }

    /** Opening and Anna's hello, with a handle: the learner's turn begins. */
    private fun toYou() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.AnnaSaid("Hi! How is your day?"))
        server(LiveEvent.ResumptionHandle("h1"))
        server(LiveEvent.TurnComplete)
        talk.on(TalkEvent.PlaybackDrained)
        talk.on(TalkEvent.EchoGuardOver)
    }

    /** The learner says something; returns the effects of the tick. */
    private fun learnerSays(): List<TalkEffect> {
        mic(MicEvent.SpeechStarted(byteArrayOf(1, 2)))
        mic(MicEvent.Audio(byteArrayOf(3)))
        return mic(MicEvent.SpeechEnded)
    }

    /** The learner's Turn fails by timeout and is retried; returns the retry's effects. */
    private fun failedOnce(): List<TalkEffect> {
        toYou()
        return talk.on(timer<TalkEvent.TurnTimeout>(learnerSays())!!.event)
    }

    /** The retry fails too: No signal. Returns its effects. */
    private fun toNoSignal(): List<TalkEffect> {
        failedOnce()
        server(LiveEvent.SetupComplete)
        return talk.on(TalkEvent.SessionLost)
    }

    @Test
    fun `no first audio 8 s after the tick retries once silently, resending the utterance as one activity`() {
        toYou()
        val t = timer<TalkEvent.TurnTimeout>(learnerSays())!!
        assertEquals(8_000L, t.ms)
        assertEquals(listOf(TalkEffect.Reconnect("h1")), talk.on(t.event))
        assertEquals(TurnState.Thinking, talk.state)
        val resent = server(LiveEvent.SetupComplete)
        assertEquals(
            listOf(TalkEffect.StartActivity, TalkEffect.SendAudio(byteArrayOf(1, 2, 3)), TalkEffect.EndActivity),
            resent.filter { it !is TalkEffect.After },
        )
        assertEquals(8_000L, timer<TalkEvent.TurnTimeout>(resent)!!.ms)
    }

    @Test
    fun `an answer that comes in time cancels the Turn timeout`() {
        toYou()
        val t = timer<TalkEvent.TurnTimeout>(learnerSays())!!
        server(LiveEvent.Audio(byteArrayOf(9)))
        assertEquals(emptyList<TalkEffect>(), talk.on(t.event))
        assertEquals(TurnState.Anna, talk.state)
    }

    @Test
    fun `an answer that arrives after its Turn failed is dropped, never played`() {
        failedOnce()
        assertEquals(emptyList<TalkEffect>(), server(LiveEvent.Audio(byteArrayOf(9))))
        assertEquals(TurnState.Thinking, talk.state)
    }

    @Test
    fun `the socket dying mid-Turn is a failed Turn too`() {
        toYou()
        learnerSays()
        assertEquals(listOf(TalkEffect.Reconnect("h1")), talk.on(TalkEvent.SessionLost))
        assertEquals(
            listOf(TalkEffect.StartActivity, TalkEffect.SendAudio(byteArrayOf(1, 2, 3)), TalkEffect.EndActivity),
            server(LiveEvent.SetupComplete).filter { it !is TalkEffect.After },
        )
    }

    @Test
    fun `the socket dying while the learner speaks resends all of it once the new session is up`() {
        toYou()
        mic(MicEvent.SpeechStarted(byteArrayOf(1, 2)))
        talk.on(TalkEvent.SessionLost)
        assertEquals(emptyList<TalkEffect>(), mic(MicEvent.Audio(byteArrayOf(3))))
        assertEquals(listOf(TalkEffect.StartActivity, TalkEffect.SendAudio(byteArrayOf(1, 2, 3))), server(LiveEvent.SetupComplete))
        assertEquals(listOf(TalkEffect.SendAudio(byteArrayOf(4))), mic(MicEvent.Audio(byteArrayOf(4))))
        assertEquals(TalkEffect.EndActivity, mic(MicEvent.SpeechEnded).first())
    }

    @Test
    fun `the retry failing gives the tone and No signal, mic closed, Anna's line kept, reconnecting`() {
        val effects = toNoSignal()
        assertEquals(TurnState.NoSignal, talk.state)
        assertEquals("Hi! How is your day?", talk.annaLine)
        assertEquals(TalkEffect.PlayNoConnection, effects[0])
        assertTrue(TalkEffect.CloseMic in effects)
        assertEquals(60_000L, timer<TalkEvent.NoSignalOver>(effects)!!.ms)
        assertTrue(timer<TalkEvent.TryAgain>(effects) != null)
    }

    @Test
    fun `the retry timing out gives No signal too`() {
        failedOnce()
        val again = timer<TalkEvent.TurnTimeout>(server(LiveEvent.SetupComplete))!!
        assertEquals(TalkEffect.PlayNoConnection, talk.on(again.event).first())
        assertEquals(TurnState.NoSignal, talk.state)
    }

    @Test
    fun `in No signal the app keeps reconnecting in the background`() {
        val t = timer<TalkEvent.TryAgain>(toNoSignal())!!
        assertEquals(listOf(TalkEffect.Reconnect("h1")), talk.on(t.event))
        val next = timer<TalkEvent.TryAgain>(talk.on(TalkEvent.SessionLost))!!
        assertEquals(TurnState.NoSignal, talk.state)
        assertEquals(listOf(TalkEffect.Reconnect("h1")), talk.on(next.event))
    }

    @Test
    fun `back from No signal the turn beep and Your turn, and Anna doesn't repeat her question`() {
        talk.on(timer<TalkEvent.TryAgain>(toNoSignal())!!.event)
        val back = server(LiveEvent.SetupComplete)
        assertEquals(TurnState.You, talk.state)
        assertEquals(listOf(TalkEffect.PlayBeep, TalkEffect.OpenMic), back.take(2))
        assertTrue(back.none { it is TalkEffect.SendText })
    }

    @Test
    fun `60 s in No signal plays the tone and ends the Talk`() {
        val over = timer<TalkEvent.NoSignalOver>(toNoSignal())!!
        assertEquals(listOf(TalkEffect.PlayNoConnection, TalkEffect.End), talk.on(over.event))
        assertEquals(TurnState.Ended, talk.state)
    }

    @Test
    fun `an old No signal timer doesn't end a later outage early`() {
        val over = timer<TalkEvent.NoSignalOver>(toNoSignal())!!
        talk.on(timer<TalkEvent.TryAgain>(talk.on(TalkEvent.SessionLost))!!.event)
        server(LiveEvent.SetupComplete)
        server(LiveEvent.ResumptionHandle("h2"))
        talk.on(TalkEvent.SessionLost)
        talk.on(TalkEvent.SessionLost)
        assertEquals(TurnState.NoSignal, talk.state)
        assertEquals(emptyList<TalkEffect>(), talk.on(over.event))
    }

    @Test
    fun `End Talk works in No signal`() {
        toNoSignal()
        assertEquals(listOf(TalkEffect.End), talk.on(TalkEvent.EndPressed))
    }

    @Test
    fun `the 30-min cap reached in No signal sends time-up once the connection is back`() {
        talk.on(timer<TalkEvent.TryAgain>(toNoSignal())!!.event)
        talk.on(TalkEvent.TimeUp)
        assertEquals(listOf(TalkEffect.SendText("(Time is up.)")), server(LiveEvent.SetupComplete).filterIsInstance<TalkEffect.SendText>())
        assertEquals(TurnState.Thinking, talk.state)
    }

    @Test
    fun `a close between Turns whose reconnect fails goes to No signal, not the end`() {
        toYou()
        talk.on(TalkEvent.SessionLost)
        assertEquals(TalkEffect.PlayNoConnection, talk.on(TalkEvent.SessionLost).first())
        assertEquals(TurnState.NoSignal, talk.state)
    }

    @Test
    fun `a refused handle opens a new session silently, then says the connection came back`() {
        toYou()
        talk.on(TalkEvent.SessionLost)
        assertEquals(listOf(TalkEffect.NewSession), talk.on(TalkEvent.ResumeRefused))
        assertEquals(TurnState.You, talk.state)
        assertEquals(listOf(TalkEffect.CloseMic, TalkEffect.SendText("(The connection came back.)"), TalkEffect.After(8_000, TalkEvent.TurnTimeout(2))), server(LiveEvent.SetupComplete))
        assertEquals(TurnState.Thinking, talk.state)
        assertEquals(TalkEffect.Play(byteArrayOf(5)), server(LiveEvent.Audio(byteArrayOf(5))).single())
    }

    @Test
    fun `a refused handle in No signal comes back the same way`() {
        talk.on(timer<TalkEvent.TryAgain>(toNoSignal())!!.event)
        assertEquals(listOf(TalkEffect.NewSession), talk.on(TalkEvent.ResumeRefused))
        assertEquals(TurnState.NoSignal, talk.state)
        assertEquals(listOf(TalkEffect.SendText("(The connection came back.)")), server(LiveEvent.SetupComplete).filterIsInstance<TalkEffect.SendText>())
        assertEquals(TurnState.Thinking, talk.state)
    }

    @Test
    fun `no network on Talk shows Connecting for up to 5 s, then the tone and No internet`() {
        val t = timer<TalkEvent.ConnectTimeout>(talk.on(TalkEvent.Start))!!
        assertEquals(5_000L, t.ms)
        assertEquals(TurnState.Connecting, talk.state)
        assertEquals(listOf(TalkEffect.PlayNoConnection, TalkEffect.GiveUp("No internet")), talk.on(t.event))
        assertEquals(TurnState.Ended, talk.state)
    }

    @Test
    fun `a failed first connection is tried again while Connecting`() {
        talk.on(TalkEvent.Start)
        val again = timer<TalkEvent.TryAgain>(talk.on(TalkEvent.SessionLost))!!
        assertEquals(TurnState.Connecting, talk.state)
        assertEquals(listOf(TalkEffect.NewSession), talk.on(again.event))
        assertEquals(listOf(TalkEffect.SendText("(The learner pressed Talk. Start the Talk.)"), TalkEffect.After(8_000, TalkEvent.TurnTimeout(1))), server(LiveEvent.SetupComplete))
    }

    @Test
    fun `the connect timeout does nothing once connected`() {
        val t = timer<TalkEvent.ConnectTimeout>(talk.on(TalkEvent.Start))!!
        server(LiveEvent.SetupComplete)
        assertEquals(emptyList<TalkEffect>(), talk.on(t.event))
    }

    @Test
    fun `an opening with no first audio in 8 s is retried, then No signal, never stuck in Thinking`() {
        val first = timer<TalkEvent.TurnTimeout>(server(LiveEvent.SetupComplete))!!
        assertEquals(8_000L, first.ms)
        assertEquals(listOf(TalkEffect.NewSession), talk.on(first.event))
        val resent = server(LiveEvent.SetupComplete)
        assertEquals(listOf(TalkEffect.SendText(TalkConfig.Text.OPENING)), resent.filterIsInstance<TalkEffect.SendText>())
        talk.on(timer<TalkEvent.TurnTimeout>(resent)!!.event)
        assertEquals(TurnState.NoSignal, talk.state)
    }

    @Test
    fun `a lost time-up is retried, then No signal ends the Talk`() {
        toYou()
        val sent = talk.on(TalkEvent.TimeUp)
        assertEquals(listOf(TalkEffect.Reconnect("h1")), talk.on(timer<TalkEvent.TurnTimeout>(sent)!!.event))
        val resent = server(LiveEvent.SetupComplete)
        assertEquals(listOf(TalkEffect.SendText(TalkConfig.Text.TIME_UP)), resent.filterIsInstance<TalkEffect.SendText>())
        val noSignal = talk.on(timer<TalkEvent.TurnTimeout>(resent)!!.event)
        assertEquals(TurnState.NoSignal, talk.state)
        assertEquals(listOf(TalkEffect.PlayNoConnection, TalkEffect.End), talk.on(timer<TalkEvent.NoSignalOver>(noSignal)!!.event))
    }

    @Test
    fun `came-back gets the 8 s Turn timeout`() {
        toYou()
        talk.on(TalkEvent.SessionLost)
        talk.on(TalkEvent.ResumeRefused)
        assertEquals(8_000L, timer<TalkEvent.TurnTimeout>(server(LiveEvent.SetupComplete))!!.ms)
    }

    @Test
    fun `a failed Turn with no handle opens a new session and says the connection came back`() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.TurnComplete)
        talk.on(TalkEvent.PlaybackDrained)
        talk.on(TalkEvent.EchoGuardOver)
        val t = timer<TalkEvent.TurnTimeout>(learnerSays())!!
        assertEquals(listOf(TalkEffect.NewSession), talk.on(t.event))
        val resent = server(LiveEvent.SetupComplete)
        assertEquals(listOf(TalkEffect.SendText(TalkConfig.Text.CAME_BACK)), resent.filterIsInstance<TalkEffect.SendText>())
        assertTrue(resent.none { it is TalkEffect.SendAudio || it == TalkEffect.StartActivity })
    }
}
