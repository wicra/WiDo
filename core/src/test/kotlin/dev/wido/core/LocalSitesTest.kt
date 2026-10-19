package dev.wido.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalSitesTest {
    private fun site(id: String, url: String = "https://$id.example/") = Site(id = id, name = id, url = url)

    @Test fun remoteWinsOnIdCollisionAndLocalIsAppended() {
        val merged = LocalSites.merge(
            remote = listOf(site("a"), site("perso-x")),
            local = listOf(site("perso-x", "https://evil.example/"), site("perso-y")),
        )
        assertEquals(listOf("a", "perso-x", "perso-y"), merged.map { it.id })
        assertEquals("https://perso-x.example/", merged[1].url)
    }

    @Test fun idsAreSlugsAndUnique() {
        assertEquals("perso-mon-site", LocalSites.newId("Mon Site", emptySet()))
        assertEquals("perso-cine-ete", LocalSites.newId("Ciné été!", emptySet()))
        assertEquals("perso-site", LocalSites.newId("???", emptySet()))
        assertEquals("perso-a-2", LocalSites.newId("a", setOf("perso-a")))
        assertEquals("perso-a-3", LocalSites.newId("a", setOf("perso-a", "perso-a-2")))
    }

    @Test fun urlGetsHttpsWhenSchemeIsMissing() {
        assertEquals("https://exemple.org", LocalSites.normalizeUrl("exemple.org"))
        assertEquals("http://192.168.1.2:8096/", LocalSites.normalizeUrl("http://192.168.1.2:8096/"))
        assertNull(LocalSites.normalizeUrl("deux mots"))
        assertNull(LocalSites.normalizeUrl(""))
    }

    @Test fun buildProducesAValidSite() {
        val built = assertIs<LocalSites.Built.Ok>(LocalSites.build("Mon site", "exemple.org", emptySet()))
        assertEquals("perso-mon-site", built.site.id)
        assertEquals("https://exemple.org", built.site.url)
        assertEquals(listOf("block-popups"), built.site.scripts)
        assertTrue(!built.site.allowInsecure)
    }

    @Test fun httpIsAllowedOnlyAsInsecureSite() {
        val built = assertIs<LocalSites.Built.Ok>(LocalSites.build("NAS", "http://192.168.1.2:8096", emptySet()))
        assertTrue(built.site.allowInsecure)
    }

    @Test fun buildRejectsBadInput() {
        assertIs<LocalSites.Built.Error>(LocalSites.build("", "exemple.org", emptySet()))
        assertIs<LocalSites.Built.Error>(LocalSites.build("x".repeat(41), "exemple.org", emptySet()))
        assertIs<LocalSites.Built.Error>(LocalSites.build("A", "ftp://exemple.org", emptySet()))
        assertIs<LocalSites.Built.Error>(LocalSites.build("A", "pas une url", emptySet()))
    }

    @Test fun editingKeepsTheSameId() {
        val built = assertIs<LocalSites.Built.Ok>(
            LocalSites.build("Nouveau nom", "exemple.org", setOf("perso-ancien"), keepId = "perso-ancien"),
        )
        assertEquals("perso-ancien", built.site.id)
    }

    @Test fun fileRoundTripKeepsSites() {
        val built = assertIs<LocalSites.Built.Ok>(LocalSites.build("Mon site", "exemple.org", emptySet()))
        val back = LocalSites.decode(LocalSites.encode(listOf(built.site)))
        assertEquals(listOf(built.site), back)
    }

    @Test fun decodeDropsInvalidAndGarbage() {
        assertEquals(emptyList(), LocalSites.decode("pas du json"))
        val ok = assertIs<LocalSites.Built.Ok>(LocalSites.build("A", "a.example", emptySet())).site
        val bad = ok.copy(id = "Mauvais Id")
        val notLocal = ok.copy(id = "distant")
        assertEquals(listOf(ok), LocalSites.decode(LocalSites.encode(listOf(bad, notLocal, ok, ok))))
    }
}
