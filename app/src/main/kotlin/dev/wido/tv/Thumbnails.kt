package dev.wido.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.wido.core.ContinueWatching
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Vignettes de la rangée « Reprendre » : l'image de la vidéo là où on l'a quittée (capturée sur cet
 * écran), sinon l'image de la page (`og:image`…) téléchargée en https, bornée en taille, réduite à la
 * taille d'une carte, et gardée en cache (disque et mémoire).
 */
class Thumbnails(private val context: Context) {
    private val dir by lazy { File(context.cacheDir, "thumbs").apply { mkdirs() } }
    private val memory = ConcurrentHashMap<String, ImageBitmap>()

    /** Mémoire faible : les images seront relues du disque au besoin. */
    fun trimMemory() = memory.clear()

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

    // --- Image de la vidéo, capturée pendant la lecture ---

    private val frames by lazy { File(context.filesDir, "frames").apply { mkdirs() } }

    private fun frameFile(pageUrl: String) = File(frames, sha1(ContinueWatching.canonical(pageUrl)) + ".jpg")

    /**
     * Garde l'image de la vidéo de `pageUrl` (réduite, JPEG). Une image presque noire (vidéo protégée,
     * lecteur pas encore dessiné) est ignorée. Appelé hors du fil principal de préférence.
     */
    fun saveFrame(pageUrl: String, bitmap: Bitmap) {
        if (isMostlyBlack(bitmap)) return
        val width = minOf(FRAME_WIDTH, bitmap.width)
        val height = (bitmap.height * width.toFloat() / bitmap.width).toInt().coerceAtLeast(1)
        val scaled = if (width == bitmap.width) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
        val file = frameFile(pageUrl)
        val tmp = File(file.parentFile, file.name + ".tmp")
        runCatching {
            tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 78, it) }
            tmp.renameTo(file)
        }
        memory.keys.removeAll { it.startsWith("frame:" + file.name) }
        // Pas plus de 40 images : les plus anciennes partent.
        frames.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_FRAMES)?.forEach { it.delete() }
    }

    /** L'image de la vidéo si cet écran en a capturé une, sinon celle de la page. */
    suspend fun forVideo(pageUrl: String, imageUrl: String?): ImageBitmap? {
        val file = frameFile(pageUrl)
        if (file.exists()) {
            val key = "frame:" + file.name + ":" + file.lastModified()
            memory[key]?.let { return it }
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) }
            if (bitmap != null) return bitmap.asImageBitmap().also { memory[key] = it }
        }
        return imageUrl?.let { load(it) }
    }

    private fun isMostlyBlack(bitmap: Bitmap): Boolean {
        var bright = 0
        var total = 0
        val stepX = maxOf(1, bitmap.width / 24)
        val stepY = maxOf(1, bitmap.height / 14)
        for (y in 0 until bitmap.height step stepY) for (x in 0 until bitmap.width step stepX) {
            val c = bitmap.getPixel(x, y)
            val luma = (0.3 * ((c shr 16) and 0xFF) + 0.59 * ((c shr 8) and 0xFF) + 0.11 * (c and 0xFF))
            if (luma > 24) bright++
            total++
        }
        return total == 0 || bright < total / 20
    }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_BYTES = 1_500_000
        const val TARGET_WIDTH = 480
        const val FRAME_WIDTH = 480
        const val MAX_FRAMES = 40
    }
}
