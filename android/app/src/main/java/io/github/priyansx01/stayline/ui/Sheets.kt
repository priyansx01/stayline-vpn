package io.github.priyansx01.stayline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.priyansx01.stayline.data.AppSettings
import io.github.priyansx01.stayline.ui.components.Banner
import io.github.priyansx01.stayline.ui.components.Chip
import io.github.priyansx01.stayline.ui.components.Field
import io.github.priyansx01.stayline.ui.components.FingerprintBlocks
import io.github.priyansx01.stayline.ui.components.GhostButton
import io.github.priyansx01.stayline.ui.components.Glyphs
import io.github.priyansx01.stayline.ui.components.JewelButton
import io.github.priyansx01.stayline.ui.components.SettingRow
import io.github.priyansx01.stayline.ui.components.SheetHeader
import io.github.priyansx01.stayline.ui.components.Toggle
import io.github.priyansx01.stayline.ui.components.pressable
import io.github.priyansx01.stayline.ui.theme.Stayline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheets(vm: AppViewModel, settings: AppSettings) {
    val sheet = vm.sheet ?: return
    val c = Stayline.colors
    ModalBottomSheet(
        onDismissRequest = vm::dismissSheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(c.outline))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (sheet) {
                is Sheet.Password -> PasswordSheet(sheet, vm)
                is Sheet.Trust -> TrustSheet(sheet, vm)
                Sheet.Switcher -> SwitcherSheet(settings, vm)
            }
        }
    }
}

@Composable
private fun PasswordSheet(sheet: Sheet.Password, vm: AppViewModel) {
    val c = Stayline.colors
    var password by rememberSaveable(sheet) { mutableStateOf("") }
    var remember by rememberSaveable(sheet) { mutableStateOf(true) }
    SheetHeader("Sign in", vm::dismissSheet)
    Text(
        "${sheet.profile.username} at ${sheet.profile.gateway}",
        style = MaterialTheme.typography.bodyMedium,
        color = c.muted,
    )
    Field(
        password,
        { password = it },
        "Password",
        password = true,
        error = if (sheet.rejected) "That password was not accepted" else null,
        shake = vm.passwordShake,
    )
    SettingRow("Remember password", "Kept encrypted on this phone") {
        Toggle(remember) { remember = it }
    }
    JewelButton(
        "Connect",
        { vm.submitPassword(sheet.profile, password, remember) },
        enabled = password.isNotEmpty(),
        icon = Glyphs.Lock,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TrustSheet(sheet: Sheet.Trust, vm: AppViewModel) {
    val c = Stayline.colors
    val changed = sheet.previous != null
    SheetHeader(if (changed) "Certificate changed" else "Trust this gateway?", vm::dismissSheet)
    if (changed) {
        Banner(
            icon = Glyphs.Warning,
            title = "This can mean someone is intercepting the connection",
            message = "Check the new fingerprint with your IT team before trusting it.",
            jewel = c.ruby,
        )
    } else {
        Text(
            "${sheet.profile.gateway} uses a certificate that is not publicly trusted. Trust it only if this fingerprint matches the one your IT team gave you.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
        )
    }
    Text(if (changed) "NEW FINGERPRINT" else "FINGERPRINT", style = MaterialTheme.typography.labelSmall, color = c.muted)
    FingerprintBlocks(sheet.fingerprint, c.text)
    sheet.previous?.let {
        Text("PINNED BEFORE", style = MaterialTheme.typography.labelSmall, color = c.muted)
        FingerprintBlocks(it, c.muted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton("Cancel", vm::dismissSheet, Modifier.weight(1f))
        JewelButton(
            if (changed) "Trust new" else "Trust",
            { vm.trust(sheet) },
            jewel = if (changed) c.ruby else c.emerald,
            icon = Glyphs.Shield,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SwitcherSheet(settings: AppSettings, vm: AppViewModel) {
    val c = Stayline.colors
    SheetHeader("Profiles", vm::dismissSheet)
    settings.profiles.forEach { profile ->
        val active = profile.id == settings.active?.id
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (active) c.emerald.base.copy(alpha = 0.12f) else c.surfaceHigh)
                .pressable({ vm.select(profile) })
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyphs.Globe, null, Modifier.size(20.dp), tint = if (active) c.emerald.base else c.muted)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(profile.gateway, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            if (active) Chip("Active", c.emerald.base)
        }
    }
    Spacer(Modifier.height(2.dp))
    GhostButton("Add profile", { vm.openEditor(null) }, Modifier.fillMaxWidth())
}
