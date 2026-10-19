package dev.wido.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.Hosts
import dev.wido.core.ReleaseInfo
import dev.wido.core.Site
import dev.wido.tv.BuildConfig
import dev.wido.tv.ConfigSource
import dev.wido.tv.ConfigState
import dev.wido.tv.MenuSite
import dev.wido.tv.MenuViewModel
import dev.wido.tv.R
import dev.wido.tv.SiteIcon
import dev.wido.tv.UpdateUi
import dev.wido.tv.WiDoApp
import kotlinx.coroutines.delay

private sealed interface Editor {
    data object Add : Editor
    data class Edit(val site: Site) : Editor
}

/**
 * Écran d'accueil : une barre en haut, la grille des sites, une ligne d'état en bas. Chaque zone a sa
 * propre place dans une colonne : rien ne se superpose.
 */
@Composable
fun MenuScreen(vm: MenuViewModel, onOpen: (Site) -> Unit) {
    val state by vm.config.collectAsState()
    val update by vm.update.collectAsState()
    val sites by vm.sites.collectAsState()

    var editor by remember { mutableStateOf<Editor?>(null) }
    var actionsFor by remember { mutableStateOf<Site?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val settings = (LocalContext.current.applicationContext as WiDoApp).settings

    // Ambiance : la lueur du haut prend la couleur de la carte sélectionnée.
    var ambientTarget by remember { mutableStateOf(Palette.Accent) }
    val ambient by animateColorAsState(ambientTarget, tween(700), label = "ambient")
    val firstCard = remember { FocusRequester() }

    LaunchedEffect(sites.isNotEmpty()) {
        if (sites.isNotEmpty()) runCatching { firstCard.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(ambient.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.5f, -size.height * 0.1f),
                        radius = size.width * 0.8f,
                    ),
                )
            }
            .padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state, onSettings = { showSettings = true }, onRefresh = vm::refresh)
            Spacer(Modifier.height(12.dp))
            UpdateBanner(update, onInstall = vm::install)
            Text(
                stringResource(R.string.menu_title).uppercase(),
                color = Palette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
            if (sites.isEmpty() && !state.loading) EmptyHint()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(210.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(sites, key = { _, it -> it.site.id }) { index, entry ->
                    SiteCard(
                        entry = entry,
                        index = index,
                        onClick = { onOpen(entry.site) },
                        onOptions = { actionsFor = entry.site },
                        onFocus = { ambientTarget = it },
                        modifier = if (index == 0) Modifier.focusRequester(firstCard) else Modifier,
                    )
                }
                item(key = "add") {
                    AddCard(index = sites.size, onClick = { editor = Editor.Add }, onFocus = { ambientTarget = it })
                }
            }
            Footer(update)
        }
    }

    actionsFor?.let { site ->
        SiteActionsDialog(
            site = site,
            onOpen = { actionsFor = null; onOpen(site) },
            onEdit = { actionsFor = null; editor = Editor.Edit(site) },
            onDelete = { vm.removeSite(site.id); actionsFor = null },
            onDismiss = { actionsFor = null },
        )
    }

    editor?.let { current ->
        val initial = (current as? Editor.Edit)?.site
        val (suggestAllow, suggestBlock) = remember(initial) { initial?.let { vm.suggestionsFor(it) } ?: (emptyList<String>() to emptyList()) }
        SiteEditorDialog(
            initial = initial,
            onSave = { name, url, options ->
                val error = if (initial == null) vm.addSite(name, url, options) else vm.updateSite(initial.id, name, url, options)
                if (error == null) editor = null
                error
            },
            onDismiss = { editor = null },
            suggestAllow = suggestAllow,
            suggestBlock = suggestBlock,
            defaultRelaxed = settings.newSitesRelaxed,
        )
    }

    if (showSettings) SettingsDialog(settings) { showSettings = false }
}

@Composable
private fun TopBar(state: ConfigState, onSettings: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.wido_logo), contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Text("WiDo", color = Palette.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        StatusDot(state)
        Spacer(Modifier.width(14.dp))
        ActionButton(stringResource(R.string.action_settings), onSettings, primary = false, compact = true)
        Spacer(Modifier.width(10.dp))
        ActionButton(stringResource(R.string.action_refresh), onRefresh, compact = true)
    }
}

