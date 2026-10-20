package dev.wido.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Message
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Fenêtre publicitaire du mode de redirection **mixte** : la pub (redirection ou pop-up) s'ouvre ici,
 * par-dessus la page, sans toucher à la page du site. Une fois affichée, elle reste quelques secondes
 * puis se ferme seule ; Retour la ferme tout de suite. Elle n'est pas filtrée (la pub doit s'afficher)
 * mais ne peut ouvrir ni application ni autre fenêtre.
 */
class AdWindow(private val activity: Activity, private val root: FrameLayout, private val onClosed: () -> Unit) {
    private var frame: FrameLayout? = null
    private var web: WebView? = null
    private var label: TextView? = null
    private var remaining = 0
    private var counting = false

    private val tick = object : Runnable {
        override fun run() {
            if (remaining <= 0) {
                close()
                return
            }
            label?.text = activity.getString(R.string.ad_closing, remaining)
            remaining--
            root.postDelayed(this, 1_000)
        }
    }
    private val hardTimeout = Runnable { close() }

    val isOpen: Boolean get() = frame != null

    /** Ouvre une page refusée par le mode mixte. */
    fun open(url: String) = create().loadUrl(url)

    /** Accueille une pop-up (`window.open`) : la page qui l'a ouverte reçoit une vraie fenêtre. */
    fun adopt(resultMsg: Message): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = create()
        resultMsg.sendToTarget()
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView {
        close(notify = false)
        val density = activity.resources.displayMetrics.density
        val wv = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                // Ni `intent:`, ni `market:` : la pub reste une page web.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme
                    return scheme != "https" && scheme != "http"
                }

                override fun onPageCommitVisible(view: WebView, url: String?) = startCountdown()
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (newProgress >= 100) startCountdown()
                }

                override fun onCloseWindow(window: WebView) = close()
            }
        }
        val tag = TextView(activity).apply {
            text = activity.getString(R.string.ad_label)
            setTextColor(Color.WHITE)
            textSize = 15f
            useJakarta(700)
            val padH = (18 * density).toInt()
            val padV = (9 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(0xEB1A1A26.toInt())
                cornerRadius = 999f
                setStroke((1 * density).toInt(), 0x24FFFFFF)
            }
        }
        val container = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            addView(wv, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(
                tag,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                    val m = (24 * density).toInt()
                    setMargins(m, m, m, m)
                },
            )
        }
        root.addView(container, FrameLayout.LayoutParams(MATCH, MATCH))
        container.bringToFront()
        frame = container
        web = wv
        label = tag
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
        label = null
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
        const val VISIBLE_SECONDS = 5
        /** Fermeture de toute façon, même si la pub ne finit jamais de charger. */
        const val MAX_OPEN_MS = 20_000L
    }
}
