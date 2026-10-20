package dev.wido.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationPolicyTest {
    private val site = Site(id = "a", name = "A", url = "https://www.a.example/", allowedDomains = listOf("player.cdn.example"))

    @Test fun allowsOwnDomainAndSubdomainsWithoutWww() {
        assertTrue(NavigationPolicy.isAllowed("https://www.a.example/film", site))
        assertTrue(NavigationPolicy.isAllowed("https://a.example/film", site))
        assertTrue(NavigationPolicy.isAllowed("https://m.a.example/", site))
    }

    @Test fun allowsListedExtraDomains() {
        assertTrue(NavigationPolicy.isAllowed("https://player.cdn.example/v/1", site))
    }

    @Test fun blocksForeignAndLookalikeDomains() {
        assertFalse(NavigationPolicy.isAllowed("https://ads.example.net/", site))
        assertFalse(NavigationPolicy.isAllowed("https://evil-a.example/", site))
        assertFalse(NavigationPolicy.isAllowed("https://a.example.evil.net/", site))
    }

    @Test fun blocksNonWebSchemes() {
        assertFalse(NavigationPolicy.isAllowed("intent://x#Intent;end", site))
        assertFalse(NavigationPolicy.isAllowed("market://details?id=x", site))
        assertFalse(NavigationPolicy.isAllowed("javascript:alert(1)", site))
    }

    @Test fun httpOnlyWhenSiteAllowsIt() {
        assertFalse(NavigationPolicy.isAllowed("http://a.example/", site))
        assertTrue(NavigationPolicy.isAllowed("http://a.example/", site.copy(allowInsecure = true)))
    }
}

class RelaxedNavigationTest {
    private val blocker = HostBlocker(FilterParser.parse("||ads.example.net^"))
    private val strict = Site(id = "y", name = "Y", url = "https://www.y.example/")
    private val relaxed = strict.copy(navigation = Navigation.RELAXED)

    @Test fun relaxedAllowsUnblockedForeignDomains() {
        assertTrue(NavigationPolicy.isAllowed("https://accounts.login.example/signin", relaxed, blocker))
    }

    @Test fun relaxedStillBlocksListedDomains() {
        assertFalse(NavigationPolicy.isAllowed("https://ads.example.net/click", relaxed, blocker))
        assertFalse(NavigationPolicy.isAllowed("https://x.ads.example.net/click", relaxed, blocker))
    }

    @Test fun relaxedStillRefusesNonWebSchemesAndHttp() {
        assertFalse(NavigationPolicy.isAllowed("intent://x#Intent;end", relaxed, blocker))
        assertFalse(NavigationPolicy.isAllowed("http://other.example/", relaxed, blocker))
    }

    @Test fun strictIgnoresTheBlockerForForeignDomains() {
        assertFalse(NavigationPolicy.isAllowed("https://accounts.login.example/signin", strict, blocker))
    }

    @Test fun relaxedWithoutBlockerBehavesAsStrict() {
        assertFalse(NavigationPolicy.isAllowed("https://accounts.login.example/signin", relaxed))
    }
}

class NavigationDecisionTest {
    private val blocker = HostBlocker(FilterParser.parse("||ads.example.net^"))
    private val site = Site(id = "s", name = "S", url = "https://site.example/")
    private val relaxed = site.copy(navigation = Navigation.RELAXED)
    private val mixed = site.copy(navigation = Navigation.MIXED)

    @Test fun ownDomainIsAlwaysAllowed() {
        for (s in listOf(site, relaxed, mixed)) {
            kotlin.test.assertEquals(NavigationPolicy.Decision.ALLOW, NavigationPolicy.decide("https://www.site.example/x", s, blocker))
        }
    }

    @Test fun relaxedAllowsAnyUnlistedDomain() {
        kotlin.test.assertEquals(NavigationPolicy.Decision.ALLOW, NavigationPolicy.decide("https://login.other/", relaxed, blocker))
        kotlin.test.assertEquals(NavigationPolicy.Decision.ALLOW, NavigationPolicy.decide("https://redirige.ailleurs.net/page", relaxed, blocker))
        kotlin.test.assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("https://ads.example.net/", relaxed, blocker))
    }

    @Test fun mixedOpensForeignPagesInAdWindow() {
        kotlin.test.assertEquals(NavigationPolicy.Decision.AD_WINDOW, NavigationPolicy.decide("https://ads.example.net/", mixed, blocker))
        kotlin.test.assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("intent://x#Intent;end", mixed, blocker))
    }

    @Test fun strictBlocksForeignPages() {
        kotlin.test.assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("https://login.other/", site, blocker))
    }
}
