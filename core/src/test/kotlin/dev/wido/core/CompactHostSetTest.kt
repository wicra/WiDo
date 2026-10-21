package dev.wido.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompactHostSetTest {
    @Test fun containsOnlyWhatWasAdded() {
        val set = CompactHostSet.of(listOf("ads.example", "tracker.net", "ads.example"))
        assertEquals(2, set.size)
        assertTrue("ads.example" in set)
        assertTrue("tracker.net" in set)
        assertFalse("example" in set)
        assertFalse("ads.example." in set)
        assertTrue(CompactHostSet.EMPTY.isEmpty())
    }

    @Test fun namesAreNotKept() {
        assertFailsWith<UnsupportedOperationException> { CompactHostSet.of(listOf("a.example")).iterator() }
    }

    @Test fun equalSetsAreEqual() {
        assertEquals(CompactHostSet.of(listOf("b.example", "a.example")), CompactHostSet.of(listOf("a.example", "b.example")))
        assertEquals(CompactHostSet.of(listOf("a.example")).hashCode(), CompactHostSet.of(listOf("a.example")).hashCode())
    }

    @Test fun noCollisionsOnAHugeList() {
        val hosts = (0 until 300_000).map { "h$it.tracker${it % 977}.example" }
        val set = CompactHostSet.of(hosts)
        assertEquals(hosts.size, set.size)
        assertTrue(hosts.all { it in set })
        assertEquals(0, (0 until 50_000).count { "absent$it.other.example" in set })
    }

    @Test fun blockerDecidesTheSameWithACompactList() {
        val rules = FilterParser.parse(sequenceOf("||ads.example^", "||tracker.net^", "@@||ok.ads.example^"))
        val full = HostBlocker(rules)
        val compact = HostBlocker(rules.compact())
        for (host in listOf("ads.example", "x.ads.example", "ok.ads.example", "tracker.net", "a.b.tracker.net", "safe.org", "example")) {
            assertEquals(full.isBlocked(host), compact.isBlocked(host), host)
        }
    }

    @Test fun compiledRulesDecodeToTheSameDecisions() {
        val rules = FilterParser.parse(sequenceOf("||ads.example^", "||tracker.net^", "##.banner", "||x.com/ad.js"))
        val text = CompiledRules.encode(rules)
        val compact = CompiledRules.decodeLines(text.lineSequence(), compact = true)!!
        assertTrue(compact.blocked is CompactHostSet)
        assertEquals(rules.blocked.size, compact.blocked.size)
        assertTrue(rules.blocked.all { it in compact.blocked })
        assertEquals(rules.hide, compact.hide)
        assertEquals(rules.urls, compact.urls)
        assertEquals(null, CompiledRules.decodeLines(sequenceOf("||a.com^"), compact = true))
    }
}
