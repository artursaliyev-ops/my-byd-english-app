package app.bydplayer

import android.content.Context
import androidx.core.content.edit

/** Where to resume (lesson + position) and the chosen speed. Written by [PlaybackService]. */
class PlaybackStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("playback", Context.MODE_PRIVATE)

    data class Resume(val lesson: Int, val positionMs: Long)

    fun resume(): Resume? {
        val lesson = prefs.getInt(KEY_LESSON, 0).takeIf { it > 0 } ?: return null
        return Resume(lesson, prefs.getLong(KEY_POSITION, 0L))
    }

    fun saveResume(lesson: Int, positionMs: Long) {
        prefs.edit { putInt(KEY_LESSON, lesson).putLong(KEY_POSITION, positionMs.coerceAtLeast(0L)) }
    }

    fun speed(): Float = prefs.getFloat(KEY_SPEED, 1f)

    fun saveSpeed(speed: Float) {
        prefs.edit { putFloat(KEY_SPEED, speed) }
    }

    private companion object {
        const val KEY_LESSON = "lesson"
        const val KEY_POSITION = "position_ms"
        const val KEY_SPEED = "speed"
    }
}
