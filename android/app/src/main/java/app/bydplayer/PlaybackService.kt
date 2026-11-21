package app.bydplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Owns the player so audio keeps going when the app leaves the screen. Everything that
 * must keep working in the background lives here, not in the UI: saving the resume
 * point, the A-B loop, and marking a lesson completed when it plays to the end.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var playback: PlaybackStore
    private lateinit var library: LibraryStore
    private val handler = Handler(Looper.getMainLooper())
    private val ab = AbLoop()
    private var currentLesson: Int? = null
    private var lastSaveAt = 0L

    /** Runs while playing: checks the A-B loop often and saves the position every few seconds. */
    private val tick = object : Runnable {
        override fun run() {
            val player = session?.player ?: return
            ab.jumpFor(player.currentPosition)?.let(player::seekTo)
            if (SystemClock.elapsedRealtime() - lastSaveAt >= SAVE_EVERY_MS) saveResume()
            if (player.isPlaying) handler.postDelayed(this, TICK_MS)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        playback = PlaybackStore(this)
        library = LibraryStore(this)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SKIP_MS)
            .setSeekForwardIncrementMs(SKIP_MS)
            // Lets the volume buttons on the player screen move the system media volume.
            .setDeviceVolumeControlEnabled(true)
            .build()
        player.setPlaybackSpeed(playback.speed())
        player.addListener(PlayerEvents(player))
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()
        publishAb()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        saveResume()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun saveResume() {
        val player = session?.player ?: return
        val lesson = player.currentMediaItem?.mediaId?.toIntOrNull() ?: return
        playback.saveResume(lesson, player.currentPosition)
        lastSaveAt = SystemClock.elapsedRealtime()
    }

    /** The UI reads the A-B points from the session extras, so they survive the screen closing. */
    private fun publishAb() {
        session?.setSessionExtras(
            Bundle().apply {
                putLong(EXTRA_AB_A, ab.a ?: -1L)
                putLong(EXTRA_AB_B, ab.b ?: -1L)
            },
        )
    }

    private inner class PlayerEvents(private val player: Player) : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(tick)
            if (isPlaying) handler.post(tick) else saveResume()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Auto-advance means the previous lesson played to its end.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                currentLesson?.let { library.setCompleted(it, true) }
            }
            currentLesson = mediaItem?.mediaId?.toIntOrNull()
            ab.clear()
            publishAb()
            saveResume()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state != Player.STATE_ENDED) return
            // The last lesson in the queue finished. Mark it and resume from its start next time.
            val lesson = currentLesson ?: return
            library.setCompleted(lesson, true)
            playback.saveResume(lesson, 0L)
            ab.clear()
            publishAb()
        }

        override fun onPlaybackParametersChanged(params: PlaybackParameters) {
            playback.saveSpeed(params.speed)
        }

        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK && !player.isPlaying) saveResume()
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(CMD_AB_MARK)
                        .add(CMD_AB_CLEAR)
                        .build(),
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_AB_MARK.customAction -> ab.mark(session.player.currentPosition)
                CMD_AB_CLEAR.customAction -> ab.clear()
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            publishAb()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        const val SKIP_MS = 5_000L
        private const val TICK_MS = 100L
        private const val SAVE_EVERY_MS = 5_000L

        const val EXTRA_AB_A = "ab_a"
        const val EXTRA_AB_B = "ab_b"

        /** Sets A, then B, then clears; the service uses the player's own position. */
        val CMD_AB_MARK = SessionCommand("app.bydplayer.AB_MARK", Bundle.EMPTY)
        val CMD_AB_CLEAR = SessionCommand("app.bydplayer.AB_CLEAR", Bundle.EMPTY)
    }
}
