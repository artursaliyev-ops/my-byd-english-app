package app.bydplayer.talk

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The Talk's own sounds (spec §7), made on the head unit, no recorded clips: the **turn beep**
 * (the learner's turn starts) and the soft **tick** (the end of speech is taken). Each is a
 * short static track played through the same attributes as Anna.
 */
class TalkSounds(attributes: AudioAttributes) {
    private val beep = track(attributes, tone(TalkConfig.BEEP_HZ, TalkConfig.BEEP_MS, 0.35f))
    private val tick = track(attributes, tone(TalkConfig.TICK_HZ, TalkConfig.TICK_MS, 0.18f))

    fun beep() = replay(beep)

    fun tick() = replay(tick)

    fun release() {
        beep.release()
        tick.release()
    }

    private fun replay(t: AudioTrack) {
        runCatching {
            t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    private fun track(attributes: AudioAttributes, pcm: ShortArray): AudioTrack {
        val t = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        return t
    }

    private companion object {
        const val RATE = TalkConfig.OUTPUT_RATE

        /** A sine at [hz] for [ms], with 8 ms fades so it doesn't click. */
        fun tone(hz: Int, ms: Int, volume: Float): ShortArray {
            val n = RATE * ms / 1000
            val fade = RATE * 8 / 1000
            return ShortArray(n) { i ->
                val env = min(1f, min(i, n - 1 - i).toFloat() / fade)
                (sin(2 * PI * hz * i / RATE) * env * volume * Short.MAX_VALUE).toInt().toShort()
            }
        }
    }
}
