package dev.wido.core

/** Type d'une requête, deviné par l'app : la WebView ne le donne pas. */
enum class RequestType {
    DOCUMENT, SUBDOCUMENT, SCRIPT, STYLESHEET, IMAGE, MEDIA, FONT, XHR, WEBSOCKET, PING, OTHER;

    companion object {
        private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif", "bmp")
        private val MEDIA_EXT = setOf("mp4", "webm", "m3u8", "mpd", "ts", "m4s", "m4a", "mp3", "aac", "ogg")
        private val FONT_EXT = setOf("woff", "woff2", "ttf", "otf", "eot")

        /** D'après l'en-tête `Accept`, l'extension de l'adresse, et s'il s'agit de la page principale. */
        fun guess(url: String, accept: String?, mainFrame: Boolean): RequestType {
            if (mainFrame) return DOCUMENT
            val a = accept.orEmpty().lowercase()
            val path = url.substringBefore('?').substringBefore('#')
            val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
            return when {
                a.startsWith("text/html") -> SUBDOCUMENT
                a.startsWith("text/css") || ext == "css" -> STYLESHEET
                a.startsWith("image/") || ext in IMAGE_EXT -> IMAGE
                a.startsWith("video/") || a.startsWith("audio/") || ext in MEDIA_EXT -> MEDIA
                ext == "js" || ext == "mjs" -> SCRIPT
                ext in FONT_EXT -> FONT
                a.contains("json") -> XHR
                else -> OTHER
            }
        }

        fun parse(option: String): RequestType? = when (option) {
            "script" -> SCRIPT
            "stylesheet", "css" -> STYLESHEET
            "image" -> IMAGE
            "media" -> MEDIA
            "font" -> FONT
            "xmlhttprequest", "xhr" -> XHR
            "subdocument", "frame" -> SUBDOCUMENT
            "websocket" -> WEBSOCKET
            "ping", "beacon" -> PING
            "object", "other" -> OTHER
            else -> null
        }
    }
}

/**
 * Une règle d'adresse du format AdBlock : motif (`*`, `^`, `|`, `||`) et options (`$third-party`,
 * `$domain=`, types, `$important`, `$match-case`). Les règles qui modifient la réponse ou ne visent que
 * des pages entières sont refusées par [parse] : WiDo bloque, il ne réécrit pas.
 */
