package app.bydplayer

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * A slice of microphone audio, tagged with where the lesson was while it was captured.
 * [epoch] changes on every jump (seek, ±5 s, A-B loop, lesson change, "▶ Me"), so a
 * kept window is only ever cut from one unbroken run of playback.
 */
class MicChunk(
    val epoch: Long,
    val startPosMs: Long,
    val endPosMs: Long,
    val playing: Boolean,
    val pcm: ShortArray,
)

/** A finished cut: the speaking pause and the voice heard in it. */
class CutAttempt(val pause: SpeakingPause, val pcm: ShortArray)

/**
 * Turns a stream of [MicChunk]s into Attempts for one lesson's [pauses] (the spec's §5).
 * A window is cut only if playback crossed its start and ran to its end with no jump.
 * While the lesson is paused inside a window, up to [MAX_PAUSED_MS] of audio is kept too.
 * Not thread-safe: feed it from the recording thread only.
 */
class AttemptCutter(private val pauses: List<SpeakingPause>, private val sampleRate: Int = SAMPLE_RATE) {
    private var epoch = Long.MIN_VALUE
    private var active: SpeakingPause? = null
    private var buffer = ShortArray(0)
    private var size = 0
    private var pausedSamples = 0

    /** True while a kept window is being captured: the recording dot pulses. */
    val capturing get() = active != null

    fun feed(chunk: MicChunk): CutAttempt? {
        if (chunk.epoch != epoch) {
            epoch = chunk.epoch
            drop()
        }
        val current = active
        if (current != null) {
            if (!chunk.playing) {
                val room = (MAX_PAUSED_MS * sampleRate / 1000).toInt() - pausedSamples
                if (room > 0) {
                    val n = minOf(room, chunk.pcm.size)
                    append(chunk.pcm, 0, n)
                    pausedSamples += n
                }
                return null
            }
            if (chunk.endPosMs < current.keepEndMs) {
                append(chunk.pcm, 0, chunk.pcm.size)
                return null
            }
            append(chunk.pcm, 0, samplesUntil(chunk, current.keepEndMs))
            val done = CutAttempt(current, buffer.copyOf(size))
            drop()
            return done
        }
        if (!chunk.playing || chunk.endPosMs <= chunk.startPosMs) return null
        val next = pauses.firstOrNull { it.keepStartMs >= chunk.startPosMs && it.keepStartMs < chunk.endPosMs } ?: return null
        active = next
        val from = samplesUntil(chunk, next.keepStartMs)
        append(chunk.pcm, from, chunk.pcm.size - from)
        return null
    }

    /** Samples in [chunk] before lesson time [posMs], scaled for any speed. */
    private fun samplesUntil(chunk: MicChunk, posMs: Long): Int {
        val span = chunk.endPosMs - chunk.startPosMs
        if (span <= 0) return 0
        val f = ((posMs - chunk.startPosMs).toDouble() / span).coerceIn(0.0, 1.0)
        return (f * chunk.pcm.size).toInt()
    }

    private fun append(pcm: ShortArray, from: Int, n: Int) {
        if (n <= 0) return
        if (size + n > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + n, sampleRate))
        System.arraycopy(pcm, from, buffer, size, n)
        size += n
    }

    private fun drop() {
        active = null
        size = 0
        pausedSamples = 0
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_PAUSED_MS = 10_000L
    }
}

/**
 * Whether [pcm] holds a voice and not just road noise: at least [VOICE_FRAMES] frames of
 * 100 ms louder than [VOICE_DBFS]. Starting values from the car check; tune on the car.
 */
fun isVoiced(pcm: ShortArray, sampleRate: Int = AttemptCutter.SAMPLE_RATE): Boolean {
    val frame = sampleRate / 10
    var loud = 0
    var i = 0
    while (i + frame <= pcm.size) {
        var sum = 0.0
        for (j in i until i + frame) sum += pcm[j].toDouble() * pcm[j]
        val rms = sqrt(sum / frame) / 32768.0
        if (rms > 0 && 20 * log10(rms) > VOICE_DBFS) loud++
        if (loud >= VOICE_FRAMES) return true
        i += frame
    }
    return false
}

const val VOICE_DBFS = -50.0
const val VOICE_FRAMES = 3