/** État de la configuration en deux mots : un point de couleur et un libellé court. */
@Composable
private fun StatusDot(state: ConfigState) {
    val (text, dot) = when {
        state.loading && state.config == null -> stringResource(R.string.status_loading) to Palette.TextSecondary
        state.warnings.isNotEmpty() -> stringResource(R.string.status_warnings, state.warnings.size) to Palette.Warning
        state.source == ConfigSource.Remote -> stringResource(R.string.status_ok) to Palette.Ok
        else -> stringResource(R.string.status_offline) to Palette.Warning
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun UpdateBanner(update: UpdateUi, onInstall: (ReleaseInfo) -> Unit) {
    val (message, info) = when (update) {
        is UpdateUi.Available -> stringResource(R.string.update_available, update.info.tag) to update.info
        is UpdateUi.Downloading -> stringResource(R.string.update_downloading, update.percent) to null
        is UpdateUi.Failed -> stringResource(update.messageRes, update.detail.orEmpty()) to update.info
        UpdateUi.None, UpdateUi.Checking, UpdateUi.UpToDate, is UpdateUi.CheckFailed -> return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(CardShape)
            .background(Palette.Accent.copy(alpha = 0.18f))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = Palette.TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (info != null) ActionButton(stringResource(R.string.update_install), { onInstall(info) }, compact = true)
    }
}

/** Apparition en fondu et légère montée, décalée carte par carte. */
@Composable
private fun rememberEntrance(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(8) * 60L)
        progress.animateTo(1f, tween(400))
    }
    return Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 30f
    }
}

@Composable
private fun SiteCard(
    entry: MenuSite,
    index: Int,
    onClick: () -> Unit,
    onOptions: () -> Unit,
    onFocus: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val site = entry.site
    val context = LocalContext.current
    val icon by produceState<SiteIcon?>(null, site.id, site.url, site.icon) {
        value = (context.applicationContext as WiDoApp).favicons.load(site)
    }
    // Couleur : celle de la config si elle en impose une, sinon celle de l'icône du site.
    val accent = parseColor(site.color) ?: icon?.color ?: Palette.Accent
    var focused by remember { mutableStateOf(false) }

    // Options d'un site perso : appui long sur OK, ou touche Menu.
    val optionsKey = if (entry.local) {
        Modifier.onKeyEvent { e ->
            val handled = e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU && e.nativeKeyEvent.action == KeyEvent.ACTION_UP
            if (handled) onOptions()
            handled
        }
    } else {
        Modifier
    }

    Column(
        modifier
            .then(rememberEntrance(index))
            .then(optionsKey)
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .tvFocusable(
                CardShape,
                glow = accent,
                onFocusChange = { focused = it; if (it) onFocus(accent) },
                onLongClick = if (entry.local) onOptions else null,
                onClick = onClick,
            )
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), Palette.Surface)))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBubble(icon, site.name)
        Spacer(Modifier.height(10.dp))
        Text(
            site.name,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        val sub = when {
            focused && entry.local -> stringResource(R.string.hint_options)
            entry.local -> "${Hosts.of(site.url).orEmpty()} · ${stringResource(R.string.tag_local)}"
            else -> Hosts.of(site.url).orEmpty()
        }
        Text(sub, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun IconBubble(icon: SiteIcon?, name: String) {
    Box(
        Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon.image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(38.dp))
        } else {
            Text(name.firstOrNull()?.uppercase().orEmpty(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AddCard(index: Int, onClick: () -> Unit, onFocus: (Color) -> Unit) {
    Column(
        Modifier
            .then(rememberEntrance(index))
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .tvFocusable(CardShape, glow = Palette.Highlight, onFocusChange = { if (it) onFocus(Palette.Highlight) }, onClick = onClick)
            .background(Color.White.copy(alpha = 0.06f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("+", color = Palette.Highlight, fontSize = 44.sp, fontWeight = FontWeight.Light)
        Text(stringResource(R.string.add_site), color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun EmptyHint() {
    Text(
        stringResource(R.string.empty_body),
        color = Palette.TextSecondary,
        fontSize = 15.sp,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
    )
}

/** Une seule ligne discrète : la version à gauche, l'état de la mise à jour à droite. */
@Composable
private fun Footer(update: UpdateUi) {
    val (status, color) = when (update) {
        UpdateUi.Checking -> stringResource(R.string.update_checking) to Palette.TextSecondary
        UpdateUi.UpToDate -> stringResource(R.string.update_uptodate) to Palette.TextSecondary
        is UpdateUi.CheckFailed -> stringResource(R.string.update_check_failed, update.detail.take(40)) to Palette.Warning
        else -> "" to Palette.TextSecondary
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.version_footer, BuildConfig.VERSION_NAME), color = Palette.TextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        if (status.isNotEmpty()) Text(status, color = color, fontSize = 11.sp, maxLines = 1)
    }
}

private fun parseColor(hex: String?): Color? = hex?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}
