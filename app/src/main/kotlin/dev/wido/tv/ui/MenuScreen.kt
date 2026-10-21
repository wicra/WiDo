package dev.wido.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import dev.wido.tv.UiSound
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.Catalog
import dev.wido.core.Hosts
import dev.wido.core.Pin
import dev.wido.core.WatchEntry
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

private enum class Tab { Sites, Continue, Settings }

/** Voile qui assombrit le bas de l'écran d'accueil, sous la rangée de cartes. */
private val MenuVeil = Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color(0xD908080E))

/**
 * Écran d'accueil : le logo, les onglets Sites / Reprendre / Réglages et l'état en haut. L'onglet Sites
 * montre en grand le site sélectionné (son nom, Ouvrir, Options) au-dessus d'un bac de disques ; le fond
 * prend la lumière du disque sélectionné. Reprendre : toutes les vidéos en cours, en grandes vignettes.
 * Retour, depuis Reprendre ou Réglages, ramène aux sites.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MenuScreen(
    vm: MenuViewModel,
    onOpen: (Site) -> Unit,
    onOpenPin: (Pin) -> Unit,
    onOpenWatch: (WatchEntry) -> Unit,
    /** Ouvrir aussitôt le formulaire de ce site perso (adresse qui ne répond plus). */
    editSiteId: String? = null,
    onEditHandled: () -> Unit = {},
    /** Augmente à chaque double appui sur Retour dans le lecteur : retour à l'onglet Sites. */
    sitesSignal: Int = 0,
) {
    val state by vm.config.collectAsState()
    val update by vm.update.collectAsState()
    val sites by vm.sites.collectAsState()
    val lists by vm.lists.collectAsState()
    val pins by vm.pins.collectAsState()
    val hides by vm.hides.collectAsState()
    val watching by vm.watching.collectAsState()
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
    // Un FocusRequester par disque : revenir aux sites remet le focus sur le dernier choisi.
    val cardFocus = remember { mutableMapOf<String, FocusRequester>() }
    // La vidéo de « Reprendre » sélectionnée : son image en fond d'ambiance, ses infos dans la scène.
    var focusedWatch by remember { mutableStateOf<WatchEntry?>(null) }
    var initialFocus by remember { mutableStateOf(false) }
    // Vidéo de Reprendre dont on a demandé les actions (appui long ou Menu).
    var watchActions by remember { mutableStateOf<WatchEntry?>(null) }
    // Le disque tiré du bac (dernier focalisé) : ses voisins s'écartent en vague.
    var rackIndex by remember { mutableIntStateOf(-1) }
    var refocusSites by remember { mutableIntStateOf(0) }
    // Positions gardées quand on passe par un autre onglet : on retrouve la rangée et la grille où on les a laissées.
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val sitesScroll = rememberScrollState()
    // Déplacer un site : sa place de départ, pour que Retour l'y remette.
    var moveStart by remember { mutableIntStateOf(-1) }
    val context = LocalContext.current
    fun backToSites() {
        tab = Tab.Sites
        refocusSites++
    }
    LaunchedEffect(sitesSignal) { if (sitesSignal > 0) backToSites() }
    // « Déplacer » : le focus revient sur le disque à déplacer (pas sur Ouvrir / Options), une fois le dialogue fermé.
    LaunchedEffect(movingId) {
        val id = movingId ?: return@LaunchedEffect
        delay(120)
        runCatching { cardFocus[id]?.requestFocus() }
        android.widget.Toast.makeText(context, R.string.move_hint, android.widget.Toast.LENGTH_LONG).show()
    }
    androidx.activity.compose.BackHandler(enabled = tab != Tab.Sites && editor == null && actionsFor == null && !showCatalog) {
        UiSound.back()
        backToSites()
    }
    LaunchedEffect(editSiteId, sites) {
        val id = editSiteId ?: return@LaunchedEffect
        val site = sites.firstOrNull { it.site.id == id }?.site ?: return@LaunchedEffect
        editor = Editor.Edit(site)
        onEditHandled()
    }

    // Au lancement : sur le premier site.
    LaunchedEffect(sites.isNotEmpty()) {
        if (sites.isEmpty() || initialFocus) return@LaunchedEffect
        initialFocus = true
        runCatching { firstCard.requestFocus() }
    }

    val glows = if (tab == Tab.Settings) {
        listOf(Glow(light, 0.1f, 0.1f, 0.6f, 0.7f), Glow(second, 0.95f, 0.9f, 0.6f, 0.7f))
    } else {
        listOf(Glow(light, 0.2f, 0.3f, 0.55f, 0.7f), Glow(second, 0.85f, 0.1f, 0.6f, 0.7f), Glow(Palette.Navy, 0.6f, 1.1f, 0.8f, 0.6f))
    }

    Box(Modifier.fillMaxSize().halo(glows, if (tab == Tab.Sites) MenuVeil else null)) {
        // Une vidéo de Reprendre sélectionnée : son image, floutée et assombrie, en fond d'ambiance.
        Backdrop(if (tab == Tab.Continue) focusedWatch ?: watching.firstOrNull() else null)
        Column(Modifier.fillMaxSize()) {
            TopBar(state, tab, onTab = { tab = it }, onRefresh = vm::refresh)
            when (tab) {
                // La scène en haut, les rangées en bas. Si l'écran est trop petit (bandeau de mise à jour,
                // Reprendre, épingles…), la page défile au focus au lieu d'écraser les boutons.
                Tab.Sites -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                  val viewport = maxHeight
                  // De retour sur les sites (Retour, double Retour dans le lecteur) : le focus revient au dernier disque.
                  LaunchedEffect(refocusSites) {
                      if (refocusSites == 0) return@LaunchedEffect
                      androidx.compose.runtime.withFrameNanos { }
                      runCatching { (currentId?.let { cardFocus[it] } ?: firstCard).requestFocus() }
                  }
                  Column(
                    Modifier.fillMaxWidth().verticalScroll(sitesScroll).heightIn(min = viewport),
                    verticalArrangement = Arrangement.SpaceBetween,
                  ) {
                  Column {
                    UpdateBanner(update, onInstall = vm::install)
                    val current = if (addFocused) null else sites.firstOrNull { it.site.id == currentId } ?: sites.firstOrNull()
                    Hero(
                        current,
                        watch = null,
                        onResume = {},
                        onRemoveWatch = {},
                        empty = sites.isEmpty() && !state.loading,
                        onOpen = { current?.let { onOpen(it.site) } },
                        onOptions = { current?.let { actionsFor = it.site } },
                        onAdd = { showCatalog = true },
                        pins = current?.let { c -> pins.filter { it.siteId == c.site.id } }.orEmpty(),
                        onPin = onOpenPin,
                        tight = viewport < 420.dp,
                    )
                  }
                  Column {
                    LazyRow(
                        state = rowState,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Bottom,
                        contentPadding = PaddingValues(start = 44.dp, end = 52.dp, top = 22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            // Venir d'en haut (onglets, Ouvrir) : le focus tombe sur le dernier disque choisi.
                            .focusProperties {
                                enter = {
                                    val last = currentId
                                    val visible = rowState.layoutInfo.visibleItemsInfo.any { it.key == last }
                                    if (last != null && visible) cardFocus[last] ?: FocusRequester.Default else FocusRequester.Default
                                }
                            }
                            .focusGroup(),
                    ) {
                        itemsIndexed(sites, key = { _, it -> it.site.id }) { index, entry ->
                            val focus = cardFocus.getOrPut(entry.site.id) { if (index == 0) firstCard else FocusRequester() }
                            SiteCard(
                                entry = entry,
                                index = index,
                                onClick = { onOpen(entry.site) },
                                onOptions = { actionsFor = entry.site },
                                moving = movingId == entry.site.id,
                                onMove = { delta -> vm.move(entry.site.id, delta) },
                                onMoveDone = { movingId = null },
                                onMoveCancel = {
                                    // Retour : le site retourne à sa place d'avant.
                                    val now = sites.indexOfFirst { it.site.id == entry.site.id }
                                    if (moveStart >= 0 && now >= 0 && now != moveStart) vm.move(entry.site.id, moveStart - now)
                                    movingId = null
                                },
                                onFocus = { color ->
                                    currentId = entry.site.id
                                    addFocused = false
                                    rackIndex = index
                                    siteLight = color
                                },
                                distance = if (rackIndex < 0) null else index - rackIndex,
                                modifier = Modifier.focusRequester(focus),
                            )
                        }
                        item(key = "add") {
                            AddCard(
                                index = sites.size,
                                onClick = { showCatalog = true },
                                onFocus = {
                                    addFocused = true
                                    rackIndex = sites.size
                                    siteLight = Palette.Accent
                                },
                                distance = if (rackIndex < 0) null else sites.size - rackIndex,
                            )
                        }
                    }
                    Footer(update, remember(state) { settings.blockedThisMonth() }, hint = stringResource(R.string.home_hint_tv))
                  }
                  }
                }
                Tab.Continue -> ContinuePane(
                    entries = watching,
                    sites = sites,
                    gridState = gridState,
                    onFocus = { entry ->
                        focusedWatch = entry
                        sites.firstOrNull { it.site.id == entry.siteId }?.let { m ->
                            siteLight = parseColor(m.site.color) ?: Palette.Accent
                        }
                    },
                    onOpen = onOpenWatch,
                    onRemove = { entry -> watchActions = entry },
                    modifier = Modifier.weight(1f),
                )
                Tab.Settings -> SettingsPane(settings, lists, vm::updateLists, Modifier.weight(1f))
            }
        }
    }

    watchActions?.let { entry ->
        val siteName = sites.firstOrNull { it.site.id == entry.siteId }?.site?.name ?: Hosts.of(entry.url)?.removePrefix("www.").orEmpty()
        DialogFrame(title = entry.title, onDismiss = { watchActions = null }, width = 560.dp) {
            ActionButton(stringResource(R.string.action_resume), { watchActions = null; onOpenWatch(entry) }, Modifier.fillMaxWidth(), glyph = Glyph.Play)
            ConfirmActionButton(
                stringResource(R.string.action_remove),
                stringResource(R.string.confirm_remove),
                {
                    vm.removeWatch(entry.url)
                    if (focusedWatch?.url == entry.url) focusedWatch = null
                    watchActions = null
                },
                Modifier.fillMaxWidth(),
                glyph = Glyph.Close,
            )
            sites.firstOrNull { it.site.id == entry.siteId }?.let { m ->
                ActionButton(stringResource(R.string.action_open_site, siteName), { watchActions = null; onOpen(m.site) }, Modifier.fillMaxWidth(), primary = false, glyph = Glyph.Globe)
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
            onMove = {
                actionsFor = null
                moveStart = sites.indexOfFirst { it.site.id == site.id }
                movingId = site.id
            },
            onEdit = { actionsFor = null; editor = Editor.Edit(site) },
            onDelete = { vm.removeSite(site.id); actionsFor = null },
            onRemovePin = vm::removePin,
            hiddenCount = hides[site.id]?.size ?: 0,
            onClearHides = { vm.clearHides(site.id) },
            onDismiss = { actionsFor = null },
            blockedThisMonth = remember(site.id) { settings.blockedThisMonth(site.id) },
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
            TabItem(stringResource(R.string.continue_title), tab == Tab.Continue) { onTab(Tab.Continue) }
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
internal fun UpdateBanner(update: UpdateUi, onInstall: (ReleaseInfo) -> Unit, horizontal: androidx.compose.ui.unit.Dp = 52.dp) {
    val (message, info) = when (update) {
        is UpdateUi.Available -> stringResource(R.string.update_available, update.info.tag) to update.info
        is UpdateUi.Downloading -> stringResource(R.string.update_downloading, update.percent) to null
        is UpdateUi.Failed -> stringResource(update.messageRes, update.detail.orEmpty()) to update.info
        UpdateUi.None, UpdateUi.Checking, UpdateUi.UpToDate, is UpdateUi.CheckFailed -> return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal, vertical = 8.dp)
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
internal fun Hero(
    current: MenuSite?,
    /** Vidéo de Reprendre sélectionnée : son titre, son site et « Reprendre ». */
    watch: WatchEntry?,
    onResume: () -> Unit,
    onRemoveWatch: () -> Unit,
    empty: Boolean,
    onOpen: () -> Unit,
    onOptions: () -> Unit,
    onAdd: () -> Unit,
    pins: List<Pin>,
    onPin: (Pin) -> Unit,
    compact: Boolean = false,
    /** Écran court : titre plus petit, sans adresse ni épingles (elles restent dans Options). */
    tight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(start = 52.dp, end = 52.dp, top = if (compact) 8.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        val title = watch?.title ?: current?.site?.name ?: stringResource(R.string.editor_title_add)
        val context = LocalContext.current
        val icon = rememberLoaded(current?.site?.id, current?.site?.url) { current?.site?.let { (context.applicationContext as WiDoApp).favicons.load(it) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (current != null) {
                SiteLogo(icon, current.site.name, if (compact) 44.dp else 54.dp, focused = true)
                Spacer(Modifier.width(if (compact) 14.dp else 18.dp))
            }
            Text(
                title,
                fontSize = if (watch != null || tight) 34.sp else if (compact) 42.sp else 56.sp,
                lineHeight = if (watch != null || tight) 40.sp else if (compact) 46.sp else 60.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val subtitle = when {
            watch != null -> listOfNotNull(
                current?.site?.name,
                if (watch.duration > 0) stringResource(R.string.watch_left, ((watch.duration - watch.position) / 60).toInt().coerceAtLeast(1)) else null,
            ).joinToString(" · ")
            current != null -> {
                val host = Hosts.of(current.site.url).orEmpty()
                if (current.local) "$host · ${stringResource(R.string.tag_local)}" else host
            }
            empty -> stringResource(R.string.empty_body)
            else -> ""
        }
        if (subtitle.isNotEmpty() && (!tight || watch != null)) {
            Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (watch != null) {
                ActionButton(stringResource(R.string.action_resume), onResume, glyph = Glyph.Play)
                ActionButton(stringResource(R.string.action_remove), onRemoveWatch, primary = false, idleText = Color.White.copy(alpha = 0.8f), glyph = Glyph.Close)
            } else if (current != null) {
                ActionButton(stringResource(R.string.action_open), onOpen, glyph = Glyph.Play)
                ActionButton(stringResource(R.string.action_options), onOptions, primary = false, idleText = Color.White.copy(alpha = 0.8f), glyph = Glyph.More)
            } else {
                ActionButton(stringResource(R.string.add_site), onAdd, glyph = Glyph.Plus)
            }
        }
        // Pages épinglées de ce site : ouvertes directement.
        if (pins.isNotEmpty() && watch == null && !tight) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Star, Color.White.copy(alpha = 0.55f), 16.dp)
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
 * Un site dans le bac : un vinyle, son logo sur l'étiquette (le nom et l'adresse sont dans la scène
 * au-dessus). Le disque focalisé sort du bac, grandit et tourne ; ses voisins s'inclinent en vague,
 * comme quand on feuillette des disques. Les disques arrivent en vague à l'ouverture. OK ouvre le site ;
 * la touche Menu ou l'appui long ouvrent ses options.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SiteCard(
    entry: MenuSite,
    index: Int,
    onClick: () -> Unit,
    onOptions: () -> Unit,
    moving: Boolean,
    onMove: (Int) -> Unit,
    onMoveDone: () -> Unit,
    /** Retour pendant un déplacement : annule (par défaut, comme OK). */
    onMoveCancel: () -> Unit = onMoveDone,
    onFocus: (Color) -> Unit,
    /** Écart avec le disque sélectionné (0 = lui), null si aucun : règle la vague. */
    distance: Int? = null,
    modifier: Modifier = Modifier,
) {
    val site = entry.site
    val context = LocalContext.current
    val icon = rememberLoaded(site.id, site.url, site.icon) { (context.applicationContext as WiDoApp).favicons.load(site) }
    // Couleur : celle de la config si elle en impose une, sinon celle de l'icône du site.
    val cached = remember(site.id, site.url, site.icon) { (context.applicationContext as WiDoApp).favicons.cachedColor(site) }
    val accent = parseColor(site.color) ?: icon?.color ?: cached ?: Palette.Accent
    var focused by remember { mutableStateOf(false) }

    val menuKey = Modifier.onKeyEvent { e ->
        val handled = e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU && e.nativeKeyEvent.action == KeyEvent.ACTION_UP
        if (handled) onOptions()
        handled
    }
    // Déplacement : ◀ ▶ déplacent le cercle (il garde le focus), OK ou Retour terminent.
    val moveKeys = if (moving) {
        Modifier.onPreviewKeyEvent { e ->
            val native = e.nativeKeyEvent
            val down = native.action == KeyEvent.ACTION_DOWN
            when (native.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) onMove(-1); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) onMove(1); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (!down) onMoveDone(); true }
                KeyEvent.KEYCODE_BACK -> { if (!down) onMoveCancel(); true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
                else -> false
            }
        }
    } else {
        Modifier
    }

    Column(
        modifier.then(rememberEntrance(index)).width(CIRCLE_SLOT),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Le cercle grandit dans une zone fixe : la rangée ne bouge pas au passage du focus.
        Box(Modifier.size(CIRCLE_SLOT), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(CIRCLE)
                    .then(menuKey)
                    .then(moveKeys)
                    .onFocusChanged {
                        if (it.isFocused && !focused) UiSound.focus()
                        focused = it.isFocused
                        if (it.isFocused) onFocus(accent)
                    }
                    .then(rackWave(distance ?: if (focused) 0 else null, focused))
                    // Après la vague : l'ouverture part du disque tel qu'on le voit (agrandi, soulevé).
                    .onGloballyPositioned { if (focused) LaunchOrigin.remember(it, accent) }
                    .shadow(if (focused) 18.dp else 4.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .then(if (moving) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier)
                    .then(if (moving) Modifier else Modifier.dpadLongPress(onOptions, onClick))
                    .describedAs(site.name)
                    .combinedClickable(onClick = onClick, onLongClick = onOptions),
                contentAlignment = Alignment.Center,
            ) {
                Vinyl(accent, focused)
                CircleLogo(icon, site.name, focused)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (moving) "◀  ▶" else site.name,
            color = if (focused || moving) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = if (focused || moving) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** L'étiquette du vinyle : le logo sur un disque blanc, un peu voilé au repos. */
@Composable
private fun CircleLogo(icon: SiteIcon?, name: String, focused: Boolean) {
    val disc by animateDpAsState(if (focused) 50.dp else 46.dp, label = "logoDisc")
    Box(
        Modifier.size(disc).clip(CircleShape).background(Color.White.copy(alpha = if (focused) 0.97f else 0.8f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(
                icon.image,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                modifier = Modifier.size(disc * 0.64f).graphicsLayer { alpha = if (focused) 1f else 0.92f },
            )
        } else {
            Text(name.firstOrNull()?.uppercase().orEmpty(), color = Palette.Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** « Ajouter » : un cercle en pointillé discret, blanc plein au focus. */
@Composable
internal fun AddCard(index: Int, onClick: () -> Unit, onFocus: () -> Unit, distance: Int? = null) {
    var focused by remember { mutableStateOf(false) }
    Column(rememberEntrance(index).width(CIRCLE_SLOT), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(CIRCLE_SLOT).then(rackWave(distance ?: if (focused) 0 else null, focused)), contentAlignment = Alignment.Center) {
            FocusSurface(
                onClick,
                Modifier.size(CIRCLE).border(1.5.dp, if (focused) Color.Transparent else Color.White.copy(alpha = 0.2f), CircleShape).describedAs(stringResource(R.string.add_site)),
                shape = CircleShape,
                idle = Color.White.copy(alpha = 0.06f),
                focusScale = 1f,
                onFocusChange = {
                    focused = it
                    if (it) onFocus()
                },
            ) { f ->
                GlyphIcon(Glyph.Plus, onSurface(f), 30.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.add_site),
            color = if (focused) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private val CIRCLE = 86.dp
private val CIRCLE_SLOT = 104.dp

/**
 * La vague du bac à disques : le disque choisi sort (plus grand, soulevé), ses voisins s'écartent et
 * s'inclinent de moins en moins loin. Des ressorts plutôt que des durées fixes : le mouvement reste
 * fluide quand on enchaîne les flèches (il repart de sa vitesse au lieu de redémarrer), et les voisins,
 * plus souples à mesure qu'ils sont loin, suivent avec un léger retard : l'onde se propage.
 */
@Composable
private fun rackWave(distance: Int?, focused: Boolean): Modifier {
    val d = distance ?: Int.MAX_VALUE
    val far = kotlin.math.abs(d)
    val side = if (d < 0) -1f else 1f
    val decay = when (far) {
        1 -> 1f
        2 -> 0.55f
        3 -> 0.25f
        else -> 0f
    }
    val spec = androidx.compose.animation.core.spring<Float>(
        dampingRatio = 0.72f,
        stiffness = if (far in 1..3) 420f / (1f + 0.6f * far) else 420f,
    )
    val scale by animateFloatAsState(
        when {
            d == 0 && focused -> 1.2f
            d == 0 -> 1.07f
            else -> 1f - 0.05f * decay
        },
        spec, label = "rackScale",
    )
    val lift by animateFloatAsState(if (d == 0) (if (focused) -14f else -7f) else -4f * decay, spec, label = "rackLift")
    val tilt by animateFloatAsState(if (d == 0) 0f else -side * 26f * decay, spec, label = "rackTilt")
    val shift by animateFloatAsState(if (d == 0) 0f else side * 8f * decay, spec, label = "rackShift")
    return Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationY = lift * density
        translationX = shift * density
        rotationY = tilt
        cameraDistance = 14f * density
    }
}

/**
 * Le disque : vinyle noir, sillons fins, un anneau à la couleur du site autour de l'étiquette, et deux
 * reflets. Focalisé, il tourne (les reflets défilent) et le bord s'éclaire de la couleur du site.
 * L'angle n'est lu qu'au dessin : la rotation ne recompose rien.
 */
@Composable
private fun Vinyl(accent: Color, focused: Boolean) {
    val angle = if (focused) {
        rememberInfiniteTransition(label = "vinyl").animateFloat(32f, 392f, infiniteRepeatable(tween(3_600, easing = LinearEasing)), label = "vinylSpin")
    } else {
        remember { mutableFloatStateOf(32f) }
    }
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(300), label = "vinylGlow")
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2
        // Vinyle : un noir franc, à peine plus clair au centre.
        drawCircle(Brush.radialGradient(listOf(Color(0xFF1E1E24), Color(0xFF0B0B0E)), center, r), r)
        // Sillons : des traits d'un pixel, bien contrastés (pas de flou de dégradé).
        val inner = r * 0.64f
        for (i in 0..6) {
            drawCircle(Color.White.copy(alpha = if (i % 2 == 0) 0.16f else 0.08f), inner + (r * 0.93f - inner) * i / 6f, style = Stroke(1f))
        }
        // Deux reflets nets qui défilent quand le disque tourne.
        rotate(angle.value) {
            val light = Color.White.copy(alpha = 0.14f + 0.1f * glow)
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.07f to light, 0.11f to Color.Transparent,
                    0.5f to Color.Transparent, 0.57f to light, 0.61f to Color.Transparent, 1f to Color.Transparent,
                    center = center,
                ),
                r * 0.95f,
            )
        }
        // Anneau à la couleur du site autour de l'étiquette, et bord du disque.
        drawCircle(accent, r * 0.6f, style = Stroke(2.5.dp.toPx()))
        drawCircle(lerp(Color.White.copy(alpha = 0.18f), accent, glow), r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
    }
}

/**
 * Où se trouve l'élément qui ouvre un site (cercle ou vignette sélectionnée) : la page s'agrandit
 * depuis lui à l'ouverture.
 */
object LaunchOrigin {
    @Volatile var bounds: android.graphics.Rect? = null
        private set

    /** Couleur de l'élément (celle du site), pour le cercle qui s'agrandit. */
    @Volatile var color: Int = 0xFF1E5BFF.toInt()
        private set

    /** Vrai : une vignette rectangulaire (Reprendre), dont la page s'agrandit en rectangle, pas en disque. */
    @Volatile var tile: Boolean = false
        private set

    fun remember(coordinates: androidx.compose.ui.layout.LayoutCoordinates, color: Color, tile: Boolean = false) {
        val r = coordinates.boundsInWindow()
        bounds = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
        this.color = color.toArgb()
        this.tile = tile
    }
}

/**
 * Fond d'ambiance : l'image de la vidéo sélectionnée, très adoucie (réduite puis agrandie, ce qui la
 * floute même sans effet de flou du système) et assombrie pour que le texte reste lisible.
 */
@Composable
private fun Backdrop(entry: WatchEntry?) {
    val app = LocalContext.current.applicationContext as WiDoApp
    var soft by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(entry?.url) {
        soft = entry?.let { e ->
            app.thumbnails.forVideo(e.url, e.image)?.let { image ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val src = image.asAndroidBitmap()
                    val w = 28
                    val h = (src.height * w / src.width.toFloat()).toInt().coerceAtLeast(1)
                    android.graphics.Bitmap.createScaledBitmap(src, w, h, true).asImageBitmap()
                }
            }
        }
    }
    androidx.compose.animation.Crossfade(soft, animationSpec = tween(700), label = "backdrop") { image ->
        if (image != null) {
            Box(Modifier.fillMaxSize()) {
                androidx.compose.foundation.Image(
                    image,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                    alpha = 0.6f,
                    modifier = Modifier.fillMaxSize().then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(24.dp) else Modifier),
                )
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Palette.Background.copy(alpha = 0.85f), 0.65f to Palette.Background.copy(alpha = 0.25f))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Palette.Background.copy(alpha = 0.95f))))
            }
        }
    }
}

/** Une seule ligne discrète : la version à gauche, l'état de la mise à jour à droite. */
@Composable
private fun Footer(update: UpdateUi, blocked: Int, hint: String = "") {
    val (status, color) = when (update) {
        UpdateUi.Checking -> stringResource(R.string.update_checking) to Palette.TextMuted
        UpdateUi.UpToDate -> stringResource(R.string.update_uptodate) to Palette.TextMuted
        is UpdateUi.CheckFailed -> stringResource(R.string.update_check_failed, update.detail.take(40)) to Palette.Warning
        else -> "" to Palette.TextMuted
    }
    Row(Modifier.fillMaxWidth().padding(start = 52.dp, end = 52.dp, top = 12.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.version_footer, BuildConfig.VERSION_NAME), color = Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (blocked > 0) {
            Spacer(Modifier.width(16.dp))
            GlyphIcon(Glyph.Blocked, Palette.Ok.copy(alpha = 0.8f), 12.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.stats_total, java.text.NumberFormat.getIntegerInstance().format(blocked)), color = Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (hint.isNotEmpty()) Text(hint, color = Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        if (hint.isNotEmpty() && status.isNotEmpty()) Spacer(Modifier.width(24.dp))
        if (status.isNotEmpty()) Text(status, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

internal fun parseColor(hex: String?): Color? = hex?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}

/**
 * Onglet « Reprendre », sur toute la page : un en-tête discret, puis les vidéos en cours en grandes
 * vignettes, la plus récente d'abord, avec leur titre en clair sous l'image (deux lignes), le site et le
 * temps restant. Plusieurs vidéos d'un même site ont chacune leur vignette. OK reprend ; appui long ou
 * Menu retire la vidéo.
 */
@Composable
private fun ContinuePane(
    entries: List<WatchEntry>,
    sites: List<MenuSite>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onFocus: (WatchEntry) -> Unit,
    onOpen: (WatchEntry) -> Unit,
    onRemove: (WatchEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(horizontal = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            GlyphIcon(Glyph.Play, Color.White.copy(alpha = 0.5f), 44.dp)
            Text(stringResource(R.string.continue_empty_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Text(stringResource(R.string.continue_empty_body), color = Palette.TextSecondary, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }
    val siteMap = remember(sites) { sites.associateBy { it.site.id } }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 52.dp, end = 52.dp, top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                pluralStringResource(R.plurals.continue_count, entries.size, entries.size),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.continue_hint_tv), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            state = gridState,
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 52.dp, end = 52.dp, top = 22.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(entries.size, key = { entries[it].url }) { i ->
                val entry = entries[i]
                WatchTile(entry, siteMap[entry.siteId]?.site, { onOpen(entry) }, { onRemove(entry) }, { onFocus(entry) })
            }
        }
    }
}

/** Une vidéo en cours : l'image (avancement en bas), puis le titre, le site et le temps restant. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchTile(entry: WatchEntry, site: Site?, onClick: () -> Unit, onRemove: () -> Unit, onFocus: () -> Unit) {
    val siteName = site?.name ?: Hosts.of(entry.url)?.removePrefix("www.").orEmpty()
    var focused by remember { mutableStateOf(false) }
    val left = if (entry.duration > 0) stringResource(R.string.watch_left, ((entry.duration - entry.position) / 60).toInt().coerceAtLeast(1)) else null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged {
                    if (it.isFocused && !focused) UiSound.focus()
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
                .onKeyEvent { e ->
                    val menu = e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU && e.nativeKeyEvent.action == KeyEvent.ACTION_UP
                    if (menu) onRemove()
                    menu
                }
                .liftEffect(focused, CardShape, focusScale = 1.06f, lift = 22.dp)
                .onGloballyPositioned { if (focused) LaunchOrigin.remember(it, Palette.Accent, tile = true) }
                .clip(CardShape)
                .background(Palette.Glass)
                .then(if (focused) Modifier.border(3.dp, Color.White, CardShape) else Modifier)
                .dpadLongPress(onRemove, onClick)
                .describedAs(listOfNotNull(entry.title, siteName.takeIf { it.isNotEmpty() }, left).joinToString(", "))
                .combinedClickable(onClick = onClick, onLongClick = onRemove),
        ) {
            VideoArt(rememberWatchImage(entry), site, entry.url, Modifier.matchParentSize(), badge = 30.dp)
            if (focused) {
                Box(Modifier.align(Alignment.Center).size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.Play, Palette.Ink, 26.dp)
                }
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(entry.progress).height(4.dp).background(if (focused) Color.White else Palette.Accent))
            }
        }
        Text(
            entry.title,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 21.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(siteName.takeIf { it.isNotEmpty() }, left).joinToString("  ·  "),
            color = Color.White.copy(alpha = if (focused) 0.8f else 0.55f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Logo d'un site dans une tuile arrondie : le favicon (ou l'initiale si le site n'en a pas).
 * `focused` : tuile blanche, pour que le logo ressorte sur la couleur du site ; sinon du verre.
 */
@Composable
internal fun SiteLogo(icon: SiteIcon?, name: String, size: androidx.compose.ui.unit.Dp, focused: Boolean, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(size * 0.3f)
    Box(
        modifier.size(size).clip(shape).background(if (focused) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(
                icon.image,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(size * 0.64f).graphicsLayer { alpha = if (focused) 1f else 0.85f },
            )
        } else {
            Text(
                name.firstOrNull()?.uppercase().orEmpty(),
                color = if (focused) Palette.Ink else Color.White,
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}
