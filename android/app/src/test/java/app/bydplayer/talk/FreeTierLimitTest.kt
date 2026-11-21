package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §8 "Free-tier limit": a quota or rate-limit refusal, told plainly, never as the network. */
class FreeTierLimitTest {
    private val talk = TalkTurns()

    private fun server(e: LiveEvent) = talk.on(TalkEvent.Server(e))

    @Test
    fun `refused at the start, the tone plays and the player shows the limit note with the lesson as it was`() {
        talk.on(TalkEvent.Start)
        assertEquals(
            listOf(TalkEffect.PlayNoConnection, TalkEffect.GiveUp("Talk limit reached. Try later.")),
            talk.on(TalkEvent.LimitReached),
        )
        assertEquals(TurnState.Ended, talk.state)
    }

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

    @Test
    fun `refused mid-Talk, it retries once after 5 s without showing No signal`() {
        toYou()
        val wait = talk.on(TalkEvent.LimitReached)
        val retry = wait.single() as TalkEffect.After
        assertEquals(5_000L, retry.ms)
        assertEquals(TurnState.You, talk.state)
        assertEquals(listOf(TalkEffect.Reconnect("h1")), talk.on(retry.event))
        assertEquals(TurnState.You, talk.state)
    }

    /** A refusal mid-Talk and its retry; returns the retry's effects. */
    private fun retried(): List<TalkEffect> {
        val retry = talk.on(TalkEvent.LimitReached).single() as TalkEffect.After
        return talk.on(retry.event)
    }

    @Test
    fun `refused again after the retry, the Talk ends with the limit note and the lesson resumes`() {
        toYou()
        retried()
        assertEquals(listOf(TalkEffect.EndWithNote("Talk limit reached. Try later.")), talk.on(TalkEvent.LimitReached))
        assertEquals(TurnState.Ended, talk.state)
    }

    @Test
    fun `a retry that gets through carries on, and a later refusal gets its own retry`() {
        toYou()
        retried()
        assertEquals(emptyList<TalkEffect>(), server(LiveEvent.SetupComplete))
        assertEquals(TurnState.You, talk.state)
        val again = talk.on(TalkEvent.LimitReached).single() as TalkEffect.After
        assertEquals(5_000L, again.ms)
    }

    @Test
    fun `refused while Anna is being asked, the retry resends the learner's utterance`() {
        toYou()
        talk.on(TalkEvent.Mic(MicEvent.SpeechStarted(byteArrayOf(1, 2))))
        talk.on(TalkEvent.Mic(MicEvent.SpeechEnded))
        assertEquals(listOf(TalkEffect.Reconnect("h1")), retried())
        assertEquals(TurnState.Thinking, talk.state)
        val resent = server(LiveEvent.SetupComplete).filter { it !is TalkEffect.After }
        assertEquals(listOf(TalkEffect.StartActivity, TalkEffect.SendAudio(byteArrayOf(1, 2)), TalkEffect.EndActivity), resent)
    }

    @Test
    fun `a limit refusal never shows No signal or the No internet note`() {
        talk.on(TalkEvent.Start)
        assertTrue(TalkEffect.GiveUp(TalkConfig.Text.NO_INTERNET) !in talk.on(TalkEvent.LimitReached))

        val mid = TalkTurns()
        mid.on(TalkEvent.Start)
        mid.on(TalkEvent.Server(LiveEvent.SetupComplete))
        mid.on(TalkEvent.Server(LiveEvent.ResumptionHandle("h1")))
        val effects = mutableListOf<TalkEffect>()
        val wait = mid.on(TalkEvent.LimitReached).also { effects += it }
        effects += mid.on((wait.single() as TalkEffect.After).event)
        // The retry waits out a Turn timeout and the reconnect gap without going to No signal.
        effects += mid.on(TalkEvent.TurnTimeout(1))
        effects += mid.on(TalkEvent.TryAgain)
        assertTrue(mid.state != TurnState.NoSignal)
        effects += mid.on(TalkEvent.LimitReached)
        assertTrue(effects.none { it == TalkEffect.PlayNoConnection || it is TalkEffect.GiveUp })
        assertEquals(TurnState.Ended, mid.state)
    }
}
