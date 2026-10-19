package dev.wido.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.wido.core.Site
import dev.wido.tv.R

/**
 * Ajout ou modification d'un site perso. `onSave` renvoie un message d'erreur à afficher,
 * ou null si le site est enregistré (le dialogue est alors fermé par l'appelant).
 */
@Composable
fun SiteEditorDialog(
    initial: Site?,
    onSave: (name: String, url: String) -> String?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    fun submit() {
        error = onSave(name, url)
    }

    LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(680.dp)
                .clip(CardShape)
                .background(Palette.SurfaceHigh)
                .padding(32.dp),
        ) {
            Text(
                stringResource(if (initial == null) R.string.editor_title_add else R.string.editor_title_edit),
                color = Palette.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; error = null },
                label = { Text(stringResource(R.string.field_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Palette.Warning, fontSize = 15.sp)
            }
            Spacer(Modifier.height(24.dp))
            Row {
                ActionButton(stringResource(R.string.action_save), { submit() })
                Spacer(Modifier.width(14.dp))
                ActionButton(stringResource(R.string.action_cancel), onDismiss, primary = false)
                if (onDelete != null) {
                    Spacer(Modifier.width(14.dp))
                    ActionButton(
                        stringResource(if (confirmDelete) R.string.action_delete_confirm else R.string.action_delete),
                        { if (confirmDelete) onDelete() else confirmDelete = true },
                        primary = false,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.editor_hint), color = Palette.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
