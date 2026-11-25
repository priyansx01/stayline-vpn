package io.github.priyansx01.stayline.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** One jewel tone: the colour itself, its glow and its shadow. */
@Immutable
data class Jewel(val base: Color, val glow: Color, val deep: Color)

/**
 * Ruby means off or needs you, topaz means working, emerald means
 * protected. Everything else stays quiet so those three carry the meaning.
 */
@Immutable
data class StaylineColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val text: Color,
    val muted: Color,
    val emerald: Jewel,
    val ruby: Jewel,
    val topaz: Jewel,
)

val DarkColors = StaylineColors(
    isDark = true,
    background = Color(0xFF0B0A0F),
    surface = Color(0xFF15131B),
    surfaceHigh = Color(0xFF1E1B26),
    outline = Color(0xFF2A2633),
    text = Color(0xFFF4F1F6),
    muted = Color(0xFF9C95A8),
    emerald = Jewel(Color(0xFF19C58A), Color(0xFF3DF5B0), Color(0xFF0B6E4F)),
    ruby = Jewel(Color(0xFFFF3B6B), Color(0xFFFF7A9A), Color(0xFF8C0F2E)),
    topaz = Jewel(Color(0xFFF5B544), Color(0xFFFFD58A), Color(0xFF8A5A0B)),
)

val LightColors = StaylineColors(
    isDark = false,
    background = Color(0xFFFBF7F8),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF3EEF0),
    outline = Color(0xFFE6DEE2),
    text = Color(0xFF1A1620),
    muted = Color(0xFF6E6678),
    emerald = Jewel(Color(0xFF0E9F6E), Color(0xFF34D399), Color(0xFF065F46)),
    ruby = Jewel(Color(0xFFC8103E), Color(0xFFFF5C85), Color(0xFF7A0A26)),
    topaz = Jewel(Color(0xFFC98A12), Color(0xFFF5B544), Color(0xFF7A520A)),
)

val LocalStaylineColors = staticCompositionLocalOf { DarkColors }
