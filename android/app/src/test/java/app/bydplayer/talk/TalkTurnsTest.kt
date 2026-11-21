package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec §6 step 1 and §7: the opening of a Talk up to Anna's hello, and End Talk. */
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
    fun `a lost session ends the Talk until reconnecting exists`() {
        talk.on(TalkEvent.Server(LiveEvent.SetupComplete))
        assertEquals(listOf(TalkEffect.End), talk.on(TalkEvent.SessionLost))
        assertEquals(TurnState.Ended, talk.state)
    }
}
