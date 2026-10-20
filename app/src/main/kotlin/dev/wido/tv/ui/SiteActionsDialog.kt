package dev.wido.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wido.core.Pin
import dev.wido.core.Site
import dev.wido.tv.R

/**
 * Options d'un site (bouton Options de l'accueil, appui long ou touche Menu sur sa carte) : ouvrir,
 * déplacer la carte, retirer une page épinglée ; pour un site perso, aussi modifier et supprimer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SiteActionsDialog(
    site: Site,
    local: Boolean,
    pins: List<Pin>,
    onOpen: () -> Unit,
    /** Déplacer la carte (télécommande) ; null sur téléphone. */
    onMove: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRemovePin: (Pin) -> Unit,
    hiddenCount: Int,
    onClearHides: () -> Unit,
    onDismiss: () -> Unit,
    /** Téléphone lié à une TV : ouvrir le site sur la TV. */
    onOpenOnTv: (() -> Unit)? = null,
    tvName: String? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    DialogFrame(title = site.name, onDismiss = onDismiss, width = 520.dp) {
        ActionButton(stringResource(R.string.action_open), onOpen, Modifier.fillMaxWidth())
        if (onOpenOnTv != null) ActionButton(stringResource(R.string.panel_send_to_tv, tvName.orEmpty()), onOpenOnTv, Modifier.fillMaxWidth(), primary = false)
        if (onMove != null) ActionButton(stringResource(R.string.action_move), onMove, Modifier.fillMaxWidth(), primary = false)
        if (local) {
            ActionButton(stringResource(R.string.action_edit), onEdit, Modifier.fillMaxWidth(), primary = false)
            ActionButton(
                stringResource(if (confirmDelete) R.string.action_delete_confirm else R.string.action_delete),
                { if (confirmDelete) onDelete() else confirmDelete = true },
                Modifier.fillMaxWidth(),
                primary = false,
            )
        }
        if (hiddenCount > 0) {
            ActionButton(stringResource(R.string.action_unhide, hiddenCount), onClearHides, Modifier.fillMaxWidth(), primary = false)
        }
        if (pins.isNotEmpty()) {
            SectionLabel(stringResource(R.string.pins_remove))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pins.forEach { pin -> Chip(pin.title, { onRemovePin(pin) }, glyph = Glyph.Close) }
            }
        }
    }
}
