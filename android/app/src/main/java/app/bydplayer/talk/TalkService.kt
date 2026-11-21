package app.bydplayer.talk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.bydplayer.BuildConfig
import app.bydplayer.MainActivity
import app.bydplayer.PlaybackService
import com.google.common.util.concurrent.ListenableFuture

/**
 * The Talk foreground service (spec §5): owns the Live session, Anna's playback, audio focus and
 * the [TalkTurns] state machine for one Talk. Every event is handled on the main thread: socket
 * callbacks are posted here, fed to [TalkTurns], and its effects carried out. Started only from
 * the visible Talk button. Nothing from a Talk is written to disk.
 */
class TalkService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var turns: TalkTurns? = null
    private var session: LiveSession? = null
    private var voice: AnnaVoice? = null
    private var focus: AudioFocusRequest? = null
    private var lessonFuture: ListenableFuture<MediaController>? = null
    private var lesson: MediaController? = null
    private var lessonWasPlaying = false
    private var startedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            turns?.let { handle(TalkEvent.EndPressed) } ?: finishTalk()
            return START_NOT_STICKY
        }
        if (turns == null) begin()
        return START_NOT_STICKY
    }

    /** Swiping the app away ends the Talk. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (turns != null) handle(TalkEvent.EndPressed)
    }

    override fun onDestroy() {
        if (turns != null) finishTalk()
        super.onDestroy()
    }

    private fun begin() {
        startedAt = SystemClock.elapsedRealtime()
        goForeground()
        turns = TalkTurns()
        publish()
        // Pause the lesson first, then take focus: taking it first would pause the lesson by
        // focus loss and hide whether it was playing (spec §9).
        pauseLessonThen { requestFocus() }
        voice = AnnaVoice()
        val prompt = assets.open(TalkConfig.PROMPT_ASSET).bufferedReader().use { it.readText() }.trim()
        session = LiveSession(
            apiKey = BuildConfig.GEMINI_API_KEY,
            prompt = prompt,
            onEvent = { e -> main.post { handle(TalkEvent.Server(e)) } },
            onLost = { main.post { handle(TalkEvent.SessionLost) } },
        ).also { it.open() }
    }

    private fun handle(event: TalkEvent) {
        val t = turns ?: return
        val before = t.state
        val effects = t.on(event)
        if (t.state != before) Log.i(TAG, "${before} -> ${t.state} at ${SystemClock.elapsedRealtime() - startedAt} ms")
        publish()
        for (effect in effects) {
            when (effect) {
                is TalkEffect.SendText -> session?.send(LiveProtocol.text(effect.text))
                is TalkEffect.Play -> voice?.play(effect.pcm)
                TalkEffect.End -> finishTalk()
            }
        }
    }

    private fun publish() {
        val t = turns ?: return
        Talk.screen = if (t.state == TurnState.Ended) null else TalkScreenState(t.state, t.annaLine)
    }

    /** Ends at once (spec §9): socket, Anna, focus, screen, then the lesson by the resume rule. */
    private fun finishTalk() {
        turns = null
        session?.close(); session = null
        voice?.release(); voice = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        Talk.screen = null
        resumeLesson()
        Log.i(TAG, "Talk ended after ${SystemClock.elapsedRealtime() - startedAt} ms")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseLessonThen(next: () -> Unit) {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token)
            .setConnectionHints(Bundle().apply { putBoolean(PlaybackService.HINT_APP_UI, true) })
            .buildAsync()
        lessonFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) Log.w(TAG, "No lesson controller")
            if (c != null && lessonFuture === future) {
                lesson = c
                // As the player screen counts it: meant to play, even while buffering.
                lessonWasPlaying = c.playWhenReady &&
                    c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE
                c.pause()
            }
            // The Talk may already be over (End Talk tapped before the controller connected).
            if (turns != null) next() else if (lesson == null) c?.release()
        }, mainExecutor)
    }

    private fun resumeLesson() {
        val future = lessonFuture ?: return
        lessonFuture = null
        val c = lesson ?: run { MediaController.releaseFuture(future); return }
        lesson = null
        resumeAfterTalk(lessonWasPlaying, c.currentPosition)?.let { at ->
            c.seekTo(at)
            c.play()
        }
        // Let the commands reach the session before letting go of the controller.
        main.postDelayed({ c.release() }, 1_000)
    }

    private fun requestFocus() {
        val v = voice ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(v.attributes)
            // Ticket 20 turns focus changes into Paused, ducking and ending.
            .setOnAudioFocusChangeListener({ change -> Log.i(TAG, "focus change $change") }, main)
            .build()
        val granted = getSystemService(AudioManager::class.java).requestAudioFocus(request)
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) Log.w(TAG, "focus not granted: $granted")
        focus = request
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Talk", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Talking with Anna")
            .setContentText("The lesson is paused.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        // Type from the manifest (microphone); the mic itself comes with ticket 15.
        startForeground(NOTIFICATION_ID, n)
    }

    companion object {
        private const val TAG = "Talk"
        private const val CHANNEL = "talk"
        private const val NOTIFICATION_ID = 12
        private const val ACTION_END = "app.bydplayer.talk.END"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TalkService::class.java))
        }

        fun end(context: Context) {
            context.startService(Intent(context, TalkService::class.java).setAction(ACTION_END))
        }
    }
}
