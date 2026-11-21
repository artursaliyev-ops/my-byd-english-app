package app.bydplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptCutterTest {
    // 1 kHz sample rate keeps the numbers readable: 1 sample = 1 ms at 1×.
    private val pause = SpeakingPause(startMs = 1_000, keepStartMs = 1_200, keepEndMs = 2_000, AttemptKind.Answer, 1)

    private fun cutter() = AttemptCutter(listOf(pause), sampleRate = 1_000)

    private fun chunk(epoch: Long, from: Long, to: Long, playing: Boolean = true, samples: Int = (to - from).toInt(), value: Short = 1) =
        MicChunk(epoch, from, to, playing, ShortArray(samples) { value })

    /** Plays [from]..[to] in 100 ms chunks; returns the attempts cut. */
    private fun AttemptCutter.play(from: Long, to: Long, epoch: Long = 1): List<CutAttempt> =
        (from until to step 100).mapNotNull { feed(chunk(epoch, it, minOf(it + 100, to))) }

    @Test fun cutsExactlyTheKeptWindow() {
        val cut = cutter().play(0, 3_000).single()
        assertEquals(pause, cut.pause)
        assertEquals(800, cut.pcm.size)
    }

    @Test fun aSeekIntoTheWindowCutsNothing() {
        val c = cutter()
        c.play(0, 1_000, epoch = 1)
        assertTrue(c.play(1_500, 3_000, epoch = 2).isEmpty())
    }

    @Test fun aSeekOutOfTheWindowDropsIt() {
        val c = cutter()
        assertTrue(c.play(0, 1_500, epoch = 1).isEmpty())
        assertTrue(c.capturing)
        assertTrue(c.play(1_500, 3_000, epoch = 2).isEmpty())
        assertFalse(c.capturing)
    }

    @Test fun pausingInsideTheWindowKeepsUpToTenSecondsMore() {
        val c = cutter()
        c.play(0, 1_500)
        // 15 s paused at 1.5 s: only the first 10 s of it is kept.
        repeat(150) { assertNull(c.feed(chunk(1, 1_500, 1_500, playing = false, samples = 100))) }
        val cut = c.play(1_500, 3_000).single()
        assertEquals(300 + 10_000 + 500, cut.pcm.size)
    }

    @Test fun pausedBeforeTheWindowThenResumedStillCuts() {
        val c = cutter()
        c.play(0, 1_100)
        repeat(30) { c.feed(chunk(1, 1_100, 1_100, playing = false, samples = 100)) }
        assertNotNull(c.play(1_100, 3_000).singleOrNull())
    }

    @Test fun fasterSpeedScalesTheSamples() {
        // At 2× a 100 ms chunk of audio covers 200 ms of lesson.
        val c = cutter()
        val cuts = (0L until 3_000L step 200).mapNotNull { c.feed(chunk(1, it, it + 200, samples = 100)) }
        assertEquals(400, cuts.single().pcm.size)
    }

    @Test fun silenceIsNotAVoice() {
        assertFalse(isVoiced(ShortArray(16_000)))
        assertFalse(isVoiced(ShortArray(16_000) { 20 })) // about -64 dBFS
        assertTrue(isVoiced(ShortArray(16_000) { if (it % 2 == 0) 3_000 else -3_000 })) // about -21 dBFS
        assertFalse(isVoiced(ShortArray(16_000) { if (it < 3_000 && it % 2 == 0) 3_000 else 0 })) // only two loud frames
    }
}
