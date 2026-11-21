package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §6 steps 1-5 and §7: the opening, Turn after Turn, and End Talk. */
class TalkTurnsTest {
    private val talk = TalkTurns()

    @Test
    fun `a Talk opens in Connecting`() {
        assertEquals(TurnState.Connecting, talk.state)
    }

    @Test
    fun `when the session is ready the opening goes out and Anna is thinking`() {
        val effects = talk.on(TalkEvent.Server(LiveEvent.SetupComplete))
        assertEquals(listOf(TalkEffect.SendText("(The learner pressed Talk. Start the Talk.)")), effects)
        assertEquals(TurnState.Thinking, talk.state)
    }

    @Test
    fun `Anna's first audio turns Thinking into Anna and every piece is played`() {
        talk.on(TalkEvent.Server(LiveEvent.SetupComplete))
        val first = LiveEvent.Audio(byteArrayOf(1, 2))
        val second = LiveEvent.Audio(byteArrayOf(3, 4))
        assertEquals(listOf(TalkEffect.Play(first.pcm)), talk.on(TalkEvent.Server(first)))
        assertEquals(TurnState.Anna, talk.state)
        assertEquals(listOf(TalkEffect.Play(second.pcm)), talk.on(TalkEvent.Server(second)))
        assertEquals(TurnState.Anna, talk.state)
    }

    @Test
    fun `Anna's latest line builds up from her transcript, and her next turn starts a new one`() {
        talk.on(TalkEvent.Server(LiveEvent.SetupComplete))
        talk.on(TalkEvent.Server(LiveEvent.AnnaSaid("Hi! How ")))
        talk.on(TalkEvent.Server(LiveEvent.AnnaSaid("is your day?")))
        assertEquals("Hi! How is your day?", talk.annaLine)
        talk.on(TalkEvent.Server(LiveEvent.TurnComplete))
        assertEquals("Hi! How is your day?", talk.annaLine)
        talk.on(TalkEvent.Server(LiveEvent.AnnaSaid(" Nice.")))
        assertEquals("Nice.", talk.annaLine)
    }

    @Test
    fun `End Talk ends at once in Connecting, Thinking and Anna`() {
        val reach = listOf<(TalkTurns) -> Unit>(
            {},
            { it.on(TalkEvent.Server(LiveEvent.SetupComplete)) },
            { it.on(TalkEvent.Server(LiveEvent.SetupComplete)); it.on(TalkEvent.Server(LiveEvent.Audio(byteArrayOf(1)))) },
        )
        val reached = reach.map { steps -> TalkTurns().also(steps) }
        assertEquals(listOf(TurnState.Connecting, TurnState.Thinking, TurnState.Anna), reached.map { it.state })
        for (t in reached) {
            assertEquals(listOf(TalkEffect.End), t.on(TalkEvent.EndPressed))
            assertEquals(TurnState.Ended, t.state)
        }
    }

    @Test
    fun `after the end, late audio is not played`() {
        talk.on(TalkEvent.Server(LiveEvent.SetupComplete))
        talk.on(TalkEvent.EndPressed)
        assertEquals(emptyList<TalkEffect>(), talk.on(TalkEvent.Server(LiveEvent.Audio(byteArrayOf(1)))))
        assertEquals(emptyList<TalkEffect>(), talk.on(TalkEvent.EndPressed))
    }

    @Test
    fun `a lost opening with no handle yet retries in a new session and resends the opening`() {
        talk.on(TalkEvent.Server(LiveEvent.SetupComplete))
        assertEquals(listOf(TalkEffect.NewSession), talk.on(TalkEvent.SessionLost))
        assertEquals(
            listOf(TalkEffect.SendText("(The learner pressed Talk. Start the Talk.)")),
            talk.on(TalkEvent.Server(LiveEvent.SetupComplete)),
        )
        assertEquals(TurnState.Thinking, talk.state)
    }

    private fun server(e: LiveEvent) = talk.on(TalkEvent.Server(e))
    private fun mic(e: MicEvent) = talk.on(TalkEvent.Mic(e))

