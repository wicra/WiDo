package dev.wido.tv

import dev.wido.core.MiniHttpServer

import android.os.Handler
import android.os.Looper
import dev.wido.core.LinkProtocol
import dev.wido.core.PhoneProtocol
import dev.wido.core.RemoteCommand
import dev.wido.core.RemoteProtocol

/** Un écran de WiDo qui reçoit les commandes de la télécommande sur téléphone. */
fun interface RemoteTarget {
    fun onRemote(command: RemoteCommand)
}

/**
 * Serveur local de la TV, ouvert tant que WiDo est à l'écran :
 * - télécommande dans le navigateur du téléphone (réglage « Télécommande sur téléphone ») : appairée
 *   une fois par QR code, l'adresse contient un jeton permanent (changeable) ;
 * - lien avec les applis WiDo du réseau (réglage « Écrans liés ») : annoncé par mDNS, chaque appareil
 *   lié a son propre jeton (voir [ScreenLink]).
 *
 * Les commandes sont transmises, sur le fil principal, à l'écran actif ([attach] / [detach]).
 */
class RemoteServer(
    private val settings: Settings,
    private val link: () -> ScreenLink,
    private val labels: () -> RemoteProtocol.Labels,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var target: RemoteTarget? = null
    private var server: MiniHttpServer? = null
    private var port: Int? = null

    private val stopLater = Runnable { stop() }

    /** L'adresse à scanner (avec le jeton), ou null si le serveur n'est pas ouvert. */
    val url: String?
        get() {
            val p = port ?: return null
            val ip = MiniHttpServer.localAddress() ?: return null
            return dev.wido.core.ScanCodes.tag("http://$ip:$p/?t=${settings.remoteToken}", dev.wido.core.ScanCodes.REMOTE)
        }

    /** QR code d'appairage de l'appli WiDo sur téléphone (`wido://pair…`), ou null si le serveur n'est pas ouvert. */
    val pairUri: String?
        get() {
            val p = port ?: return null
            val ip = MiniHttpServer.localAddress() ?: return null
            return LinkProtocol.pairUri(ip, p, settings.pairKey, settings.deviceName)
        }

    /** L'adresse à saisir à la main dans l'appli du téléphone. */
    val address: String?
        get() {
            val p = port ?: return null
            val ip = MiniHttpServer.localAddress() ?: return null
            return if (p == LinkProtocol.PORT) ip else "$ip:$p"
        }

    private val wanted: Boolean get() = link().isTv && (settings.phoneRemote || settings.screenLink)

    /** Un écran passe au premier plan : il reçoit les commandes ; le serveur s'ouvre si un réglage le demande. */
    fun attach(screen: RemoteTarget) {
        target = screen
        main.removeCallbacks(stopLater)
        if (wanted) start()
    }

    /** L'écran quitte le premier plan. Le serveur se ferme si aucun écran ne prend le relais. */
    fun detach(screen: RemoteTarget) {
        if (target === screen) target = null
        main.removeCallbacks(stopLater)
        main.postDelayed(stopLater, STOP_DELAY_MS)
    }

    /** Après un changement de réglage, de jeton ou de nom. */
    fun restart() {
        stop()
        if (wanted && target != null) start()
    }

    /** Le groupe ou le nom a changé : l'annonce sur le réseau est refaite. */
    fun readvertise() {
        val p = port ?: return
        advertise(p)
    }

    private fun start() {
        if (server?.isRunning == true) return
        val http = MiniHttpServer("WiDoRemote", LinkProtocol.MAX_BODY, log = { m, e -> android.util.Log.w("WiDoHttp", m, e) }) { request, body -> route(request, body) }
        val p = http.start(LinkProtocol.PORT) ?: return
        port = p
        server = http
        advertise(p)
    }

    private fun advertise(p: Int) {
        val l = link()
        if (!settings.screenLink) {
            l.discovery.unregister()
            return
        }
        l.discovery.register(settings.deviceName, p, LinkProtocol.txt(l.device, l.groupHash))
        // Une TV de « mes écrans » cherche les autres pour se synchroniser avec elles.
        if (l.groupHash != null) l.discovery.start() else l.discovery.stop()
    }

    private fun route(request: PhoneProtocol.Request, body: String): MiniHttpServer.Response {
        val token = request.query["t"]
        val browser = settings.phoneRemote && PhoneProtocol.tokenMatches(settings.remoteToken, token)
        return when {
            request.method == "GET" && request.path == "/" ->
                if (browser) MiniHttpServer.Response(200, "text/html; charset=utf-8", RemoteProtocol.page(settings.remoteToken, labels())) else MiniHttpServer.FORBIDDEN
            // Télécommande du navigateur ; les appareils liés passent par le lien (même route, leur jeton).
            request.path == "/cmd" && browser -> {
                if (request.method != "POST") return MiniHttpServer.BAD_REQUEST
                val command = RemoteProtocol.parse(body) ?: return MiniHttpServer.Response(400, body = "Bad command")
                main.post { target?.onRemote(command) }
                MiniHttpServer.Response(200, body = "ok")
            }
            settings.screenLink -> link().handle(request, body) { target } ?: MiniHttpServer.NOT_FOUND
            else -> MiniHttpServer.NOT_FOUND
        }
    }

    private fun stop() {
        server?.stop()
        server = null
        port = null
        if (link().isTv) {
            link().discovery.unregister()
            link().discovery.stop()
        }
    }

    private companion object {
        /** Le temps de passer d'un écran à l'autre (accueil → lecteur) sans couper la télécommande. */
        const val STOP_DELAY_MS = 5_000L
    }
}
