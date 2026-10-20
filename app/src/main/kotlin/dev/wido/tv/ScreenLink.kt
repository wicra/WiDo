package dev.wido.tv

import android.content.Intent
import android.os.Handler
import android.os.Looper
import dev.wido.core.DeviceInfo
import dev.wido.core.DeviceRole
import dev.wido.core.GroupKey
import dev.wido.core.HandoffPlay
import dev.wido.core.LinkLevel
import dev.wido.core.LinkProtocol
import dev.wido.core.NowPlaying
import dev.wido.core.PairingDesk
import dev.wido.core.Peer
import dev.wido.core.PhoneProtocol
import dev.wido.core.RemoteCommand
import dev.wido.core.RemoteKey
import dev.wido.core.LinkGuest
import dev.wido.core.LinkHost
import dev.wido.core.LinkHostEvents
import dev.wido.core.LinkStore
import dev.wido.core.MiniHttpServer
import dev.wido.core.SyncCursor
import dev.wido.core.SyncRecord
import kotlinx.coroutines.withContext
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

    /** Demandes d'appairage à valider à l'écran (la plus ancienne en tête). */
    val pairRequests = MutableStateFlow<List<PairingDesk.Pending>>(emptyList())

    /** L'écran actif (accueil, lecteur), qui reçoit les ordres des téléphones. */
    @Volatile private var target: () -> RemoteTarget? = { null }

    /** Bibliothèque et réglages du lien, vus par la logique commune (module core). */
    private val store = object : LinkStore {
        override var peers: List<Peer>
            get() = _peers.value
            set(value) = updatePeers { value }
        override var group: GroupKey?
            get() = this@ScreenLink.group
            set(value) {
                this@ScreenLink.group = value
            }
        override val pairKey: String get() = settings.pairKey
        override fun rotatePairKey() {
            settings.newPairKey()
        }
        override fun outgoing(since: Long, epoch: String) = app.repository.syncOutgoing(since, epoch)
        override fun apply(records: List<SyncRecord>) {
            app.repository.applySync(records)
        }
        override fun exchange(incoming: List<SyncRecord>, since: Long, epoch: String) = app.repository.syncExchange(incoming, since, epoch)
    }

    private val host = LinkHost({ device }, store, object : LinkHostEvents {
        override fun onPairRequests(pending: List<PairingDesk.Pending>) {
            pairRequests.value = pending
        }
        override fun onPlay(play: HandoffPlay) {
            main.post { openOnTv(play, target()) }
        }
        override fun onTake() {
            main.post { target()?.onRemote(RemoteCommand.Key(RemoteKey.PAUSE)) }
        }
        override fun onCommand(command: RemoteCommand) {
            main.post { target()?.onRemote(command) }
        }
        override fun onGroupChanged() {
            main.post { app.remote.readvertise() }
        }
        override fun nowPlaying(): NowPlaying? = this@ScreenLink.nowPlaying.value
    })

    /** Un téléphone suit la lecture : les lecteurs relèvent la position plus souvent. */
    val followed: Boolean get() = System.currentTimeMillis() - host.lastStatusAt < FOLLOW_MS

    fun decidePair(pending: PairingDesk.Pending, approved: Boolean, level: String) = host.decide(pending, approved, level)

    /** Retire les demandes expirées (le téléphone a cessé d'attendre) : la fenêtre se ferme. */
    fun refreshPairRequests() {
        pairRequests.value = host.pending()
    }

    fun peerForToken(token: String?): Peer? = host.peerForToken(token)

    /** Les routes du lien ; null si la requête n'en est pas une. Appelé hors du fil principal. */
    fun handle(request: PhoneProtocol.Request, body: String, target: () -> RemoteTarget?): MiniHttpServer.Response? {
        this.target = target
        return host.handle(request, body)
    }

    /** « Sur la TV » : la page s'ouvre (le site du menu qui la connaît, sinon un site éphémère), à la position donnée. */
    private fun openOnTv(play: HandoffPlay, current: RemoteTarget?) {
        (current as? android.app.Activity)?.takeIf { it !is MainActivity }?.finish()
        val intent = PlayerActivity.intent(app, ConfigRepository.LINK_SITE_ID, play.url, play.position)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
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

    private val guest = LinkGuest({ device })

    /**
     * Se lie à la TV `host:port`. Sans `key` (QR code), la TV demande confirmation à l'écran : on
     * redemande jusqu'à la réponse (2 minutes au plus).
     */
    suspend fun pair(host: String, port: Int, key: String?): LinkGuest.PairResult {
        val result = withContext(Dispatchers.IO) {
            val job = coroutineContext[Job]
            guest.pair(host, port, key, PAIR_POLL_MS) { job?.isActive == false }
        }
        if (result is LinkGuest.PairResult.Approved) {
            updatePeers { LinkProtocol.upsert(it, result.peer) }
            selectScreen(result.peer.id)
            refresh(result.peer)
            syncSoon(immediate = true)
        }
        return result
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
        val status = withContext(Dispatchers.IO) { guest.status(peer) }
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
        val ok = withContext(Dispatchers.IO) { guest.play(peer, play) }
        if (ok) {
            delay(1_500)
            refresh(peer)
        }
        return ok
    }

    /** « Reprendre sur le téléphone » : la TV se met en pause et dit où elle en était. */
    suspend fun take(peer: Peer): NowPlaying? {
        val status = withContext(Dispatchers.IO) { guest.take(peer) } ?: return null
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

    /** TV → TV : où en est la synchro avec chaque TV du groupe (en mémoire : au redémarrage, une synchro complète). */
    private val groupCursors = HashMap<String, SyncCursor>()

    suspend fun syncNow() = syncMutex.withLock {
        lastSync = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            if (isTv) {
                val g = group ?: return@withContext
                val token = LinkProtocol.groupToken(g.secret)
                for (tv in groupPeers()) {
                    val result = guest.sync(tv.host, tv.port, token, store, groupCursors[tv.advert.id] ?: SyncCursor())
                    if (result != null && result.reply.accepted) groupCursors[tv.advert.id] = result.cursor
                }
            } else {
                for (peer in _peers.value.filter { it.level == LinkLevel.MINE && it.role == DeviceRole.TV }) {
                    val result = guest.sync(peer.host, peer.port, peer.token, store, peer.cursor, group) ?: continue
                    setState(peer.id) { it.copy(online = true, syncRefused = !result.reply.accepted) }
                    if (result.reply.accepted) {
                        updatePeers { list -> list.map { if (it.id == peer.id) it.copy(cursor = result.cursor) else it } }
                        adoptGroup(result.reply.group)
                    }
                }
            }
        }
    }

    init {
        // Les commandes partent dans l'ordre, une à une (touches, texte, déplacements du pavé).
        scope.launch {
            for ((peer, json) in commands) guest.command(peer, json)
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
