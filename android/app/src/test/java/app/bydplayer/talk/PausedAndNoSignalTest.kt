package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §8 and §10 together: focus lost (Paused) and the connection lost (No signal) are two
 * separate conditions that can hold at once, each with its own 60 s timer.
 */
class PausedAndNoSignalTest {
    private val talk = TalkTurns()

    private fun server(e: LiveEvent) = talk.on(TalkEvent.Server(e))

    private inline fun <reified T : TalkEvent> timer(effects: List<TalkEffect>): TalkEffect.After? =
        effects.filterIsInstance<TalkEffect.After>().singleOrNull { it.event is T }

    /** Opening and Anna's hello, with a handle: the learner's turn begins. */
    private fun toYou() {
        talk.on(TalkEvent.Start)
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.AnnaSaid("Hi! How is your day?"))
        server(LiveEvent.ResumptionHandle("h1"))
        server(LiveEvent.TurnComplete)
        talk.on(TalkEvent.PlaybackDrained)
        talk.on(TalkEvent.EchoGuardOver)
    }

    /** Paused in Your turn, then the session and its silent reconnect are lost: No signal too. */
    private fun pausedThenNoSignal(): List<TalkEffect> {
        toYou()
        talk.on(TalkEvent.FocusPaused)
        assertEquals(listOf(TalkEffect.Reconnect("h1")), talk.on(TalkEvent.SessionLost))
        return talk.on(TalkEvent.SessionLost)
    }

    /** The learner's Turn fails twice: No signal. Returns its effects. */
    private fun toNoSignal(): List<TalkEffect> {
        toYou()
        talk.on(TalkEvent.Mic(MicEvent.SpeechStarted(byteArrayOf(1))))
        talk.on(TalkEvent.Mic(MicEvent.SpeechEnded))
        talk.on(TalkEvent.SessionLost)
        return talk.on(TalkEvent.SessionLost)
    }

    @Test
    fun `a pause while connecting still gives up after 5 s, and the lesson waits for focus`() {
        val connect = timer<TalkEvent.ConnectTimeout>(talk.on(TalkEvent.Start))!!
        talk.on(TalkEvent.FocusPaused)
        assertEquals(TurnState.Paused, talk.state)
        // No tone while focus is lost; the note shows and the lesson comes back as it was once focus is back.
        assertEquals(listOf(TalkEffect.EndWhenFocusBack(TalkConfig.Text.NO_INTERNET, asBefore = true)), talk.on(connect.event))
        assertEquals(TurnState.Ended, talk.state)
    }

    @Test
    fun `connection attempts go on while paused in Connecting`() {
        talk.on(TalkEvent.Start)
        talk.on(TalkEvent.FocusPaused)
        val again = timer<TalkEvent.TryAgain>(talk.on(TalkEvent.SessionLost))!!
        assertEquals(listOf(TalkEffect.NewSession), talk.on(again.event))
    }

    @Test
    fun `losing the connection while paused stays Paused, plays no tone, and keeps reconnecting`() {
        val effects = pausedThenNoSignal()
        assertEquals(TurnState.Paused, talk.state)
        assertTrue(TalkEffect.PlayNoConnection !in effects)
        assertEquals(60_000L, timer<TalkEvent.NoSignalOver>(effects)!!.ms)
        assertEquals(listOf(TalkEffect.Reconnect("h1")), talk.on(timer<TalkEvent.TryAgain>(effects)!!.event))
    }

    @Test
    fun `the connection back while paused opens Your turn only once focus is back`() {
        talk.on(timer<TalkEvent.TryAgain>(pausedThenNoSignal())!!.event)
        assertEquals(emptyList<TalkEffect>(), server(LiveEvent.SetupComplete))
        assertEquals(TurnState.Paused, talk.state)
        assertEquals(listOf(TalkEffect.PlayBeep, TalkEffect.OpenMic), talk.on(TalkEvent.FocusBack).take(2))
        assertEquals(TurnState.You, talk.state)
    }

    @Test
    fun `focus back while still in No signal gives the tone and No signal, not Your turn`() {
        pausedThenNoSignal()
        val back = talk.on(TalkEvent.FocusBack)
        assertEquals(TurnState.NoSignal, talk.state)
        assertTrue(TalkEffect.PlayNoConnection in back)
        assertTrue(TalkEffect.PlayBeep !in back && TalkEffect.OpenMic !in back)
    }

    @Test
    fun `a pause during No signal keeps the outage timer, and that end waits for focus`() {
        val over = timer<TalkEvent.NoSignalOver>(toNoSignal())!!
        assertEquals(TurnState.NoSignal, talk.state)
        talk.on(TalkEvent.FocusPaused)
        assertEquals(TurnState.Paused, talk.state)
        assertEquals(listOf(TalkEffect.EndWhenFocusBack()), talk.on(over.event))
        assertEquals(TurnState.Ended, talk.state)
    }

    @Test
    fun `the pause timer keeps running in No signal`() {
        toNoSignal()
        val pause = timer<TalkEvent.PauseOver>(talk.on(TalkEvent.FocusPaused))!!
        assertEquals(listOf(TalkEffect.EndWhenFocusBack()), talk.on(pause.event))
    }

    @Test
    fun `a pause during No signal doesn't stop reconnecting`() {
        val retry = timer<TalkEvent.TryAgain>(toNoSignal())!!
        talk.on(TalkEvent.FocusPaused)
        assertEquals(listOf(TalkEffect.Reconnect("h1")), talk.on(retry.event))
        assertEquals(emptyList<TalkEffect>(), server(LiveEvent.SetupComplete))
        assertEquals(TurnState.Paused, talk.state)
        talk.on(TalkEvent.FocusBack)
        assertEquals(TurnState.You, talk.state)
    }

    @Test
    fun `the limit end while paused gives the lesson back once focus is back`() {
        toYou()
        talk.on(TalkEvent.FocusPaused)
        talk.on((talk.on(TalkEvent.LimitReached).single() as TalkEffect.After).event)
        assertEquals(listOf(TalkEffect.EndWhenFocusBack(TalkConfig.Text.TALK_LIMIT)), talk.on(TalkEvent.LimitReached))
    }
}
