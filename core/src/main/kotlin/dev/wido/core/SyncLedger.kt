package dev.wido.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Une valeur synchronisée : sa clé, sa valeur (null = supprimée) et l'heure de sa dernière modification.
 * Une suppression est gardée (« pierre tombale ») pour qu'un autre écran ne fasse pas revenir l'élément.
 */
@Serializable
data class SyncRecord(val k: String, val v: JsonElement? = null, val t: Long) {
    val deleted: Boolean get() = v == null || v is JsonNull
}

/**
 * Registre de synchronisation : chaque clé garde sa version la plus récente (« le dernier qui écrit
 * gagne »). Fusionner deux registres donne le même résultat dans n'importe quel ordre, sur n'importe
 * quel écran : pas de serveur, pas de conflit à trancher à la main.
 */
class SyncLedger(val records: Map<String, SyncRecord> = emptyMap()) {

    /** Les valeurs vivantes (sans les suppressions). */
    val live: Map<String, JsonElement>
        get() = records.values.filterNot { it.deleted }.associate { it.k to it.v!! }

    /** Fusion clé par clé : la version la plus récente gagne ; à égalité, un ordre fixe départage. */
    fun merge(other: SyncLedger): SyncLedger = merge(other.records.values)

    fun merge(incoming: Collection<SyncRecord>): SyncLedger {
        if (incoming.isEmpty()) return this
        val out = records.toMutableMap()
        for (r in incoming) {
            if (r.k.isEmpty() || r.k.length > MAX_KEY) continue
            val mine = out[r.k]
            if (mine == null || wins(r, mine)) out[r.k] = r
        }
        return SyncLedger(out)
    }

    /**
     * Enregistre l'état local actuel : une clé nouvelle ou modifiée prend l'heure `now`, une clé
     * disparue devient une suppression. L'heure ne recule jamais pour une clé (horloges décalées).
     */
    fun stamp(current: Map<String, JsonElement>, now: Long, scope: (String) -> Boolean = { true }): SyncLedger {
        val out = records.toMutableMap()
        var changed = false
        for ((k, v) in current) {
            val mine = out[k]
            if (mine == null || mine.deleted || mine.v != v) {
                out[k] = SyncRecord(k, v, maxOf(now, (mine?.t ?: 0) + 1))
                changed = true
            }
        }
        for (mine in records.values) {
            if (!mine.deleted && mine.k !in current && scope(mine.k)) {
                out[mine.k] = SyncRecord(mine.k, null, maxOf(now, mine.t + 1))
                changed = true
            }
        }
        return if (changed) SyncLedger(out) else this
    }

    /** Oublie les suppressions plus vieilles que `maxAgeMs` (tous les écrans les ont vues depuis). */
    fun prune(now: Long, maxAgeMs: Long = TOMBSTONE_MS): SyncLedger {
        val kept = records.filterValues { !it.deleted || now - it.t < maxAgeMs }
        return if (kept.size == records.size) this else SyncLedger(kept)
    }

    /** Ce qui a changé depuis `since` (synchro incrémentale). */
    fun since(since: Long): List<SyncRecord> = records.values.filter { it.t > since }

    val latest: Long get() = records.values.maxOfOrNull { it.t } ?: 0

    fun encode(): String = json.encodeToString(ListSerializer(SyncRecord.serializer()), records.values.sortedBy { it.k })

    override fun equals(other: Any?) = other is SyncLedger && other.records == records
    override fun hashCode() = records.hashCode()

    companion object {
        const val MAX_KEY = 2100
        const val TOMBSTONE_MS = 60L * 24 * 3600 * 1000

        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): SyncLedger = runCatching {
            SyncLedger(json.decodeFromString(ListSerializer(SyncRecord.serializer()), text).associateBy { it.k })
        }.getOrDefault(SyncLedger())

        private fun wins(a: SyncRecord, b: SyncRecord): Boolean =
            a.t > b.t || (a.t == b.t && (a.v?.toString() ?: "") > (b.v?.toString() ?: ""))
    }
}

