package dev.wido.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

/** Reprendre un site là où on l'avait laissé. */
object ResumeUrl {
    private val YOUTUBE_WATCH = Regex("^https://(www\\.|m\\.)?youtube\\.com/watch\\?")

    /**
     * L'adresse à retenir pour `site` en quittant `url`, ou null si elle ne lui appartient pas (autre
     * domaine, page d'erreur). Sur une vidéo YouTube, la position de lecture est ajoutée (`t=`).
     */
    fun toRemember(site: Site, url: String?, positionSeconds: Double? = null): String? {
        if (url.isNullOrBlank() || url.length > 2000) return null
        val scheme = Hosts.schemeOf(url)
        if (scheme != "https" && !(scheme == "http" && site.allowInsecure)) return null
        val host = Hosts.of(url) ?: return null
        if (NavigationPolicy.allowedDomains(site).none { Hosts.matchesPattern(host, it) }) return null
        if (positionSeconds != null && positionSeconds >= 10 && YOUTUBE_WATCH.containsMatchIn(url)) {
            val withoutTime = url.replace(Regex("[&?]t=\\d+s?"), "")
            return "$withoutTime&t=${positionSeconds.toLong()}s"
        }
        return url
    }

    /** L'adresse d'ouverture : celle retenue si elle appartient encore au site, sinon son accueil. */
    fun startUrl(site: Site, remembered: String?): String = toRemember(site, remembered) ?: site.url
}

