package dev.wido.tv

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/** État du lecteur vidéo d'une page. */
data class MediaState(val paused: Boolean, val time: Double, val duration: Double, val volume: Float)

fun parseMediaState(json: String?): MediaState? {
    if (json.isNullOrBlank() || json == "null") return null
    return runCatching {
        val o = JSONObject(json)
        MediaState(
            paused = o.optBoolean("paused", true),
            time = o.optDouble("t", 0.0),
            duration = o.optDouble("d", 0.0),
            volume = o.optDouble("vol", 1.0).toFloat(),
        )
    }.getOrNull()
}

/**
 * Passerelle entre l'app et les scripts de WiDo injectés dans **toutes** les frames d'une page
 * (y compris les iframes d'un autre domaine, où se trouve souvent le lecteur vidéo).
 * Chaque frame s'annonce ; l'app envoie ses commandes à toutes, et celle qui a une vidéo répond.
 */
class FrameBridge(
    private val onMedia: (MediaState) -> Unit,
    /** Un passage a été sauté par la page (`what` : `sponsor`). */
    private val onSkip: (String) -> Unit = {},
    /** Un champ texte de la page a reçu (vrai) ou perdu (faux) le focus. */
    private val onField: (Boolean) -> Unit = {},
) : WebViewCompat.WebMessageListener {
    private val proxies = CopyOnWriteArrayList<JavaScriptReplyProxy>()

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (replyProxy !in proxies) proxies += replyProxy
        val data = message.data ?: return
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
        when (json.optString("type")) {
            "media" -> parseMediaState(json.optJSONObject("s")?.toString())?.let(onMedia)
            "skip" -> onSkip(json.optString("what"))
            "field" -> onField(json.optBoolean("on"))
        }
    }

    fun send(json: String) {
        for (proxy in proxies) {
            runCatching { proxy.postMessage(json) }.onFailure { proxies.remove(proxy) }
        }
    }

    /** À appeler à chaque nouvelle page : les frames précédentes n'existent plus. */
    fun reset() = proxies.clear()
}
