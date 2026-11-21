package app.bydplayer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Pimsleur level I, II or III. Each holds 30 lessons; lesson numbers run 1-90 across them. */
enum class Level(val roman: String, val first: Int) {
    I("I", 1), II("II", 31), III("III", 61);

    val last get() = first + LESSONS_PER_LEVEL - 1
    val label get() = "$roman  ($first–$last)"
}

const val LESSONS_PER_LEVEL = 30

/** One lesson mp3 found in the picked folder. [number] is the 1-90 course number. */
data class Lesson(val level: Level, val number: Int, val fileName: String, val uri: Uri) {
    val title get() = "Lesson $number"
}

data class LibraryScan(val lessons: List<Lesson>, val duplicates: Int) {
    fun byLevel(level: Level) = lessons.filter { it.level == level }
}

// Pimsleur_I_01.mp3 .. Pimsleur_III_30.mp3. The number in the file is 1-30 within the level.
// Reading Lessons (Pimsleur_Reading_Lesson_NN.mp3) are out of scope and don't match.
private val LESSON_FILE = Regex("""^Pimsleur_(I{1,3})_(\d{1,2})\.mp3$""", RegexOption.IGNORE_CASE)

/** Returns the level and 1-90 course number for a lesson file name, or null if it isn't one. */
fun parseLessonFileName(name: String): Pair<Level, Int>? {
    val m = LESSON_FILE.matchEntire(name.trim()) ?: return null
    val level = Level.valueOf(m.groupValues[1].uppercase())
    val inLevel = m.groupValues[2].toInt()
    if (inLevel !in 1..LESSONS_PER_LEVEL) return null
    return level to level.first + inLevel - 1
}

/**
 * Walks the picked SAF tree (a few levels deep, so the user can pick either the course
 * folder or one of its I/II/III subfolders) and collects the lesson mp3s, sorted 1-90.
 * Throws if the tree can't be listed (permission lost, USB stick removed).
 */
fun scanLibrary(context: Context, tree: Uri): LibraryScan {
    val found = sortedMapOf<Int, Lesson>()
    var duplicates = 0
    var visited = 0
    fun walk(docId: String, depth: Int) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cursor = context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        ) ?: error("Folder can't be listed")
        cursor.use { c ->
            while (c.moveToNext() && visited < 5000) {
                visited++
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (depth < 4) walk(id, depth + 1)
                    continue
                }
                val (level, number) = parseLessonFileName(name) ?: continue
                if (number in found) {
                    duplicates++
                } else {
                    found[number] = Lesson(level, number, name, DocumentsContract.buildDocumentUriUsingTree(tree, id))
                }
            }
        }
    }
    walk(DocumentsContract.getTreeDocumentId(tree), 0)
    return LibraryScan(found.values.toList(), duplicates)
}

/** Human-readable folder name for a tree URI, e.g. "Music/Pimsleur_I". */
fun treeLabel(tree: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifEmpty { "/" } }
        .getOrDefault(tree.toString())
