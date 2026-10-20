package dev.wido.core

import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Une bibliothèque en mémoire : un registre, et ce que chaque échange a fait voyager. */
private class MemoryStore : LinkStore {
    @Volatile var ledger = SyncLedger()
    override var peers: List<Peer> = emptyList()
    override var group: GroupKey? = null
    override var pairKey: String = LinkProtocol.randomToken(8)
    val received = CopyOnWriteArrayList<Int>()
    val sent = CopyOnWriteArrayList<Int>()

    override fun rotatePairKey() {
        pairKey = LinkProtocol.randomToken(8)
    }

    @Synchronized
    fun set(key: String, value: String?) {
        val current = ledger.live.toMutableMap()
        if (value == null) current.remove(key) else current[key] = JsonPrimitive(value)
        ledger = ledger.stamp(current, System.currentTimeMillis())
    }

    fun value(key: String): String? = (ledger.live[key] as? JsonPrimitive)?.content

    @Synchronized
    override fun outgoing(since: Long, epoch: String): SyncDelta = ledger.delta(since, epoch).also { sent += it.records.size }

    @Synchronized
    override fun apply(records: List<SyncRecord>) {
        received += records.size
        ledger = ledger.merge(records)
    }

    @Synchronized
    override fun exchange(incoming: List<SyncRecord>, since: Long, epoch: String): SyncDelta {
        received += incoming.size
        val out = ledger.delta(since, epoch)
        ledger = ledger.merge(incoming)
        sent += out.records.size
        return out.copy(seq = ledger.seq)
    }
}

/** Une TV : son serveur local (vrai HTTP sur la machine de test) et ce qu'elle a reçu comme ordres. */
private class FakeTv(name: String) : LinkHostEvents {
    val store = MemoryStore()
    val info = DeviceInfo(LinkProtocol.randomToken(4), name, DeviceRole.TV, "test")
    val plays = CopyOnWriteArrayList<HandoffPlay>()
    val commands = CopyOnWriteArrayList<RemoteCommand>()
    @Volatile var takes = 0
    @Volatile var pending: List<PairingDesk.Pending> = emptyList()
    @Volatile var playing: NowPlaying? = null
    val host = LinkHost({ info }, store, this)
    private val server = MiniHttpServer("tv-$name", LinkProtocol.MAX_BODY) { r, b -> host.handle(r, b) ?: MiniHttpServer.NOT_FOUND }
    val port: Int = server.start(0)!!

    override fun onPairRequests(pending: List<PairingDesk.Pending>) {
        this.pending = pending
    }

    override fun onPlay(play: HandoffPlay) {
        plays += play
    }

    override fun onTake() {
        takes++
    }

    override fun onCommand(command: RemoteCommand) {
        commands += command
    }

    override fun nowPlaying() = playing

    fun stop() = server.stop()
}

class LinkEndToEndTest {
    private val tv = FakeTv("Salon")
    private val tv2 = FakeTv("Chambre")
    private val phoneInfo = DeviceInfo("phone1", "Pixel", DeviceRole.PHONE)
    private val phone = LinkGuest({ phoneInfo })
    private val phoneStore = MemoryStore()

    @AfterTest fun stop() {
        tv.stop()
        tv2.stop()
    }

    /** Liaison par QR code : acceptée d'office, en « Mon téléphone ». */
    private fun pairWithQr(target: FakeTv): Peer {
        val result = phone.pair("127.0.0.1", target.port, target.store.pairKey, pollMs = 50)
        return assertIs<LinkGuest.PairResult.Approved>(result).peer
    }

    @Test fun qrPairingIsImmediateMineAndSingleUse() {
        val key = tv.store.pairKey
        val peer = pairWithQr(tv)
        assertEquals(LinkLevel.MINE, peer.level)
        assertEquals("Salon", peer.name)
        assertNotEquals(key, tv.store.pairKey)
        assertEquals(listOf("phone1"), tv.store.peers.map { it.id })
    }

