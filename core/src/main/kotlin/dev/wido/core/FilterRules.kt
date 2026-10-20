package dev.wido.core

/**
 * Résultat de l'analyse de règles : des hôtes à bloquer, des exceptions, et des règles cosmétiques.
 *
 * Règles cosmétiques : `hide` et `unhide` associent un domaine (`""` = tous les sites) à des
 * sélecteurs CSS à masquer, ou à ne pas masquer. `noGenericHide` (`$generichide`) coupe les
 * sélecteurs génériques sur un domaine, `noHide` (`$elemhide`) coupe tout le masquage.
 */
data class HostRules(
    val blocked: Set<String> = emptySet(),
    val allowed: Set<String> = emptySet(),
    /** Lignes comprises mais non gérées (chemins, options, règles procédurales). */
    val skipped: Int = 0,
    val hide: Map<String, Set<String>> = emptyMap(),
    val unhide: Map<String, Set<String>> = emptyMap(),
    val noGenericHide: Set<String> = emptySet(),
    val noHide: Set<String> = emptySet(),
) {
    /** Nombre de sélecteurs cosmétiques (génériques et par domaine). */
    val selectorCount: Int get() = hide.values.sumOf { it.size }

    companion object {
        fun merge(parts: List<HostRules>): HostRules {
            val blocked = HashSet<String>()
            val allowed = HashSet<String>()
            val hide = HashMap<String, MutableSet<String>>()
            val unhide = HashMap<String, MutableSet<String>>()
            val noGeneric = HashSet<String>()
            val noHide = HashSet<String>()
            var skipped = 0
            for (p in parts) {
                blocked += p.blocked
                allowed += p.allowed
                skipped += p.skipped
                p.hide.forEach { (d, s) -> hide.getOrPut(d) { LinkedHashSet() } += s }
                p.unhide.forEach { (d, s) -> unhide.getOrPut(d) { LinkedHashSet() } += s }
                noGeneric += p.noGenericHide
                noHide += p.noHide
            }
            return HostRules(blocked, allowed, skipped, hide, unhide, noGeneric, noHide)
        }
    }
}

/**
 * Lit un sous-ensemble du format AdBlock (EasyList, uBlock) et le format hosts.
 *
 * Réseau, au niveau du nom d'hôte uniquement (comme un DNS), jamais sur le chemin d'une URL :
 * - `||pub.exemple.com^`   bloque pub.exemple.com et ses sous-domaines
 * - `||pub.exemple.com^$third-party,script` : les options de type ou de tiers sont acceptées
 *   (l'hôte est alors bloqué entièrement, ce qui est le cas des régies publicitaires)
 * - `@@||cdn.exemple.com^` exception
 * - `0.0.0.0 pub.exemple.com`, ou `pub.exemple.com` seul
 *
 * Cosmétique :
 * - `##.banniere` masque partout, `exemple.com,autre.fr##.banniere` sur ces sites
 * - `exemple.com#@#.banniere` exception
 * - `@@||exemple.com^$generichide` / `$elemhide` : coupe le masquage générique / tout le masquage
 *
 * Sont comptées dans `skipped` : règles à chemin ou joker, options restreintes à des domaines
 * (`domain=`) ou qui modifient la réponse, et règles cosmétiques procédurales (`:has-text`…).
 */
object FilterParser {
    private val hostsLine = Regex("^(?:0\\.0\\.0\\.0|127\\.0\\.0\\.1|::1?)\\s+(\\S+)")

    /** Options qui ne changent pas la nature d'une règle de blocage d'hôte. */
    private val BLOCK_OPTIONS = setOf(
        "third-party", "3p", "all", "popup", "important", "document", "doc", "script", "image",
        "subdocument", "frame", "xmlhttprequest", "xhr", "media", "object", "ping", "websocket",
        "other", "stylesheet", "css", "font", "beacon",
    )
    private val ALLOW_OPTIONS = setOf("important", "document", "doc", "all")
    private val GENERIC_HIDE = setOf("generichide", "ghide")
    private val ELEM_HIDE = setOf("elemhide", "ehide")

    /** Extensions procédurales d'uBlock / AdGuard : pas du CSS valide. */
    private val PROCEDURAL = listOf(
        ":-abp-", ":has-text(", ":contains(", ":xpath(", ":upward(", ":remove(", ":style(", ":matches-css",
        ":min-text-length(", ":watch-attr(", ":others(", ":matches-path(", ":matches-attr(", ":remove-attr(",
        ":remove-class(", ":matches-media(", ":matches-prop(", ":nth-ancestor(", ":if(", ":if-not(",
    )

    const val MAX_SELECTOR = 400

