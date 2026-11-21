package app.bydplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * What the media buttons (steering wheel, DiLink now-playing widget, Bluetooth keys) do
 * with ⏮ ⏭. The player screen's own controls never follow it. See GLOSSARY.md.
 */
enum class ButtonMode(val label: String) {
    Lessons("Previous / next lesson"),
    Skip("−5 s / +5 s"),
    ;

    /** What a ⏮ ([forward] = false) or ⏭ press does in this mode. */
    fun effectOf(forward: Boolean): ButtonEffect = when (this) {
        Lessons -> if (forward) ButtonEffect.NextLesson else ButtonEffect.PreviousLesson
        Skip -> if (forward) ButtonEffect.Forward5 else ButtonEffect.Back5
    }

    companion object {
        val DEFAULT = Skip
    }
}

enum class ButtonEffect(val label: String) {
    PreviousLesson("previous lesson"),
    NextLesson("next lesson"),
    Back5("−5 s"),
    Forward5("+5 s"),
}

/** App settings. Read by [PlaybackService] on every media-button press, so changes apply in the background. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun buttonMode(): ButtonMode =
        prefs.getString(KEY_BUTTON_MODE, null)?.let { name -> ButtonMode.entries.firstOrNull { it.name == name } }
            ?: ButtonMode.DEFAULT

    fun setButtonMode(mode: ButtonMode) {
        prefs.edit { putString(KEY_BUTTON_MODE, mode.name) }
    }

    /** Calls [onChange] when the button mode changes. Keep the returned listener referenced. */
    fun onButtonModeChanged(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY_BUTTON_MODE) onChange() }
            .also(prefs::registerOnSharedPreferenceChangeListener)

    private companion object {
        const val KEY_BUTTON_MODE = "button_mode"
    }
}
