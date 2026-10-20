package dev.wido.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.Catalog
import dev.wido.core.HandoffPlay
import dev.wido.core.Hosts
import dev.wido.core.LinkProtocol
import dev.wido.core.NowPlaying
import dev.wido.core.Pin
import dev.wido.core.Site
import dev.wido.core.WatchEntry
import dev.wido.tv.BuildConfig
import dev.wido.tv.MenuSite
import dev.wido.tv.MenuViewModel
import dev.wido.tv.R
import dev.wido.tv.ScreenLink
import dev.wido.tv.SiteIcon
import dev.wido.tv.WiDoApp
import kotlinx.coroutines.launch

private enum class PhoneTab { Home, Continue, Tv, Settings }

/** Message bref en bas de l'écran, avec une action d'annulation éventuelle. */
private data class PhoneMessage(val text: String, val undo: (() -> Unit)? = null)

/** Ce qu'un appui long a désigné : une vidéo de Reprendre ou une page épinglée. */
internal sealed interface LongPressed {
    data class Watch(val entry: WatchEntry) : LongPressed
    data class Pinned(val pin: Pin) : LongPressed
}

private sealed interface PhoneEditor {
    data object Add : PhoneEditor
    data class Edit(val site: Site) : PhoneEditor
}

/**
 * L'accueil sur téléphone : la même appli, pensée pour le doigt. Quatre onglets en bas — Accueil
 * (grille des sites, Reprendre, épingles), Reprendre (liste complète), TV (ce qui passe sur la TV,
 * télécommande, écrans liés) et Réglages.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneHome(
    vm: MenuViewModel,
    link: ScreenLink,
    onOpen: (Site) -> Unit,
    onOpenPin: (Pin) -> Unit,
    onOpenWatch: (WatchEntry) -> Unit,
    onPlayHere: (NowPlaying) -> Unit,
    pairTarget: LinkProtocol.PairTarget?,
    onPairTargetHandled: () -> Unit,
    /** Une vidéo vient de partir sur la TV (depuis le lecteur) : afficher l'onglet TV. */
    showTv: Boolean = false,
    onShowTvHandled: () -> Unit = {},
    editSiteId: String? = null,
    onEditHandled: () -> Unit = {},
) {
    val state by vm.config.collectAsState()
    val update by vm.update.collectAsState()
    val sites by vm.sites.collectAsState()
    val lists by vm.lists.collectAsState()
    val pins by vm.pins.collectAsState()
    val hides by vm.hides.collectAsState()
    val watching by vm.watching.collectAsState()
    val peers by link.peers.collectAsState()
    val states by link.states.collectAsState()
    val selectedId by link.selected.collectAsState()
    val app = LocalContext.current.applicationContext as WiDoApp
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(PhoneTab.Home) }
    var editor by remember { mutableStateOf<PhoneEditor?>(null) }
    var actionsFor by remember { mutableStateOf<Site?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<PhoneMessage?>(null) }
    var pressed by remember { mutableStateOf<LongPressed?>(null) }

    // Un QR code d'appairage scanné : direction l'onglet TV, qui s'en occupe.
    androidx.compose.runtime.LaunchedEffect(pairTarget) { if (pairTarget != null) tab = PhoneTab.Tv }
    androidx.compose.runtime.LaunchedEffect(editSiteId, sites) {
        val id = editSiteId ?: return@LaunchedEffect
        val site = sites.firstOrNull { it.site.id == id }?.site ?: return@LaunchedEffect
        tab = PhoneTab.Home
        editor = PhoneEditor.Edit(site)
        onEditHandled()
    }
    androidx.compose.runtime.LaunchedEffect(showTv) {
        if (showTv) {
            tab = PhoneTab.Tv
            onShowTvHandled()
        }
    }

    val screen = peers.firstOrNull { it.id == selectedId } ?: peers.firstOrNull()
    val screenState = screen?.let { states[it.id] }
    val tvOnline = screenState?.online == true
    val unreachable = stringResource(R.string.toast_tv_unreachable, screen?.name.orEmpty())

    /** « Sur la TV » depuis l'accueil ou Reprendre. */
    fun sendToTv(play: HandoffPlay) {
        val peer = screen ?: return
        scope.launch {
            // Envoyée : l'onglet TV s'affiche pour la piloter. Sinon, le message dit pourquoi.
            if (link.play(peer, play)) tab = PhoneTab.Tv else message = PhoneMessage(unreachable)
        }
    }

    val removedText = stringResource(R.string.msg_removed)
    /** Retirer de Reprendre, avec « Annuler » quelques secondes. */
    fun removeWatch(entry: WatchEntry) {
        vm.removeWatch(entry.url)
        message = PhoneMessage(removedText) { vm.restoreWatch(entry) }
    }
    val siteMap = remember(sites) { sites.associate { it.site.id to it.site } }
    val nowOnTv = screenState?.status?.playing?.takeIf { tvOnline }

    val light by animateColorAsState(if (tab == PhoneTab.Tv) Palette.Purple else Palette.Accent, label = "phoneLight")
    BoxWithConstraints(Modifier.fillMaxSize().halo(listOf(Glow(light, 0.1f, 0.0f, 0.9f, 0.45f), Glow(Palette.Violet, 1.0f, 0.75f, 0.8f, 0.4f)))) {
        // Tablette ou téléphone en paysage large : onglets sur le côté, et la TV à droite de l'accueil.
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) SideRail(tab, tvOnline) { tab = it }
            Column(Modifier.weight(1f)) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        PhoneTab.Home -> Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1.5f)) {
                                HomeTab(
                                    sites = sites,
                                    watching = watching,
                                    pins = pins,
                                    loading = state.loading,
                                    update = { UpdateBanner(update, vm::install, horizontal = 0.dp) },
                                    onOpen = onOpen,
                                    onOptions = { actionsFor = it },
                                    onAdd = { showCatalog = true },
                                    onOpenPin = onOpenPin,
                                    onOpenWatch = onOpenWatch,
                                    onLongPress = { pressed = it },
                                    onRefresh = vm::refresh,
                                )
                            }
                            if (wide) Box(Modifier.weight(1f)) { TvSidePane(link, onPlayHere) { tab = PhoneTab.Tv } }
                        }
                        PhoneTab.Continue -> ContinueTab(
                            entries = watching,
                            siteMap = siteMap,
                            tvName = screen?.name?.takeIf { tvOnline },
                            onOpen = onOpenWatch,
                            onRemove = ::removeWatch,
                            onSendToTv = { sendToTv(HandoffPlay(it.url, it.siteId, it.title, it.position)) },
                            onLongPress = { pressed = LongPressed.Watch(it) },
                        )
                        PhoneTab.Tv -> PhoneTvTab(link, onPlayHere, pairTarget, onPairTargetHandled)
                        PhoneTab.Settings -> Column(Modifier.fillMaxSize()) {
                            PhoneTitle(stringResource(R.string.action_settings), Modifier.padding(start = 18.dp, top = 16.dp))
                            SettingsPane(app.settings, lists, vm::updateLists, Modifier.weight(1f))
                        }
                    }
                    message?.let { m ->
                        Snack(m.text, Modifier.align(Alignment.BottomCenter), actionLabel = if (m.undo != null) stringResource(R.string.msg_undo) else null, onAction = m.undo) { message = null }
                    }
                }
                // Mini-lecteur : ce que lit la TV, dans tous les onglets (sauf là où la TV est déjà affichée).
                if (nowOnTv != null && screen != null && tab != PhoneTab.Tv && !(wide && tab == PhoneTab.Home)) {
                    MiniPlayer(nowOnTv, screenState.receivedAt, screen.name, onToggle = { link.key(dev.wido.core.RemoteKey.PLAY_PAUSE) }) { tab = PhoneTab.Tv }
                }
                if (!wide) BottomBar(tab, tvOnline) { tab = it }
            }
        }
    }

    pressed?.let { target ->
        LongPressMenu(
            target = target,
            siteMap = siteMap,
            tvName = screen?.name?.takeIf { tvOnline },
            onDismiss = { pressed = null },
            onOpenWatch = { pressed = null; onOpenWatch(it) },
            onOpenPin = { pressed = null; onOpenPin(it) },
            onOpenSite = { site -> pressed = null; onOpen(site) },
            onSendToTv = { play -> pressed = null; sendToTv(play) },
            onRemoveWatch = { pressed = null; removeWatch(it) },
            onRemovePin = { pressed = null; vm.removePin(it) },
        )
    }

    if (showCatalog) {
        CatalogDialog(
            entries = remember(sites) { Catalog.available(sites.map { it.site.url }) },
            onPick = { entry -> vm.addFromCatalog(entry).also { if (it == null) showCatalog = false } },
            onOther = {
                showCatalog = false
                editor = PhoneEditor.Add
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
            onMove = null,
            onEdit = { actionsFor = null; editor = PhoneEditor.Edit(site) },
            onDelete = { vm.removeSite(site.id); actionsFor = null },
            onRemovePin = vm::removePin,
            hiddenCount = hides[site.id]?.size ?: 0,
            onClearHides = { vm.clearHides(site.id) },
            onDismiss = { actionsFor = null },
            blockedThisMonth = remember(site.id) { app.settings.blockedThisMonth(site.id) },
            onOpenOnTv = if (tvOnline) ({ actionsFor = null; sendToTv(HandoffPlay(site.url, site.id, site.name)) }) else null,
            tvName = screen?.name,
        )
    }

    editor?.let { current ->
        val initial = (current as? PhoneEditor.Edit)?.site
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
            defaultNavigation = app.settings.newSitesNavigation,
        )
    }
}

