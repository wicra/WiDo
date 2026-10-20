package dev.wido.tv

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy

/**
 * Capture de l'accueil au moment d'ouvrir un site (TV) : le lecteur l'affiche sous le vinyle qui
 * s'agrandit, pour que l'accueil ne disparaisse pas d'un coup. Faite par le GPU (PixelCopy), sans
 * redessiner l'écran sur le fil principal : pas d'à-coup au clic, même sur une petite clé TV. Gardée
 * une seule fois, puis libérée.
 */
object LaunchSnapshot {
    private var bitmap: Bitmap? = null

    /** Capture l'écran puis appelle `then` (au plus tard après 150 ms, avec ou sans capture). */
    fun capture(activity: Activity, then: () -> Unit) {
        bitmap?.recycle()
        bitmap = null
        val view = activity.window.decorView
        val shot = if (view.width > 0 && view.height > 0) {
            runCatching { Bitmap.createBitmap(view.width, view.height, Bitmap.Config.RGB_565) }.getOrNull()
        } else {
            null
        }
        if (shot == null) {
            then()
            return
        }
        val main = Handler(Looper.getMainLooper())
        var done = false
        fun finish(ok: Boolean) {
            if (done) return
            done = true
            // En retard (délai dépassé) : l'image n'est pas gardée ; le ramasse-miettes la libérera.
            if (ok) bitmap = shot
            then()
        }
        main.postDelayed({ finish(false) }, 150)
        runCatching {
            PixelCopy.request(activity.window, shot, { result -> finish(result == PixelCopy.SUCCESS) }, main)
        }.onFailure { finish(false) }
    }

    fun take(): Bitmap? = bitmap.also { bitmap = null }
}
