package dev.wido.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * Curseur dessiné par-dessus la page, déplacé aux flèches de la télécommande : la plupart des
 * sites de streaming ne se pilotent pas au focus. Il se masque seul après un moment d'inactivité.
 */
class CursorView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(6f * density, 0f, 0f, 0xFF000000.toInt())
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7C5CFF.toInt() }
    private val hide = Runnable { alpha = 0f }

    var px = 0f
        private set
    var py = 0f
        private set

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
        canvas.drawCircle(px, py, 14f * density, ring)
        canvas.drawCircle(px, py, 4f * density, dot)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hide)
        super.onDetachedFromWindow()
    }
}
