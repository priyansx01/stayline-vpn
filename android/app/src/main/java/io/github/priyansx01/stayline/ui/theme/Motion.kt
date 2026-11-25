package io.github.priyansx01.stayline.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Shared motion, so every surface moves with the same character. */
object Motion {
    /** Presses and toggles: quick, with a little bounce. */
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = 700f)

    /** Things settling into place. */
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Colour changes of the gem: slow enough to feel like a transition. */
    fun <T> mood(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 60f)

    const val STAGGER_MS = 35
}

/** True when the user turned animations off in developer or accessibility settings. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
