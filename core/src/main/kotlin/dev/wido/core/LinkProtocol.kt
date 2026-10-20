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
) {
    /** Position estimée `elapsedMs` millisecondes après la mesure. */
    fun positionAfter(elapsedMs: Long): Double {
        val p = if (playing) position + elapsedMs.coerceAtLeast(0) / 1000.0 else position
        return if (duration > 0) p.coerceAtMost(duration) else p
    }

    val progress: Float get() = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
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
data class SyncEnvelope(val from: DeviceInfo, val records: List<SyncRecord> = emptyList(), val group: GroupKey? = null)

@Serializable
data class SyncReply(val accepted: Boolean, val records: List<SyncRecord> = emptyList(), val group: GroupKey? = null)

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
)

/**
 * Protocole entre les applis WiDo d'un même réseau : découverte (`_wido._tcp`), appairage validé sur la
 * TV ou par QR code, statut, envoi d'une vidéo d'un écran à l'autre, synchronisation.
 */
object LinkProtocol {
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
    data class Pending(val request: PairRequest, val since: Long)

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
    private val decided = HashMap<String, Decision>() // appareil/nonce → décision

    /** Une demande arrive (ou revient, le téléphone redemande tant que c'est en attente). */
    @Synchronized
    fun submit(request: PairRequest, keyValid: Boolean): Outcome {
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
        if (pending.size >= MAX_PENDING) return Outcome.Denied
        val p = Pending(request, clock())
        pending[id] = p
        return Outcome.AskUser(p)
    }

    @Synchronized
    fun decide(request: PairRequest, approved: Boolean, level: String = LinkLevel.GUEST) {
        val id = request.device.id + "/" + request.nonce
        pending.remove(id)
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
        if (decided.size > 50) decided.clear()
    }

    companion object {
        const val EXPIRY_MS = 120_000L
        const val MAX_PENDING = 4
    }
}
