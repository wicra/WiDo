package dev.wido.tv

import android.content.Context
import android.graphics.Color
import android.view.View

/**
 * Le vinyle de l'ouverture : disque noir, sillons, reflets qui tournent, anneau à la couleur du site
 * et étiquette blanche avec le logo, comme sur l'accueil. Dessiné directement (fluide sur une clé TV).
 */
internal class RevealView(context: Context, color: Int) : View(context) {
    // Dégradés créés une seule fois (rayon 1, centre 0,0), placés à chaque image par une matrice :
    // aucune allocation pendant l'animation.
    private val placement = android.graphics.Matrix()
    private val discShader = android.graphics.RadialGradient(
        0f, 0f, 1f, intArrayOf(0xFF1E1E24.toInt(), 0xFF0B0B0E.toInt()), null, android.graphics.Shader.TileMode.CLAMP,
    )
    private val sheenShader = android.graphics.SweepGradient(
        0f, 0f,
        intArrayOf(0, 0x26FFFFFF, 0, 0, 0x26FFFFFF, 0, 0),
        floatArrayOf(0f, 0.07f, 0.11f, 0.5f, 0.57f, 0.61f, 1f),
    )
    private val disc = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { shader = discShader }
    private val groove = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        this.color = Color.WHITE
        strokeWidth = 1.5f
    }
    private val sheen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { shader = sheenShader }
    private val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        this.color = color
    }
    private val label = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xF7FFFFFF.toInt() }
    private val picture = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val dest = android.graphics.RectF()
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var labelRadius = 0f
    private var spin = 0f

    /** Opacité du logo (il s'efface pendant l'ouverture). */
    var logoAlpha = 1f

    /** Logo du site sur l'étiquette (dès qu'il est chargé). */
    var logo: android.graphics.Bitmap? = null
        set(v) {
            field = v
            invalidate()
        }

    private val tileRect = android.graphics.RectF()
    private val tilePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF0B0B12.toInt() }
    private var tileCorner = 0f
    private var tileMode = false

    /** Ouverture depuis une vignette rectangulaire : un rectangle à coins arrondis qui grandit. */
    fun setTile(l: Float, t: Float, r: Float, b: Float, corner: Float) {
        tileMode = true
        tileRect.set(l, t, r, b)
        tileCorner = corner
        invalidate()
    }

    fun set(x: Float, y: Float, r: Float, labelR: Float, angle: Float) {
        cx = x
        cy = y
        radius = r
        labelRadius = labelR
        spin = angle
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        if (tileMode) {
            canvas.drawRoundRect(tileRect, tileCorner, tileCorner, tilePaint)
            return
        }
        if (radius <= 0) return
        placement.setScale(radius, radius)
        placement.postTranslate(cx, cy)
        discShader.setLocalMatrix(placement)
        canvas.drawCircle(cx, cy, radius, disc)
        // Sillons : de l'anneau au bord, un trait fin tous les 14 px environ.
        val inner = labelRadius * 1.12f
        val outer = radius * 0.95f
        if (outer > inner) {
            val count = ((outer - inner) / 14f).toInt().coerceIn(4, 40)
            for (i in 0..count) {
                groove.alpha = if (i % 2 == 0) 0x2A else 0x14
                canvas.drawCircle(cx, cy, inner + (outer - inner) * i / count, groove)
            }
        }
        placement.setRotate(spin)
        placement.postTranslate(cx, cy)
        sheenShader.setLocalMatrix(placement)
        canvas.drawCircle(cx, cy, radius * 0.95f, sheen)
        ring.strokeWidth = maxOf(3f, labelRadius * 0.06f)
        canvas.drawCircle(cx, cy, labelRadius * 1.06f, ring)
        canvas.drawCircle(cx, cy, labelRadius, label)
        logo?.let { bitmap ->
            val half = labelRadius * 0.62f
            val ratio = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
            dest.set(cx - half, cy - half * ratio, cx + half, cy + half * ratio)
            picture.alpha = (logoAlpha * 255).toInt()
            if (picture.alpha > 0) canvas.drawBitmap(bitmap, null, dest, picture)
        }
    }
}
