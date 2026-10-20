package dev.wido.tv

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Vignettes de la rangée « Reprendre » : téléchargées en https, bornées en taille, réduites à la
 * taille d'une carte, et gardées en cache (disque et mémoire).
 */
class Thumbnails(private val context: Context) {
    private val dir by lazy { File(context.cacheDir, "thumbs").apply { mkdirs() } }
    private val memory = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        memory[url]?.let { return@withContext it }
        val file = File(dir, sha1(url))
        if (!file.exists()) {
            val bytes = runCatching { Http.get(url, MAX_BYTES, timeoutMs = 8_000) }.getOrNull() ?: return@withContext null
            file.writeBytes(bytes)
        }
        // Réduite à ~480 px de large : une carte n'en affiche pas plus.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
        bitmap.asImageBitmap().also { memory[url] = it }
    }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_BYTES = 1_500_000
        const val TARGET_WIDTH = 480
    }
}