class UrlRule private constructor(
    val exception: Boolean,
    val important: Boolean,
    /** Mot de l'adresse qui doit être présent pour que la règle puisse s'appliquer ; null = à tester toujours. */
    val token: String?,
    private val source: String,
    private val matchCase: Boolean,
    private val thirdParty: Boolean?,
    private val types: Set<RequestType>?,
    private val includeDomains: DomainSet,
    private val excludeDomains: DomainSet,
) {
    // Compilée au premier usage : la plupart des règles ne servent jamais pendant une soirée.
    // Une règle dont le motif ne compile pas ne bloque rien (et ne fait jamais planter le lecteur).
    private val regex: Regex? by lazy {
        runCatching { Regex(source, if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)) }.getOrNull()
    }

    fun matches(url: String, pageHost: String, type: RequestType, isThirdParty: Boolean): Boolean {
        if (thirdParty != null && thirdParty != isThirdParty) return false
        if (types != null && type !in types) return false
        if (!includeDomains.isEmpty && !includeDomains.contains(pageHost)) return false
        if (excludeDomains.contains(pageHost)) return false
        return regex?.containsMatchIn(url) == true
    }

    /** Vrai si le motif compile (diagnostic, tests). */
    internal fun isValid(): Boolean = regex != null

    companion object {
        private val NEVER_TOKENS = setOf("http", "https", "www", "com")

        /** La règle, ou null si WiDo ne sait pas l'appliquer fidèlement. */
        fun parse(line: String): UrlRule? {
            var body = line.trim()
            val exception = body.startsWith("@@")
            if (exception) body = body.substring(2)
            if (body.isEmpty()) return null

            val dollar = body.lastIndexOf('$')
            val pattern: String
            val options: List<String>
            if (dollar >= 0 && body.indexOf('/', dollar) < 0) {
                pattern = body.substring(0, dollar)
                options = body.substring(dollar + 1).split(',').map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                pattern = body
                options = emptyList()
            }
            // Expressions régulières (`/…/`) : hors de portée.
            if (pattern.length > 2 && pattern.startsWith("/") && pattern.endsWith("/")) return null

            var important = false
            var matchCase = false
            var thirdParty: Boolean? = null
            val types = mutableSetOf<RequestType>()
            val notTypes = mutableSetOf<RequestType>()
            val include = mutableListOf<String>()
            val exclude = mutableListOf<String>()
            for (raw in options) {
                val negated = raw.startsWith("~")
                val option = raw.removePrefix("~").lowercase()
                val name = option.substringBefore('=')
                when {
                    name == "third-party" || name == "3p" -> thirdParty = !negated
                    name == "first-party" || name == "1p" -> thirdParty = negated
                    name == "important" -> important = true
                    name == "match-case" -> matchCase = true
                    name == "all" -> Unit
                    (name == "domain" || name == "from") && option.contains('=') -> {
                        for (d in option.substringAfter('=').split('|')) {
                            val target = if (d.startsWith("~")) exclude else include
                            val domain = Hosts.normalize(d.removePrefix("~"))
                            if (!Hosts.isValidHostname(domain) && !Hosts.isEntity(domain)) return null
                            target += domain
                        }
                    }
                    else -> {
                        // Pages entières, pop-ups, réécritures (`redirect`, `removeparam`, `csp`…) : non.
                        val type = RequestType.parse(name) ?: return null
                        if (negated) notTypes += type else types += type
                    }
                }
            }
            // Un motif vide ou `*` ne vaut que limité à des sites : sinon il bloquerait tout.
            if ((pattern.isEmpty() || pattern == "*") && include.isEmpty()) return null
            val typeSet = when {
                types.isNotEmpty() -> types - notTypes
                notTypes.isNotEmpty() -> RequestType.entries.toSet() - RequestType.DOCUMENT - notTypes
                else -> null
            }
            if (typeSet != null && typeSet.isEmpty()) return null
            return UrlRule(
                exception, important, bestToken(pattern), toRegex(pattern), matchCase, thirdParty, typeSet,
                DomainSet(include), DomainSet(exclude),
            )
        }

        /** Motif AdBlock → expression régulière. */
        internal fun toRegex(pattern: String): String {
            val out = StringBuilder()
            var start = 0
            when {
                pattern.startsWith("||") -> { out.append("^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?"); start = 2 }
                pattern.startsWith("|") -> { out.append('^'); start = 1 }
            }
            val anchoredEnd = pattern.length > start && pattern.endsWith("|")
            val end = if (anchoredEnd) pattern.length - 1 else pattern.length
            for (i in start until end) {
                when (val c = pattern[i]) {
                    '*' -> out.append(".*")
                    '^' -> out.append("(?:[^a-zA-Z0-9_.%-]|$)")
                    else -> if (c.isLetterOrDigit()) out.append(c) else out.append('\\').append(c)
                }
            }
            if (anchoredEnd) out.append('$')
            return out.toString()
        }

        /**
         * Le mot le plus long du motif qui sera forcément un mot entier de l'adresse (ni collé à un
         * joker, ni coupé en début ou fin de motif). Il sert d'index : une règle n'est testée que si
         * son mot apparaît dans l'adresse.
         */
        internal fun bestToken(pattern: String): String? {
            val p = pattern.lowercase()
            var start = 0
            var anchoredStart = false
            if (p.startsWith("||")) { start = 2; anchoredStart = true } else if (p.startsWith("|")) { start = 1; anchoredStart = true }
            val anchoredEnd = p.length > start && p.endsWith("|")
            val end = if (anchoredEnd) p.length - 1 else p.length
            var best: String? = null
            var i = start
            while (i < end) {
                if (!isTokenChar(p[i])) { i++; continue }
                val runStart = i
                while (i < end && isTokenChar(p[i])) i++
                val before = if (runStart == start) null else p[runStart - 1]
                val after = if (i == end) null else p[i]
                val startOk = if (before == null) anchoredStart else before != '*'
                val endOk = if (after == null) anchoredEnd else after != '*'
                val run = p.substring(runStart, i)
                if (startOk && endOk && run.length >= 2 && run !in NEVER_TOKENS && (best == null || run.length > best.length)) best = run
            }
            return best
        }

        internal fun isTokenChar(c: Char) = c in 'a'..'z' || c in '0'..'9' || c == '%'

        /** Les mots d'une adresse, au sens de [bestToken]. */
        internal fun tokens(url: String): Set<String> {
            val out = HashSet<String>()
            val u = url.lowercase()
            var i = 0
            while (i < u.length) {
                if (!isTokenChar(u[i])) { i++; continue }
                val s = i
                while (i < u.length && isTokenChar(u[i])) i++
                if (i - s >= 2) out += u.substring(s, i)
            }
            return out
        }
    }
}

