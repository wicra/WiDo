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
            sequenceOf("! titre", "# note", "[Adblock Plus 2.0]", "", "||ok.com^", "||a.com/path^", "||b.com^\$third-party", "site.com##.banner"),
        )
        assertEquals(setOf("ok.com"), r.blocked)
        assertEquals(3, r.skipped)
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
