package dev.wido.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Icônes dessinées : parfaitement centrées dans leur bouton, quelle que soit la police du téléphone
 * (les caractères ↻ ⏪ ▶ dépendent de polices de secours et tombent souvent à côté du centre).
 */
enum class Glyph { Refresh, Play, Pause, Rewind, Forward, Up, Down, Left, Right, Back, Home, Menu, VolumeDown, VolumeUp, Mute }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, color) }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val s = size.width
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun poly(pts: List<Offset>, closed: Boolean = true) = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
        if (closed) close()
    }
    fun speaker() = drawPath(poly(listOf(p(0.16f, 0.4f), p(0.3f, 0.4f), p(0.47f, 0.25f), p(0.47f, 0.75f), p(0.3f, 0.6f), p(0.16f, 0.6f))), color)
    fun wave(r: Float) = drawArc(color, -45f, 90f, false, topLeft = p(0.47f - r, 0.5f - r), size = Size(2 * r * s, 2 * r * s), style = stroke)

    when (glyph) {
        Glyph.Refresh -> {
            val r = 0.3f * s
            val c = p(0.5f, 0.5f)
            drawArc(color, 30f, 290f, false, topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = stroke)
            // Pointe de flèche au bout de l'arc, dans le sens de rotation.
            val end = Math.toRadians(320.0)
            val tip = Offset(c.x + r * cos(end).toFloat(), c.y + r * sin(end).toFloat())
            val dir = Offset(-sin(end).toFloat(), cos(end).toFloat())
            val normal = Offset(-dir.y, dir.x)
            val head = s * 0.13f
            drawPath(poly(tip + dir * head, tip - normal * head, tip + normal * head), color)
        }
        Glyph.Play -> drawPath(poly(listOf(p(0.34f, 0.24f), p(0.78f, 0.5f), p(0.34f, 0.76f))), color)
        Glyph.Pause -> {
            drawRoundRect(color, p(0.3f, 0.25f), Size(0.14f * s, 0.5f * s), CornerRadius(0.04f * s))
            drawRoundRect(color, p(0.56f, 0.25f), Size(0.14f * s, 0.5f * s), CornerRadius(0.04f * s))
        }
        Glyph.Rewind -> {
            drawPath(poly(listOf(p(0.5f, 0.28f), p(0.16f, 0.5f), p(0.5f, 0.72f))), color)
            drawPath(poly(listOf(p(0.84f, 0.28f), p(0.5f, 0.5f), p(0.84f, 0.72f))), color)
        }
        Glyph.Forward -> {
            drawPath(poly(listOf(p(0.16f, 0.28f), p(0.5f, 0.5f), p(0.16f, 0.72f))), color)
            drawPath(poly(listOf(p(0.5f, 0.28f), p(0.84f, 0.5f), p(0.5f, 0.72f))), color)
        }
        Glyph.Up -> drawPath(poly(listOf(p(0.28f, 0.62f), p(0.5f, 0.4f), p(0.72f, 0.62f)), closed = false), color, style = stroke)
        Glyph.Down -> drawPath(poly(listOf(p(0.28f, 0.38f), p(0.5f, 0.6f), p(0.72f, 0.38f)), closed = false), color, style = stroke)
        Glyph.Left -> drawPath(poly(listOf(p(0.62f, 0.28f), p(0.4f, 0.5f), p(0.62f, 0.72f)), closed = false), color, style = stroke)
        Glyph.Right -> drawPath(poly(listOf(p(0.38f, 0.28f), p(0.6f, 0.5f), p(0.38f, 0.72f)), closed = false), color, style = stroke)
        Glyph.Back -> {
            drawPath(poly(listOf(p(0.76f, 0.74f), p(0.76f, 0.46f), p(0.26f, 0.46f)), closed = false), color, style = stroke)
            drawPath(poly(listOf(p(0.4f, 0.32f), p(0.26f, 0.46f), p(0.4f, 0.6f)), closed = false), color, style = stroke)
        }
        Glyph.Home -> {
            drawPath(poly(listOf(p(0.16f, 0.5f), p(0.5f, 0.2f), p(0.84f, 0.5f)), closed = false), color, style = stroke)
            drawPath(poly(listOf(p(0.27f, 0.42f), p(0.27f, 0.8f), p(0.73f, 0.8f), p(0.73f, 0.42f)), closed = false), color, style = stroke)
        }
        Glyph.Menu -> listOf(0.3f, 0.5f, 0.7f).forEach { y ->
            drawLine(color, p(0.22f, y), p(0.78f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        Glyph.VolumeDown -> {
            speaker()
            wave(0.14f)
        }
        Glyph.VolumeUp -> {
            speaker()
            wave(0.14f)
            wave(0.27f)
        }
        Glyph.Mute -> {
            speaker()
            drawLine(color, p(0.62f, 0.4f), p(0.82f, 0.6f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, p(0.82f, 0.4f), p(0.62f, 0.6f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}
