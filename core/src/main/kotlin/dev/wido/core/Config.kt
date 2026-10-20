package dev.wido.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Version du format de fichier que cette version de l'app sait lire. */
const val SUPPORTED_SCHEMA = 1

@Serializable
data class WiDoConfig(
    /** Version du format (pas du contenu). */
    val schema: Int = 1,
    /** Numéro de révision du contenu, libre, pour t'y retrouver. */
    val revision: Int = 0,
    /** URL (https) de listes de blocage : format AdBlock `||domaine^` ou fichier hosts. */
    val blocklists: List<String> = emptyList(),
    /** Règles de blocage écrites directement ici, appliquées à tous les sites. */
    val globalRules: List<String> = emptyList(),
    /** Domaines jamais bloqués, pour tous les sites. */
    val whitelist: List<String> = emptyList(),
    val sites: List<Site> = emptyList(),
)

@Serializable
data class Site(
    /** Identifiant stable : minuscules, chiffres et tirets. Sert à lancer le site depuis Home Assistant. */
    val id: String,
    val name: String,
    val url: String,
    /** Domaines supplémentaires où la navigation est permise (le domaine de [url] l'est déjà). */
    val allowedDomains: List<String> = emptyList(),
    /** Règles de blocage propres à ce site. */
    val rules: List<String> = emptyList(),
    /** Scripts intégrés à l'app à injecter (voir docs/SCRIPTS.md). */
    val scripts: List<String> = emptyList(),
    /** Sélecteurs CSS à masquer sur ce site. */
    val hideSelectors: List<String> = emptyList(),
    val enabled: Boolean = true,
    /** `default`, `desktop`, ou une chaîne d'agent utilisateur complète. */
    val userAgent: String = "default",
    /** Couleur d'accent de la carte, ex. `#E50914`. */
    val color: String? = null,
    /** URL (https) d'une icône à la place du favicon détecté automatiquement. */
    val icon: String? = null,
    /** Autorise http (réseau local, serveur maison). Désactivé par défaut. */
    val allowInsecure: Boolean = false,
    /**
     * `strict` : la fenêtre ne navigue que sur les domaines du site (le plus sûr contre les pubs).
     * `relaxed` : navigation libre, sauf vers les domaines bloqués par les listes (YouTube, connexions).
     * `mixed` : comme `strict`, mais une redirection ou une pop-up publicitaire s'ouvre dans une fenêtre
     *  à part, affichée quelques secondes puis fermée (sites qui exigent que la pub soit vue).
     */
    val navigation: String = Navigation.STRICT,
    /** Faux : les listes de blocage et règles globales ne s'appliquent pas à ce site (seules ses propres règles). */
    val blockAds: Boolean = true,
)

object Navigation {
    const val STRICT = "strict"
    const val RELAXED = "relaxed"
    const val MIXED = "mixed"
    val ALL = setOf(STRICT, RELAXED, MIXED)
}

class ConfigException(message: String) : Exception(message)

data class ParsedConfig(val config: WiDoConfig, val warnings: List<String>)

object ConfigParser {
    const val MAX_SITES = 100
    const val MAX_RULES = 5000
    val ID_REGEX = Regex("^[a-z0-9][a-z0-9-]{0,31}$")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Lit et valide une configuration. Un site invalide est écarté avec un avertissement
     * plutôt que de rejeter tout le fichier : une faute de frappe ne doit pas vider le menu.
     * Seuls un JSON illisible ou un format trop récent lèvent une [ConfigException].
     */
    fun parse(text: String): ParsedConfig {
        val raw = try {
            json.decodeFromString<WiDoConfig>(text)
        } catch (e: Exception) {
            throw ConfigException("JSON illisible : ${e.message?.lineSequence()?.firstOrNull()}")
        }
        if (raw.schema > SUPPORTED_SCHEMA) {
            throw ConfigException(
                "Format ${raw.schema} trop récent (cette version lit jusqu'à $SUPPORTED_SCHEMA) : mets l'app à jour.",
            )
        }
        val warnings = mutableListOf<String>()

        val lists = raw.blocklists.filter {
            (Hosts.schemeOf(it) == "https" && Hosts.of(it) != null).also { ok ->
                if (!ok) warnings += "Liste ignorée (https requis) : $it"
            }
        }
        val globalRules = raw.globalRules.take(MAX_RULES)
        if (raw.globalRules.size > MAX_RULES) warnings += "globalRules tronqué à $MAX_RULES règles."

        val seen = mutableSetOf<String>()
        val sites = mutableListOf<Site>()
        for (site in raw.sites.take(MAX_SITES)) {
            val problem = validateSite(site, seen)
            if (problem != null) {
                warnings += "Site « ${site.id} » ignoré : $problem"
            } else {
                seen += site.id
                val icon = site.icon?.takeIf { Hosts.schemeOf(it) == "https" && Hosts.of(it) != null }
                if (site.icon != null && icon == null) warnings += "Site « ${site.id} » : icône ignorée (https requis)"
                val nav = site.navigation.lowercase()
                if (nav !in Navigation.ALL) warnings += "Site « ${site.id} » : navigation « ${site.navigation} » inconnue, « strict » utilisé"
                sites += site.copy(
                    rules = site.rules.take(MAX_RULES),
                    icon = icon,
                    navigation = if (nav in Navigation.ALL) nav else Navigation.STRICT,
                )
            }
        }
        if (raw.sites.size > MAX_SITES) warnings += "Plus de $MAX_SITES sites : les suivants sont ignorés."

        return ParsedConfig(
            raw.copy(blocklists = lists, globalRules = globalRules, sites = sites),
            warnings,
        )
    }

    /** Pourquoi ce site est inacceptable, ou null s'il est valide. `taken` : identifiants déjà utilisés. */
    fun validateSite(site: Site, taken: Set<String>): String? {
        if (!ID_REGEX.matches(site.id)) return "identifiant invalide (a-z, 0-9, tirets, 32 max)"
        if (site.id in taken) return "identifiant en double"
        if (site.name.isBlank()) return "nom vide"
        val scheme = Hosts.schemeOf(site.url)
        if (Hosts.of(site.url) == null) return "URL invalide"
        if (scheme != "https" && !(scheme == "http" && site.allowInsecure)) {
            return "https requis (ou allowInsecure pour un serveur local)"
        }
        return null
    }
}
