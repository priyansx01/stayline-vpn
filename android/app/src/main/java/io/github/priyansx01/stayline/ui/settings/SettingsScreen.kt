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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import io.github.priyansx01.stayline.ui.components.SheetHeader
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
    var showLicences by rememberSaveable { mutableStateOf(false) }
    if (showLicences) LicencesSheet { showLicences = false }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("PREFERENCES", "Settings", Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = NavClearance),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("Appearance") {
                Segmented(
                    options = listOf(ThemeMode.System to "System", ThemeMode.Light to "Light", ThemeMode.Dark to "Dark"),
                    selected = settings.theme,
                    onSelect = vm::setTheme,
                )
            }

            Section("Connection") {
                SettingRow("Auto-connect", "Connects when you open the app, using the saved password") {
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
                SettingRow("Open-source licences", "Components Stayline is built on", onClick = { showLicences = true }) {
                    Icon(Glyphs.ChevronRight, null, Modifier.size(20.dp), tint = c.muted)
                }
            }

            Text(
                "Stayline works with FortiGate SSL-VPN gateways. SAML sign-in and token codes are not supported yet.",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
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

/** What Stayline is built on, with the font licence the OFL asks to ship. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicencesSheet(onClose: () -> Unit) {
    val c = Stayline.colors
    val context = LocalContext.current
    val ofl = remember {
        runCatching { context.assets.open("licenses/manrope-ofl.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader("Open-source licences", onClose)
            LICENCES.forEach { (name, licence) ->
                Column {
                    Text(name, style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text(licence, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            Text(
                "The full list of Rust components and their licences is in THIRD-PARTY-NOTICES.html in the source code.",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
            Text("MANROPE LICENCE", style = MaterialTheme.typography.labelSmall, color = c.muted)
            Text(
                ofl,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = c.text,
            )
        }
    }
}

private val LICENCES = listOf(
    "Stayline and its Rust tunnel engine" to "MIT or Apache-2.0",
    "Rust crates (tokio, rustls, ring, reqwest and others)" to "MIT, Apache-2.0, ISC and similar permissive licences",
    "AndroidX, Jetpack Compose and Kotlin libraries" to "Apache-2.0",
    "Manrope typeface, The Manrope Project Authors" to "SIL Open Font License 1.1, reproduced below",
)

