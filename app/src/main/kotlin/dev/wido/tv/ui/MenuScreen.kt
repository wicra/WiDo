package dev.wido.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.Catalog
import dev.wido.core.Hosts
import dev.wido.core.Pin
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
import java.util.Date

private sealed interface Editor {
    data object Add : Editor
    data class Edit(val site: Site) : Editor
}

private enum class Tab { Sites, Settings }

/** Voile qui assombrit le bas de l'écran d'accueil, sous la rangée de cartes. */
private val MenuVeil = Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color(0xD908080E))

/**
 * Écran d'accueil : le logo, les onglets Sites / Réglages et l'état en haut. L'onglet Sites montre en
 * grand le site sélectionné (son nom, Ouvrir, Options) au-dessus d'une rangée de cartes ; le fond prend
 * la lumière de la carte sélectionnée. L'onglet Réglages remplace la scène par la liste des réglages.
 */
@Composable
fun MenuScreen(vm: MenuViewModel, onOpen: (Site) -> Unit, onOpenPin: (Pin) -> Unit) {
    val state by vm.config.collectAsState()
    val update by vm.update.collectAsState()
    val sites by vm.sites.collectAsState()
    val lists by vm.lists.collectAsState()
    val pins by vm.pins.collectAsState()
    val hides by vm.hides.collectAsState()
    // Carte en cours de déplacement (◀ ▶ la déplacent, OK ou Retour terminent).
    var movingId by remember { mutableStateOf<String?>(null) }

    var tab by remember { mutableStateOf(Tab.Sites) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    var actionsFor by remember { mutableStateOf<Site?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    // Le site mis en scène : celui de la dernière carte focalisée (null = carte « Ajouter »).
    var currentId by remember { mutableStateOf<String?>(null) }
    var addFocused by remember { mutableStateOf(false) }
    val settings = (LocalContext.current.applicationContext as WiDoApp).settings

    // Ambiance : les halos prennent la couleur de la carte sélectionnée, ou le bleu-violet des réglages.
    var siteLight by remember { mutableStateOf(Palette.Accent) }
    val light by animateColorAsState(if (tab == Tab.Settings) Palette.Accent else siteLight, tween(700), label = "light")
    val second by animateColorAsState(if (tab == Tab.Settings) Palette.Purple else Palette.Violet, tween(700), label = "light2")
    val firstCard = remember { FocusRequester() }

    LaunchedEffect(sites.isNotEmpty()) {
        if (sites.isNotEmpty()) runCatching { firstCard.requestFocus() }
    }

    val glows = if (tab == Tab.Settings) {
        listOf(Glow(light, 0.1f, 0.1f, 0.6f, 0.7f), Glow(second, 0.95f, 0.9f, 0.6f, 0.7f))
    } else {
        listOf(Glow(light, 0.2f, 0.3f, 0.55f, 0.7f), Glow(second, 0.85f, 0.1f, 0.6f, 0.7f), Glow(Palette.Navy, 0.6f, 1.1f, 0.8f, 0.6f))
    }

    Box(Modifier.fillMaxSize().halo(glows, if (tab == Tab.Sites) MenuVeil else null)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state, tab, onTab = { tab = it }, onRefresh = vm::refresh)
            when (tab) {
                Tab.Sites -> {
                    UpdateBanner(update, onInstall = vm::install)
                    val current = if (addFocused) null else sites.firstOrNull { it.site.id == currentId } ?: sites.firstOrNull()
                    Hero(
                        current,
                        empty = sites.isEmpty() && !state.loading,
                        onOpen = { current?.let { onOpen(it.site) } },
                        onOptions = { current?.let { actionsFor = it.site } },
                        onAdd = { showCatalog = true },
                        pins = current?.let { c -> pins.filter { it.siteId == c.site.id } }.orEmpty(),
                        onPin = onOpenPin,
                        modifier = Modifier.weight(1f),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.Bottom,
                        contentPadding = PaddingValues(start = 52.dp, end = 52.dp, top = 30.dp),
                        modifier = Modifier.fillMaxWidth().height(176.dp),
                    ) {
                        itemsIndexed(sites, key = { _, it -> it.site.id }) { index, entry ->
                            SiteCard(
                                entry = entry,
                                index = index,
                                onClick = { onOpen(entry.site) },
                                onOptions = { actionsFor = entry.site },
                                moving = movingId == entry.site.id,
                                onMove = { delta -> vm.move(entry.site.id, delta) },
                                onMoveDone = { movingId = null },
                                onFocus = { color ->
                                    currentId = entry.site.id
                                    addFocused = false
                                    siteLight = color
                                },
                                modifier = if (index == 0) Modifier.focusRequester(firstCard) else Modifier,
                            )
                        }
                        item(key = "add") {
                            AddCard(
                                index = sites.size,
                                onClick = { showCatalog = true },
                                onFocus = {
                                    addFocused = true
                                    siteLight = Palette.Accent
                                },
                            )
                        }
                    }
                    Footer(update)
                }
                Tab.Settings -> SettingsPane(settings, lists, vm::updateLists, Modifier.weight(1f))
            }
        }
    }

    if (showCatalog) {
        CatalogDialog(
            entries = remember(sites) { Catalog.available(sites.map { it.site.url }) },
            onPick = { entry -> vm.addFromCatalog(entry).also { if (it == null) showCatalog = false } },
            onOther = {
                showCatalog = false
                editor = Editor.Add
            },
            onDismiss = { showCatalog = false },
        )
    }

    actionsFor?.let { site ->
        SiteActionsDialog(
            site = site,
            local = sites.firstOrNull { it.site.id == site.id }?.local == true,
            pins = pins.filter { it.siteId == site.id },
            onOpen = { actionsFor = null; onOpen(site) },
            onMove = { actionsFor = null; movingId = site.id },
            onEdit = { actionsFor = null; editor = Editor.Edit(site) },
            onDelete = { vm.removeSite(site.id); actionsFor = null },
            onRemovePin = vm::removePin,
            hiddenCount = hides[site.id]?.size ?: 0,
            onClearHides = { vm.clearHides(site.id) },
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
            defaultNavigation = settings.newSitesNavigation,
        )
    }
}