    /** Opening, Anna's hello played to the end: the learner's turn begins. */
    private fun toYou() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.TurnComplete)
        talk.on(TalkEvent.PlaybackDrained)
        talk.on(TalkEvent.EchoGuardOver)
    }

    @Test
    fun `when Anna's turn is complete the service waits for her audio to drain, then 300 ms more`() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        assertEquals(listOf(TalkEffect.AwaitDrain), server(LiveEvent.TurnComplete))
        assertEquals(TurnState.Anna, talk.state)
        assertEquals(listOf(TalkEffect.After(300, TalkEvent.EchoGuardOver)), talk.on(TalkEvent.PlaybackDrained))
        assertEquals(TurnState.Anna, talk.state)
    }

    @Test
    fun `after the echo guard the turn beep plays and the mic opens for Your turn`() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.TurnComplete)
        talk.on(TalkEvent.PlaybackDrained)
        assertEquals(
            listOf(TalkEffect.PlayBeep, TalkEffect.OpenMic, TalkEffect.After(9_000, TalkEvent.Quiet(1))),
            talk.on(TalkEvent.EchoGuardOver),
        )
        assertEquals(TurnState.You, talk.state)
    }

    @Test
    fun `playback draining before Anna's turn is complete doesn't open the mic`() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        assertEquals(emptyList<TalkEffect>(), talk.on(TalkEvent.PlaybackDrained))
        assertEquals(emptyList<TalkEffect>(), talk.on(TalkEvent.EchoGuardOver))
        assertEquals(TurnState.Anna, talk.state)
    }

    @Test
    fun `the learner's speech goes out as activityStart, the pre-roll, then live audio`() {
        toYou()
        val pre = byteArrayOf(9, 9)
        val chunk = byteArrayOf(5, 5)
        assertEquals(listOf(TalkEffect.StartActivity, TalkEffect.SendAudio(pre)), mic(MicEvent.SpeechStarted(pre)))
        assertEquals(listOf(TalkEffect.SendAudio(chunk)), mic(MicEvent.Audio(chunk)))
        assertEquals(TurnState.You, talk.state)
    }

    @Test
    fun `the end of speech sends activityEnd, ticks, closes the mic, and Anna is thinking`() {
        toYou()
        mic(MicEvent.SpeechStarted(byteArrayOf()))
        assertEquals(
            listOf(
                TalkEffect.EndActivity, TalkEffect.PlayTick, TalkEffect.CloseMic,
                TalkEffect.After(8_000, TalkEvent.TurnTimeout(1)),
            ),
            mic(MicEvent.SpeechEnded),
        )
        assertEquals(TurnState.Thinking, talk.state)
    }

    @Test
    fun `Anna answers and the next Turn begins, Turn after Turn`() {
        toYou()
        repeat(3) {
            mic(MicEvent.SpeechStarted(byteArrayOf(1)))
            mic(MicEvent.SpeechEnded)
            assertEquals(TurnState.Thinking, talk.state)
            server(LiveEvent.Audio(byteArrayOf(2)))
            assertEquals(TurnState.Anna, talk.state)
            server(LiveEvent.TurnComplete)
            talk.on(TalkEvent.PlaybackDrained)
            talk.on(TalkEvent.EchoGuardOver)
            assertEquals(TurnState.You, talk.state)
        }
    }

    @Test
    fun `the current utterance is kept whole in memory until the next one starts`() {
        toYou()
        mic(MicEvent.SpeechStarted(byteArrayOf(1, 2)))
        mic(MicEvent.Audio(byteArrayOf(3, 4)))
        mic(MicEvent.Audio(byteArrayOf(5)))
        mic(MicEvent.SpeechEnded)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), talk.utterance)
        server(LiveEvent.Audio(byteArrayOf(0)))
        server(LiveEvent.TurnComplete)
        talk.on(TalkEvent.PlaybackDrained)
        talk.on(TalkEvent.EchoGuardOver)
        mic(MicEvent.SpeechStarted(byteArrayOf(7)))
        assertArrayEquals(byteArrayOf(7), talk.utterance)
    }

    @Test
    fun `the silence ring shows only in Your turn and starts empty each Turn`() {
        toYou()
        mic(MicEvent.SpeechStarted(byteArrayOf()))
        mic(MicEvent.Silence(0.4f))
        assertEquals(0.4f, talk.silence, 0f)
        mic(MicEvent.SpeechEnded)
        assertEquals(0f, talk.silence, 0f)
    }

    @Test
    fun `mic events outside Your turn are ignored`() {
        server(LiveEvent.SetupComplete)
        server(LiveEvent.Audio(byteArrayOf(1)))
        assertTrue(mic(MicEvent.SpeechStarted(byteArrayOf(1))).isEmpty())
        assertTrue(mic(MicEvent.Audio(byteArrayOf(1))).isEmpty())
        assertTrue(mic(MicEvent.SpeechEnded).isEmpty())
        assertEquals(TurnState.Anna, talk.state)
    }

    @Test
    fun `End Talk ends at once in Your turn too`() {
        toYou()
        mic(MicEvent.SpeechStarted(byteArrayOf()))
        assertEquals(listOf(TalkEffect.End), talk.on(TalkEvent.EndPressed))
        assertEquals(TurnState.Ended, talk.state)
        assertTrue(talk.on(TalkEvent.EchoGuardOver).isEmpty())
    }
}
