package dev.wido.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.wido.core.Site
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Icône d'un site et la couleur qui en est tirée (null si l'icône est grise, blanche ou noire). */
data class SiteIcon(val image: ImageBitmap, val color: Color?)

/**
 * Icônes des cartes et leur couleur. Téléchargées directement depuis le site (aucun service tiers),
 * en https seulement, bornées en taille, puis mises en cache sur disque.
 *
 * La couleur de chaque site est aussi gardée dans un petit fichier, relu d'un coup : l'accueil
 * s'affiche directement en couleur, sans attendre le décodage des icônes. Une icône périmée est
 * servie tout de suite et rafraîchie en arrière-plan.
 */
class FaviconRepository(private val context: Context) {
    private val dir by lazy { File(context.filesDir, "favicons").apply { mkdirs() } }
    private val memory = ConcurrentHashMap<String, SiteIcon>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = ConcurrentHashMap.newKeySet<String>()

    private val colorsFile get() = File(dir, "colors.txt")

    /** Couleur connue de chaque site (clé → ARGB), relue une fois depuis le disque. */
    private val colors: ConcurrentHashMap<String, Int> by lazy {
        val map = ConcurrentHashMap<String, Int>()
        runCatching {
            colorsFile.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) line.substring(tab + 1).toLongOrNull(16)?.let { map[line.substring(0, tab)] = it.toInt() }
            }
        }
        map
    }

    // « v2 » : icônes reprises en plus grand (les anciennes, souvent en 16-32 px, étaient floues).
    private fun keyOf(site: Site) = sha1("v2:" + (site.icon ?: site.url))

    /** Couleur déjà connue du site, sans réseau ni décodage : de quoi colorer la carte dès le premier affichage. */
    fun cachedColor(site: Site): Color? = colors[keyOf(site)]?.let { Color(it) }

    suspend fun load(site: Site): SiteIcon? = withContext(Dispatchers.IO) {
        val key = keyOf(site)
        memory[key]?.let { return@withContext it }

        val png = File(dir, "$key.png")
        val miss = File(dir, "$key.miss")
        val now = System.currentTimeMillis()
        val fresh = png.exists() && now - png.lastModified() < TTL_MS
        val recentMiss = miss.exists() && now - miss.lastModified() < MISS_TTL_MS

        when {
            fresh || recentMiss -> Unit
            // Périmée : servie tout de suite, rafraîchie en arrière-plan.
            png.exists() -> if (refreshing.add(key)) scope.launch {
                try {
                    if (download(site, png, miss)) memory.remove(key)
                } finally {
                    refreshing.remove(key)
                }
            }
            else -> download(site, png, miss)
        }
        if (!png.exists()) return@withContext null
        val bitmap = BitmapFactory.decodeFile(png.path) ?: return@withContext null
        val color = dominantColor(bitmap)
        rememberColor(key, color)
        SiteIcon(bitmap.asImageBitmap(), color).also { memory[key] = it }
    }

    /** Télécharge l'icône. Vrai si une nouvelle icône a été enregistrée. */
    private fun download(site: Site, png: File, miss: File): Boolean {
        val bitmap = fetch(site)
        when {
            bitmap != null -> {
                png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                miss.delete()
            }
            png.exists() -> png.setLastModified(System.currentTimeMillis()) // échec : on garde l'ancienne icône
            else -> miss.writeText("")
        }
        return bitmap != null
    }

    @Synchronized
    private fun rememberColor(key: String, color: Color?) {
        val argb = color?.let { android.graphics.Color.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
        if (colors[key] == argb) return
        if (argb == null) colors.remove(key) else colors[key] = argb
        runCatching {
            colorsFile.writeText(colors.entries.joinToString("") { "${it.key}\t${Integer.toHexString(it.value)}\n" })
        }
    }

    /**
     * Couleur dominante de l'icône : moyenne des pixels colorés, pondérée par leur saturation.
     * Les gris, blancs et noirs sont ignorés (fonds, contours). Le résultat est ramené à une
     * teinte vive mais lisible pour servir de fond de carte.
     */
    private fun dominantColor(source: Bitmap): Color? {
        val size = 24
        val small = Bitmap.createScaledBitmap(source, size, size, true)
        val hsv = FloatArray(3)
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var total = 0.0
        for (y in 0 until size) for (x in 0 until size) {
            val p = small.getPixel(x, y)
            val alpha = AndroidColor.alpha(p) / 255f
            if (alpha < 0.5f) continue
            AndroidColor.colorToHSV(p, hsv)
            if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue
            val w = (hsv[1] * hsv[2] * alpha).toDouble()
            r += AndroidColor.red(p) * w
            g += AndroidColor.green(p) * w
            b += AndroidColor.blue(p) * w
            total += w
        }
        if (total <= 0.0) return null
        val average = AndroidColor.rgb((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
        AndroidColor.colorToHSV(average, hsv)
        hsv[1] = hsv[1].coerceIn(0.5f, 0.9f)
        hsv[2] = hsv[2].coerceIn(0.6f, 0.95f)
        return Color(AndroidColor.HSVToColor(hsv))
    }

    private fun fetch(site: Site): Bitmap? {
        val candidates = LinkedHashSet<String>()
        site.icon?.let { candidates += it }
        if (site.icon == null && site.url.startsWith("https://")) {
            runCatching { discover(site.url) }.getOrNull()?.let { candidates += it }
            runCatching { URI(site.url).resolve("/favicon.ico").toString() }.getOrNull()?.let { candidates += it }
        }
        // La plus grande des icônes proposées (on s'arrête dès qu'elle est assez grande pour un disque net).
        var best: Bitmap? = null
        for (url in candidates.take(5)) {
            if (!url.startsWith("https://")) continue
            val bytes = runCatching { Http.get(url, MAX_ICON_BYTES, timeoutMs = 6_000) }.getOrNull() ?: continue
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            if (bitmap.width < 16 || bitmap.height < 16) continue
            if (best == null || bitmap.width > best.width) best = bitmap
            if (bitmap.width >= SHARP_PX) break
        }
        return best?.let(::sharpen)
    }

    /**
     * Une petite icône (favicon de 16 à 48 px) agrandie au filtre doux devient floue sur la TV : on la
     * double d'abord pixel par pixel (bords francs), l'affichage n'a plus qu'à la réduire un peu.
     */
    private fun sharpen(bitmap: Bitmap): Bitmap {
        if (bitmap.width >= SHARP_PX) return bitmap
        val factor = (SHARP_PX + bitmap.width - 1) / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, bitmap.width * factor, bitmap.height * factor, false)
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
        const val SHARP_PX = 128
        const val TTL_MS = 14L * 24 * 60 * 60 * 1000
        const val MISS_TTL_MS = 24L * 60 * 60 * 1000
    }
}
