package dev.wido.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.Catalog
import dev.wido.tv.MenuViewModel
import dev.wido.tv.R
import dev.wido.tv.WiDoApp
import kotlinx.coroutines.delay

/**
 * Premier lancement sur TV, en trois écrans : choisir ses sites, lier son téléphone, retenir les gestes
 * de la télécommande. « Passer » est toujours là ; on peut la revoir depuis les réglages.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Onboarding(vm: MenuViewModel, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as WiDoApp
    val sites by vm.sites.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    val chosen = remember { mutableStateListOf<Catalog.Entry>() }
    val entries = remember(sites) { Catalog.available(sites.map { it.site.url }) }
    val primary = remember { FocusRequester() }
    LaunchedEffect(step) { runCatching { primary.requestFocus() } }

    fun next() {
        if (step == 0) chosen.forEach { vm.addFromCatalog(it) }
        if (step < 2) step++ else onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .halo(listOf(Glow(Palette.Accent, 0.1f, 0.1f, 0.6f, 0.7f), Glow(Palette.Purple, 0.95f, 0.9f, 0.6f, 0.7f))),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 72.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                // Avancement : trois points, l'étape en cours en blanc.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { i ->
                        Box(Modifier.size(if (i == step) 10.dp else 8.dp).clip(CircleShape).background(if (i == step) Color.White else Color.White.copy(alpha = 0.3f)))
                    }
                }
            }
            when (step) {
                0 -> {
                    Title(stringResource(R.string.onb_sites_title), stringResource(R.string.onb_sites_body))
                    if (entries.isEmpty()) {
                        Text(stringResource(R.string.onb_sites_none), color = Palette.TextSecondary, fontSize = 16.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            entries.forEach { entry ->
                                val on = entry in chosen
                                Chip(
                                    entry.name,
                                    { if (on) chosen.remove(entry) else chosen.add(entry) },
                                    selected = on,
                                )
                            }
                        }
                    }
                    Text(stringResource(R.string.onb_sites_more), color = Palette.TextMuted, fontSize = 14.sp)
                }
                1 -> {
                    Title(stringResource(R.string.onb_phone_title), stringResource(R.string.onb_phone_body))
                    // Le serveur s'ouvre avec l'écran : l'adresse arrive en une seconde ou deux.
                    val pair by produceState<Pair<String?, String?>>(null to null) {
                        while (true) {
                            value = app.remote.pairUri to app.remote.address
                            if (value.first != null) break
                            delay(1_000)
                        }
                    }
                    LinkPairingCard(pair.first, pair.second)
                }
                else -> {
                    Title(stringResource(R.string.onb_remote_title), stringResource(R.string.onb_remote_body))
                    Gesture(listOf(Glyph.Left, Glyph.Right), stringResource(R.string.onb_gesture_arrows))
                    Gesture(listOf(Glyph.Check), stringResource(R.string.onb_gesture_ok))
                    Gesture(listOf(Glyph.Back), stringResource(R.string.onb_gesture_back))
                    Gesture(listOf(Glyph.Play, Glyph.Rewind, Glyph.Forward), stringResource(R.string.onb_gesture_media))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                ActionButton(
                    stringResource(if (step < 2) R.string.onb_next else R.string.onb_done),
                    { next() },
                    Modifier.focusRequester(primary),
                    glyph = if (step < 2) Glyph.Right else Glyph.Check,
                )
                if (step == 1) ActionButton(stringResource(R.string.onb_later), { step++ }, primary = false)
                ActionButton(stringResource(R.string.onb_skip), onDone, primary = false, idleText = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun Title(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 760.dp)) {
        Text(title, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp, lineHeight = 50.sp)
        Text(body, color = Palette.TextSecondary, fontSize = 18.sp, lineHeight = 26.sp)
    }
}

@Composable
private fun Gesture(glyphs: List<Glyph>, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(196.dp)) {
            glyphs.forEach { g ->
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Glass), contentAlignment = Alignment.Center) {
                    GlyphIcon(g, Color.White, 28.dp)
                }
            }
        }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
