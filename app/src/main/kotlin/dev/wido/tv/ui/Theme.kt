package dev.wido.tv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

object Palette {
    val Background = Color(0xFF07080D)
    val Surface = Color(0xFF14171F)
    val SurfaceHigh = Color(0xFF1C2030)
    val Accent = Color(0xFF7C5CFF)
    val Gold = Color(0xFFE8C26A)
    val TextPrimary = Color(0xFFF4F1EA)
    val TextSecondary = Color(0xFF9A9FB0)
    val Warning = Color(0xFFFFB74D)
    val Ok = Color(0xFF4CD08B)
}

val CardShape = RoundedCornerShape(18.dp)
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
 * Rend un élément activable à la télécommande : zoom, halo coloré et contour clair quand il a le
 * focus, clic au bouton OK et clic long en option.
 * `onFocusChanged` doit précéder `combinedClickable` pour observer le focus de ce dernier.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvFocusable(
    shape: Shape = CardShape,
    glow: Color = Palette.Accent,
    onFocusChange: (Boolean) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.09f else 1f, label = "focusScale")
    val elevation by animateDpAsState(if (focused) 28.dp else 0.dp, label = "focusGlow")
    return this
        .onFocusChanged {
            focused = it.isFocused
            onFocusChange(it.isFocused)
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(elevation, shape, ambientColor = glow, spotColor = glow)
        .border(3.dp, if (focused) Palette.Gold else Color.Transparent, shape)
        .clip(shape)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
}
