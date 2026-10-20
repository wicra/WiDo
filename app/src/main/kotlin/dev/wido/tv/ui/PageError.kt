package dev.wido.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Une page qui ne s'ouvre pas : un écran au style de WiDo (fond de l'appli, message clair) au lieu de
 * la page grise du navigateur. Réessayer, Retour, et pour un site perso introuvable, Modifier l'adresse.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageError(
    title: String,
    body: String,
    retry: String,
    back: String,
    edit: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val phone = LocalPhone.current
    Box(
        Modifier.fillMaxSize().halo(listOf(Glow(Palette.Warning.copy(alpha = 0.5f), 0.5f, 0.15f, 0.7f, 0.6f), Glow(Palette.Accent, 0.9f, 1f, 0.7f, 0.6f))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 680.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlyphIcon(Glyph.Warning, Palette.Warning, if (phone) 44.dp else 56.dp)
            Text(title, fontSize = if (phone) 26.sp else 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, textAlign = TextAlign.Center)
            Text(body, color = Palette.TextSecondary, fontSize = if (phone) 15.sp else 18.sp, lineHeight = if (phone) 21.sp else 26.sp, textAlign = TextAlign.Center)
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(retry, onRetry, Modifier.focusRequester(first), glyph = Glyph.Refresh)
                if (edit != null) ActionButton(edit, onEdit, primary = false, glyph = Glyph.Globe)
                ActionButton(back, onBack, primary = false, idleText = Color.White.copy(alpha = 0.8f), glyph = Glyph.Back)
            }
        }
    }
}
