package dev.wido.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/** Un appareil où tourne WiDo. */
@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    /** [DeviceRole.TV] ou [DeviceRole.PHONE]. */
    val role: String,
    val version: String = "",
)

object DeviceRole {
    const val TV = "tv"
    const val PHONE = "phone"
}

/**
 * Le lien entre deux appareils. `mine` : mes écrans, la bibliothèque (Reprendre, sites, épingles) se
 * synchronise. `guest` : écran d'ailleurs (chez un ami, à l'hôtel), télécommande et envoi de vidéos
 * seulement ; rien de personnel n'y est copié.
 */
object LinkLevel {
    const val MINE = "mine"
    const val GUEST = "guest"
    fun of(raw: String?) = if (raw == MINE) MINE else GUEST
}

/** Demande d'appairage d'un téléphone. `nonce` lie les demandes successives ; `key` vient du QR code de la TV. */
@Serializable
data class PairRequest(val device: DeviceInfo, val nonce: String, val key: String? = null)

@Serializable
data class PairReply(
    /** [PairReply.PENDING], [PairReply.APPROVED] ou [PairReply.DENIED]. */
    val status: String,
    val token: String? = null,
    val level: String = LinkLevel.GUEST,
    val tv: DeviceInfo? = null,
) {
    companion object {
        const val PENDING = "pending"
        const val APPROVED = "approved"
        const val DENIED = "denied"
    }
}

/** Ce que la TV affiche en ce moment. */
@Serializable
data class NowPlaying(
    val siteId: String,
    val siteName: String = "",
    val url: String,
    val title: String = "",
    val image: String? = null,
    /** Position en secondes à l'instant [at] (horloge de la TV, en ms). */
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val playing: Boolean = false,
    val at: Long = 0,
    /** Lu dans le lecteur natif plutôt que dans la page. */
    val native: Boolean = false,
    /** Lecteur natif : qualité, audio, sous-titres et vitesse, réglables depuis le téléphone. */
    val options: PlayerOptions? = null,
) {
    /** Position estimée `elapsedMs` millisecondes après la mesure. */
    fun positionAfter(elapsedMs: Long): Double {
        val p = if (playing) position + elapsedMs.coerceAtLeast(0) / 1000.0 else position
        return if (duration > 0) p.coerceAtMost(duration) else p
    }

    val progress: Float get() = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
}

/** Un choix d'un menu du lecteur (une qualité, une piste audio, des sous-titres). */
@Serializable
data class TrackOption(val id: String, val label: String, val selected: Boolean = false)

/**
 * Les réglages du film en cours dans le lecteur natif. Chaque liste n'apparaît que s'il y a vraiment le choix
 * (au moins deux qualités, deux pistes audio, ou des sous-titres) ; `subtitles` commence par « Désactivés ».
 */
@Serializable
data class PlayerOptions(
    val quality: List<TrackOption> = emptyList(),
    val audio: List<TrackOption> = emptyList(),
    val subtitles: List<TrackOption> = emptyList(),
    val speed: Float = 1f,
) {
    val isEmpty: Boolean get() = quality.isEmpty() && audio.isEmpty() && subtitles.isEmpty()

    companion object {
        const val QUALITY = "quality"
        const val AUDIO = "audio"
        const val SUBTITLES = "subtitles"
        const val SPEED = "speed"
        val KINDS = listOf(QUALITY, AUDIO, SUBTITLES, SPEED)

        /** Identifiant de « qualité automatique » et de « sous-titres désactivés ». */
        const val AUTO = "auto"
        const val OFF = "off"

        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

        /** « 1× », « 1,25× » (virgule française), « 0.75× » ailleurs. */
        fun speedLabel(speed: Float, decimalComma: Boolean): String {
            val text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().trimEnd('0')
            return (if (decimalComma) text.replace('.', ',') else text) + "×"
        }

        /** Les qualités proposées pour ces hauteurs d'image (px) : Auto, puis de la meilleure à la plus légère. */
        fun qualities(heights: Collection<Int>, maxHeight: Int, selected: Int?, autoLabel: String): List<TrackOption> {
            val list = heights.filter { it in 1..maxHeight }.distinct().sortedDescending()
            if (list.size < 2) return emptyList()
            return listOf(TrackOption(AUTO, autoLabel, selected == null)) +
                list.map { h -> TrackOption(h.toString(), "${h}p", selected == h) }
        }
    }
}

