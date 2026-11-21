package app.bydplayer.talk

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bydplayer.GlassButton
import app.bydplayer.GlassLabel
import app.bydplayer.Ui6
import app.bydplayer.Ui6Colors

/**
 * The full-screen Talk screen (spec §7, variant A of the prototype): one big shape in the middle
 * with one state word under it, Anna's latest line in small text at the bottom, and End Talk at
 * the bottom right, which works in every state. It sits on the UI6 backdrop; the lesson UI is
 * hidden. The learner's words are never shown.
 */
@Composable
fun TalkScreen(screen: TalkScreenState) {
    val context = LocalContext.current
    // Swallow taps so nothing underneath reacts.
    Box(
        Modifier.fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Column(Modifier.align(Alignment.Center).padding(bottom = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TalkShape(screen.state, screen.silence, screen.quietSince, 320.dp)
            Spacer(Modifier.height(24.dp))
            Text(word(screen.state), fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = Ui6.colors.text)
        }
        Text(
            screen.annaLine,
            Modifier.align(Alignment.BottomStart).fillMaxWidth(0.72f).padding(bottom = 20.dp),
            fontSize = 24.sp,
            color = Ui6.colors.textSecondary,
            textAlign = TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        GlassButton(onClick = { Talk.end(context) }, modifier = Modifier.align(Alignment.BottomEnd).heightIn(min = 80.dp)) {
            GlassLabel("End Talk")
        }
    }
}

private fun word(state: TurnState) = when (state) {
    TurnState.Connecting -> "Connecting…"
    TurnState.Thinking -> "Thinking"
    TurnState.Anna -> "Anna"
    TurnState.You -> "Your turn"
    TurnState.NoSignal -> "No signal"
    TurnState.Paused -> "Paused"
    TurnState.Ended -> ""
}

@Composable
private fun TalkShape(state: TurnState, silence: Float, quietSince: Long?, size: Dp) {
    val pulse by rememberInfiniteTransition(label = "talk shape").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "pulse",
    )
    val quiet = quietProgress(quietSince)
    val c = Ui6.colors
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        when (state) {
            TurnState.Anna -> annaRings(r, pulse, c.done)
            TurnState.Thinking -> thinkingDots(r, pulse, lit = c.text, dim = c.textDisabled)
            TurnState.You -> yourTurn(r, silence, quiet, c)
            TurnState.NoSignal -> brokenRing(r, c.textDisabled)
            TurnState.Connecting, TurnState.Paused, TurnState.Ended ->
                drawCircle(c.textDisabled, radius = r * 0.6f, style = Stroke(10.dp.toPx()))
        }
    }
}

/** How full the slow quiet ring is, 0 to 1 over [TalkConfig.Timing.QUIET_MS] from [since], redrawn every frame while it runs. */
@Composable
private fun quietProgress(since: Long?): Float {
    val progress by produceState(0f, since) {
        if (since == null) return@produceState
        while (true) {
            value = ((SystemClock.uptimeMillis() - since).toFloat() / TalkConfig.Timing.QUIET_MS).coerceIn(0f, 1f)
            if (value >= 1f) break
            withFrameMillis { }
        }
    }
    return if (since == null) 0f else progress
}

/** Green rings going out: Anna's voice. */
private fun DrawScope.annaRings(r: Float, pulse: Float, green: Color) {
    for (k in 0..2) {
        val f = (pulse + k / 3f) % 1f
        drawCircle(green.copy(alpha = 0.5f * (1 - f)), radius = r * (0.55f + 0.45f * f), style = Stroke(6.dp.toPx()))
    }
    drawCircle(green, radius = r * 0.5f)
}

/**
 * A blue disc. Before the learner speaks, a thin slow ring fills toward the nudge (spec §6
 * step 6); once they speak, the white silence ring fills clockwise over the silence window
 * (spec §6 step 4).
 */
private fun DrawScope.yourTurn(r: Float, silence: Float, quiet: Float, c: Ui6Colors) {
    drawCircle(c.blue, radius = r * 0.55f)
    val stroke = 10.dp.toPx()
    val inset = r * 0.25f
    drawCircle(c.missingOutline, radius = r * 0.75f, style = Stroke(stroke))
    val ring = { fraction: Float, color: Color, width: Float ->
        drawArc(
            color, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
            topLeft = Offset(center.x - r + inset, center.y - r + inset),
            size = Size((r - inset) * 2, (r - inset) * 2),
            style = Stroke(width, cap = StrokeCap.Round),
        )
    }
    if (silence > 0f) ring(silence, c.text, stroke) else if (quiet > 0f) ring(quiet, c.textSecondary, stroke / 2)
}

/** A grey ring broken into pieces: No signal (spec §7). */
private fun DrawScope.brokenRing(r: Float, grey: Color) {
    val stroke = 10.dp.toPx()
    for (k in 0 until 5) {
        drawArc(
            grey, startAngle = -90f + k * 72f + 10f, sweepAngle = 52f, useCenter = false,
            topLeft = Offset(center.x - r * 0.6f, center.y - r * 0.6f),
            size = Size(r * 1.2f, r * 1.2f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Three white dots, lit in turn. */
private fun DrawScope.thinkingDots(r: Float, pulse: Float, lit: Color, dim: Color) {
    for (k in 0..2) {
        val on = ((pulse * 3).toInt() % 3) == k
        drawCircle(
            if (on) lit else dim,
            radius = r * 0.12f,
            center = Offset(center.x + (k - 1) * r * 0.38f, center.y),
        )
    }
}
