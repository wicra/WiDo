package dev.wido.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SuggestNameTest {
    @Test fun nameComesFromTheHost() {
        assertEquals("Exemple", LocalSites.suggestName("https://www.exemple.org/x"))
        assertEquals("Youtube", LocalSites.suggestName("youtube.com"))
        assertEquals("Exemple", LocalSites.suggestName("http://exemple.co.uk"))
        assertEquals("Monsite", LocalSites.suggestName("https://films.monsite.fr"))
    }

    @Test fun ipAddressesAreKeptAndGarbageGivesNothing() {
        assertEquals("192.168.1.20", LocalSites.suggestName("http://192.168.1.20:8096"))
        assertEquals("", LocalSites.suggestName(""))
        assertEquals("", LocalSites.suggestName("https://"))
        assertEquals("", LocalSites.suggestName("localhost"))
    }
}

class OptionsRoundTripTest {
    @Test fun everyToggleSurvivesBuildAndRead() {
        val options = LocalSites.SiteOptions(
            allowedDomains = listOf("cdn.exemple.org"),
            blockedDomains = listOf("pub.exemple.org"),
            relaxed = true,
            blockAds = false,
            blockPopups = false,
            hideCookieBanners = true,
            allowLogins = true,
        )
        val site = assertIs<LocalSites.Built.Ok>(LocalSites.build("S", "exemple.org", emptySet(), options = options)).site
        assertEquals(options, LocalSites.optionsOf(site))
        assertFalse(site.blockAds)
        assertTrue("accounts.google.com" in site.allowedDomains)
        assertEquals(listOf("hide-cookie-banners"), site.scripts)
    }

    @Test fun defaultsAreProtectiveAndEditableBack() {
        val site = assertIs<LocalSites.Built.Ok>(LocalSites.build("S", "exemple.org", emptySet())).site
        val back = LocalSites.optionsOf(site)
        assertTrue(back.blockAds && back.blockPopups)
        assertFalse(back.relaxed || back.allowLogins || back.hideCookieBanners)
    }

    @Test fun loginPresetCanBeSwitchedOffWithoutLosingCustomDomains() {
        val on = DomainPresets.withLogins(listOf("cdn.exemple.org"), true)
        assertTrue(DomainPresets.hasLogins(on))
        assertEquals(listOf("cdn.exemple.org"), DomainPresets.withLogins(on, false))
        assertEquals(listOf("cdn.exemple.org"), DomainPresets.custom(on))
    }
}

class SeenDomainsTest {
    @Test fun mergeKeepsNewestNavigationsAndTopRequests() {
        val first = SeenDomains.merge(SeenDomains.Entry(), listOf("a.example", "b.example"), mapOf("t1.example" to 3, "t2.example" to 1))
        assertEquals(listOf("b.example", "a.example"), first.navigations)
        val second = SeenDomains.merge(first, listOf("c.example", "b.example"), mapOf("t2.example" to 5))
        assertEquals(listOf("b.example", "c.example", "a.example"), second.navigations)
        assertEquals(listOf("t2.example", "t1.example"), second.requests.keys.toList())
        assertEquals(6, second.requests["t2.example"])
    }

    @Test fun mergeIsBounded() {
        val many = (1..50).associate { "h$it.example" to it }
        val e = SeenDomains.merge(SeenDomains.Entry(), (1..30).map { "n$it.example" }, many)
        assertEquals(SeenDomains.MAX_NAVIGATIONS, e.navigations.size)
        assertEquals(SeenDomains.MAX_REQUESTS, e.requests.size)
        assertEquals("h50.example", e.requests.keys.first())
    }

    @Test fun suggestionsExcludeKnownDomainsAndSubdomains() {
        val e = SeenDomains.Entry(listOf("login.exemple.org", "pub.autre.net"), mapOf("cdn.exemple.org" to 9, "tracker.net" to 4))
        val (allow, block) = SeenDomains.suggestions(e, setOf("exemple.org"))
        assertEquals(listOf("pub.autre.net"), allow)
        assertEquals(listOf("tracker.net"), block)
    }

    @Test fun fileRoundTripAndGarbage() {
        val map = mapOf("perso-x" to SeenDomains.Entry(listOf("a.example"), mapOf("t.example" to 2)))
        assertEquals(map, SeenDomains.decode(SeenDomains.encode(map)))
        assertEquals(emptyMap(), SeenDomains.decode("pas du json"))
        assertEquals(emptyMap(), SeenDomains.decode(SeenDomains.encode(mapOf("Bad Id" to SeenDomains.Entry(listOf("a.example"))))))
    }
}
