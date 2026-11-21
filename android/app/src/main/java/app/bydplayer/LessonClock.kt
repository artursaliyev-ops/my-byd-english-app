package app.bydplayer

import android.os.SystemClock

/**
 * Where the lesson is, for the recording thread. [PlaybackService] publishes a snapshot on
 * every player event and tick; the recorder reads it from its own thread and extrapolates
 * the position between snapshots. The service and the recorder share the process.
 */
object LessonClock {
    class Snapshot(
        val lesson: Int?,
        val positionMs: Long,
        val atRealtimeMs: Long,
        val playing: Boolean,
        val speed: Float,
        /** Changes on every jump: seek, ±5 s, A-B loop, lesson change, "▶ Me". */
        val epoch: Long,
    ) {
        fun positionNow(): Long =
            if (playing) positionMs + ((SystemClock.elapsedRealtime() - atRealtimeMs) * speed).toLong() else positionMs
    }

    @Volatile var snapshot = Snapshot(null, 0, 0, false, 1f, 0)
        private set

    /** True while "▶ Me" plays the speaker's line: that audio is never cut into an Attempt. */
    @Volatile var reviewing = false

    private var epoch = 0L

    fun publish(lesson: Int?, positionMs: Long, playing: Boolean, speed: Float, jumped: Boolean = false) {
        if (jumped) epoch++
        snapshot = Snapshot(lesson, positionMs, SystemClock.elapsedRealtime(), playing, speed, epoch)
    }

    /** Marks a jump without new player state (e.g. "▶ Me" starting or ending). */
    fun jump() {
        val s = snapshot
        publish(s.lesson, s.positionNow(), s.playing, s.speed, jumped = true)
    }
}
