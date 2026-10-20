package dev.wido.tv

import android.content.Intent
import android.os.Handler
import android.os.Looper
import dev.wido.core.DeviceInfo
import dev.wido.core.DeviceRole
import dev.wido.core.GroupKey
import dev.wido.core.HandoffPlay
import dev.wido.core.Hosts
import dev.wido.core.LinkLevel
import dev.wido.core.LinkProtocol
import dev.wido.core.LinkStatus
import dev.wido.core.NowPlaying
import dev.wido.core.PairReply
import dev.wido.core.PairRequest
import dev.wido.core.PairingDesk
import dev.wido.core.Peer
import dev.wido.core.PhoneProtocol
import dev.wido.core.RemoteCommand
import dev.wido.core.RemoteKey
import dev.wido.core.SyncEnvelope
import dev.wido.core.SyncReply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Le lien entre les applis WiDo d'un même réseau.
 *
 * - Sur la TV : répond aux téléphones (appairage validé à l'écran ou par QR code, statut de lecture,
 *   « Sur la TV », « Reprendre sur le téléphone », télécommande) et se synchronise avec les autres TV
 *   de « mes écrans ».
 * - Sur le téléphone : trouve les TV, s'y lie, pilote celle choisie, et synchronise la bibliothèque
 *   (Reprendre, sites perso, épingles, éléments masqués) avec celles marquées « Mon écran ».
 *
 * Rien ne passe par Internet : sans le même réseau, chaque écran vit sa vie, et se resynchronise au
 * retour.
 */
class ScreenLink(private val app: WiDoApp) {
    val isTv: Boolean = DeviceKind.isTv(app)
    private val settings get() = app.settings
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val discovery = LinkDiscovery(app) { settings.deviceId }

    val device: DeviceInfo
        get() = DeviceInfo(settings.deviceId, settings.deviceName, if (isTv) DeviceRole.TV else DeviceRole.PHONE, BuildConfig.VERSION_NAME)

    private val _peers = MutableStateFlow(LinkProtocol.decodePeers(settings.peersJson))

    /** Appareils liés : les téléphones (sur la TV), les TV (sur le téléphone). */
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    @Synchronized
    private fun updatePeers(transform: (List<Peer>) -> List<Peer>) {
        val next = transform(_peers.value)
        if (next == _peers.value) return
        _peers.value = next
        settings.peersJson = LinkProtocol.encodePeers(next)
    }

    fun forget(id: String) {
        updatePeers { list -> list.filterNot { it.id == id } }
        if (settings.selectedScreen == id) selectScreen(_peers.value.firstOrNull()?.id)
    }

    fun setLevel(id: String, level: String) {
        updatePeers { list -> list.map { if (it.id == id) it.copy(level = LinkLevel.of(level)) else it } }
        if (level == LinkLevel.MINE) syncSoon(immediate = true)
    }

    private var group: GroupKey?
        get() = settings.groupJson?.let { LinkProtocol.decode(GroupKey.serializer(), it) }
        set(v) {
            settings.groupJson = v?.let { LinkProtocol.encode(GroupKey.serializer(), it) }
        }

    /** Empreinte du groupe publiée dans la découverte (TV). */
    val groupHash: String? get() = group?.let { LinkProtocol.groupHash(it.secret) }

    private fun adoptGroup(theirs: GroupKey?) {
        val mine = group
        val next = LinkProtocol.pickGroup(mine, theirs)
        if (next != mine) {
            group = next
            if (isTv) main.post { app.remote.readvertise() }
        }
    }

    // =============================================================================================
    // TV : routes du serveur
    // =============================================================================================

    /** Ce que la TV affiche en ce moment (mis à jour par les lecteurs). */
    val nowPlaying = MutableStateFlow<NowPlaying?>(null)

    private val desk = PairingDesk()

    /** Demandes d'appairage à valider à l'écran (la plus ancienne en tête). */
    val pairRequests = MutableStateFlow<List<PairingDesk.Pending>>(emptyList())

