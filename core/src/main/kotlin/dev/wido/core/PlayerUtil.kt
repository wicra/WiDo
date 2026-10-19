package dev.wido.core

import kotlin.math.roundToInt

/** Zoom du texte des pages, en pourcentage. */
object Zoom {
    const val MIN = 70
    const val MAX = 250
    const val STEP = 10
    const val DEFAULT = 100

    fun clamp(percent: Int): Int = percent.coerceIn(MIN, MAX)

    /** Un cran plus grand (`direction` > 0) ou plus petit (< 0), toujours sur un multiple de [STEP]. */
    fun step(current: Int, direction: Int): Int {
        val aligned = (clamp(current) / STEP.toFloat()).roundToInt() * STEP
        return clamp(aligned + direction.coerceIn(-1, 1) * STEP)
    }
}

/** Mise en forme des valeurs affichées sur la barre de lecture. */
object PlayerFormat {
    /** `1:05`, `12:34`, `1:02:03`. Valeur absente ou négative : `0:00`. */
    fun time(seconds: Double): String {
        val total = if (seconds.isNaN() || seconds < 0) 0 else seconds.toLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Niveau 0..1 en pourcentage entier. */
    fun percent(level: Float): Int = (level.coerceIn(0f, 1f) * 100).roundToInt()
}
