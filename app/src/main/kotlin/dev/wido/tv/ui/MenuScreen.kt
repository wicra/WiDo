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
import androidx.compose.ui.focus.FocusRequester
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

private enum class Tab { Sites, Settings }

/** Voile qui assombrit le bas de l'écran d'accueil, sous la rangée de cartes. */
private val MenuVeil = Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color(0xD908080E))

/**
 * Écran d'accueil : le logo, les onglets Sites / Réglages et l'état en haut. L'onglet Sites montre en
 * grand le site sélectionné (son nom, Ouvrir, Options) au-dessus d'une rangée de cartes ; le fond prend
 * la lumière de la carte sélectionnée. L'onglet Réglages remplace la scène par la liste des réglages.
 */
@Composable
fun MenuScreen(
    vm: MenuViewModel,
    onOpen: (Site) -> Unit,
    onOpenPin: (Pin) -> Unit,
    onOpenWatch: (WatchEntry) -> Unit,
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
    val firstWatch = remember { FocusRequester() }
    // La vidéo de « Reprendre » sélectionnée : la scène la montre à la place du site.
    var focusedWatch by remember { mutableStateOf<WatchEntry?>(null) }
    var initialFocus by remember { mutableStateOf(false) }

    // Au lancement : sur la dernière vidéo en cours (OK et le film repart), sinon sur le premier site.
    LaunchedEffect(sites.isNotEmpty()) {
        if (sites.isEmpty() || initialFocus) return@LaunchedEffect
        initialFocus = true
        runCatching { if (watching.isNotEmpty()) firstWatch.requestFocus() else firstCard.requestFocus() }
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
                // La scène en haut, les rangées en bas. Si l'écran est trop petit (bandeau de mise à jour,
                // Reprendre, épingles…), la page défile au focus au lieu d'écraser les boutons.
                Tab.Sites -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                  val viewport = maxHeight
                  Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport),
                    verticalArrangement = Arrangement.SpaceBetween,
                  ) {
                  Column {
                    UpdateBanner(update, onInstall = vm::install)
                    val current = if (addFocused) null else sites.firstOrNull { it.site.id == currentId } ?: sites.firstOrNull()
                    Hero(
                        current,
                        watch = focusedWatch?.takeIf { w -> watching.any { it.url == w.url } },
                        onResume = { focusedWatch?.let(onOpenWatch) },
                        onRemoveWatch = { focusedWatch?.let { vm.removeWatch(it.url) }; focusedWatch = null },
                        empty = sites.isEmpty() && !state.loading,
                        onOpen = { current?.let { onOpen(it.site) } },
                        onOptions = { current?.let { actionsFor = it.site } },
                        onAdd = { showCatalog = true },
                        pins = current?.let { c -> pins.filter { it.siteId == c.site.id } }.orEmpty(),
                        onPin = onOpenPin,
                        // Avec la rangée « Reprendre », la scène se fait plus compacte pour tout faire tenir ;
                        // sur un écran court, encore plus (titre seul, sans adresse ni épingles) : rien ne défile sous l'en-tête.
                        compact = watching.isNotEmpty(),
                        tight = watching.isNotEmpty() && viewport < 520.dp,
                    )
                  }
                  Column {
                    if (watching.isNotEmpty()) {
                        ContinueRow(
                            entries = watching,
                            siteMap = sites.associate { it.site.id to it.site },
                            onOpen = onOpenWatch,
                            onRemove = { vm.removeWatch(it.url) },
                            onFocus = { entry ->
                                currentId = entry.siteId
                                addFocused = false
                                focusedWatch = entry
                            },
                            firstModifier = Modifier.focusRequester(firstWatch),
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Bottom,
                        contentPadding = PaddingValues(start = 44.dp, end = 52.dp, top = 12.dp),
                        modifier = Modifier.fillMaxWidth().height(if (watching.isNotEmpty()) 146.dp else 166.dp),
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
                                    focusedWatch = null
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
                                    focusedWatch = null
                                    siteLight = Palette.Accent
                                },
                            )
                        }
                    }
                    if (watching.isEmpty()) Footer(update) else Spacer(Modifier.height(14.dp))
                  }
                  }
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
 * Un site dans la rangée : un cercle avec son logo (le nom et l'adresse sont déjà dans la scène au-dessus).
 * Au repos : du verre. Avec le focus : il se soulève, passe à la couleur pleine du site avec un reflet,
 * le logo sur un disque blanc. Les cercles arrivent en vague à l'ouverture. OK ouvre le site ; la
 * touche Menu ou l'appui long ouvrent ses options.
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
    onFocus: (Color) -> Unit,
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
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> { if (!down) onMoveDone(); true }
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
                    .onGloballyPositioned { if (focused) LaunchOrigin.remember(it, accent) }
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onFocus(accent)
                    }
                    .liftEffect(focused, CircleShape, focusScale = 1.14f, lift = 24.dp)
                    .clip(CircleShape)
                    .then(if (focused) Modifier.background(siteGradient(accent)).background(GlassSheen) else Modifier.background(Palette.Glass))
                    .border(if (moving) 2.5.dp else 1.dp, if (moving) Color.White else Palette.GlassBorder, CircleShape)
                    .combinedClickable(onClick = onClick, onLongClick = onOptions),
                contentAlignment = Alignment.Center,
            ) {
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

/** Le logo dans le cercle : discret sur le verre, sur un disque blanc quand le cercle a le focus. */
@Composable
private fun CircleLogo(icon: SiteIcon?, name: String, focused: Boolean) {
    val disc by animateDpAsState(if (focused) 58.dp else 52.dp, label = "logoDisc")
    Box(
        Modifier.size(disc).clip(CircleShape).background(if (focused) Color.White.copy(alpha = 0.95f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(
                icon.image,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(disc * 0.62f).graphicsLayer { alpha = if (focused) 1f else 0.9f },
            )
        } else {
            Text(name.firstOrNull()?.uppercase().orEmpty(), color = if (focused) Palette.Ink else Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** « Ajouter » : un cercle en pointillé discret, blanc plein au focus. */
@Composable
internal fun AddCard(index: Int, onClick: () -> Unit, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(rememberEntrance(index).width(CIRCLE_SLOT), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(CIRCLE_SLOT), contentAlignment = Alignment.Center) {
            FocusSurface(
                onClick,
                Modifier.size(CIRCLE).border(1.5.dp, if (focused) Color.Transparent else Color.White.copy(alpha = 0.2f), CircleShape),
                shape = CircleShape,
                idle = Color.White.copy(alpha = 0.06f),
                focusScale = 1.14f,
                onFocusChange = {
                    focused = it
                    if (it) onFocus()
                },
            ) { f ->
                Text("+", color = onSurface(f), fontSize = 34.sp, fontWeight = FontWeight.Light)
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
 * Où se trouve l'élément qui ouvre un site (cercle ou vignette sélectionnée) : la page s'agrandit
 * depuis lui à l'ouverture.
 */
object LaunchOrigin {
    @Volatile var bounds: android.graphics.Rect? = null
        private set

    /** Couleur de l'élément (celle du site), pour le cercle qui s'agrandit. */
    @Volatile var color: Int = 0xFF1E5BFF.toInt()
        private set

    fun remember(coordinates: androidx.compose.ui.layout.LayoutCoordinates, color: Color) {
        val r = coordinates.boundsInWindow()
        bounds = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
        this.color = color.toArgb()
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

internal fun parseColor(hex: String?): Color? = hex?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}

/** « Reprendre » : les vidéos en cours, la plus récente d'abord. OK reprend, appui long retire la carte. */
@Composable
private fun ContinueRow(
    entries: List<WatchEntry>,
    siteMap: Map<String, Site>,
    onOpen: (WatchEntry) -> Unit,
    onRemove: (WatchEntry) -> Unit,
    onFocus: (WatchEntry) -> Unit,
    firstModifier: Modifier = Modifier,
) {
    Column {
        SectionLabel(stringResource(R.string.continue_title), Modifier.padding(start = 52.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 52.dp, end = 52.dp, top = 10.dp, bottom = 6.dp),
            modifier = Modifier.fillMaxWidth().height(126.dp),
        ) {
            itemsIndexed(entries, key = { _, it -> it.url }) { index, entry ->
                WatchCard(entry, siteMap[entry.siteId], { onOpen(entry) }, { onRemove(entry) }, { onFocus(entry) }, if (index == 0) firstModifier else Modifier)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchCard(entry: WatchEntry, site: Site?, onClick: () -> Unit, onRemove: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    val siteName = site?.name ?: Hosts.of(entry.url)?.removePrefix("www.").orEmpty()
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(178.dp, 100.dp)
            .onGloballyPositioned { if (focused) LaunchOrigin.remember(it, Palette.Accent) }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .liftEffect(focused, CardShape, focusScale = 1.08f, lift = 22.dp)
            .clip(CardShape)
            .background(Palette.Glass)
            .then(if (focused) Modifier.border(2.dp, Color.White, CardShape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onRemove),
    ) {
        // L'image de la vidéo (ou de la page), avec le logo du site ; sinon la couleur et le logo du site.
        VideoArt(rememberWatchImage(entry), site, entry.url, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
            Text(entry.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (siteName.isNotEmpty()) Text(siteName, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1)
        }
        // Avancement de la vidéo.
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(entry.progress).height(3.dp).background(Color.White))
        }
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
