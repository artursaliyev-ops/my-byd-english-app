package app.bydplayer.talk

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
            TalkShape(screen.state, screen.silence, 320.dp)
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
    TurnState.Paused -> "Paused"
    TurnState.Ended -> ""
}

private val AnnaGreen = Color(0xFF6FD08C)
private val Grey = Color.White.copy(alpha = 0.35f)

@Composable
private fun TalkShape(state: TurnState, silence: Float, size: Dp) {
    val pulse by rememberInfiniteTransition(label = "talk shape").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "pulse",
    )
    val blue = Ui6.colors.blue
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        when (state) {
            TurnState.Anna -> annaRings(r, pulse)
            TurnState.Thinking -> thinkingDots(r, pulse)
            TurnState.You -> yourTurn(r, silence, blue)
            TurnState.Connecting, TurnState.Paused, TurnState.Ended -> drawCircle(Grey, radius = r * 0.6f, style = Stroke(10.dp.toPx()))
        }
    }
}

/** Green rings going out: Anna's voice. */
private fun DrawScope.annaRings(r: Float, pulse: Float) {
    for (k in 0..2) {
        val f = (pulse + k / 3f) % 1f
        drawCircle(AnnaGreen.copy(alpha = 0.5f * (1 - f)), radius = r * (0.55f + 0.45f * f), style = Stroke(6.dp.toPx()))
    }
    drawCircle(AnnaGreen, radius = r * 0.5f)
}

/** A blue disc; the white silence ring fills clockwise over the silence window (spec §6 step 4). */
private fun DrawScope.yourTurn(r: Float, silence: Float, blue: Color) {
    drawCircle(blue, radius = r * 0.55f)
    val stroke = 10.dp.toPx()
    val inset = r * 0.25f
    drawCircle(Color.White.copy(alpha = 0.15f), radius = r * 0.75f, style = Stroke(stroke))
    if (silence > 0f) {
        drawArc(
            Color.White, startAngle = -90f, sweepAngle = 360f * silence, useCenter = false,
            topLeft = Offset(center.x - r + inset, center.y - r + inset),
            size = Size((r - inset) * 2, (r - inset) * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Three white dots, lit in turn. */
private fun DrawScope.thinkingDots(r: Float, pulse: Float) {
    for (k in 0..2) {
        val lit = ((pulse * 3).toInt() % 3) == k
        drawCircle(
            Color.White.copy(alpha = if (lit) 0.95f else 0.35f),
            radius = r * 0.12f,
            center = Offset(center.x + (k - 1) * r * 0.38f, center.y),
        )
    }
}
