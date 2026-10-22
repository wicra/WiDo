package dev.wido.tv

import android.view.KeyEvent

/**
 * Rebonds de télécommande : certaines (infrarouge et Bluetooth à la fois, contacts usés) envoient deux fois
 * la même touche à quelques dizaines de millisecondes d'écart. Le journal l'a montré : deux Retour à 48 ms,
 * qui ferment l'écran de pause puis quittent la page. Aucun doigt n'appuie deux fois en moins de 100 ms :
 * le second appui est ignoré, avec son relâchement.
 */
class KeyDebounce(private val windowMs: Long = WINDOW_MS) {
    private var lastCode = 0
    private var lastDown = 0L
    private var swallowUp = 0
    /** Le relâchement du dernier appui retenu n'est pas encore arrivé. */
    private var upPending = false

    /** Vrai si la touche est un rebond (à consommer sans rien faire). */
    fun isBounce(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE) return false
        if (event.action == KeyEvent.ACTION_UP) {
            if (code == lastCode) upPending = false
            if (swallowUp == code) {
                swallowUp = 0
                return true
            }
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        val bounce = code == lastCode && event.eventTime - lastDown in 0 until windowMs
        if (bounce) {
            // Un seul relâchement pour les deux appuis : il termine le premier, on ne l'avale pas.
            swallowUp = if (upPending) 0 else code
            CrashLog.trace("touche ${KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")} en double ignorée (rebond)")
            return true
        }
        lastCode = code
        lastDown = event.eventTime
        upPending = true
        return false
    }

    private companion object {
        const val WINDOW_MS = 100L
    }
}
