package dev.wido.core

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Requêtes vers l'appli WiDo d'un autre écran du réseau local (http, réponses bornées). Bloquant. */
object LinkHttp {
    /** Le corps de la réponse 200, ou null (injoignable, refusé, trop long). */
    fun call(
        host: String,
        port: Int,
        method: String,
        path: String,
        token: String? = null,
        body: String? = null,
        timeoutMs: Int = 4_000,
    ): String? {
        if (!LinkProtocol.isLanHost(host)) return null
        return runCatching {
            val query = if (token != null) (if ('?' in path) "&" else "?") + "t=" + token else ""
            val conn = URL("http://$host:$port$path$query").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.requestMethod = method
                conn.useCaches = false
                if (body != null) {
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.setFixedLengthStreamingMode(bytes.size)
                    conn.outputStream.use { it.write(bytes) }
                }
                if (conn.responseCode != 200) return@runCatching null
                conn.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (out.size() + n > LinkProtocol.MAX_BODY) return@runCatching null
                        out.write(buf, 0, n)
                    }
                    out.toString("UTF-8")
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}

/** La bibliothèque synchronisée d'un écran, vue par le lien. */
interface SyncSource {
    /** Mes valeurs arrivées après `since` (tout si `epoch` n'est pas celui de mon registre). */
    fun outgoing(since: Long, epoch: String): SyncDelta

    /** Fusionne ce qu'envoie un autre écran. */
    fun apply(records: List<SyncRecord>)

