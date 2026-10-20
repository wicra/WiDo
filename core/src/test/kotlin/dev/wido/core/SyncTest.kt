package dev.wido.core

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncLedgerTest {
    private fun v(s: String) = JsonPrimitive(s)

    @Test fun stampRecordsNewChangedAndRemovedKeys() {
        val a = SyncLedger().stamp(mapOf("x" to v("1"), "y" to v("2")), now = 100)
        assertEquals(setOf("x", "y"), a.live.keys)
        val b = a.stamp(mapOf("x" to v("1b")), now = 200)
        assertEquals(mapOf("x" to v("1b")), b.live)
        assertEquals(200, b.records["x"]!!.t)
        assertTrue(b.records["y"]!!.deleted)
        assertEquals(200, b.records["y"]!!.t)
    }

    @Test fun unchangedStateKeepsLedger() {
        val a = SyncLedger().stamp(mapOf("x" to v("1")), now = 100)
        val b = a.stamp(mapOf("x" to v("1")), now = 500)
        assertTrue(a === b)
    }

    @Test fun timeNeverGoesBackwardsForAKey() {
        val a = SyncLedger().stamp(mapOf("x" to v("1")), now = 1000)
        val b = a.stamp(mapOf("x" to v("2")), now = 10) // horloge en retard
        assertEquals(1001, b.records["x"]!!.t)
    }

    @Test fun mergeIsLastWriterWinsAndCommutative() {
        val phone = SyncLedger().stamp(mapOf("x" to v("phone"), "y" to v("p")), now = 100)
        val tv = SyncLedger().stamp(mapOf("x" to v("tv"), "z" to v("t")), now = 200)
        val m1 = phone.merge(tv)
        val m2 = tv.merge(phone)
        assertEquals(m1, m2)
        assertEquals(v("tv"), m1.live["x"])
        assertEquals(setOf("x", "y", "z"), m1.live.keys)
    }

    @Test fun deletionBeatsOlderValue() {
        val base = SyncLedger().stamp(mapOf("x" to v("1")), now = 100)
        val deleted = base.stamp(emptyMap(), now = 300)
        val stale = SyncLedger().stamp(mapOf("x" to v("1-old")), now = 200)
        assertFalse("x" in deleted.merge(stale).live)
        assertFalse("x" in stale.merge(deleted).live)
    }

    @Test fun equalTimesAreBrokenDeterministically() {
        val a = SyncLedger(mapOf("x" to SyncRecord("x", v("a"), 5)))
        val b = SyncLedger(mapOf("x" to SyncRecord("x", v("b"), 5)))
        assertEquals(a.merge(b), b.merge(a))
    }

    @Test fun scopeLimitsDeletions() {
        val a = SyncLedger().stamp(mapOf("w:1" to v("1"), "s:1" to v("s")), now = 100)
        val b = a.stamp(mapOf("w:1" to v("1")), now = 200) { it.startsWith("w:") }
        assertEquals(setOf("w:1", "s:1"), b.live.keys)
    }

    @Test fun pruneDropsOldTombstonesOnly() {
        val a = SyncLedger().stamp(mapOf("x" to v("1"), "y" to v("2")), now = 0).stamp(mapOf("x" to v("1")), now = 10)
        val pruned = a.prune(now = SyncLedger.TOMBSTONE_MS + 100)
        assertEquals(setOf("x"), pruned.records.keys)
    }

    @Test fun encodeDecodeRoundTrip() {
        val a = SyncLedger().stamp(mapOf("x" to v("1"), "y" to v("2")), now = 7).stamp(mapOf("x" to v("1")), now = 9)
        assertEquals(a, SyncLedger.decode(a.encode()))
        assertEquals(a.epoch, SyncLedger.decode(a.encode()).epoch)
        assertEquals(a.seq, SyncLedger.decode(a.encode()).seq)
        assertEquals(SyncLedger(), SyncLedger.decode("pas du json"))
    }

    @Test fun deltaSendsOnlyWhatArrivedAfterASequence() {
        val a = SyncLedger().stamp(mapOf("x" to v("1")), now = 10)
        val seenUpTo = a.seq
        val b = a.stamp(mapOf("x" to v("1"), "y" to v("2")), now = 20)
        assertEquals(listOf("y"), b.delta(seenUpTo, b.epoch).records.map { it.k })
        assertEquals(20, b.latest)
        // Registre inconnu (autre epoch) : tout.
        assertEquals(setOf("x", "y"), b.delta(seenUpTo, "autre").records.map { it.k }.toSet())
    }

    @Test fun mergedRecordsGetNewSequenceNumbersForThirdScreens() {
        val tv1 = SyncLedger().stamp(mapOf("x" to v("1")), now = 10)
        val phone = SyncLedger().stamp(mapOf("y" to v("2")), now = 5)
        val seen = tv1.seq
        // Le téléphone apporte « y » à la TV 1 : une autre TV qui avait tout vu jusque-là doit le recevoir.
        val merged = tv1.merge(phone.records.values)
        assertEquals(listOf("y"), merged.delta(seen, merged.epoch).records.map { it.k })
        // Rien de neuf : pas de nouveau numéro.
        assertTrue(merged.merge(phone.records.values) === merged)
    }

    @Test fun legacyFileIsRead() {
        val legacy = "[{\"k\":\"x\",\"v\":\"1\",\"t\":3}]"
        val ledger = SyncLedger.decode(legacy)
        assertEquals(setOf("x"), ledger.live.keys)
        assertEquals(1, ledger.seq)
    }
}

