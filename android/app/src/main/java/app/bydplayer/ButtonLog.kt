package app.bydplayer

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last few media-button signals the app received, newest first, for the **Last button**
 * line in Settings. ADB to the car doesn't work, so this is how the user reads in the car
 * what the steering wheel actually sends (key codes, long-press flag, key repeats).
 * In memory only: the service and the screen share the process. Main thread only.
 */
object ButtonLog {
    const val SIZE = 24

    val entries = mutableStateListOf<String>()

    fun key(event: KeyEvent, via: String) = add(describeKey(KeyEvent.keyCodeToString(event.keyCode), event.action, event.repeatCount, event.isLongPress, via))

    fun command(name: String, effect: ButtonEffect?, from: String) = add(describeCommand(name, effect, from))

    private fun add(text: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        entries.add(0, "$time  $text")
        while (entries.size > SIZE) entries.removeAt(entries.lastIndex)
    }
}

/** e.g. "key MEDIA_NEXT down · repeat 3 · long-press · via screen" */
fun describeKey(keyName: String, action: Int, repeatCount: Int, longPress: Boolean, via: String): String {
    val name = keyName.removePrefix("KEYCODE_")
    val act = when (action) {
        KeyEvent.ACTION_DOWN -> "down"
        KeyEvent.ACTION_UP -> "up"
        else -> "action $action"
    }
    return buildString {
        append("key $name $act")
        append(" · repeat $repeatCount")
        if (longPress) append(" · long-press")
        append(" · via $via")
    }
}

/** e.g. "cmd next → +5 s · from com.byd.launcher" */
fun describeCommand(name: String, effect: ButtonEffect?, from: String): String =
    "cmd $name → ${effect?.label ?: "as is"} · from $from"
