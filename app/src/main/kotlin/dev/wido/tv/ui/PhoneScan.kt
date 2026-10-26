package dev.wido.tv.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import dev.wido.core.ScanTarget
import dev.wido.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * « Écrire sur la TV » ouvert par le QR code du champ de saisie : le clavier du téléphone, puis Envoyer (ou
 * Envoyer + Entrée). Le texte part au petit serveur que la TV ouvre tant que son QR code est affiché.
 */
@Composable
fun PhoneTypeDialog(target: ScanTarget.Keyboard, onDone: (sent: Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun send(submit: Boolean) {
        if (sending) return
        sending = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                post("${target.base}/send?t=${target.token}", JSONObject().put("text", text).put("submit", submit).toString())
            }
            sending = false
            if (ok) onDone(true) else failed = true
        }
    }
    DialogFrame(
        title = stringResource(R.string.type_title),
        onDismiss = { onDone(false) },
        buttons = {
            ActionButton(stringResource(R.string.phone_send), { send(false) }, primary = false)
            ActionButton(stringResource(R.string.phone_send_enter), { send(true) })
        },
    ) {
        GlassTextField(text, { text = it; failed = false }, stringResource(R.string.phone_placeholder), Modifier.fillMaxWidth())
        if (failed) Text(stringResource(R.string.type_failed), color = Palette.Warning, fontSize = 13.sp)
    }
}

/** Le rapport (plantage ou test complet) servi par la TV, lu ici : copier, partager, envoyer. */
@Composable
fun ScannedReportDialog(target: ScanTarget.Report, onDismiss: () -> Unit) {
    var report by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        val text = withContext(Dispatchers.IO) { get("${target.base}/report.txt?t=${target.token}") }
        if (text == null) failed = true else report = text
    }
    val loaded = report
    if (loaded != null) {
        CrashReportDialog(loaded, onDismiss = onDismiss, title = stringResource(R.string.report_title), sendText = { loaded.take(7_500) })
        return
    }
    DialogFrame(
        title = stringResource(R.string.report_title),
        onDismiss = onDismiss,
        buttons = { ActionButton(stringResource(R.string.action_close), onDismiss, primary = false) },
    ) {
        Text(stringResource(if (failed) R.string.report_failed else R.string.report_loading), color = Palette.TextSecondary, fontSize = 15.sp)
    }
}

private fun post(url: String, body: String): Boolean = runCatching {
    val c = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 4_000
        readTimeout = 4_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
    }
    try {
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        c.responseCode in 200..299
    } finally {
        c.disconnect()
    }
}.getOrDefault(false)

private fun get(url: String): String? = runCatching {
    val c = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 4_000
        readTimeout = 8_000
    }
    try {
        if (c.responseCode !in 200..299) null else c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    } finally {
        c.disconnect()
    }
}.getOrNull()
