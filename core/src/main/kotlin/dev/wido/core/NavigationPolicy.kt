package dev.wido.core

/**
 * Quelles pages le site peut-il ouvrir dans la fenêtre ? Tout le reste (régies publicitaires,
 * redirections, schémas `intent:` ou `market:`) est refusé. C'est ce qui neutralise la plupart
 * des pop-ups et redirections, sans toucher au lecteur vidéo (qui charge ses ressources via
 * des sous-requêtes, jugées par [HostBlocker], pas par cette politique).
 */
object NavigationPolicy {
    fun isAllowed(url: String, site: Site): Boolean {
        val scheme = Hosts.schemeOf(url) ?: return false
        val host = Hosts.of(url) ?: return false
        val secureOk = scheme == "https"
        val insecureOk = scheme == "http" && site.allowInsecure
        if (!secureOk && !insecureOk) return false
        return allowedDomains(site).any { Hosts.matches(host, it) }
    }

    fun allowedDomains(site: Site): List<String> {
        val own = Hosts.of(site.url)?.removePrefix("www.")
        return listOfNotNull(own) + site.allowedDomains.map(Hosts::normalize)
    }
}
