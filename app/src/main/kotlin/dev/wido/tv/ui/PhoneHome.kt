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
    var message by remember { mutableStateOf<String?>(null) }

    // Un QR code d'appairage scanné : direction l'onglet TV, qui s'en occupe.
    androidx.compose.runtime.LaunchedEffect(pairTarget) { if (pairTarget != null) tab = PhoneTab.Tv }

    val screen = peers.firstOrNull { it.id == selectedId } ?: peers.firstOrNull()
    val screenState = screen?.let { states[it.id] }
    val tvOnline = screenState?.online == true
    val sentTo = stringResource(R.string.toast_sent_to_tv, screen?.name.orEmpty())
    val unreachable = stringResource(R.string.toast_tv_unreachable, screen?.name.orEmpty())

    /** « Sur la TV » depuis l'accueil ou Reprendre. */
    fun sendToTv(play: HandoffPlay) {
        val peer = screen ?: return
        scope.launch { message = if (link.play(peer, play)) sentTo else unreachable }
    }

    val light by animateColorAsState(if (tab == PhoneTab.Tv) Palette.Purple else Palette.Accent, label = "phoneLight")
    Box(Modifier.fillMaxSize().halo(listOf(Glow(light, 0.1f, 0.0f, 0.9f, 0.45f), Glow(Palette.Violet, 1.0f, 0.75f, 0.8f, 0.4f)))) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    PhoneTab.Home -> HomeTab(
                        sites = sites,
                        watching = watching,
                        pins = pins,
                        nowOnTv = screenState?.status?.playing?.takeIf { tvOnline },
                        tvName = screen?.name,
                        loading = state.loading && sites.isEmpty(),
                        update = { UpdateBanner(update, vm::install, horizontal = 0.dp) },
                        onOpen = onOpen,
                        onOptions = { actionsFor = it },
                        onAdd = { showCatalog = true },
                        onOpenPin = onOpenPin,
                        onOpenWatch = onOpenWatch,
                        onShowTv = { tab = PhoneTab.Tv },
                        onRefresh = vm::refresh,
                    )
                    PhoneTab.Continue -> ContinueTab(
                        entries = watching,
                        siteNames = sites.associate { it.site.id to it.site.name },
                        tvName = screen?.name?.takeIf { tvOnline },
                        onOpen = onOpenWatch,
                        onRemove = { vm.removeWatch(it.url) },
                        onSendToTv = { sendToTv(HandoffPlay(it.url, it.siteId, it.title, it.position)) },
                    )
                    PhoneTab.Tv -> PhoneTvTab(link, onPlayHere, pairTarget, onPairTargetHandled)
                    PhoneTab.Settings -> Column(Modifier.fillMaxSize()) {
                        PhoneTitle(stringResource(R.string.action_settings), Modifier.padding(start = 18.dp, top = 16.dp))
                        SettingsPane(app.settings, lists, vm::updateLists, Modifier.weight(1f))
                    }
                }
                message?.let { text ->
                    Snack(text, Modifier.align(Alignment.BottomCenter)) { message = null }
                }
            }
            BottomBar(tab, tvOnline) { tab = it }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeTab(
    sites: List<MenuSite>,
    watching: List<WatchEntry>,
    pins: List<Pin>,
    nowOnTv: NowPlaying?,
    tvName: String?,
    loading: Boolean,
    update: @Composable () -> Unit,
    onOpen: (Site) -> Unit,
    onOptions: (Site) -> Unit,
    onAdd: () -> Unit,
    onOpenPin: (Pin) -> Unit,
    onOpenWatch: (WatchEntry) -> Unit,
    onShowTv: () -> Unit,
    onRefresh: () -> Unit,
) {
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
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
                RoundButton("↻", onRefresh)
            }
        }
        item(span = full) { update() }
        if (nowOnTv != null && tvName != null) {
            item(span = full) { NowOnTvStrip(nowOnTv, tvName, onShowTv) }
        }
        if (watching.isNotEmpty()) {
            item(span = full) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.continue_title))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(watching, key = { it.url }) { entry ->
                            WatchTile(entry, sites.firstOrNull { it.site.id == entry.siteId }?.site?.name.orEmpty(), Modifier.width(220.dp)) { onOpenWatch(entry) }
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
                        pins.forEach { pin -> Chip("★  ${pin.title}", { onOpenPin(pin) }) }
                    }
                }
            }
        }
        item(span = full) { SectionLabel(stringResource(R.string.tab_sites)) }
        if (loading) {
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

/** Carte d'un site : toujours en couleur (pas de focus au doigt), logo en haut, nom en bas. Appui long : options. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneSiteCard(entry: MenuSite, onClick: () -> Unit, onOptions: () -> Unit) {
    val site = entry.site
    val context = LocalContext.current
    val app = context.applicationContext as WiDoApp
    val icon by produceState<SiteIcon?>(null, site.id, site.url, site.icon) { value = app.favicons.load(site) }
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
            Text("+", fontSize = 34.sp, fontWeight = FontWeight.Light)
            Text(stringResource(R.string.add_site), color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** « En cours sur Salon » : rappel en haut de l'accueil, touché il mène à l'onglet TV. */
@Composable
private fun NowOnTvStrip(now: NowPlaying, tvName: String, onClick: () -> Unit) {
    val image = rememberThumbnail(now.image)
    Row(
        Modifier
            .fillMaxWidth()
            .glassPanel(Palette.Glass, RowShape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(image, Modifier.size(72.dp, 42.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.now_on_tv, tvName), color = Palette.Ok, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
            Text(now.title.ifEmpty { now.siteName }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", fontSize = 26.sp, color = Palette.TextSecondary)
    }
}

// --- Reprendre -------------------------------------------------------------------------------

@Composable
private fun ContinueTab(
    entries: List<WatchEntry>,
    siteNames: Map<String, String>,
    tvName: String?,
    onOpen: (WatchEntry) -> Unit,
    onRemove: (WatchEntry) -> Unit,
    onSendToTv: (WatchEntry) -> Unit,
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .glassPanel(Palette.GlassFaint, RowShape)
                    .clickable { onOpen(entry) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(128.dp, 72.dp).clip(RoundedCornerShape(10.dp))) {
                    Thumb(rememberThumbnail(entry.image), Modifier.matchParentSize())
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(entry.progress).fillMaxHeight().background(Color.White))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(siteNames[entry.siteId], remaining(entry)).joinToString(" · "),
                        color = Palette.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        if (tvName != null) SmallPill(stringResource(R.string.action_on_tv_short), primary = true) { onSendToTv(entry) }
                        SmallPill("✕") { onRemove(entry) }
                    }
                }
            }
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
    val image by produceState<ImageBitmap?>(null, url) {
        value = url?.let { (context.applicationContext as WiDoApp).thumbnails.load(it) }
    }
    return image
}

@Composable
internal fun Thumb(image: ImageBitmap?, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Glass), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text("▶", color = Palette.TextMuted, fontSize = 18.sp)
        }
    }
}

@Composable
internal fun RoundButton(label: String, onClick: () -> Unit, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Palette.Glass).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
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
internal fun Snack(text: String, modifier: Modifier = Modifier, onDone: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(text) {
        kotlinx.coroutines.delay(3_000)
        onDone()
    }
    Box(
        modifier
            .padding(16.dp)
            .glassPanel(Palette.PanelSolid, RowShape, elevation = 12.dp)
            .clickable(onClick = onDone)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

/** Barre d'onglets du bas : quatre icônes dessinées et leur nom ; l'onglet actif en blanc. */
@Composable
private fun BottomBar(tab: PhoneTab, tvOnline: Boolean, onTab: (PhoneTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Background.copy(alpha = 0.92f))
            .border(width = 0.5.dp, color = Palette.GlassBorder, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        PhoneTab.entries.forEach { t ->
            val selected = t == tab
            val color = if (selected) Color.White else Color.White.copy(alpha = 0.5f)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTab(t) }
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
@Composable
internal fun WatchTile(entry: WatchEntry, siteName: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val image = rememberThumbnail(entry.image)
    Box(modifier.aspectRatio(16f / 9f).clip(CardShape).background(Palette.Glass).clickable(onClick = onClick)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
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