    @Test fun pairingWithoutQrWaitsForTheTv() {
        var result: LinkGuest.PairResult? = null
        val t = thread { result = phone.pair("127.0.0.1", tv.port, null, pollMs = 50) }
        val deadline = System.currentTimeMillis() + 5_000
        while (tv.pending.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val request = tv.pending.single()
        assertEquals("Pixel", request.request.device.name)
        tv.host.decide(request, approved = true, level = LinkLevel.GUEST)
        t.join(5_000)
        val peer = assertIs<LinkGuest.PairResult.Approved>(result).peer
        assertEquals(LinkLevel.GUEST, peer.level)
    }

    @Test fun refusedPairing() {
        var result: LinkGuest.PairResult? = null
        val t = thread { result = phone.pair("127.0.0.1", tv.port, null, pollMs = 50) }
        val deadline = System.currentTimeMillis() + 5_000
        while (tv.pending.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        tv.host.decide(tv.pending.single(), approved = false, level = LinkLevel.GUEST)
        t.join(5_000)
        assertEquals(LinkGuest.PairResult.Denied, result)
    }

    @Test fun unreachableTv() {
        val dead = FakeTv("Éteinte").also { it.stop() }
        assertEquals(LinkGuest.PairResult.Unreachable, phone.pair("127.0.0.1", dead.port, null, pollMs = 50))
    }

    @Test fun statusPlayTakeAndCommands() {
        val peer = pairWithQr(tv)
        assertNull(phone.status(peer)!!.playing)
        tv.playing = NowPlaying("yt", "YouTube", "https://www.youtube.com/watch?v=abcdefghijk", "Film", position = 100.0, duration = 600.0, playing = true, at = System.currentTimeMillis())
        val status = assertNotNull(phone.status(peer))
        assertEquals("Film", status.playing!!.title)
        assertTrue(status.playing!!.position >= 100.0)

        assertTrue(phone.play(peer, HandoffPlay("https://www.arte.tv/fr/videos/1/", position = 42.0)))
        assertEquals(42.0, tv.plays.single().position)
        assertFalse(phone.play(peer, HandoffPlay("javascript:alert(1)")))

        val taken = assertNotNull(phone.take(peer))
        assertEquals("Film", taken.playing!!.title)
        assertEquals(1, tv.takes)

        assertTrue(phone.command(peer, """{"cmd":"key","key":"ok"}"""))
        assertEquals(RemoteCommand.Key(RemoteKey.OK), tv.commands.single())
    }

    @Test fun nothingWithoutAValidToken() {
        val peer = pairWithQr(tv)
        val stranger = peer.copy(token = LinkProtocol.randomToken())
        assertNull(phone.status(stranger))
        assertFalse(phone.play(stranger, HandoffPlay("https://a.fr/")))
        assertFalse(phone.command(stranger, """{"cmd":"key","key":"ok"}"""))
        assertNull(LinkHttp.call("127.0.0.1", tv.port, "GET", "/status"))
        assertNotNull(LinkHttp.call("127.0.0.1", tv.port, "GET", "/hello"))
    }

    @Test fun syncBothWaysThenOnlyChanges() {
        val peer = pairWithQr(tv)
        phoneStore.set("w:a", "phone")
        tv.store.set("w:b", "tv")

        val first = assertNotNull(phone.sync("127.0.0.1", tv.port, peer.token, phoneStore, peer.cursor))
        assertTrue(first.reply.accepted)
        assertEquals("tv", phoneStore.value("w:b"))
        assertEquals("phone", tv.store.value("w:a"))

        // Rien n'a changé : rien ne voyage.
        val sentBefore = tv.store.sent.size
        val second = assertNotNull(phone.sync("127.0.0.1", tv.port, peer.token, phoneStore, first.cursor))
        assertEquals(0, tv.store.sent.drop(sentBefore).sum())

        // Une modification sur la TV : elle seule revient.
        tv.store.set("w:b", "tv-2")
        val third = assertNotNull(phone.sync("127.0.0.1", tv.port, peer.token, phoneStore, second.cursor))
        assertEquals(1, third.reply.records.size)
        assertEquals("tv-2", phoneStore.value("w:b"))
    }

    @Test fun deletionTravels() {
        val peer = pairWithQr(tv)
        phoneStore.set("w:a", "x")
        val c1 = phone.sync("127.0.0.1", tv.port, peer.token, phoneStore, peer.cursor)!!.cursor
        assertEquals("x", tv.store.value("w:a"))
        phoneStore.set("w:a", null) // vidéo finie
        phone.sync("127.0.0.1", tv.port, peer.token, phoneStore, c1)
        assertNull(tv.store.value("w:a"))
    }

    @Test fun guestTvRefusesSync() {
        var result: LinkGuest.PairResult? = null
        val t = thread { result = phone.pair("127.0.0.1", tv.port, null, pollMs = 50) }
        val deadline = System.currentTimeMillis() + 5_000
        while (tv.pending.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        tv.host.decide(tv.pending.single(), approved = true, level = LinkLevel.GUEST)
        t.join(5_000)
        val peer = assertIs<LinkGuest.PairResult.Approved>(result).peer
        phoneStore.set("w:secret", "perso")
        val reply = phone.sync("127.0.0.1", tv.port, peer.token, phoneStore, peer.cursor)!!.reply
        assertFalse(reply.accepted)
        assertNull(tv.store.value("w:secret"))
    }

    @Test fun myTvsSyncBetweenThemselvesWithTheGroupKey() {
        val toSalon = pairWithQr(tv)
        val toChambre = pairWithQr(tv2)
        // Le téléphone apporte la même clé aux deux TV.
        val r1 = phone.sync("127.0.0.1", tv.port, toSalon.token, phoneStore, toSalon.cursor)!!
        val group = assertNotNull(r1.reply.group)
        phone.sync("127.0.0.1", tv2.port, toChambre.token, phoneStore, toChambre.cursor, group)
        assertEquals(tv.store.group, tv2.store.group)

        // Une vidéo regardée sur la TV du salon arrive dans la chambre, sans le téléphone.
        tv.store.set("w:film", "salon")
        val salonAsGuest = LinkGuest({ tv.info })
        val token = LinkProtocol.groupToken(group.secret)
        val r = assertNotNull(salonAsGuest.sync("127.0.0.1", tv2.port, token, tv.store, SyncCursor()))
        assertTrue(r.reply.accepted)
        assertEquals("salon", tv2.store.value("w:film"))

        // Une TV d'un autre groupe est refusée.
        assertFalse(salonAsGuest.sync("127.0.0.1", tv2.port, LinkProtocol.groupToken("autre"), tv.store, SyncCursor())!!.reply.accepted)
    }

    @Test fun reinstalledTvGetsEverythingAgain() {
        val peer = pairWithQr(tv)
        phoneStore.set("w:a", "1")
        val c1 = phone.sync("127.0.0.1", tv.port, peer.token, phoneStore, peer.cursor)!!.cursor
        // La TV perd sa bibliothèque (nouveau registre).
        tv.store.ledger = SyncLedger()
        val c2 = phone.sync("127.0.0.1", tv.port, peer.token, phoneStore, c1)!!.cursor
        assertEquals(0, c2.sentSeq)
        phone.sync("127.0.0.1", tv.port, peer.token, phoneStore, c2)
        assertEquals("1", tv.store.value("w:a"))
    }

    @Test fun parallelRequestsAreServed() {
        val peer = pairWithQr(tv)
        val threads = (1..8).map { thread { repeat(5) { assertNotNull(phone.status(peer)) } } }
        threads.forEach { it.join(10_000) }
    }
}
