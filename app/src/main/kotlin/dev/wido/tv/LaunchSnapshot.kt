package dev.wido.tv

import android.app.Activity
import android.graphics.Bitmap
import androidx.core.view.drawToBitmap

/**
 * Capture de l'accueil au moment d'ouvrir un site (TV) : le lecteur l'affiche sous le vinyle qui
 * s'agrandit, pour que l'accueil ne disparaisse pas d'un coup. Gardée une seule fois, puis libérée.
 */
object LaunchSnapshot {
    private var bitmap: Bitmap? = null

    fun capture(activity: Activity) {
        bitmap?.recycle()
        bitmap = runCatching { activity.window.decorView.drawToBitmap(Bitmap.Config.RGB_565) }.getOrNull()
    }

    fun take(): Bitmap? = bitmap.also { bitmap = null }
}
