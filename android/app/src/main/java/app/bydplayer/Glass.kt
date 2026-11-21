package app.bydplayer

import android.app.Activity
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val TAG = "BydGlass"

/**
 * Turns on the system blur of the wallpaper behind the window (API 31+), and keeps it in
 * step with the system: when blur is switched off (battery saver, thermal) the app simply
 * runs without it. Call from [Activity.onCreate]; returns the cleanup for onDestroy.
 */
fun Activity.enableWallpaperBlur(): () -> Unit {
    if (Build.VERSION.SDK_INT < 31) return {}
    val listener = java.util.function.Consumer<Boolean> { enabled ->
        window.setBackgroundBlurRadius(if (enabled) Ui6.BLUR_RADIUS_PX else 0)
    }
    windowManager.addCrossWindowBlurEnabledListener(mainExecutor, listener)
    return { windowManager.removeCrossWindowBlurEnabledListener(listener) }
}

/**
 * The full-window black scrim under all content (spec §1): 30 %, or 55 % when the wallpaper
 * reports itself as light. A wallpaper that reports no colours gets 30 %.
 */
@Composable
fun Ui6Backdrop(content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    var scrim by remember { mutableFloatStateOf(Ui6.SCRIM) }
    DisposableEffect(Unit) {
        val wm = WallpaperManager.getInstance(context)
        fun update(colors: WallpaperColors?) {
            val light = colors != null && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            scrim = if (light) Ui6.SCRIM_LIGHT_WALLPAPER else Ui6.SCRIM
            // Car check (spec §8): does BYD's live wallpaper report colours at all?
            Log.i(TAG, "Wallpaper colours: ${colors?.let { "primary=${it.primaryColor} hints=${it.colorHints}" } ?: "none"}")
        }
        update(wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM))
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) update(colors)
        }
        wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { wm.removeOnColorsChangedListener(listener) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)), content = content)
}

/**
 * A glass control: white 10 % (20 % while pressed), or BYD blue when [blue]. No ripple; the
 * fill fades between states (spec §4, §7). Disabled keeps the glass and dims the content.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blue: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(Ui6.Radius),
    contentPadding: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = Ui6.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = when {
            blue && pressed -> t.bluePressed
            blue -> t.blue
            pressed && enabled -> t.glassPressed
            else -> t.glass
        },
        animationSpec = Ui6.tween(if (pressed) Ui6.PRESS_IN_MS else if (blue) Ui6.SELECT_MS else Ui6.PRESS_OUT_MS),
        label = "glass fill",
    )
    val contentColor = when {
        !enabled -> t.textDisabled
        blue -> t.blueOn
        else -> t.text
    }
    Box(
        modifier
            .background(fill, shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/** Text label for a [GlassButton], in the button's content colour. */
@Composable
fun GlassLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current, maxLines = 1)
}

/**
 * A segmented control on a glass track (I/II/III tabs, Button mode). The selected segment is
 * filled blue; the fill fades rather than slides, as in UI6 (spec §7).
 */
@Composable
fun Segmented(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    segment: @Composable RowScope.(index: Int) -> Unit,
) {
    val t = Ui6.colors
    Row(modifier.background(t.glass, RoundedCornerShape(Ui6.Radius)).padding(4.dp)) {
        repeat(count) { i ->
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val isSelected = i == selected
            val fill by animateColorAsState(
                targetValue = when {
                    isSelected && pressed -> t.bluePressed
                    isSelected -> t.blue
                    pressed -> t.glass
                    else -> Color.Transparent
                },
                animationSpec = Ui6.tween(if (pressed) Ui6.PRESS_IN_MS else Ui6.SELECT_MS),
                label = "segment fill",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(fill, RoundedCornerShape(Ui6.Radius - 2.dp))
                    .clickable(interactionSource = source, indication = null) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides if (isSelected) t.blueOn else t.text) {
                    Row(verticalAlignment = Alignment.CenterVertically) { segment(i) }
                }
            }
        }
    }
}