class SyncDataTest {
    private val site = LocalSites.build("Arte", "https://www.arte.tv", emptySet()).let { (it as LocalSites.Built.Ok).site }
    private val watch = WatchEntry("arte", "https://www.youtube.com/watch?v=abcdefghijk", "Vidéo", null, 120.0, 600.0, 1000)
    private val pin = Pin("arte", "Concerts", "https://www.arte.tv/fr/concerts/")

    @Test fun libraryRoundTripsThroughLedger() {
        val lib = SyncData.Library(listOf(watch), listOf(site), listOf(pin), mapOf("arte" to listOf(".popup")))
        val ledger = SyncLedger().stamp(SyncData.toMap(lib), now = 1)
        val back = SyncData.fromLedger(ledger)
        assertEquals(lib.sites, back.sites)
        assertEquals(lib.pins, back.pins)
        assertEquals(lib.hides, back.hides)
        assertEquals(listOf(watch.url), back.watching.map { it.url })
    }

    @Test fun finishedVideoOnOneScreenDisappearsEverywhere() {
        val lib = SyncData.Library(watching = listOf(watch))
        val phone = SyncLedger().stamp(SyncData.toMap(lib), now = 100)
        val tv = phone // déjà synchronisés
        // Fini sur la TV : sorti de Reprendre.
        val tvAfter = tv.stamp(SyncData.toMap(SyncData.Library()), now = 200)
        // Le téléphone avait encore une position plus ancienne.
        val merged = phone.merge(tvAfter)
        assertTrue(SyncData.fromLedger(merged).watching.isEmpty())
    }

    @Test fun mostRecentPositionWins() {
        val phone = SyncLedger().stamp(SyncData.toMap(SyncData.Library(listOf(watch.copy(position = 300.0)))), now = 300)
        val tv = SyncLedger().stamp(SyncData.toMap(SyncData.Library(listOf(watch.copy(position = 200.0)))), now = 200)
        assertEquals(300.0, SyncData.fromLedger(tv.merge(phone)).watching.single().position)
    }

    @Test fun builtInSitesAreNeverSynced() {
        val builtIn = site.copy(id = "netflix")
        assertTrue(SyncData.toMap(SyncData.Library(sites = listOf(builtIn))).isEmpty())
    }

    @Test fun keepsLocalOrderAndPutsNewItemsFirst() {
        val other = Pin("arte", "Cinéma", "https://www.arte.tv/fr/cinema/")
        val fresh = Pin("arte", "Séries", "https://www.arte.tv/fr/series/")
        val ledger = SyncLedger().stamp(SyncData.toMap(SyncData.Library(pins = listOf(pin, other, fresh))), now = 5)
        val back = SyncData.fromLedger(ledger, SyncData.Library(pins = listOf(other, pin)))
        assertEquals(listOf(fresh, other, pin), back.pins)
    }

