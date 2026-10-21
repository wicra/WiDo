package dev.wido.core

/**
 * Résultat de l'analyse de règles : des hôtes à bloquer, des exceptions, des règles d'URL, des
 * règles cosmétiques et des scriptlets.
 *
 * - `hide` / `unhide` : domaine (`""` = tous les sites, `site.*` = toutes ses extensions) → sélecteurs
 *   CSS à masquer, ou à ne pas masquer. `noGenericHide` (`$generichide`) coupe les sélecteurs génériques
 *   sur un domaine, `noHide` (`$elemhide`) coupe tout le masquage.
 * - `urls` : règles réseau sur l'adresse complète (chemin, joker, `domain=`…), gardées telles quelles et
 *   appliquées par [UrlFilter].
 * - `scripts` / `unscripts` : domaine → scriptlets (`##+js(...)`), voir `internal/scriptlets.js`.
 */
data class HostRules(
    val blocked: Set<String> = emptySet(),
    val allowed: Set<String> = emptySet(),
    /** Lignes comprises mais non gérées (expressions régulières, options qui modifient la réponse…). */
    val skipped: Int = 0,
    val hide: Map<String, Set<String>> = emptyMap(),
    val unhide: Map<String, Set<String>> = emptyMap(),
    val noGenericHide: Set<String> = emptySet(),
    val noHide: Set<String> = emptySet(),
    val urls: List<String> = emptyList(),
    val scripts: Map<String, Set<String>> = emptyMap(),
    val unscripts: Map<String, Set<String>> = emptyMap(),
) {
    /**
     * Même règles, domaines bloqués sous forme compacte ([CompactHostSet]) : bien moins de mémoire pour
     * les grosses listes. À réserver aux règles qui ne seront plus ré-encodées ni fusionnées.
     */
    fun compact(): HostRules = if (blocked is CompactHostSet) this else copy(blocked = CompactHostSet.of(blocked))

    /** Nombre de sélecteurs cosmétiques (génériques et par domaine). */
    val selectorCount: Int get() = hide.values.sumOf { it.size }

    /** Nombre de règles d'URL et de scriptlets. */
    val advancedCount: Int get() = urls.size + scripts.values.sumOf { it.size }

    companion object {
        fun merge(parts: List<HostRules>): HostRules {
            val blocked = HashSet<String>()
            val allowed = HashSet<String>()
            val hide = HashMap<String, MutableSet<String>>()
            val unhide = HashMap<String, MutableSet<String>>()
            val scripts = HashMap<String, MutableSet<String>>()
            val unscripts = HashMap<String, MutableSet<String>>()
            val noGeneric = HashSet<String>()
            val noHide = HashSet<String>()
            val urls = ArrayList<String>()
            var skipped = 0
            for (p in parts) {
                blocked += p.blocked
                allowed += p.allowed
                skipped += p.skipped
                p.hide.forEach { (d, s) -> hide.getOrPut(d) { LinkedHashSet() } += s }
                p.unhide.forEach { (d, s) -> unhide.getOrPut(d) { LinkedHashSet() } += s }
                p.scripts.forEach { (d, s) -> scripts.getOrPut(d) { LinkedHashSet() } += s }
                p.unscripts.forEach { (d, s) -> unscripts.getOrPut(d) { LinkedHashSet() } += s }
                noGeneric += p.noGenericHide
                noHide += p.noHide
                urls += p.urls
            }
            return HostRules(blocked, allowed, skipped, hide, unhide, noGeneric, noHide, urls, scripts, unscripts)
        }
    }
}