/** Ordre des cartes choisi par l'utilisateur. Les sites absents de l'ordre gardent leur place relative, à la fin. */
object SiteOrder {
    fun <T> sort(items: List<T>, order: List<String>, id: (T) -> String): List<T> {
        if (order.isEmpty()) return items
        val rank = order.withIndex().associate { it.value to it.index }
        return items.withIndex()
            .sortedWith(compareBy({ rank[id(it.value)] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }

    /** Nouvel ordre après avoir déplacé `moving` de `delta` places (bornes respectées). */
    fun move(currentIds: List<String>, moving: String, delta: Int): List<String> {
        val from = currentIds.indexOf(moving)
        if (from < 0) return currentIds
        val to = (from + delta).coerceIn(0, currentIds.lastIndex)
        if (to == from) return currentIds
        return currentIds.toMutableList().apply { add(to, removeAt(from)) }
    }
}

/** Une page épinglée : une chaîne, une playlist, une émission… ouverte directement depuis l'accueil. */
@Serializable
data class Pin(val siteId: String, val title: String, val url: String)

object Pins {
    const val MAX_PER_SITE = 12
    const val MAX_TITLE = 60

    @Serializable
    private data class FileModel(val pins: List<Pin> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun encode(pins: List<Pin>): String = json.encodeToString(FileModel(pins))

    fun decode(text: String): List<Pin> =
        runCatching { json.decodeFromString<FileModel>(text).pins }.getOrDefault(emptyList()).filter { valid(it) }

    /**
     * Ajoute une page (titre nettoyé, à défaut le chemin de l'adresse). Une page déjà épinglée remonte
     * en tête ; au-delà de [MAX_PER_SITE], la plus ancienne du site est retirée.
     */
    fun add(pins: List<Pin>, siteId: String, title: String?, url: String): List<Pin> {
        val cleanTitle = title?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX_TITLE)?.takeIf { it.isNotEmpty() } ?: fallbackTitle(url)
        val pin = Pin(siteId, cleanTitle, url)
        if (!valid(pin)) return pins
        val others = pins.filterNot { it.siteId == siteId && it.url == url }
        val forSite = listOf(pin) + others.filter { it.siteId == siteId }
        return forSite.take(MAX_PER_SITE) + others.filter { it.siteId != siteId }
    }

    fun remove(pins: List<Pin>, pin: Pin): List<Pin> = pins.filterNot { it.siteId == pin.siteId && it.url == pin.url }

    fun forSite(pins: List<Pin>, siteId: String): List<Pin> = pins.filter { it.siteId == siteId }

    private fun valid(pin: Pin): Boolean {
        val scheme = Hosts.schemeOf(pin.url)
        return pin.siteId.isNotBlank() && pin.title.isNotBlank() && pin.url.length <= 2000 &&
            (scheme == "https" || scheme == "http") && Hosts.of(pin.url) != null
    }

    private fun fallbackTitle(url: String): String {
        val path = runCatching { URI(url).path }.getOrNull().orEmpty().trim('/')
        return (path.substringAfterLast('/').ifEmpty { Hosts.of(url).orEmpty() }).take(MAX_TITLE)
    }
}

/**
 * Éléments masqués à la main (« Masquer un élément » du menu rapide), par site. Gardés à part de la
 * configuration : valables pour les sites perso comme pour ceux de la configuration distante.
 */
object UserHides {
    const val MAX_PER_SITE = 50
    const val MAX_SELECTOR = 300

    @Serializable
    private data class FileModel(val sites: Map<String, List<String>> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun encode(hides: Map<String, List<String>>): String = json.encodeToString(FileModel(hides))

    fun decode(text: String): Map<String, List<String>> =
        runCatching { json.decodeFromString<FileModel>(text).sites }.getOrDefault(emptyMap())
            .mapValues { (_, list) -> list.filter(::valid).distinct().take(MAX_PER_SITE) }
            .filterValues { it.isNotEmpty() }

    fun add(hides: Map<String, List<String>>, siteId: String, selector: String): Map<String, List<String>> {
        val s = selector.trim()
        if (!valid(s)) return hides
        val list = (hides[siteId].orEmpty() - s + s).takeLast(MAX_PER_SITE)
        return hides + (siteId to list)
    }

    fun clear(hides: Map<String, List<String>>, siteId: String): Map<String, List<String>> = hides - siteId

    /** Le site avec ses éléments masqués à la main ajoutés à `hideSelectors`. */
    fun apply(site: Site, hides: Map<String, List<String>>): Site {
        val extra = hides[site.id].orEmpty()
        return if (extra.isEmpty()) site else site.copy(hideSelectors = (site.hideSelectors + extra).distinct())
    }

    private fun valid(s: String) = s.isNotEmpty() && s.length <= MAX_SELECTOR && s.none { it in "{}<>\n" }
}

/** Une vidéo en cours, pour la rangée « Reprendre » de l'accueil. */
@Serializable
data class WatchEntry(
    val siteId: String,
    val url: String,
    val title: String,
    val image: String? = null,
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val updatedAt: Long = 0,
) {
    /** Avancement entre 0 et 1 (0 si la durée est inconnue). */
    val progress: Float get() = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
}

/**
 * « Reprendre » : les dernières vidéos regardées, tous sites confondus. Une vidéo à peine commencée
 * (moins de 10 s) n'y entre pas ; une vidéo finie (moins de 30 s restantes) en sort.
 */
object ContinueWatching {
    const val MAX = 12
    const val MIN_POSITION = 10.0
    const val END_MARGIN = 30.0
    const val MAX_TITLE = 80

    @Serializable
    private data class FileModel(val entries: List<WatchEntry> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun encode(entries: List<WatchEntry>): String = json.encodeToString(FileModel(entries))

    fun decode(text: String): List<WatchEntry> =
        runCatching { json.decodeFromString<FileModel>(text).entries }.getOrDefault(emptyList()).filter { valid(it.url) }.take(MAX)

    /** Met à jour la liste avec la position d'une vidéo (la plus récente en tête). */
    fun record(entries: List<WatchEntry>, entry: WatchEntry): List<WatchEntry> {
        if (!valid(entry.url)) return entries
        val others = entries.filterNot { it.url == entry.url }
        val finished = entry.duration > 0 && entry.position > entry.duration - END_MARGIN
        if (finished) return others
        if (entry.position < MIN_POSITION) return entries
        val title = entry.title.trim().replace(Regex("\\s+"), " ").take(MAX_TITLE).ifEmpty { Hosts.of(entry.url).orEmpty() }
        val image = entry.image?.takeIf { Hosts.schemeOf(it) == "https" } ?: youtubeThumbnail(entry.url)
        return (listOf(entry.copy(title = title, image = image)) + others).take(MAX)
    }

    fun remove(entries: List<WatchEntry>, url: String): List<WatchEntry> = entries.filterNot { it.url == url }

    fun removeSite(entries: List<WatchEntry>, siteId: String): List<WatchEntry> = entries.filterNot { it.siteId == siteId }

    /** Miniature d'une vidéo YouTube, déduite de son identifiant (les pages YouTube n'en donnent pas toujours). */
    fun youtubeThumbnail(url: String): String? {
        val host = Hosts.of(url) ?: return null
        if (!Hosts.matches(host, "youtube.com") && host != "youtu.be") return null
        val id = Regex("[?&]v=([\\w-]{11})").find(url)?.groupValues?.get(1)
            ?: Regex("/(?:shorts|live|embed)/([\\w-]{11})").find(url)?.groupValues?.get(1)
            ?: if (host == "youtu.be") Regex("^https?://youtu\\.be/([\\w-]{11})").find(url)?.groupValues?.get(1) else null
        return id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
    }

    private fun valid(url: String): Boolean {
        val scheme = Hosts.schemeOf(url)
        return url.length <= 2000 && (scheme == "https" || scheme == "http") && Hosts.of(url) != null
    }
}
