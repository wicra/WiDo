package dev.wido.tv.ui

import androidx.compose.foundation.background
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

/** Une action du menu rapide. */
data class QuickAction(val label: String, val run: () -> Unit)

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
 * La barre de lecture (si une vidéo est connue), puis une rangée de pastilles. Une redirection qui vient
 * d'être refusée prend la tête, sur un verre teinté ambre, avec le focus sur « Ouvrir une fois ».
 */
@Composable
fun QuickMenu(media: MediaState?, redirect: BlockedRedirect?, actions: List<QuickAction>, counts: String) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 40.dp, end = 40.dp, bottom = 32.dp)
                .fillMaxWidth()
                .glassPanel(Palette.PanelSolid, elevation = 30.dp)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (redirect != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(RedirectTint).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(redirect.title.uppercase(), color = Palette.Warning, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(redirect.host, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton(redirect.openOnce.label, redirect.openOnce.run, Modifier.focusRequester(first))
                        ActionButton(redirect.allow.label, redirect.allow.run, primary = false)
                        ActionButton(redirect.ignore.label, redirect.ignore.run, primary = false, idleText = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
            if (media != null && media.duration > 0) MediaBar(media)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    actions.forEachIndexed { index, action ->
                        val focus = if (index == 0 && redirect == null) Modifier.focusRequester(first) else Modifier
                        ActionButton(action.label, action.run, focus, primary = false)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(counts, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** Temps écoulé, barre de progression blanche, temps restant et volume. */
@Composable
private fun MediaBar(media: MediaState) {
    val progress = (media.time / media.duration).toFloat().coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (media.paused) "❚❚" else "▶", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(PlayerFormat.time(media.time), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(3.dp)).background(Color.White))
        }
        Text("−" + PlayerFormat.time(media.duration - media.time), color = Palette.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("· ${PlayerFormat.percent(media.volume)} %", color = Palette.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
