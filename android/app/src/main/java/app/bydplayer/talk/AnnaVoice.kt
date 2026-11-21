package app.bydplayer.talk

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Plays Anna's 24 kHz mono 16-bit PCM as it streams in (spec §6 step 2), through
 * `USAGE_MEDIA` / `CONTENT_TYPE_SPEECH`. Pieces are queued and written on one thread, so the
 * caller never blocks. [whenDrained] tells when what was queued has actually been heard.
 * Audio lives in memory only.
 */
class AnnaVoice {
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var running = true
    @Volatile private var onDrained: (() -> Unit)? = null

    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    // A quarter second of headroom against network jitter.
    private val bufferBytes = maxOf(
        AudioTrack.getMinBufferSize(TalkConfig.OUTPUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
        TalkConfig.OUTPUT_RATE / 2,
    )

    private val track: AudioTrack = run {
        val format = AudioFormat.Builder()
            .setSampleRate(TalkConfig.OUTPUT_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private val writer = Thread({
        try {
            track.play()
            var written = 0L // frames written, padding included
            var target = -1L // frames Anna's audio ends at, once a drain is asked for
            var lastHead = -1L
            var stillSince = 0L
            while (running) {
                val pcm = queue.poll(POLL_MS, TimeUnit.MILLISECONDS)
                if (!running) break
                if (pcm != null) {
                    track.write(pcm, 0, pcm.size)
                    written += pcm.size / 2
                    target = -1L
                    continue
                }
                val done = onDrained ?: continue
                // A stream track holds back a tail shorter than its start level: a buffer of
                // silence pushes Anna's last words out.
                if (target < 0) {
                    target = written
                    track.write(ByteArray(bufferBytes), 0, bufferBytes)
                    written += bufferBytes / 2
                    lastHead = -1L
                }
                val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                val now = System.nanoTime() / 1_000_000
                if (head != lastHead) { lastHead = head; stillSince = now }
                // A head that hasn't moved for a while is a stalled track: don't wait forever.
                if (head >= target || now - stillSince > STALL_MS) {
                    onDrained = null
                    done()
                }
            }
        } catch (_: InterruptedException) {
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioTrack failed: ${e.javaClass.simpleName}")
        }
    }, "anna-voice").apply { start() }

    fun play(pcm: ByteArray) {
        if (running) queue.offer(pcm)
    }

    /**
     * Calls [done] once, on the writer thread, when everything queued so far has played: Anna
     * has finished speaking (spec §6 step 2).
     */
    fun whenDrained(done: () -> Unit) {
        if (running) onDrained = done
    }

    /** Stops at once, mid-word, and frees the track. */
    fun release() {
        running = false
        onDrained = null
        queue.clear()
        writer.interrupt()
        // Pause + flush drops what is buffered and unblocks a write in progress.
        runCatching { track.pause(); track.flush(); track.stop() }
        writer.join(500)
        track.release()
    }

    private companion object {
        const val TAG = "TalkVoice"
        const val POLL_MS = 20L
        const val STALL_MS = 1_000L
    }
}
