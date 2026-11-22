package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §6 steps 3-4: the learner's speech as the VAD hears it, with a fake detector that calls a
 * frame speech when its samples are loud. Frames are 32 ms (512 samples at 16 kHz).
 */
class SpeechGateTest {
    private val loud = object : SpeechDetector {
        override fun speechProbability(frame: ShortArray) = if (frame.any { it >= 1000 }) 0.9f else 0.05f
    }
    private val gate = SpeechGate(loud)

    private fun quiet(tag: Short = 1) = ShortArray(512) { tag }
    private fun speech(tag: Short = 1000) = ShortArray(512) { tag }
    private fun feed(n: Int, frame: () -> ShortArray) = (1..n).flatMap { gate.feed(frame()) }

    /** Quiet frames that just reach the silence window. */
    private val window = ((TalkConfig.Timing.SILENCE_MS + 31) / 32).toInt()

    /** Samples of a little-endian 16-bit PCM byte array. */
    private fun ByteArray.samples() = ShortArray(size / 2) { ((this[2 * it + 1].toInt() shl 8) or (this[2 * it].toInt() and 0xFF)).toShort() }

    @Test
    fun `a closed mic hears nothing, speech or not`() {
        assertEquals(emptyList<MicEvent>(), feed(20) { speech() })
    }

    @Test
    fun `speech start carries the 300 ms heard just before it`() {
        gate.open()
        feed(5) { quiet(1) }
        feed(10) { quiet(2) }
        val events = gate.feed(speech())
        val start = events.first() as MicEvent.SpeechStarted
        val pre = start.preRoll.samples()
        assertEquals(4800, pre.size)
        assertTrue(pre.all { it == 2.toShort() })
    }

    @Test
    fun `the live speech streams in chunks of about 100 ms, from the first speech frame`() {
        gate.open()
        feed(3) { quiet() }
        val audio = feed(10) { speech() }.filterIsInstance<MicEvent.Audio>()
        assertTrue(audio.isNotEmpty())
        for (chunk in audio) assertTrue(chunk.pcm.size / 2 in 1600..2100)
        assertEquals(1000.toShort(), audio.first().pcm.samples().first())
    }

    @Test
    fun `the silence window ends the utterance with all its audio sent, and closes the mic`() {
        gate.open()
        val events = feed(4) { speech() } + feed(window) { quiet() }
        assertEquals(MicEvent.SpeechEnded, events.last())
        val sent = events.filterIsInstance<MicEvent.Audio>().sumOf { it.pcm.size / 2 }
        assertEquals((4 + window) * 512, sent)
        assertEquals(emptyList<MicEvent>(), feed(10) { speech() })
    }

    @Test
    fun `just under the silence window the utterance goes on`() {
        gate.open()
        val events = feed(4) { speech() } + feed(window - 1) { quiet() }
        assertTrue(MicEvent.SpeechEnded !in events)
    }

    @Test
    fun `the silence ring fills over the silence window and empties when the learner speaks again`() {
        gate.open()
        feed(2) { speech() }
        val rising = feed(window / 2) { quiet() }.filterIsInstance<MicEvent.Silence>().map { it.fraction }
        assertTrue(rising.zipWithNext().all { (a, b) -> b > a })
        assertEquals(0.5f, rising.last(), 0.05f)
        assertEquals(MicEvent.Silence(0f), gate.feed(speech()).filterIsInstance<MicEvent.Silence>().single())
    }

    @Test
    fun `an utterance reaching 30 s ends the same way`() {
        gate.open()
        val events = feed(30_000 / 32 + 1) { speech() }
        assertEquals(MicEvent.SpeechEnded, events.last())
        assertEquals(1, events.count { it == MicEvent.SpeechEnded })
    }

    @Test
    fun `audio from before the mic opened never reaches the pre-roll`() {
        gate.open()
        feed(10) { quiet(7) }
        gate.close()
        gate.open()
        feed(2) { quiet(3) }
        val pre = (gate.feed(speech()).first() as MicEvent.SpeechStarted).preRoll.samples()
        assertEquals(1024, pre.size)
        assertTrue(pre.all { it == 3.toShort() })
    }
}
