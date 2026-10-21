package dev.wido.tv

import android.content.Context
import android.util.Log
import dev.wido.core.CompiledRules
import dev.wido.core.ConfigParser
import dev.wido.core.CosmeticPlan
import dev.wido.core.FilterParser
import dev.wido.core.HostBlocker
import dev.wido.core.HostRules
import dev.wido.core.LocalSites
import dev.wido.core.Hosts
import dev.wido.core.ListIncludes
import dev.wido.core.UrlFilter
import dev.wido.core.UrlMatcher
import dev.wido.core.NavigationPolicy
import dev.wido.core.ContinueWatching
import dev.wido.core.Pin
import dev.wido.core.WatchEntry
import dev.wido.core.Pins
import dev.wido.core.UserHides
import dev.wido.core.SeenDomains
import dev.wido.core.SiteOverrides
import dev.wido.core.SyncData
import dev.wido.core.SyncDelta
import dev.wido.core.SyncLedger
import dev.wido.core.SyncRecord
import dev.wido.core.Site
import dev.wido.core.WiDoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

enum class ConfigSource { Remote, Cache, Bundled }

/** Un site du menu : `local` = ajouté sur cet appareil (modifiable, jamais écrasé par la config distante). */
data class MenuSite(val site: Site, val local: Boolean)

/** État des listes de blocage, affiché dans les réglages. */
data class ListStatus(
    val lists: Int = 0,
    val hosts: Int = 0,
    val selectors: Int = 0,
    /** Règles d'adresse et scriptlets. */
    val advanced: Int = 0,
    /** Date de la plus ancienne liste en cache, ou null si aucune n'est encore téléchargée. */
    val updatedAt: Long? = null,
    val updating: Boolean = false,
)