@Serializable
data class LinkStatus(
    val tv: DeviceInfo,
    /** Le niveau que la TV accorde à ce téléphone. */
    val level: String = LinkLevel.GUEST,
    val playing: NowPlaying? = null,
    /** Heure de la TV au moment de la réponse (ms), pour estimer la position. */
    val now: Long = 0,
)

/** « Sur la TV » : la TV ouvre cette page, à cette position. */
@Serializable
data class HandoffPlay(val url: String, val siteId: String? = null, val title: String? = null, val position: Double = 0.0)

/** Clé commune de « mes écrans » : les TV qui la partagent se synchronisent entre elles. */
@Serializable
data class GroupKey(val id: String, val secret: String)

@Serializable
data class SyncEnvelope(
    val from: DeviceInfo,
    /** Ce que l'autre écran n'a pas encore de moi. */
    val records: List<SyncRecord> = emptyList(),
    val group: GroupKey? = null,
    /** Ce que j'ai déjà reçu de lui : son dernier numéro vu (-1 : rien) et l'identité de son registre. */
    val since: Long = -1,
    val epoch: String = "",
)

@Serializable
data class SyncReply(
    val accepted: Boolean,
    val records: List<SyncRecord> = emptyList(),
    val group: GroupKey? = null,
    /** Numéro actuel et identité du registre qui répond : à renvoyer la fois suivante. */
    val seq: Long = 0,
    val epoch: String = "",
)

/**
 * Où en est la synchro avec un autre écran : ce que j'ai reçu de lui (`theirEpoch`, `theirSeq`) et ce que
 * je lui ai envoyé de mon registre (`myEpoch`, `sentSeq`). Seule la différence voyage.
 */
@Serializable
data class SyncCursor(val theirEpoch: String = "", val theirSeq: Long = -1, val myEpoch: String = "", val sentSeq: Long = 0)

/** Un appareil appairé, vu depuis l'autre. */
@Serializable
data class Peer(
    val id: String,
    val name: String,
    val role: String,
    val host: String = "",
    val port: Int = LinkProtocol.PORT,
    /** Jeton à présenter (côté téléphone) ou à attendre (côté TV). */
    val token: String,
    val level: String = LinkLevel.GUEST,
    val lastSeen: Long = 0,
    /** Synchro incrémentale avec cet écran (côté téléphone). */
    val cursor: SyncCursor = SyncCursor(),
)

/**
 * Protocole entre les applis WiDo d'un même réseau : découverte (`_wido._tcp`), appairage validé sur la
 * TV ou par QR code, statut, envoi d'une vidéo d'un écran à l'autre, synchronisation.
 */
object LinkProtocol {
    /**
     * Le nom d'un appareil tel que la TV l'affiche : sans caractères de contrôle, espaces réduits, 40 signes au
     * plus. Un nom trop long ou illisible ne peut pas dissimuler qui demande la liaison.
     */
    fun cleanName(raw: String): String =
        raw.filterNot { Character.isISOControl(it) }.replace(Regex("\\s+"), " ").trim().take(40).ifBlank { "Appareil inconnu" }

    const val SERVICE_TYPE = "_wido._tcp."
    const val PORT = 8766
    const val MAX_BODY = 512 * 1024
    const val SCHEME = "wido"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    private val random = SecureRandom()

    fun <T> encode(serializer: kotlinx.serialization.KSerializer<T>, value: T): String = json.encodeToString(serializer, value)

    fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, text: String): T? =
        runCatching { json.decodeFromString(serializer, text) }.getOrNull()

    fun randomToken(bytes: Int = 16): String {
        val b = ByteArray(bytes).also(random::nextBytes)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Empreinte publiée dans la découverte : les TV d'un même groupe se reconnaissent sans dévoiler la clé. */
    fun groupHash(secret: String) = sha256("wido-group-id:$secret").take(12)

    /** Jeton qu'une TV présente à une autre TV du même groupe. */
    fun groupToken(secret: String) = sha256("wido-group-token:$secret").take(32)

    // --- Découverte : enregistrement TXT du service ---

    fun txt(device: DeviceInfo, groupHash: String?): Map<String, String> = buildMap {
        put("id", device.id)
        put("n", device.name.take(60))
        put("r", device.role)
        put("v", device.version)
        groupHash?.let { put("g", it) }
    }

    data class Advert(val id: String, val name: String, val role: String, val version: String, val groupHash: String?)

    fun parseTxt(attrs: Map<String, ByteArray?>): Advert? {
        fun s(k: String) = attrs[k]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
        val id = s("id") ?: return null
        return Advert(id, s("n") ?: id, s("r") ?: DeviceRole.TV, s("v").orEmpty(), s("g"))
    }

    // --- QR code d'appairage : wido://pair?h=…&p=…&k=…&n=… ---

    data class PairTarget(val host: String, val port: Int, val key: String?, val name: String?)

    fun pairUri(host: String, port: Int, key: String, name: String): String {
        fun e(s: String) = URLEncoder.encode(s, "UTF-8")
        return "$SCHEME://pair?h=${e(host)}&p=$port&k=${e(key)}&n=${e(name)}"
    }

    fun parsePairUri(raw: String): PairTarget? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME || uri.host != "pair") return null
        val q = uri.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val host = q["h"]?.takeIf { isLanHost(it) } ?: return null
        val port = q["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: PORT
        return PairTarget(host, port, q["k"]?.takeIf { it.isNotBlank() }, q["n"])
    }

    /** Adresse saisie à la main (« 192.168.1.20 » ou « 192.168.1.20:8766 »). */
    fun parseAddress(raw: String): Pair<String, Int>? {
        val t = raw.trim().removePrefix("http://").trimEnd('/')
        val host = t.substringBefore(':')
        val port = t.substringAfter(':', "").ifEmpty { "$PORT" }.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return if (isLanHost(host)) host to port else null
    }

    /** Une adresse IPv4 ou un nom simple : jamais d'URL, de chemin ni d'espace. */
    fun isLanHost(host: String): Boolean {
        if (host.isEmpty() || host.length > 253) return false
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true && p.isNotEmpty() }) return true
        return Hosts.isValidHostname(host)
    }

    // --- Listes d'appareils appairés ---

    fun encodePeers(peers: List<Peer>): String = json.encodeToString(ListSerializer(Peer.serializer()), peers)

    fun decodePeers(text: String): List<Peer> =
        runCatching { json.decodeFromString(ListSerializer(Peer.serializer()), text) }.getOrDefault(emptyList())
            .filter { it.id.isNotBlank() && it.token.length >= 16 }
            .distinctBy { it.id }

    /**
     * Le groupe de « mes écrans » à garder quand deux appareils en ont un différent (deux téléphones
     * de la maison) : le même choix des deux côtés, pour converger vers un seul groupe.
     */
    fun pickGroup(mine: GroupKey?, theirs: GroupKey?): GroupKey? = when {
        mine == null -> theirs
        theirs == null -> mine
        else -> if (theirs.id < mine.id) theirs else mine
    }

    fun upsert(peers: List<Peer>, peer: Peer): List<Peer> = listOf(peer) + peers.filterNot { it.id == peer.id }

    /** Le pair dont le jeton correspond (comparaison en temps constant). */
    fun peerForToken(peers: List<Peer>, token: String?): Peer? =
        if (token == null) null else peers.firstOrNull { PhoneProtocol.tokenMatches(it.token, token) }
}

/**
 * Le guichet d'appairage de la TV : les demandes en attente, validées à l'écran ou d'office si elles
 * portent la clé du QR code (qui suppose d'être devant la TV). Une demande expire au bout de 2 minutes.
 */
