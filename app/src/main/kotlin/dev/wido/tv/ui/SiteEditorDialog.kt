package dev.wido.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.wido.core.LocalSites
import dev.wido.core.Navigation
import dev.wido.core.Site
import dev.wido.tv.R

private const val SCHEME = "https://"

/** Suffixes proposés en un bouton : taper une adresse à la télécommande est pénible. */
private val URL_HELPERS = listOf("www.", ".com", ".fr", ".org", ".net", ".tv", "/")

/**
 * Ajout ou modification d'un site perso.
 *
 * Pensé pour la télécommande : on commence par l'adresse (aidée par des boutons), le nom s'en déduit
 * tout seul, les protections sont des boutons, et les domaines se choisissent parmi ceux que le site a
 * réellement essayé de joindre plutôt que d'être saisis.
 *
 * `onSave` renvoie un message d'erreur à afficher, ou null si le site est enregistré.
 * `suggestAllow` / `suggestBlock` : domaines appris à l'usage du site (vides pour un nouveau site).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SiteEditorDialog(
    initial: Site?,
    onSave: (name: String, url: String, options: LocalSites.SiteOptions) -> String?,
    onDismiss: () -> Unit,
    suggestAllow: List<String> = emptyList(),
    suggestBlock: List<String> = emptyList(),
    defaultNavigation: String = Navigation.STRICT,
) {
    val start = initial?.let { LocalSites.optionsOf(it) } ?: LocalSites.SiteOptions(navigation = defaultNavigation)

    var url by remember { mutableStateOf(initial?.url ?: SCHEME) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    // Tant qu'on n'a pas touché au nom, il suit l'adresse.
    var nameEdited by remember { mutableStateOf(initial != null) }
    var error by remember { mutableStateOf<String?>(null) }

    var blockAds by remember { mutableStateOf(start.blockAds) }
    var blockPopups by remember { mutableStateOf(start.blockPopups) }
    var hideCookies by remember { mutableStateOf(start.hideCookieBanners) }
    var allowLogins by remember { mutableStateOf(start.allowLogins) }
    var navigation by remember { mutableStateOf(start.navigation) }
    val allowed = remember { mutableStateListOf<String>().apply { addAll(start.allowedDomains) } }
    val blocked = remember { mutableStateListOf<String>().apply { addAll(start.blockedDomains) } }
    val urlFocus = remember { FocusRequester() }
    var showPhone by remember { mutableStateOf(false) }

    fun setUrl(next: String) {
        url = next
        error = null
        if (!nameEdited) name = LocalSites.suggestName(next)
    }

    fun submit() {
        val options = LocalSites.SiteOptions(
            allowedDomains = allowed.toList(),
            blockedDomains = blocked.toList(),
            navigation = navigation,
            blockAds = blockAds,
            blockPopups = blockPopups,
            hideCookieBanners = hideCookies,
            allowLogins = allowLogins,
        )
        error = onSave(name, url, options)
    }

    LaunchedEffect(Unit) { if (initial == null) runCatching { urlFocus.requestFocus() } }

    // Plein écran : le formulaire est un panneau de verre posé sur des halos ambre et bleu.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .halo(listOf(Glow(Palette.Amber, 0.85f, 0.1f, 0.6f, 0.7f), Glow(Palette.Accent, 0.05f, 0.95f, 0.6f, 0.7f)))
                .padding(horizontal = 150.dp, vertical = 28.dp),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .glassPanel(elevation = 30.dp)
                    .padding(horizontal = 30.dp, vertical = 26.dp),
            ) {
                DialogBody(
                    title = stringResource(if (initial == null) R.string.editor_title_add else R.string.editor_title_edit),
                    buttons = {
                        ActionButton(stringResource(R.string.action_save), { submit() })
                        ActionButton(stringResource(R.string.action_cancel), onDismiss, primary = false)
                    },
                    fill = true,
                ) {
                    // 1. L'adresse, avec ses aides.
                    GlassTextField(
                        value = url,
                        onValueChange = { setUrl(it) },
                        label = stringResource(R.string.field_url),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().focusRequester(urlFocus),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(SCHEME, { setUrl(SCHEME + url.removePrefix("https://").removePrefix("http://")) })
                        URL_HELPERS.forEach { helper -> Chip(helper, { setUrl(url + helper) }) }
                        Chip("⌫", { setUrl(url.dropLast(1)) })
                        Chip(stringResource(R.string.editor_phone), { showPhone = true })
                    }

                    // 2. Le nom, déduit de l'adresse, modifiable.
                    GlassTextField(
                        value = name,
                        onValueChange = { name = it; nameEdited = true; error = null },
                        label = stringResource(R.string.field_name),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = Palette.Warning, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }

                    // 3. Les protections : des boutons.
                    SectionLabel(stringResource(R.string.section_protection))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleChip(stringResource(R.string.p_ads), blockAds) { blockAds = !blockAds }
                        ToggleChip(stringResource(R.string.p_popups), blockPopups) { blockPopups = !blockPopups }
                        ToggleChip(stringResource(R.string.p_cookies), hideCookies) { hideCookies = !hideCookies }
                        ToggleChip(stringResource(R.string.p_logins), allowLogins) { allowLogins = !allowLogins }
                        // Redirections : strictes → souples → mixtes → strictes.
                        ToggleChip(stringResource(navigationLabel(navigation)), navigation != Navigation.STRICT) {
                            navigation = when (navigation) {
                                Navigation.STRICT -> Navigation.RELAXED
                                Navigation.RELAXED -> Navigation.MIXED
                                else -> Navigation.STRICT
                            }
                        }
                    }

                    // 4. Les domaines : on choisit, on ne saisit pas.
                    val allowSuggestions = suggestAllow.filterNot { it in allowed }
                    if (allowed.isNotEmpty() || allowSuggestions.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.section_allowed))
                        DomainChips(chosen = allowed, suggestions = allowSuggestions, tint = Palette.Ok, tintText = Color(0xFF9CF2C3))
                    }
                    val blockSuggestions = suggestBlock.filterNot { it in blocked }
                    if (blocked.isNotEmpty() || blockSuggestions.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.section_blocked))
                        DomainChips(chosen = blocked, suggestions = blockSuggestions, tint = Palette.Warning, tintText = Color(0xFFFFD2A1))
                    }
                }
            }
        }
    }
    if (showPhone) {
        PhoneDialog(
            stringResource(R.string.phone_hint_url),
            onMessage = {
                setUrl(it.text.trim())
                showPhone = false
            },
            onDismiss = { showPhone = false },
        )
    }
}

private fun navigationLabel(navigation: String): Int = when (navigation) {
    Navigation.RELAXED -> R.string.nav_relaxed
    Navigation.MIXED -> R.string.nav_mixed
    else -> R.string.nav_strict
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onToggle: () -> Unit) {
    Chip(
        if (on) "✓ $label" else label,
        onToggle,
        selected = on,
        idleText = if (on) Palette.TextPrimary else Color.White.copy(alpha = 0.6f),
        idle = if (on) Color.White.copy(alpha = 0.28f) else Palette.GlassFaint,
    )
}

/** Les domaines retenus (toucher pour retirer) puis ceux que le site a essayé de joindre (toucher pour ajouter). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DomainChips(chosen: MutableList<String>, suggestions: List<String>, tint: Color, tintText: Color) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chosen.toList().forEach { domain ->
            Chip("✓ $domain", { chosen.remove(domain) }, selected = true, idleText = tintText, idle = tint.copy(alpha = 0.22f))
        }
        suggestions.forEach { domain -> Chip("+ $domain", { chosen.add(domain) }) }
    }
}
