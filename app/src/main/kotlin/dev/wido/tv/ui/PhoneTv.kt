package dev.wido.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.DeviceRole
import dev.wido.core.LinkLevel
import dev.wido.core.LinkGuest
import dev.wido.core.LinkProtocol
import dev.wido.core.NowPlaying
import dev.wido.core.Peer
import dev.wido.core.PlayerFormat
import dev.wido.core.RemoteKey
import dev.wido.tv.LinkDiscovery
import dev.wido.tv.R
import dev.wido.tv.ScreenLink
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private sealed interface Pairing {
    data class Waiting(val name: String) : Pairing
    data class Done(val result: LinkGuest.PairResult, val name: String) : Pairing
}

private enum class RemoteMode { Keys, Gestures, Pad, Keyboard }

/**
 * Onglet TV du téléphone : ce qui passe sur la TV choisie (avec « Reprendre sur le téléphone »), la
 * télécommande (touches, pavé tactile, clavier en direct), les écrans liés et ceux à lier.
 */
@Composable
fun PhoneTvTab(
    link: ScreenLink,
    onPlayHere: (NowPlaying) -> Unit,
    pairTarget: LinkProtocol.PairTarget?,
    onPairTargetHandled: () -> Unit,
) {
    val peers by link.peers.collectAsState()
    val states by link.states.collectAsState()
    val selectedId by link.selected.collectAsState()
    val found by link.discovery.found.collectAsState()
    val scope = rememberCoroutineScope()
    var pairing by remember { mutableStateOf<Pairing?>(null) }
    var pairJob by remember { mutableStateOf<Job?>(null) }
    var manual by remember { mutableStateOf(false) }

    val selected = peers.firstOrNull { it.id == selectedId } ?: peers.firstOrNull()
    val unpaired = found.filter { f -> f.advert.role == DeviceRole.TV && peers.none { it.id == f.advert.id } }

    fun startPair(host: String, port: Int, key: String?, name: String) {
        pairing = Pairing.Waiting(name)
        pairJob = scope.launch { pairing = Pairing.Done(link.pair(host, port, key), name) }
    }

    LaunchedEffect(pairTarget) {
        val target = pairTarget ?: return@LaunchedEffect
        startPair(target.host, target.port, target.key, target.name ?: target.host)
        onPairTargetHandled()
    }

    // Tant que l'onglet est affiché, les touches de volume du téléphone règlent la TV.
    DisposableEffect(Unit) {
        link.volumeToTv = true
        onDispose { link.volumeToTv = false }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhoneTitle(stringResource(R.string.tab_tv), Modifier.weight(1f))
                RoundButton(Glyph.Refresh, { link.discovery.restart() })
            }
        }
        if (peers.isEmpty()) {
            item { EmptyScreens() }
            if (unpaired.isNotEmpty()) item { SectionLabel(stringResource(R.string.screens_found)) }
            items(unpaired, key = { it.advert.id }) { f -> FoundRow(f) { startPair(f.host, f.port, null, f.advert.name) } }
            if (unpaired.isEmpty()) item { Searching() }
            item { SmallPill(stringResource(R.string.screens_manual)) { manual = true } }
        } else {
            if (peers.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(peers, key = { it.id }) { peer ->
                            val online = states[peer.id]?.online == true
                            Chip(peer.name, { link.selectScreen(peer.id) }, selected = peer.id == selected?.id, glyph = Glyph.Dot, glyphTint = if (online) Palette.Ok else Palette.TextMuted)
                        }
                    }
                }
            }
            if (selected != null) {
                val st = states[selected.id] ?: ScreenLink.ScreenState()
                item(key = "now-${selected.id}") {
                    NowPlayingCard(selected, st, link, onPlayHere = {
                        scope.launch { link.take(selected)?.let(onPlayHere) }
                    })
                }
                if (st.online) item(key = "remote") { RemotePanel(link) }
            }
            item { SectionLabel(stringResource(R.string.screens_linked), Modifier.padding(top = 6.dp)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinkedDevicesPhone(peers, states, link)
                }
            }
            if (unpaired.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.screens_found), Modifier.padding(top = 6.dp)) }
                items(unpaired, key = { it.advert.id }) { f -> FoundRow(f) { startPair(f.host, f.port, null, f.advert.name) } }
            }
            item { SmallPill(stringResource(R.string.screens_manual)) { manual = true } }
        }
    }

    pairing?.let { p -> PairingDialog(p, onCancel = { pairJob?.cancel(); pairing = null }) }
    if (manual) ManualAddressDialog(onDismiss = { manual = false }) { host, port ->
        manual = false
        startPair(host, port, null, host)
    }
}

