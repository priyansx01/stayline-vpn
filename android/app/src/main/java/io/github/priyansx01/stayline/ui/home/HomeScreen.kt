package io.github.priyansx01.stayline.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.priyansx01.stayline.data.AppSettings
import io.github.priyansx01.stayline.data.VpnProfile
import io.github.priyansx01.stayline.ui.AppViewModel
import io.github.priyansx01.stayline.ui.components.Banner
import io.github.priyansx01.stayline.ui.components.Card
import io.github.priyansx01.stayline.ui.components.GemOrb
import io.github.priyansx01.stayline.ui.components.Glyphs
import io.github.priyansx01.stayline.ui.components.Mood
import io.github.priyansx01.stayline.ui.components.NavClearance
import io.github.priyansx01.stayline.ui.components.RollingText
import io.github.priyansx01.stayline.ui.components.ScreenHeader
import io.github.priyansx01.stayline.ui.components.Sparkline
import io.github.priyansx01.stayline.ui.components.formatBytes
import io.github.priyansx01.stayline.ui.components.formatDuration
import io.github.priyansx01.stayline.ui.components.formatRate
import io.github.priyansx01.stayline.ui.components.moodJewel
import io.github.priyansx01.stayline.ui.components.pressable
import io.github.priyansx01.stayline.ui.theme.Motion
import io.github.priyansx01.stayline.ui.theme.Stayline
import io.github.priyansx01.stayline.vpn.Attention
import io.github.priyansx01.stayline.vpn.Traffic
import io.github.priyansx01.stayline.vpn.TunnelState
import kotlinx.coroutines.delay

fun TunnelState.mood(): Mood = when (this) {
    TunnelState.Disconnected -> Mood.Off
    is TunnelState.Connected -> Mood.On
    is TunnelState.NeedsUser -> Mood.Alert
    else -> Mood.Working
}

@Composable
fun HomeScreen(vm: AppViewModel, settings: AppSettings) {
    val state by vm.tunnel.collectAsStateWithLifecycle()
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val profile = settings.active
    val mood = state.mood()
    val c = Stayline.colors
    val ambient by animateColorAsState(moodJewel(mood).base, Motion.mood(), label = "ambient")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A wash of the current jewel tone behind the gem.
        // Drawn over the whole screen so it fades out fully instead of ending in an edge.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(ambient.copy(alpha = if (c.isDark) 0.20f else 0.12f), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.38f),
                    radius = size.width * 0.95f,
                ),
            )
        }
        val orbSize = (maxHeight * 0.28f).coerceIn(150.dp, 228.dp)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = NavClearance),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(profile, onProfile = { if (profile == null) vm.openEditor(null) else vm.showSwitcher() })
            Spacer(Modifier.weight(1f))
            GemOrb(
                mood = mood,
                onClick = vm::toggle,
                label = if (state.isActive) "Disconnect" else "Connect",
                size = orbSize,
            )
            Spacer(Modifier.height(8.dp))
            StatusText(state, profile)
            Spacer(Modifier.weight(1f))
            val needsUser = state as? TunnelState.NeedsUser
            AnimatedVisibility(
                needsUser != null && needsUser.attention == Attention.Other,
                enter = expandVertically(Motion.gentle()) + fadeIn(),
                exit = shrinkVertically(Motion.gentle()) + fadeOut(),
            ) {
                Banner(
                    icon = Glyphs.Warning,
                    title = "Stopped trying",
                    message = needsUser?.reason.orEmpty(),
                    jewel = c.ruby,
                    action = "Retry",
                    onAction = { vm.connect(profile) },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            StatsCard(state, traffic)
        }
    }
}

@Composable
private fun TopBar(profile: VpnProfile?, onProfile: () -> Unit) {
    val c = Stayline.colors
    ScreenHeader("STAYLINE", "Your tunnel") {
        Row(
            Modifier
                .widthIn(max = 200.dp)
                .clip(CircleShape)
                .background(c.surface)
                .border(1.dp, c.outline, CircleShape)
                .pressable(onProfile)
                .padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(c.ruby.base, c.emerald.base))),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                profile?.name ?: "Add profile",
                style = MaterialTheme.typography.titleSmall,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Icon(if (profile == null) Glyphs.Plus else Glyphs.ChevronDown, null, Modifier.size(16.dp), tint = c.muted)
        }
    }
}

