package app.bydplayer.talk

import java.io.File

/**
 * Anna's Notes on disk (Notes spec §5): one file, written to a temp file and renamed, so a car
 * switched off mid-write leaves the old Notes whole. All reads and writes go through one lock.
 *
 * A **wipe generation** (in memory) guards against wiped Notes coming back: a Talk records it when
 * it starts, and its reply is stored only if no wipe happened since. [onChange] runs after every
 * write or wipe (on the writing thread), so Settings can redraw.
 */
class NotesStore(private val file: File, private val onChange: (Notes) -> Unit = {}) {
    private val lock = Any()
    private var generation = 0L

    fun read(): Notes = synchronized(lock) {
        if (!file.exists()) Notes() else runCatching { Notes.fromJson(file.readText()) }.getOrDefault(Notes())
    }

    /** What a Talk records when it starts. */
    fun generation(): Long = synchronized(lock) { generation }

    /**
     * Reads the latest Notes, lets [change] build the new ones, and writes them, all under the lock,
     * unless a wipe came after [since]. Returns whether it wrote.
     */
    fun update(since: Long, change: (Notes) -> Notes?): Boolean {
        val written = synchronized(lock) {
            if (generation != since) return false
            val next = change(read()) ?: return false
            if (generation != since) return false
            write(next)
            next
        }
        onChange(written)
        return true
    }

    /** "Delete Anna's notes": the file goes, and any Talk or call still out writes nothing. */
    fun wipe() {
        synchronized(lock) {
            generation++
            file.delete()
            tmp().delete()
        }
        onChange(Notes())
    }

    private fun write(notes: Notes) {
        file.parentFile?.mkdirs()
        val tmp = tmp()
        tmp.writeText(Notes.toJson(notes))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun tmp() = File(file.parentFile, "${file.name}.tmp")
}
