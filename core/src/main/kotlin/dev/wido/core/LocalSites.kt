package dev.wido.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Sites ajoutés par l'utilisateur depuis l'app. Ils vivent dans un fichier local, séparé de la
 * configuration distante : actualiser la config ou mettre l'app à jour ne les efface jamais.
 */
object LocalSites {
    const val PREFIX = "perso-"
    const val MAX_LOCAL = 50
    const val MAX_NAME = 40

    /** Remote d'abord ; un site local dont l'identifiant existe déjà côté remote est écarté. */
    fun merge(remote: List<Site>, local: List<Site>): List<Site> {
        val taken = remote.map { it.id }.toMutableSet()
        val out = remote.toMutableList()
        for (site in local) {
            if (taken.add(site.id)) out += site
        }
        return out
    }

    fun isLocalId(id: String) = id.startsWith(PREFIX)

    /** `perso-` + nom en minuscules sans accents, rendu unique parmi `taken`. */
    fun newId(name: String, taken: Set<String>): String {
        val slug = name.lowercase()
            .let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD) }
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "site" }
        val base = (PREFIX + slug).take(28).trimEnd('-')
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }

    /** Ajoute `https://` si absent. Renvoie null si ce n'est pas une URL exploitable. */
    fun normalizeUrl(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val withScheme = if (t.contains("://")) t else "https://$t"
        return withScheme.takeIf { Hosts.of(it) != null }
    }

    /** Réglages avancés d'un site perso. */
    data class SiteOptions(
        val allowedDomains: List<String> = emptyList(),
        val blockedDomains: List<String> = emptyList(),
        val relaxed: Boolean = false,
    )

    private const val MAX_DOMAINS = 30

    /**
     * Lit une liste de domaines saisie à la main (séparés par virgule, point-virgule, espace ou saut
     * de ligne). Accepte `exemple.org`, `https://www.exemple.org/page`. Renvoie (domaines, erreur).
     */
    fun parseDomains(text: String): Pair<List<String>, String?> {
        val out = linkedSetOf<String>()
        for (part in text.split(',', ';', ' ', '\n')) {
            val t = part.trim()
            if (t.isEmpty()) continue
            val host = Hosts.of(if (t.contains("://")) t else "https://$t")?.removePrefix("www.")
            if (host == null || !Hosts.isValidHostname(host)) return emptyList<String>() to "Domaine invalide : $t"
            out += host
        }
        if (out.size > MAX_DOMAINS) return emptyList<String>() to "Trop de domaines ($MAX_DOMAINS maximum)."
        return out.toList() to null
    }

    fun domainsToText(domains: List<String>): String = domains.joinToString(", ")

    /** Domaines d'une liste de règles `||domaine^` (les autres règles sont ignorées). */
    fun blockedDomainsOf(rules: List<String>): List<String> =
        rules.mapNotNull { r -> r.trim().takeIf { it.startsWith("||") && it.endsWith("^") }?.removePrefix("||")?.removeSuffix("^") }

    sealed interface Built {
        data class Ok(val site: Site) : Built
        data class Error(val message: String) : Built
    }

    /**
     * Construit un site local à partir de ce que l'utilisateur a saisi.
     * `http://` est accepté (serveur du réseau local) et marque le site `allowInsecure`.
     */
    fun build(
        name: String,
        rawUrl: String,
        taken: Set<String>,
        keepId: String? = null,
        options: SiteOptions = SiteOptions(),
    ): Built {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return Built.Error("Donne un nom au site.")
        if (cleanName.length > MAX_NAME) return Built.Error("Nom trop long ($MAX_NAME caractères maximum).")
        val url = normalizeUrl(rawUrl) ?: return Built.Error("Adresse invalide. Exemple : https://exemple.org")
        val scheme = Hosts.schemeOf(url)
        if (scheme != "https" && scheme != "http") return Built.Error("Seules les adresses http et https sont acceptées.")
        val id = keepId ?: newId(cleanName, taken)
        val site = Site(
            id = id,
            name = cleanName,
            url = url,
            allowedDomains = options.allowedDomains,
            rules = options.blockedDomains.map { "||$it^" },
            scripts = listOf("block-popups"),
            allowInsecure = scheme == "http",
            navigation = if (options.relaxed) Navigation.RELAXED else Navigation.STRICT,
        )
        val problem = ConfigParser.validateSite(site, if (keepId != null) taken - keepId else taken)
        return if (problem != null) Built.Error(problem) else Built.Ok(site)
    }

    @Serializable
    private data class FileModel(val sites: List<Site> = emptyList())

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(sites: List<Site>): String = json.encodeToString(FileModel(sites))

    /** Relit le fichier local. Illisible ou partiellement invalide : on garde ce qui est valide. */
    fun decode(text: String): List<Site> {
        val raw = try {
            json.decodeFromString<FileModel>(text).sites
        } catch (_: Exception) {
            return emptyList()
        }
        val taken = mutableSetOf<String>()
        val out = mutableListOf<Site>()
        for (site in raw) {
            if (!isLocalId(site.id) || ConfigParser.validateSite(site, taken) != null) continue
            taken += site.id
            out += site
            if (out.size >= MAX_LOCAL) break
        }
        return out
    }
}
