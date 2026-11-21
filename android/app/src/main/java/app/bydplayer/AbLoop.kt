package app.bydplayer

/**
 * A-B repeat points within one lesson, in milliseconds. One button drives it:
 * the first mark sets A, the second sets B and starts looping, the third clears.
 */
class AbLoop {
    var a: Long? = null
        private set
    var b: Long? = null
        private set

    val looping get() = a != null && b != null

    fun mark(position: Long) {
        val start = a
        when {
            start == null -> a = position
            // B must come after A. Marking at or before A (or right after it) moves A instead.
            b == null -> if (position >= start + MIN_LOOP_MS) b = position else a = position
            else -> clear()
        }
    }

    fun clear() {
        a = null
        b = null
    }

    /** Where to jump when playback is at [position]: A once B is reached, otherwise null. */
    fun jumpFor(position: Long): Long? {
        val start = a ?: return null
        val end = b ?: return null
        return if (position >= end) start else null
    }

    companion object {
        const val MIN_LOOP_MS = 1_000L
    }
}
