package dev.wido.tv

import android.os.Handler
import android.os.Looper
import dev.wido.core.PhoneProtocol
import java.security.SecureRandom

/**
 * Saisie depuis un téléphone : un petit serveur HTTP sur le réseau local, ouvert seulement tant que
 * le QR code est affiché. Le téléphone ouvre l'adresse du QR code (avec son jeton aléatoire), tape un
 * texte et l'envoie ; [onMessage] le reçoit sur le fil principal.
 */
class PhoneLink(
    private val labels: PhoneProtocol.Labels,
    private val onMessage: (PhoneProtocol.Message) -> Unit,
) {
    private val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val main = Handler(Looper.getMainLooper())
    private val server = MiniHttpServer("WiDoPhoneLink", token) { method, path, body ->
        when {
            method == "GET" && path == "/" -> MiniHttpServer.Response(200, "text/html; charset=utf-8", PhoneProtocol.page(token, labels))
            method == "POST" && path == "/send" -> {
                val message = PhoneProtocol.parseMessage(body)
                if (message == null) {
                    MiniHttpServer.Response(400, body = "Bad message")
                } else {
                    main.post { onMessage(message) }
                    MiniHttpServer.Response(200, body = "ok")
                }
            }
            else -> MiniHttpServer.Response(404, body = "Not found")
        }
    }

    /** Démarre le serveur ; renvoie l'adresse à mettre dans le QR code, ou null sans réseau local. */
    fun start(): String? {
        val ip = MiniHttpServer.localAddress() ?: return null
        val port = server.start(PORT) ?: return null
        return "http://$ip:$port/?t=$token"
    }

    fun stop() = server.stop()

    private companion object {
        const val PORT = 8765
    }
}
