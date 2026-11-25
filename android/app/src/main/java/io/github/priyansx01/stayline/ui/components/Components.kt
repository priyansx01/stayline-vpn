package io.github.priyansx01.stayline.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.priyansx01.stayline.ui.theme.Jewel
import io.github.priyansx01.stayline.ui.theme.LocalReducedMotion
import io.github.priyansx01.stayline.ui.theme.Motion
import io.github.priyansx01.stayline.ui.theme.Stayline
import io.github.priyansx01.stayline.ui.theme.TABULAR
import kotlinx.coroutines.delay
import java.util.Locale

/** A quiet surface with a hairline border. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Stayline.colors
    val border by animateColorAsState(accent?.copy(alpha = 0.55f) ?: c.outline, Motion.gentle(), label = "border")
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(c.surface)
            .border(1.dp, border, RoundedCornerShape(24.dp))
            .padding(padding),
        content = content,
    )
}

/** Shrinks a little under the finger. */
@Composable
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true, role: Role = Role.Button): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.snappy(), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun JewelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    jewel: Jewel = Stayline.colors.emerald,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "enabled")
    Row(
        modifier
            .height(54.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(jewel.glow, jewel.base)))
            .pressable(onClick, enabled = enabled && !busy)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(busy, label = "busy") { isBusy ->
            if (isBusy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        Icon(it, null, Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Stayline.colors.text) {
    Box(
        modifier
            .height(54.dp)
            .clip(CircleShape)
            .border(1.dp, Stayline.colors.outline, CircleShape)
            .pressable(onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun IconBubble(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Stayline.colors.text) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Stayline.colors.surfaceHigh)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = tint)
    }
}

/**
 * A text field that shakes when [shake] changes to a new non-zero value,
 * e.g. after a rejected password.
 */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    monospace: Boolean = false,
    error: String? = null,
    shake: Int = 0,
) {
    val c = Stayline.colors
    var visible by rememberSaveable { mutableStateOf(false) }
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake > 0) {
            for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) offset.animateTo(x, tween(45))
        }
    }
    Column(modifier.graphicsLayer { translationX = offset.value * density }) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = c.muted.copy(alpha = 0.6f)) } },
            singleLine = true,
            isError = error != null,
            textStyle = if (monospace) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            trailingIcon = if (password) {
                {
                    Icon(
                        if (visible) Glyphs.EyeOff else Glyphs.Eye,
                        if (visible) "Hide password" else "Show password",
                        Modifier.size(20.dp).clip(CircleShape).clickable { visible = !visible },
                        tint = c.muted,
                    )
                }
            } else {
                null
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.emerald.base,
                unfocusedBorderColor = c.outline,
                errorBorderColor = c.ruby.base,
                focusedLabelColor = c.emerald.base,
                unfocusedLabelColor = c.muted,
                errorLabelColor = c.ruby.base,
                cursorColor = c.emerald.base,
                focusedTextColor = c.text,
                unfocusedTextColor = c.text,
                focusedContainerColor = c.surfaceHigh.copy(alpha = 0.5f),
                unfocusedContainerColor = c.surfaceHigh.copy(alpha = 0.5f),
                errorContainerColor = c.surfaceHigh.copy(alpha = 0.5f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(error != null) {
            Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.ruby.base, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
        }
    }
}

/** A pill-shaped choice whose highlight slides to the selected option. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Stayline.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .background(c.surfaceHigh)
            .padding(4.dp),
    ) {
        val width = maxWidth / options.size
        val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        val x by animateDpAsState(width * index, Motion.snappy(), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(width)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(c.surface)
                .border(1.dp, c.outline, CircleShape),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (value, label) ->
                val active = value == selected
                val color by animateColorAsState(if (active) c.text else c.muted, label = "segment-text")
                Box(
                    Modifier
                        .width(width)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(role = Role.Tab) { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = color)
                }
            }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Stayline.colors.text)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Stayline.colors.muted) }
        }
        Spacer(Modifier.width(12.dp))
        // Centred on the title's line, however long the description runs.
        Box(Modifier.height(TITLE_LINE), contentAlignment = Alignment.Center) {
            Box(Modifier.wrapContentHeight(unbounded = true)) { trailing() }
        }
    }
}

/** Line height of `titleMedium`. */
private val TITLE_LINE = 22.dp

/** Space the floating nav takes at the bottom, above the system bar. */
val NavClearance = 92.dp

/** Eyebrow and title at the top of every tab, so nothing moves when switching. */
@Composable
fun ScreenHeader(eyebrow: String, title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val c = Stayline.colors
    Row(
        modifier.fillMaxWidth().height(HEADER_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = c.muted)
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1)
        }
        trailing()
    }
}

