package dev.wido.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Ce qu'il faut masquer sur un site, prêt à être injecté dans la page. Les règles cosmétiques des
 * listes couvrent des milliers de domaines : on ne garde que les génériques et celles des domaines
 * du site, puis le script de la page choisit selon l'hôte qu'il affiche réellement.
 */
@Serializable
data class CosmeticPlan(
    /** Sélecteurs génériques (tous les sites). */
    @SerialName("g") val generic: List<String> = emptyList(),
    /** Sélecteurs par domaine. */
    @SerialName("d") val byDomain: Map<String, List<String>> = emptyMap(),
    /** Exceptions par domaine (`""` : partout). */
    @SerialName("u") val unhide: Map<String, List<String>> = emptyMap(),
    /** Domaines sans sélecteurs génériques (`$generichide`). */
    @SerialName("ng") val noGeneric: List<String> = emptyList(),
    /** Domaines sans aucun masquage (`$elemhide`). */
    @SerialName("off") val off: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = generic.isEmpty() && byDomain.isEmpty()

    fun toJson(): String = json.encodeToString(this)

    /**
     * Sélecteurs à masquer sur `host`. Même logique que le script injecté (`internal/cosmetic.js`) :
     * sert aux tests et au diagnostic.
     */
    fun selectorsFor(host: String): List<String> {
        val h = Hosts.normalize(host)
        if (off.any { Hosts.matches(h, it) }) return emptyList()
        val excluded = unhide.filterKeys { it.isEmpty() || Hosts.matches(h, it) }.values.flatten().toSet()
        val out = LinkedHashSet<String>()
        if (noGeneric.none { Hosts.matches(h, it) }) generic.filterTo(out) { it !in excluded }
        byDomain.forEach { (d, list) -> if (Hosts.matches(h, d)) list.filterTo(out) { it !in excluded } }
        return out.toList()
    }

    companion object {
        /** Au-delà, les sélecteurs génériques coûtent trop cher à chaque page sur une TV. */
        const val MAX_GENERIC = 8000

        private val json = Json { encodeDefaults = false }

        /**
         * Retient, parmi les couches de règles, ce qui peut s'appliquer à un site dont la fenêtre
         * peut afficher `siteDomains` (son domaine, ceux qu'il peut ouvrir).
         */
        fun build(layers: List<HostRules>, siteDomains: Collection<String>, maxGeneric: Int = MAX_GENERIC): CosmeticPlan {
            val domains = siteDomains.map(Hosts::normalize).filter { it.isNotEmpty() }
            fun relevant(d: String) = d.isEmpty() || domains.any { Hosts.matches(d, it) || Hosts.matches(it, d) }

            val generic = LinkedHashSet<String>()
            val byDomain = LinkedHashMap<String, LinkedHashSet<String>>()
            val unhide = LinkedHashMap<String, LinkedHashSet<String>>()
            val noGeneric = LinkedHashSet<String>()
            val off = LinkedHashSet<String>()
            for (layer in layers) {
                layer.hide.forEach { (d, list) ->
                    when {
                        d.isEmpty() -> for (s in list) if (generic.size < maxGeneric) generic += s
                        relevant(d) -> byDomain.getOrPut(d) { LinkedHashSet() } += list
                    }
                }
                layer.unhide.forEach { (d, list) -> if (relevant(d)) unhide.getOrPut(d) { LinkedHashSet() } += list }
                layer.noGenericHide.filterTo(noGeneric) { relevant(it) }
                layer.noHide.filterTo(off) { relevant(it) }
            }
            return CosmeticPlan(
                generic.toList(),
                byDomain.mapValues { it.value.toList() },
                unhide.mapValues { it.value.toList() },
                noGeneric.toList(),
                off.toList(),
            )
        }
    }
}

/**
 * Format compilé des listes de blocage, gardé sur disque : une ligne par règle, préfixée d'une
 * lettre. Le relire est bien plus rapide que réanalyser la liste d'origine (expressions régulières,
 * syntaxe AdBlock), ce qui rend l'ouverture d'un site quasi instantanée.
 */
object CompiledRules {
    private const val HEADER = "wido-rules 1"

    fun encode(rules: HostRules): String = buildString {
        append(HEADER).append('\n')
        append("s ").append(rules.skipped).append('\n')
        for (h in rules.blocked) append("b ").append(h).append('\n')
        for (h in rules.allowed) append("a ").append(h).append('\n')
        for (h in rules.noGenericHide) append("g ").append(h).append('\n')
        for (h in rules.noHide) append("e ").append(h).append('\n')
        rules.hide.forEach { (d, list) -> for (s in list) append("h ").append(d).append('\t').append(s).append('\n') }
        rules.unhide.forEach { (d, list) -> for (s in list) append("u ").append(d).append('\t').append(s).append('\n') }
    }

    /** Null si le texte n'est pas au format compilé (ancien cache, fichier abîmé). */
    fun decode(text: String): HostRules? {
        val lines = text.lineSequence().iterator()
        if (!lines.hasNext() || lines.next() != HEADER) return null
        val blocked = HashSet<String>()
        val allowed = HashSet<String>()
        val noGeneric = HashSet<String>()
        val noHide = HashSet<String>()
        val hide = HashMap<String, MutableSet<String>>()
        val unhide = HashMap<String, MutableSet<String>>()
        var skipped = 0
        for (line in lines) {
            if (line.length < 3 || line[1] != ' ') continue
            val value = line.substring(2)
            when (line[0]) {
                's' -> skipped = value.toIntOrNull() ?: 0
                'b' -> blocked += value
                'a' -> allowed += value
                'g' -> noGeneric += value
                'e' -> noHide += value
                'h', 'u' -> {
                    val tab = value.indexOf('\t')
                    if (tab < 0) continue
                    val target = if (line[0] == 'h') hide else unhide
                    target.getOrPut(value.substring(0, tab)) { LinkedHashSet() } += value.substring(tab + 1)
                }
            }
        }
        return HostRules(blocked, allowed, skipped, hide, unhide, noGeneric, noHide)
    }
}
