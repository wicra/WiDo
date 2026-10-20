package dev.wido.tv

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Looper
import android.webkit.WebView

/**
 * Une WebView préparée à l'avance, pendant que l'accueil est affiché et que rien ne se passe.
 * La toute première création d'une WebView charge le moteur Chromium : plusieurs centaines de
 * millisecondes sur une TV. Préparée à l'avance, l'ouverture d'un site démarre tout de suite.
 *
 * Créée sur un [MutableContextWrapper] du contexte de l'application, elle est rattachée à l'activité
 * du lecteur au moment de servir (listes déroulantes, alertes et plein écran en ont besoin).
 */
object WebViewPool {
    private var spare: WebView? = null
    private var pending = false

    /** Prépare une WebView dès que le fil principal est libre. Sans effet si une est déjà prête. */
    fun prepare(context: Context) {
        if (spare != null || pending) return
        pending = true
        val app = context.applicationContext
        Looper.myQueue().addIdleHandler {
            pending = false
            if (spare == null) spare = runCatching { WebView(MutableContextWrapper(app)) }.getOrNull()
            false
        }
    }

    /** La WebView préparée, rattachée à `activity`, ou une nouvelle s'il n'y en a pas. */
    fun obtain(activity: Activity): WebView {
        val ready = spare
        spare = null
        val wrapper = ready?.context as? MutableContextWrapper
        if (ready != null && wrapper != null) {
            wrapper.baseContext = activity
            return ready
        }
        return WebView(activity)
    }

    /** Libère la WebView préparée (mémoire faible). */
    fun clear() {
        spare?.destroy()
        spare = null
    }
}
