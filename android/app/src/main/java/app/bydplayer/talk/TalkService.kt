package app.bydplayer.talk

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
import app.bydplayer.RecorderService
import com.google.common.util.concurrent.ListenableFuture

/**
 * The Talk foreground service (spec §5): owns the mic, the Live session, Anna's playback, audio
 * focus and the [TalkTurns] state machine for one Talk. Every event is handled on the main
 * thread: socket, mic and playback callbacks are posted here, fed to [TalkTurns], and its effects
 * carried out. Started only from the visible Talk button, so the one recording of the Talk starts
 * while the app is on screen. Nothing from a Talk is written to disk.
 */
class TalkService : Service() {
    private val main = Handler(Looper.getMainLooper())

    /** The state machine's timers, on the main thread; cleared when a Talk ends so none reach the next. */
    private val timers = Handler(Looper.getMainLooper())
    private var turns: TalkTurns? = null
    private var session: LiveLink? = null
    private var voice: AnnaVoice? = null
    private var mic: TalkMic? = null
    private var sounds: TalkSounds? = null
    private var focus: AudioFocusRequest? = null
    private var lessonFuture: ListenableFuture<MediaController>? = null
    private var lesson: MediaController? = null
    private var lessonWasPlaying = false
    private var startedAt = 0L

    /** When the last "no connection" tone finishes, so a Talk that ends on it lets it play out. */
    private var toneUntil = 0L

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
        if (waitingForFocus) giveLessonBack(resume = true)
        super.onDestroy()
    }

    private fun begin() {
        startedAt = SystemClock.elapsedRealtime()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "No microphone permission: no Talk")
            Talk.screen = null
            stopSelf()
            return
        }
        // Voice practice gives the mic up; it comes back by its own rule after the Talk (spec §5).
        RecorderService.stop(this)
        goForeground()
        turns = TalkTurns()
        publish()
        // The 30-min cap counts from here, wall-clock (spec §6 step 9).
        timers.postDelayed({ handle(TalkEvent.TimeUp) }, TalkConfig.TALK_CAP_MS)
        // Pause the lesson first, then take focus: taking it first would pause the lesson by
        // focus loss and hide whether it was playing (spec §9).
        pauseLessonThen { requestFocus() }
        voice = AnnaVoice().also { sounds = TalkSounds(it.attributes) }
        mic = TalkMic(
            context = this,
            detector = { SileroVad(this) },
            main = main,
            onEvent = { e -> main.post { handle(TalkEvent.Mic(e)) } },
            onFailed = { main.post { Log.w(TAG, "mic failed: ending the Talk"); handle(TalkEvent.EndPressed) } },
        ).also { it.start() }
        val prompt = assets.open(TalkConfig.PROMPT_ASSET).bufferedReader().use { it.readText() }.trim()
        session = LiveLink(
            connect = { resume, onEvent, onLost ->
                LiveSession(BuildConfig.GEMINI_API_KEY, prompt, onEvent, onLost, resume)
            },
            post = { block -> main.post(block) },
            onEvent = { e -> handle(TalkEvent.Server(e)) },
            onLost = { loss -> handle(lostEvent(loss)) },
        ).also { it.open() }
        // Connecting for up to 5 s (spec §8).
        handle(TalkEvent.Start)
    }

    /** What a lost session means to the state machine; the loss is classified in [LiveProtocol.classifyLoss]. */
    private fun lostEvent(loss: LiveLoss): TalkEvent = when (loss) {
        LiveLoss.Network -> TalkEvent.SessionLost
        LiveLoss.HandleRefused -> TalkEvent.ResumeRefused
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
                TalkEffect.StartActivity -> session?.send(LiveProtocol.activityStart())
                is TalkEffect.SendAudio -> if (effect.pcm.isNotEmpty()) session?.send(LiveProtocol.audio(effect.pcm))
                TalkEffect.EndActivity -> session?.send(LiveProtocol.activityEnd())
                TalkEffect.AwaitDrain -> voice?.whenDrained { main.post { handle(TalkEvent.PlaybackDrained) } }
                is TalkEffect.After -> timers.postDelayed({ handle(effect.event) }, effect.ms)
                TalkEffect.OpenMic -> mic?.open()
                TalkEffect.CloseMic -> mic?.close()
                TalkEffect.PlayBeep -> sounds?.beep()
                TalkEffect.PlayTick -> sounds?.tick()
                TalkEffect.End -> finishTalk()
                TalkEffect.StopAnna -> voice?.stop()
                is TalkEffect.Volume -> voice?.volume(effect.level)
                TalkEffect.EndWhenFocusBack -> finishTalk(LessonBack.WhenFocusBack)
                TalkEffect.EndLessonPaused -> finishTalk(LessonBack.Never)
                is TalkEffect.Reconnect -> reconnect(effect.handle)
                TalkEffect.PlayNoConnection -> sounds?.let {
                    it.noConnection()
                    toneUntil = SystemClock.elapsedRealtime() + it.noConnectionMs
                }
                TalkEffect.NewSession -> {
                    Log.i(TAG, "new session at ${SystemClock.elapsedRealtime() - startedAt} ms")
                    session?.open()
                }
                is TalkEffect.GiveUp -> finishTalk(LessonBack.AsBefore, effect.note)
            }
        }
    }

    /**
     * Swaps the Live session for one resuming with [handle] (spec §3): the old socket is closed
     * before the new one opens. Silent: no state change, no sound. Ticket 18 builds on this.
     */
    private fun reconnect(handle: String) {
        Log.i(TAG, "reconnecting at ${SystemClock.elapsedRealtime() - startedAt} ms")
        session?.open(handle)
    }

    private fun publish() {
        val t = turns ?: return
        Talk.screen = if (t.state == TurnState.Ended) null else TalkScreenState(t.state, t.annaLine, t.silence)
    }

    /**
     * When the lesson gets its turn back after a Talk (spec §9, §10). [AsBefore]: now, but as it
     * was before the tap, not by the resume rule (spec §8, a Talk that never connected).
     */
    private enum class LessonBack { Now, WhenFocusBack, Never, AsBefore }

    /** The Talk is over but holds focus until it returns, then resumes the lesson (spec §10). */
    private var waitingForFocus = false

    /**
     * Ends at once (spec §9): socket, Anna, focus, screen, then the lesson by the resume rule.
     * A "no connection" tone still playing finishes before the lesson comes back. [note] shows
     * on the player.
     */
    private fun finishTalk(lessonBack: LessonBack = LessonBack.Now, note: String? = null) {
        turns = null
        timers.removeCallbacksAndMessages(null)
        mic?.stop(); mic = null
        session?.close(); session = null
        voice?.release(); voice = null
        val toneLeft = (toneUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val s = sounds; sounds = null
        main.postDelayed({ s?.release() }, toneLeft)
        Talk.screen = null
        Talk.note = note
        Log.i(TAG, "Talk ended after ${SystemClock.elapsedRealtime() - startedAt} ms")
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (lessonBack == LessonBack.WhenFocusBack && focus != null) {
            // Played now, the lesson would only be held back by the call: keep the focus
            // request so its return is heard, and resume then.
            waitingForFocus = true
            return
        }
        val back = { giveLessonBack(resume = lessonBack != LessonBack.Never, asBefore = lessonBack == LessonBack.AsBefore) }
        // Let the tone play out first; a new Talk started meanwhile has the lesson now.
        if (toneLeft > 0) main.postDelayed({ if (turns == null) back() }, toneLeft) else back()
    }

    private fun giveLessonBack(resume: Boolean, asBefore: Boolean = false) {
        waitingForFocus = false
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        if (resume) resumeLesson(asBefore) else releaseLesson()
        stopSelf()
    }

    private fun releaseLesson() {
        val future = lessonFuture ?: return
        lessonFuture = null
        lesson = null
        MediaController.releaseFuture(future)
    }

    /** Audio focus changes become Talk events (spec §10). */
    private fun onFocusChange(change: Int) {
        Log.i(TAG, "focus change $change")
        if (waitingForFocus) {
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> giveLessonBack(resume = true)
                AudioManager.AUDIOFOCUS_LOSS -> giveLessonBack(resume = false)
            }
            return
        }
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> handle(TalkEvent.FocusBack)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> handle(TalkEvent.FocusPaused)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> handle(TalkEvent.FocusDucked)
            AudioManager.AUDIOFOCUS_LOSS -> handle(TalkEvent.FocusGone)
        }
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

    private fun resumeLesson(asBefore: Boolean) {
        val future = lessonFuture ?: return
        lessonFuture = null
        val c = lesson ?: run { MediaController.releaseFuture(future); return }
        lesson = null
        val wasPlaying = lessonWasPlaying
        if (asBefore) {
            if (wasPlaying) c.play()
        } else {
            resumeAfterTalk(wasPlaying, c.currentPosition)?.let { at ->
                c.seekTo(at)
                c.play()
            }
        }
        // Let the commands reach the session before letting go of the controller.
        main.postDelayed({ c.release() }, 1_000)
    }

    private fun requestFocus() {
        val v = voice ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(v.attributes)
            // Duck Anna ourselves, so a navigation prompt reaches us as an event (spec §10).
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change -> onFocusChange(change) }, main)
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
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION_ID, n)
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