    /**
     * Côté TV qui reçoit une synchro, d'un seul tenant : ce que l'autre n'a pas encore (calculé avant la
     * fusion, pour ne pas lui renvoyer ce qu'il vient d'envoyer), puis la fusion ; le numéro rendu est
     * celui d'après la fusion.
     */
    fun exchange(incoming: List<SyncRecord>, since: Long, epoch: String): SyncDelta
}

/** Ce que la TV garde du lien : appareils liés, groupe de « mes écrans », clé du QR code. */
interface LinkStore : SyncSource {
    var peers: List<Peer>
    var group: GroupKey?
    val pairKey: String
    fun rotatePairKey()
}

/** Ce que la TV fait des demandes des autres écrans (sur Android : ouvrir le lecteur, mettre en pause…). */
interface LinkHostEvents {
    fun onPairRequests(pending: List<PairingDesk.Pending>) {}
    fun onPlay(play: HandoffPlay) {}
    fun onTake() {}
    fun onCommand(command: RemoteCommand) {}
    /** Un écran lié propose des sites : la TV demande confirmation à l'écran (rien n'est ajouté sans). */
    fun onShare(peer: Peer, share: SiteShare) {}
    fun onGroupChanged() {}
    fun nowPlaying(): NowPlaying? = null
}

/**
 * La TV dans le lien : les routes de son serveur local, sans rien d'Android (testées de bout en bout).
 *
 * `/hello` (sans jeton), `/pair` (sans jeton, validé à l'écran ou par la clé du QR code), puis avec le
 * jeton d'un appareil lié : `/status`, `/play`, `/take`, `/cmd`, `/share`, `/sync` (ou jeton du groupe pour une TV).
 */
class LinkHost(
    private val device: () -> DeviceInfo,
    private val store: LinkStore,
    private val events: LinkHostEvents,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val desk = PairingDesk(clock)

    @Volatile var lastStatusAt = 0L
        private set

    fun pending(): List<PairingDesk.Pending> = desk.pendingRequests()

    fun decide(pending: PairingDesk.Pending, approved: Boolean, level: String) {
        desk.decide(pending.request, approved, level)
        events.onPairRequests(desk.pendingRequests())
    }

    fun peerForToken(token: String?): Peer? = LinkProtocol.peerForToken(store.peers, token)

    @Synchronized
    private fun updatePeers(transform: (List<Peer>) -> List<Peer>) {
        val next = transform(store.peers)
        if (next != store.peers) store.peers = next
    }

    /** Les routes du lien ; null si la requête n'en est pas une. */
    fun handle(request: PhoneProtocol.Request, body: String): MiniHttpServer.Response? {
        val token = request.query["t"]
        val post = request.method == "POST"
        return when (request.path) {
            "/hello" -> MiniHttpServer.json(LinkProtocol.encode(DeviceInfo.serializer(), device()))
            "/pair" -> if (post) pair(body, request.remote) else MiniHttpServer.BAD_REQUEST
            "/status" -> {
                val peer = peerForToken(token) ?: return MiniHttpServer.FORBIDDEN
                lastStatusAt = clock()
                touch(peer)
                MiniHttpServer.json(LinkProtocol.encode(LinkStatus.serializer(), statusFor(peer)))
            }
            "/play" -> {
                val peer = peerForToken(token) ?: return MiniHttpServer.FORBIDDEN
                if (!post) return MiniHttpServer.BAD_REQUEST
                val play = LinkProtocol.decode(HandoffPlay.serializer(), body)?.takeIf { validUrl(it.url) }
                    ?: return MiniHttpServer.BAD_REQUEST
                touch(peer)
                events.onPlay(play)
                MiniHttpServer.json("{}")
            }
            "/take" -> {
                val peer = peerForToken(token) ?: return MiniHttpServer.FORBIDDEN
                val status = statusFor(peer)
                if (status.playing != null) events.onTake()
                MiniHttpServer.json(LinkProtocol.encode(LinkStatus.serializer(), status))
            }
            "/cmd" -> {
                peerForToken(token) ?: return MiniHttpServer.FORBIDDEN
                if (!post) return MiniHttpServer.BAD_REQUEST
                val command = RemoteProtocol.parse(body) ?: return MiniHttpServer.Response(400, body = "Bad command")
                events.onCommand(command)
                MiniHttpServer.Response(200, body = "ok")
            }
            "/share" -> {
                val peer = peerForToken(token) ?: return MiniHttpServer.FORBIDDEN
                if (!post) return MiniHttpServer.BAD_REQUEST
                val share = LinkProtocol.decode(SiteShare.serializer(), body) ?: return MiniHttpServer.BAD_REQUEST
                val sites = SiteSharing.sanitize(share.sites)
                if (sites.isEmpty()) return MiniHttpServer.BAD_REQUEST
                touch(peer)
                // Le nom affiché est celui que la TV connaît pour cet appareil, pas celui qu'il annonce.
                events.onShare(peer, share.copy(from = share.from.copy(id = peer.id, name = peer.name), sites = sites))
                MiniHttpServer.json("{\"asked\":${sites.size}}")
            }
            "/sync" -> if (post) receiveSync(token, body) else MiniHttpServer.BAD_REQUEST
            else -> null
        }
    }

    private fun statusFor(peer: Peer): LinkStatus {
        val now = clock()
        val playing = events.nowPlaying()?.let { it.copy(position = it.positionAfter(now - it.at), at = now) }
        return LinkStatus(device(), peer.level, playing, now)
    }

    private fun touch(peer: Peer) {
        val now = clock()
        if (now - peer.lastSeen > 60_000) updatePeers { list -> list.map { if (it.id == peer.id) it.copy(lastSeen = now) else it } }
    }

    private fun pair(body: String, remote: String): MiniHttpServer.Response {
        val request = LinkProtocol.decode(PairRequest.serializer(), body)
            ?.takeIf { it.device.id.isNotBlank() && it.device.id.length <= 64 && it.nonce.length in 8..64 }
            ?.let { it.copy(device = it.device.copy(name = LinkProtocol.cleanName(it.device.name))) }
            ?: return MiniHttpServer.BAD_REQUEST
        val keyValid = request.key != null && PhoneProtocol.tokenMatches(store.pairKey, request.key)
        val reply = when (val outcome = desk.submit(request, keyValid, remote)) {
            is PairingDesk.Outcome.AskUser -> {
                events.onPairRequests(desk.pendingRequests())
                PairReply(PairReply.PENDING)
            }
            PairingDesk.Outcome.Waiting -> PairReply(PairReply.PENDING)
            PairingDesk.Outcome.Denied -> PairReply(PairReply.DENIED)
            is PairingDesk.Outcome.Approved -> {
                if (keyValid) store.rotatePairKey() // un QR code ne sert qu'une fois
                val d = request.device
                updatePeers { list ->
                    LinkProtocol.upsert(list, Peer(d.id, d.name.take(40), d.role, token = outcome.token, level = outcome.level, lastSeen = clock()))
                }
                PairReply(PairReply.APPROVED, outcome.token, outcome.level, device())
            }
        }
        return MiniHttpServer.json(LinkProtocol.encode(PairReply.serializer(), reply))
    }

    private fun receiveSync(token: String?, body: String): MiniHttpServer.Response {
        val envelope = LinkProtocol.decode(SyncEnvelope.serializer(), body) ?: return MiniHttpServer.BAD_REQUEST
        val peer = peerForToken(token)
        val g = store.group
        val fromGroup = g != null && token != null && PhoneProtocol.tokenMatches(LinkProtocol.groupToken(g.secret), token)
        val allowed = (peer != null && peer.level == LinkLevel.MINE) || fromGroup
        if (!allowed) return MiniHttpServer.json(LinkProtocol.encode(SyncReply.serializer(), SyncReply(false)))
        if (peer != null) {
            touch(peer)
            // Un téléphone « à moi » apporte la clé du groupe (ou en reçoit une) : mes TV se trouveront entre elles.
            val next = LinkProtocol.pickGroup(store.group, envelope.group ?: store.group ?: GroupKey(LinkProtocol.randomToken(8), LinkProtocol.randomToken(16)))
            if (next != store.group) {
                store.group = next
                events.onGroupChanged()
            }
        }
        val known = envelope.records.filter { SyncData.isKnownKey(it.k) }
        val delta = store.exchange(known, envelope.since, envelope.epoch)
        val reply = SyncReply(true, delta.records, if (peer != null) store.group else null, delta.seq, delta.epoch)
        return MiniHttpServer.json(LinkProtocol.encode(SyncReply.serializer(), reply))
    }

    private fun validUrl(url: String): Boolean {
        val scheme = Hosts.schemeOf(url)
        return url.length <= 2000 && (scheme == "https" || scheme == "http") && Hosts.of(url) != null
    }
}

/**
 * Le côté qui appelle (téléphone, ou TV qui se synchronise avec une autre TV de son groupe). Bloquant :
 * à appeler hors du fil principal.
 */
class LinkGuest(private val device: () -> DeviceInfo, private val http: LinkHttp = LinkHttp) {
    sealed interface PairResult {
        data class Approved(val peer: Peer) : PairResult
        data object Denied : PairResult
        data object Unreachable : PairResult
        data object TimedOut : PairResult
    }

