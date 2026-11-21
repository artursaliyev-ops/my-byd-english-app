package app.bydplayer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The UI6 glass tokens (docs/ui6-glass-spec.md §2), measured on the car. Glass is a white
 * overlay on the wallpaper that shows through the window; blue marks primary and selected.
 */
@Immutable
data class Ui6Colors(
    val blue: Color = Color(0xFF3388FF),
    val blueOn: Color = Color.White,
    /** [blue] 15 % darker, for a pressed blue control. */
    val bluePressed: Color = Color(0xFF2B74D9),
    val glass: Color = Color.White.copy(alpha = 0.10f),
    val glassPressed: Color = Color.White.copy(alpha = 0.20f),
    val divider: Color = Color.White.copy(alpha = 0.06f),
    val missingOutline: Color = Color.White.copy(alpha = 0.15f),
    val inset: Color = Color.Black.copy(alpha = 0.35f),
    val done: Color = Color(0xFF6FD08C),
    val text: Color = Color.White,
    val textSecondary: Color = Color.White.copy(alpha = 0.70f),
    val textDisabled: Color = Color.White.copy(alpha = 0.35f),
)

object Ui6 {
    val colors: Ui6Colors
        @Composable @ReadOnlyComposable get() = LocalUi6.current

    val Radius = 10.dp
    val Gap = 12.dp

    /** Scrim under all content (§1): normal, and on a wallpaper that reports itself as light. */
    const val SCRIM = 0.30f
    const val SCRIM_LIGHT_WALLPAPER = 0.55f

    /** Window blur behind the app on API 31+: 40 dp at 240 dpi. */
    const val BLUR_RADIUS_PX = 60

    // Motion (§7): one curve, no bounce.
    const val SELECT_MS = 150
    const val SCREEN_MS = 250
    const val ACTIVE_LINE_MS = 200
    const val PLAY_PAUSE_MS = 150
    const val PRESS_IN_MS = 100
    const val PRESS_OUT_MS = 150

    fun <T> tween(ms: Int): TweenSpec<T> = tween(ms, easing = FastOutSlowInEasing)
}

private val LocalUi6 = staticCompositionLocalOf { Ui6Colors() }

private fun materialColors(t: Ui6Colors) = darkColorScheme(
    primary = t.blue,
    onPrimary = t.blueOn,
    background = Color.Transparent,
    onBackground = t.text,
    surface = t.glass,
    onSurface = t.text,
    secondaryContainer = t.glass,
    onSecondaryContainer = t.text,
    outline = t.missingOutline,
    error = t.text,
)

// Larger than phone defaults: the screen is read at arm's length in a moving car.
private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 34.sp),
    titleLarge = TextStyle(fontSize = 28.sp),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp),
    labelLarge = TextStyle(fontSize = 22.sp),
)

@Composable
fun BydTheme(content: @Composable () -> Unit) {
    val tokens = Ui6Colors()
    CompositionLocalProvider(LocalUi6 provides tokens) {
        MaterialTheme(colorScheme = materialColors(tokens), typography = Type, content = content)
    }
}
