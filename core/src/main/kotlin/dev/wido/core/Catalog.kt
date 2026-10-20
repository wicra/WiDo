package dev.wido.core

/**
 * Sites prêts à ajouter en un clic, avec des réglages qui conviennent : redirections souples (lecteurs
 * et connexions hébergés ailleurs), pop-ups bloquées, bandeaux de cookies masqués. Ils deviennent des
 * sites perso ordinaires, modifiables ensuite.
 */
object Catalog {
    data class Entry(
        val name: String,
        val url: String,
        val color: String,
        /** Autorise les pages de connexion Google, Microsoft, Apple et Facebook. */
        val allowLogins: Boolean = false,
        val allowedDomains: List<String> = emptyList(),
    ) {
        val host: String get() = Hosts.of(url).orEmpty().removePrefix("www.")

        fun options() = LocalSites.SiteOptions(
            allowedDomains = allowedDomains,
            navigation = Navigation.RELAXED,
            blockPopups = true,
            hideCookieBanners = true,
            allowLogins = allowLogins,
        )
    }

    val entries = listOf(
        Entry("YouTube", "https://www.youtube.com/", "#FF0033", allowLogins = true),
        Entry("YouTube Kids", "https://www.youtubekids.com/", "#FF4E45", allowLogins = true),
        Entry("Arte", "https://www.arte.tv/fr/", "#FA481C"),
        Entry("france.tv", "https://www.france.tv/", "#3D5AFE"),
        Entry("TF1+", "https://www.tf1.fr/", "#1F5BFF"),
        Entry("M6+", "https://www.m6.fr/", "#E2322B"),
        Entry("Twitch", "https://www.twitch.tv/", "#9146FF"),
        Entry("Dailymotion", "https://www.dailymotion.com/", "#2D6BFF"),
        Entry("Pluto TV", "https://pluto.tv/fr", "#F5C518"),
        Entry("Vimeo", "https://vimeo.com/", "#1AB7EA"),
        Entry("Radio France", "https://www.radiofrance.fr/", "#E6007E"),
        Entry("Plex", "https://app.plex.tv/", "#E5A00D", allowLogins = true),
        Entry("Internet Archive", "https://archive.org/details/movies", "#3F8CFF"),
    )

    /** Les entrées dont le site n'est pas déjà dans le menu (même domaine). */
    fun available(existingUrls: Collection<String>): List<Entry> {
        val hosts = existingUrls.mapNotNull { Hosts.of(it)?.removePrefix("www.") }
        return entries.filter { e -> hosts.none { Hosts.matches(it, e.host) || Hosts.matches(e.host, it) } }
    }
}