    fun parse(lines: Sequence<String>): HostRules {
        val blocked = HashSet<String>()
        val allowed = HashSet<String>()
        val hide = HashMap<String, MutableSet<String>>()
        val unhide = HashMap<String, MutableSet<String>>()
        val noGeneric = HashSet<String>()
        val noHide = HashSet<String>()
        var skipped = 0
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '[') continue
            if (line[0] == '#' && !line.startsWith("##") && !line.startsWith("#@#")) continue // commentaire hosts
            val ok = if (line.contains("#@#") || line.contains("##")) {
                cosmetic(line, hide, unhide)
            } else {
                val hostsMatch = hostsLine.find(line)
                when {
                    hostsMatch != null -> {
                        val h = Hosts.normalize(hostsMatch.groupValues[1])
                        (h != "localhost" && Hosts.isValidHostname(h)).also { if (it) blocked += h }
                    }
                    line.startsWith("@@||") -> exception(line.substring(4), allowed, noGeneric, noHide)
                    line.startsWith("||") -> {
                        val parsed = adblockHost(line.substring(2))
                        (parsed != null && parsed.second.all { it in BLOCK_OPTIONS }).also { if (it) blocked += parsed!!.first }
                    }
                    line.any { it == '#' || it == '$' || it == '/' || it == '*' || it == '|' || it == '@' } -> false
                    else -> {
                        val h = Hosts.normalize(line)
                        Hosts.isValidHostname(h).also { if (it) blocked += h }
                    }
                }
            }
            if (!ok) skipped++
        }
        return HostRules(blocked, allowed, skipped, hide, unhide, noGeneric, noHide)
    }

    fun parse(text: String): HostRules = parse(text.lineSequence())

    private fun exception(body: String, allowed: MutableSet<String>, noGeneric: MutableSet<String>, noHide: MutableSet<String>): Boolean {
        val (host, options) = adblockHost(body) ?: return false
        return when {
            options.isEmpty() || options.all { it in ALLOW_OPTIONS } -> { allowed += host; true }
            options.all { it in GENERIC_HIDE } -> { noGeneric += host; true }
            options.all { it in ELEM_HIDE || it in GENERIC_HIDE } -> { noHide += host; true }
            else -> false
        }
    }

    /** `exemple.com^$opt1,opt2` → (hôte, options), ou null s'il y a un chemin ou un joker. */
    private fun adblockHost(body: String): Pair<String, List<String>>? {
        val dollar = body.indexOf('$')
        val target = if (dollar >= 0) body.substring(0, dollar) else body
        val options = if (dollar >= 0) {
            body.substring(dollar + 1).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        val caret = target.indexOf('^')
        val host = if (caret >= 0) {
            if (target.length > caret + 1) return null // chemin après le ^
            target.substring(0, caret)
        } else {
            target
        }
        val normalized = Hosts.normalize(host).takeIf { Hosts.isValidHostname(it) } ?: return null
        return normalized to options
    }

    private fun cosmetic(line: String, hide: MutableMap<String, MutableSet<String>>, unhide: MutableMap<String, MutableSet<String>>): Boolean {
        val isException = line.contains("#@#")
        val marker = if (isException) "#@#" else "##"
        val at = line.indexOf(marker)
        val domainsPart = line.substring(0, at)
        val selector = line.substring(at + marker.length).trim()
        // `#?#`, `#$#`, `#%#` et `#@?#` : règles procédurales ou scripts, hors de portée.
        if (domainsPart.isNotEmpty() && domainsPart.last() in "#?$%@") return false
        if (!validSelector(selector)) return false
        val domains = if (domainsPart.isBlank()) {
            listOf("")
        } else {
            val parts = domainsPart.split(',').map { Hosts.normalize(it) }
            // Domaines exclus (`~site`) ou génériques (`site.*`) : on ne sait pas les appliquer exactement.
            if (parts.any { it.startsWith("~") || !Hosts.isValidHostname(it) }) return false
            parts
        }
        val target = if (isException) unhide else hide
        for (d in domains) target.getOrPut(d) { LinkedHashSet() } += selector
        return true
    }

    private fun validSelector(s: String): Boolean {
        if (s.isEmpty() || s.length > MAX_SELECTOR) return false
        if (s.startsWith("+js") || s.startsWith("^") || s.startsWith("%")) return false
        if (s.any { it == '{' || it == '}' || it == '<' || it == '\n' }) return false
        return PROCEDURAL.none { s.contains(it) }
    }
}

/**
 * Décide si une requête vers un hôte doit être bloquée. Une exception l'emporte toujours.
 *
 * Les règles restent en **couches** (listes distantes, règles globales, règles du site) : on ne
 * recopie pas des dizaines de milliers de domaines à chaque ouverture de site. Les décisions sont
 * mises en cache par hôte : une page émet des centaines de requêtes vers quelques dizaines d'hôtes.
 */
class HostBlocker(private val layers: List<HostRules>) {
    constructor(rules: HostRules) : this(listOf(rules))

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun isBlocked(host: String): Boolean {
        val h = Hosts.normalize(host)
        if (h.isEmpty()) return false
        cache[h]?.let { return it }
        val decision = decide(h)
        if (cache.size < MAX_CACHE) cache[h] = decision
        return decision
    }

    private fun decide(host: String): Boolean {
        if (layers.any { inSuffixes(host, it.allowed) }) return false
        return layers.any { inSuffixes(host, it.blocked) }
    }

    private fun inSuffixes(host: String, set: Set<String>): Boolean {
        if (set.isEmpty()) return false
        var cur = host
        while (true) {
            if (cur in set) return true
            val dot = cur.indexOf('.')
            if (dot < 0) return false
            cur = cur.substring(dot + 1)
        }
    }

    private companion object {
        const val MAX_CACHE = 4096
    }
}
