package dev.wido.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import dev.wido.core.PhoneProtocol
import dev.wido.tv.PhoneLink
import dev.wido.tv.R

/** Libellés de la page du téléphone, dans la langue de la TV. */
@Composable
fun phoneLabels() = PhoneProtocol.Labels(
    placeholder = stringResource(R.string.phone_placeholder),
    send = stringResource(R.string.phone_send),
    sendEnter = stringResource(R.string.phone_send_enter),
    sent = stringResource(R.string.phone_sent),
    failed = stringResource(R.string.phone_failed),
)

/**
 * Le QR code à scanner et l'adresse, tant que le serveur du téléphone est ouvert. Le serveur démarre
 * avec cet affichage et s'arrête avec lui. `onMessage` reçoit chaque texte envoyé.
 */
@Composable
fun PhoneLinkCard(hint: String, onMessage: (PhoneProtocol.Message) -> Unit, modifier: Modifier = Modifier) {
    val labels = phoneLabels()
    val latest by rememberUpdatedState(onMessage)
    var url by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val link = PhoneLink(labels) { latest(it) }
        url = link.start()
        failed = url == null
        onDispose { link.stop() }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(12.dp)) {
            url?.let { QrCode(it, 200.dp) } ?: Box(Modifier.size(200.dp))
        }
        Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (failed) stringResource(R.string.phone_no_network) else hint,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            url?.let { Text(it.substringBefore("?"), color = Palette.TextSecondary, fontSize = 14.sp) }
            Text(stringResource(R.string.phone_same_network), color = Palette.TextMuted, fontSize = 13.sp)
        }
    }
}

/** Fenêtre « Saisir au téléphone » : le QR code, puis elle se ferme à la réception du texte. */
@Composable
fun PhoneDialog(hint: String, onMessage: (PhoneProtocol.Message) -> Unit, onDismiss: () -> Unit) {
    DialogFrame(
        title = stringResource(R.string.phone_title),
        onDismiss = onDismiss,
        width = 660.dp,
        buttons = { ActionButton(stringResource(R.string.action_cancel), onDismiss, primary = false) },
    ) {
        PhoneLinkCard(hint, onMessage)
    }
}

/** QR code dessiné en modules noirs sur blanc. */
@Composable
fun QrCode(text: String, size: Dp) {
    val matrix = remember(text) { encode(text) }
    Canvas(Modifier.size(size)) {
        val m = matrix ?: return@Canvas
        val cell = this.size.width / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

private fun encode(text: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
}.getOrNull()

/** Version plein écran pour le lecteur : le QR code sur un panneau de verre, par-dessus la page. */
@Composable
fun PhoneOverlay(hint: String, onMessage: (PhoneProtocol.Message) -> Unit, onClose: () -> Unit) {
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0x99080810)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.glassPanel(Palette.PanelSolid, elevation = 30.dp).padding(horizontal = 30.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.phone_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            PhoneLinkCard(hint, onMessage)
            ActionButton(stringResource(R.string.settings_close), onClose, Modifier.focusRequester(close), primary = false)
        }
    }
}