@Composable
private fun EmptyScreens() {
    Column(
        Modifier.fillMaxWidth().glassPanel(Palette.Glass, RowShape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.screens_empty_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.screens_empty_body), color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun Searching() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        Text(stringResource(R.string.screens_searching), color = Palette.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun FoundRow(found: LinkDiscovery.Found, onPair: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glassPanel(Palette.GlassFaint, RowShape).clickable(onClick = onPair).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(found.advert.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(found.host, color = Palette.TextSecondary, fontSize = 12.sp)
        }
        SmallPill(stringResource(R.string.screens_pair), primary = true, onClick = onPair)
    }
}

/** Ce qui passe sur la TV : image, titre, barre de lecture déplaçable, commandes et « Reprendre sur le téléphone ». */
@Composable
internal fun NowPlayingCard(peer: Peer, state: ScreenLink.ScreenState, link: ScreenLink, onPlayHere: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxWidth().glassPanel(Palette.Glass, PanelShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (state.online) Palette.Ok else Palette.TextMuted))
            Text(peer.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (peer.level == LinkLevel.MINE) R.string.level_mine_screen else R.string.level_guest),
                color = Palette.TextSecondary,
                fontSize = 12.sp,
            )
        }
        val now = state.status?.playing
        when {
            !state.online -> {
                Text(stringResource(R.string.tv_offline, peer.name), color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
            }
            now == null -> {
                Text(stringResource(R.string.tv_idle), color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
            }
            else -> {
                // La position avance toute seule entre deux nouvelles de la TV.
                var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(now) {
                    while (true) {
                        tick = System.currentTimeMillis()
                        delay(500)
                    }
                }
                val position = now.positionAfter(tick - state.receivedAt)
                var dragging by remember { mutableStateOf(false) }
                var dragValue by remember { mutableFloatStateOf(0f) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    VideoArt(rememberThumbnail(now.image), null, now.url, Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(10.dp)), badge = 16.dp)
                    Column(Modifier.weight(1f)) {
                        Text(now.title.ifEmpty { now.url }, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (now.siteName.isNotEmpty()) Text(now.siteName, color = Palette.TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (now.duration > 0) {
                    val shown = if (dragging) dragValue * now.duration else position
                    Slider(
                        value = if (dragging) dragValue else (position / now.duration).toFloat().coerceIn(0f, 1f),
                        onValueChange = {
                            dragging = true
                            dragValue = it
                        },
                        onValueChangeFinished = {
                            link.command(JSONObject().put("cmd", "seek").put("to", dragValue * now.duration))
                            dragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                        ),
                    )
                    Row {
                        Text(PlayerFormat.time(shown), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("−" + PlayerFormat.time(now.duration - shown), color = Palette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Glyph.Rewind, { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); link.key(RemoteKey.REWIND) }, size = 52.dp)
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Color.White).clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            link.key(RemoteKey.PLAY_PAUSE)
                        },
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(if (now.playing) Glyph.Pause else Glyph.Play, Palette.Ink, 28.dp) }
                    RoundButton(Glyph.Forward, { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); link.key(RemoteKey.FORWARD) }, size = 52.dp)
                }
                ActionButton(stringResource(R.string.action_play_here), onPlayHere, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Télécommande : touches, pavé tactile ou clavier en direct. */
@Composable
internal fun RemotePanel(link: ScreenLink) {
    var mode by remember { mutableStateOf(RemoteMode.Keys) }
    Column(
        Modifier.fillMaxWidth().glassPanel(Palette.GlassFaint, PanelShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().clip(PillShape).background(Palette.Glass).padding(4.dp)) {
            RemoteMode.entries.forEach { m ->
                val on = m == mode
                Box(
                    Modifier.weight(1f).clip(PillShape).background(if (on) Color.White else Color.Transparent).clickable { mode = m }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(
                            when (m) {
                                RemoteMode.Keys -> R.string.remote_keys
                                RemoteMode.Gestures -> R.string.remote_gestures
                                RemoteMode.Pad -> R.string.remote_touchpad
                                RemoteMode.Keyboard -> R.string.remote_keyboard
                            },
                        ),
                        color = if (on) Palette.Ink else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        when (mode) {
            RemoteMode.Keys -> KeysPad(link)
            RemoteMode.Gestures -> GesturePad(link)
            RemoteMode.Pad -> TouchPad(link)
            RemoteMode.Keyboard -> LiveKeyboard(link)
        }
    }
}

@Composable
private fun KeysPad(link: ScreenLink) {
    val haptic = LocalHapticFeedback.current
    fun press(key: RemoteKey) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        link.key(key)
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Croix directionnelle : un disque de verre, OK au centre.
        Box(Modifier.size(232.dp).clip(CircleShape).background(Palette.Glass)) {
            DpadArrow(Glyph.Up, Alignment.TopCenter) { press(RemoteKey.UP) }
            DpadArrow(Glyph.Down, Alignment.BottomCenter) { press(RemoteKey.DOWN) }
            DpadArrow(Glyph.Left, Alignment.CenterStart) { press(RemoteKey.LEFT) }
            DpadArrow(Glyph.Right, Alignment.CenterEnd) { press(RemoteKey.RIGHT) }
            Box(
                Modifier.align(Alignment.Center).size(88.dp).clip(CircleShape).background(Color.White).clickable { press(RemoteKey.OK) },
                contentAlignment = Alignment.Center,
            ) { Text("OK", color = Palette.Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
        }
        KeyRow(listOf(Glyph.Back to RemoteKey.BACK, Glyph.Home to RemoteKey.HOME, Glyph.Menu to RemoteKey.MENU), ::press)
        KeyRow(listOf(Glyph.VolumeDown to RemoteKey.VOLUME_DOWN, Glyph.Mute to RemoteKey.MUTE, Glyph.VolumeUp to RemoteKey.VOLUME_UP), ::press)
        Text(stringResource(R.string.remote_volume_hint), color = Palette.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BoxScope.DpadArrow(glyph: Glyph, alignment: Alignment, onClick: () -> Unit) {
    Box(
        Modifier.align(alignment).size(72.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, Color.White.copy(alpha = 0.9f), 30.dp) }
}

@Composable
private fun KeyRow(keys: List<Pair<Glyph, RemoteKey>>, onPress: (RemoteKey) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.forEach { (glyph, key) ->
            Box(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Glass).clickable { onPress(key) },
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(glyph, Color.White, 24.dp) }
        }
    }
}

/**
 * Gestes, sans regarder le téléphone : glisser dans une direction = la flèche, toucher = OK,
 * appui long = Retour. La flèche jouée s'affiche un instant au centre.
 */
@Composable
private fun GesturePad(link: ScreenLink) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    var shown by remember { mutableStateOf<Glyph?>(null) }
    var pulse by remember { mutableLongStateOf(0L) }
    fun press(key: RemoteKey, glyph: Glyph) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        link.key(key)
        shown = glyph
        pulse = System.nanoTime()
    }
    LaunchedEffect(pulse) {
        if (pulse == 0L) return@LaunchedEffect
        delay(450)
        shown = null
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Glass)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { press(RemoteKey.OK, Glyph.Dot) },
                        onLongPress = { press(RemoteKey.BACK, Glyph.Back) },
                    )
                }
                .pointerInput(Unit) {
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDrag = { change, drag ->
                            change.consume()
                            dx += drag.x
                            dy += drag.y
                            // Un glissement assez long joue la flèche ; on peut enchaîner sans lever le doigt.
                            val step = SWIPE_DP * density
                            if (kotlin.math.abs(dx) > step || kotlin.math.abs(dy) > step) {
                                if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                    if (dx > 0) press(RemoteKey.RIGHT, Glyph.Right) else press(RemoteKey.LEFT, Glyph.Left)
                                } else {
                                    if (dy > 0) press(RemoteKey.DOWN, Glyph.Down) else press(RemoteKey.UP, Glyph.Up)
                                }
                                dx = 0f
                                dy = 0f
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val glyph = shown
            if (glyph != null) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    if (glyph == Glyph.Dot) Text("OK", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) else GlyphIcon(glyph, Color.White, 44.dp)
                }
            } else {
                Text(stringResource(R.string.remote_gestures_hint), color = Palette.TextMuted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
        }
        KeyRow(listOf(Glyph.Home to RemoteKey.HOME, Glyph.Menu to RemoteKey.MENU, Glyph.VolumeDown to RemoteKey.VOLUME_DOWN, Glyph.VolumeUp to RemoteKey.VOLUME_UP)) { link.key(it) }
    }
}

private const val SWIPE_DP = 56f

/** Pavé tactile : glisser déplace le curseur de la TV, toucher clique ; la bande de droite fait défiler. */
@Composable
private fun TouchPad(link: ScreenLink) {
    val density = LocalDensity.current.density
    var pendingX by remember { mutableFloatStateOf(0f) }
    var pendingY by remember { mutableFloatStateOf(0f) }
    // Les petits déplacements sont regroupés : une commande toutes les 30 ms au plus.
    LaunchedEffect(Unit) {
        while (true) {
            if (pendingX != 0f || pendingY != 0f) {
                link.command(JSONObject().put("cmd", "move").put("dx", (pendingX / density * 1.6f).toDouble()).put("dy", (pendingY / density * 1.6f).toDouble()))
                pendingX = 0f
                pendingY = 0f
            }
            delay(30)
        }
    }
    Row(Modifier.fillMaxWidth().height(260.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Glass)
                .pointerInput(Unit) { detectTapGestures(onTap = { link.command(JSONObject().put("cmd", "tap")) }) }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        pendingX += drag.x
                        pendingY += drag.y
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.remote_touchpad_hint), color = Palette.TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
        }
        var scrollAcc by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier
                .width(56.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.GlassFaint)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        scrollAcc += dy
                        if (kotlin.math.abs(scrollAcc) > 12 * density) {
                            link.command(JSONObject().put("cmd", "scroll").put("dy", (-scrollAcc / density * 4f).toDouble()))
                            scrollAcc = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GlyphIcon(Glyph.Up, Palette.TextMuted, 22.dp)
                GlyphIcon(Glyph.Down, Palette.TextMuted, 22.dp)
            }
        }
    }
}

/**
 * Clavier en direct : chaque lettre tapée s'écrit aussitôt dans le champ sélectionné sur la TV ;
 * effacer efface aussi là-bas. « Entrée » valide (recherche, formulaire).
 */
@Composable
private fun LiveKeyboard(link: ScreenLink) {
    var text by remember { mutableStateOf("") }
    fun type(t: String, submit: Boolean = false) = link.command(JSONObject().put("cmd", "type").put("text", t).put("submit", submit))
    fun change(next: String) {
        val old = text
        when {
            next.startsWith(old) -> if (next.length > old.length) type(next.substring(old.length))
            old.startsWith(next) -> repeat(old.length - next.length) { link.key(RemoteKey.DEL) }
            else -> {
                val common = old.commonPrefixWith(next).length
                repeat(old.length - common) { link.key(RemoteKey.DEL) }
                if (next.length > common) type(next.substring(common))
            }
        }
        text = next
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicTextField(
            value = text,
            onValueChange = ::change,
            textStyle = TextStyle(fontFamily = Jakarta, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                type("", submit = true)
                text = ""
            }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().clip(RowShape).background(Palette.Glass).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    if (text.isEmpty()) Text(stringResource(R.string.remote_keyboard_placeholder), color = Palette.TextMuted, fontSize = 16.sp)
                    inner()
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(stringResource(R.string.phone_send_enter), { type("", submit = true); text = "" })
            ActionButton(stringResource(R.string.remote_keyboard_clear), { text = "" }, primary = false)
        }
        Text(stringResource(R.string.remote_keyboard_hint), color = Palette.TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

/** Écrans liés, vus du téléphone : en ligne ou non, Mon écran (synchro) ou Invité, Oublier. */
@Composable
private fun LinkedDevicesPhone(peers: List<Peer>, states: Map<String, ScreenLink.ScreenState>, link: ScreenLink) {
    peers.forEach { peer ->
        val st = states[peer.id]
        Column(
            Modifier.fillMaxWidth().glassPanel(Palette.GlassFaint, RowShape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (st?.online == true) Palette.Ok else Palette.TextMuted))
                Column(Modifier.weight(1f)) {
                    Text(peer.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(if (st?.online == true) R.string.screen_online else R.string.screen_offline),
                        color = Palette.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.level_mine_screen), { link.setLevel(peer.id, LinkLevel.MINE) }, selected = peer.level == LinkLevel.MINE)
                Chip(stringResource(R.string.level_guest), { link.setLevel(peer.id, LinkLevel.GUEST) }, selected = peer.level == LinkLevel.GUEST)
                Chip(stringResource(R.string.link_forget), { link.forget(peer.id) }, idleText = Color.White.copy(alpha = 0.6f))
            }
            val hint = when {
                peer.level == LinkLevel.MINE && st?.syncRefused == true -> stringResource(R.string.sync_refused, peer.name)
                peer.level == LinkLevel.MINE -> stringResource(R.string.level_mine_hint)
                else -> stringResource(R.string.level_guest_hint)
            }
            Text(hint, color = if (st?.syncRefused == true && peer.level == LinkLevel.MINE) Palette.Warning else Palette.TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun PairingDialog(pairing: Pairing, onCancel: () -> Unit) {
    when (pairing) {
        is Pairing.Waiting -> DialogFrame(
            title = stringResource(R.string.pairing_title),
            onDismiss = onCancel,
            buttons = { ActionButton(stringResource(R.string.action_cancel), onCancel, primary = false) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
                Text(stringResource(R.string.pairing_confirm_on_tv, pairing.name), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.pairing_levels_hint), color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        }
        is Pairing.Done -> {
            val (title, body) = when (pairing.result) {
                is LinkGuest.PairResult.Approved -> stringResource(R.string.pairing_ok_title) to stringResource(R.string.pairing_ok, pairing.name)
                LinkGuest.PairResult.Denied -> stringResource(R.string.pairing_failed_title) to stringResource(R.string.pairing_denied)
                LinkGuest.PairResult.Unreachable -> stringResource(R.string.pairing_failed_title) to stringResource(R.string.pairing_unreachable)
                LinkGuest.PairResult.TimedOut -> stringResource(R.string.pairing_failed_title) to stringResource(R.string.pairing_timeout)
            }
            DialogFrame(title = title, onDismiss = onCancel, buttons = { ActionButton(stringResource(R.string.action_ok), onCancel) }) {
                Text(body, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun ManualAddressDialog(onDismiss: () -> Unit, onPair: (String, Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun submit() {
        val parsed = LinkProtocol.parseAddress(text)
        if (parsed == null) error = true else onPair(parsed.first, parsed.second)
    }
    DialogFrame(
        title = stringResource(R.string.screens_manual),
        onDismiss = onDismiss,
        buttons = {
            ActionButton(stringResource(R.string.screens_pair), { submit() })
            ActionButton(stringResource(R.string.action_cancel), onDismiss, primary = false)
        },
    ) {
        Text(stringResource(R.string.screens_manual_hint), color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        GlassTextField(
            value = text,
            onValueChange = { text = it; error = false },
            label = stringResource(R.string.screens_address),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        if (error) Text(stringResource(R.string.screens_address_invalid), color = Palette.Warning, fontSize = 13.sp)
    }
}

/**
 * Tablette : la TV à droite de l'accueil, en permanence (ce qu'elle lit et la télécommande). Sans TV
 * liée, un mot pour en lier une.
 */
@Composable
fun TvSidePane(link: ScreenLink, onPlayHere: (NowPlaying) -> Unit, onOpenTvTab: () -> Unit) {
    val peers by link.peers.collectAsState()
    val states by link.states.collectAsState()
    val selectedId by link.selected.collectAsState()
    val scope = rememberCoroutineScope()
    val peer = peers.firstOrNull { it.id == selectedId } ?: peers.firstOrNull()
    LazyColumn(
        contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (peer == null) {
            item { EmptyScreens() }
            item { SmallPill(stringResource(R.string.tab_tv), primary = true, onClick = onOpenTvTab) }
        } else {
            val st = states[peer.id] ?: ScreenLink.ScreenState()
            item(key = "now") { NowPlayingCard(peer, st, link) { scope.launch { link.take(peer)?.let(onPlayHere) } } }
            if (st.online) item(key = "remote") { RemotePanel(link) }
        }
    }
}
