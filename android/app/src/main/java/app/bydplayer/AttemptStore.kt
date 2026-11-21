package app.bydplayer

import java.io.File

/** Which Attempt: its kind and the start of its speaking pause, in lesson ms. */
data class AttemptKey(val kind: AttemptKind, val pauseStartMs: Long) {
    val fileName get() = "%s-%07d.m4a".format(kind.fileTag, pauseStartMs)

    companion object {
        private val NAME = Regex("(answer|echo)-(\\d+)\\.m4a")

        fun parse(name: String): AttemptKey? {
            val m = NAME.matchEntire(name) ?: return null
            val kind = AttemptKind.entries.first { it.fileTag == m.groupValues[1] }
            return AttemptKey(kind, m.groupValues[2].toLong())
        }
    }
}

/**
 * Attempts on disk ("Choose how Attempts are stored"): one folder per lesson under [root],
 * named by transcript id, one `.m4a` per Attempt, no database. Keeps the [MAX_LESSONS]
 * lessons recorded most recently. Each folder remembers the transcript fingerprint its
 * Attempts were cut against and is dropped when the transcript changes.
 */
class AttemptStore(private val root: File, private val now: () -> Long = System::currentTimeMillis) {

    /** The Attempts of a lesson. Drops them first if they were cut against another transcript. */
    fun list(lessonId: String, fingerprint: String): Set<AttemptKey> {
        val dir = File(root, lessonId)
        if (!dir.isDirectory) return emptySet()
        if (readFingerprint(dir) != fingerprint) {
            dir.deleteRecursively()
            return emptySet()
        }
        return dir.list().orEmpty().mapNotNull(AttemptKey::parse).toSet()
    }

    fun file(lessonId: String, key: AttemptKey) = File(File(root, lessonId), key.fileName)

    /**
     * Saves an Attempt, replacing the one for the same speaking pause. [write] fills a temp
     * file, which is then renamed over the old one, so a crash never leaves half an Attempt.
     */
    fun save(lessonId: String, fingerprint: String, key: AttemptKey, write: (File) -> Unit) {
        val dir = File(root, lessonId)
        if (dir.isDirectory && readFingerprint(dir) != fingerprint) dir.deleteRecursively()
        dir.mkdirs()
        File(dir, FINGERPRINT).writeText(fingerprint)
        val tmp = File(dir, "${key.fileName}.tmp")
        write(tmp)
        val target = File(dir, key.fileName)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        target.setLastModified(now())
        evictBeyondLimit(keep = lessonId)
    }

    /** "Delete my answers". */
    fun deleteAll() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun evictBeyondLimit(keep: String) {
        val lessons = root.listFiles { f -> f.isDirectory }.orEmpty()
            .map { it to newestAttempt(it) }
            .filter { it.second > 0 }
        if (lessons.size <= MAX_LESSONS) return
        lessons.filter { it.first.name != keep }
            .sortedBy { it.second }
            .take(lessons.size - MAX_LESSONS)
            .forEach { it.first.deleteRecursively() }
    }

    private fun newestAttempt(dir: File): Long =
        dir.listFiles { f -> AttemptKey.parse(f.name) != null }.orEmpty().maxOfOrNull { it.lastModified() } ?: 0L

    private fun readFingerprint(dir: File) = File(dir, FINGERPRINT).takeIf { it.exists() }?.readText()

    companion object {
        const val MAX_LESSONS = 5
        private const val FINGERPRINT = "transcript.fingerprint"
    }
}
