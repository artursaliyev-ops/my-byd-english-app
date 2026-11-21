package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §10: calls, "Hi BYD", navigation prompts and other media apps. */
class TalkFocusTest {
    private val talk = TalkTurns()

    private fun server(e: LiveEvent) = talk.on(TalkEvent.Server(e))
    private fun mic(e: MicEvent) = talk.on(TalkEvent.Mic(e))

    /** Opening, Anna's hello played to the end: the learner's turn begins. */
    private fun toYou() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.AnnaSaid("Hi! How was your day?"))
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.TurnComplete)
        talk.on(TalkEvent.PlaybackDrained)
        talk.on(TalkEvent.EchoGuardOver)
    }

    /** The pause timer the effects asked for. */
    private fun pauseTimer(effects: List<TalkEffect>): TalkEffect.After =
        effects.filterIsInstance<TalkEffect.After>().single()

    @Test
    fun `a call while Anna speaks pauses, so she stops, the mic closes, and the 60 s timer runs`() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.AnnaSaid("Hi!"))
        server(LiveEvent.Audio(byteArrayOf(1)))
        val effects = talk.on(TalkEvent.FocusPaused)
        assertEquals(TurnState.Paused, talk.state)
        assertTrue(TalkEffect.StopAnna in effects)
        assertTrue(TalkEffect.CloseMic in effects)
        assertEquals(60_000L, pauseTimer(effects).ms)
        assertEquals("Hi!", talk.annaLine)
    }

    @Test
    fun `the rest of an answer arriving while paused is dropped, and her latest line stays`() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.AnnaSaid("Hi!"))
        server(LiveEvent.Audio(byteArrayOf(1)))
        talk.on(TalkEvent.FocusPaused)
        assertTrue(server(LiveEvent.Audio(byteArrayOf(2))).isEmpty())
        server(LiveEvent.AnnaSaid("How was your day?"))
        assertTrue(server(LiveEvent.TurnComplete).isEmpty())
        assertEquals("Hi!", talk.annaLine)
        // And nothing of it plays once focus is back.
        talk.on(TalkEvent.FocusBack)
        assertTrue(server(LiveEvent.Audio(byteArrayOf(3))).isEmpty())
    }

    @Test
    fun `focus back gives the turn beep and Your turn, without Anna repeating herself`() {
        toYou()
        talk.on(TalkEvent.FocusPaused)
        val back = talk.on(TalkEvent.FocusBack)
        assertEquals(listOf(TalkEffect.PlayBeep, TalkEffect.OpenMic), back.take(2))
        assertTrue(back.drop(2).single().let { it is TalkEffect.After && it.event is TalkEvent.Quiet })
        assertEquals(TurnState.You, talk.state)
        assertEquals("Hi! How was your day?", talk.annaLine)
    }

    @Test
    fun `no nudge while paused, and the cap reached while paused says goodbye on focus back`() {
        toYou()
        val quiet = TalkEvent.Quiet(1)
        talk.on(TalkEvent.FocusPaused)
        assertTrue(talk.on(quiet).isEmpty())
        talk.on(TalkEvent.FocusBack)
        // The quiet timer from before the pause stays stale after it.
        assertTrue(talk.on(quiet).isEmpty())
        talk.on(TalkEvent.FocusPaused)
        assertTrue(talk.on(TalkEvent.TimeUp).isEmpty())
        assertEquals(listOf(TalkEffect.SendText(TalkConfig.TIME_UP), TalkEffect.After(8_000, TalkEvent.TurnTimeout(4))), talk.on(TalkEvent.FocusBack))
    }

    @Test
    fun `an utterance in progress is dropped, its activity is closed and mic events are ignored`() {
        toYou()
        mic(MicEvent.SpeechStarted(byteArrayOf(1)))
        val effects = talk.on(TalkEvent.FocusPaused)
        assertTrue(TalkEffect.EndActivity in effects)
        assertTrue(TalkEffect.CloseMic in effects)
        assertTrue(mic(MicEvent.Audio(byteArrayOf(2))).isEmpty())
        assertTrue(mic(MicEvent.SpeechEnded).isEmpty())
        // The server's answer to the cut-off words is not played.
        assertTrue(server(LiveEvent.Audio(byteArrayOf(3))).isEmpty())
    }

    @Test
    fun `a pause in Your turn before speaking closes no activity`() {
        toYou()
        assertTrue(TalkEffect.EndActivity !in talk.on(TalkEvent.FocusPaused))
    }

    @Test
    fun `Anna's audio draining during a pause doesn't open the mic`() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.TurnComplete)
        talk.on(TalkEvent.FocusPaused)
        assertTrue(talk.on(TalkEvent.PlaybackDrained).isEmpty())
        assertTrue(talk.on(TalkEvent.EchoGuardOver).isEmpty())
        assertEquals(TurnState.Paused, talk.state)
    }

    @Test
    fun `after the pause the next Turn runs as usual`() {
        toYou()
        talk.on(TalkEvent.FocusPaused)
        talk.on(TalkEvent.FocusBack)
        mic(MicEvent.SpeechStarted(byteArrayOf(1)))
        mic(MicEvent.SpeechEnded)
        assertEquals(listOf(TalkEffect.Play(byteArrayOf(2))), server(LiveEvent.Audio(byteArrayOf(2))))
        assertEquals(TurnState.Anna, talk.state)
    }

    @Test
    fun `a pause while connecting goes back to the opening once focus returns`() {
        talk.on(TalkEvent.FocusPaused)
        assertTrue(server(LiveEvent.SetupComplete).isEmpty())
        assertEquals(listOf(TalkEffect.SendText(TalkConfig.OPENING), TalkEffect.After(8_000, TalkEvent.TurnTimeout(2))), talk.on(TalkEvent.FocusBack))
        assertEquals(TurnState.Thinking, talk.state)
    }

    @Test
    fun `60 s paused ends the Talk, and the lesson waits for focus to come back`() {
        toYou()
        val timer = pauseTimer(talk.on(TalkEvent.FocusPaused))
        assertEquals(listOf(TalkEffect.EndWhenFocusBack()), talk.on(timer.event))
        assertEquals(TurnState.Ended, talk.state)
    }

    @Test
    fun `the timer of an earlier pause doesn't end a later one`() {
        toYou()
        val first = pauseTimer(talk.on(TalkEvent.FocusPaused))
        talk.on(TalkEvent.FocusBack)
        talk.on(TalkEvent.FocusPaused)
        assertTrue(talk.on(first.event).isEmpty())
        assertEquals(TurnState.Paused, talk.state)
    }

    @Test
    fun `End Talk works while paused, and the lesson waits for focus to come back`() {
        toYou()
        talk.on(TalkEvent.FocusPaused)
        assertEquals(listOf(TalkEffect.EndWhenFocusBack()), talk.on(TalkEvent.EndPressed))
    }

    @Test
    fun `a navigation prompt only makes Anna quieter`() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        assertEquals(listOf(TalkEffect.Volume(0.3f)), talk.on(TalkEvent.FocusDucked))
        assertEquals(TurnState.Anna, talk.state)
        assertEquals(listOf(TalkEffect.Play(byteArrayOf(2))), server(LiveEvent.Audio(byteArrayOf(2))))
        assertEquals(listOf(TalkEffect.Volume(1f)), talk.on(TalkEvent.FocusBack))
        assertEquals(TurnState.Anna, talk.state)
    }

    @Test
    fun `another media app ends the Talk at once and the lesson stays paused`() {
        for (t in listOf(TalkTurns(), TalkTurns().also { it.on(TalkEvent.FocusPaused) })) {
            assertEquals(listOf(TalkEffect.EndLessonPaused), t.on(TalkEvent.FocusGone))
            assertEquals(TurnState.Ended, t.state)
        }
    }
}
