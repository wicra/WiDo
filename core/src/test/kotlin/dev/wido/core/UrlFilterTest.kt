package dev.wido.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlFilterTest {
    private fun matcher(vararg lines: String) = UrlMatcher(listOf(UrlFilter(lines.toList())))

    private fun UrlMatcher.blocks(url: String, page: String = "site.com", type: RequestType = RequestType.SCRIPT) =
        shouldBlock(url, page, type, Hosts.site(Hosts.of(url)!!) != Hosts.site(page))

    @Test fun pathRulesBlockOnlyMatchingAddresses() {
        val m = matcher("||site.com/ads/*", "/banner/*", "-ad-banner.")
        assertTrue(m.blocks("https://site.com/ads/top.js"))
        assertTrue(m.blocks("https://cdn.site.com/ads/x.png"))
        assertFalse(m.blocks("https://site.com/video/ads.js"))
        assertTrue(m.blocks("https://other.org/img/banner/1.gif"))
        assertTrue(m.blocks("https://x.org/static/top-ad-banner.png"))
        assertFalse(m.blocks("https://x.org/static/header.png"))
    }

    @Test fun separatorAndAnchors() {
        val m = matcher("||ads.example.com^", "|https://evil.net/track|")
        assertTrue(m.blocks("https://ads.example.com/x"))
        assertFalse(m.blocks("https://ads.example.community/x"))
        assertTrue(m.blocks("https://evil.net/track"))
        assertFalse(m.blocks("https://evil.net/track?x=1"))
    }

    @Test fun domainThirdPartyAndTypeOptions() {
        val m = matcher("||tracker.net^\$script,third-party,domain=news.com|~sport.news.com")
        assertTrue(m.blocks("https://tracker.net/t.js", page = "www.news.com"))
        assertFalse(m.blocks("https://tracker.net/t.js", page = "sport.news.com"))
        assertFalse(m.blocks("https://tracker.net/t.js", page = "other.com"))
        assertFalse(m.blocks("https://tracker.net/t.png", page = "news.com", type = RequestType.IMAGE))
    }

    @Test fun exceptionsAndImportant() {
        val m = matcher("/adserver/*", "@@||site.com/adserver/ok.js", "||bad.com/adserver/\$important", "@@||bad.com^")
        assertTrue(m.blocks("https://x.com/adserver/a.js"))
        assertFalse(m.blocks("https://site.com/adserver/ok.js"))
        assertTrue(m.blocks("https://bad.com/adserver/a.js"))
    }

    @Test fun unsupportedRulesAreRefused() {
        assertNull(UrlRule.parse("/ads?[0-9]+\\.js/"))
        assertNull(UrlRule.parse("||site.com^\$redirect=noopjs"))
        assertNull(UrlRule.parse("||site.com^\$removeparam=utm_source"))
        assertNull(UrlRule.parse("||site.com^\$document"))
        assertNull(UrlRule.parse("*\$script"))
    }

    @Test fun tokensIndexRules() {
        assertEquals("banner", UrlRule.bestToken("-ad-banner."))
        assertEquals("example", UrlRule.bestToken("||example.com^"))
        assertNull(UrlRule.bestToken("ban*ner"))
        assertEquals("adserver", UrlRule.bestToken("/adserver/*"))
    }

    @Test fun guessesRequestTypes() {
        assertEquals(RequestType.SCRIPT, RequestType.guess("https://a.com/x.js?v=1", "*/*", false))
        assertEquals(RequestType.IMAGE, RequestType.guess("https://a.com/x", "image/webp,*/*", false))
        assertEquals(RequestType.DOCUMENT, RequestType.guess("https://a.com/", "text/html", true))
        assertEquals(RequestType.MEDIA, RequestType.guess("https://a.com/live.m3u8", null, false))
    }
}

class FilterParserAdvancedTest {
    @Test fun keepsUrlRulesAndScriptlets() {
        val r = FilterParser.parse(
            sequenceOf(
                "||site.com/ads/*",
                "||tracker.net^\$script,domain=news.com",
                "news.com,video.*##+js(set-constant, adBlockDetected, false)",
                "news.com#@#+js(nowoif)",
                "##+js(generic)",
                "/regex[0-9]/",
            ),
        )
        assertEquals(listOf("||site.com/ads/*", "||tracker.net^\$script,domain=news.com"), r.urls)
        assertEquals(setOf("set-constant, adBlockDetected, false"), r.scripts["news.com"])
        assertEquals(setOf("set-constant, adBlockDetected, false"), r.scripts["video.*"])
        assertEquals(setOf("nowoif"), r.unscripts["news.com"])
        assertEquals(2, r.skipped)
        assertEquals(r, CompiledRules.decode(CompiledRules.encode(r)))
    }

    @Test fun preprocessorKeepsOnlyMatchingBlocks() {
        val r = FilterParser.parse(
            sequenceOf(
                "!#if env_firefox", "||firefox-only.com^", "!#endif",
                "!#if env_mobile", "||mobile.com^", "!#else", "||desktop.com^", "!#endif",
                "!#if !env_safari", "||not-safari.com^", "!#endif",
            ),
        )
        assertEquals(setOf("mobile.com", "not-safari.com"), r.blocked)
    }

    @Test fun scriptletPlanFollowsPageHost() {
        val r = FilterParser.parse(
            sequenceOf("news.com##+js(aopr, adsbygoogle)", "video.*##+js(nostif, ads)", "sport.news.com#@#+js()"),
        )
        val plan = CosmeticPlan.build(listOf(r), listOf("news.com", "video.fr"))
        assertEquals(listOf("aopr, adsbygoogle"), plan.scriptsFor("www.news.com"))
        assertEquals(emptyList(), plan.scriptsFor("sport.news.com"))
        assertEquals(listOf("nostif, ads"), plan.scriptsFor("www.video.fr"))
    }

    @Test fun entityAndSiteHelpers() {
        assertTrue(Hosts.matchesPattern("www.video.co.uk", "video.*"))
        assertFalse(Hosts.matchesPattern("video.com.evil.net", "video.*"))
        assertEquals("example.co.uk", Hosts.site("a.b.example.co.uk"))
        assertEquals("example.com", Hosts.site("cdn.example.com"))
    }
}

class ListIncludesTest {
    @Test fun includesAreInlinedInPlace() {
        val out = ListIncludes.expand("a\n!#if env_mobile\n!#include mobile.txt\n!#endif\n!#include ../x.txt") { if (it == "mobile.txt") "||m.com^" else null }
        assertEquals("a\n!#if env_mobile\n||m.com^\n!#endif\n!#include ../x.txt", out)
        assertEquals(setOf("m.com"), FilterParser.parse(out).blocked)
    }
}