// --- Accueil ---------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTab(
    sites: List<MenuSite>,
    watching: List<WatchEntry>,
    pins: List<Pin>,
    loading: Boolean,
    update: @Composable () -> Unit,
    onOpen: (Site) -> Unit,
    onOptions: (Site) -> Unit,
    onAdd: () -> Unit,
    onOpenPin: (Pin) -> Unit,
    onOpenWatch: (WatchEntry) -> Unit,
    onLongPress: (LongPressed) -> Unit,
    onRefresh: () -> Unit,
) {
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    // Tirer vers le bas : configuration, listes et état de la TV se mettent à jour.
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = full) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("WiDo", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, modifier = Modifier.weight(1f))
                RoundButton(Glyph.Refresh, onRefresh)
            }
        }
        item(span = full) { update() }
        if (watching.isNotEmpty()) {
            item(span = full) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.continue_title))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(watching, key = { it.url }) { entry ->
                            WatchTile(entry, sites.firstOrNull { it.site.id == entry.siteId }?.site, Modifier.width(220.dp), onLongClick = { onLongPress(LongPressed.Watch(entry)) }) { onOpenWatch(entry) }
                        }
                    }
                }
            }
        }
        if (pins.isNotEmpty()) {
            item(span = full) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.pins_title))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pins.forEach { pin -> PressChip(pin.title, Glyph.Star, { onOpenPin(pin) }) { onLongPress(LongPressed.Pinned(pin)) } }
                    }
                }
            }
        }
        item(span = full) { SectionLabel(stringResource(R.string.tab_sites)) }
        if (loading && sites.isEmpty()) {
            item(span = full) { Text(stringResource(R.string.status_loading), color = Palette.TextSecondary, fontSize = 14.sp) }
        }
        items(sites, key = { it.site.id }) { entry -> PhoneSiteCard(entry, { onOpen(entry.site) }, { onOptions(entry.site) }) }
        item(key = "add") { PhoneAddCard(onAdd) }
        item(span = full) {
            Text(
                stringResource(R.string.version_footer, BuildConfig.VERSION_NAME),
                color = Palette.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    }
}

