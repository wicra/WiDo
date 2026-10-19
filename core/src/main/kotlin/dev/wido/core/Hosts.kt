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

    /** `host` est `domain` ou un de ses sous-domaines. */
    fun matches(host: String, domain: String): Boolean {
        val h = normalize(host)
        val d = normalize(domain)
        return h == d || h.endsWith(".$d")
    }
}
