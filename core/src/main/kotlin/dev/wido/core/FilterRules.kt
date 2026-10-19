package dev.wido.core

/** Résultat de l'analyse de règles : des hôtes à bloquer et des exceptions. */
data class HostRules(
    val blocked: Set<String> = emptySet(),
    val allowed: Set<String> = emptySet(),
    /** Lignes comprises mais non gérées (chemins, options, règles cosmétiques). */
    val skipped: Int = 0,
) {
    companion object {
        fun merge(parts: List<HostRules>): HostRules {
            val blocked = HashSet<String>()
            val allowed = HashSet<String>()
            var skipped = 0
            for (p in parts) {
                blocked += p.blocked
                allowed += p.allowed
                skipped += p.skipped
            }
            return HostRules(blocked, allowed, skipped)
        }
    }
}

/**
 * Lit un sous-ensemble du format AdBlock et le format hosts. Le blocage se fait au niveau
 * du nom d'hôte uniquement (comme un DNS), jamais sur le chemin d'une URL :
 *
 * - `||pub.exemple.com^`   bloque pub.exemple.com et ses sous-domaines
 * - `@@||cdn.exemple.com^` exception
 * - `0.0.0.0 pub.exemple.com`, ou `pub.exemple.com` seul
 *
 * Les règles avec chemin, joker ou option (`$third-party`…) sont comptées dans `skipped`.
 */
object FilterParser {
    private val hostsLine = Regex("^(?:0\\.0\\.0\\.0|127\\.0\\.0\\.1|::1?)\\s+(\\S+)")

    fun parse(lines: Sequence<String>): HostRules {
        val blocked = HashSet<String>()
        val allowed = HashSet<String>()
        var skipped = 0
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '#' || line[0] == '[') continue
            val hostsMatch = hostsLine.find(line)
            val ok = when {
                hostsMatch != null -> {
                    val h = Hosts.normalize(hostsMatch.groupValues[1])
                    (h != "localhost" && Hosts.isValidHostname(h)).also { if (it) blocked += h }
                }
                line.startsWith("@@||") -> adblockHost(line.substring(4))?.also { allowed += it } != null
                line.startsWith("||") -> adblockHost(line.substring(2))?.also { blocked += it } != null
                else -> {
                    val h = Hosts.normalize(line)
                    Hosts.isValidHostname(h).also { if (it) blocked += h }
                }
            }
            if (!ok) skipped++
        }
        return HostRules(blocked, allowed, skipped)
    }

    fun parse(text: String): HostRules = parse(text.lineSequence())

    private fun adblockHost(body: String): String? {
        val caret = body.indexOf('^')
        val host = if (caret >= 0) {
            if (body.length > caret + 1) return null // options ou chemin après le ^
            body.substring(0, caret)
        } else {
            body
        }
        return Hosts.normalize(host).takeIf { Hosts.isValidHostname(it) }
    }
}

/** Décide si une requête vers un hôte doit être bloquée. Une exception l'emporte toujours. */
class HostBlocker(private val rules: HostRules) {
    fun isBlocked(host: String): Boolean {
        val h = Hosts.normalize(host)
        if (h.isEmpty()) return false
        if (inSuffixes(h, rules.allowed)) return false
        return inSuffixes(h, rules.blocked)
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
}
