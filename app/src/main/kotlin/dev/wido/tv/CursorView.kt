package dev.wido.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * Curseur de souris dessiné par-dessus la page, déplacé aux flèches de la télécommande : la plupart des
 * sites de streaming ne se pilotent pas au focus. La pointe de la flèche est le point de clic.
 * Il devient bleu pendant l'appui sur OK et se masque seul après un moment d'inactivité.
 */
class CursorView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val scale = 1.8f * density

    // Flèche classique, pointe en (0, 0), tracée sur une grille d'environ 13 x 20.
    private val arrow = Path().apply {
        moveTo(0f, 0f)
        lineTo(0f, 17f)
        lineTo(4.6f, 13.2f)
        lineTo(7.6f, 20f)
        lineTo(10.2f, 18.9f)
        lineTo(7.3f, 12.2f)
        lineTo(13f, 12.2f)
        close()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = IDLE_COLOR
        setShadowLayer(2.5f, 1f, 1.5f, 0x99000000.toInt())
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        strokeJoin = Paint.Join.ROUND
        color = 0xFF0A0E1A.toInt()
    }
    private val hide = Runnable { alpha = 0f }

    var px = 0f
        private set
    var py = 0f
        private set

    /** Vrai pendant l'appui sur OK. */
    var pressedState = false
        set(value) {
            if (field == value) return
            field = value
            fill.color = if (value) PRESSED_COLOR else IDLE_COLOR
            wake()
            invalidate()
        }

    init {
        isFocusable = false
        isClickable = false
        setLayerType(LAYER_TYPE_SOFTWARE, null) // nécessaire au setShadowLayer
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (ow == 0 && oh == 0) {
            px = w / 2f
            py = h / 2f
        }
    }

    /** Déplace le curseur ; renvoie le dépassement (dx, dy) hors de l'écran, pour faire défiler. */
    fun moveBy(dx: Float, dy: Float): Pair<Float, Float> {
        val nx = (px + dx).coerceIn(0f, (width - 1).toFloat())
        val ny = (py + dy).coerceIn(0f, (height - 1).toFloat())
        val overflow = (px + dx - nx) to (py + dy - ny)
        px = nx
        py = ny
        wake()
        invalidate()
        return overflow
    }

    fun wake() {
        alpha = 1f
        removeCallbacks(hide)
        postDelayed(hide, 3_000)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(px, py)
        canvas.scale(scale, scale)
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, outline)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hide)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val IDLE_COLOR = 0xFFFFFFFF.toInt()
        const val PRESSED_COLOR = 0xFF1E90FF.toInt()
    }
}
