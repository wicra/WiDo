package dev.wido.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.Hosts
import dev.wido.core.Site
import dev.wido.tv.BuildConfig
import dev.wido.tv.ConfigSource
import dev.wido.tv.ConfigState
import dev.wido.tv.MenuViewModel
import dev.wido.tv.R
import dev.wido.tv.UpdateUi

@Composable
fun MenuScreen(vm: MenuViewModel, onOpen: (Site) -> Unit) {
    val state by vm.config.collectAsState()
    val update by vm.update.collectAsState()
    val sites = state.config?.sites?.filter { it.enabled }.orEmpty()
    val firstCard = remember { FocusRequester() }

    LaunchedEffect(sites.isNotEmpty()) {
        if (sites.isNotEmpty()) runCatching { firstCard.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF141830), Palette.Background)))
            .padding(horizontal = 56.dp, vertical = 32.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(state, onRefresh = vm::refresh)
            Spacer(Modifier.height(20.dp))
            UpdateBanner(update, onInstall = vm::install)
            Text(
                stringResource(R.string.menu_title),
                color = Palette.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))
            if (sites.isEmpty() && !state.loading) {
                EmptyState()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(250.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(sites, key = { it.id }) { site ->
                        SiteCard(
                            site,
                            onClick = { onOpen(site) },
                            modifier = if (site == sites.first()) Modifier.focusRequester(firstCard) else Modifier,
                        )
                    }
                }
            }
            Footer()
        }
    }
}

@Composable
private fun Header(state: ConfigState, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("WiDo", color = Palette.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.padding(start = 6.dp, top = 14.dp).size(10.dp).clip(CircleShape).background(Palette.Accent))
        Spacer(Modifier.weight(1f))
        StatusPill(state)
        Spacer(Modifier.width(16.dp))
        ActionButton(stringResource(R.string.action_refresh), onRefresh)
    }
}

@Composable
private fun StatusPill(state: ConfigState) {
    val revision = state.config?.revision ?: 0
    val (text, dot) = when {
        state.loading && state.config == null -> stringResource(R.string.status_loading) to Palette.TextSecondary
        state.warnings.isNotEmpty() -> stringResource(R.string.status_warnings, state.warnings.size) to Palette.Warning
        state.source == ConfigSource.Remote -> stringResource(R.string.status_ok, revision) to Color(0xFF4CD08B)
        state.source == ConfigSource.Cache -> stringResource(R.string.status_offline, revision) to Palette.Warning
        else -> stringResource(R.string.status_bundled) to Palette.Warning
    }
    Row(
        Modifier.clip(PillShape).background(Palette.Surface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Palette.TextSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .tvFocusable(PillShape, onClick)
            .background(Palette.Accent)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun UpdateBanner(update: UpdateUi, onInstall: (dev.wido.core.ReleaseInfo) -> Unit) {
    if (update is UpdateUi.None) return
    val (message, info) = when (update) {
        is UpdateUi.Available -> stringResource(R.string.update_available, update.info.tag) to update.info
        is UpdateUi.Downloading -> stringResource(R.string.update_downloading, update.percent) to null
        is UpdateUi.Failed -> {
            val base = stringResource(update.messageRes, update.detail.orEmpty())
            base to update.info
        }
        UpdateUi.None -> return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clip(CardShape)
            .background(Palette.Surface)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = Palette.TextPrimary, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (info != null) ActionButton(stringResource(R.string.update_install), { onInstall(info) })
    }
}

@Composable
private fun SiteCard(site: Site, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = parseColor(site.color) ?: Palette.Accent
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .tvFocusable(CardShape, onClick)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Palette.Surface)))
            .padding(20.dp),
    ) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                site.name,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Hosts.of(site.url).orEmpty(),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(44.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                site.name.firstOrNull()?.uppercase().orEmpty(),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.empty_title), color = Palette.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.empty_body), color = Palette.TextSecondary, fontSize = 18.sp)
    }
}

@Composable
private fun Footer() {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.menu_hint), color = Palette.TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.version_footer, BuildConfig.VERSION_NAME), color = Palette.TextSecondary, fontSize = 14.sp)
    }
}

private fun parseColor(hex: String?): Color? = hex?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}
