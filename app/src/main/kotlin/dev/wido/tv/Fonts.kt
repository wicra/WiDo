package dev.wido.tv

import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/** Plus Jakarta Sans pour les vues classiques (lecteur) : la graisse est une position de l'axe `wght`. */
fun TextView.useJakarta(weight: Int) {
    typeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)
    setFontVariationSettings("'wght' $weight")
}
