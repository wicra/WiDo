package dev.wido.core

import kotlinx.serialization.Serializable

/**
 * Partage de sites d'un écran à l'autre (ex. le téléphone qui prépare la TV d'un proche) : le nom,
 * l'adresse et les réglages de blocage de chaque site, jamais de compte ni de cookie. La TV qui reçoit
 * demande toujours confirmation à l'écran avant d'ajouter quoi que ce soit.
 */
@Serializable
data class SharedSite(
    val name: String,
    val url: String,
    val color: String? = null,
    val allowedDomains: List<String> = emptyList(),
    val blockedDomains: List<String> = emptyList(),
    val navigation: String = Navigation.STRICT,
    val blockAds: Boolean = true,
    val blockPopups: Boolean = true,
    val hideCookieBanners: Boolean = false,
    val hideOverlays: Boolean = false,
    val allowLogins: Boolean = false,
) {
    fun options() = LocalSites.SiteOptions(
        allowedDomains = allowedDomains,
        blockedDomains = blockedDomains,
        navigation = navigation,
        blockAds = blockAds,
        blockPopups = blockPopups,
        hideCookieBanners = hideCookieBanners,
        hideOverlays = hideOverlays,
        allowLogins = allowLogins,
    )
}

/** Une proposition de sites, avec l'appareil qui l'envoie. */
@Serializable
data class SiteShare(val from: DeviceInfo, val sites: List<SharedSite>)

/** Un pack de sites enregistré sur le téléphone, à renvoyer plus tard (« Pack Mamie »). */
@Serializable
data class SitePack(val name: String, val sites: List<SharedSite>)

object SiteSharing {
    const val MAX_SITES = 30
    const val MAX_PACKS = 20
    private const val MAX_DOMAINS = 50

    fun fromSite(site: Site): SharedSite {
        val o = LocalSites.optionsOf(site)
        return SharedSite(
            name = site.name.take(LocalSites.MAX_NAME),
            url = site.url,
            color = site.color,
            allowedDomains = o.allowedDomains,
            blockedDomains = o.blockedDomains,
            navigation = o.navigation,
            blockAds = o.blockAds,
            blockPopups = o.blockPopups,
            hideCookieBanners = o.hideCookieBanners,
            hideOverlays = o.hideOverlays,
            allowLogins = o.allowLogins,
        )
    }

    /** Ce qu'une TV accepte de montrer : adresses http(s) valides, noms non vides, listes bornées. */
    fun sanitize(sites: List<SharedSite>): List<SharedSite> = sites
        .filter { s ->
            val scheme = Hosts.schemeOf(s.url)
            s.name.isNotBlank() && s.url.length <= 2000 && (scheme == "https" || scheme == "http") && Hosts.of(s.url) != null
        }
        .map { s ->
            s.copy(
                name = s.name.trim().take(LocalSites.MAX_NAME),
                navigation = s.navigation.takeIf { it in Navigation.ALL } ?: Navigation.STRICT,
                allowedDomains = s.allowedDomains.filter { it.length <= 253 }.take(MAX_DOMAINS),
                blockedDomains = s.blockedDomains.filter { it.length <= 253 }.take(MAX_DOMAINS),
                color = s.color?.takeIf { it.length <= 9 && it.startsWith("#") },
            )
        }
        .distinctBy { normalized(it.url) }
        .take(MAX_SITES)

    /** Les sites proposés qui ne sont pas déjà là (même adresse). */
    fun newOnly(sites: List<SharedSite>, existingUrls: List<String>): List<SharedSite> {
        val known = existingUrls.map { normalized(it) }.toSet()
        return sites.filter { normalized(it.url) !in known }
    }

    private fun normalized(url: String) = url.trim().lowercase().removeSuffix("/").replace("://www.", "://")

    /** Ajoute ou remplace (même nom) un pack ; le plus récent en tête. */
    fun savePack(packs: List<SitePack>, pack: SitePack): List<SitePack> =
        (listOf(pack) + packs.filterNot { it.name.equals(pack.name, ignoreCase = true) }).take(MAX_PACKS)

    fun encodePacks(packs: List<SitePack>): String = LinkProtocol.encode(kotlinx.serialization.builtins.ListSerializer(SitePack.serializer()), packs)

    fun decodePacks(text: String?): List<SitePack> =
        text?.let { LinkProtocol.decode(kotlinx.serialization.builtins.ListSerializer(SitePack.serializer()), it) }.orEmpty()
}
