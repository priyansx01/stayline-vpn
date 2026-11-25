package io.github.priyansx01.stayline.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.priyansx01.stayline.BuildConfig
import io.github.priyansx01.stayline.data.AppSettings
import io.github.priyansx01.stayline.data.ThemeMode
import io.github.priyansx01.stayline.ui.AppViewModel
import io.github.priyansx01.stayline.ui.components.Card
import io.github.priyansx01.stayline.ui.components.Glyphs
import io.github.priyansx01.stayline.ui.components.NavClearance
import io.github.priyansx01.stayline.ui.components.ScreenHeader
import io.github.priyansx01.stayline.ui.components.Segmented
import io.github.priyansx01.stayline.ui.components.SettingRow
import io.github.priyansx01.stayline.ui.components.Toggle
import io.github.priyansx01.stayline.ui.theme.Stayline

private const val SOURCE = "https://github.com/priyansx01/stayline-vpn"

@Composable
fun SettingsScreen(vm: AppViewModel, settings: AppSettings) {
    val c = Stayline.colors
    val context = LocalContext.current
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = NavClearance),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("PREFERENCES", "Settings", Modifier.padding(bottom = 2.dp))

        Section("Appearance") {
            Segmented(
                options = listOf(ThemeMode.System to "System", ThemeMode.Light to "Light", ThemeMode.Dark to "Dark"),
                selected = settings.theme,
                onSelect = vm::setTheme,
            )
        }

        Section("Connection") {
            SettingRow("Connect when the app opens", "Uses the active profile and its saved password") {
                Toggle(settings.autoConnect, vm::setAutoConnect)
            }
            SettingRow(
                "Always-on VPN",
                "Reconnect after a restart and block traffic while the VPN is down. Turn it on for Stayline in Android's VPN settings.",
                onClick = { open(Intent(Settings.ACTION_VPN_SETTINGS)) },
            ) {
                Icon(Glyphs.External, null, Modifier.size(20.dp), tint = c.muted)
            }
            SettingRow(
                "Quick Settings tile",
                "Add the Stayline tile to toggle the VPN from the notification shade",
            )
        }

        Section("About") {
            SettingRow("Version", BuildConfig.VERSION_NAME)
            SettingRow("Source code", SOURCE.removePrefix("https://"), onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE))) }) {
                Icon(Glyphs.External, null, Modifier.size(20.dp), tint = c.muted)
            }
            SettingRow("Licence", "MIT or Apache-2.0")
        }

        Text(
            "Stayline works with FortiGate SSL-VPN gateways. SAML sign-in and token codes are not supported yet.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = Stayline.colors
    Card(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(8.dp))
        content()
    }
}