/**
 * Ce qui se synchronise entre « mes écrans » : la rangée Reprendre, les sites perso, les pages
 * épinglées et les éléments masqués. Jamais les connexions, cookies ni réglages propres à un écran.
 */
object SyncData {
    const val WATCH = "w:"
    const val SITE = "s:"
    const val PIN = "p:"
    const val HIDE = "h:"
    private const val SEP = '\u0001'

    data class Library(
        val watching: List<WatchEntry> = emptyList(),
        val sites: List<Site> = emptyList(),
        val pins: List<Pin> = emptyList(),
        val hides: Map<String, List<String>> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun pinKey(pin: Pin) = "$PIN${pin.siteId}$SEP${pin.url}"

    /** La bibliothèque sous forme de clés → valeurs, à passer à [SyncLedger.stamp]. */
    fun toMap(lib: Library): Map<String, JsonElement> {
        val out = LinkedHashMap<String, JsonElement>()
        lib.watching.forEach { out[WATCH + it.url] = json.encodeToJsonElement(it) }
        lib.sites.filter { LocalSites.isLocalId(it.id) }.forEach { out[SITE + it.id] = json.encodeToJsonElement(it) }
        lib.pins.forEach { out[pinKey(it)] = json.encodeToJsonElement(it) }
        lib.hides.forEach { (site, list) -> if (list.isNotEmpty()) out[HIDE + site] = json.encodeToJsonElement(list) }
        return out
    }

    /**
     * La bibliothèque décrite par un registre. `previous` garde l'ordre local : les éléments déjà là
     * restent à leur place, les nouveaux arrivent (Reprendre : du plus récent au plus ancien).
     */
    fun fromLedger(ledger: SyncLedger, previous: Library = Library()): Library {
        val watching = mutableListOf<WatchEntry>()
        val sites = mutableListOf<Site>()
        val pins = mutableListOf<Pin>()
        val pinTimes = HashMap<Pin, Long>()
        val hides = LinkedHashMap<String, List<String>>()
        for (r in ledger.records.values) {
            if (r.deleted) continue
            val v = r.v!!
            when {
                r.k.startsWith(WATCH) -> decode<WatchEntry>(v)?.takeIf { WATCH + it.url == r.k }?.let { watching += it }
                r.k.startsWith(SITE) -> decode<Site>(v)?.takeIf { SITE + it.id == r.k }?.let { sites += it }
                r.k.startsWith(PIN) -> decode<Pin>(v)?.takeIf { pinKey(it) == r.k }?.let { pins += it; pinTimes[it] = r.t }
                r.k.startsWith(HIDE) -> decode<List<String>>(v)?.let { hides[r.k.removePrefix(HIDE)] = it }
            }
        }
        val cleanSites = LocalSites.decode(LocalSites.encode(keepOrder(sites, previous.sites.map { it.id }) { it.id }))
        val cleanPins = Pins.decode(Pins.encode(keepOrder(pins.sortedByDescending { pinTimes[it] }, previous.pins.map { pinKey(it) }, ::pinKey)))
        val cleanHides = UserHides.decode(UserHides.encode(hides))
        val cleanWatching = ContinueWatching.decode(ContinueWatching.encode(watching.sortedByDescending { it.updatedAt }))
        return Library(cleanWatching, cleanSites, cleanPins, cleanHides)
    }

    /** Les éléments déjà connus dans leur ordre, puis les nouveaux. Les nouveaux épinglés passent devant. */
    private fun <T> keepOrder(items: List<T>, order: List<String>, id: (T) -> String): List<T> {
        val rank = order.withIndex().associate { it.value to it.index }
        val (known, fresh) = items.partition { id(it) in rank }
        return fresh + known.sortedBy { rank[id(it)] }
    }

    private inline fun <reified T> decode(v: JsonElement): T? = runCatching { json.decodeFromJsonElement<T>(v) }.getOrNull()

    /** Garde-fou : un registre reçu d'un autre écran n'est accepté que s'il ne touche que nos clés. */
    fun isKnownKey(key: String) = key.startsWith(WATCH) || key.startsWith(SITE) || key.startsWith(PIN) || key.startsWith(HIDE)
}
