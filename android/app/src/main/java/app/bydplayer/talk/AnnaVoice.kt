package app.bydplayer.talk

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue

/**
 * Plays Anna's 24 kHz mono 16-bit PCM as it streams in (spec §6 step 2), through
 * `USAGE_MEDIA` / `CONTENT_TYPE_SPEECH`. Pieces are queued and written on one thread, so the
 * caller never blocks. Audio lives in memory only.
 */
class AnnaVoice {
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var running = true

    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val track: AudioTrack = run {
        val format = AudioFormat.Builder()
            .setSampleRate(TalkConfig.OUTPUT_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val minBuf = AudioTrack.getMinBufferSize(
            TalkConfig.OUTPUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            // A quarter second of headroom against network jitter.
            .setBufferSizeInBytes(maxOf(minBuf, TalkConfig.OUTPUT_RATE / 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private val writer = Thread({
        try {
            track.play()
            while (running) {
                val pcm = queue.take()
                if (!running) break
                track.write(pcm, 0, pcm.size)
            }
        } catch (_: InterruptedException) {
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioTrack failed: ${e.javaClass.simpleName}")
        }
    }, "anna-voice").apply { start() }

    fun play(pcm: ByteArray) {
        if (running) queue.offer(pcm)
    }

    /** Stops at once, mid-word, and frees the track. */
    fun release() {
        running = false
        queue.clear()
        writer.interrupt()
        // Pause + flush drops what is buffered and unblocks a write in progress.
        runCatching { track.pause(); track.flush(); track.stop() }
        writer.join(500)
        track.release()
    }

    private companion object {
        const val TAG = "TalkVoice"
    }
}
