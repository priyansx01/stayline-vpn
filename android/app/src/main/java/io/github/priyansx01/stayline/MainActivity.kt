package io.github.priyansx01.stayline

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.priyansx01.stayline.data.ThemeMode
import io.github.priyansx01.stayline.ui.AppViewModel
import io.github.priyansx01.stayline.ui.StaylineRoot
import io.github.priyansx01.stayline.ui.theme.Stayline
import io.github.priyansx01.stayline.ui.theme.StaylineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            StaylineTheme(settings?.theme ?: ThemeMode.System) {
                // Status and navigation bar icons follow the app's theme, not the system's.
                val dark = Stayline.colors.isDark
                DisposableEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(style, style)
                    onDispose {}
                }
                StaylineRoot(vm)
            }
        }
    }
}
