package dev.wido.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Bouton arrondi activable à la télécommande. `compact` : version discrète pour la barre du haut. */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    compact: Boolean = false,
) {
    Box(
        modifier
            .tvFocusable(PillShape, glow = Palette.Accent, focusScale = 1.05f, glowElevation = 12.dp, onClick = onClick)
            .background(if (primary) Palette.Accent else Color.White.copy(alpha = 0.10f))
            .padding(horizontal = if (compact) 18.dp else 24.dp, vertical = if (compact) 8.dp else 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Pastille activable : un réglage à bascule (`selected`), ou un domaine à ajouter / retirer. */
@Composable
fun Chip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Box(
        modifier
            .tvFocusable(PillShape, glow = Palette.Accent, focusScale = 1.05f, glowElevation = 10.dp, onClick = onClick)
            .background(if (selected) Palette.Accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else Palette.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Palette.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * Cadre commun des fenêtres : titre en haut, contenu défilant au milieu, boutons toujours visibles en bas.
 * Ainsi rien ne se superpose, quelle que soit la taille du contenu.
 */
@Composable
fun DialogFrame(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = 720.dp,
    buttons: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(width)
                .heightIn(max = 480.dp)
                .clip(CardShape)
                .background(Palette.SurfaceHigh)
                .padding(24.dp),
        ) {
            Text(title, color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
        }
    }
}
