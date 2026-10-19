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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateUi {
    data object None : UpdateUi
    data class Available(val info: ReleaseInfo) : UpdateUi
    data class Downloading(val info: ReleaseInfo, val percent: Int) : UpdateUi
    data class Failed(val info: ReleaseInfo, @StringRes val messageRes: Int, val detail: String? = null) : UpdateUi
}

class MenuViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WiDoApp

    val config: StateFlow<ConfigState> = app.repository.state

    val sites: StateFlow<List<MenuSite>> =
        app.repository.sites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Chaque fonction renvoie un message d'erreur à afficher, ou null si c'est fait. */
    fun addSite(name: String, url: String, options: LocalSites.SiteOptions): String? =
        app.repository.addLocalSite(name, url, options)
    fun updateSite(id: String, name: String, url: String, options: LocalSites.SiteOptions): String? =
        app.repository.updateLocalSite(id, name, url, options)
    fun removeSite(id: String) = app.repository.removeLocalSite(id)

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.None)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    init {
        refresh()
        checkUpdate()
    }

    fun refresh() {
        viewModelScope.launch {
            app.repository.ensureLoaded()
            app.repository.refresh()
        }
    }

    private fun checkUpdate() {
        viewModelScope.launch {
            // Pas de réseau ou pas de release : on ne dit rien, la mise à jour n'est jamais bloquante.
            val info = runCatching { app.updates.check() }.getOrNull() ?: return@launch
            _update.value = UpdateUi.Available(info)
        }
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
