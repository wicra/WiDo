package dev.wido.core

import java.net.URI

/** Ce qu'un QR code scanné dans l'appli du téléphone demande. */
sealed interface ScanTarget {
    /** Lier ce téléphone à la TV (`wido://pair…`). */
    data class Pair(val target: LinkProtocol.PairTarget) : ScanTarget
    /** Écrire dans le champ sélectionné sur la TV : `base` sans requête, `token` à joindre. */
    data class Keyboard(val base: String, val token: String) : ScanTarget
    /** Le rapport de plantage ou du test complet, servi par la TV. */
    data class Report(val base: String, val token: String) : ScanTarget
    /** Télécommande du navigateur (l'appli a mieux : l'onglet TV). */
    data class Remote(val url: String) : ScanTarget
    /** Un lien ordinaire. */
    data class Web(val url: String) : ScanTarget
}

/**
 * Les QR codes de la TV : des adresses `http://<ip>:<port>/?t=<jeton>` que n'importe quel appareil photo ouvre dans
 * le navigateur. Le paramètre `w` (ignoré par les navigateurs et par la TV) dit à l'appli WiDo du téléphone ce que
 * c'est, pour qu'elle le traite elle-même, sans navigateur.
 */
object ScanCodes {
    const val KEYBOARD = "kbd"
    const val REPORT = "report"
    const val REMOTE = "remote"

    /** L'adresse du QR code, marquée pour l'appli. */
    fun tag(url: String, kind: String): String = url + (if (url.contains('?')) "&" else "?") + "w=" + kind

    fun parse(raw: String): ScanTarget? {
        val text = raw.trim()
        LinkProtocol.parsePairUri(text)?.let { return ScanTarget.Pair(it) }
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        val q = uri.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
        val token = q["t"]?.takeIf { TOKEN.matches(it) }
        val local = scheme == "http" && LinkProtocol.isLanHost(host) && token != null && uri.rawPath.orEmpty().let { it.isEmpty() || it == "/" }
        if (local) {
            val base = "http://$host" + (if (uri.port > 0) ":${uri.port}" else "")
            when (q["w"]) {
                KEYBOARD -> return ScanTarget.Keyboard(base, token!!)
                REPORT -> return ScanTarget.Report(base, token!!)
                REMOTE -> return ScanTarget.Remote(text)
            }
        }
        return if (Hosts.of(text) != null) ScanTarget.Web(text) else null
    }

    private val TOKEN = Regex("^[A-Za-z0-9_-]{8,128}$")
}
