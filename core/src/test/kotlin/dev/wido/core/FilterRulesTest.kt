package dev.wido.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterRulesTest {
    private fun blocker(vararg lines: String) = HostBlocker(FilterParser.parse(lines.asSequence()))

    @Test fun adblockRuleBlocksHostAndSubdomains() {
        val b = blocker("||pub.example.com^")
        assertTrue(b.isBlocked("pub.example.com"))
        assertTrue(b.isBlocked("cdn.pub.example.com"))
        assertFalse(b.isBlocked("example.com"))
        assertFalse(b.isBlocked("notpub.example.com"))
    }

    @Test fun exceptionWinsOverBlock() {
        val b = blocker("||example.com^", "@@||player.example.com^")
        assertTrue(b.isBlocked("example.com"))
        assertFalse(b.isBlocked("player.example.com"))
        assertFalse(b.isBlocked("x.player.example.com"))
    }

    @Test fun hostsFormatAndPlainHosts() {
        val b = blocker("0.0.0.0 ads.tracker.net # commentaire", "127.0.0.1 localhost", "plain.ads.org")
        assertTrue(b.isBlocked("ads.tracker.net"))
        assertTrue(b.isBlocked("plain.ads.org"))
        assertFalse(b.isBlocked("localhost"))
    }

    @Test fun commentsAndUnsupportedRulesAreSkippedAndCounted() {
        val r = FilterParser.parse(
            sequenceOf(
                "! titre", "# note", "[Adblock Plus 2.0]", "", "||ok.com^", "||a.com/path^",
                "||c.com^\$domain=x.com", "/banner/*", "site.com#?#div:has-text(Pub)", "site.com##+js(nowebrtc)",
            ),
        )
        assertEquals(setOf("ok.com"), r.blocked)
        assertEquals(5, r.skipped)
    }

    @Test fun harmlessOptionsStillBlockTheHost() {
        val r = FilterParser.parse(sequenceOf("||b.com^\$third-party", "||c.com^\$script,image,important", "||d.com^\$csp=none"))
        assertEquals(setOf("b.com", "c.com"), r.blocked)
        assertEquals(1, r.skipped)
    }

    @Test fun cosmeticRulesAreKeptByDomain() {
        val r = FilterParser.parse(
            sequenceOf("##.ad-banner", "site.com,autre.fr##.promo", "site.com#@#.ad-banner", "~exclu.com##.x", "*##.y"),
        )
        assertEquals(setOf(".ad-banner"), r.hide[""])
        assertEquals(setOf(".promo"), r.hide["site.com"])
        assertEquals(setOf(".promo"), r.hide["autre.fr"])
        assertEquals(setOf(".ad-banner"), r.unhide["site.com"])
        assertEquals(2, r.skipped)
    }

    @Test fun hideExceptionsByOption() {
        val r = FilterParser.parse(sequenceOf("@@||site.com^\$generichide", "@@||other.com^\$elemhide"))
        assertEquals(setOf("site.com"), r.noGenericHide)
        assertEquals(setOf("other.com"), r.noHide)
        assertTrue(r.allowed.isEmpty())
    }

    @Test fun neverBlocksBareTld() {
        assertFalse(blocker("com").isBlocked("example.com"))
    }

    @Test fun hostIsCaseAndDotInsensitive() {
        assertTrue(blocker("||Pub.Example.COM^").isBlocked("PUB.example.com."))
    }

    @Test fun mergeCombinesRules() {
        val m = HostRules.merge(listOf(FilterParser.parse("||a.com^"), FilterParser.parse("@@||b.com^")))
        assertEquals(setOf("a.com"), m.blocked)
        assertEquals(setOf("b.com"), m.allowed)
    }
}

class HostBlockerLayersTest {
    @Test fun layersAreCombinedWithoutMerging() {
        val b = HostBlocker(listOf(FilterParser.parse("||a.com^"), FilterParser.parse("||b.com^"), HostRules(allowed = setOf("ok.a.com"))))
        assertTrue(b.isBlocked("x.a.com"))
        assertTrue(b.isBlocked("b.com"))
        assertFalse(b.isBlocked("ok.a.com"))
        assertFalse(b.isBlocked("c.com"))
    }

    @Test fun exceptionInAnyLayerWins() {
        val b = HostBlocker(listOf(FilterParser.parse("||a.com^"), HostRules(allowed = setOf("a.com"))))
        assertFalse(b.isBlocked("a.com"))
    }

    @Test fun repeatedLookupsAreStable() {
        val b = HostBlocker(FilterParser.parse("||a.com^"))
        repeat(3) { assertTrue(b.isBlocked("A.com.")) ; assertFalse(b.isBlocked("z.org")) }
    }
}
