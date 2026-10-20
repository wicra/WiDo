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
    /** Scriptlets par domaine : le contenu de `+js(...)`. */
    @SerialName("j") val scripts: Map<String, List<String>> = emptyMap(),
    /** Scriptlets désactivés par domaine (`#@#+js(...)`, vide = tous). */
    @SerialName("uj") val unscripts: Map<String, List<String>> = emptyMap(),
) {
    val isEmpty: Boolean get() = generic.isEmpty() && byDomain.isEmpty()

    val hasScripts: Boolean get() = scripts.isNotEmpty()

    /** Le masquage seul (sans les scriptlets), pour `internal/cosmetic.js`. */
    fun toJson(): String = json.encodeToString(copy(scripts = emptyMap(), unscripts = emptyMap()))

    /** Les scriptlets seuls, pour `internal/scriptlets.js`. */
    fun scriptsJson(): String = json.encodeToString(CosmeticPlan(scripts = scripts, unscripts = unscripts))

    /** Scriptlets à lancer sur `host` (même logique que `internal/scriptlets.js`). */
    fun scriptsFor(host: String): List<String> {
        val h = Hosts.normalize(host)
        val disabled = unscripts.filterKeys { it.isEmpty() || Hosts.matchesPattern(h, it) }.values.flatten().toSet()
        if ("" in disabled) return emptyList()
        return scripts.filterKeys { Hosts.matchesPattern(h, it) }.values.flatten().distinct().filter { it !in disabled }
    }

    /**
     * Sélecteurs à masquer sur `host`. Même logique que le script injecté (`internal/cosmetic.js`) :
     * sert aux tests et au diagnostic.
     */
    fun selectorsFor(host: String): List<String> {
        val h = Hosts.normalize(host)
        if (off.any { Hosts.matchesPattern(h, it) }) return emptyList()
        val excluded = unhide.filterKeys { it.isEmpty() || Hosts.matchesPattern(h, it) }.values.flatten().toSet()
        val out = LinkedHashSet<String>()
        if (noGeneric.none { Hosts.matchesPattern(h, it) }) generic.filterTo(out) { it !in excluded }
        byDomain.forEach { (d, list) -> if (Hosts.matchesPattern(h, d)) list.filterTo(out) { it !in excluded } }
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
            fun relevant(d: String) = d.isEmpty() ||
                domains.any { Hosts.matchesPattern(it, d) || (!d.endsWith(".*") && Hosts.matches(d, it)) }

            val generic = LinkedHashSet<String>()
            val byDomain = LinkedHashMap<String, LinkedHashSet<String>>()
            val unhide = LinkedHashMap<String, LinkedHashSet<String>>()
            val noGeneric = LinkedHashSet<String>()
            val off = LinkedHashSet<String>()
            val scripts = LinkedHashMap<String, LinkedHashSet<String>>()
            val unscripts = LinkedHashMap<String, LinkedHashSet<String>>()
            for (layer in layers) {
                layer.scripts.forEach { (d, list) -> if (d.isNotEmpty() && relevant(d)) scripts.getOrPut(d) { LinkedHashSet() } += list }
                layer.unscripts.forEach { (d, list) -> if (relevant(d)) unscripts.getOrPut(d) { LinkedHashSet() } += list }
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
                scripts.mapValues { it.value.toList() },
                unscripts.mapValues { it.value.toList() },
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
    private const val HEADER = "wido-rules 2"

    fun encode(rules: HostRules): String = buildString {
        append(HEADER).append('\n')
        append("s ").append(rules.skipped).append('\n')
        for (h in rules.blocked) append("b ").append(h).append('\n')
        for (h in rules.allowed) append("a ").append(h).append('\n')
        for (h in rules.noGenericHide) append("g ").append(h).append('\n')
        for (h in rules.noHide) append("e ").append(h).append('\n')
        for (u in rules.urls) append("n ").append(u).append('\n')
        rules.scripts.forEach { (d, list) -> for (s in list) append("j ").append(d).append('\t').append(s).append('\n') }
        rules.unscripts.forEach { (d, list) -> for (s in list) append("k ").append(d).append('\t').append(s).append('\n') }
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
        val scripts = HashMap<String, MutableSet<String>>()
        val unscripts = HashMap<String, MutableSet<String>>()
        val urls = ArrayList<String>()
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
                'n' -> urls += value
                'h', 'u', 'j', 'k' -> {
                    val tab = value.indexOf('\t')
                    if (tab < 0) continue
                    val target = when (line[0]) {
                        'h' -> hide
                        'u' -> unhide
                        'j' -> scripts
                        else -> unscripts
                    }
                    target.getOrPut(value.substring(0, tab)) { LinkedHashSet() } += value.substring(tab + 1)
                }
            }
        }
        return HostRules(blocked, allowed, skipped, hide, unhide, noGeneric, noHide, urls, scripts, unscripts)
    }
}

/**
 * Directives `!#include fichier.txt` des listes uBlock : le fichier inclus (dans le même dossier que
 * la liste) remplace la ligne, pour que les blocs `!#if` qui l'entourent s'appliquent encore.
 */
object ListIncludes {
    const val MAX_INCLUDES = 20
    private val NAME = Regex("^[A-Za-z0-9._-]+$")

    /** `fetch(nom)` renvoie le texte du fichier inclus, ou null s'il est indisponible. */
    fun expand(text: String, fetch: (String) -> String?): String {
        if (!text.contains("!#include ")) return text
        var count = 0
        return text.lineSequence().joinToString("\n") { line ->
            val name = line.trim().takeIf { it.startsWith("!#include ") }?.substringAfter("!#include ")?.trim()
            if (name == null || !NAME.matches(name) || name.contains("..") || count >= MAX_INCLUDES) {
                line
            } else {
                count++
                fetch(name) ?: line
            }
        }
    }
}