class PairingDesk(private val clock: () -> Long = System::currentTimeMillis) {
    /** `source` : l'adresse de l'appareil qui demande (affichée sur la TV pour le reconnaître). */
    data class Pending(val request: PairRequest, val since: Long, val source: String = "")

    sealed interface Outcome {
        /** À montrer à l'écran (première fois qu'on voit cette demande). */
        data class AskUser(val pending: Pending) : Outcome
        data object Waiting : Outcome
        /** `token` : le jeton remis au téléphone, le même à chaque redemande. */
        data class Approved(val level: String, val token: String) : Outcome
        data object Denied : Outcome
    }

    private val pending = LinkedHashMap<String, Pending>()
    private data class Decision(val approved: Boolean, val level: String, val token: String)
    private val decided = LinkedHashMap<String, Decision>() // appareil/nonce → décision (les plus anciennes partent d'abord)
    private val refusedAt = HashMap<String, Long>() // adresse → dernier refus
    private val promptTimes = ArrayDeque<Long>() // demandes nouvelles montrées à l'écran (toutes adresses)

    /** Une demande arrive (ou revient, le téléphone redemande tant que c'est en attente). */
    @Synchronized
    fun submit(request: PairRequest, keyValid: Boolean, source: String = ""): Outcome {
        expire()
        val id = request.device.id + "/" + request.nonce
        decided[id]?.let { d -> return if (d.approved) Outcome.Approved(d.level, d.token) else Outcome.Denied }
        if (keyValid) {
            val d = Decision(true, LinkLevel.MINE, LinkProtocol.randomToken())
            decided[id] = d
            pending.remove(id)
            return Outcome.Approved(d.level, d.token)
        }
        val known = pending[id]
        if (known != null) return Outcome.Waiting
        val now = clock()
        // Garde-fous contre un appareil du réseau qui ferait défiler les fenêtres de confirmation sur la TV.
        if (source.isNotEmpty()) {
            refusedAt[source]?.let { if (now - it < REFUSAL_COOLDOWN_MS) return Outcome.Denied }
            if (pending.values.count { it.source == source } >= MAX_PER_SOURCE) return Outcome.Denied
        }
        if (pending.size >= MAX_PENDING) return Outcome.Denied
        while (promptTimes.isNotEmpty() && now - promptTimes.first() > MINUTE_MS) promptTimes.removeFirst()
        if (promptTimes.size >= MAX_PROMPTS_PER_MINUTE) return Outcome.Denied
        promptTimes.addLast(now)
        val p = Pending(request, now, source)
        pending[id] = p
        return Outcome.AskUser(p)
    }

    @Synchronized
    fun decide(request: PairRequest, approved: Boolean, level: String = LinkLevel.GUEST) {
        val id = request.device.id + "/" + request.nonce
        val refused = pending.remove(id)
        // Refusé : cette adresse ne peut plus redemander avant un moment.
        if (!approved && refused != null && refused.source.isNotEmpty()) refusedAt[refused.source] = clock()
        decided.remove(id)
        decided[id] = Decision(approved, LinkLevel.of(level), LinkProtocol.randomToken())
    }

    @Synchronized
    fun pendingRequests(): List<Pending> {
        expire()
        return pending.values.toList()
    }

    private fun expire() {
        val now = clock()
        pending.values.removeAll { now - it.since > EXPIRY_MS }
        // Les décisions récentes (jeton à remettre au téléphone) sont gardées ; seules les plus anciennes partent.
        while (decided.size > 50) decided.remove(decided.keys.first())
        refusedAt.values.removeAll { now - it > REFUSAL_COOLDOWN_MS }
    }

    companion object {
        const val EXPIRY_MS = 120_000L
        const val MAX_PENDING = 4
        /** Demandes en attente par adresse. */
        const val MAX_PER_SOURCE = 2
        /** Après un refus, l'adresse ne peut plus faire apparaître de demande pendant 5 minutes. */
        const val REFUSAL_COOLDOWN_MS = 300_000L
        /** Nouvelles demandes montrées à l'écran, toutes adresses confondues, par minute. */
        const val MAX_PROMPTS_PER_MINUTE = 4
        private const val MINUTE_MS = 60_000L
    }
}