/** Carte d'un site : toujours en couleur (pas de focus au doigt), logo en haut, nom en bas. Appui long : options. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneSiteCard(entry: MenuSite, onClick: () -> Unit, onOptions: () -> Unit) {
    val site = entry.site
    val context = LocalContext.current
    val app = context.applicationContext as WiDoApp
    val icon = rememberLoaded(site.id, site.url, site.icon) { app.favicons.load(site) }
    val cached = remember(site.id, site.url, site.icon) { app.favicons.cachedColor(site) }
    val accent = parseColor(site.color) ?: icon?.color ?: cached ?: Palette.Accent
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.45f)
            .clip(CardShape)
            .background(siteGradient(accent))
            .background(GlassSheen)
            .combinedClickable(onClick = onClick, onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOptions()
            })
            .padding(14.dp),
    ) {
        SiteLogo(icon, site.name, 34.dp, focused = true, modifier = Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(site.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val host = Hosts.of(site.url).orEmpty().removePrefix("www.")
            Text(
                if (entry.local) "$host · ${stringResource(R.string.tag_local)}" else host,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PhoneAddCard(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.45f)
            .clip(CardShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.5.dp, Color.White.copy(alpha = 0.2f), CardShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphIcon(Glyph.Plus, Color.White, 30.dp)
            Text(stringResource(R.string.add_site), color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Mini-lecteur au-dessus des onglets : la TV lit une vidéo. Image, titre, « Salon », lecture/pause et
 * un fin trait d'avancement ; le toucher ouvre l'onglet TV.
 */
