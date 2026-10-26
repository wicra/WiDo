package dev.wido.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.PlayerOptions
import dev.wido.core.TrackOption
import dev.wido.tv.R

/**
 * Options du film dans le lecteur natif, à la manière des applis de streaming : un plateau de verre en bas, une
 * colonne par réglage (Audio, Sous-titres, Qualité, Vitesse), le choix en cours coché. ◀ ▶ changent de colonne,
 * ▲ ▼ parcourent les choix, OK choisit (le plateau reste ouvert), Retour le ferme. Une colonne sans choix n'est
 * pas montrée. Téléphone (`compact`) : les colonnes défilent de côté, toucher à côté ferme.
 */
@Composable
fun PlayerOptionsSheet(
    options: PlayerOptions,
    decimalComma: Boolean,
    compact: Boolean = false,
    onPick: (kind: String, id: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!compact) runCatching { first.requestFocus() } }
    val speeds = PlayerOptions.SPEEDS.map { TrackOption(it.toString(), PlayerOptions.speedLabel(it, decimalComma), it == options.speed) }
    val columns = listOfNotNull(
        options.audio.takeIf { it.isNotEmpty() }?.let { Triple(PlayerOptions.AUDIO, stringResource(R.string.opt_audio), it) },
        options.subtitles.takeIf { it.isNotEmpty() }?.let { Triple(PlayerOptions.SUBTITLES, stringResource(R.string.opt_subtitles), it) },
        options.quality.takeIf { it.isNotEmpty() }?.let { Triple(PlayerOptions.QUALITY, stringResource(R.string.opt_quality), it) },
        Triple(PlayerOptions.SPEED, stringResource(R.string.opt_speed), speeds),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0.25f to Color.Transparent, 0.6f to Palette.Background.copy(alpha = 0.8f), 1f to Palette.Background.copy(alpha = 0.97f)))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .then(if (compact) Modifier.navigationBarsPadding().padding(10.dp) else Modifier.padding(start = 40.dp, end = 40.dp, bottom = 32.dp))
                .fillMaxWidth()
                .glassPanel(Palette.PanelSolid, elevation = 30.dp)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(horizontal = if (compact) 16.dp else 28.dp, vertical = if (compact) 16.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.opt_title), fontSize = if (compact) 18.sp else 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 36.dp),
            ) {
                columns.forEachIndexed { c, (kind, title, list) ->
                    Column(Modifier.widthIn(min = 150.dp, max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel(title)
                        Spacer(Modifier.size(2.dp))
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Le focus arrive sur le choix en cours de la première colonne.
                            val focusAt = if (c == 0) list.indexOfFirst { it.selected }.coerceAtLeast(0) else -1
                            list.forEachIndexed { i, option ->
                                OptionRow(option, if (i == focusAt) Modifier.focusRequester(first) else Modifier) { onPick(kind, option.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Un choix : coche s'il est en cours, verre clair au focus. */
@Composable
private fun OptionRow(option: TrackOption, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick, modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), idle = Color.Transparent, focusScale = 1.03f, contentAlignment = Alignment.CenterStart) { focused ->
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                if (option.selected) GlyphIcon(Glyph.Check, if (focused) Palette.Ink else Palette.Accent, 18.dp)
            }
            Text(
                option.label,
                color = onSurface(focused, if (option.selected) Palette.TextPrimary else Palette.TextSecondary),
                fontSize = 16.sp,
                fontWeight = if (option.selected || focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
