package dev.wido.core

/** Un flux vidéo repéré dans les requêtes d'une page, lisible par le lecteur natif. */
data class MediaStream(val url: String, val kind: Kind) {
    enum class Kind { HLS, DASH, PROGRESSIVE }
}

/**
 * Repère, parmi les requêtes d'une page, un flux vidéo que le lecteur natif saura lire : manifeste HLS
 * (`.m3u8`), DASH (`.mpd`) ou fichier vidéo (`.mp4`, `.webm`). Les segments (`.ts`, `.m4s`) et les flux
 * de YouTube (assemblés par script, illisibles hors de la page) sont ignorés.
 */
object MediaDetector {
    private val EXCLUDED_HOSTS = listOf("googlevideo.com", "youtube.com", "ytimg.com", "doubleclick.net", "googlesyndication.com")
    private val AD_HINTS = listOf("/ads/", "/ad/", "advert", "preroll", "vast", "/pub/", "imasdk")

    fun detect(url: String): MediaStream? {
        val host = Hosts.of(url) ?: return null
        if (EXCLUDED_HOSTS.any { Hosts.matches(host, it) }) return null
        val scheme = Hosts.schemeOf(url)
        if (scheme != "https" && scheme != "http") return null
        val lower = url.lowercase()
        if (AD_HINTS.any { lower.contains(it) }) return null
        val path = lower.substringBefore('?').substringBefore('#')
        val kind = when {
            path.endsWith(".m3u8") -> MediaStream.Kind.HLS
            path.endsWith(".mpd") -> MediaStream.Kind.DASH
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") -> MediaStream.Kind.PROGRESSIVE
            else -> return null
        }
        return MediaStream(url, kind)
    }

    /**
     * Garde le meilleur flux d'une page : un manifeste plutôt qu'un fichier, et le premier manifeste vu
     * (la playlist principale, avant ses variantes de qualité).
     */
    fun better(current: MediaStream?, found: MediaStream): MediaStream {
        if (current == null) return found
        val currentIsManifest = current.kind != MediaStream.Kind.PROGRESSIVE
        val foundIsManifest = found.kind != MediaStream.Kind.PROGRESSIVE
        return if (!currentIsManifest && foundIsManifest) found else current
    }
}
