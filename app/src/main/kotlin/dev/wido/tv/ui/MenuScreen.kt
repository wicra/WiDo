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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

@Composable
fun MenuScreen(vm: MenuViewModel, onOpen: (Site) -> Unit) {
    val state by vm.config.collectAsState()
    val update by vm.update.collectAsState()
    val sites by vm.sites.collectAsState()

    var editor by remember { mutableStateOf<Editor?>(null) }
    // Ambiance : le fond prend la couleur de la carte qui a le focus, comme la lumière d'une salle.
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
                        listOf(ambient.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(size.width * 0.5f, -size.height * 0.05f),
                        radius = size.width * 0.85f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.maxDimension * 0.72f,
                    ),
                )
            }
            .padding(horizontal = 56.dp, vertical = 28.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(state, onRefresh = vm::refresh)
            Spacer(Modifier.height(14.dp))
            UpdateBanner(update, onInstall = vm::install)
            Marquee()
            Spacer(Modifier.height(18.dp))
            if (sites.isEmpty() && !state.loading) {
                EmptyState()
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(250.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
                contentPadding = PaddingValues(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(sites, key = { _, it -> it.site.id }) { index, entry ->
                    SiteCard(
                        entry = entry,
                        index = index,
                        onClick = { onOpen(entry.site) },
                        onEdit = { editor = Editor.Edit(entry.site) },
                        onFocus = { ambientTarget = it },
                        modifier = if (index == 0) Modifier.focusRequester(firstCard) else Modifier,
                    )
                }
                item(key = "add") {
                    AddCard(
                        index = sites.size,
                        onClick = { editor = Editor.Add },
                        onFocus = { ambientTarget = it },
                    )
                }
            }
            Footer()
        }
    }

    editor?.let { current ->
        val initial = (current as? Editor.Edit)?.site
        SiteEditorDialog(
            initial = initial,
            onSave = { name, url, options ->
                val error = if (initial == null) vm.addSite(name, url, options) else vm.updateSite(initial.id, name, url, options)
                if (error == null) editor = null
                error
            },
            onDelete = initial?.let { site ->
                {
                    vm.removeSite(site.id)
                    editor = null
                }
            },
            onDismiss = { editor = null },
        )
    }
}

@Composable
private fun Header(state: ConfigState, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.wido_logo),
            contentDescription = null,
            modifier = Modifier.size(58.dp),
        )
        Spacer(Modifier.width(18.dp))
        Column {
            Text(
                "WiDo",
                color = Palette.TextPrimary,
                fontSize = 38.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 10.sp,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(72.dp)
                    .height(2.dp)
                    .background(Brush.horizontalGradient(listOf(Palette.Highlight, Color.Transparent))),
            )
        }
        Spacer(Modifier.weight(1f))
        StatusPill(state)
        Spacer(Modifier.width(16.dp))
        ActionButton(stringResource(R.string.action_refresh), onRefresh)
    }
}

@Composable
private fun Marquee() {
    Text(
        stringResource(R.string.menu_title).uppercase(),
        color = Palette.Highlight,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 6.sp,
    )
}

