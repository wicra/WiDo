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
enum class Glyph {
    Refresh, Play, Pause, Rewind, Forward, Up, Down, Left, Right, Back, Home, Menu, VolumeDown, VolumeUp, Mute,
    Star, ZoomIn, ZoomOut, Hide, Save, Sleep, Help, Sites, Cursor, Phone, Native, Tv, Next, Restart, More,
    Check, Warning, Blocked, Close, Plus, Globe, Dot,
}

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
            drawPath(poly(listOf(tip + dir * head, tip - normal * head, tip + normal * head)), color)
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
        Glyph.Star -> {
            val pts = (0 until 10).map { i ->
                val a = Math.toRadians(-90.0 + i * 36.0)
                val r = if (i % 2 == 0) 0.4f else 0.17f
                p(0.5f + r * cos(a).toFloat(), 0.53f + r * sin(a).toFloat())
            }
            drawPath(poly(pts), color)
        }
        Glyph.ZoomIn, Glyph.ZoomOut -> {
            drawCircle(color, radius = 0.25f * s, center = p(0.43f, 0.43f), style = stroke)
            drawLine(color, p(0.62f, 0.62f), p(0.82f, 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, p(0.32f, 0.43f), p(0.54f, 0.43f), strokeWidth = stroke.width * 0.8f, cap = StrokeCap.Round)
            if (glyph == Glyph.ZoomIn) drawLine(color, p(0.43f, 0.32f), p(0.43f, 0.54f), strokeWidth = stroke.width * 0.8f, cap = StrokeCap.Round)
        }
        Glyph.Hide -> {
            drawArc(color, 200f, 140f, false, topLeft = p(0.12f, 0.28f), size = Size(0.76f * s, 0.6f * s), style = stroke)
            drawArc(color, 20f, 140f, false, topLeft = p(0.12f, 0.12f), size = Size(0.76f * s, 0.6f * s), style = stroke)
            drawCircle(color, radius = 0.09f * s, center = p(0.5f, 0.5f))
            drawLine(color, p(0.2f, 0.82f), p(0.8f, 0.18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        Glyph.Save -> drawPath(poly(listOf(p(0.28f, 0.16f), p(0.72f, 0.16f), p(0.72f, 0.84f), p(0.5f, 0.66f), p(0.28f, 0.84f))), color, style = stroke)
        Glyph.Sleep -> {
            val moon = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(p(0.16f, 0.16f), Size(0.66f * s, 0.66f * s)))
            }
            val bite = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(p(0.36f, 0.06f), Size(0.6f * s, 0.6f * s)))
            }
            drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, moon, bite), color)
        }
        Glyph.Help -> {
            drawCircle(color, radius = 0.38f * s, center = p(0.5f, 0.5f), style = stroke)
            drawArc(color, 200f, 230f, false, topLeft = p(0.37f, 0.27f), size = Size(0.26f * s, 0.24f * s), style = stroke)
            drawLine(color, p(0.5f, 0.52f), p(0.5f, 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawCircle(color, radius = 0.05f * s, center = p(0.5f, 0.72f))
        }
        Glyph.Sites -> listOf(0.18f, 0.54f).forEach { x ->
            listOf(0.18f, 0.54f).forEach { y -> drawRoundRect(color, p(x, y), Size(0.28f * s, 0.28f * s), CornerRadius(0.07f * s)) }
        }
        Glyph.Cursor -> drawPath(poly(listOf(p(0.26f, 0.14f), p(0.26f, 0.8f), p(0.42f, 0.64f), p(0.54f, 0.88f), p(0.64f, 0.83f), p(0.52f, 0.6f), p(0.74f, 0.6f))), color)
        Glyph.Phone -> {
            drawRoundRect(color, p(0.3f, 0.1f), Size(0.4f * s, 0.8f * s), CornerRadius(0.08f * s), style = stroke)
            drawCircle(color, radius = 0.045f * s, center = p(0.5f, 0.76f))
        }
        Glyph.Native -> {
            drawRoundRect(color, p(0.1f, 0.2f), Size(0.8f * s, 0.6f * s), CornerRadius(0.08f * s), style = stroke)
            drawPath(poly(listOf(p(0.43f, 0.36f), p(0.62f, 0.5f), p(0.43f, 0.64f))), color)
        }
        Glyph.Tv -> {
            drawRoundRect(color, p(0.1f, 0.2f), Size(0.8f * s, 0.52f * s), CornerRadius(0.08f * s), style = stroke)
            drawLine(color, p(0.34f, 0.86f), p(0.66f, 0.86f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        Glyph.Next -> {
            drawPath(poly(listOf(p(0.22f, 0.26f), p(0.62f, 0.5f), p(0.22f, 0.74f))), color)
            drawRoundRect(color, p(0.66f, 0.26f), Size(0.12f * s, 0.48f * s), CornerRadius(0.03f * s))
        }
        Glyph.Restart -> {
            drawRoundRect(color, p(0.22f, 0.26f), Size(0.12f * s, 0.48f * s), CornerRadius(0.03f * s))
            drawPath(poly(listOf(p(0.78f, 0.26f), p(0.38f, 0.5f), p(0.78f, 0.74f))), color)
        }
        Glyph.More -> listOf(0.26f, 0.5f, 0.74f).forEach { x -> drawCircle(color, radius = 0.075f * s, center = p(x, 0.5f)) }
        Glyph.Check -> drawPath(poly(listOf(p(0.22f, 0.52f), p(0.42f, 0.72f), p(0.8f, 0.3f)), closed = false), color, style = stroke)
        Glyph.Warning -> {
            drawPath(poly(listOf(p(0.5f, 0.14f), p(0.88f, 0.82f), p(0.12f, 0.82f))), color, style = stroke)
            drawLine(color, p(0.5f, 0.4f), p(0.5f, 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawCircle(color, radius = 0.045f * s, center = p(0.5f, 0.7f))
        }
        Glyph.Blocked -> {
            drawCircle(color, radius = 0.36f * s, center = p(0.5f, 0.5f), style = stroke)
            drawLine(color, p(0.25f, 0.25f), p(0.75f, 0.75f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        Glyph.Close -> {
            drawLine(color, p(0.26f, 0.26f), p(0.74f, 0.74f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, p(0.74f, 0.26f), p(0.26f, 0.74f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        Glyph.Plus -> {
            drawLine(color, p(0.5f, 0.2f), p(0.5f, 0.8f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, p(0.2f, 0.5f), p(0.8f, 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        Glyph.Globe -> {
            drawCircle(color, radius = 0.36f * s, center = p(0.5f, 0.5f), style = stroke)
            drawOval(color, p(0.34f, 0.14f), Size(0.32f * s, 0.72f * s), style = stroke)
            drawLine(color, p(0.14f, 0.5f), p(0.86f, 0.5f), strokeWidth = stroke.width * 0.8f)
        }
        Glyph.Dot -> drawCircle(color, radius = 0.24f * s, center = p(0.5f, 0.5f))
        Glyph.Mute -> {
            speaker()
            drawLine(color, p(0.62f, 0.4f), p(0.82f, 0.6f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, p(0.82f, 0.4f), p(0.62f, 0.6f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}
