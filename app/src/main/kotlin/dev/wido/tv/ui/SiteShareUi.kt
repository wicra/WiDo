package dev.wido.tv.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.Hosts
import dev.wido.core.SharedSite
import dev.wido.core.Site
import dev.wido.core.SitePack
import dev.wido.tv.R
import dev.wido.tv.ScreenLink
import kotlinx.coroutines.launch

/** TV : des sites proposés par un écran lié attendent une confirmation (affichée sur l'accueil). */
@Composable
fun SharePromptHost(link: ScreenLink) {
    val share by link.incomingShare.collectAsState()
    val current = share ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SharePrompt(
        from = current.from.name,
        sites = current.sites,
        onAccept = { chosen ->
            scope.launch {
                val added = link.acceptShare(chosen)
                val text = if (added > 0) {
                    context.resources.getQuantityString(R.plurals.share_added, added, added)
                } else {
                    context.getString(R.string.share_nothing_new)
                }
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
        },
        onRefuse = { link.dismissShare() },
    )
}

/**
 * « Ajouter ces sites ? » : un téléphone lié propose des sites. Chacun se coche ou se décoche ; rien
 * n'est ajouté sans « Ajouter ». Les comptes et cookies ne voyagent jamais, seulement l'adresse et les
 * réglages de blocage.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharePrompt(from: String, sites: List<SharedSite>, onAccept: (List<SharedSite>) -> Unit, onRefuse: () -> Unit) {
    var chosen by remember(sites) { mutableStateOf(sites.toSet()) }
    val add = remember { FocusRequester() }
    LaunchedEffect(sites) { runCatching { add.requestFocus() } }
    DialogFrame(
        title = stringResource(R.string.share_prompt_title),
        onDismiss = onRefuse,
        width = 720.dp,
        buttons = {
            ActionButton(
                pluralStringResource(R.plurals.share_add_button, chosen.size, chosen.size),
                { if (chosen.isNotEmpty()) onAccept(sites.filter { it in chosen }) },
                Modifier.focusRequester(add),
                glyph = Glyph.Check,
            )
            ActionButton(stringResource(R.string.pair_refuse), onRefuse, primary = false, idleText = Color.White.copy(alpha = 0.7f), glyph = Glyph.Close)
        },
    ) {
        Text(stringResource(R.string.share_prompt_body, from), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sites.forEach { site ->
                val on = site in chosen
                Chip(
                    "${site.name} · ${Hosts.of(site.url)?.removePrefix("www.").orEmpty()}",
                    { chosen = if (on) chosen - site else chosen + site },
                    selected = on,
                    glyph = if (on) Glyph.Check else Glyph.Plus,
                )
            }
        }
        Text(stringResource(R.string.share_prompt_note), color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

/**
 * Téléphone : « Envoyer des sites à Salon ». On coche des sites (ou on part d'un pack enregistré), on
 * peut garder la sélection comme pack pour la renvoyer plus tard, puis la TV demande confirmation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendSitesDialog(
    tvName: String,
    sites: List<Site>,
    packs: List<SitePack>,
    onSend: (List<SharedSite>) -> Unit,
    onSavePack: (SitePack) -> Unit,
    onDeletePack: (SitePack) -> Unit,
    onDismiss: () -> Unit,
) {
    val shared = remember(sites) { sites.map { dev.wido.core.SiteSharing.fromSite(it) } }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    var packName by remember { mutableStateOf("") }
    val selection = shared.filter { it.url in chosen }
    DialogFrame(
        title = stringResource(R.string.share_send_title, tvName),
        onDismiss = onDismiss,
        buttons = {
            ActionButton(
                pluralStringResource(R.plurals.share_send_button, selection.size, selection.size),
                { if (selection.isNotEmpty()) onSend(selection) },
                glyph = Glyph.Tv,
            )
            ActionButton(stringResource(R.string.action_cancel), onDismiss, primary = false)
        },
    ) {
        if (packs.isNotEmpty()) {
            SectionLabel(stringResource(R.string.share_packs))
            packs.forEach { pack ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        pluralStringResource(R.plurals.share_pack_label, pack.sites.size, pack.name, pack.sites.size),
                        { onSend(pack.sites) },
                        Modifier.weight(1f),
                        glyph = Glyph.Tv,
                    )
                    Chip(stringResource(R.string.action_remove), { onDeletePack(pack) }, idleText = Color.White.copy(alpha = 0.6f))
                }
            }
            SectionLabel(stringResource(R.string.share_or_choose))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shared.forEach { site ->
                val on = site.url in chosen
                Chip(site.name, { chosen = if (on) chosen - site.url else chosen + site.url }, selected = on, glyph = if (on) Glyph.Check else Glyph.Plus)
            }
        }
        if (selection.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextField(
                    value = packName,
                    onValueChange = { packName = it.take(30) },
                    label = stringResource(R.string.share_pack_name),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions.Default,
                )
                ActionButton(
                    stringResource(R.string.share_pack_save),
                    {
                        if (packName.isNotBlank()) {
                            onSavePack(SitePack(packName.trim(), selection))
                            packName = ""
                        }
                    },
                    primary = false,
                    glyph = Glyph.Save,
                )
            }
        }
        Text(stringResource(R.string.share_send_note, tvName), color = Palette.TextSecondary, fontSize = 13.sp)
    }
}
