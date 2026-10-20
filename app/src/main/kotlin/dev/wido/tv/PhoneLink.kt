package dev.wido.tv

import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.wido.core.PhoneProtocol
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
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
    @Volatile private var server: ServerSocket? = null

    /** Démarre le serveur ; renvoie l'adresse à mettre dans le QR code, ou null sans réseau local. */
    fun start(): String? {
        val ip = localAddress() ?: return null
        val socket = runCatching { ServerSocket(PORT) }.recoverCatching { ServerSocket(0) }.getOrNull() ?: return null
        server = socket
        Thread({ serve(socket) }, "WiDoPhoneLink").apply { isDaemon = true }.start()
        return "http://$ip:${socket.localPort}/?t=$token"
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun serve(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                return // fermé par stop()
            } catch (e: Exception) {
                Log.w(TAG, "Connexion refusée", e)
                continue
            }
            runCatching { client.use { handle(it) } }.onFailure { Log.w(TAG, "Requête illisible", it) }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 5_000
        val input = BufferedInputStream(client.getInputStream())
        val request = PhoneProtocol.parseRequestLine(readLine(input) ?: return) ?: return respond(client, 400, "text/plain", "Bad request")
        var length = 0
        while (true) {
            val header = readLine(input) ?: break
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        if (!PhoneProtocol.tokenMatches(token, request.query["t"])) return respond(client, 403, "text/plain", "Forbidden")
        when {
            request.method == "GET" && request.path == "/" ->
                respond(client, 200, "text/html; charset=utf-8", PhoneProtocol.page(token, labels))
            request.method == "POST" && request.path == "/send" -> {
                if (length <= 0 || length > PhoneProtocol.MAX_BODY) return respond(client, 413, "text/plain", "Too large")
                val body = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(body, read, length - read)
                    if (n < 0) break
                    read += n
                }
                val message = PhoneProtocol.parseMessage(String(body, 0, read, Charsets.UTF_8))
                    ?: return respond(client, 400, "text/plain", "Bad message")
                main.post { onMessage(message) }
                respond(client, 200, "text/plain", "ok")
            }
            else -> respond(client, 404, "text/plain", "Not found")
        }
    }

    /** Une ligne terminée par CRLF, bornée à 4 Ko. */
    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (out.size() < 4096) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toString("UTF-8")
            if (b == '\n'.code) return out.toString("UTF-8").trimEnd('\r')
            out.write(b)
        }
        return null
    }

    private fun respond(client: Socket, status: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            403 -> "Forbidden"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            else -> "Bad Request"
        }
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply {
            write(head.toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
    }

    /** Adresse IPv4 de la TV sur le réseau local (Wi-Fi ou Ethernet). */
    private fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .sortedByDescending { it.isSiteLocalAddress }
            .firstOrNull()
            ?.hostAddress
    }.getOrNull()

    private companion object {
        const val TAG = "WiDoPhone"
        const val PORT = 8765
    }
}
