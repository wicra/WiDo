package dev.wido.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.wido.core.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Icônes des cartes. Téléchargées directement depuis le site (aucun service tiers), en https
 * seulement, bornées en taille, puis mises en cache sur disque.
 */
class FaviconRepository(private val context: Context) {
    private val dir by lazy { File(context.filesDir, "favicons").apply { mkdirs() } }
    private val memory = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun load(site: Site): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = sha1(site.icon ?: site.url)
        memory[key]?.let { return@withContext it }

        val png = File(dir, "$key.png")
        val miss = File(dir, "$key.miss")
        val now = System.currentTimeMillis()
        val fresh = png.exists() && now - png.lastModified() < TTL_MS
        val recentMiss = miss.exists() && now - miss.lastModified() < MISS_TTL_MS

        if (!fresh && !recentMiss) {
            val bitmap = fetch(site)
            when {
                bitmap != null -> {
                    png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    miss.delete()
                }
                png.exists() -> png.setLastModified(now) // échec : on garde l'ancienne icône
                else -> miss.writeText("")
            }
        }
        if (!png.exists()) return@withContext null
        BitmapFactory.decodeFile(png.path)?.asImageBitmap()?.also { memory[key] = it }
    }

    private fun fetch(site: Site): Bitmap? {
        val candidates = LinkedHashSet<String>()
        site.icon?.let { candidates += it }
        if (site.icon == null && site.url.startsWith("https://")) {
            runCatching { discover(site.url) }.getOrNull()?.let { candidates += it }
            runCatching { URI(site.url).resolve("/favicon.ico").toString() }.getOrNull()?.let { candidates += it }
        }
        for (url in candidates.take(5)) {
            if (!url.startsWith("https://")) continue
            val bytes = runCatching { Http.get(url, MAX_ICON_BYTES, timeoutMs = 6_000) }.getOrNull() ?: continue
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            if (bitmap.width >= 16 && bitmap.height >= 16) return bitmap
        }
        return null
    }

    /** Icônes déclarées dans la page d'accueil, la plus grande (et `apple-touch-icon`) d'abord. */
    private fun discover(pageUrl: String): List<String> {
        val html = String(Http.getPrefix(pageUrl, 100_000), Charsets.UTF_8)
        val base = URI(pageUrl)
        val found = mutableListOf<Pair<String, Int>>()
        for (tag in LINK.findAll(html)) {
            val t = tag.value
            val rel = attr(t, "rel")?.lowercase() ?: continue
            if ("icon" !in rel) continue
            val href = attr(t, "href")?.trim() ?: continue
            val absolute = runCatching { base.resolve(href).toString() }.getOrNull() ?: continue
            val size = attr(t, "sizes")?.substringBefore('x')?.toIntOrNull() ?: 0
            found += absolute to ((if ("apple-touch" in rel) 1000 else 0) + size)
        }
        return found.sortedByDescending { it.second }.map { it.first }
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] } }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val LINK = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        const val MAX_ICON_BYTES = 512_000
        const val TTL_MS = 14L * 24 * 60 * 60 * 1000
        const val MISS_TTL_MS = 24L * 60 * 60 * 1000
    }
}
