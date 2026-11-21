package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Spec §11: barge-in stays off. The mic is closed while Anna speaks; only End Talk stops her. */
class BargeInTest {
    private val talk = TalkTurns()
    private val effects = mutableListOf<TalkEffect>()

    private fun on(event: TalkEvent) = talk.on(event).also { effects += it }

    @Test
    fun `the mic stays closed for all of Anna's turn, and speaking over her doesn't stop her`() {
        on(TalkEvent.Server(LiveEvent.SetupComplete))
        on(TalkEvent.Server(LiveEvent.Audio(byteArrayOf(1))))
        on(TalkEvent.Mic(MicEvent.SpeechStarted(byteArrayOf(9))))
        on(TalkEvent.Mic(MicEvent.Audio(byteArrayOf(9))))
        assertEquals(listOf(TalkEffect.Play(byteArrayOf(2))), on(TalkEvent.Server(LiveEvent.Audio(byteArrayOf(2)))))
        on(TalkEvent.Mic(MicEvent.SpeechEnded))
        on(TalkEvent.Server(LiveEvent.TurnComplete))
        on(TalkEvent.PlaybackDrained)
        assertEquals(TurnState.Anna, talk.state)
        assertFalse(effects.any { it == TalkEffect.OpenMic || it == TalkEffect.StartActivity || it == TalkEffect.End })
    }

    @Test
    fun `End Talk stops Anna mid-line`() {
        on(TalkEvent.Server(LiveEvent.SetupComplete))
        on(TalkEvent.Server(LiveEvent.Audio(byteArrayOf(1))))
        assertEquals(listOf(TalkEffect.End), on(TalkEvent.EndPressed))
        assertEquals(emptyList<TalkEffect>(), on(TalkEvent.Server(LiveEvent.Audio(byteArrayOf(2)))))
    }
}
