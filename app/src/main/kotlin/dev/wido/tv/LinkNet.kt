package dev.wido.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.wido.core.LinkProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address

/** TV (télécommande, écran de salon) ou téléphone / tablette (écran tactile). */
object DeviceKind {
    fun isTv(context: Context): Boolean {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
}

/**
 * Découverte des applis WiDo du réseau (mDNS / DNS-SD, `_wido._tcp`) : la TV s'annonce tant que son
 * serveur est ouvert ; le téléphone (et les TV d'un même groupe) cherchent les autres.
 */
class LinkDiscovery(context: Context, private val selfId: () -> String) {
    data class Found(val advert: LinkProtocol.Advert, val host: String, val port: Int, val serviceName: String)

    private val app = context.applicationContext
    private val nsd by lazy { app.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private val main = Handler(Looper.getMainLooper())

    private val _found = MutableStateFlow<List<Found>>(emptyList())
    val found: StateFlow<List<Found>> = _found.asStateFlow()

    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val toResolve = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    /** Annonce cet appareil (`txt` : identifiant, nom, rôle, empreinte de groupe). */
    fun register(name: String, port: Int, txt: Map<String, String>) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = name.take(60).ifBlank { "WiDo" }
            serviceType = SERVICE_TYPE
            this.port = port
            txt.forEach { (k, v) -> runCatching { setAttribute(k, v) } }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { Log.w(TAG, "Annonce refusée ($errorCode)") }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { registration = null }
    }

    fun unregister() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    fun start() {
        if (discovery != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { main.post { discovery = null } }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { main.post { enqueue(info) } }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { _found.value = _found.value.filterNot { it.serviceName == info.serviceName } }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { discovery = null }
    }

    fun stop() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        toResolve.clear()
    }

    /** Relance la recherche (bouton « Actualiser ») : les appareils partis disparaissent de la liste. */
    fun restart() {
        stop()
        _found.value = emptyList()
        start()
    }

    // Android ne résout qu'un service à la fois : file d'attente.
    private fun enqueue(info: NsdServiceInfo) {
        if (toResolve.none { it.serviceName == info.serviceName }) toResolve.addLast(info)
        resolveNext()
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving) return
        val next = toResolve.removeFirstOrNull() ?: return
        resolving = true
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                main.post {
                    resolving = false
                    if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) main.postDelayed({ enqueue(next) }, 500)
                    resolveNext()
                }
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                main.post {
                    resolving = false
                    accept(info)
                    resolveNext()
                }
            }
        }
        runCatching { nsd.resolveService(next, listener) }.onFailure { resolving = false }
    }

    @Suppress("DEPRECATION")
    private fun accept(info: NsdServiceInfo) {
        val address = info.host as? Inet4Address ?: return
        val advert = LinkProtocol.parseTxt(info.attributes.orEmpty()) ?: return
        if (advert.id == selfId()) return
        val found = Found(advert, address.hostAddress ?: return, info.port, info.serviceName)
        _found.value = listOf(found) + _found.value.filterNot { it.advert.id == advert.id || it.serviceName == info.serviceName }
    }

    private companion object {
        const val TAG = "WiDoDiscovery"
        const val SERVICE_TYPE = "_wido._tcp"
    }
}