private val HEADER_HEIGHT = 64.dp

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Stayline.colors
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.emerald.base,
            checkedBorderColor = c.emerald.base,
            uncheckedThumbColor = c.muted,
            uncheckedTrackColor = c.surfaceHigh,
            uncheckedBorderColor = c.outline,
        ),
    )
}

/** A small rounded label. */
@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Each character rolls in from below as it changes, like an odometer. */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    Row(modifier) {
        text.forEachIndexed { index, char ->
            key(index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (reduced) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (slideInVertically(Motion.gentle()) { it } + fadeIn()) togetherWith
                                (slideOutVertically(Motion.gentle()) { -it } + fadeOut())
                        }
                    },
                    label = "digit",
                ) { c ->
                    Text(c.toString(), style = style.copy(fontFeatureSettings = TABULAR), color = color)
                }
            }
        }
    }
}

/** The combined throughput of the last minute, filled underneath. */
@Composable
fun Sparkline(samples: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val peak = (samples.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val animatedPeak by animateFloatAsState(peak, tween(600), label = "peak")
    Canvas(modifier) {
        if (samples.size < 2) {
            val y = size.height - 1.dp.toPx()
            drawLine(color.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), StrokeCap.Round)
            return@Canvas
        }
        val step = size.width / (SPARK_POINTS - 1)
        val start = size.width - step * (samples.size - 1)
        fun y(v: Float) = size.height - (v / animatedPeak).coerceIn(0f, 1f) * size.height * 0.9f
        val line = Path().apply {
            moveTo(start, y(samples[0]))
            for (i in 1 until samples.size) {
                val x0 = start + step * (i - 1)
                val x1 = start + step * i
                val mid = (x0 + x1) / 2
                cubicTo(mid, y(samples[i - 1]), mid, y(samples[i]), x1, y(samples[i]))
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(start, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private const val SPARK_POINTS = 30

/**
 * A SHA-256 fingerprint in blocks of eight, the way the desktop app shows
 * it, so the two can be compared at a glance. Blocks wrap to the width and
 * appear one after another.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FingerprintBlocks(hex: String, color: Color, modifier: Modifier = Modifier) {
    val blocks = hex.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT).chunked(8)
    val reduced = LocalReducedMotion.current
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEachIndexed { index, block ->
            var shown by remember(hex) { mutableStateOf(reduced) }
            LaunchedEffect(hex) {
                delay((index * Motion.STAGGER_MS).toLong())
                shown = true
            }
            val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "block")
            val lift by animateDpAsState(if (shown) 0.dp else 6.dp, Motion.gentle(), label = "lift")
            Text(
                block,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = color,
                modifier = Modifier
                    .offset(y = lift)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** A coloured strip with an icon, a message and an optional action. */
@Composable
fun Banner(
    icon: ImageVector,
    title: String,
    message: String,
    jewel: Jewel,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(jewel.base.copy(alpha = 0.12f))
            .border(1.dp, jewel.base.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(jewel.base.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = jewel.base)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Stayline.colors.text)
            Text(message, style = MaterialTheme.typography.bodySmall, color = Stayline.colors.muted, maxLines = 3)
        }
        action?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelLarge,
                color = jewel.base,
                modifier = Modifier.clip(CircleShape).pressable(onAction).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

data class NavItem<T>(val value: T, val label: String, val icon: ImageVector)

/**
 * A floating bar. The selected tab grows a label and an emerald pill
 * slides to sit behind it.
 */
@Composable
fun <T> FloatingNav(items: List<NavItem<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Stayline.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.outline, CircleShape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val active = item.value == selected
            val background by animateColorAsState(if (active) c.emerald.base.copy(alpha = 0.16f) else Color.Transparent, Motion.gentle(), label = "nav-bg")
            val tint by animateColorAsState(if (active) c.emerald.base else c.muted, Motion.gentle(), label = "nav-tint")
            Row(
                Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(background)
                    .pressable({ onSelect(item.value) }, role = Role.Tab)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, item.label, Modifier.size(22.dp), tint = tint)
                AnimatedVisibility(
                    active,
                    enter = expandHorizontally(Motion.snappy()) + fadeIn(),
                    exit = shrinkHorizontally(Motion.gentle()) + fadeOut(),
                ) {
                    Text(item.label, style = MaterialTheme.typography.titleSmall, color = tint, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** A sheet's title row with a close button. */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Stayline.colors.text, modifier = Modifier.weight(1f))
        IconBubble(Glyphs.Close, "Close", onClose)
    }
}

fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(Locale.ROOT, if (value < 10) "%.1f %s" else "%.0f %s", value, units[unit])
}

fun formatRate(bytesPerSecond: Long) = "${formatBytes(bytesPerSecond)}/s"

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
}