/**
 * Domaines d'une option `domain=` : table des domaines exacts (on remonte les niveaux de l'hôte) et
 * liste courte des domaines entité (`site.*`). Certaines règles visent des centaines de sites.
 */
internal class DomainSet(domains: List<String>) {
    private val exact = domains.filterNot { it.endsWith(".*") }.toHashSet()
    private val entities = domains.filter { it.endsWith(".*") }

    val isEmpty = exact.isEmpty() && entities.isEmpty()

    fun contains(host: String): Boolean {
        if (isEmpty) return false
        if (exact.isNotEmpty()) {
            var cur = host
            while (true) {
                if (cur in exact) return true
                val dot = cur.indexOf('.')
                if (dot < 0) break
                cur = cur.substring(dot + 1)
            }
        }
        return entities.any { Hosts.matchesPattern(host, it) }
    }
}

/** Règles d'adresse indexées par mot : chaque requête ne teste que les quelques règles qui peuvent la concerner. */
class UrlFilter(lines: Iterable<String>) {
    private val block = Index()
    private val allow = Index()

    var size = 0
        private set

    init {
        for (line in lines) {
            val rule = UrlRule.parse(line) ?: continue
            (if (rule.exception) allow else block).add(rule)
            size++
        }
    }

    internal fun findBlock(url: String, tokens: Set<String>, host: String, type: RequestType, thirdParty: Boolean, importantOnly: Boolean) =
        block.find(url, tokens, host, type, thirdParty, importantOnly)

    internal fun findAllow(url: String, tokens: Set<String>, host: String, type: RequestType, thirdParty: Boolean) =
        allow.find(url, tokens, host, type, thirdParty, false)

    private class Index {
        val byToken = HashMap<String, MutableList<UrlRule>>()
        val anywhere = ArrayList<UrlRule>()

        fun add(rule: UrlRule) {
            val t = rule.token
            if (t == null) anywhere += rule else byToken.getOrPut(t) { ArrayList(2) } += rule
        }

        fun find(url: String, tokens: Set<String>, host: String, type: RequestType, thirdParty: Boolean, importantOnly: Boolean): UrlRule? {
            for (t in tokens) {
                val list = byToken[t] ?: continue
                for (r in list) if ((!importantOnly || r.important) && r.matches(url, host, type, thirdParty)) return r
            }
            for (r in anywhere) if ((!importantOnly || r.important) && r.matches(url, host, type, thirdParty)) return r
            return null
        }
    }
}

/**
 * Plusieurs [UrlFilter] (listes, règles globales, règles du site) jugés ensemble : une exception de
 * n'importe quelle couche l'emporte, sauf sur une règle `$important`.
 */
class UrlMatcher(private val filters: List<UrlFilter>) {
    val size: Int get() = filters.sumOf { it.size }

    /** `pageHost` : l'hôte de la page affichée ; `thirdParty` : la requête vise un autre site que la page. */
    fun shouldBlock(url: String, pageHost: String, type: RequestType, thirdParty: Boolean): Boolean {
        if (filters.isEmpty()) return false
        val tokens = UrlRule.tokens(url)
        if (filters.any { it.findBlock(url, tokens, pageHost, type, thirdParty, importantOnly = true) != null }) return true
        if (filters.none { it.findBlock(url, tokens, pageHost, type, thirdParty, importantOnly = false) != null }) return false
        return filters.none { it.findAllow(url, tokens, pageHost, type, thirdParty) != null }
    }

    companion object {
        val EMPTY = UrlMatcher(emptyList())
    }
}