@Composable
internal fun MiniPlayer(now: NowPlaying, receivedAt: Long, tvName: String, onToggle: () -> Unit, onOpen: () -> Unit) {
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(now) {
        while (true) {
            tick = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }
    val progress = if (now.duration > 0) (now.positionAfter(tick - receivedAt) / now.duration).toFloat().coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .glassPanel(Palette.PanelSolid, RowShape, elevation = 10.dp)
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VideoArt(rememberThumbnail(now.image), null, now.url, Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(8.dp)), badge = 0.dp)
            Column(Modifier.weight(1f)) {
                Text(now.title.ifEmpty { now.siteName.ifEmpty { now.url } }, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    GlyphIcon(Glyph.Tv, Palette.Ok, 13.dp)
                    Text(tvName, color = Palette.Ok, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White).describedAs(glyphLabel(Glyph.Play)).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                GlyphIcon(if (now.playing) Glyph.Pause else Glyph.Play, Palette.Ink, 20.dp)
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
        }
        Box(Modifier.height(6.dp))
    }
}

/** Menu de l'appui long sur une vidéo ou une page épinglée. */
@Composable
private fun LongPressMenu(
    target: LongPressed,
    siteMap: Map<String, Site>,
    tvName: String?,
    onDismiss: () -> Unit,
    onOpenWatch: (WatchEntry) -> Unit,
    onOpenPin: (Pin) -> Unit,
    onOpenSite: (Site) -> Unit,
    onSendToTv: (HandoffPlay) -> Unit,
    onRemoveWatch: (WatchEntry) -> Unit,
    onRemovePin: (Pin) -> Unit,
) {
    val title = when (target) {
        is LongPressed.Watch -> target.entry.title
        is LongPressed.Pinned -> target.pin.title
    }
    DialogFrame(title = title, onDismiss = onDismiss, width = 520.dp) {
        when (target) {
            is LongPressed.Watch -> {
                val e = target.entry
                ActionButton(stringResource(R.string.action_resume), { onOpenWatch(e) }, Modifier.fillMaxWidth(), glyph = Glyph.Play)
                if (tvName != null) ActionButton(stringResource(R.string.panel_send_to_tv, tvName), { onSendToTv(HandoffPlay(e.url, e.siteId, e.title, e.position)) }, Modifier.fillMaxWidth(), primary = false, glyph = Glyph.Tv)
                siteMap[e.siteId]?.let { site -> ActionButton(stringResource(R.string.action_open_site, site.name), { onOpenSite(site) }, Modifier.fillMaxWidth(), primary = false, glyph = Glyph.Globe) }
                ActionButton(stringResource(R.string.action_remove), { onRemoveWatch(e) }, Modifier.fillMaxWidth(), primary = false, glyph = Glyph.Close)
            }
            is LongPressed.Pinned -> {
                val p = target.pin
                ActionButton(stringResource(R.string.action_open), { onOpenPin(p) }, Modifier.fillMaxWidth(), glyph = Glyph.Play)
                if (tvName != null) ActionButton(stringResource(R.string.panel_send_to_tv, tvName), { onSendToTv(HandoffPlay(p.url, p.siteId, p.title)) }, Modifier.fillMaxWidth(), primary = false, glyph = Glyph.Tv)
                ActionButton(stringResource(R.string.action_remove), { onRemovePin(p) }, Modifier.fillMaxWidth(), primary = false, glyph = Glyph.Close)
            }
        }
    }
}

/** Pastille avec icône : toucher ouvre, appui long propose les actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PressChip(label: String, glyph: Glyph, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.10f))
            .combinedClickable(onClick = onClick, onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            })
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        GlyphIcon(glyph, Color.White.copy(alpha = 0.75f), 14.dp)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Onglets sur le côté (tablette). */
@Composable
private fun SideRail(tab: PhoneTab, tvOnline: Boolean, onTab: (PhoneTab) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(92.dp).background(Palette.Background.copy(alpha = 0.6f)).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("WiDo", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = Modifier.padding(bottom = 14.dp))
        PhoneTab.entries.forEach { t -> TabButton(t, t == tab, tvOnline, Modifier.width(76.dp)) { onTab(t) } }
    }
}

