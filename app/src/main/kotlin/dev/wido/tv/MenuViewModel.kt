package dev.wido.tv

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.wido.core.LocalSites
import dev.wido.core.ReleaseInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import dev.wido.core.Pin
import dev.wido.core.SiteOrder
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateUi {
    data object None : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class CheckFailed(val detail: String) : UpdateUi
    data class Available(val info: ReleaseInfo) : UpdateUi
    data class Downloading(val info: ReleaseInfo, val percent: Int) : UpdateUi
    data class Failed(val info: ReleaseInfo, @StringRes val messageRes: Int, val detail: String? = null) : UpdateUi
}

class MenuViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WiDoApp

    val config: StateFlow<ConfigState> = app.repository.state

    /** État des listes de blocage (réglages). */
    val lists: StateFlow<ListStatus> = app.repository.lists

    private val order = MutableStateFlow(app.settings.siteOrder)

    /** Sites dans l'ordre choisi par l'utilisateur. */
    val sites: StateFlow<List<MenuSite>> =
        combine(app.repository.sites, order) { list, ids -> SiteOrder.sort(list, ids) { it.site.id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pins: StateFlow<List<Pin>> = app.repository.pins

    /** Déplace une carte d'une place vers la gauche (-1) ou la droite (+1). */
    fun move(siteId: String, delta: Int) {
        val next = SiteOrder.move(sites.value.map { it.site.id }, siteId, delta)
        app.settings.siteOrder = next
        order.value = next
    }

    fun removePin(pin: Pin) = app.repository.removePin(pin)

    /** Chaque fonction renvoie un message d'erreur à afficher, ou null si c'est fait. */
    fun addSite(name: String, url: String, options: LocalSites.SiteOptions): String? =
        app.repository.addLocalSite(name, url, options)
    fun updateSite(id: String, name: String, url: String, options: LocalSites.SiteOptions): String? =
        app.repository.updateLocalSite(id, name, url, options)
    fun removeSite(id: String) = app.repository.removeLocalSite(id)
    fun addFromCatalog(entry: dev.wido.core.Catalog.Entry): String? =
        app.repository.addLocalSite(entry.name, entry.url, entry.options(), entry.color)
    fun suggestionsFor(site: dev.wido.core.Site) = app.repository.suggestionsFor(site)

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.None)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    private var lastCheck = 0L

    init {
        refresh()
    }

    /** Recharge la configuration et cherche une mise à jour (bouton « Actualiser »). */
    fun refresh() {
        viewModelScope.launch {
            app.repository.ensureLoaded()
            // Les listes en cache sont chargées tout de suite : ouvrir un site sera immédiat.
            launch { app.repository.warmLists() }
            app.repository.refresh()
            app.repository.warmLists()
        }
        checkUpdate()
    }

    /** Retélécharge les listes de blocage (bouton des réglages, ou changement de listes). */
    fun updateLists(force: Boolean = true) {
        viewModelScope.launch { app.repository.warmLists(force) }
    }

    /** Retour au premier plan : on revérifie si la dernière vérification date de plus de 5 minutes. */
    fun onForeground() {
        if (System.currentTimeMillis() - lastCheck > RECHECK_MS) checkUpdate()
    }

    fun checkUpdate() {
        val state = _update.value
        if (state is UpdateUi.Downloading) return
        lastCheck = System.currentTimeMillis()
        if (state !is UpdateUi.Available) _update.value = UpdateUi.Checking
        viewModelScope.launch {
            // Une mise à jour n'est jamais bloquante, mais un échec doit se voir : sinon impossible à comprendre.
            _update.value = try {
                app.updates.check()?.let { UpdateUi.Available(it) } ?: UpdateUi.UpToDate
            } catch (e: Exception) {
                UpdateUi.CheckFailed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private companion object {
        const val RECHECK_MS = 5 * 60 * 1000L
    }

    fun install(info: ReleaseInfo) {
        viewModelScope.launch {
            _update.value = UpdateUi.Downloading(info, 0)
            try {
                val apk = app.updates.download(info) { _update.value = UpdateUi.Downloading(info, it) }
                _update.value = when (app.updates.install(apk)) {
                    InstallResult.Started -> UpdateUi.Available(info)
                    InstallResult.NeedsPermission -> UpdateUi.Failed(info, R.string.update_permission)
                }
            } catch (e: UpdateException) {
                _update.value = UpdateUi.Failed(info, e.messageRes, e.detail)
            }
        }
    }
}