@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun StatusText(state: TunnelState, profile: VpnProfile?) {
    val c = Stayline.colors
    val now = rememberNow()
    // When the current state arrived, for the reconnect countdown.
    val since = remember(state) { System.currentTimeMillis() }
    val title = when (state) {
        TunnelState.Disconnected -> "Not connected"
        is TunnelState.Connecting -> "Connecting"
        is TunnelState.Connected -> "Protected"
        is TunnelState.Reconnecting -> "Reconnecting"
        TunnelState.WaitingForNetwork -> "Waiting for network"
        is TunnelState.NeedsUser -> "Needs you"
    }
    val subtitle = when (state) {
        TunnelState.Disconnected -> if (profile == null) "Add your company gateway to begin" else "Tap the gem to connect to ${profile.name}"
        is TunnelState.Connecting -> if (state.attempt > 1) "Attempt ${state.attempt} · ${profile?.gateway.orEmpty()}" else profile?.gateway.orEmpty()
        is TunnelState.Connected -> "Tunnel address ${state.localIp}"
        is TunnelState.Reconnecting -> {
            val left = (state.retryInSecs - (now - since) / 1000).coerceAtLeast(0)
            if (left > 0) "Trying again in ${left}s · ${state.lastError}" else state.lastError
        }
        TunnelState.WaitingForNetwork -> "Reconnects the moment you are back online"
        is TunnelState.NeedsUser -> state.reason
    }
    AnimatedContent(
        title,
        transitionSpec = {
            (slideInVertically(Motion.gentle()) { it / 2 } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(Motion.gentle()) { -it / 2 } + fadeOut(tween(150)))
        },
        label = "title",
    ) {
        Text(it, style = MaterialTheme.typography.headlineMedium, color = c.text)
    }
    Spacer(Modifier.height(4.dp))
    AnimatedContent(subtitle, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "subtitle") {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
    AnimatedVisibility(
        state is TunnelState.Connected,
        enter = expandVertically(Motion.gentle()) + fadeIn(),
        exit = shrinkVertically(Motion.gentle()) + fadeOut(),
    ) {
        val sinceUnix = (state as? TunnelState.Connected)?.sinceUnix ?: 0
        val seconds = if (sinceUnix > 0) now / 1000 - sinceUnix else 0
        RollingText(
            formatDuration(seconds),
            style = MaterialTheme.typography.displaySmall,
            color = c.emerald.base,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun StatsCard(state: TunnelState, traffic: Traffic) {
    val c = Stayline.colors
    val live = state is TunnelState.Connected
    val dim by animateFloatAsState(if (live) 1f else 0.45f, label = "dim")
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(
                Glyphs.ArrowDown, "Down", animatedBytes(traffic.received),
                if (live) formatRate(traffic.downRate) else "—", c.emerald.base, dim, Modifier.weight(1f),
            )
            Stat(
                Glyphs.ArrowUp, "Up", animatedBytes(traffic.sent),
                if (live) formatRate(traffic.upRate) else "—", c.ruby.base, dim, Modifier.weight(1f),
            )
        }
        // Only takes room once there is traffic to draw, so the idle card stays balanced.
        AnimatedVisibility(
            live && traffic.samples.size >= 2,
            enter = expandVertically(Motion.gentle()) + fadeIn(),
            exit = shrinkVertically(Motion.gentle()) + fadeOut(),
        ) {
            Sparkline(
                traffic.samples,
                c.emerald.base,
                Modifier.padding(top = 14.dp).fillMaxWidth().height(40.dp),
            )
        }
    }
}

@Composable
private fun animatedBytes(bytes: Long): String {
    val shown by animateFloatAsState(bytes.toFloat(), tween(900), label = "bytes")
    return formatBytes(shown.toLong())
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String, detail: String, tint: Color, alpha: Float, modifier: Modifier) {
    val c = Stayline.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f * alpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = tint.copy(alpha = alpha))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.muted)
            Text(value, style = MaterialTheme.typography.titleMedium, color = c.text.copy(alpha = alpha), maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
        }
    }
}
