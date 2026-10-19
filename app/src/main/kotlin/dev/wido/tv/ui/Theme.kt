package dev.wido.tv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

object Palette {
    val Background = Color(0xFF0B0D12)
    val Surface = Color(0xFF161A22)
    val SurfaceFocused = Color(0xFF212736)
    val Accent = Color(0xFF7C5CFF)
    val TextPrimary = Color(0xFFF2F4F8)
    val TextSecondary = Color(0xFF9AA3B5)
    val Warning = Color(0xFFFFB74D)
}

val CardShape = RoundedCornerShape(20.dp)
val PillShape = RoundedCornerShape(50)

@Composable
fun WiDoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            background = Palette.Background,
            surface = Palette.Surface,
            onBackground = Palette.TextPrimary,
            onSurface = Palette.TextPrimary,
        ),
        content = content,
    )
}

/**
 * Rend un élément activable à la télécommande : zoom léger et contour blanc quand il a le focus.
 * `onFocusChanged` doit précéder `clickable` pour observer le focus de ce dernier.
 */
@Composable
fun Modifier.tvFocusable(shape: Shape = CardShape, onClick: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "focusScale")
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(3.dp, if (focused) Color.White else Color.Transparent, shape)
        .clip(shape)
        .clickable(onClick = onClick)
}