@Composable
private fun StatusPill(state: ConfigState) {
    val revision = state.config?.revision ?: 0
    val (text, dot) = when {
        state.loading && state.config == null -> stringResource(R.string.status_loading) to Palette.TextSecondary
        state.warnings.isNotEmpty() -> stringResource(R.string.status_warnings, state.warnings.size) to Palette.Warning
        state.source == ConfigSource.Remote -> stringResource(R.string.status_ok, revision) to Palette.Ok
        state.source == ConfigSource.Cache -> stringResource(R.string.status_offline, revision) to Palette.Warning
        else -> stringResource(R.string.status_bundled) to Palette.Warning
    }
    Row(
        Modifier.clip(PillShape).background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Palette.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
internal fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
) {
    Box(
        modifier
            .tvFocusable(PillShape, glow = Palette.Accent, onClick = onClick)
            .background(if (primary) Palette.Accent else Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 24.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun UpdateBanner(update: UpdateUi, onInstall: (ReleaseInfo) -> Unit) {
    if (update is UpdateUi.None) return
    val (message, info) = when (update) {
        is UpdateUi.Available -> stringResource(R.string.update_available, update.info.tag) to update.info
        is UpdateUi.Downloading -> stringResource(R.string.update_downloading, update.percent) to null
        is UpdateUi.Failed -> stringResource(update.messageRes, update.detail.orEmpty()) to update.info
        UpdateUi.None -> return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(CardShape)
            .background(Brush.horizontalGradient(listOf(Palette.Highlight.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = Palette.TextPrimary, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (info != null) ActionButton(stringResource(R.string.update_install), { onInstall(info) })
    }
}

/** Apparition en fondu et légère montée, décalée carte par carte. */
@Composable
private fun rememberEntrance(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(8) * 70L))
        progress.animateTo(1f, tween(450))
    }
    return Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 40f
    }
}

@Composable
private fun SiteCard(
    entry: MenuSite,
    index: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
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
    // Les cartes perso se modifient par un clic long sur OK, ou la touche Menu.
    val editKeys = if (entry.local) {
        Modifier.onKeyEvent { e ->
            val handled = e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU && e.nativeKeyEvent.action == KeyEvent.ACTION_UP
            if (handled) onEdit()
            handled
        }
    } else {
        Modifier
    }

    Box(
        modifier
            .then(rememberEntrance(index))
            .then(editKeys)
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .tvFocusable(CardShape, glow = accent, onFocusChange = { if (it) onFocus(accent) }, onLongClick = if (entry.local) onEdit else null, onClick = onClick)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.25f), Color(0xFF0C0E16))))
            .filmStrip(),
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .padding(bottom = 26.dp)
                .size(76.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            val siteIcon = icon
            if (siteIcon != null) {
                Image(
                    siteIcon.image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(52.dp),
                )
            } else {
                Text(
                    site.name.firstOrNull()?.uppercase().orEmpty(),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 16.dp),
        ) {
            Text(
                site.name,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Hosts.of(site.url).orEmpty(),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (entry.local) {
            Text(
                stringResource(R.string.badge_local),
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 12.dp)
                    .clip(PillShape)
                    .background(Palette.Highlight)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

/** Perforations de pellicule en haut et en bas de la carte. */
private fun Modifier.filmStrip(): Modifier = drawBehind {
    val hole = Size(10.dp.toPx(), 6.dp.toPx())
    val gap = 18.dp.toPx()
    val margin = 7.dp.toPx()
    val color = Color.Black.copy(alpha = 0.35f)
    var x = gap / 2
    while (x + hole.width < size.width) {
        drawRoundRect(color, Offset(x, margin), hole, CornerRadius(2.dp.toPx()))
        drawRoundRect(color, Offset(x, size.height - margin - hole.height), hole, CornerRadius(2.dp.toPx()))
        x += gap
    }
}

@Composable
private fun AddCard(index: Int, onClick: () -> Unit, onFocus: (Color) -> Unit) {
    Box(
        Modifier
            .then(rememberEntrance(index))
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .tvFocusable(CardShape, glow = Palette.Highlight, onFocusChange = { if (it) onFocus(Palette.Highlight) }, onClick = onClick)
            .background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+", color = Palette.Highlight, fontSize = 54.sp, fontWeight = FontWeight.Light)
            Text(
                stringResource(R.string.add_site),
                color = Palette.TextSecondary,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(stringResource(R.string.empty_title), color = Palette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_body), color = Palette.TextSecondary, fontSize = 17.sp)
    }
}

@Composable
private fun Footer() {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.menu_hint), color = Palette.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.version_footer, BuildConfig.VERSION_NAME), color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

private fun parseColor(hex: String?): Color? = hex?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}
