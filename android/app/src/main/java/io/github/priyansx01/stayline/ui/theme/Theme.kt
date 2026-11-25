package io.github.priyansx01.stayline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import io.github.priyansx01.stayline.data.ThemeMode

@Composable
fun StaylineTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.emerald.base, onPrimary = Color(0xFF04140E),
            secondary = c.ruby.base, onSecondary = Color.White,
            tertiary = c.topaz.base,
            error = c.ruby.base, onError = Color.White,
            background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.muted,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHigh, surfaceContainerLow = c.surface,
            outline = c.outline, outlineVariant = c.outline,
            inverseSurface = c.text, inverseOnSurface = c.background,
        )
    } else {
        lightColorScheme(
            primary = c.emerald.base, onPrimary = Color.White,
            secondary = c.ruby.base, onSecondary = Color.White,
            tertiary = c.topaz.base,
            error = c.ruby.base, onError = Color.White,
            background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.muted,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHigh, surfaceContainerLow = c.surface,
            outline = c.outline, outlineVariant = c.outline,
            inverseSurface = c.text, inverseOnSurface = c.background,
        )
    }
    CompositionLocalProvider(
        LocalStaylineColors provides c,
        LocalReducedMotion provides rememberReducedMotion(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = StaylineTypography, content = content)
    }
}

/** The palette in use: `Stayline.colors.emerald.base`. */
object Stayline {
    val colors: StaylineColors
        @Composable @ReadOnlyComposable get() = LocalStaylineColors.current
}
