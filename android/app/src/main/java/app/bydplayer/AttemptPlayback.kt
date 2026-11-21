package app.bydplayer

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import java.io.File

/**
 * "▶ Me" / "▶ Echo" playback (spec §7): pause the lesson, play the Attempt, wait 0.4 s,
 * play the speaker's line from the lesson audio, then put the lesson back where it was,
 * still paused. Main thread only.
 */
object AttemptPlayer {
    enum class Phase { Attempt, Speaker }

    /** What is playing: the line's start (identifies the chip row), the kind, and the phase. */
    data class Now(val lineStartMs: Long, val kind: AttemptKind, val phase: Phase)

    var now by mutableStateOf<Now?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var restoreTo: Long? = null
    private var controller: MediaController? = null

    fun play(controller: MediaController, file: File, kind: AttemptKind, line: Segment) {
        stop()
        this.controller = controller
        restoreTo = controller.currentPosition
        LessonClock.reviewing = true
        LessonClock.jump()
        controller.pause()
        now = Now(line.startMs, kind, Phase.Attempt)
        player = try {
            MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
                setOnCompletionListener {
                    release()
                    player = null
                    main.postDelayed({ speaker(controller, line, kind) }, GAP_MS)
                }
                start()
            }
        } catch (e: Exception) {
            Log.w("VoicePractice", "Couldn't play ${file.name}", e)
            stop()
            null
        }
    }

    private fun speaker(controller: MediaController, line: Segment, kind: AttemptKind) {
        now = Now(line.startMs, kind, Phase.Speaker)
        controller.seekTo(line.startMs)
        controller.play()
        val ms = ((line.endMs - line.startMs) / controller.playbackParameters.speed).toLong() + 150
        main.postDelayed({ stop() }, ms)
    }

    /** Stops playback and puts the lesson back where it was, paused. Safe to call any time. */
    fun stop() {
        main.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        val c = controller
        val at = restoreTo
        if (c != null && at != null) {
            c.pause()
            c.seekTo(at)
        }
        controller = null
        restoreTo = null
        if (LessonClock.reviewing) {
            LessonClock.reviewing = false
            LessonClock.jump()
        }
        now = null
    }

    const val GAP_MS = 400L
}

/**
 * The recording dot beside the lesson title (spec §8): red while recording, pulsing inside a
 * speaking pause, grey while the lesson is paused or "▶ Me" plays, hidden when off.
 */
@Composable
fun RecordingDot(recording: Boolean, capturing: Boolean) {
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "dot alpha",
    )
    Box(
        Modifier.size(22.dp)
            .alpha(if (recording && capturing) pulse else 1f)
            .background(if (recording) RecordingRed else Ui6.colors.textDisabled, CircleShape),
    )
}

private val RecordingRed = Color(0xFFFF453A)

/** A "▶ Me" / "▶ Echo" chip at the end of a transcript line. */
@Composable
fun AttemptChip(kind: AttemptKind, line: Segment, onPlay: () -> Unit) {
    val now = AttemptPlayer.now
    val mine = now != null && now.lineStartMs == line.startMs && now.kind == kind
    GlassButton(onClick = { if (mine) AttemptPlayer.stop() else onPlay() }, blue = mine, modifier = Modifier.height(56.dp), contentPadding = 18.dp) {
        GlassLabel(
            when {
                !mine -> "▶ ${kind.label}"
                now?.phase == AttemptPlayer.Phase.Attempt -> "■ ${kind.label}…"
                else -> "■ Speaker…"
            },
        )
    }
}
