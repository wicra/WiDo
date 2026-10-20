package dev.wido.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.Catalog
import dev.wido.tv.R

/**
 * Ajouter un site : d'abord le catalogue des sites prêts (un clic, réglages déjà choisis), puis
 * « Autre adresse » pour le formulaire. `onPick` renvoie un message d'erreur, ou null si c'est fait.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogDialog(
    entries: List<Catalog.Entry>,
    onPick: (Catalog.Entry) -> String?,
    onOther: () -> Unit,
    onDismiss: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    DialogFrame(
        title = stringResource(R.string.catalog_title),
        onDismiss = onDismiss,
        width = 820.dp,
        buttons = {
            ActionButton(stringResource(R.string.catalog_other), onOther, if (entries.isEmpty()) Modifier.focusRequester(first) else Modifier)
            ActionButton(stringResource(R.string.action_cancel), onDismiss, primary = false)
        },
    ) {
        if (entries.isEmpty()) {
            Text(stringResource(R.string.catalog_empty), color = Palette.TextSecondary, fontSize = 15.sp)
        } else {
            Text(stringResource(R.string.catalog_hint), color = Palette.TextSecondary, fontSize = 14.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                entries.forEachIndexed { index, entry ->
                    CatalogTile(
                        entry,
                        onClick = { error = onPick(entry) },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
        error?.let { Text(it, color = Palette.Warning, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun CatalogTile(entry: Catalog.Entry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = runCatching { Color(android.graphics.Color.parseColor(entry.color)) }.getOrDefault(Palette.Accent)
    FocusSurface(onClick, modifier, shape = RowShape, idle = Palette.Glass) { focused ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Text(entry.name, color = onSurface(focused), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