    @Test fun mismatchedKeysAreIgnored() {
        val bad = SyncLedger(mapOf("s:perso-x" to SyncRecord("s:perso-x", SyncData.toMap(SyncData.Library(sites = listOf(site))).values.first(), 1)))
        assertTrue(SyncData.fromLedger(bad).sites.isEmpty())
        assertTrue(SyncData.isKnownKey("w:https://x"))
        assertFalse(SyncData.isKnownKey("settings:x"))
    }
}

class LinkProtocolTest {
    private val tv = DeviceInfo("tv1", "Salon", DeviceRole.TV, "0.8.0")
    private val phone = DeviceInfo("ph1", "Pixel", DeviceRole.PHONE)

    @Test fun pairUriRoundTrip() {
        val uri = LinkProtocol.pairUri("192.168.1.20", 8766, "k3y", "Salon TV")
        val t = assertNotNull(LinkProtocol.parsePairUri(uri))
        assertEquals("192.168.1.20", t.host)
        assertEquals(8766, t.port)
        assertEquals("k3y", t.key)
        assertEquals("Salon TV", t.name)
    }

    @Test fun pairUriRejectsForeignHostsAndSchemes() {
        assertNull(LinkProtocol.parsePairUri("https://pair?h=1.2.3.4"))
        assertNull(LinkProtocol.parsePairUri("wido://pair?h=evil.com/x&p=1"))
        assertNull(LinkProtocol.parsePairUri("wido://other?h=1.2.3.4"))
    }

    @Test fun parsesManualAddress() {
        assertEquals("192.168.1.5" to 8766, LinkProtocol.parseAddress("192.168.1.5"))
        assertEquals("192.168.1.5" to 9000, LinkProtocol.parseAddress("http://192.168.1.5:9000/"))
        assertNull(LinkProtocol.parseAddress("192.168.1.5:abc"))
        assertNull(LinkProtocol.parseAddress("1.2.3.4/x"))
    }

    @Test fun txtRoundTrip() {
        val txt = LinkProtocol.txt(tv, "abc").mapValues { it.value.toByteArray() }
        val ad = assertNotNull(LinkProtocol.parseTxt(txt))
        assertEquals("tv1", ad.id)
        assertEquals("Salon", ad.name)
        assertEquals("abc", ad.groupHash)
        assertNull(LinkProtocol.parseTxt(emptyMap()))
    }

    @Test fun groupHashAndTokenDifferAndAreStable() {
        val h = LinkProtocol.groupHash("secret")
        assertEquals(h, LinkProtocol.groupHash("secret"))
        assertEquals(12, h.length)
        assertTrue(LinkProtocol.groupToken("secret") != LinkProtocol.groupToken("autre"))
        assertFalse(LinkProtocol.groupToken("secret").startsWith(h))
    }

    @Test fun peersRoundTripAndTokenLookup() {
        val p = Peer("ph1", "Pixel", DeviceRole.PHONE, token = LinkProtocol.randomToken(), level = LinkLevel.MINE)
        val list = LinkProtocol.decodePeers(LinkProtocol.encodePeers(listOf(p, p.copy(token = "court"))))
        assertEquals(listOf(p), list)
        assertEquals(p, LinkProtocol.peerForToken(list, p.token))
        assertNull(LinkProtocol.peerForToken(list, "x".repeat(32)))
        assertEquals(2, LinkProtocol.upsert(list, p.copy(id = "ph2")).size)
        assertEquals(1, LinkProtocol.upsert(list, p.copy(name = "Nouveau")).size)
    }

    @Test fun nowPlayingExtrapolatesWhilePlaying() {
        val np = NowPlaying("s", url = "https://a.b", position = 10.0, duration = 12.0, playing = true)
        assertEquals(11.0, np.positionAfter(1000))
        assertEquals(12.0, np.positionAfter(10_000))
        assertEquals(10.0, np.copy(playing = false).positionAfter(5000))
    }

    @Test fun seekCommandParses() {
        assertEquals(RemoteCommand.Seek(42.5), RemoteProtocol.parse("""{"cmd":"seek","to":42.5}"""))
        assertNull(RemoteProtocol.parse("""{"cmd":"seek","to":-1}"""))
        assertEquals(RemoteCommand.Key(RemoteKey.DEL), RemoteProtocol.parse("""{"cmd":"key","key":"del"}"""))
    }
}