// --- Reprendre -------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ContinueTab(
    entries: List<WatchEntry>,
    siteMap: Map<String, Site>,
    tvName: String?,
    onOpen: (WatchEntry) -> Unit,
    onRemove: (WatchEntry) -> Unit,
    onSendToTv: (WatchEntry) -> Unit,
    onLongPress: (WatchEntry) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { PhoneTitle(stringResource(R.string.continue_title)) }
        if (entries.isEmpty()) {
            item { Text(stringResource(R.string.continue_empty), color = Palette.TextSecondary, fontSize = 15.sp) }
        }
        items(entries, key = { it.url }) { entry ->
            // Glisser vers la gauche retire la vidéo (avec « Annuler »).
            val dismiss = androidx.compose.material3.rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) onRemove(entry)
                    value == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
                },
            )
            val haptic = LocalHapticFeedback.current
            androidx.compose.material3.SwipeToDismissBox(
                state = dismiss,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        Modifier.fillMaxSize().clip(RowShape).background(Color(0x66E5484D)).padding(end = 24.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) { GlyphIcon(Glyph.Close, Color.White, 24.dp) }
                },
            ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .glassPanel(Palette.PanelSolid, RowShape)
                    .combinedClickable(onClick = { onOpen(entry) }, onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress(entry)
                    })
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(128.dp, 72.dp).clip(RoundedCornerShape(10.dp))) {
                    VideoArt(rememberWatchImage(entry), siteMap[entry.siteId], entry.url, Modifier.matchParentSize(), badge = 18.dp)
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(entry.progress).fillMaxHeight().background(Color.White))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(siteMap[entry.siteId]?.name ?: Hosts.of(entry.url)?.removePrefix("www."), remaining(entry)).joinToString(" · "),
                        color = Palette.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    if (tvName != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            SmallPill(stringResource(R.string.action_on_tv_short), primary = true) { onSendToTv(entry) }
                        }
                    }
                }
            }
            }
        }
        if (entries.isNotEmpty()) {
            item { Text(stringResource(R.string.continue_hint), color = Palette.TextMuted, fontSize = 12.sp) }
        }
    }
}

private fun remaining(entry: WatchEntry): String? {
    if (entry.duration <= 0) return null
    val minutes = ((entry.duration - entry.position) / 60).toInt().coerceAtLeast(1)
    return "$minutes min"
}

// --- Éléments communs ------------------------------------------------------------------------

@Composable
internal fun PhoneTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, modifier = modifier)
}

@Composable
internal fun rememberThumbnail(url: String?): ImageBitmap? {
    val context = LocalContext.current
    return rememberLoaded(url) { url?.let { (context.applicationContext as WiDoApp).thumbnails.load(it) } }
}

/**
 * L'image d'une vidéo en cours : celle capturée là où on l'a quittée, sinon celle de la page. Elle
 * change dès qu'une image plus récente est capturée.
 */
@Composable
internal fun rememberWatchImage(entry: WatchEntry): ImageBitmap? {
    val app = LocalContext.current.applicationContext as WiDoApp
    var image by remember(entry.url) { mutableStateOf<ImageBitmap?>(null) }
    androidx.compose.runtime.LaunchedEffect(entry.url, entry.updatedAt, entry.image) {
        app.thumbnails.forVideo(entry.url, entry.image)?.let { image = it }
    }
    return image
}

/**
 * Vignette d'une vidéo : l'image, avec le logo du site en petit badge ; sans image, la couleur du site
 * et son logo en grand. On reconnaît toujours ce que c'était.
 */
@Composable
internal fun VideoArt(image: ImageBitmap?, site: Site?, url: String, modifier: Modifier, badge: Dp = 22.dp) {
    val app = LocalContext.current.applicationContext as WiDoApp
    val shown = site ?: remember(url) {
        Hosts.of(url)?.let { host -> Site(id = dev.wido.tv.ConfigRepository.LINK_SITE_ID, name = host.removePrefix("www."), url = "https://$host/") }
    }
    val icon = rememberLoaded(shown?.id, shown?.url) { shown?.let { app.favicons.load(it) } }
    val accent = parseColor(shown?.color) ?: icon?.color ?: shown?.let { app.favicons.cachedColor(it) } ?: Palette.Accent
    Box(modifier.then(if (image == null) Modifier.background(siteGradient(accent)) else Modifier.background(Palette.Glass))) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            if (badge > 0.dp && shown != null) {
                SiteLogo(icon, shown.name, badge, focused = true, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
        } else if (shown != null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                SiteLogo(icon, shown.name, if (badge > 0.dp) badge * 1.7f else 26.dp, focused = true)
            }
        }
    }
}

@Composable
internal fun RoundButton(glyph: Glyph, onClick: () -> Unit, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Palette.Glass).describedAs(glyphLabel(glyph)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, Color.White, size * 0.5f) }
}

