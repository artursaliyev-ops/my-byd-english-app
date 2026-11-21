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

    /** What holding ⏮ / ⏭ on the wheel does: the press action of the other mode. */
    fun holdEffectOf(forward: Boolean): ButtonEffect = when (this) {
        Lessons -> Skip
        Skip -> Lessons
    }.effectOf(forward)

    /**
     * What a media button does, given whether a Talk is active. During a Talk play/pause ends it
     * and ⏮ ⏭ and Wheel hold do nothing, so the lesson never moves behind the learner (spec §10).
     */
    fun actionOf(press: MediaPress, talking: Boolean): ButtonAction = when {
        talking -> if (press == MediaPress.PlayPause) ButtonAction.EndTalk else ButtonAction.Ignore
        else -> when (press) {
            MediaPress.PlayPause -> ButtonAction.AsUsual
            MediaPress.Previous -> ButtonAction.Do(effectOf(forward = false))
            MediaPress.Next -> ButtonAction.Do(effectOf(forward = true))
            MediaPress.HoldPrevious -> ButtonAction.Do(holdEffectOf(forward = false))
            MediaPress.HoldNext -> ButtonAction.Do(holdEffectOf(forward = true))
        }
    }

    companion object {
        val DEFAULT = Skip
    }
}

/** A media button press as [PlaybackService] sees it. */
enum class MediaPress { PlayPause, Previous, Next, HoldPrevious, HoldNext }

/** What [ButtonMode.actionOf] decides a media button press does. */
sealed interface ButtonAction {
    /** Do this effect on the lesson. */
    data class Do(val effect: ButtonEffect) : ButtonAction

    /** Play or pause the lesson as Media3 would. */
    data object AsUsual : ButtonAction

    /** End the Talk; the lesson resumes by the usual rule. */
    data object EndTalk : ButtonAction

    /** Do nothing. */
    data object Ignore : ButtonAction
}

/**
 * The BYD steering wheel sends a hold of ◀ / ▶ as its own key (one down, no repeats, to the
 * media session only), not as a long-press of MEDIA_PREVIOUS / MEDIA_NEXT. The keycodes are
 * BYD additions with no stock Android constant, so they are matched by name.
 * Returns false for ◀, true for ▶, null for any other key.
 */
fun wheelHoldDirection(keyName: String): Boolean? = when (keyName) {
    "KEYCODE_AUTO_MEDIA_PREVIOUS_LP" -> false
    "KEYCODE_AUTO_MEDIA_NEXT_LP" -> true
    else -> null
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

    /** "Record my answers" (voice practice). Off by default. */
    fun recordAnswers(): Boolean = prefs.getBoolean(KEY_RECORD_ANSWERS, false)

    fun setRecordAnswers(on: Boolean) {
        prefs.edit { putBoolean(KEY_RECORD_ANSWERS, on) }
    }

    private companion object {
        const val KEY_BUTTON_MODE = "button_mode"
        const val KEY_RECORD_ANSWERS = "record_answers"
    }
}
