package dev.wido.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Fenêtre publicitaire du mode de redirection **mixte** : la pub (redirection ou pop-up) s'ouvre ici,
 * **en arrière-plan**, derrière la page du site : elle se charge vraiment (le site la voit ouverte),
 * reste quelques secondes puis se ferme seule, sans jamais prendre l'écran, le son ni la télécommande.
 * Elle n'est pas filtrée (la pub doit se charger) mais ne peut ouvrir ni application ni autre fenêtre.
 */
class AdWindow(
    private val activity: Activity,
    private val root: FrameLayout,
    /** Une pub vient de s'ouvrir en arrière-plan (pour un message discret). */
    private val onOpened: () -> Unit = {},
    private val onClosed: () -> Unit,
) {
    private var frame: FrameLayout? = null
    private var web: WebView? = null
    private var remaining = 0
    private var counting = false

    private val tick = object : Runnable {
        override fun run() {
            if (remaining <= 0) {
                close()
                return
            }
            remaining--
            root.postDelayed(this, 1_000)
        }
    }
    private val hardTimeout = Runnable { close() }

    val isOpen: Boolean get() = frame != null

    /** Ouvre une page refusée par le mode mixte. */
    fun open(url: String) {
        create().loadUrl(url)
        onOpened()
    }

    /** Accueille une pop-up (`window.open`) : la page qui l'a ouverte reçoit une vraie fenêtre. */
    fun adopt(resultMsg: Message): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = create()
        resultMsg.sendToTarget()
        onOpened()
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView {
        close(notify = false)
        val wv = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Pas de son : une vidéo de pub ne démarre pas toute seule avec le son.
            settings.mediaPlaybackRequiresUserGesture = true
            settings.setSupportMultipleWindows(false)
            // Jamais le focus : la télécommande reste sur la page du site.
            isFocusable = false
            isFocusableInTouchMode = false
            webViewClient = object : WebViewClient() {
                // Ni `intent:`, ni `market:` : la pub reste une page web.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme
                    return scheme != "https" && scheme != "http"
                }

                override fun onPageCommitVisible(view: WebView, url: String?) {
                    view.evaluateJavascript(MUTE_SCRIPT, null)
                    startCountdown()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (newProgress >= 100) startCountdown()
                }

                override fun onCloseWindow(window: WebView) = close()
            }
        }
        val container = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            addView(wv, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        // Tout au fond : la page du site (opaque) la recouvre entièrement.
        root.addView(container, 0, FrameLayout.LayoutParams(MATCH, MATCH))
        frame = container
        web = wv
        root.postDelayed(hardTimeout, MAX_OPEN_MS)
        return wv
    }

    private fun startCountdown() {
        if (counting || frame == null) return
        counting = true
        remaining = VISIBLE_SECONDS
        tick.run()
    }

    fun close() = close(notify = true)

    private fun close(notify: Boolean) {
        val container = frame ?: return
        root.removeCallbacks(tick)
        root.removeCallbacks(hardTimeout)
        frame = null
        counting = false
        web?.let {
            container.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        web = null
        root.removeView(container)
        if (notify) onClosed()
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        /** Secondes pendant lesquelles la pub reste affichée une fois chargée. */
        const val VISIBLE_SECONDS = 2
        /** Fermeture de toute façon, même si la pub ne finit jamais de charger. */
        const val MAX_OPEN_MS = 20_000L
        /** Coupe le son de tout média de la pub, y compris ceux ajoutés ensuite. */
        const val MUTE_SCRIPT = "(function(){function m(){document.querySelectorAll('video,audio').forEach(function(e){e.muted=true;});}m();setInterval(m,500);})()"
    }
}
