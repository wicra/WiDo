package dev.wido.core

import java.net.URI

/** Utilitaires sur les noms d'hôte. Tout est en minuscules, sans point final. */
object Hosts {
    private val HOSTNAME =
        Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")

    fun normalize(raw: String): String = raw.trim().lowercase().trimEnd('.')

    /** Un nom d'hôte valide avec au moins un point (donc jamais un TLD seul). */
    fun isValidHostname(host: String): Boolean = host.length <= 253 && HOSTNAME.matches(host)

    fun of(url: String): String? = try {
        URI(url.trim()).host?.let(::normalize)?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    fun schemeOf(url: String): String? = try {
        URI(url.trim()).scheme?.lowercase()
    } catch (_: Exception) {
        null
    }

    private val ENTITY = Regex("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.\\*$")

    /** Second niveau courant sous un suffixe national : `site.co.uk` est un site, pas `co.uk`. */
    private val SECOND_LEVEL = setOf("co", "com", "net", "org", "gov", "gouv", "ac", "edu", "asso", "or", "ne")

    /** Domaine « entité » des listes uBlock : `exemple.*` vaut pour toutes les extensions de exemple. */
    fun isEntity(domain: String): Boolean = ENTITY.matches(domain)

    /** Comme [matches], en acceptant aussi un domaine entité (`exemple.*` couvre exemple.fr, www.exemple.co.uk…). */
    fun matchesPattern(host: String, domain: String): Boolean {
        if (!domain.endsWith(".*")) return matches(host, domain)
        val base = domain.dropLast(2)
        val labels = normalize(host).split('.')
        // On retire l'extension (un ou deux niveaux, pour .co.uk) puis on compare.
        return (1..2).any { cut ->
            labels.size > cut && labels.dropLast(cut).joinToString(".").let { it == base || it.endsWith(".$base") }
        }
    }

    /**
     * Le site d'un hôte, pour savoir si une requête est « tierce » : les deux derniers niveaux
     * (`cdn.exemple.com` → `exemple.com`), trois pour les suffixes nationaux (`a.exemple.co.uk`).
     */
    fun site(host: String): String {
        val labels = normalize(host).split('.')
        if (labels.size <= 2) return labels.joinToString(".")
        val keep = if (labels[labels.size - 2] in SECOND_LEVEL && labels.last().length == 2) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    /** `host` est `domain` ou un de ses sous-domaines. */
    fun matches(host: String, domain: String): Boolean {
        val h = normalize(host)
        val d = normalize(domain)
        return h == d || h.endsWith(".$d")
    }
}
