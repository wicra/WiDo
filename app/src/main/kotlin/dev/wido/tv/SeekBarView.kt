package dev.wido.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.compose.ui.graphics.toArgb
import dev.wido.core.PlayerFormat
import dev.wido.tv.ui.Palette

/**
 * La barre de lecture de WiDo, en bas de l'écran, pour une vidéo de site en plein écran (YouTube…) : où on en
 * est, la durée, et la seconde visée par ◀ ▶ avant d'y aller (« +1:30 »). Les sites cachent leur propre barre
 * et la télécommande ne peut pas la viser : celle-ci se pilote aux flèches.
 */
class SeekBarView(context: Context, private val scale: Float) : View(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density * scale

    private val shade = Paint()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
    private val played = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.Accent.toArgb() or 0xFF000000.toInt() }
    private val ahead = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x80FFFFFF.toInt() }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val titlePaint = text(18f, 0xFFFFFFFF.toInt(), 800)
    private val timePaint = text(16f, 0xE6FFFFFF.toInt(), 700)
    private val targetPaint = text(17f, 0xFF0B0B12.toInt(), 800)
    private val hintPaint = text(13f, 0x99FFFFFF.toInt(), 600)
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val rect = RectF()

    private fun text(size: Float, color: Int, weight: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = dp(size)
        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)
        setFontVariationSettings("'wght' $weight")
    }

    var title: String = ""
    var hint: String = ""

    /** Position du moment (s), relue à chaque image : la barre avance avec la vidéo. */
    var position: () -> Double = { 0.0 }
    var duration = 0.0
    /** Seconde visée par ◀ ▶, pas encore appliquée ; négative : aucune. */
    var target = -1.0
        set(v) {
            field = v
            invalidate()
        }

    val isShowing: Boolean get() = visibility == VISIBLE && alpha > 0f

    fun show() {
        animate().cancel()
        if (visibility != VISIBLE) {
            alpha = 0f
            visibility = VISIBLE
        }
        animate().alpha(1f).setDuration(160).start()
        invalidate()
    }

    fun hide() {
        if (visibility != VISIBLE) return
        animate().cancel()
        animate().alpha(0f).setDuration(220).withEndAction { visibility = GONE }.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        shade.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x00000000, 0xE6000000.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || duration <= 0) return
        canvas.drawRect(0f, 0f, w, h, shade)
        val side = dp(56f)
        val now = position().coerceIn(0.0, duration)
        val left = side
        val right = w - side
        val barY = h - dp(64f)
        val barH = dp(6f)
        // Titre à gauche, temps à droite.
        val times = "${PlayerFormat.time(now)} / ${PlayerFormat.time(duration)}"
        val timesW = timePaint.measureText(times)
        val titleY = barY - dp(92f)
        canvas.drawText(times, right - timesW, titleY, timePaint)
        val shown = TextUtils.ellipsize(title, titlePaint, (right - left - timesW - dp(24f)).coerceAtLeast(0f), TextUtils.TruncateAt.END)
        canvas.drawText(shown, 0, shown.length, left, titleY, titlePaint)
        // La barre : déjà vu, puis jusqu'à la seconde visée.
        fun x(t: Double) = (left + (right - left) * (t / duration)).toFloat()
        rect.set(left, barY, right, barY + barH)
        canvas.drawRoundRect(rect, barH, barH, track)
        val t = target
        if (t >= 0 && t > now) {
            rect.set(x(now), barY, x(t), barY + barH)
            canvas.drawRoundRect(rect, barH, barH, ahead)
        }
        rect.set(left, barY, x(if (t >= 0) minOf(t, now) else now), barY + barH)
        canvas.drawRoundRect(rect, barH, barH, played)
        val knobX = x(if (t >= 0) t else now)
        canvas.drawCircle(knobX, barY + barH / 2, dp(if (t >= 0) 11f else 8f), knob)
        // Seconde visée : une pastille au-dessus du curseur de la barre, avec l'écart (« +1:30 »).
        if (t >= 0) {
            val delta = t - now
            val label = "${PlayerFormat.time(t)}  ${if (delta >= 0) "+" else "−"}${PlayerFormat.time(kotlin.math.abs(delta))}"
            val lw = targetPaint.measureText(label) + dp(24f)
            val lh = dp(34f)
            val lx = (knobX - lw / 2).coerceIn(left, right - lw)
            val ly = barY - dp(40f) - lh / 2
            rect.set(lx, ly, lx + lw, ly + lh)
            canvas.drawRoundRect(rect, lh / 2, lh / 2, bubble)
            canvas.drawText(label, lx + dp(12f), ly + lh / 2 + targetPaint.textSize * 0.36f, targetPaint)
        }
        if (hint.isNotEmpty()) canvas.drawText(hint, left, barY + barH + dp(30f), hintPaint)
        // La vidéo avance : la barre aussi (2 images par seconde suffisent).
        if (visibility == VISIBLE) postInvalidateDelayed(500)
    }
}
