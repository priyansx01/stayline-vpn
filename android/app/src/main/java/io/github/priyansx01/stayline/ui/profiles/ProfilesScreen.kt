package io.github.priyansx01.stayline.ui.profiles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.priyansx01.stayline.data.AppSettings
import io.github.priyansx01.stayline.data.VpnProfile
import io.github.priyansx01.stayline.ui.AppViewModel
import io.github.priyansx01.stayline.ui.components.Card
import io.github.priyansx01.stayline.ui.components.Chip
import io.github.priyansx01.stayline.ui.components.Glyphs
import io.github.priyansx01.stayline.ui.components.IconBubble
import io.github.priyansx01.stayline.ui.components.JewelButton
import io.github.priyansx01.stayline.ui.components.NavClearance
import io.github.priyansx01.stayline.ui.components.ScreenHeader
import io.github.priyansx01.stayline.ui.components.pressable
import io.github.priyansx01.stayline.ui.theme.LocalReducedMotion
import io.github.priyansx01.stayline.ui.theme.Motion
import io.github.priyansx01.stayline.ui.theme.Stayline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ProfilesScreen(vm: AppViewModel, settings: AppSettings) {
    val c = Stayline.colors
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("CONNECTIONS", "Profiles", Modifier.padding(top = 8.dp, bottom = 16.dp)) {
            IconBubble(Glyphs.Plus, "Add profile", { vm.openEditor(null) }, tint = c.emerald.base)
        }
        val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        if (settings.profiles.isEmpty()) {
            EmptyProfiles { vm.openEditor(null) }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = NavClearance + navBar),
            ) {
                itemsIndexed(settings.profiles, key = { _, p -> p.id }) { index, profile ->
                    Entrance(index, Modifier.animateItem()) {
                        SwipeToDelete(onDelete = { vm.delete(profile) }) {
                            ProfileCard(
                                profile = profile,
                                active = profile.id == settings.active?.id,
                                savedPassword = vm.hasPassword(profile),
                                onSelect = { vm.select(profile) },
                                onEdit = { vm.openEditor(profile) },
                            )
                        }
                    }
                }
                item {
                    Text(
                        "Swipe a profile left to remove it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** Fades and lifts each card in, one after another. */
@Composable
private fun Entrance(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    var shown by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(8) * Motion.STAGGER_MS * 2L)
        shown = true
    }
    val progress by animateFloatAsState(if (shown) 1f else 0f, Motion.gentle(), label = "entrance")
    Box(modifier.graphicsLayer { alpha = progress; translationY = (1f - progress) * 24.dp.toPx() }) { content() }
}

@Composable
private fun EmptyProfiles(onAdd: () -> Unit) {
    val c = Stayline.colors
    Card(Modifier.fillMaxWidth(), padding = 24.dp) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(c.ruby.base, c.emerald.base))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Glyphs.Shield, null, Modifier.size(26.dp), tint = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Text("Add your gateway", style = MaterialTheme.typography.titleLarge, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(
            "Enter the FortiGate address your IT team gave you, like vpn.example.com:10443, and your username.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
        )
        Spacer(Modifier.height(20.dp))
        JewelButton("Add profile", onAdd, icon = Glyphs.Plus, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProfileCard(
    profile: VpnProfile,
    active: Boolean,
    savedPassword: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    val c = Stayline.colors
    Card(Modifier.fillMaxWidth().pressable(onSelect), accent = if (active) c.emerald.base else null, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (active) c.emerald.base.copy(alpha = 0.16f) else c.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Glyphs.Globe, null, Modifier.size(22.dp), tint = if (active) c.emerald.base else c.muted)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (active) {
                        Spacer(Modifier.width(8.dp))
                        Chip("Active", c.emerald.base)
                    }
                }
                Text(
                    "${profile.username} · ${profile.gateway}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (savedPassword || !profile.pin.isNullOrBlank()) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (savedPassword) Chip("Password saved", c.muted)
                        if (!profile.pin.isNullOrBlank()) Chip("Pinned", c.topaz.base)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconBubble(Glyphs.Pencil, "Edit ${profile.name}", onEdit, tint = c.muted)
        }
    }
}

/** Drag left past a third of the width to remove; the trash grows as you go. */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val c = Stayline.colors
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val limit = constraints.maxWidth.toFloat()
        val threshold = limit * 0.35f
        val progress = (-offset.value / threshold).coerceIn(0f, 1f)
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .background(c.ruby.base.copy(alpha = 0.10f + 0.75f * progress)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                Glyphs.Trash,
                null,
                Modifier.padding(end = 26.dp).size(22.dp).graphicsLayer {
                    val s = 0.6f + 0.5f * progress
                    scaleX = s
                    scaleY = s
                },
                tint = Color.White.copy(alpha = 0.4f + 0.6f * progress),
            )
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-limit, 0f)) }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        if (offset.value < -threshold) {
                            offset.animateTo(-limit, tween(180))
                            onDelete()
                        } else {
                            offset.animateTo(0f, Motion.snappy())
                        }
                    },
                ),
        ) { content() }
    }
}
