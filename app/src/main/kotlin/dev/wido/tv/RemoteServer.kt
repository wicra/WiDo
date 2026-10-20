package dev.wido.tv

import android.os.Handler
import android.os.Looper
import dev.wido.core.RemoteCommand
import dev.wido.core.RemoteProtocol

/** Un écran de WiDo qui reçoit les commandes de la télécommande sur téléphone. */
fun interface RemoteTarget {
    fun onRemote(command: RemoteCommand)
}

/**
 * Télécommande sur téléphone : un serveur local permanent, ouvert tant que WiDo est à l'écran et que le
 * réglage est actif. Le téléphone est appairé une fois en scannant le QR code des réglages : l'adresse
 * contient un jeton permanent (changeable), sans lequel la TV refuse tout.
 *
 * Les commandes sont transmises, sur le fil principal, à l'écran actif ([attach] / [detach]).
 */
class RemoteServer(private val settings: Settings, private val labels: () -> RemoteProtocol.Labels) {
    private val main = Handler(Looper.getMainLooper())
    private var target: RemoteTarget? = null
    private var server: MiniHttpServer? = null
    private var port: Int? = null

    private val stopLater = Runnable { stop() }

    /** L'adresse à scanner (avec le jeton), ou null si le serveur n'est pas ouvert. */
    val url: String?
        get() {
            val p = port ?: return null
            val ip = MiniHttpServer.localAddress() ?: return null
            return "http://$ip:$p/?t=${settings.remoteToken}"
        }

    /** Un écran passe au premier plan : il reçoit les commandes ; le serveur s'ouvre si le réglage est actif. */
    fun attach(screen: RemoteTarget) {
        target = screen
        main.removeCallbacks(stopLater)
        if (settings.phoneRemote) start()
    }

    /** L'écran quitte le premier plan. Le serveur se ferme si aucun écran ne prend le relais. */
    fun detach(screen: RemoteTarget) {
        if (target === screen) target = null
        main.removeCallbacks(stopLater)
        main.postDelayed(stopLater, STOP_DELAY_MS)
    }

    /** Après un changement de réglage ou de jeton. */
    fun restart() {
        stop()
        if (settings.phoneRemote && target != null) start()
    }

    private fun start() {
        if (server?.isRunning == true) return
        val token = settings.remoteToken
        val http = MiniHttpServer("WiDoRemote", token) { method, path, body ->
            when {
                method == "GET" && path == "/" -> MiniHttpServer.Response(200, "text/html; charset=utf-8", RemoteProtocol.page(token, labels()))
                method == "POST" && path == "/cmd" -> {
                    val command = RemoteProtocol.parse(body)
                    if (command == null) {
                        MiniHttpServer.Response(400, body = "Bad command")
                    } else {
                        main.post { target?.onRemote(command) }
                        MiniHttpServer.Response(200, body = "ok")
                    }
                }
                else -> MiniHttpServer.Response(404, body = "Not found")
            }
        }
        port = http.start(PORT)
        server = http
    }

    private fun stop() {
        server?.stop()
        server = null
        port = null
    }

    private companion object {
        const val PORT = 8766
        /** Le temps de passer d'un écran à l'autre (accueil → lecteur) sans couper la télécommande. */
        const val STOP_DELAY_MS = 5_000L
    }
}
