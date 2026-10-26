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

class MediaDetectorTest {
    @Test fun detectsStreamsButNotSegmentsOrYoutube() {
        assertEquals(MediaStream.Kind.HLS, MediaDetector.detect("https://cdn.site.com/v/master.m3u8?token=x")?.kind)
        assertEquals(MediaStream.Kind.DASH, MediaDetector.detect("https://cdn.site.com/v/manifest.mpd")?.kind)
        assertEquals(MediaStream.Kind.PROGRESSIVE, MediaDetector.detect("https://cdn.site.com/film.mp4")?.kind)
        assertNull(MediaDetector.detect("https://cdn.site.com/v/seg-001.ts"))
        assertNull(MediaDetector.detect("https://rr1.googlevideo.com/videoplayback?x.m3u8"))
        assertNull(MediaDetector.detect("https://ads.site.com/preroll/spot.mp4"))
    }

    @Test fun prefersFirstManifest() {
        val mp4 = MediaStream("https://a/f.mp4", MediaStream.Kind.PROGRESSIVE)
        val master = MediaStream("https://a/master.m3u8", MediaStream.Kind.HLS)
        val variant = MediaStream("https://a/720p.m3u8", MediaStream.Kind.HLS)
        assertEquals(master, MediaDetector.better(MediaDetector.better(mp4, master), variant))
    }
}

class ContinueWatchingTest {
    @Test
    fun `reprendre ignore les vidéos courtes et remplace l'épisode précédent de la séance`() {
        // Pub ou bande-annonce : jamais dans Reprendre.
        assertEquals(emptyList<WatchEntry>(), ContinueWatching.record(emptyList(), e("https://s.com/pub", 30.0, 90.0)))
        var list = ContinueWatching.record(emptyList(), e("https://s.com/autre", 100.0))
        list = ContinueWatching.record(list, e("https://s.com/serie/ep1", 100.0))
        // Épisode suivant dans la même séance : il prend la place du précédent, l'autre vidéo reste.
        list = ContinueWatching.record(list, e("https://s.com/serie/ep2", 50.0), replaces = "https://s.com/serie/ep1")
        assertEquals(listOf("https://s.com/serie/ep2", "https://s.com/autre"), list.map { it.url })
        // Une même vidéo ne se remplace pas elle-même.
        list = ContinueWatching.record(list, e("https://s.com/serie/ep2", 80.0), replaces = "https://s.com/serie/ep2")
        assertEquals(listOf("https://s.com/serie/ep2", "https://s.com/autre"), list.map { it.url })
        // Un autre film du même site, juste après, ne remplace pas l'épisode : chacun garde sa place.
        val film = WatchEntry("s", "https://s.com/film-b", "Un tout autre film", null, 100.0, 600.0, 1)
        list = ContinueWatching.record(list, film, replaces = "https://s.com/serie/ep2")
        assertEquals(listOf("https://s.com/film-b", "https://s.com/serie/ep2", "https://s.com/autre"), list.map { it.url })
    }

    private fun e(url: String, pos: Double, dur: Double = 600.0) = WatchEntry("s", url, "Titre", null, pos, dur, 1)

    @Test fun recordsMostRecentFirstAndDropsFinished() {
        var list = emptyList<WatchEntry>()
        list = ContinueWatching.record(list, e("https://s.com/a", 100.0))
        list = ContinueWatching.record(list, e("https://s.com/b", 50.0))
        list = ContinueWatching.record(list, e("https://s.com/a", 200.0))
        assertEquals(listOf("https://s.com/a", "https://s.com/b"), list.map { it.url })
        assertEquals(200.0, list.first().position)
        list = ContinueWatching.record(list, e("https://s.com/a", 590.0))
        assertEquals(listOf("https://s.com/b"), list.map { it.url })
        assertEquals(list, ContinueWatching.record(list, e("https://s.com/c", 3.0)))
        assertEquals(list, ContinueWatching.decode(ContinueWatching.encode(list)))
    }

    @Test fun youtubeThumbnails() {
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", ContinueWatching.youtubeThumbnail("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s"))
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", ContinueWatching.record(emptyList(), e("https://m.youtube.com/watch?v=dQw4w9WgXcQ", 30.0)).first().image)
        assertNull(ContinueWatching.youtubeThumbnail("https://vimeo.com/123"))
    }
}

class RemoteProtocolTest {
    @Test fun parsesCommands() {
        assertEquals(RemoteCommand.Key(RemoteKey.PLAY_PAUSE), RemoteProtocol.parse("""{"cmd":"key","key":"play_pause"}"""))
        assertEquals(RemoteCommand.Move(400f, -3f), RemoteProtocol.parse("""{"cmd":"move","dx":9999,"dy":-3}"""))
        assertEquals(RemoteCommand.Tap, RemoteProtocol.parse("""{"cmd":"tap"}"""))
        assertEquals(RemoteCommand.Type("salut", true), RemoteProtocol.parse("""{"cmd":"type","text":"salut\n","submit":true}"""))
        // Champ effacé sur le téléphone : le champ de la TV est vidé aussi.
        assertEquals(RemoteCommand.Type("", false), RemoteProtocol.parse("""{"cmd":"type","text":""}"""))
        assertEquals(RemoteCommand.Open("https://exemple.org"), RemoteProtocol.parse("""{"cmd":"open","url":"exemple.org"}"""))
        assertNull(RemoteProtocol.parse("""{"cmd":"open","url":"javascript:alert(1)"}"""))
        assertNull(RemoteProtocol.parse("""{"cmd":"key","key":"power"}"""))
        assertNull(RemoteProtocol.parse("nope"))
    }
}

class SiteEntityTest {
    @Test fun entityDomainsAllowSisterAddresses() {
        assertEquals("monsite.*", LocalSites.siteEntity("https://www.monsite.bio/film"))
        assertEquals("exemple.*", LocalSites.siteEntity("a.exemple.co.uk"))
        assertNull(LocalSites.siteEntity("http://192.168.1.20:8096"))
        val site = Site(id = "w", name = "W", url = "https://monsite.bio/", allowedDomains = listOf("monsite.*"))
        assertEquals(NavigationPolicy.Decision.ALLOW, NavigationPolicy.decide("https://monsite.cloud/film", site, null))
        assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("https://pub-monsite.net/", site, null))
        assertEquals(listOf("monsite.*", "cdn.exemple.org") to null, LocalSites.parseDomains("monsite.*, https://cdn.exemple.org/x"))
    }
}
