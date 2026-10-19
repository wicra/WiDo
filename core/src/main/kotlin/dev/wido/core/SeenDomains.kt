package dev.wido.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Ce qu'un site a tenté de faire, mémorisé localement pour proposer des choix en un bouton :
 * les redirections refusées (à autoriser ?) et les domaines tiers contactés (à bloquer ?).
 * Rien de tout cela ne quitte l'appareil.
 */
object SeenDomains {
    const val MAX_NAVIGATIONS = 8
    const val MAX_REQUESTS = 12
    const val MAX_SITES = 100

    @Serializable
    data class Entry(
        /** Hôtes des redirections refusées, les plus récentes d'abord. */
        val navigations: List<String> = emptyList(),
        /** Domaines tiers contactés, avec leur nombre de requêtes. */
        val requests: Map<String, Int> = emptyMap(),
    )

    @Serializable
    private data class FileModel(val sites: Map<String, Entry> = emptyMap())

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun merge(old: Entry, navigationHosts: List<String>, requestCounts: Map<String, Int>): Entry {
        val navigations = (navigationHosts.asReversed() + old.navigations)
            .map(Hosts::normalize).filter(Hosts::isValidHostname).distinct().take(MAX_NAVIGATIONS)
        val counts = old.requests.toMutableMap()
        for ((host, n) in requestCounts) {
            val h = Hosts.normalize(host)
            if (Hosts.isValidHostname(h) && n > 0) counts[h] = (counts[h] ?: 0) + n
        }
        val top = counts.entries.sortedByDescending { it.value }.take(MAX_REQUESTS).associate { it.key to it.value }
        return Entry(navigations, top)
    }

    /** (à autoriser, à bloquer) sans les domaines de `exclude` (déjà autorisés, déjà bloqués, domaine du site). */
    fun suggestions(entry: Entry, exclude: Set<String>): Pair<List<String>, List<String>> {
        fun excluded(h: String) = exclude.any { Hosts.matches(h, it) }
        val allow = entry.navigations.filterNot(::excluded)
        val block = entry.requests.entries.sortedByDescending { it.value }.map { it.key }.filterNot(::excluded)
        return allow to block
    }

    fun encode(map: Map<String, Entry>): String = json.encodeToString(FileModel(map))

    fun decode(text: String): Map<String, Entry> {
        val raw = try {
            json.decodeFromString<FileModel>(text).sites
        } catch (_: Exception) {
            return emptyMap()
        }
        val out = linkedMapOf<String, Entry>()
        for ((id, entry) in raw) {
            if (!ConfigParser.ID_REGEX.matches(id) || out.size >= MAX_SITES) continue
            out[id] = merge(Entry(), entry.navigations, entry.requests)
        }
        return out
    }
}
