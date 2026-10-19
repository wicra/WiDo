package dev.wido.tv

import android.content.Context
import android.util.Log
import dev.wido.core.ConfigParser
import dev.wido.core.FilterParser
import dev.wido.core.HostBlocker
import dev.wido.core.HostRules
import dev.wido.core.Hosts
import dev.wido.core.NavigationPolicy
import dev.wido.core.Site
import dev.wido.core.WiDoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

enum class ConfigSource { Remote, Cache, Bundled }

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
            HostBlocker(HostRules.merge(lists + inline + HostRules(allowed = allowed)))
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
