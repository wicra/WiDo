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

class PhoneProtocolTest {
    @Test fun parsesRequestLineAndQuery() {
        val r = PhoneProtocol.parseRequestLine("POST /send?t=abc%20d&x=1 HTTP/1.1")!!
        assertEquals("POST", r.method)
        assertEquals("/send", r.path)
        assertEquals("abc d", r.query["t"])
        assertEquals(null, PhoneProtocol.parseRequestLine("garbage"))
        assertEquals(null, PhoneProtocol.parseRequestLine("GET http://x/ HTTP/1.1"))
    }

    @Test fun messagesAreBounded() {
        assertEquals(PhoneProtocol.Message("bonjour", true), PhoneProtocol.parseMessage("""{"text":"bonjour\n","submit":true}"""))
        assertEquals(null, PhoneProtocol.parseMessage("""{"text":""}"""))
        assertEquals(null, PhoneProtocol.parseMessage("pas du json"))
        assertEquals(null, PhoneProtocol.parseMessage("""{"text":"${"a".repeat(PhoneProtocol.MAX_TEXT + 1)}"}"""))
    }

    @Test fun tokenComparison() {
        assertTrue(PhoneProtocol.tokenMatches("abcd", "abcd"))
        assertTrue(!PhoneProtocol.tokenMatches("abcd", "abce"))
        assertTrue(!PhoneProtocol.tokenMatches("abcd", null))
    }

    @Test fun pageEscapesLabels() {
        val html = PhoneProtocol.page("tok", PhoneProtocol.Labels("<a>", "Envoyer", "Envoyer + Entrée", "Envoyé \"ok\"", "Échec"))
        assertTrue("&lt;a&gt;" in html)
        assertTrue("/send?t=tok" in html)
        assertTrue("\"Envoyé \\\"ok\\\"\"" in html)
    }
}

class LibraryTest {
    private val yt = Site(id = "youtube", name = "YouTube", url = "https://www.youtube.com/")

    @Test fun remembersOnlyPagesOfTheSite() {
        assertEquals("https://www.youtube.com/@chaine", ResumeUrl.toRemember(yt, "https://www.youtube.com/@chaine"))
        assertEquals(null, ResumeUrl.toRemember(yt, "https://pub.example.com/x"))
        assertEquals(null, ResumeUrl.toRemember(yt, "about:blank"))
        assertEquals(null, ResumeUrl.toRemember(yt, "http://www.youtube.com/"))
    }

    @Test fun addsYoutubePosition() {
        assertEquals(
            "https://www.youtube.com/watch?v=abc&t=125s",
            ResumeUrl.toRemember(yt, "https://www.youtube.com/watch?v=abc&t=30s", 125.4),
        )
        assertEquals("https://www.youtube.com/watch?v=abc", ResumeUrl.toRemember(yt, "https://www.youtube.com/watch?v=abc", 3.0))
    }

    @Test fun startUrlFallsBackToHome() {
        assertEquals(yt.url, ResumeUrl.startUrl(yt, null))
        assertEquals(yt.url, ResumeUrl.startUrl(yt, "https://ailleurs.com/"))
        assertEquals("https://m.youtube.com/feed", ResumeUrl.startUrl(yt, "https://m.youtube.com/feed"))
    }

    @Test fun ordersByUserChoiceThenOriginalOrder() {
        assertEquals(listOf("c", "a", "b", "d"), SiteOrder.sort(listOf("a", "b", "c", "d"), listOf("c", "a")) { it })
        assertEquals(listOf("b", "a", "c"), SiteOrder.move(listOf("a", "b", "c"), "b", -1))
        assertEquals(listOf("a", "b", "c"), SiteOrder.move(listOf("a", "b", "c"), "c", 1))
    }

    @Test fun pinsAreDedupedAndBounded() {
        var pins = emptyList<Pin>()
        pins = Pins.add(pins, "yt", "  Ma   chaîne ", "https://www.youtube.com/@chaine")
        pins = Pins.add(pins, "yt", "Ma chaîne", "https://www.youtube.com/@chaine")
        assertEquals(listOf(Pin("yt", "Ma chaîne", "https://www.youtube.com/@chaine")), pins)
        repeat(20) { pins = Pins.add(pins, "yt", null, "https://www.youtube.com/p$it") }
        assertEquals(Pins.MAX_PER_SITE, Pins.forSite(pins, "yt").size)
        assertEquals("p19", pins.first().title)
        assertEquals(pins, Pins.decode(Pins.encode(pins)))
        assertEquals(pins.size - 1, Pins.remove(pins, pins.first()).size)
        assertEquals(emptyList(), Pins.add(emptyList(), "yt", "x", "javascript:alert(1)"))
    }
}

class UserHidesTest {
    @Test fun addsAppliesAndClears() {
        var hides = emptyMap<String, List<String>>()
        hides = UserHides.add(hides, "s", "#popup")
        hides = UserHides.add(hides, "s", "#popup")
        hides = UserHides.add(hides, "s", "div{color:red}")
        assertEquals(mapOf("s" to listOf("#popup")), hides)
        val site = Site(id = "s", name = "S", url = "https://s.example/", hideSelectors = listOf(".a"))
        assertEquals(listOf(".a", "#popup"), UserHides.apply(site, hides).hideSelectors)
        assertEquals(hides, UserHides.decode(UserHides.encode(hides)))
        assertTrue(UserHides.clear(hides, "s").isEmpty())
    }
}
