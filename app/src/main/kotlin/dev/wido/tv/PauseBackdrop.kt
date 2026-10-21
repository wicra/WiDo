package dev.wido.tv

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.compose.ui.graphics.toArgb
import dev.wido.tv.ui.Palette

/**
 * Le fond de l'écran de pause : celui de WiDo (son fond et ses deux lumières, aux couleurs de l'ambiance
 * choisie), plus opaque vers le bas où se lisent le titre et le temps restant ; l'image de la vidéo reste
 * devinée en haut.
 */
object PauseBackdrop {
    fun drawable(resources: Resources): Drawable {
        val background = Palette.Background.toArgb() and 0x00FFFFFF
        fun glow(color: Int, x: Float, y: Float, radius: Float) = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf((color and 0x00FFFFFF) or 0x80000000.toInt(), color and 0x00FFFFFF)
            setGradientCenter(x, y)
            gradientRadius = radius * resources.displayMetrics.widthPixels
        }
        return LayerDrawable(
            arrayOf<Drawable>(
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x66000000 or background, 0xD9000000.toInt() or background, 0xF5000000.toInt() or background),
                ),
                glow(Palette.Accent.toArgb(), 0.15f, 0.95f, 0.45f),
                glow(Palette.Violet.toArgb(), 0.9f, 0.9f, 0.42f),
            ),
        )
    }
}
