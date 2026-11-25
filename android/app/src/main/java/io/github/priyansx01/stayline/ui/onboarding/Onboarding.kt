package io.github.priyansx01.stayline.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.priyansx01.stayline.ui.components.GemOrb
import io.github.priyansx01.stayline.ui.components.Glyphs
import io.github.priyansx01.stayline.ui.components.JewelButton
import io.github.priyansx01.stayline.ui.components.Mood
import io.github.priyansx01.stayline.ui.theme.LocalReducedMotion
import io.github.priyansx01.stayline.ui.theme.Motion
import io.github.priyansx01.stayline.ui.theme.Stayline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Onboarding(onDone: () -> Unit) {
    val c = Stayline.colors
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            if (page == 0) Welcome() else Promises()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 20.dp)) {
            repeat(2) { i ->
                val active = pager.currentPage == i
                val width by animateDpAsState(if (active) 24.dp else 8.dp, Motion.snappy(), label = "dot")
                val color by animateColorAsState(if (active) c.emerald.base else c.outline, label = "dot-color")
                Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
            }
        }
        val last = pager.currentPage == 1
        AnimatedContent(last, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "cta") { isLast ->
            JewelButton(
                if (isLast) "Get started" else "Next",
                onClick = { if (isLast) onDone() else scope.launch { pager.animateScrollToPage(1) } },
                icon = if (isLast) Glyphs.Check else Glyphs.ChevronRight,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The gem cycles through its moods to show what each colour means. */
@Composable
private fun Welcome() {
    val c = Stayline.colors
    val reduced = LocalReducedMotion.current
    var mood by remember { mutableStateOf(Mood.Off) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        while (true) {
            delay(1600); mood = Mood.Working
            delay(2200); mood = Mood.On
            delay(3000); mood = Mood.Off
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        GemOrb(mood, onClick = {}, label = "Stayline", size = 260.dp)
        Spacer(Modifier.height(24.dp))
        Text("Stay connected", style = MaterialTheme.typography.headlineMedium, color = c.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Stayline keeps your FortiGate VPN up through Wi-Fi changes, sleep and dead spots, and only asks when it really needs you.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Promises() {
    val c = Stayline.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Clear at a glance", style = MaterialTheme.typography.headlineMedium, color = c.text)
        Spacer(Modifier.height(24.dp))
        Promise(Glyphs.Shield, c.emerald.base, "Emerald means protected", "Your traffic is going through your company's gateway.")
        Promise(Glyphs.Warning, c.ruby.base, "Ruby means off, or it needs you", "A new password or a certificate to check.")
        Promise(Glyphs.Lock, c.topaz.base, "Passwords stay on this phone", "Encrypted with a key held by the Android Keystore.")
    }
}

@Composable
private fun Promise(icon: ImageVector, tint: Color, title: String, body: String) {
    val c = Stayline.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
    }
}
