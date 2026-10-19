package dev.wido.tv

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Client HTTP minimal : https uniquement, tailles bornées, aucune dépendance. */
object Http {
    class HttpException(message: String) : IOException(message)

    fun get(
        url: String,
        maxBytes: Int,
        timeoutMs: Int = 10_000,
        headers: Map<String, String> = emptyMap(),
    ): ByteArray {
        val conn = open(url, timeoutMs, headers)
        try {
            check(conn)
            val declared = conn.contentLengthLong
            if (declared > maxBytes) throw HttpException("Réponse trop grande ($declared octets)")
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) throw HttpException("Réponse trop grande")
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Lit au plus `maxBytes` octets du début de la réponse, sans erreur si elle est plus longue. */
    fun getPrefix(url: String, maxBytes: Int, timeoutMs: Int = 6_000): ByteArray {
        val conn = open(url, timeoutMs, emptyMap())
        try {
            check(conn)
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                while (out.size() < maxBytes) {
                    val n = input.read(buf, 0, minOf(buf.size, maxBytes - out.size()))
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    fun download(
        url: String,
        dest: File,
        maxBytes: Long,
        timeoutMs: Int = 30_000,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        val conn = open(url, timeoutMs, emptyMap())
        try {
            check(conn)
            val total = conn.contentLengthLong
            if (total > maxBytes) throw HttpException("Fichier trop grand ($total octets)")
            var done = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        done += n
                        if (done > maxBytes) throw HttpException("Fichier trop grand")
                        out.write(buf, 0, n)
                        onProgress(done, total)
                    }
                }
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, timeoutMs: Int, headers: Map<String, String>): HttpURLConnection {
        if (!url.startsWith("https://")) throw HttpException("https requis : $url")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true // https -> https seulement : Java refuse https -> http
        conn.setRequestProperty("User-Agent", "WiDo/${BuildConfig.VERSION_NAME}")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return conn
    }

    private fun check(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code !in 200..299) throw HttpException("HTTP $code")
    }
}
