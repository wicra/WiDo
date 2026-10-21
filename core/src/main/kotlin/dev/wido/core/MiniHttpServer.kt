package dev.wido.core

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Petit serveur HTTP pour le réseau local (saisie, télécommande, lien entre écrans). Le gestionnaire
 * vérifie lui-même le jeton (`t` dans l'adresse). Jusqu'à 4 requêtes sont traitées en même temps : une
 * synchro lente ne retarde jamais une touche de la télécommande.
 */
class MiniHttpServer(
    private val name: String,
    private val maxBody: Int = PhoneProtocol.MAX_BODY,
    /** Journal des erreurs (Android : Log.w). */
    private val log: (String, Throwable?) -> Unit = { _, _ -> },
    /** (requête, corps) → réponse. Appelé hors du fil principal, éventuellement sur plusieurs fils. */
    private val handler: (request: PhoneProtocol.Request, body: String) -> Response,
) {
    data class Response(val status: Int, val type: String = "text/plain", val body: String = "")

    @Volatile private var server: ServerSocket? = null
    @Volatile private var workers: ExecutorService? = null

    val isRunning: Boolean get() = server?.isClosed == false

    /** Démarre sur `port` (ou un port libre) ; renvoie le port, ou null. */
    fun start(port: Int): Int? {
        val socket = runCatching { ServerSocket(port) }.recoverCatching { ServerSocket(0) }.getOrNull() ?: return null
        server = socket
        workers = Executors.newFixedThreadPool(WORKERS) { r -> Thread(r, "$name-worker").apply { isDaemon = true } }
        Thread({ serve(socket) }, name).apply { isDaemon = true }.start()
        return socket.localPort
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        workers?.shutdownNow()
        workers = null
    }

    private fun serve(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                return // fermé par stop()
            } catch (e: Exception) {
                log("Connexion refusée", e)
                continue
            }
            val pool = workers
            if (pool == null) {
                runCatching { client.close() }
                return
            }
            runCatching { pool.execute { runCatching { client.use { handle(it) } }.onFailure { log("Requête illisible", it) } } }
                .onFailure { runCatching { client.close() } }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 5_000
        val input = BufferedInputStream(client.getInputStream())
        val request = PhoneProtocol.parseRequestLine(readLine(input) ?: return) ?: return respond(client, Response(400, body = "Bad request"))
        var length = 0
        while (true) {
            val header = readLine(input) ?: break
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        if (length > maxBody) return respond(client, Response(413, body = "Too large"))
        val body = ByteArray(length.coerceAtLeast(0))
        var read = 0
        while (read < body.size) {
            val n = input.read(body, read, body.size - read)
            if (n < 0) break
            read += n
        }
        val remote = (client.remoteSocketAddress as? java.net.InetSocketAddress)?.address?.hostAddress.orEmpty()
        respond(client, handler(request.copy(remote = remote), String(body, 0, read, Charsets.UTF_8)))
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

    private fun respond(client: Socket, response: Response) {
        val bytes = response.body.toByteArray(Charsets.UTF_8)
        val reason = when (response.status) {
            200 -> "OK"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            else -> "Bad Request"
        }
        val head = "HTTP/1.1 ${response.status} $reason\r\nContent-Type: ${response.type}\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply {
            write(head.toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
    }

    companion object {
        private const val WORKERS = 4

        fun json(body: String) = Response(200, "application/json; charset=utf-8", body)
        val FORBIDDEN = Response(403, body = "Forbidden")
        val NOT_FOUND = Response(404, body = "Not found")
        val BAD_REQUEST = Response(400, body = "Bad request")

        /** Adresse IPv4 de la TV sur le réseau local (Wi-Fi ou Ethernet). */
        fun localAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .sortedByDescending { it.isSiteLocalAddress }
                .firstOrNull()
                ?.hostAddress
        }.getOrNull()
    }
}