    @Volatile private var lastStatusAt = 0L

    /** Un téléphone suit la lecture : les lecteurs relèvent la position plus souvent. */
    val followed: Boolean get() = System.currentTimeMillis() - lastStatusAt < FOLLOW_MS

    fun decidePair(pending: PairingDesk.Pending, approved: Boolean, level: String) {
        desk.decide(pending.request, approved, level)
        pairRequests.value = desk.pendingRequests()
    }

    fun peerForToken(token: String?): Peer? = LinkProtocol.peerForToken(_peers.value, token)

    /** Les routes du lien ; null si la requête n'en est pas une. Appelé hors du fil principal. */
    fun handle(request: PhoneProtocol.Request, body: String, target: () -> RemoteTarget?): MiniHttpServer.Response? {
        val token = request.query["t"]
        val post = request.method == "POST"
        return when (request.path) {
            "/hello" -> MiniHttpServer.json(LinkProtocol.encode(DeviceInfo.serializer(), device))
            "/pair" -> if (post) pair(body) else MiniHttpServer.BAD_REQUEST
            "/status" -> {
                val peer = peerForToken(token) ?: return MiniHttpServer.FORBIDDEN
                lastStatusAt = System.currentTimeMillis()
                touch(peer)
                MiniHttpServer.json(LinkProtocol.encode(LinkStatus.serializer(), statusFor(peer)))
            }
            "/play" -> {
                val peer = peerForToken(token) ?: return MiniHttpServer.FORBIDDEN
                val play = LinkProtocol.decode(HandoffPlay.serializer(), body)?.takeIf { validUrl(it.url) }
                    ?: return MiniHttpServer.BAD_REQUEST
                touch(peer)
                main.post { openOnTv(play, target()) }
                MiniHttpServer.json("{}")
            }
            "/take" -> {
                val peer = peerForToken(token) ?: return MiniHttpServer.FORBIDDEN
                val status = statusFor(peer)
                if (status.playing != null) main.post { target()?.onRemote(RemoteCommand.Key(RemoteKey.PAUSE)) }
                MiniHttpServer.json(LinkProtocol.encode(LinkStatus.serializer(), status))
            }
            "/sync" -> if (post) receiveSync(token, body) else MiniHttpServer.BAD_REQUEST
            else -> null
        }
    }

    private fun statusFor(peer: Peer): LinkStatus {
        val now = System.currentTimeMillis()
        val playing = nowPlaying.value?.let { it.copy(position = it.positionAfter(now - it.at), at = now) }
        return LinkStatus(device, peer.level, playing, now)
    }

    private fun touch(peer: Peer) {
        val now = System.currentTimeMillis()
        if (now - peer.lastSeen > 60_000) updatePeers { list -> list.map { if (it.id == peer.id) it.copy(lastSeen = now) else it } }
    }

    private fun pair(body: String): MiniHttpServer.Response {
        val request = LinkProtocol.decode(PairRequest.serializer(), body)
            ?.takeIf { it.device.id.isNotBlank() && it.device.id.length <= 64 && it.nonce.length in 8..64 }
            ?: return MiniHttpServer.BAD_REQUEST
        val keyValid = request.key != null && PhoneProtocol.tokenMatches(settings.pairKey, request.key)
        val reply = when (val outcome = desk.submit(request, keyValid)) {
            is PairingDesk.Outcome.AskUser -> {
                pairRequests.value = desk.pendingRequests()
                PairReply(PairReply.PENDING)
            }
            PairingDesk.Outcome.Waiting -> PairReply(PairReply.PENDING)
            PairingDesk.Outcome.Denied -> PairReply(PairReply.DENIED)
            is PairingDesk.Outcome.Approved -> {
                if (keyValid) settings.newPairKey() // un QR code ne sert qu'une fois
                val d = request.device
                updatePeers { list ->
                    LinkProtocol.upsert(list, Peer(d.id, d.name.take(40), d.role, token = outcome.token, level = outcome.level, lastSeen = System.currentTimeMillis()))
                }
                PairReply(PairReply.APPROVED, outcome.token, outcome.level, device)
            }
        }
        return MiniHttpServer.json(LinkProtocol.encode(PairReply.serializer(), reply))
    }

