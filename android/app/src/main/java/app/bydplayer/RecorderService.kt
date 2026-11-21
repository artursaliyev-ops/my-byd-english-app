package app.bydplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors

/**
 * The voice-practice microphone (spec §3): a foreground service of type microphone that
 * records `VOICE_COMMUNICATION` (the car's echo cancellation keeps the lesson out) and
 * cuts Attempts out of the speaking pauses as the lesson plays. Only kept windows are ever
 * written. Stops when the setting is turned off, after 30 min with the lesson paused, or
 * when the app is closed. Must be started while the app is on screen.
 */
class RecorderService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var stopping = false
    private var thread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopping = true
            return START_NOT_STICKY
        }
        if (thread != null) return START_NOT_STICKY
        try {
            goForeground()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start the microphone service", e)
            stopSelf()
            return START_NOT_STICKY
        }
        VoicePractice.micRunning = true
        stopping = false
        thread = Thread(::record, "voice-practice").also { it.start() }
        return START_NOT_STICKY
    }

    /** Swiping the app away closes it, and the microphone with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopping = true
    }

    override fun onDestroy() {
        stopping = true
        VoicePractice.micRunning = false
        writer.shutdown()
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recording my answers", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Recording your answers")
            .setContentText("Only the speaking pauses are kept.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION_ID, n)
    }

    private fun record() {
        val rate = AttemptCutter.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate * 2))
        } catch (e: SecurityException) {
            Log.w(TAG, "No microphone permission", e)
            finish(); return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord didn't initialise")
            rec.release(); finish(); return
        }
        val silenced = java.util.concurrent.atomic.AtomicBoolean(false)
        val am = getSystemService(AudioManager::class.java)
        val callback = if (Build.VERSION.SDK_INT >= 29) object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                configs.firstOrNull { it.clientAudioSessionId == rec.audioSessionId }?.let { silenced.set(it.isClientSilenced) }
            }
        }.also { am.registerAudioRecordingCallback(it, main) } else null

        val settings = SettingsStore(this)
        val store = VoicePractice.store(this)
        val pcm = ShortArray(rate / 10)
        var lesson: LessonCut? = null
        var lastPlayingAt = SystemClock.elapsedRealtime()
        var wasCapturing = false
        try {
            rec.startRecording()
            while (!stopping) {
                val before = LessonClock.snapshot
                val startPos = before.positionNow()
                val n = rec.read(pcm, 0, pcm.size)
                if (n <= 0) { Log.w(TAG, "read returned $n"); break }
                val after = LessonClock.snapshot
                val endPos = after.positionNow()
                val now = SystemClock.elapsedRealtime()
                if (after.playing) lastPlayingAt = now
                if (now - lastPlayingAt > IDLE_STOP_MS || !settings.recordAnswers()) break

                if (lesson?.number != after.lesson) lesson = after.lesson?.let { LessonCut.load(this, it) }
                val cut = lesson ?: continue
                val audio = if (silenced.get()) ShortArray(n) else pcm.copyOf(n)
                val chunk = MicChunk(
                    epoch = after.epoch,
                    startPosMs = if (before.epoch == after.epoch) startPos else endPos,
                    endPosMs = endPos,
                    playing = after.playing && !LessonClock.reviewing,
                    pcm = audio,
                )
                cut.cutter.feed(chunk)?.let { done ->
                    val voiced = isVoiced(done.pcm)
                    Log.i(TAG, "Cut ${done.pause.kind} at ${done.pause.startMs} ms, ${done.pcm.size * 1000L / AttemptCutter.SAMPLE_RATE} ms of audio, voiced=$voiced")
                    if (voiced) save(store, cut, done)
                }
                if (cut.cutter.capturing != wasCapturing) {
                    wasCapturing = cut.cutter.capturing
                    val c = wasCapturing
                    main.post { VoicePractice.capturing = c }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Recording failed", e)
        }
        runCatching { rec.stop() }
        rec.release()
        callback?.let(am::unregisterAudioRecordingCallback)
        finish()
    }

    private fun save(store: AttemptStore, lesson: LessonCut, done: CutAttempt) {
        writer.execute {
            try {
                store.save(lesson.id, lesson.fingerprint, AttemptKey(done.pause.kind, done.pause.startMs)) { file ->
                    AacWriter.write(done.pcm, AttemptCutter.SAMPLE_RATE, file)
                }
                main.post { VoicePractice.storeVersion++ }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save an Attempt", e)
            }
        }
    }

    private fun finish() {
        main.post {
            VoicePractice.capturing = false
            VoicePractice.micRunning = false
            thread = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** The current lesson's transcript, turned into a cutter. */
    private class LessonCut(val number: Int, val id: String, val fingerprint: String, val cutter: AttemptCutter) {
        companion object {
            fun load(context: Context, number: Int): LessonCut? {
                val id = transcriptId(number) ?: return null
                val segments = loadTranscript(context, number) ?: return null
                return LessonCut(number, id, transcriptFingerprint(segments), AttemptCutter(speakingPauses(segments)))
            }
        }
    }

    companion object {
        private const val TAG = "VoicePractice"
        private const val CHANNEL = "voice_practice"
        private const val NOTIFICATION_ID = 11
        private const val ACTION_STOP = "app.bydplayer.voice.STOP"
        private const val IDLE_STOP_MS = 30 * 60_000L

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, RecorderService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't start the microphone", e)
            }
        }

        fun stop(context: Context) {
            if (!VoicePractice.micRunning) return
            context.startService(Intent(context, RecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
