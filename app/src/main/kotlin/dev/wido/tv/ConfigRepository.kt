package dev.wido.tv

import android.content.Context
import android.util.Log
import dev.wido.core.ConfigParser
import dev.wido.core.FilterParser
import dev.wido.core.HostBlocker
import dev.wido.core.HostRules
import dev.wido.core.LocalSites
import dev.wido.core.Hosts
import dev.wido.core.NavigationPolicy
import dev.wido.core.SiteOverrides
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

    suspend fun findSite(id: String): Site? {
        val config = ensureLoaded()
        return LocalSites.merge(config.sites, _local.value)
            .firstOrNull { it.id == id && it.enabled }
            ?.let { SiteOverrides.apply(it, overrides) }
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
    fun addLocalSite(name: String, url: String, options: LocalSites.SiteOptions = LocalSites.SiteOptions()): String? {
        val local = _local.value
        if (local.size >= LocalSites.MAX_LOCAL) return "Limite de ${LocalSites.MAX_LOCAL} sites atteinte."
        return when (val built = LocalSites.build(name, url, takenIds(), options = options)) {
            is LocalSites.Built.Error -> built.message
            is LocalSites.Built.Ok -> { saveLocal(local + built.site); null }
        }
    }

    @Synchronized
    fun updateLocalSite(id: String, name: String, url: String, options: LocalSites.SiteOptions = LocalSites.SiteOptions()): String? {
        val local = _local.value
        if (local.none { it.id == id }) return "Site introuvable."
        return when (val built = LocalSites.build(name, url, takenIds(), keepId = id, options = options)) {
            is LocalSites.Built.Error -> built.message
            is LocalSites.Built.Ok -> { saveLocal(local.map { if (it.id == id) built.site else it }); null }
        }
    }

    @Synchronized
    fun removeLocalSite(id: String) = saveLocal(_local.value.filterNot { it.id == id })

    private fun takenIds(): Set<String> =
        (_state.value.config?.sites.orEmpty().map { it.id } + _local.value.map { it.id }).toSet()

    private fun saveLocal(list: List<Site>) {
        writeAtomically(localFile, LocalSites.encode(list))
        _local.value = list
    }

    private fun readLocalSites(): List<Site> =
        runCatching { LocalSites.decode(localFile.readText()) }.getOrDefault(emptyList())

    /** Construit le filtre d'un site : listes distantes + règles globales + règles du site. */
    suspend fun blockerFor(site: Site): HostBlocker {
        val config = ensureLoaded()
        return withContext(Dispatchers.Default) {
            val lists = coroutineScope {
                config.blocklists.map { url -> async { loadList(url) } }.awaitAll()
            }
            val inline = FilterParser.parse((config.globalRules + site.rules).asSequence())
            // Le domaine du site et ceux qu'il peut ouvrir ne sont jamais bloqués : c'est ton choix explicite.
            val allowed = (config.whitelist.map(Hosts::normalize) + NavigationPolicy.allowedDomains(site)).toSet()
            // Couches conservées telles quelles : pas de recopie des listes à chaque ouverture.
            HostBlocker(lists + inline + HostRules(allowed = allowed))
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

    private suspend fun loadList(url: String): HostRules = withContext(Dispatchers.IO) {
        val file = File(listsDir, sha1(url) + ".txt")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < LIST_TTL_MS
        if (!fresh) {
            try {
                val bytes = Http.get(url, MAX_LIST_BYTES, timeoutMs = 8_000)
                writeAtomically(file, String(bytes, Charsets.UTF_8))
                parsedLists.remove(url)
            } catch (e: Exception) {
                Log.w(TAG, "Liste $url indisponible, utilisation du cache s'il existe", e)
            }
        }
        parsedLists.getOrPut(url) {
            if (file.exists()) FilterParser.parse(file.readText()) else HostRules()
        }
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

    private companion object {
        const val TAG = "WiDoConfig"
        const val MAX_CONFIG_BYTES = 1_000_000
        const val MAX_LIST_BYTES = 8_000_000
        const val LIST_TTL_MS = 6 * 60 * 60 * 1000L
    }
}
