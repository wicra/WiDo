package dev.wido.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.wido.tv.R

/**
 * Style « Verre » : un fond presque noir éclairé par des halos de la couleur du site, des panneaux de
 * verre translucides, et un focus franc (pastille blanche pleine, ou carte en couleur soulevée).
 */
object Palette {
    val Background = Color(0xFF0B0B12)
    /** Texte posé sur un élément blanc (focus). */
    val Ink = Color(0xFF0B0B12)
    val InkSecondary = Color(0xFF5A5A6A)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.65f)
    val TextMuted = Color.White.copy(alpha = 0.45f)

    val Glass = Color.White.copy(alpha = 0.12f)
    val GlassStrong = Color.White.copy(alpha = 0.22f)
    val GlassFaint = Color.White.copy(alpha = 0.08f)
    val GlassBorder = Color.White.copy(alpha = 0.14f)
    /** Panneau de verre posé sur un fond à halos. */
    val Panel = Color(0x8C14141E)
    /** Panneau posé sur une page web ou une fenêtre assombrie : plus opaque, faute de flou d'arrière-plan. */
    val PanelSolid = Color(0xEB1A1A26)

    /** Lumière par défaut (site sans couleur, réglages). */
    val Accent = Color(0xFF1E5BFF)
    val Violet = Color(0xFF6E1BD6)
    val Purple = Color(0xFF7A2BE0)
    val Navy = Color(0xFF0A1A4A)
    val Amber = Color(0xFFF2A93A)

    val Ok = Color(0xFF5BE39A)
    val Warning = Color(0xFFFFB86B)
}

val CardShape = RoundedCornerShape(14.dp)
val RowShape = RoundedCornerShape(16.dp)
val PanelShape = RoundedCornerShape(28.dp)
val PillShape = RoundedCornerShape(50)

/** Plus Jakarta Sans, police variable : chaque graisse est une position de son axe `wght`. */
val Jakarta = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
        .map { Font(R.font.plus_jakarta_sans, it) },
)

/** Vrai sur téléphone ou tablette : mises en page tactiles, adaptées aux écrans étroits. */
val LocalPhone = staticCompositionLocalOf { false }

@Composable
fun WiDoTheme(content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as? dev.wido.tv.WiDoApp
    val phone = app?.isTv == false
    // TV : taille de l'interface réglable (projecteur regardé de loin). Le téléphone suit le système.
    val scale = if (phone) 1f else app?.settings?.uiScale ?: 1f
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalPhone provides phone,
        LocalDensity provides Density(density.density * scale, density.fontScale),
    ) { WiDoMaterial(content) }
}

@Composable
private fun WiDoMaterial(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            background = Palette.Background,
            surface = Palette.Background,
            onBackground = Palette.TextPrimary,
            onSurface = Palette.TextPrimary,
        ),
    ) {
        ProvideTextStyle(LocalTextStyle.current.merge(TextStyle(fontFamily = Jakarta, color = Palette.TextPrimary)), content)
    }
}

/** Halo elliptique, en fractions de l'écran : centre (`x`, `y`) et rayons (`rx`, `ry`). S'efface à 70 % du rayon. */
data class Glow(val color: Color, val x: Float, val y: Float, val rx: Float, val ry: Float)

private fun DrawScope.drawGlow(glow: Glow) {
    val center = Offset(size.width * glow.x, size.height * glow.y)
    val rx = size.width * glow.rx
    val ry = size.height * glow.ry
    scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
        drawCircle(Brush.radialGradient(0f to glow.color, 0.7f to Color.Transparent, center = center, radius = rx), rx, center)
    }
}

/** Fond du style : noir bleuté, halos de couleur, puis un voile optionnel qui assombrit le bas. */
fun Modifier.halo(glows: List<Glow>, veil: Brush? = null): Modifier = this
    .background(Palette.Background)
    .drawBehind {
        glows.forEach { drawGlow(it) }
        veil?.let { drawRect(it) }
    }

/** Dégradé d'une carte focalisée : la couleur du site, éclaircie en haut à gauche, assombrie en bas à droite. */
fun siteGradient(color: Color): Brush = Brush.linearGradient(
    listOf(lerp(color, Color.White, 0.15f), lerp(color, Color.Black, 0.35f)),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** Reflet de verre posé sur une carte en couleur. */
val GlassSheen: Brush = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.28f),
    0.45f to Color.Transparent,
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** Zoom et ombre portée quand `active` : l'élément se soulève. Sert aux éléments à plusieurs zones de focus. */
@Composable
fun Modifier.liftEffect(
    active: Boolean,
    shape: Shape,
    focusScale: Float = 1.06f,
    lift: Dp = 18.dp,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier {
    val scale by animateFloatAsState(if (active) focusScale else 1f, label = "focusScale")
    val elevation by animateDpAsState(if (active) lift else 0.dp, label = "focusLift")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = origin
        }
        .shadow(elevation, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
}

/**
 * Rend un élément activable à la télécommande : il se soulève quand il a le focus, clic au bouton OK
 * et clic long en option. `onFocusChanged` doit précéder `combinedClickable` pour observer son focus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvFocusable(
    shape: Shape = CardShape,
    focusScale: Float = 1.06f,
    lift: Dp = 18.dp,
    onFocusChange: (Boolean) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged {
            focused = it.isFocused
            onFocusChange(it.isFocused)
        }
        .liftEffect(focused, shape, focusScale, lift)
        .clip(shape)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
}

/**
 * Surface de verre activable : translucide au repos, blanche pleine quand elle a le focus.
 * Le contenu reçoit l'état du focus pour passer son texte en encre sur le blanc.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = PillShape,
    idle: Color = Palette.Glass,
    focusScale: Float = 1.06f,
    onLongClick: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(if (focused) Color.White else idle, label = "focusFill")
    Box(
        modifier
            .tvFocusable(
                shape,
                focusScale,
                onFocusChange = {
                    focused = it
                    onFocusChange(it)
                },
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .background(background),
        contentAlignment = contentAlignment,
    ) {
        content(focused)
    }
}

/** Couleur du texte sur une [FocusSurface]. */
fun onSurface(focused: Boolean, idle: Color = Palette.TextPrimary): Color = if (focused) Palette.Ink else idle

/** Une valeur chargée en tâche de fond (icône, image), rechargée quand une des clés change ; null en attendant. */
@Composable
fun <T> rememberLoaded(vararg keys: Any?, load: suspend () -> T?): T? {
    var value by remember(*keys) { mutableStateOf<T?>(null) }
    androidx.compose.runtime.LaunchedEffect(*keys) { value = load() }
    return value
}
