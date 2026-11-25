package io.github.priyansx01.stayline.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The few line icons the app needs, drawn here instead of pulling in an
 * icon library. 24×24, rounded 1.8 px strokes; tinted by `Icon`.
 */
object Glyphs {
    val Shield = icon("shield") {
        moveTo(12f, 3f); lineTo(19f, 6f); verticalLineTo(11.5f)
        curveTo(19f, 16f, 15.9f, 19.6f, 12f, 21f)
        curveTo(8.1f, 19.6f, 5f, 16f, 5f, 11.5f); verticalLineTo(6f); close()
    }
    val Layers = icon("layers") {
        moveTo(12f, 4f); lineTo(20f, 8f); lineTo(12f, 12f); lineTo(4f, 8f); close()
        moveTo(4f, 12f); lineTo(12f, 16f); lineTo(20f, 12f)
        moveTo(4f, 16f); lineTo(12f, 20f); lineTo(20f, 16f)
    }
    val Sliders = icon("sliders") {
        moveTo(4f, 7f); horizontalLineTo(13f); moveTo(17.5f, 7f); horizontalLineTo(20f); circle(15.5f, 7f, 2f)
        moveTo(4f, 12f); horizontalLineTo(6.5f); moveTo(10.5f, 12f); horizontalLineTo(20f); circle(8.5f, 12f, 2f)
        moveTo(4f, 17f); horizontalLineTo(11f); moveTo(15f, 17f); horizontalLineTo(20f); circle(13f, 17f, 2f)
    }
    val Plus = icon("plus") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) }
    val Close = icon("close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val Check = icon("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
    val ChevronDown = icon("chevron_down") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
    val ChevronRight = icon("chevron_right") { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
    val ArrowUp = icon("arrow_up") { moveTo(12f, 19f); verticalLineTo(5f); moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f) }
    val ArrowDown = icon("arrow_down") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f) }
    val Lock = icon("lock") {
        moveTo(6f, 11f); horizontalLineTo(18f); verticalLineTo(20f); horizontalLineTo(6f); close()
        moveTo(8.5f, 11f); verticalLineTo(8f); arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 8f); verticalLineTo(11f)
    }
    val Globe = icon("globe") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 3.5f); arcTo(4f, 8.5f, 0f, false, false, 12f, 20.5f); arcTo(4f, 8.5f, 0f, false, false, 12f, 3.5f)
        moveTo(3.5f, 12f); horizontalLineTo(20.5f)
    }
    val Warning = icon("warning") {
        moveTo(12f, 4f); lineTo(21f, 19.5f); horizontalLineTo(3f); close()
        moveTo(12f, 10f); verticalLineTo(14f); moveTo(12f, 17f); verticalLineTo(17.01f)
    }
    val Trash = icon("trash") {
        moveTo(5f, 7f); horizontalLineTo(19f); moveTo(9f, 7f); verticalLineTo(5f); horizontalLineTo(15f); verticalLineTo(7f)
        moveTo(7f, 7f); lineTo(8f, 20f); horizontalLineTo(16f); lineTo(17f, 7f)
    }
    val Pencil = icon("pencil") {
        moveTo(4f, 20f); lineTo(5f, 15.5f); lineTo(15.5f, 5f); lineTo(19f, 8.5f); lineTo(8.5f, 19f); close()
        moveTo(13f, 7.5f); lineTo(16.5f, 11f)
    }
    val Eye = icon("eye") {
        moveTo(2.5f, 12f); curveTo(5f, 7f, 8.5f, 5f, 12f, 5f); curveTo(15.5f, 5f, 19f, 7f, 21.5f, 12f)
        curveTo(19f, 17f, 15.5f, 19f, 12f, 19f); curveTo(8.5f, 19f, 5f, 17f, 2.5f, 12f); close()
        circle(12f, 12f, 3f)
    }
    val EyeOff = icon("eye_off") {
        moveTo(2.5f, 12f); curveTo(5f, 7f, 8.5f, 5f, 12f, 5f); curveTo(15.5f, 5f, 19f, 7f, 21.5f, 12f)
        curveTo(19f, 17f, 15.5f, 19f, 12f, 19f); curveTo(8.5f, 19f, 5f, 17f, 2.5f, 12f); close()
        moveTo(4f, 4f); lineTo(20f, 20f)
    }
    val Badge = icon("badge") {
        circle(12f, 9f, 5f)
        moveTo(9f, 13f); lineTo(8f, 21f); lineTo(12f, 19f); lineTo(16f, 21f); lineTo(15f, 13f)
    }
    val External = icon("external") {
        moveTo(14f, 4f); horizontalLineTo(20f); verticalLineTo(10f); moveTo(20f, 4f); lineTo(11f, 13f)
        moveTo(18f, 14f); verticalLineTo(19f); horizontalLineTo(5f); verticalLineTo(6f); horizontalLineTo(10f)
    }
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
}

private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
        .build()
