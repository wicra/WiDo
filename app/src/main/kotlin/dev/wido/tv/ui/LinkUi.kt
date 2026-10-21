package dev.wido.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.DeviceRole
import dev.wido.core.LinkLevel
import dev.wido.core.PairingDesk
import dev.wido.core.Peer
import dev.wido.tv.R
import dev.wido.tv.ScreenLink

/** TV : la première demande d'appairage en attente, à valider (rien si aucune). */
@Composable
fun PairPromptHost(link: ScreenLink) {
    val requests by link.pairRequests.collectAsState()
    val pending = requests.firstOrNull() ?: return
    // Une demande restée sans réponse expire : la fenêtre se ferme d'elle-même.
    LaunchedEffect(pending) {
        kotlinx.coroutines.delay((PairingDesk.EXPIRY_MS - (System.currentTimeMillis() - pending.since)).coerceAtLeast(0) + 500)
        link.refreshPairRequests()
    }
    PairPrompt(pending) { approved, level -> link.decidePair(pending, approved, level) }
}

/**
 * « Lier un appareil ? » : un téléphone du réseau demande à piloter cette TV. Invité (par défaut) :
 * télécommande et envoi de vidéos. Mon téléphone : en plus, la bibliothèque se synchronise.
 */
@Composable
fun PairPrompt(pending: PairingDesk.Pending, onDecide: (approved: Boolean, level: String) -> Unit) {
    val guest = remember { FocusRequester() }
    LaunchedEffect(pending) { runCatching { guest.requestFocus() } }
    val device = pending.request.device
    DialogFrame(
        autoFocus = false,
        title = stringResource(R.string.pair_title),
        onDismiss = { onDecide(false, LinkLevel.GUEST) },
        width = 640.dp,
        buttons = {
            ActionButton(stringResource(R.string.pair_guest), { onDecide(true, LinkLevel.GUEST) }, Modifier.focusRequester(guest))
            ActionButton(stringResource(R.string.pair_mine), { onDecide(true, LinkLevel.MINE) }, primary = false)
            ActionButton(stringResource(R.string.pair_refuse), { onDecide(false, LinkLevel.GUEST) }, primary = false, idleText = Color.White.copy(alpha = 0.7f))
        },
    ) {
        Text(
            stringResource(R.string.pair_body, device.name, stringResource(if (device.role == DeviceRole.TV) R.string.role_tv else R.string.role_phone)),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (pending.source.isNotEmpty()) Text(stringResource(R.string.pair_address, pending.source), color = Palette.TextSecondary, fontSize = 15.sp)
        Text(stringResource(R.string.pair_levels), color = Palette.TextSecondary, fontSize = 14.sp)
        Text(stringResource(R.string.pair_unknown), color = Palette.Warning, fontSize = 14.sp)
    }
}

/** TV : QR code d'appairage de l'appli, adresse à saisir à la main. */
@Composable
fun LinkPairingCard(pairUri: String?, address: String?) {
    if (pairUri == null || address == null) {
        Text(stringResource(R.string.phone_no_network), color = Palette.Warning, fontSize = 14.sp)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(10.dp)) { QrCode(pairUri, 150.dp) }
        Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.link_scan), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.link_address, address), color = Palette.TextSecondary, fontSize = 13.sp)
        }
    }
}

/** Appareils liés : niveau (mon appareil / invité) et « Oublier ». */
@Composable
fun LinkedDevices(peers: List<Peer>, onLevel: (Peer, String) -> Unit, onForget: (Peer) -> Unit, tvSide: Boolean = true) {
    if (peers.isEmpty()) {
        Text(stringResource(R.string.link_none), color = Palette.TextSecondary, fontSize = 13.sp)
        return
    }
    peers.forEach { peer ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (peer.level == LinkLevel.MINE) Palette.Ok else Palette.TextMuted))
            Column(Modifier.weight(1f)) {
                Text(peer.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (peer.role == DeviceRole.TV) R.string.role_tv else R.string.role_phone),
                    color = Palette.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            val mine = stringResource(if (tvSide) R.string.level_mine_phone else R.string.level_mine_screen)
            Chip(mine, { onLevel(peer, LinkLevel.MINE) }, selected = peer.level == LinkLevel.MINE)
            Chip(stringResource(R.string.level_guest), { onLevel(peer, LinkLevel.GUEST) }, selected = peer.level == LinkLevel.GUEST)
            ConfirmChip(stringResource(R.string.link_forget), stringResource(R.string.confirm_forget), { onForget(peer) })
        }
    }
}
