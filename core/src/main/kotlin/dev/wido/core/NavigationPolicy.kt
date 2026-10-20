package dev.wido.core

/**
 * Quelles pages le site peut-il ouvrir dans la fenêtre ?
 *
 * - **strict** (défaut) : seulement le domaine du site, ses sous-domaines et `allowedDomains` (qui
 *   peut contenir `site.*` : toutes les extensions du site, pour ceux qui changent souvent d'adresse).
 *   Tout le reste (régies, redirections, `intent:`, `market:`) est refusé : c'est ce qui neutralise
 *   la plupart des pop-ups et redirections publicitaires.
 * - **relaxed** : en plus, toute page web dont le domaine n'est **pas** bloqué par les listes. Pour
 *   les sites qui ont besoin de redirections utiles (connexion, consentement…).
 *
 * - **mixed** : comme strict, mais la page refusée s'ouvre dans une fenêtre publicitaire à part,
 *   affichée quelques secondes puis fermée : pour les sites qui exigent que la pub soit vue.
 *
 * Les sous-requêtes (scripts, images, vidéo) ne passent pas par ici : elles sont jugées par [HostBlocker].
 */
object NavigationPolicy {
    enum class Decision {
        /** La fenêtre ouvre la page. */
        ALLOW,
        /** Refusée. */
        BLOCK,
        /** Ouverte dans une fenêtre publicitaire, fermée seule une fois affichée (mode mixte). */
        AD_WINDOW,
    }

    /**
     * Décision pour une navigation de la fenêtre principale. En mode souple, toute page d'un domaine
     * que les listes ne bloquent pas est acceptée, qu'elle vienne d'un clic, d'un script ou du serveur.
     */
    fun decide(url: String, site: Site, blocker: HostBlocker?): Decision {
        val scheme = Hosts.schemeOf(url) ?: return Decision.BLOCK
        val host = Hosts.of(url) ?: return Decision.BLOCK
        val web = scheme == "https" || scheme == "http"
        if (!web) return Decision.BLOCK
        val schemeOk = scheme == "https" || site.allowInsecure
        if (schemeOk && allowedDomains(site).any { Hosts.matchesPattern(host, it) }) return Decision.ALLOW
        return when (site.navigation) {
            Navigation.RELAXED ->
                if (schemeOk && blocker != null && !blocker.isBlocked(host)) Decision.ALLOW else Decision.BLOCK
            Navigation.MIXED -> Decision.AD_WINDOW
            else -> Decision.BLOCK
        }
    }

    fun isAllowed(url: String, site: Site, blocker: HostBlocker? = null): Boolean {
        val scheme = Hosts.schemeOf(url) ?: return false
        val host = Hosts.of(url) ?: return false
        val secureOk = scheme == "https"
        val insecureOk = scheme == "http" && site.allowInsecure
        if (!secureOk && !insecureOk) return false
        if (allowedDomains(site).any { Hosts.matchesPattern(host, it) }) return true
        return site.navigation == Navigation.RELAXED && blocker != null && !blocker.isBlocked(host)
    }

    fun allowedDomains(site: Site): List<String> {
        val own = Hosts.of(site.url)?.removePrefix("www.")
        return listOfNotNull(own) + site.allowedDomains.map(Hosts::normalize)
    }
}