class PairingDeskTest {
    private var now = 0L
    private val desk = PairingDesk { now }
    private val req = PairRequest(DeviceInfo("ph1", "Pixel", DeviceRole.PHONE), nonce = "n1")

    @Test fun askThenWaitThenApprove() {
        assertIs<PairingDesk.Outcome.AskUser>(desk.submit(req, keyValid = false))
        assertEquals(PairingDesk.Outcome.Waiting, desk.submit(req, keyValid = false))
        desk.decide(req, approved = true, level = LinkLevel.GUEST)
        val a = assertIs<PairingDesk.Outcome.Approved>(desk.submit(req, keyValid = false))
        val again = assertIs<PairingDesk.Outcome.Approved>(desk.submit(req, keyValid = false))
        assertEquals(LinkLevel.GUEST, a.level)
        assertEquals(a.token, again.token)
        assertTrue(desk.pendingRequests().isEmpty())
    }

    @Test fun qrKeyApprovesAsMine() {
        val a = assertIs<PairingDesk.Outcome.Approved>(desk.submit(req, keyValid = true))
        assertEquals(LinkLevel.MINE, a.level)
    }

    @Test fun anotherNonceIsAnotherRequest() {
        desk.submit(req, keyValid = false)
        desk.decide(req, approved = true)
        assertIs<PairingDesk.Outcome.AskUser>(desk.submit(req.copy(nonce = "spoof"), keyValid = false))
    }

    @Test fun deniedAndExpired() {
        desk.submit(req, keyValid = false)
        desk.decide(req, approved = false)
        assertEquals(PairingDesk.Outcome.Denied, desk.submit(req, keyValid = false))
        val other = req.copy(nonce = "n2")
        desk.submit(other, keyValid = false)
        now += PairingDesk.EXPIRY_MS + 1
        assertTrue(desk.pendingRequests().isEmpty())
    }
}

class CanonicalWatchTest {
    @Test fun youtubeVariantsAreOneVideo() {
        val key = "https://www.youtube.com/watch?v=abcdefghijk"
        listOf(
            "https://www.youtube.com/watch?v=abcdefghijk&t=95s",
            "https://m.youtube.com/watch?v=abcdefghijk&si=xyz&pp=1",
            "https://youtu.be/abcdefghijk?t=3",
            "https://www.youtube.com/shorts/abcdefghijk",
            "https://www.youtube.com/watch?list=PL1&v=abcdefghijk&index=2",
        ).forEach { assertEquals(key, ContinueWatching.canonical(it), it) }
    }

    @Test fun otherSitesLoseOnlyNoise() {
        assertEquals("https://site.tv/ep?id=4#x", ContinueWatching.canonical("https://site.tv/ep?utm_source=a&id=4&t=30#x"))
        assertEquals("https://site.tv/ep", ContinueWatching.canonical("https://site.tv/ep?fbclid=1"))
        assertEquals("https://site.tv/ep/5", ContinueWatching.canonical("https://site.tv/ep/5"))
    }

    @Test fun recordMergesTimeVariants() {
        val a = WatchEntry("yt", "https://www.youtube.com/watch?v=abcdefghijk&t=60s", "V", position = 60.0, duration = 600.0)
        val b = a.copy(url = "https://www.youtube.com/watch?v=abcdefghijk&t=200s", position = 200.0)
        val list = ContinueWatching.record(ContinueWatching.record(emptyList(), a), b)
        assertEquals(1, list.size)
        assertEquals(200.0, list.single().position)
        assertTrue(ContinueWatching.remove(list, a.url).isEmpty())
    }

    @Test fun decodeMergesOldDuplicates() {
        val old = listOf(
            WatchEntry("yt", "https://www.youtube.com/watch?v=abcdefghijk&t=200s", "V", position = 200.0, duration = 600.0),
            WatchEntry("yt", "https://www.youtube.com/watch?v=abcdefghijk&t=60s", "V", position = 60.0, duration = 600.0),
        )
        val back = ContinueWatching.decode(ContinueWatching.encode(old))
        assertEquals(1, back.size)
        assertEquals(200.0, back.single().position)
    }
}
