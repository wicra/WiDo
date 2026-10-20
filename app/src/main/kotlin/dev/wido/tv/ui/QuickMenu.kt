package dev.wido.tv.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.PlayerFormat
import dev.wido.tv.MediaState

/** Une action du menu rapide : une tuile avec son icône. `more` : rangée sous « Plus… ». */
data class QuickAction(val label: String, val glyph: Glyph = Glyph.More, val more: Boolean = false, val run: () -> Unit)

/** La dernière redirection refusée et ses trois réponses. */
class BlockedRedirect(
    val title: String,
    val host: String,
    val openOnce: QuickAction,
    val allow: QuickAction,
    val ignore: QuickAction,
)

private val RedirectTint = Brush.linearGradient(listOf(Color(0x52FF9646), Color(0x1FFF5A3C)))

/**
 * Menu rapide pendant la lecture : un plateau de verre en bas de l'écran, la page reste visible au-dessus.
 * La barre de lecture (si une vidéo est connue), puis des tuiles à icône, les plus utiles d'abord ; le
 * reste est sous « Plus… ». Une redirection qui vient d'être refusée prend la tête, sur un verre teinté
 * ambre, avec le focus sur « Ouvrir une fois ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickMenu(
    media: MediaState?,
    redirect: BlockedRedirect?,
    actions: List<QuickAction>,
    counts: String,
    /** Téléphone : plateau pleine largeur, tuiles plus petites, toucher à côté le ferme. */
    compact: Boolean = false,
    onDismiss: () -> Unit = {},
    moreLabel: String = "",
) {
    val first = remember { FocusRequester() }
    val firstMore = remember { FocusRequester() }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!compact) runCatching { first.requestFocus() } }
    LaunchedEffect(expanded) { if (expanded && !compact) runCatching { firstMore.requestFocus() } }
    val main = actions.filterNot { it.more }
    val extra = actions.filter { it.more }

    Box(
        Modifier
            .fillMaxSize()
            .then(if (compact) Modifier.background(Color.Black.copy(alpha = 0.35f)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss) else Modifier),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .then(if (compact) Modifier.navigationBarsPadding().padding(10.dp) else Modifier.padding(start = 40.dp, end = 40.dp, bottom = 32.dp))
                .fillMaxWidth()
                .glassPanel(Palette.PanelSolid, elevation = 30.dp)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(horizontal = if (compact) 14.dp else 24.dp, vertical = if (compact) 14.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
        ) {
            if (redirect != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(RedirectTint).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlyphIcon(Glyph.Blocked, Palette.Warning, 16.dp)
                        Text(redirect.title.uppercase(), color = Palette.Warning, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Text(redirect.host, fontSize = if (compact) 20.sp else 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton(redirect.openOnce.label, redirect.openOnce.run, Modifier.focusRequester(first))
                        ActionButton(redirect.allow.label, redirect.allow.run, primary = false)
                        ActionButton(redirect.ignore.label, redirect.ignore.run, primary = false, idleText = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
            if (media != null && media.duration > 0) MediaBar(media, compact)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
                main.forEachIndexed { index, action ->
                    val focus = if (index == 0 && redirect == null) Modifier.focusRequester(first) else Modifier
                    ActionTile(action, compact, focus)
                }
                if (extra.isNotEmpty() && !expanded) {
                    ActionTile(QuickAction(moreLabel, Glyph.More) { expanded = true }, compact)
                }
                if (expanded) {
                    extra.forEachIndexed { index, action ->
                        ActionTile(action, compact, if (index == 0) Modifier.focusRequester(firstMore) else Modifier, faint = true)
                    }
                }
            }
            Text(counts, color = Color.White.copy(alpha = 0.55f), fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** Une tuile : l'icône au-dessus d'un mot court ; blanche au focus. */
@Composable
private fun ActionTile(action: QuickAction, compact: Boolean, modifier: Modifier = Modifier, faint: Boolean = false) {
    FocusSurface(
        action.run,
        modifier.size(if (compact) 80.dp else 108.dp, if (compact) 72.dp else 88.dp),
        shape = RowShape,
        idle = if (faint) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f),
        focusScale = 1.08f,
    ) { focused ->
        Column(
            Modifier.padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp),
        ) {
            GlyphIcon(action.glyph, onSurface(focused), if (compact) 22.dp else 28.dp)
            Text(
                action.label,
                color = onSurface(focused, Color.White.copy(alpha = 0.85f)),
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Temps écoulé, barre de progression blanche, temps restant et volume. */
@Composable
private fun MediaBar(media: MediaState, compact: Boolean = false) {
    val progress = (media.time / media.duration).toFloat().coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GlyphIcon(if (media.paused) Glyph.Pause else Glyph.Play, Color.White, 16.dp)
        Text(PlayerFormat.time(media.time), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(3.dp)).background(Color.White))
        }
        Text("−" + PlayerFormat.time(media.duration - media.time), color = Palette.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (!compact) Text("· ${PlayerFormat.percent(media.volume)} %", color = Palette.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
