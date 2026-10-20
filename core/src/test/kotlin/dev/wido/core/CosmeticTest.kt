package dev.wido.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CosmeticTest {
    private val rules = FilterParser.parse(
        sequenceOf(
            "##.ad-banner", "##.sponsor", "site.com##.promo", "ailleurs.org##.autre",
            "site.com#@#.sponsor", "@@||nogeneric.site.com^\$generichide",
        ),
    )

    @Test fun keepsGenericAndSiteRulesOnly() {
        val plan = CosmeticPlan.build(listOf(rules), listOf("site.com"))
        assertEquals(listOf(".ad-banner", ".sponsor"), plan.generic)
        assertEquals(setOf("site.com"), plan.byDomain.keys)
    }

    @Test fun selectorsDependOnPageHost() {
        val plan = CosmeticPlan.build(listOf(rules), listOf("site.com"))
        assertEquals(listOf(".ad-banner", ".promo"), plan.selectorsFor("www.site.com"))
        assertEquals(listOf(".ad-banner", ".sponsor"), plan.selectorsFor("autre.net"))
        assertEquals(listOf(".promo"), plan.selectorsFor("nogeneric.site.com"))
    }

    @Test fun genericSelectorsAreCapped() {
        val many = FilterParser.parse((1..50).map { "##.ad$it" }.asSequence())
        assertEquals(10, CosmeticPlan.build(listOf(many), emptyList(), maxGeneric = 10).generic.size)
    }

    @Test fun planSerializesWithShortKeys() {
        val json = CosmeticPlan.build(listOf(rules), listOf("site.com")).toJson()
        assertTrue(json.startsWith("{\"g\":["))
    }
}

class CompiledRulesTest {
    @Test fun roundTripKeepsEverything() {
        val rules = FilterParser.parse(
            sequenceOf("||a.com^", "@@||b.com^", "##.x", "site.com##div.y > a", "site.com#@#.x", "@@||c.com^\$generichide", "@@||d.com^\$elemhide", "||bad/path"),
        )
        assertEquals(rules, CompiledRules.decode(CompiledRules.encode(rules)))
    }

    @Test fun rejectsOtherFormats() {
        assertEquals(null, CompiledRules.decode("||a.com^\n"))
    }
}

class CatalogTest {
    @Test fun everyEntryBuildsAValidSite() {
        val taken = mutableSetOf<String>()
        for (e in Catalog.entries) {
            val built = LocalSites.build(e.name, e.url, taken, options = e.options(), color = e.color)
            assertTrue(built is LocalSites.Built.Ok, "${e.name} : $built")
            assertEquals(e.color, built.site.color)
            taken += built.site.id
        }
    }

    @Test fun hidesSitesAlreadyInTheMenu() {
        val left = Catalog.available(listOf("https://youtube.com", "https://www.arte.tv/de/"))
        assertTrue(left.none { it.name == "YouTube" || it.name == "Arte" })
        assertTrue(left.any { it.name == "Twitch" })
    }
}
