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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Palette {
    // Palette tirée de l'icône : bleu nuit, bleu électrique, blanc.
    val Background = Color(0xFF070B14)
    val Surface = Color(0xFF101626)
    val SurfaceHigh = Color(0xFF172036)
    val Accent = Color(0xFF1E90FF)
    val Highlight = Color(0xFF66C2FF)
    val TextPrimary = Color(0xFFF4F7FC)
    val TextSecondary = Color(0xFF9AAAC4)
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

/** Zoom, halo coloré et contour clair quand `active`. Sert aux éléments à plusieurs zones de focus (carte + bouton « ••• »). */
@Composable
fun Modifier.focusEffect(
    active: Boolean,
    shape: Shape,
    glow: Color,
    focusScale: Float = 1.07f,
    glowElevation: Dp = 24.dp,
    ring: Boolean = true,
): Modifier {
    val scale by animateFloatAsState(if (active) focusScale else 1f, label = "focusScale")
    val elevation by animateDpAsState(if (active) glowElevation else 0.dp, label = "focusGlow")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(elevation, shape, ambientColor = glow, spotColor = glow)
        .then(if (ring) Modifier.border(3.dp, if (active) Palette.Highlight else Color.Transparent, shape) else Modifier)
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
    focusScale: Float = 1.07f,
    glowElevation: Dp = 24.dp,
    onFocusChange: (Boolean) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focusScale")
    val elevation by animateDpAsState(if (focused) glowElevation else 0.dp, label = "focusGlow")
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
        .border(3.dp, if (focused) Palette.Highlight else Color.Transparent, shape)
        .clip(shape)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
}
