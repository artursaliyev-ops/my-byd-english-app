package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §6 steps 6, 7 and 9: the nudge, "wait", "bye" and the 30-min cap. */
class TalkRulesTest {
    private val talk = TalkTurns()

    private fun server(e: LiveEvent) = talk.on(TalkEvent.Server(e))
    private fun mic(e: MicEvent) = talk.on(TalkEvent.Mic(e))

    /** Anna says [line] and her audio plays out; returns the effects of the echo guard ending. */
    private fun annaSays(line: String): List<TalkEffect> {
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.AnnaSaid(line))
        server(LiveEvent.TurnComplete)
        talk.on(TalkEvent.PlaybackDrained)
        return talk.on(TalkEvent.EchoGuardOver)
    }

    /** The timer [effects] asked for that feeds back a quiet timeout, if any. */
    private fun quietTimer(effects: List<TalkEffect>): TalkEffect.After? =
        effects.filterIsInstance<TalkEffect.After>().singleOrNull { it.event is TalkEvent.Quiet }

    private fun learnerSays() {
        mic(MicEvent.SpeechStarted(byteArrayOf(1)))
        mic(MicEvent.SpeechEnded)
    }

    private fun opened(): List<TalkEffect> {
        server(LiveEvent.SetupComplete)
        return annaSays("Hi! How is your day?")
    }

    @Test
    fun `after 9 s of silence in Your turn the app nudges Anna once`() {
        val timer = quietTimer(opened())!!
        assertEquals(9_000L, timer.ms)
        assertEquals(
            listOf(TalkEffect.CloseMic, TalkEffect.SendText("(The learner is quiet.)"), TalkEffect.After(8_000, TalkEvent.TurnTimeout(2))),
            talk.on(timer.event),
        )
        assertEquals(TurnState.Thinking, talk.state)
    }

    @Test
    fun `after the nudge Anna waits silently, no second nudge until the learner has spoken`() {
        talk.on(quietTimer(opened())!!.event)
        val afterNudge = annaSays("Take your time.")
        assertEquals(TurnState.You, talk.state)
        assertEquals(null, quietTimer(afterNudge))
        learnerSays()
        assertTrue(quietTimer(annaSays("Nice. Is the road busy?")) != null)
    }

    @Test
    fun `speaking before 9 s means no nudge, and an old timer never nudges a later turn`() {
        val first = quietTimer(opened())!!
        mic(MicEvent.SpeechStarted(byteArrayOf(1)))
        assertTrue(talk.on(first.event).isEmpty())
        mic(MicEvent.SpeechEnded)
        annaSays("Nice. Is the road busy?")
        assertTrue(talk.on(first.event).isEmpty())
        assertEquals(TurnState.You, talk.state)
    }

    @Test
    fun `silence never ends a Talk`() {
        talk.on(quietTimer(opened())!!.event)
        annaSays("Take your time.")
        assertEquals(TurnState.You, talk.state)
        assertTrue(talk.on(TalkEvent.Quiet(2)).isEmpty())
        assertEquals(TurnState.You, talk.state)
    }

    @Test
    fun `after Anna's exact OK the nudge is skipped until the learner next speaks`() {
        opened()
        learnerSays()
        assertEquals(null, quietTimer(annaSays("OK.")))
        learnerSays()
        assertTrue(quietTimer(annaSays("Good. What is for dinner?")) != null)
    }

    @Test
    fun `a longer line starting with OK still gets the nudge`() {
        opened()
        learnerSays()
        assertTrue(quietTimer(annaSays("OK. Where are you going?")) != null)
    }

    @Test
    fun `after Anna's goodbye the Talk ends once her audio has played`() {
        opened()
        learnerSays()
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.AnnaSaid("Bye! Drive safe. Talk to you later."))
        assertEquals(listOf(TalkEffect.AwaitDrain), server(LiveEvent.TurnComplete))
        assertEquals(TurnState.Anna, talk.state)
        assertEquals(listOf(TalkEffect.End), talk.on(TalkEvent.PlaybackDrained))
        assertEquals(TurnState.Ended, talk.state)
    }

    @Test
    fun `a line that only starts with Bye, or mentions by the way, doesn't end the Talk`() {
        opened()
        learnerSays()
        annaSays("Bye-bye to the rain! Is it sunny now?")
        assertEquals(TurnState.You, talk.state)
        learnerSays()
        annaSays("By the way, drive safe. How old are your children?")
        assertEquals(TurnState.You, talk.state)
    }

    @Test
    fun `the 30-min cap lets the current Turn finish, then time is up instead of Your turn`() {
        opened()
        mic(MicEvent.SpeechStarted(byteArrayOf(1)))
        assertTrue(talk.on(TalkEvent.TimeUp).isEmpty())
        mic(MicEvent.SpeechEnded)
        assertEquals(TurnState.Thinking, talk.state)
        assertEquals(listOf(TalkEffect.SendText("(Time is up.)"), TalkEffect.After(8_000, TalkEvent.TurnTimeout(3))), annaSays("Nice. Is the road busy?"))
        assertEquals(TurnState.Thinking, talk.state)
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.AnnaSaid("We talked a lot today! Bye! Drive safe."))
        server(LiveEvent.TurnComplete)
        assertEquals(listOf(TalkEffect.End), talk.on(TalkEvent.PlaybackDrained))
        assertEquals(TurnState.Ended, talk.state)
    }

    @Test
    fun `time up in Your turn before the learner speaks goes out at once`() {
        opened()
        assertEquals(listOf(TalkEffect.CloseMic, TalkEffect.SendText("(Time is up.)"), TalkEffect.After(8_000, TalkEvent.TurnTimeout(2))), talk.on(TalkEvent.TimeUp))
        assertEquals(TurnState.Thinking, talk.state)
        assertTrue(talk.on(TalkEvent.Quiet(1)).isEmpty())
    }

    @Test
    fun `the slow quiet ring runs in Your turn until the learner speaks`() {
        opened()
        assertTrue(talk.quietRing)
        mic(MicEvent.SpeechStarted(byteArrayOf(1)))
        assertTrue(!talk.quietRing)
    }

    @Test
    fun `no quiet ring outside Your turn, nor once the nudge is spent`() {
        server(LiveEvent.SetupComplete)
        assertTrue(!talk.quietRing)
        talk.on(quietTimer(annaSays("Hi! How is your day?"))!!.event)
        assertTrue(!talk.quietRing)
        annaSays("Take your time.")
        assertEquals(TurnState.You, talk.state)
        assertTrue(!talk.quietRing)
    }

    /** The Turn timeout among [effects]. */
    private fun turnTimeout(effects: List<TalkEffect>) =
        effects.filterIsInstance<TalkEffect.After>().single { it.event is TalkEvent.TurnTimeout }

    @Test
    fun `the nudge and time-up get the 8 s Turn timeout too`() {
        assertEquals(8_000L, turnTimeout(talk.on(quietTimer(opened())!!.event)).ms)
        annaSays("Take your time.")
        assertEquals(8_000L, turnTimeout(talk.on(TalkEvent.TimeUp)).ms)
    }
}
