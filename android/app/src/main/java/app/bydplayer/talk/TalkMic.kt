package app.bydplayer.talk

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The one recording of a Talk (spec §5): `VOICE_COMMUNICATION` (the car's AEC and NS), 16 kHz
 * mono 16-bit, started once from the visible screen and stopped only when the Talk ends; a
 * restart from the background would be muted on the car. [open] and [close] only decide whether
 * frames reach the [SpeechGate]; closed, they are dropped. Silenced capture counts as silence.
 * [onEvent] and [onFailed] run on the mic thread. Nothing is written anywhere.
 */
class TalkMic(
    private val context: Context,
    /** Made on the mic thread, so loading the model doesn't hold up the screen. */
    private val detector: () -> SpeechDetector,
    private val main: Handler,
    private val onEvent: (MicEvent) -> Unit,
    private val onFailed: () -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var wantOpen = false
    @Volatile private var openRequest = 0
    private var thread: Thread? = null

    /** Starts the recording. Call it while the app is on screen. */
    fun start() {
        if (thread != null) return
        running = true
        thread = Thread(::record, "talk-mic").also { it.start() }
    }

    /** Lets frames through: the learner's turn has begun. */
    fun open() {
        openRequest++
        wantOpen = true
    }

    /** Drops frames again. */
    fun close() {
        wantOpen = false
    }

    /** Ends the recording (the Talk is over). */
    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun record() {
        val rate = TalkConfig.Audio.INPUT_RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate * 2),
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "No microphone permission")
            onFailed(); return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord didn't initialise")
            rec.release(); onFailed(); return
        }
        val silenced = AtomicBoolean(false)
        val am = context.getSystemService(AudioManager::class.java)
        val callback = if (Build.VERSION.SDK_INT >= 29) object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                configs.firstOrNull { it.clientAudioSessionId == rec.audioSessionId }?.let {
                    if (it.isClientSilenced != silenced.get()) Log.i(TAG, "client silenced: ${it.isClientSilenced}")
                    silenced.set(it.isClientSilenced)
                }
            }
        }.also { am.registerAudioRecordingCallback(it, main) } else null

        var vad: SpeechDetector? = null
        var applied = 0
        val frame = ShortArray(TalkConfig.Audio.VAD_FRAME)
        var failed = false
        try {
            rec.startRecording()
            vad = detector()
            val gate = SpeechGate(vad)
            while (running) {
                val n = readFully(rec, frame)
                if (n < 0) { Log.w(TAG, "read returned $n"); failed = true; break }
                val request = openRequest
                if (wantOpen && applied != request) { gate.open(); applied = request }
                if (!wantOpen && gate.isOpen) gate.close()
                if (!gate.isOpen) continue
                if (silenced.get()) frame.fill(0)
                gate.feed(frame).forEach(onEvent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Recording failed: ${e.javaClass.simpleName}")
            failed = true
        }
        runCatching { rec.stop() }
        rec.release()
        callback?.let(am::unregisterAudioRecordingCallback)
        (vad as? AutoCloseable)?.close()
        if (failed && running) onFailed()
    }

    /** Fills [frame] (a whole VAD frame), or returns a negative error code. */
    private fun readFully(rec: AudioRecord, frame: ShortArray): Int {
        var got = 0
        while (got < frame.size) {
            val n = rec.read(frame, got, frame.size - got)
            if (n < 0) return n
            if (n == 0 && !running) return got
            got += n
        }
        return got
    }

    private companion object {
        const val TAG = "TalkMic"
    }
}
