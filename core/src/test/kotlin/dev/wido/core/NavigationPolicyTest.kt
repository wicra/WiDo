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