    private fun receiveSync(token: String?, body: String): MiniHttpServer.Response {
        val envelope = LinkProtocol.decode(SyncEnvelope.serializer(), body) ?: return MiniHttpServer.BAD_REQUEST
        val peer = peerForToken(token)
        val g = group
        val fromGroup = g != null && token != null && PhoneProtocol.tokenMatches(LinkProtocol.groupToken(g.secret), token)
        val allowed = (peer != null && peer.level == LinkLevel.MINE) || fromGroup
        if (!allowed) return MiniHttpServer.json(LinkProtocol.encode(SyncReply.serializer(), SyncReply(false)))
        if (peer != null) {
            touch(peer)
            // Un téléphone « à moi » apporte la clé du groupe (ou en reçoit une) : mes TV se trouveront entre elles.
            adoptGroup(envelope.group ?: group ?: GroupKey(LinkProtocol.randomToken(8), LinkProtocol.randomToken(16)))
        }
        app.repository.applySync(envelope.records)
        val reply = SyncReply(true, app.repository.syncRecords(), if (peer != null) group else null)
        return MiniHttpServer.json(LinkProtocol.encode(SyncReply.serializer(), reply))
    }

    /** « Sur la TV » : la page s'ouvre (le site du menu qui la connaît, sinon un site éphémère), à la position donnée. */
    private fun openOnTv(play: HandoffPlay, current: RemoteTarget?) {
        (current as? android.app.Activity)?.takeIf { it !is MainActivity }?.finish()
        val intent = PlayerActivity.intent(app, ConfigRepository.LINK_SITE_ID, play.url, play.position)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    private fun validUrl(url: String): Boolean {
        val scheme = Hosts.schemeOf(url)
        return url.length <= 2000 && (scheme == "https" || scheme == "http") && Hosts.of(url) != null
    }

    // =============================================================================================
    // Téléphone : écrans liés, pilotage
    // =============================================================================================

    data class ScreenState(
        val online: Boolean = false,
        val status: LinkStatus? = null,
        /** Heure locale de réception du statut, pour estimer la position. */
        val receivedAt: Long = 0,
        /** La synchro a été refusée par la TV (elle me voit en invité). */
        val syncRefused: Boolean = false,
    )

    private val _states = MutableStateFlow<Map<String, ScreenState>>(emptyMap())
    val states: StateFlow<Map<String, ScreenState>> = _states.asStateFlow()

    private val _selected = MutableStateFlow(settings.selectedScreen)

    /** La TV pilotée par le téléphone (identifiant). */
    val selected: StateFlow<String?> = _selected.asStateFlow()

    fun selectScreen(id: String?) {
        settings.selectedScreen = id
        _selected.value = id
    }

    fun selectedPeer(): Peer? = _peers.value.firstOrNull { it.id == _selected.value } ?: _peers.value.firstOrNull()

    private fun setState(id: String, transform: (ScreenState) -> ScreenState) {
        val map = _states.value
        _states.value = map + (id to transform(map[id] ?: ScreenState()))
    }

    sealed interface PairResult {
        data class Approved(val peer: Peer) : PairResult
        data object Denied : PairResult
        data object Unreachable : PairResult
        data object TimedOut : PairResult
    }

    /**
     * Se lie à la TV `host:port`. Sans `key` (QR code), la TV demande confirmation à l'écran : on
     * redemande jusqu'à la réponse (2 minutes au plus).
     */
    suspend fun pair(host: String, port: Int, key: String?): PairResult {
        val hello = LinkClient.call(host, port, "GET", "/hello")?.let { LinkProtocol.decode(DeviceInfo.serializer(), it) }
            ?: return PairResult.Unreachable
        val request = PairRequest(device, LinkProtocol.randomToken(8), key)
        val body = LinkProtocol.encode(PairRequest.serializer(), request)
        val deadline = System.currentTimeMillis() + PairingDesk.EXPIRY_MS
        while (System.currentTimeMillis() < deadline) {
            val reply = LinkClient.call(host, port, "POST", "/pair", body = body)?.let { LinkProtocol.decode(PairReply.serializer(), it) }
            when (reply?.status) {
                PairReply.APPROVED -> {
                    val token = reply.token ?: return PairResult.Denied
                    val tv = reply.tv ?: hello
                    val peer = Peer(tv.id, tv.name, tv.role, host, port, token, LinkLevel.of(reply.level), System.currentTimeMillis())
                    updatePeers { LinkProtocol.upsert(it, peer) }
                    selectScreen(peer.id)
                    refresh(peer)
                    syncSoon(immediate = true)
                    return PairResult.Approved(peer)
                }
                PairReply.DENIED -> return PairResult.Denied
                null -> return PairResult.Unreachable
            }
            delay(PAIR_POLL_MS)
        }
        return PairResult.TimedOut
    }

    /** L'adresse d'un écran lié a pu changer (box redémarrée) : la découverte la remet à jour. */
    private fun followAddresses() {
        val found = discovery.found.value
        if (found.isEmpty()) return
        updatePeers { list ->
            list.map { peer ->
                val f = found.firstOrNull { it.advert.id == peer.id } ?: return@map peer
                if (f.host == peer.host && f.port == peer.port) peer else peer.copy(host = f.host, port = f.port)
            }
        }
    }

    /** Demande son statut à un écran lié. */
    suspend fun refresh(peer: Peer): ScreenState {
        val raw = LinkClient.call(peer.host, peer.port, "GET", "/status", peer.token, timeoutMs = 2_500)
        val status = raw?.let { LinkProtocol.decode(LinkStatus.serializer(), it) }
        setState(peer.id) { it.copy(online = status != null, status = status ?: it.status, receivedAt = if (status != null) System.currentTimeMillis() else it.receivedAt) }
        if (status != null && status.tv.name != peer.name) updatePeers { list -> list.map { if (it.id == peer.id) it.copy(name = status.tv.name) else it } }
        return _states.value[peer.id] ?: ScreenState()
    }

    private var polling: Job? = null

    /** Téléphone au premier plan : découverte, statut de la TV choisie (toutes les 2 s), des autres (toutes les 15 s). */
    fun startPolling() {
        if (isTv || polling?.isActive == true) return
        main.post { discovery.start() }
        polling = scope.launch {
            var tick = 0
            syncSoon(immediate = true)
            while (isActive) {
                followAddresses()
                val chosen = selectedPeer()
                chosen?.let { refresh(it) }
                if (tick % 8 == 0) _peers.value.filter { it.id != chosen?.id }.forEach { refresh(it) }
                // Ce que la TV a regardé entre-temps arrive dans Reprendre sans relancer l'appli.
                if (tick > 0 && tick % 15 == 0) syncSoon(immediate = true)
                tick++
                delay(STATUS_POLL_MS)
            }
        }
    }

    fun stopPolling() {
        polling?.cancel()
        polling = null
        main.post { discovery.stop() }
    }

    /** « Sur la TV » : ouvre cette page sur l'écran choisi. */
    suspend fun play(peer: Peer, play: HandoffPlay): Boolean {
        val ok = LinkClient.call(peer.host, peer.port, "POST", "/play", peer.token, LinkProtocol.encode(HandoffPlay.serializer(), play)) != null
        if (ok) {
            delay(1_500)
            refresh(peer)
        }
        return ok
    }

    /** « Reprendre sur le téléphone » : la TV se met en pause et dit où elle en était. */
    suspend fun take(peer: Peer): NowPlaying? {
        val raw = LinkClient.call(peer.host, peer.port, "POST", "/take", peer.token) ?: return null
        val status = LinkProtocol.decode(LinkStatus.serializer(), raw) ?: return null
        setState(peer.id) { it.copy(online = true, status = status, receivedAt = System.currentTimeMillis()) }
        return status.playing
    }

    /** L'onglet TV est affiché : les touches de volume du téléphone règlent la TV. */
    @Volatile var volumeToTv = false

    /** La TV choisie répond : les touches de volume peuvent lui être envoyées. */
    val selectedOnline: Boolean get() = selectedPeer()?.let { _states.value[it.id]?.online } == true

    private val commands = Channel<Pair<Peer, String>>(Channel.BUFFERED)

    /** Télécommande : envoie une commande (`{"cmd":"key","key":"ok"}`…) à l'écran choisi. */
    fun command(json: JSONObject) {
        val peer = selectedPeer() ?: return
        commands.trySend(peer to json.toString())
    }

    fun key(key: RemoteKey) = command(JSONObject().put("cmd", "key").put("key", key.name.lowercase()))

    // =============================================================================================
    // Synchronisation
    // =============================================================================================

    private val syncMutex = Mutex()
    private var pendingSync: Job? = null
    @Volatile private var lastSync = 0L

    /** Synchronise bientôt (regroupe les modifications rapprochées), ou tout de suite. */
    fun syncSoon(immediate: Boolean = false) {
        if (!immediate && pendingSync?.isActive == true) return // déjà prévue : elle emportera ce changement
        pendingSync?.cancel()
        val wait = if (immediate) 0 else maxOf(SYNC_DELAY_MS, lastSync + MIN_SYNC_GAP_MS - System.currentTimeMillis())
        pendingSync = scope.launch {
            delay(wait)
            syncNow()
        }
    }

    private fun groupPeers(): List<LinkDiscovery.Found> {
        val hash = groupHash ?: return emptyList()
        return discovery.found.value.filter { it.advert.role == DeviceRole.TV && it.advert.groupHash == hash }
    }

    suspend fun syncNow() = syncMutex.withLock {
        lastSync = System.currentTimeMillis()
        if (isTv) {
            val g = group ?: return@withLock
            val token = LinkProtocol.groupToken(g.secret)
            for (tv in groupPeers()) exchange(tv.host, tv.port, token, null)
        } else {
            for (peer in _peers.value.filter { it.level == LinkLevel.MINE && it.role == DeviceRole.TV }) {
                val reply = exchange(peer.host, peer.port, peer.token, group)
                if (reply != null) {
                    setState(peer.id) { it.copy(online = true, syncRefused = !reply.accepted) }
                    if (reply.accepted) adoptGroup(reply.group)
                }
            }
        }
    }

    private suspend fun exchange(host: String, port: Int, token: String, group: GroupKey?): SyncReply? {
        val envelope = SyncEnvelope(device, app.repository.syncRecords(), group)
        val raw = LinkClient.call(host, port, "POST", "/sync", token, LinkProtocol.encode(SyncEnvelope.serializer(), envelope), timeoutMs = 8_000)
        val reply = raw?.let { LinkProtocol.decode(SyncReply.serializer(), it) } ?: return null
        if (reply.accepted) app.repository.applySync(reply.records)
        return reply
    }

    init {
        // Les commandes partent dans l'ordre, une à une (touches, texte, déplacements du pavé).
        scope.launch {
            for ((peer, json) in commands) LinkClient.call(peer.host, peer.port, "POST", "/cmd", peer.token, json, timeoutMs = 2_000)
        }
        // Toute modification de la bibliothèque part vers mes écrans (regroupée, au plus toutes les 30 s).
        scope.launch { app.repository.syncVersion.drop(1).collect { syncSoon() } }
        // TV : une TV de mon groupe apparaît sur le réseau → synchro.
        if (isTv) scope.launch { discovery.found.collect { if (groupPeers().isNotEmpty()) syncSoon() } }
    }

    private companion object {
        const val FOLLOW_MS = 20_000L
        const val PAIR_POLL_MS = 1_500L
        const val STATUS_POLL_MS = 2_000L
        const val SYNC_DELAY_MS = 4_000L
        const val MIN_SYNC_GAP_MS = 30_000L
    }
}