/**
 * Lit le format AdBlock (EasyList, uBlock Origin) et le format hosts.
 *
 * Réseau :
 * - `||pub.exemple.com^` bloque l'hôte et ses sous-domaines ; avec des options de type ou de tiers
 *   (`$third-party,script`), l'hôte est bloqué entièrement (cas des régies publicitaires) ;
 * - `@@||cdn.exemple.com^` exception d'hôte ;
 * - `0.0.0.0 pub.exemple.com`, ou `pub.exemple.com` seul ;
 * - règles d'adresse : `||site.com/pubs/`, `/banner/$image`, `-ad-banner.`, `$domain=`,
 *   `$third-party`, `$important`, et leurs exceptions `@@` ([UrlFilter]).
 *
 * Cosmétique : `##.banniere`, `site.com,autre.*##.banniere`, `site.com#@#.banniere`,
 * `@@||site.com^$generichide` / `$elemhide`.
 *
 * Scriptlets uBlock : `site.com##+js(set-constant, adBlockDetected, false)` et `#@#+js(...)`.
 *
 * Directives des listes uBlock : `!#if env_mobile` … `!#else` … `!#endif`.
 *
 * Sont comptées dans `skipped` : expressions régulières, options qui modifient la réponse
 * (`$redirect`, `$removeparam`, `$csp`…), règles cosmétiques procédurales (`:has-text()`…),
 * scriptlets génériques (sans domaine) et domaines exclus (`~site`).
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

    /** Ce que les directives `!#if` des listes uBlock savent de WiDo : un moteur Chromium sur appareil Android. */
    private val ENV_TRUE = setOf("env_chromium", "env_mobile", "ext_ublock", "cap_user_stylesheet")

    const val MAX_SELECTOR = 400
    private val ADGUARD_COSMETIC = Regex("#@?[?$%]#")

    fun parse(lines: Sequence<String>): HostRules {
        val blocked = HashSet<String>()
        val allowed = HashSet<String>()
        val hide = HashMap<String, MutableSet<String>>()
        val unhide = HashMap<String, MutableSet<String>>()
        val scripts = HashMap<String, MutableSet<String>>()
        val unscripts = HashMap<String, MutableSet<String>>()
        val noGeneric = HashSet<String>()
        val noHide = HashSet<String>()
        val urls = ArrayList<String>()
        var skipped = 0
        // Pile des blocs `!#if` : vrai si le bloc est actif.
        val conditions = ArrayDeque<Boolean>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("!#if ")) {
                conditions.addLast(evaluate(line.substring(5)))
                continue
            }
            if (line.startsWith("!#else")) {
                if (conditions.isNotEmpty()) conditions.addLast(!conditions.removeLast())
                continue
            }
            if (line.startsWith("!#endif")) {
                conditions.removeLastOrNull()
                continue
            }
            if (conditions.any { !it }) continue
            if (line.isEmpty() || line[0] == '!' || line[0] == '[') continue
            if (line[0] == '#' && !line.startsWith("##") && !line.startsWith("#@#")) continue // commentaire hosts
            val ok = if (line.contains("#@#") || line.contains("##")) {
                cosmetic(line, hide, unhide, scripts, unscripts)
            } else {
                val hostsMatch = hostsLine.find(line)
                when {
                    // Règles procédurales ou scripts AdGuard (`#?#`, `#$#`, `#%#`) : hors de portée.
                    ADGUARD_COSMETIC.containsMatchIn(line) -> false
                    hostsMatch != null -> {
                        val h = Hosts.normalize(hostsMatch.groupValues[1])
                        (h != "localhost" && Hosts.isValidHostname(h)).also { if (it) blocked += h }
                    }
                    line.startsWith("@@||") && hostException(line.substring(4), allowed, noGeneric, noHide) -> true
                    line.startsWith("||") && hostBlock(line.substring(2), blocked) -> true
                    line.startsWith("||") || line.startsWith("|") || line.startsWith("@@") ||
                        line.any { it == '/' || it == '*' || it == '$' || it == '^' } ->
                        (UrlRule.parse(line) != null).also { if (it) urls += line }
                    else -> {
                        val h = Hosts.normalize(line)
                        when {
                            Hosts.isValidHostname(h) -> { blocked += h; true }
                            // Fragment d'adresse (`-ad-banner.`, `_pub_`) : règle d'adresse sans joker.
                            else -> (UrlRule.parse(line) != null).also { if (it) urls += line }
                        }
                    }
                }
            }
            if (!ok) skipped++
        }
        return HostRules(blocked, allowed, skipped, hide, unhide, noGeneric, noHide, urls, scripts, unscripts)
    }

    fun parse(text: String): HostRules = parse(text.lineSequence())

    /** `env_mobile`, `!env_firefox`, `env_chromium && !env_mobile`, `(a || b)`… */
    private fun evaluate(expression: String): Boolean =
        expression.split("||").any { alternative ->
            alternative.split("&&").all { term ->
                val t = term.trim().trim('(', ')').trim()
                if (t.startsWith("!")) t.substring(1).trim() !in ENV_TRUE else t in ENV_TRUE
            }
        }

    private fun hostBlock(body: String, blocked: MutableSet<String>): Boolean {
        val (host, options) = adblockHost(body) ?: return false
        if (!options.all { it in BLOCK_OPTIONS }) return false
        blocked += host
        return true
    }

    private fun hostException(body: String, allowed: MutableSet<String>, noGeneric: MutableSet<String>, noHide: MutableSet<String>): Boolean {
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

    private fun cosmetic(
        line: String,
        hide: MutableMap<String, MutableSet<String>>,
        unhide: MutableMap<String, MutableSet<String>>,
        scripts: MutableMap<String, MutableSet<String>>,
        unscripts: MutableMap<String, MutableSet<String>>,
    ): Boolean {
        val isException = line.contains("#@#")
        val marker = if (isException) "#@#" else "##"
        val at = line.indexOf(marker)
        val domainsPart = line.substring(0, at)
        val body = line.substring(at + marker.length).trim()
        // `#?#`, `#$#`, `#%#` et `#@?#` : règles procédurales ou scripts AdGuard, hors de portée.
        if (domainsPart.isNotEmpty() && domainsPart.last() in "#?$%@") return false
        val domains = if (domainsPart.isBlank()) {
            listOf("")
        } else {
            val parts = domainsPart.split(',').map { Hosts.normalize(it) }
            // Domaines exclus (`~site`) : on ne sait pas les appliquer exactement.
            if (parts.any { it.startsWith("~") || !(Hosts.isValidHostname(it) || Hosts.isEntity(it)) }) return false
            parts
        }
        if (body.startsWith("+js(") && body.endsWith(")")) {
            val call = body.substring(4, body.length - 1).trim()
            // Les scriptlets génériques (sans domaine) sont réservés aux exceptions : `#@#+js()` coupe tout.
            if (call.isEmpty() && !isException) return false
            if (domains == listOf("") && !isException) return false
            val target = if (isException) unscripts else scripts
            for (d in domains) target.getOrPut(d) { LinkedHashSet() } += call
            return true
        }
        if (!validSelector(body)) return false
        val target = if (isException) unhide else hide
        for (d in domains) target.getOrPut(d) { LinkedHashSet() } += body
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
