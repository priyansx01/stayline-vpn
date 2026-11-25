package io.github.priyansx01.stayline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.priyansx01.stayline.ui.theme.Jewel
import io.github.priyansx01.stayline.ui.theme.LocalReducedMotion
import io.github.priyansx01.stayline.ui.theme.Motion
import io.github.priyansx01.stayline.ui.theme.Stayline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Mood { Off, Working, On, Alert }

@Composable
fun moodJewel(mood: Mood): Jewel {
    val c = Stayline.colors
    return when (mood) {
        Mood.Off, Mood.Alert -> c.ruby
        Mood.Working -> c.topaz
        Mood.On -> c.emerald
    }
}

/**
 * The connect button: a cut gem that glows ruby when off, turns topaz and
 * spins a ring while working, and settles into a breathing emerald once
 * the tunnel is up, with one ripple to mark the moment.
 */
@Composable
fun GemOrb(
    mood: Mood,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
) {
    val jewel = moodJewel(mood)
    val base by animateColorAsState(jewel.base, Motion.mood(), label = "base")
    val glow by animateColorAsState(jewel.glow, Motion.mood(), label = "glow")
    val deep by animateColorAsState(jewel.deep, Motion.mood(), label = "deep")
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current

    val infinite = rememberInfiniteTransition(label = "orb")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spin")
    val breathe by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val shimmer by infinite.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "shimmer")

    // One ripple each time the tunnel comes up, and a confirming tick.
    val ripple = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(mood) }
    LaunchedEffect(mood) {
        val cameUp = mood == Mood.On && previous != Mood.On
        previous = mood
        if (cameUp) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            if (!reduced) {
                ripple.snapTo(0f)
                ripple.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
            }
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, Motion.snappy(), label = "press")

    val moving = !reduced
    Canvas(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { contentDescription = label },
    ) {
        val r = this.size.minDimension * 0.31f
        val breath = if (moving && (mood == Mood.On || mood == Mood.Alert)) breathe else 0.5f

        // Halo.
        val haloAlpha = when (mood) {
            Mood.On -> 0.30f + 0.22f * breath
            Mood.Alert -> 0.22f + 0.18f * breath
            Mood.Working -> 0.32f
            Mood.Off -> 0.16f
        }
        val haloRadius = r * (2.0f + 0.12f * breath)
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = haloAlpha), Color.Transparent), center, haloRadius), haloRadius)

        // Ripple when the tunnel comes up.
        if (ripple.value < 1f) {
            val t = ripple.value
            drawCircle(
                color = glow.copy(alpha = (1f - t) * 0.7f),
                radius = r * (1.15f + t * 0.95f),
                style = Stroke(width = 4.dp.toPx() * (1f - t) + 0.5f),
            )
        }

        // Orbit track, and the spinning arc while working.
        val orbit = r * 1.38f
        drawCircle(base.copy(alpha = 0.18f), orbit, style = Stroke(1.5.dp.toPx()))
        if (mood == Mood.Working) {
            rotate(if (moving) spin else 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, base, glow), center),
                    startAngle = 0f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = center - Offset(orbit, orbit),
                    size = Size(orbit * 2, orbit * 2),
                    style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        // Soft shadow under the gem.
        drawCircle(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), center + Offset(0f, r * 0.35f), r * 1.15f),
            r * 1.15f,
            center + Offset(0f, r * 0.35f),
        )

        drawGem(r, base, glow, deep)

        if (moving && mood == Mood.Working) {
            clipPath(polygon(octagon(r))) {
                val x = center.x - r + shimmer * 2 * r
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                        start = Offset(x - r * 0.45f, center.y - r),
                        end = Offset(x + r * 0.45f, center.y + r),
                    ),
                )
            }
        }

        drawPower(r * 0.30f, Color.White.copy(alpha = 0.95f), 2.8.dp.toPx())
    }
}

/** Vertices of a flat-topped octagon around the centre. */
private fun DrawScope.octagon(radius: Float): List<Offset> = List(8) { i ->
    val angle = (-112.5 + 45.0 * i) * PI / 180
    center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
}

private fun polygon(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

private fun DrawScope.drawGem(r: Float, base: Color, glow: Color, deep: Color) {
    val outer = octagon(r)
    val inner = octagon(r * 0.56f)
    drawPath(
        polygon(outer),
        Brush.linearGradient(listOf(glow, base, deep), start = center + Offset(-r, -r), end = center + Offset(r, r)),
    )
    // Facets lit from the top left.
    val light = -135.0 * PI / 180
    for (i in 0 until 8) {
        val j = (i + 1) % 8
        val facet = polygon(listOf(outer[i], outer[j], inner[j], inner[i]))
        val facing = (-112.5 + 45.0 * i + 22.5) * PI / 180
        val shade = cos(facing - light).toFloat()
        val tint = if (shade > 0) Color.White.copy(alpha = 0.24f * shade) else Color.Black.copy(alpha = 0.30f * -shade)
        drawPath(facet, tint)
    }
    drawPath(
        polygon(inner),
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.06f)),
            start = inner[7],
            end = inner[3],
        ),
    )
    val edge = Color.White.copy(alpha = 0.22f)
    val width = 1.dp.toPx()
    for (i in 0 until 8) drawLine(edge, outer[i], inner[i], width)
    drawPath(polygon(inner), edge, style = Stroke(width))
    drawPath(polygon(outer), Color.White.copy(alpha = 0.30f), style = Stroke(width))
}

private fun DrawScope.drawPower(radius: Float, color: Color, width: Float) {
    drawArc(
        color = color,
        startAngle = -60f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = center - Offset(radius, radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width, cap = StrokeCap.Round),
    )
    drawLine(color, center + Offset(0f, -radius * 1.2f), center + Offset(0f, -radius * 0.25f), width, StrokeCap.Round)
}