/** Le logo à gauche, la pilule d'onglets au centre, l'état et l'heure à droite. */
@Composable
private fun TopBar(state: ConfigState, tab: Tab, onTab: (Tab) -> Unit, onRefresh: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(start = 52.dp, end = 40.dp, top = 24.dp, bottom = 8.dp)) {
        Wordmark(Modifier.align(Alignment.CenterStart))
        Row(
            Modifier
                .align(Alignment.Center)
                .clip(PillShape)
                .background(Palette.Glass)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TabItem(stringResource(R.string.tab_sites), tab == Tab.Sites) { onTab(Tab.Sites) }
            TabItem(stringResource(R.string.action_settings), tab == Tab.Settings) { onTab(Tab.Settings) }
        }
        StatusPill(state, onRefresh, Modifier.align(Alignment.CenterEnd))
    }
}

/** Le logo : le nom de l'app en Plus Jakarta Sans extra-grasse. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text("WiDo", color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = modifier)
}

/** Un onglet : il s'active dès qu'il reçoit le focus, comme sur les écrans d'accueil des TV. */
@Composable
private fun TabItem(label: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "tab")
    Box(
        Modifier
            .tvFocusable(
                PillShape,
                focusScale = 1.08f,
                onFocusChange = {
                    focused = it
                    if (it) onSelect()
                },
                onClick = onSelect,
            )
            .background(background)
            .padding(horizontal = 20.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (selected) Palette.Ink else Color.White.copy(alpha = if (focused) 1f else 0.75f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** État de la configuration et heure. Activable : OK actualise la configuration. */
@Composable
private fun StatusPill(state: ConfigState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (text, dot) = when {
        state.loading && state.config == null -> stringResource(R.string.status_loading) to Palette.TextSecondary
        state.warnings.isNotEmpty() -> stringResource(R.string.status_warnings, state.warnings.size) to Palette.Warning
        state.source == ConfigSource.Remote -> stringResource(R.string.status_ok) to Palette.Ok
        else -> stringResource(R.string.status_offline) to Palette.Warning
    }
    val clock by produceState("") {
        val format = android.text.format.DateFormat.getTimeFormat(context)
        while (true) {
            value = format.format(Date())
            delay(15_000)
        }
    }
    FocusSurface(onRefresh, modifier, idle = Color.Transparent) { focused ->
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (focused) {
                Text("↻  " + stringResource(R.string.action_refresh), color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            } else {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
                Text("$text · $clock", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
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
            .padding(horizontal = 52.dp, vertical = 8.dp)
            .glassPanel(Palette.Glass, PillShape)
            .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        if (info != null) ActionButton(stringResource(R.string.update_install), { onInstall(info) }, compact = true)
    }
}

/** La scène : le site sélectionné en grand titre, son adresse, et ses actions. */
@Composable
private fun Hero(
    current: MenuSite?,
    empty: Boolean,
    onOpen: () -> Unit,
    onOptions: () -> Unit,
    onAdd: () -> Unit,
    pins: List<Pin>,
    onPin: (Pin) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(start = 52.dp, end = 52.dp, top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val title = current?.site?.name ?: stringResource(R.string.editor_title_add)
        Text(
            title,
            fontSize = 56.sp,
            lineHeight = 60.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-2).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = when {
            current != null -> {
                val host = Hosts.of(current.site.url).orEmpty()
                if (current.local) "$host · ${stringResource(R.string.tag_local)}" else host
            }
            empty -> stringResource(R.string.empty_body)
            else -> ""
        }
        if (subtitle.isNotEmpty()) {
            Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (current != null) {
                ActionButton("▶  " + stringResource(R.string.action_open), onOpen)
                ActionButton(stringResource(R.string.action_options), onOptions, primary = false, idleText = Color.White.copy(alpha = 0.8f))
            } else {
                ActionButton("+  " + stringResource(R.string.add_site), onAdd)
            }
        }
        // Pages épinglées de ce site : ouvertes directement.
        if (pins.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("★", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
                pins.forEach { pin -> Chip(pin.title, { onPin(pin) }) }
            }
        }
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

/**
 * Carte d'un site. Au repos : du verre. Avec le focus : elle grandit, passe à la couleur pleine du site
 * avec un reflet, et se soulève depuis son coin bas gauche. OK ouvre le site ; sur un site perso, la
 * touche Menu ou l'appui long ouvrent ses options (aussi accessibles par le bouton Options de la scène).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteCard(
    entry: MenuSite,
    index: Int,
    onClick: () -> Unit,
    onOptions: () -> Unit,
    moving: Boolean,
    onMove: (Int) -> Unit,
    onMoveDone: () -> Unit,
    onFocus: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val site = entry.site
    val context = LocalContext.current
    val icon by produceState<SiteIcon?>(null, site.id, site.url, site.icon) {
        value = (context.applicationContext as WiDoApp).favicons.load(site)
    }
    // Couleur : celle de la config si elle en impose une, sinon celle de l'icône du site.
    val cached = remember(site.id, site.url, site.icon) { (context.applicationContext as WiDoApp).favicons.cachedColor(site) }
    val accent = parseColor(site.color) ?: icon?.color ?: cached ?: Palette.Accent
    var focused by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (focused) 210.dp else 170.dp, label = "cardWidth")
    val height by animateDpAsState(if (focused) 126.dp else 102.dp, label = "cardHeight")
    // Le zoom part du coin bas gauche : cette marge évite de recouvrir la carte suivante.
    val room by animateDpAsState(if (focused) 18.dp else 0.dp, label = "cardRoom")

    val menuKey = Modifier.onKeyEvent { e ->
        val handled = e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU && e.nativeKeyEvent.action == KeyEvent.ACTION_UP
        if (handled) onOptions()
        handled
    }
    // Déplacement : ◀ ▶ déplacent la carte (elle garde le focus), OK ou Retour terminent.
    val moveKeys = if (moving) {
        Modifier.onPreviewKeyEvent { e ->
            val native = e.nativeKeyEvent
            val down = native.action == KeyEvent.ACTION_DOWN
            when (native.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) onMove(-1); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) onMove(1); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> { if (!down) onMoveDone(); true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
                else -> false
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier
            .then(rememberEntrance(index))
            .padding(end = room)
            .size(width, height)
            .then(menuKey)
            .then(moveKeys)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus(accent)
            }
            .liftEffect(focused, CardShape, focusScale = 1.08f, lift = 26.dp, origin = TransformOrigin(0f, 1f))
            .clip(CardShape)
            .then(if (focused) Modifier.background(siteGradient(accent)).background(GlassSheen) else Modifier.background(Palette.Glass))
            .then(if (moving) Modifier.border(2.dp, Color.White, CardShape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onOptions)
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column {
            Text(
                site.name,
                color = Color.White,
                fontSize = if (focused) 17.sp else 15.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (moving) {
                Text("◀  ▶", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            } else if (entry.local) {
                Text(stringResource(R.string.tag_local), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AddCard(index: Int, onClick: () -> Unit, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    FocusSurface(
        onClick,
        rememberEntrance(index)
            .size(102.dp)
            .border(1.5.dp, if (focused) Color.Transparent else Color.White.copy(alpha = 0.2f), CardShape),
        shape = CardShape,
        idle = Color.White.copy(alpha = 0.06f),
        focusScale = 1.08f,
        onFocusChange = {
            focused = it
            if (it) onFocus()
        },
    ) { f ->
        Text("+", color = onSurface(f), fontSize = 34.sp, fontWeight = FontWeight.Light)
    }
}

/** Une seule ligne discrète : la version à gauche, l'état de la mise à jour à droite. */
@Composable
private fun Footer(update: UpdateUi) {
    val (status, color) = when (update) {
        UpdateUi.Checking -> stringResource(R.string.update_checking) to Palette.TextMuted
        UpdateUi.UpToDate -> stringResource(R.string.update_uptodate) to Palette.TextMuted
        is UpdateUi.CheckFailed -> stringResource(R.string.update_check_failed, update.detail.take(40)) to Palette.Warning
        else -> "" to Palette.TextMuted
    }
    Row(Modifier.fillMaxWidth().padding(start = 52.dp, end = 52.dp, top = 12.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.version_footer, BuildConfig.VERSION_NAME), color = Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (status.isNotEmpty()) Text(status, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private fun parseColor(hex: String?): Color? = hex?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}
