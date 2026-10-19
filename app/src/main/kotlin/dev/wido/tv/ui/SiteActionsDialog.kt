package dev.wido.tv.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.wido.core.Site
import androidx.compose.ui.unit.dp
import dev.wido.tv.R

/** Options d'un site perso (appui long sur sa carte) : ouvrir, modifier, supprimer. */
@Composable
fun SiteActionsDialog(
    site: Site,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    DialogFrame(title = site.name, onDismiss = onDismiss, width = 460.dp) {
        ActionButton(stringResource(R.string.action_open), onOpen, Modifier.fillMaxWidth())
        ActionButton(stringResource(R.string.action_edit), onEdit, Modifier.fillMaxWidth(), primary = false)
        ActionButton(
            stringResource(if (confirmDelete) R.string.action_delete_confirm else R.string.action_delete),
            { if (confirmDelete) onDelete() else confirmDelete = true },
            Modifier.fillMaxWidth(),
            primary = false,
        )
    }
}
