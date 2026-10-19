package dev.wido.core

/**
 * Quelles pages le site peut-il ouvrir dans la fenêtre ?
 *
 * - **strict** (défaut) : seulement le domaine du site, ses sous-domaines et `allowedDomains`.
 *   Tout le reste (régies, redirections, `intent:`, `market:`) est refusé : c'est ce qui neutralise
 *   la plupart des pop-ups et redirections publicitaires.
 * - **relaxed** : en plus, toute page web dont le domaine n'est **pas** bloqué par les listes. Pour
 *   les sites qui ont besoin de redirections utiles (connexion, consentement…).
 *
 * Les sous-requêtes (scripts, images, vidéo) ne passent pas par ici : elles sont jugées par [HostBlocker].
 */
object NavigationPolicy {
    fun isAllowed(url: String, site: Site, blocker: HostBlocker? = null): Boolean {
        val scheme = Hosts.schemeOf(url) ?: return false
        val host = Hosts.of(url) ?: return false
        val secureOk = scheme == "https"
        val insecureOk = scheme == "http" && site.allowInsecure
        if (!secureOk && !insecureOk) return false
        if (allowedDomains(site).any { Hosts.matches(host, it) }) return true
        return site.navigation == Navigation.RELAXED && blocker != null && !blocker.isBlocked(host)
    }

    fun allowedDomains(site: Site): List<String> {
        val own = Hosts.of(site.url)?.removePrefix("www.")
        return listOfNotNull(own) + site.allowedDomains.map(Hosts::normalize)
    }
}
