package io.github.priyansx01.stayline.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.priyansx01.stayline.data.AppSettings
import io.github.priyansx01.stayline.ui.components.FloatingNav
import io.github.priyansx01.stayline.ui.components.Glyphs
import io.github.priyansx01.stayline.ui.components.NavItem
import io.github.priyansx01.stayline.ui.home.HomeScreen
import io.github.priyansx01.stayline.ui.onboarding.Onboarding
import io.github.priyansx01.stayline.ui.profiles.ProfileEditor
import io.github.priyansx01.stayline.ui.profiles.ProfilesScreen
import io.github.priyansx01.stayline.ui.settings.SettingsScreen
import io.github.priyansx01.stayline.ui.theme.Motion
import io.github.priyansx01.stayline.ui.theme.Stayline
import kotlinx.coroutines.launch

enum class Tab { Home, Profiles, Settings }

@Composable
fun StaylineRoot(vm: AppViewModel) {
    val c = Stayline.colors
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val snackbar = remember { SnackbarHostState() }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onPermissionResult(it.resultCode == Activity.RESULT_OK)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.VpnPermission -> vpnPermission.launch(event.intent)
                is UiEvent.Message -> launch { snackbar.showSnackbar(event.text) }
                is UiEvent.Removed -> launch {
                    val result = snackbar.showSnackbar("Removed ${event.profile.name}", "Undo", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.restore(event.profile, event.index)
                    } else {
                        vm.forgetPasswordIfRemoved(event.profile)
                    }
                }
                UiEvent.NotificationPermission -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        if (settings == null) return@Box
        LaunchedEffect(settings.onboarded) {
            if (settings.onboarded) vm.maybeAutoConnect()
        }
        AnimatedContent(settings.onboarded, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) }, label = "onboarding") { onboarded ->
            if (onboarded) Main(vm, settings) else Onboarding(vm::finishOnboarding)
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
        ) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(18.dp),
                containerColor = c.surfaceHigh,
                contentColor = c.text,
                actionColor = c.emerald.base,
                modifier = Modifier.border(1.dp, c.outline, RoundedCornerShape(18.dp)),
            )
        }
    }
}

@Composable
private fun Main(vm: AppViewModel, settings: AppSettings) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val editor = vm.editor

    BackHandler(enabled = editor == null && tab != Tab.Home) { tab = Tab.Home }
    BackHandler(enabled = editor != null) { vm.closeEditor() }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            tab,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(Motion.gentle()) { it / 5 * direction } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(Motion.gentle()) { -it / 5 * direction } + fadeOut(tween(160)))
            },
            label = "tab",
        ) { current ->
            when (current) {
                Tab.Home -> HomeScreen(vm, settings)
                Tab.Profiles -> ProfilesScreen(vm, settings)
                Tab.Settings -> SettingsScreen(vm, settings)
            }
        }
        FloatingNav(
            items = listOf(
                NavItem(Tab.Home, "Home", Glyphs.Shield),
                NavItem(Tab.Profiles, "Profiles", Glyphs.Layers),
                NavItem(Tab.Settings, "Settings", Glyphs.Sliders),
            ),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
        AnimatedVisibility(
            editor != null,
            enter = slideInVertically(Motion.gentle()) { it / 3 } + fadeIn(tween(200)),
            exit = slideOutVertically(Motion.gentle()) { it / 3 } + fadeOut(tween(160)),
        ) {
            // Keep showing the last editor while it animates out.
            val shown = remember { mutableStateOf(editor) }
            if (editor != null) shown.value = editor
            shown.value?.let { ProfileEditor(it, vm) }
        }
        Sheets(vm, settings)
    }
}
