package dev.wido.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Domaines que l'utilisateur a autorisés sur un site de la configuration distante (par exemple
 * après une redirection refusée). Stockés localement : la configuration distante n'est jamais modifiée,
 * et ces choix survivent à son actualisation comme aux mises à jour de l'app.
 */
object SiteOverrides {
    const val MAX_SITES = 100
    const val MAX_DOMAINS = 50

    @Serializable
    private data class FileModel(val allowedDomains: Map<String, List<String>> = emptyMap())

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(map: Map<String, List<String>>): String = json.encodeToString(FileModel(map))

    fun decode(text: String): Map<String, List<String>> {
        val raw = try {
            json.decodeFromString<FileModel>(text).allowedDomains
        } catch (_: Exception) {
            return emptyMap()
        }
        val out = linkedMapOf<String, List<String>>()
        for ((id, domains) in raw) {
            if (!ConfigParser.ID_REGEX.matches(id) || out.size >= MAX_SITES) continue
            val valid = domains.map(Hosts::normalize).filter(Hosts::isValidHostname).distinct().take(MAX_DOMAINS)
            if (valid.isNotEmpty()) out[id] = valid
        }
        return out
    }

    /** Ajoute `domain` à `siteId`. Null si le domaine est invalide ou si une limite est atteinte. */
    fun withDomain(map: Map<String, List<String>>, siteId: String, domain: String): Map<String, List<String>>? {
        val d = Hosts.normalize(domain).removePrefix("www.")
        if (!Hosts.isValidHostname(d) || !ConfigParser.ID_REGEX.matches(siteId)) return null
        val current = map[siteId].orEmpty()
        if (d in current) return map
        if (current.size >= MAX_DOMAINS || (siteId !in map && map.size >= MAX_SITES)) return null
        return map + (siteId to (current + d))
    }

    fun apply(site: Site, map: Map<String, List<String>>): Site {
        val extra = map[site.id] ?: return site
        return site.copy(allowedDomains = (site.allowedDomains + extra).distinct())
    }
}