    /**
     * Se lie à la TV `host:port`. Sans `key` (QR code), la TV demande confirmation à l'écran : on
     * redemande jusqu'à la réponse (2 minutes au plus, ou jusqu'à `cancelled`).
     */
    fun pair(host: String, port: Int, key: String?, pollMs: Long = 1_500, cancelled: () -> Boolean = { false }): PairResult {
        val hello = http.call(host, port, "GET", "/hello")?.let { LinkProtocol.decode(DeviceInfo.serializer(), it) }
            ?: return PairResult.Unreachable
        val request = PairRequest(device(), LinkProtocol.randomToken(8), key)
        val body = LinkProtocol.encode(PairRequest.serializer(), request)
        val deadline = System.currentTimeMillis() + PairingDesk.EXPIRY_MS
        while (System.currentTimeMillis() < deadline && !cancelled()) {
            val reply = http.call(host, port, "POST", "/pair", body = body)?.let { LinkProtocol.decode(PairReply.serializer(), it) }
            when (reply?.status) {
                PairReply.APPROVED -> {
                    val token = reply.token ?: return PairResult.Denied
                    val tv = reply.tv ?: hello
                    return PairResult.Approved(Peer(tv.id, tv.name, tv.role, host, port, token, LinkLevel.of(reply.level), System.currentTimeMillis()))
                }
                PairReply.DENIED -> return PairResult.Denied
                null -> return PairResult.Unreachable
            }
            Thread.sleep(pollMs)
        }
        return PairResult.TimedOut
    }

    fun status(peer: Peer, timeoutMs: Int = 2_500): LinkStatus? =
        http.call(peer.host, peer.port, "GET", "/status", peer.token, timeoutMs = timeoutMs)?.let { LinkProtocol.decode(LinkStatus.serializer(), it) }

    fun play(peer: Peer, play: HandoffPlay): Boolean =
        http.call(peer.host, peer.port, "POST", "/play", peer.token, LinkProtocol.encode(HandoffPlay.serializer(), play)) != null

    fun take(peer: Peer): LinkStatus? =
        http.call(peer.host, peer.port, "POST", "/take", peer.token)?.let { LinkProtocol.decode(LinkStatus.serializer(), it) }

    /** Propose des sites à la TV ; elle demande confirmation à l'écran. Vrai si la demande est arrivée. */
    fun share(peer: Peer, sites: List<SharedSite>): Boolean =
        http.call(peer.host, peer.port, "POST", "/share", peer.token, LinkProtocol.encode(SiteShare.serializer(), SiteShare(device(), sites))) != null

    fun command(peer: Peer, json: String): Boolean =
        http.call(peer.host, peer.port, "POST", "/cmd", peer.token, json, timeoutMs = 2_000) != null

    data class SyncResult(val reply: SyncReply, val cursor: SyncCursor)

    /**
     * Une synchro : j'envoie ce que l'autre n'a pas encore de moi, il répond avec ce que je n'ai pas
     * encore de lui. Le curseur rendu est à garder pour la fois suivante.
     */
    fun sync(host: String, port: Int, token: String, source: SyncSource, cursor: SyncCursor, group: GroupKey? = null): SyncResult? {
        val mine = source.outgoing(cursor.sentSeq, cursor.myEpoch)
        val envelope = SyncEnvelope(device(), mine.records, group, cursor.theirSeq, cursor.theirEpoch)
        val raw = http.call(host, port, "POST", "/sync", token, LinkProtocol.encode(SyncEnvelope.serializer(), envelope), timeoutMs = 8_000)
        val reply = raw?.let { LinkProtocol.decode(SyncReply.serializer(), it) } ?: return null
        if (!reply.accepted) return SyncResult(reply, cursor)
        source.apply(reply.records.filter { SyncData.isKnownKey(it.k) })
        // Registre de l'autre recréé (appli réinstallée) : il faudra tout lui renvoyer.
        val theyReset = cursor.theirEpoch.isNotEmpty() && reply.epoch != cursor.theirEpoch
        val next = SyncCursor(reply.epoch, reply.seq, mine.epoch, if (theyReset) 0 else mine.seq)
        return SyncResult(reply, next)
    }
}
