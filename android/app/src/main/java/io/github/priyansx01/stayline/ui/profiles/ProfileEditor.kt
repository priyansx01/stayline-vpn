package io.github.priyansx01.stayline.ui.profiles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.priyansx01.stayline.data.VpnProfile
import io.github.priyansx01.stayline.ui.AppViewModel
import io.github.priyansx01.stayline.ui.Editor
import io.github.priyansx01.stayline.ui.components.Card
import io.github.priyansx01.stayline.ui.components.Field
import io.github.priyansx01.stayline.ui.components.FingerprintBlocks
import io.github.priyansx01.stayline.ui.components.GhostButton
import io.github.priyansx01.stayline.ui.components.Glyphs
import io.github.priyansx01.stayline.ui.components.IconBubble
import io.github.priyansx01.stayline.ui.components.JewelButton
import io.github.priyansx01.stayline.ui.components.SettingRow
import io.github.priyansx01.stayline.ui.components.Toggle
import io.github.priyansx01.stayline.ui.theme.Motion
import io.github.priyansx01.stayline.ui.theme.Stayline
import io.github.priyansx01.stayline.vpn.CertificateReport

@Composable
fun ProfileEditor(editor: Editor, vm: AppViewModel) {
    val c = Stayline.colors
    val original = editor.original
    val key = original?.id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(original?.name.orEmpty()) }
    var gateway by rememberSaveable(key) { mutableStateOf(original?.gateway.orEmpty()) }
    var username by rememberSaveable(key) { mutableStateOf(original?.username.orEmpty()) }
    var realm by rememberSaveable(key) { mutableStateOf(original?.realm.orEmpty()) }
    var pin by rememberSaveable(key) { mutableStateOf(original?.pin.orEmpty()) }
    var password by rememberSaveable(key) { mutableStateOf("") }
    var remember by rememberSaveable(key) { mutableStateOf(original == null || vm.hasPassword(original)) }
    var advanced by rememberSaveable(key) { mutableStateOf(!original?.realm.isNullOrBlank() || !original?.pin.isNullOrBlank()) }
    val valid = gateway.isNotBlank() && username.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Glyphs.Close, "Close", vm::closeEditor)
            Spacer(Modifier.width(14.dp))
            Text(if (original == null) "New profile" else "Edit profile", style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Field(gateway, { gateway = it }, "Gateway", placeholder = "vpn.example.com:10443", keyboardType = KeyboardType.Uri)
            Field(username, { username = it }, "Username", placeholder = "alice")
            Field(name, { name = it }, "Name (optional)", placeholder = "Office")
            Card(Modifier.fillMaxWidth(), padding = 14.dp) {
                SettingRow("Remember password", "Encrypted with a key kept in the Android Keystore") {
                    Toggle(remember) { remember = it }
                }
                AnimatedVisibility(remember, enter = expandVertically(Motion.gentle()) + fadeIn(), exit = shrinkVertically(Motion.gentle()) + fadeOut()) {
                    Field(
                        password,
                        { password = it },
                        if (original != null && vm.hasPassword(original)) "New password (leave empty to keep)" else "Password",
                        password = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            SettingRow(
                "Advanced",
                "Realm and certificate pin",
                onClick = { advanced = !advanced },
            ) {
                Icon(if (advanced) Glyphs.ChevronDown else Glyphs.ChevronRight, null, Modifier.size(20.dp), tint = c.muted)
            }
            AnimatedVisibility(advanced, enter = expandVertically(Motion.gentle()) + fadeIn(), exit = shrinkVertically(Motion.gentle()) + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Field(realm, { realm = it }, "Realm", placeholder = "Only if your IT team uses one")
                    Field(pin, { pin = it.trim() }, "Certificate pin (SHA-256)", placeholder = "Only for self-signed gateways", monospace = true)
                    CertificateCheck(
                        inspecting = vm.inspecting,
                        report = vm.inspected,
                        canCheck = gateway.isNotBlank(),
                        onCheck = { vm.inspect(gateway) },
                        onTrust = { pin = it },
                    )
                }
            }
            if (original != null) {
                GhostButton("Remove profile", { vm.delete(original) }, Modifier.fillMaxWidth(), color = c.ruby.base)
            }
        }
        JewelButton(
            "Save",
            onClick = {
                val profile = VpnProfile(
                    id = original?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim().ifEmpty { gateway.trim().substringBefore(':').removePrefix("https://") },
                    gateway = gateway.trim(),
                    username = username.trim(),
                    realm = realm.trim().ifEmpty { null },
                    pin = pin.trim().ifEmpty { null },
                )
                vm.save(profile, password, remember)
            },
            enabled = valid,
            icon = Glyphs.Check,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
    }
}

@Composable
private fun CertificateCheck(
    inspecting: Boolean,
    report: CertificateReport?,
    canCheck: Boolean,
    onCheck: () -> Unit,
    onTrust: (String) -> Unit,
) {
    val c = Stayline.colors
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Glyphs.Badge, null, Modifier.size(20.dp), tint = c.muted)
            Spacer(Modifier.width(10.dp))
            Text("Gateway certificate", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            if (inspecting) CircularProgressIndicator(Modifier.size(18.dp), color = c.topaz.base, strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(10.dp))
        AnimatedContent(report, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "report") { r ->
            Column {
                when {
                    r == null -> Text(
                        "Check the certificate before pinning it. Compare the fingerprint with the one your IT team gave you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                    r.publiclyTrusted -> Text(
                        "Publicly trusted. No pin is needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.emerald.base,
                    )
                    r.fingerprint == null -> Text(
                        r.problem ?: "Could not reach the gateway.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ruby.base,
                    )
                    else -> {
                        Text(
                            r.problem ?: "Not publicly trusted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.topaz.base,
                        )
                        Spacer(Modifier.height(10.dp))
                        FingerprintBlocks(r.fingerprint, c.text)
                        Spacer(Modifier.height(12.dp))
                        JewelButton("Pin this certificate", { onTrust(r.fingerprint) }, jewel = c.topaz, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GhostButton(if (report == null) "Check certificate" else "Check again", { if (canCheck && !inspecting) onCheck() }, Modifier.fillMaxWidth())
    }
}
