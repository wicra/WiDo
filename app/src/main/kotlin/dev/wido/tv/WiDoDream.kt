package dev.wido.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.view.View
import androidx.compose.ui.graphics.toArgb
import dev.wido.tv.ui.Palette
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

/**
 * L'écran de veille de WiDo (à choisir dans Android → Écran de veille), à la place de l'arc-en-ciel du
 * système : le fond de l'appli, ses halos de lumière (bleu et violet, ou l'ambiance choisie) qui dérivent
 * lentement, et une plaque de verre avec l'heure, la date et le nom de l'appli, qui change de place toutes
 * les minutes pour ne pas marquer l'écran.
 */
class WiDoDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Ambiance de couleur choisie dans les réglages (lue au démarrage des réglages).
        (application as WiDoApp).settings
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        setContentView(DreamView(this))
    }
}

/** Plus Jakarta Sans à la graisse voulue (position de l'axe `wght`), comme dans le reste de l'appli. */
private fun Paint.jakarta(context: Context, weight: Int) {
    typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)
    setFontVariationSettings("'wght' $weight")
}

private class DreamView(context: Context) : View(context) {
    private val background = Palette.Background.toArgb()
    private val glows = listOf(Palette.Accent.toArgb(), Palette.Violet.toArgb(), Palette.Purple.toArgb())
    private val density = resources.displayMetrics.density

    // Dégradés créés une fois (rayon 1, centre 0,0), placés par une matrice : aucune allocation par image.
    private val shaders = glows.map { color ->
        RadialGradient(0f, 0f, 1f, intArrayOf((color and 0x00FFFFFF) or 0x99000000.toInt(), color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    }
    private val glowPaints = shaders.map { Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = it } }
    private val matrix = Matrix()

    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1FFFFFFF }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0x2EFFFFFF
    }
    private val clock = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 96 * density
        jakarta(context, 800)
        letterSpacing = -0.03f
    }
    private val date = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3FFFFFF.toInt()
        textSize = 22 * density
        jakarta(context, 600)
    }
    private val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FFFFFF.toInt()
        textSize = 18 * density
        jakarta(context, 800)
        letterSpacing = 0.12f
    }
    private val panel = RectF()
    private val started = System.currentTimeMillis()
    private var spot = 0
    private var spotChangedAt = 0L

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val t = (System.currentTimeMillis() - started) / 1000f
        canvas.drawColor(background)
        // Trois halos sur des trajets lents (une à deux minutes par tour), jamais immobiles.
        val paths = arrayOf(
            floatArrayOf(0.22f, 0.25f, 0.18f, 0.14f, 97f, 0.62f),
            floatArrayOf(0.78f, 0.75f, 0.16f, 0.16f, 131f, 0.58f),
            floatArrayOf(0.55f, 0.45f, 0.24f, 0.2f, 173f, 0.42f),
        )
        for (i in glowPaints.indices) {
            val p = paths[i]
            val a = (t / p[4]) * 2f * Math.PI.toFloat()
            val cx = w * (p[0] + p[2] * cos(a))
            val cy = h * (p[1] + p[3] * sin(a * 0.8f))
            val r = w * p[5]
            matrix.setScale(r, r)
            matrix.postTranslate(cx, cy)
            shaders[i].setLocalMatrix(matrix)
            canvas.drawCircle(cx, cy, r, glowPaints[i])
        }
        // La plaque de verre change de coin chaque minute (fondu), pour ne pas marquer l'écran.
        val now = System.currentTimeMillis()
        if (now - spotChangedAt > 60_000) {
            spot = (spot + 1 + (now / 1000 % 3).toInt()) % SPOTS.size
            spotChangedAt = now
        }
        val fade = ((now - spotChangedAt) / 1200f).coerceIn(0f, 1f) * ((60_000 - (now - spotChangedAt)) / 1200f).coerceIn(0f, 1f)
        val time = DateFormat.getTimeFormat(context).format(Date(now))
        val day = DateFormat.format("EEEE d MMMM", Date(now)).toString().replaceFirstChar { it.uppercase() }
        val pad = 36 * density
        val pw = maxOf(clock.measureText(time), date.measureText(day)) + pad * 2
        val ph = clock.textSize + date.textSize + brand.textSize + pad * 2 + 22 * density
        val (fx, fy) = SPOTS[spot]
        val left = (w - pw) * fx
        val top = (h - ph) * fy
        panel.set(left, top, left + pw, top + ph)
        val alpha = (fade * 255).toInt()
        glass.alpha = (0x1F * fade).toInt()
        border.alpha = (0x2E * fade).toInt()
        clock.alpha = alpha
        date.alpha = (0xB3 * fade).toInt()
        brand.alpha = (0x80 * fade).toInt()
        val corner = 28 * density
        canvas.drawRoundRect(panel, corner, corner, glass)
        canvas.drawRoundRect(panel, corner, corner, border)
        var y = top + pad + clock.textSize * 0.82f
        canvas.drawText(time, left + pad, y, clock)
        y += date.textSize + 14 * density
        canvas.drawText(day, left + pad, y, date)
        y += brand.textSize + 14 * density
        canvas.drawText("WiDo", left + pad, y, brand)
        // 20 images par seconde suffisent à une dérive aussi lente, et ménagent la clé.
        postInvalidateDelayed(50)
    }

    private companion object {
        /** Emplacements de la plaque, en fractions de l'espace libre. */
        val SPOTS = listOf(0.1f to 0.15f, 0.85f to 0.2f, 0.5f to 0.5f, 0.12f to 0.85f, 0.88f to 0.82f)
    }
}