@Composable
internal fun SmallPill(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(PillShape)
            .background(if (primary) Color.White else Palette.Glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (primary) Palette.Ink else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Message bref en bas de l'écran ; touché, il disparaît (sinon au bout de 3 s). */
@Composable
internal fun Snack(text: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null, onDone: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(text, onAction) {
        kotlinx.coroutines.delay(if (onAction != null) 5_000 else 3_000)
        onDone()
    }
    Row(
        modifier
            .padding(16.dp)
            .glassPanel(Palette.PanelSolid, RowShape, elevation = 12.dp)
            .clickable(onClick = onDone)
            .padding(start = 18.dp, end = if (actionLabel != null) 8.dp else 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
        if (actionLabel != null && onAction != null) {
            SmallPill(actionLabel, primary = true) {
                onAction()
                onDone()
            }
        }
    }
}

/** Barre d'onglets du bas : quatre icônes dessinées et leur nom ; l'onglet actif en blanc. */
@Composable
private fun BottomBar(tab: PhoneTab, tvOnline: Boolean, onTab: (PhoneTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Background.copy(alpha = 0.92f))
            .border(width = 0.5.dp, color = Palette.GlassBorder, shape = RoundedCornerShape(0.dp))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        PhoneTab.entries.forEach { t -> TabButton(t, t == tab, tvOnline, Modifier.weight(1f)) { onTab(t) } }
    }
}

/** Un onglet : icône dessinée et nom ; l'onglet actif en blanc, un point vert si la TV répond. */
@Composable
private fun TabButton(t: PhoneTab, selected: Boolean, tvOnline: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) Color.White else Color.White.copy(alpha = 0.5f)
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box {
            TabIcon(t, color)
            if (t == PhoneTab.Tv && tvOnline) {
                Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(Palette.Ok))
            }
        }
        Text(
            stringResource(
                when (t) {
                    PhoneTab.Home -> R.string.tab_home
                    PhoneTab.Continue -> R.string.continue_title
                    PhoneTab.Tv -> R.string.tab_tv
                    PhoneTab.Settings -> R.string.action_settings
                },
            ),
            color = color,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TabIcon(tab: PhoneTab, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
        when (tab) {
            PhoneTab.Home -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.15f, h * 0.48f)
                    lineTo(w * 0.5f, h * 0.16f)
                    lineTo(w * 0.85f, h * 0.48f)
                    moveTo(w * 0.26f, h * 0.4f)
                    lineTo(w * 0.26f, h * 0.84f)
                    lineTo(w * 0.74f, h * 0.84f)
                    lineTo(w * 0.74f, h * 0.4f)
                }
                drawPath(path, color, style = stroke)
            }
            PhoneTab.Continue -> {
                drawCircle(color, radius = w * 0.38f, style = stroke)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.42f, h * 0.34f)
                    lineTo(w * 0.68f, h * 0.5f)
                    lineTo(w * 0.42f, h * 0.66f)
                    close()
                }
                drawPath(path, color)
            }
            PhoneTab.Tv -> {
                drawRoundRect(color, Offset(w * 0.1f, h * 0.2f), Size(w * 0.8f, h * 0.52f), CornerRadius(w * 0.08f), style = stroke)
                drawLine(color, Offset(w * 0.34f, h * 0.86f), Offset(w * 0.66f, h * 0.86f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            PhoneTab.Settings -> {
                listOf(0.28f, 0.5f, 0.72f).forEachIndexed { i, y ->
                    drawLine(color, Offset(w * 0.14f, h * y), Offset(w * 0.86f, h * y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    val x = listOf(0.66f, 0.34f, 0.58f)[i]
                    drawCircle(Palette.Background, radius = w * 0.1f, center = Offset(w * x, h * y))
                    drawCircle(color, radius = w * 0.1f, center = Offset(w * x, h * y), style = Stroke(width = w * 0.07f))
                }
            }
        }
    }
}

/** Vignette d'une vidéo en cours (image, titre, site, avancement). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WatchTile(entry: WatchEntry, site: Site?, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val siteName = site?.name ?: Hosts.of(entry.url)?.removePrefix("www.").orEmpty()
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.aspectRatio(16f / 9f).clip(CardShape).background(Palette.Glass).combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick?.let { l -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); l() } },
        ),
    ) {
        VideoArt(rememberWatchImage(entry), site, entry.url, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
            Text(entry.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (siteName.isNotEmpty()) Text(siteName, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1)
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(entry.progress).height(3.dp).background(Color.White))
        }
    }
}
