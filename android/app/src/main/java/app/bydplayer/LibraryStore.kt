package app.bydplayer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit

/**
 * Remembers the picked lesson folder and which lessons are completed. The folder's
 * read permission is persisted with the system, so the app keeps access across restarts.
 */
class LibraryStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)

    /** The saved folder, or null if none was picked or the system no longer grants access. */
    fun folder(): Uri? {
        val uri = prefs.getString(KEY_TREE, null)?.let(Uri::parse) ?: return null
        val granted = app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        return uri.takeIf { granted }
    }

    /** True when a folder was saved but its permission is gone (e.g. app data restored). */
    fun folderAccessLost(): Boolean = prefs.contains(KEY_TREE) && folder() == null

    fun setFolder(uri: Uri) {
        val resolver = app.contentResolver
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Drop grants for previously picked folders; the system caps how many an app may hold.
        resolver.persistedUriPermissions
            .filter { it.uri != uri }
            .forEach { runCatching { resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        prefs.edit { putString(KEY_TREE, uri.toString()) }
    }

    fun completed(): Set<Int> =
        prefs.getStringSet(KEY_COMPLETED, emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()

    fun setCompleted(lesson: Int, done: Boolean) {
        val next = completed().let { if (done) it + lesson else it - lesson }
        prefs.edit { putStringSet(KEY_COMPLETED, next.map(Int::toString).toSet()) }
    }

    /** Calls [onChange] whenever the completed set changes. Keep the returned listener referenced. */
    fun onCompletedChanged(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY_COMPLETED) onChange() }
            .also(prefs::registerOnSharedPreferenceChangeListener)

    private companion object {
        const val KEY_TREE = "tree_uri"
        const val KEY_COMPLETED = "completed"
    }
}