data class ConfigState(
    val config: WiDoConfig? = null,
    val warnings: List<String> = emptyList(),
    val source: ConfigSource? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Charge la configuration : cache disque, sinon fichier intégré, puis tentative distante.
 * Une configuration distante invalide n'écrase jamais le cache.
 */
class ConfigRepository(private val context: Context, private val settings: Settings) {
    private val _state = MutableStateFlow(ConfigState())
    val state: StateFlow<ConfigState> = _state.asStateFlow()

    private val localFile get() = File(context.filesDir, "local-sites.json")

    /** Sites ajoutés depuis l'app : fichier à part, que ni l'actualisation ni les mises à jour ne touchent. */
    private val overridesFile get() = File(context.filesDir, "site-overrides.json")

    /** Domaines autorisés par l'utilisateur sur des sites de la config distante (jamais écrasés par elle). */
    @Volatile private var overrides: Map<String, List<String>> =
        runCatching { SiteOverrides.decode(overridesFile.readText()) }.getOrDefault(emptyMap())

    private val seenFile get() = File(context.filesDir, "seen-domains.json")

    /** Ce que chaque site a tenté de faire : sert à proposer des choix en un bouton. */
    @Volatile private var seen: Map<String, SeenDomains.Entry> =
        runCatching { SeenDomains.decode(seenFile.readText()) }.getOrDefault(emptyMap())

    private val pinsFile get() = File(context.filesDir, "pins.json")

    private val _pins = MutableStateFlow(runCatching { Pins.decode(pinsFile.readText()) }.getOrDefault(emptyList()))

    /** Pages épinglées, tous sites confondus. */
    val pins: StateFlow<List<Pin>> = _pins.asStateFlow()

    @Synchronized
    fun addPin(siteId: String, title: String?, url: String) = savePins(Pins.add(_pins.value, siteId, title, url))

    @Synchronized
    fun removePin(pin: Pin) = savePins(Pins.remove(_pins.value, pin))

    private fun savePins(next: List<Pin>) {
        writeAtomically(pinsFile, Pins.encode(next))
        _pins.value = next
        stamp()
    }

    private val watchingFile get() = File(context.filesDir, "continue-watching.json")

    private val _watching = MutableStateFlow(runCatching { ContinueWatching.decode(watchingFile.readText()) }.getOrDefault(emptyList()))

    /** Vidéos en cours, pour la rangée « Reprendre » (la plus récente en tête). */
    val watching: StateFlow<List<WatchEntry>> = _watching.asStateFlow()

    /** Retient où en est une vidéo (lecteur de la page ou lecteur natif). */
    @Synchronized
    fun recordProgress(siteId: String, url: String, title: String?, image: String?, positionSeconds: Double, durationSeconds: Double) {
        val entry = WatchEntry(siteId, url, title.orEmpty(), image, positionSeconds, durationSeconds, System.currentTimeMillis())
        saveWatching(ContinueWatching.record(_watching.value, entry))
    }

    @Synchronized
    fun removeWatch(url: String) = saveWatching(ContinueWatching.remove(_watching.value, url))

    /** Remet une vidéo retirée par erreur, à sa place (par date). */
    @Synchronized
    fun restoreWatch(entry: WatchEntry) =
        saveWatching((_watching.value.filterNot { it.url == entry.url } + entry).sortedByDescending { it.updatedAt }.take(ContinueWatching.MAX))

    private fun saveWatching(next: List<WatchEntry>) {
        if (next == _watching.value) return
        writeAtomically(watchingFile, ContinueWatching.encode(next))
        _watching.value = next
        stamp()
    }

    private val hidesFile get() = File(context.filesDir, "user-hides.json")

    private val _hides = MutableStateFlow(runCatching { UserHides.decode(hidesFile.readText()) }.getOrDefault(emptyMap()))

    /** Éléments masqués à la main, par site (sélecteurs CSS). */
    val hides: StateFlow<Map<String, List<String>>> = _hides.asStateFlow()

    @Synchronized
    fun addHide(siteId: String, selector: String) = saveHides(UserHides.add(_hides.value, siteId, selector))

    @Synchronized
    fun clearHides(siteId: String) = saveHides(UserHides.clear(_hides.value, siteId))

    private fun saveHides(next: Map<String, List<String>>) {
        writeAtomically(hidesFile, UserHides.encode(next))
        _hides.value = next
        stamp()
    }

    private val _local = MutableStateFlow(readLocalSites())
    val localSites: StateFlow<List<Site>> = _local.asStateFlow()

    /** Sites distants activés, puis sites locaux. */
    val sites: Flow<List<MenuSite>> = combine(_state, _local) { st, local ->
        val remote = st.config?.sites.orEmpty()
        val remoteIds = remote.map { it.id }.toSet()
        LocalSites.merge(remote, local).filter { it.enabled }.map { MenuSite(it, local = it.id !in remoteIds) }
    }

    private val mutex = Mutex()
    private val parsedLists = ConcurrentHashMap<String, HostRules>()
    /** Règles d'adresse indexées, par liste ; reconstruites quand la liste change. */
    private val urlFilters = ConcurrentHashMap<String, Pair<HostRules, UrlFilter>>()
    private val listsMutex = Mutex()

    private val _lists = MutableStateFlow(ListStatus())
    val lists: StateFlow<ListStatus> = _lists.asStateFlow()

    private val cacheFile get() = File(context.filesDir, "config.json")
    private val listsDir get() = File(context.filesDir, "lists").apply { mkdirs() }

    /** Garantit qu'une configuration est disponible (cache ou intégrée), sans réseau. */
    suspend fun ensureLoaded(): WiDoConfig = mutex.withLock {
        _state.value.config ?: loadLocal()
    }

    suspend fun refresh() = mutex.withLock {
        if (_state.value.config == null) loadLocal()
        _state.update { it.copy(loading = true, error = null) }
        try {
            val text = withContext(Dispatchers.IO) {
                String(Http.get(settings.configUrl, MAX_CONFIG_BYTES), Charsets.UTF_8)
            }
            val parsed = ConfigParser.parse(text)
            withContext(Dispatchers.IO) { writeAtomically(cacheFile, text) }
            _state.value = ConfigState(parsed.config, parsed.warnings, ConfigSource.Remote, loading = false)
        } catch (e: Exception) {
            Log.w(TAG, "Configuration distante indisponible", e)
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }

    /**
     * Le site du menu qui peut afficher `url` (son domaine ou ses domaines autorisés), ou un site
     * éphémère pour cette seule adresse (lien envoyé depuis le téléphone) : redirections souples,
     * pop-ups bloquées, listes de blocage actives.
     */
    suspend fun siteForUrl(url: String): Site {
        val config = ensureLoaded()
        val host = Hosts.of(url).orEmpty()
        val known = LocalSites.merge(config.sites, _local.value)
            .firstOrNull { it.enabled && NavigationPolicy.allowedDomains(it).any { d -> Hosts.matches(host, d) } }
            ?.let { SiteOverrides.apply(it, overrides) }
            ?.let { UserHides.apply(it, _hides.value) }
        return known ?: Site(
            id = LINK_SITE_ID,
            name = host.removePrefix("www."),
            url = url,
            scripts = listOf("block-popups"),
            navigation = dev.wido.core.Navigation.RELAXED,
            allowInsecure = Hosts.schemeOf(url) == "http",
        )
    }

    /** Adresses de tous les sites (configuration et sites perso), pour ne pas ajouter deux fois le même. */
    suspend fun allSiteUrls(): List<String> = LocalSites.merge(ensureLoaded().sites, _local.value).map { it.url }

    suspend fun findSite(id: String): Site? {
        val config = ensureLoaded()
        return LocalSites.merge(config.sites, _local.value)
            .firstOrNull { it.id == id && it.enabled }
            ?.let { SiteOverrides.apply(it, overrides) }
            ?.let { UserHides.apply(it, _hides.value) }
    }

    /**
     * Autorise un domaine pour un site (après une redirection refusée, par exemple). Un site perso est
     * modifié directement ; pour un site de la config distante, le choix est gardé à part, localement.
     * Renvoie un message d'erreur, ou null si c'est fait.
     */
    @Synchronized
    fun allowDomain(siteId: String, rawDomain: String): String? {
        val domain = Hosts.normalize(rawDomain).removePrefix("www.")
        if (!Hosts.isValidHostname(domain)) return "Domaine invalide."
        val local = _local.value.firstOrNull { it.id == siteId }
        if (local != null) {
            if (domain in local.allowedDomains) return null
            if (local.allowedDomains.size >= 30) return "Trop de domaines autorisés (30 maximum)."
            saveLocal(_local.value.map { if (it.id == siteId) it.copy(allowedDomains = it.allowedDomains + domain) else it })
            return null
        }
        val next = SiteOverrides.withDomain(overrides, siteId, domain) ?: return "Limite de domaines atteinte."
        writeAtomically(overridesFile, SiteOverrides.encode(next))
        overrides = next
        return null
    }

    /** Ajoute un site local. Renvoie un message d'erreur, ou null si c'est fait. */
    @Synchronized
    fun addLocalSite(
        name: String,
        url: String,
        options: LocalSites.SiteOptions = LocalSites.SiteOptions(),
        color: String? = null,
    ): String? {
        val local = _local.value
        if (local.size >= LocalSites.MAX_LOCAL) return "Limite de ${LocalSites.MAX_LOCAL} sites atteinte."
        return when (val built = LocalSites.build(name, url, takenIds(), options = options, color = color)) {
            is LocalSites.Built.Error -> built.message
            is LocalSites.Built.Ok -> { saveLocal(local + built.site); null }
        }
    }

    @Synchronized
    fun updateLocalSite(id: String, name: String, url: String, options: LocalSites.SiteOptions = LocalSites.SiteOptions()): String? {
        val local = _local.value
        val existing = local.firstOrNull { it.id == id } ?: return "Site introuvable."
        return when (val built = LocalSites.build(name, url, takenIds(), keepId = id, options = options, color = existing.color)) {
            is LocalSites.Built.Error -> built.message
            is LocalSites.Built.Ok -> { saveLocal(local.map { if (it.id == id) built.site else it }); null }
        }
    }

    @Synchronized
    fun removeLocalSite(id: String) {
        saveLocal(_local.value.filterNot { it.id == id })
        if (id in seen) writeSeen(seen - id)
        if (_pins.value.any { it.siteId == id }) savePins(_pins.value.filterNot { it.siteId == id })
        settings.setLastUrlFor(id, null)
        if (id in _hides.value) saveHides(UserHides.clear(_hides.value, id))
        saveWatching(ContinueWatching.removeSite(_watching.value, id))
    }

    /** Mémorise les redirections refusées et les domaines tiers vus pendant une session. */
    @Synchronized
    fun recordSeen(siteId: String, navigationHosts: List<String>, requestCounts: Map<String, Int>) {
        if (navigationHosts.isEmpty() && requestCounts.isEmpty()) return
        writeSeen(seen + (siteId to SeenDomains.merge(seen[siteId] ?: SeenDomains.Entry(), navigationHosts, requestCounts)))
    }

    /**
     * (à autoriser, à bloquer) : ce que le site a tenté de faire, sans ce qui est déjà autorisé,
     * déjà bloqué ou propre au site. Vide tant que le site n'a pas été utilisé.
     */
    fun suggestionsFor(site: Site): Pair<List<String>, List<String>> {
        val known = NavigationPolicy.allowedDomains(site) + LocalSites.blockedDomainsOf(site.rules)
        return SeenDomains.suggestions(seen[site.id] ?: SeenDomains.Entry(), known.toSet())
    }

    private fun writeSeen(next: Map<String, SeenDomains.Entry>) {
        writeAtomically(seenFile, SeenDomains.encode(next))
        seen = next
    }

    private fun takenIds(): Set<String> =
        (_state.value.config?.sites.orEmpty().map { it.id } + _local.value.map { it.id }).toSet()

    private fun saveLocal(list: List<Site>) {
        writeAtomically(localFile, LocalSites.encode(list))
        _local.value = list
        stamp()
    }

    // --- Synchronisation entre mes écrans -----------------------------------------------------

    private val syncFile get() = File(context.filesDir, "sync-ledger.json")

    @Volatile private var ledger: SyncLedger? = null
    private var applyingSync = false

    private val _syncVersion = MutableStateFlow(0L)

    /** Change à chaque modification de ce qui se synchronise (Reprendre, sites perso, épingles, masquages). */
    val syncVersion: StateFlow<Long> = _syncVersion.asStateFlow()

    private fun library() = SyncData.Library(_watching.value, _local.value, _pins.value, _hides.value)

    private fun currentLedger(): SyncLedger =
        ledger ?: runCatching { SyncLedger.decode(syncFile.readText()) }.getOrDefault(SyncLedger()).also { ledger = it }

    /** Date les modifications locales dès qu'elles ont lieu : le plus récent gagne, d'un écran à l'autre. */
    private fun stamp() {
        if (applyingSync) return
        val before = currentLedger()
        val next = before.stamp(SyncData.toMap(library()), System.currentTimeMillis())
        if (next === before) return
        ledger = next
        writeAtomically(syncFile, next.encode())
        _syncVersion.value = _syncVersion.value + 1
    }

    /** Ce qu'un autre écran n'a pas encore de moi (tout si `epoch` n'est pas celui de mon registre). */
    @Synchronized
    fun syncOutgoing(since: Long, epoch: String): SyncDelta {
        stamp()
        val pruned = currentLedger().prune(System.currentTimeMillis())
        ledger = pruned
        return pruned.delta(since, epoch)
    }

    /** TV qui reçoit une synchro : ce que l'autre n'a pas encore (avant fusion), puis la fusion. */
    @Synchronized
    fun syncExchange(incoming: List<SyncRecord>, since: Long, epoch: String): SyncDelta {
        stamp()
        val out = currentLedger().prune(System.currentTimeMillis()).also { ledger = it }.delta(since, epoch)
        applySync(incoming)
        return out.copy(seq = currentLedger().seq)
    }

    /** Fusionne ce qu'envoie un autre écran ; renvoie vrai si la bibliothèque locale a changé. */
    @Synchronized
    fun applySync(records: List<SyncRecord>): Boolean {
        stamp()
        val before = currentLedger()
        val merged = before.merge(records.filter { SyncData.isKnownKey(it.k) })
        if (merged == before) return false
        val lib = SyncData.fromLedger(merged, library())
        applyingSync = true
        try {
            if (lib.watching != _watching.value) saveWatching(lib.watching)
            if (lib.pins != _pins.value) savePins(lib.pins)
            if (lib.hides != _hides.value) saveHides(lib.hides)
            if (lib.sites != _local.value) saveLocal(lib.sites.take(LocalSites.MAX_LOCAL))
        } finally {
            applyingSync = false
        }
        // Ce qui a été nettoyé à l'arrivée (liste trop longue, site invalide) est daté maintenant.
        val settled = merged.stamp(SyncData.toMap(library()), System.currentTimeMillis())
        ledger = settled
        writeAtomically(syncFile, settled.encode())
        return true
    }

    private fun readLocalSites(): List<Site> =
        runCatching { LocalSites.decode(localFile.readText()) }.getOrDefault(emptyList())

    /** Listes de la configuration, plus les listes recommandées si le réglage est activé. */
    private fun effectiveLists(config: WiDoConfig): List<String> =
        (config.blocklists + if (settings.recommendedLists) (if (settings.lightLists) LIGHT_LISTS else RECOMMENDED_LISTS) else emptyList()).distinct()

    /** Couches de règles d'un site : listes + règles globales + règles du site. */
    private suspend fun layersFor(site: Site): List<HostRules> {
        val config = ensureLoaded()
        // Listes qui ne sont plus utilisées (passage aux listes légères…) : libérées de la mémoire.
        effectiveLists(config).toSet().let { wanted ->
            parsedLists.keys.retainAll(wanted)
            urlFilters.keys.retainAll(wanted)
        }
        return withContext(Dispatchers.Default) {
            // « Bloquer les pubs » désactivé : ni listes ni règles globales, seulement les règles du site.
            val lists = if (site.blockAds) {
                coroutineScope { effectiveLists(config).map { url -> async { loadList(url) } }.awaitAll() }
            } else {
                emptyList()
            }
            val inline = FilterParser.parse(((if (site.blockAds) config.globalRules else emptyList()) + site.rules).asSequence())
            lists + inline
        }
    }

    /** Construit le filtre réseau d'un site. */
    suspend fun blockerFor(site: Site): HostBlocker {
        val config = ensureLoaded()
        val layers = layersFor(site)
        // Le domaine du site et ceux qu'il peut ouvrir ne sont jamais bloqués : c'est ton choix explicite.
        val allowed = (config.whitelist.map(Hosts::normalize) + NavigationPolicy.allowedDomains(site)).toSet()
        // Couches conservées telles quelles : pas de recopie des listes à chaque ouverture.
        return HostBlocker(layers + HostRules(allowed = allowed))
    }

    /**
     * Les règles d'adresse d'un site (chemins, `domain=`…) : celles des listes, indexées une fois et
     * gardées en mémoire, plus les règles globales et celles du site. Les domaines de `whitelist` y
     * restent intouchables.
     */
    suspend fun urlMatcherFor(site: Site): UrlMatcher {
        val config = ensureLoaded()
        val lists = if (site.blockAds) {
            coroutineScope { effectiveLists(config).map { url -> async { url to loadList(url) } }.awaitAll() }
        } else {
            emptyList()
        }
        return withContext(Dispatchers.Default) {
            val filters = lists.map { (url, rules) ->
                val cached = urlFilters[url]
                if (cached != null && cached.first === rules) cached.second else UrlFilter(rules.urls).also { urlFilters[url] = rules to it }
            }
            val inlineRules = FilterParser.parse(((if (site.blockAds) config.globalRules else emptyList()) + site.rules).asSequence())
            val whitelist = config.whitelist.map { "@@||${Hosts.normalize(it)}^" }
            UrlMatcher(filters + UrlFilter(inlineRules.urls + whitelist))
        }
    }

    /** Ce qu'il faut masquer dans les pages du site (règles `##` des listes et du site). */
    suspend fun cosmeticFor(site: Site): CosmeticPlan {
        val layers = layersFor(site)
        return withContext(Dispatchers.Default) { CosmeticPlan.build(layers, NavigationPolicy.allowedDomains(site)) }
    }

    /**
     * Charge toutes les listes en mémoire, en arrière-plan, pour que l'ouverture d'un site soit
     * immédiate. `force` : retélécharge les listes même si le cache est récent.
     */
    suspend fun warmLists(force: Boolean = false) {
        if (!listsMutex.tryLock()) return
        try {
            val urls = effectiveLists(ensureLoaded())
            _lists.update { it.copy(updating = true) }
            val rules = coroutineScope { urls.map { url -> async { loadList(url, force) } }.awaitAll() }
            val dates = urls.mapNotNull { compiledFile(it).takeIf(File::exists)?.lastModified() }
            _lists.value = ListStatus(
                lists = urls.size,
                hosts = rules.sumOf { it.blocked.size },
                selectors = rules.sumOf { it.selectorCount },
                advanced = rules.sumOf { it.advancedCount },
                updatedAt = dates.minOrNull(),
            )
        } finally {
            listsMutex.unlock()
        }
    }

    private fun loadLocal(): WiDoConfig {
        val cached = runCatching { ConfigParser.parse(cacheFile.readText()) }.getOrNull()
        val (parsed, source) = if (cached != null) {
            cached to ConfigSource.Cache
        } else {
            val bundled = context.assets.open("default-config.json").bufferedReader().use { it.readText() }
            ConfigParser.parse(bundled) to ConfigSource.Bundled
        }
        _state.value = ConfigState(parsed.config, parsed.warnings, source, loading = true)
        return parsed.config
    }

    private fun compiledFile(url: String) = File(listsDir, sha1(url) + ".rules")

    /**
     * Une liste : téléchargée au plus toutes les 6 heures, analysée une fois puis gardée sur disque au
     * format compilé (bien plus rapide à relire), et en mémoire tant que l'app tourne.
     */
    private suspend fun loadList(url: String, force: Boolean = false): HostRules = withContext(Dispatchers.IO) {
        val file = compiledFile(url)
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < LIST_TTL_MS
        if (!fresh || force) {
            try {
                val bytes = Http.get(url, MAX_LIST_BYTES, timeoutMs = 10_000)
                // Listes uBlock découpées en fichiers : `!#include x.txt` est récupéré dans le même dossier.
                val base = url.substringBeforeLast('/') + "/"
                val text = ListIncludes.expand(String(bytes, Charsets.UTF_8)) { name ->
                    runCatching { String(Http.get(base + name, MAX_LIST_BYTES, timeoutMs = 10_000), Charsets.UTF_8) }.getOrNull()
                }
                val parsed = withContext(Dispatchers.Default) { FilterParser.parse(text) }
                writeAtomically(file, CompiledRules.encode(parsed))
                // En mémoire : domaines sous forme compacte (déjà écrits sur disque avec leurs noms).
                val compact = parsed.compact()
                parsedLists[url] = compact
                File(listsDir, sha1(url) + ".txt").delete() // ancien cache, non compilé
                return@withContext compact
            } catch (e: Exception) {
                Log.w(TAG, "Liste $url indisponible, utilisation du cache s'il existe", e)
            }
        }
        parsedLists[url]?.let { return@withContext it }
        // Relu ligne à ligne, domaines directement compacts : jamais tout le fichier ni toutes les chaînes en mémoire.
        val cached = if (file.exists()) {
            runCatching { file.bufferedReader().use { r -> CompiledRules.decodeLines(r.lineSequence(), compact = true) } }.getOrNull()
        } else {
            null
        }
        (cached ?: HostRules()).also { parsedLists[url] = it }
    }

    private fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "WiDoConfig"
        /** Identifiant du site éphémère d'un lien ouvert depuis le téléphone. */
        const val LINK_SITE_ID = "lien"
        private const val MAX_CONFIG_BYTES = 1_000_000
        private const val MAX_LIST_BYTES = 8_000_000

        /**
         * Listes recommandées, ajoutées à celles de la configuration (réglage « Listes recommandées ») :
         * EasyList (pubs, réseau et cosmétique), sa variante française, le filtre DNS d'AdGuard, et les
         * listes d'uBlock Origin (scriptlets contre les pubs et les anti-bloqueurs, correctifs rapides,
         * exceptions qui réparent les sites cassés par les autres listes). Mises à jour par leurs
         * communautés, plusieurs fois par jour : WiDo les relit toutes les 6 heures.
         */
        private val RECOMMENDED_LISTS = listOf(
            "https://easylist.to/easylist/easylist.txt",
            "https://easylist-downloads.adblockplus.org/liste_fr.txt",
            "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            "https://ublockorigin.github.io/uAssets/filters/filters.txt",
            "https://ublockorigin.github.io/uAssets/filters/quick-fixes.txt",
            "https://ublockorigin.github.io/uAssets/filters/unbreak.txt",
        )
        /**
         * Listes légères (appareils à peu de mémoire) : les noms de domaine de pubs et de pisteurs (peu
         * coûteux en mémoire une fois compactés) et les correctifs d'uBlock ; sans les grosses listes de
         * règles d'adresse et de masquage (EasyList, liste française, filtres d'uBlock).
         */
        private val LIGHT_LISTS = listOf(
            "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            "https://ublockorigin.github.io/uAssets/filters/quick-fixes.txt",
            "https://ublockorigin.github.io/uAssets/filters/unbreak.txt",
        )
        private const val LIST_TTL_MS = 6 * 60 * 60 * 1000L
    }
}
