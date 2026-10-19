package dev.wido.tv

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dev.wido.core.Hosts
import dev.wido.core.Site
import kotlinx.coroutines.launch

/**
 * Lit un site dans une WebView plein écran, filtrée par les règles de la configuration.
 * Commandes : flèches = curseur, OK = clic, Retour = page précédente puis menu,
 * appui long sur Retour (ou Menu) = menu rapide, lecture/pause/avance/recul = lecteur vidéo.
 */
class PlayerActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var cursor: CursorView
    private var webView: WebView? = null
    private var client: WiDoWebViewClient? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var cursorMode = true
    private var backLongPressed = false
    private var site: Site? = null

    private lateinit var overlay: FrameLayout
    private lateinit var loadingLabel: TextView
    private lateinit var topBar: ProgressBar
    private var overlayAnimator: ObjectAnimator? = null
    private var overlayVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        WindowInsetsControllerCompat(window, root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        overlay = buildLoadingOverlay()
        root.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))
        // Sécurité : si la page ne se dessine jamais, on retire l'écran de chargement après 20 s.
        root.postDelayed({ hideOverlay() }, OVERLAY_TIMEOUT_MS)

        val siteId = intent.getStringExtra(EXTRA_SITE).orEmpty()
        val app = application as WiDoApp
        lifecycleScope.launch {
            val site = app.repository.findSite(siteId)
            this@PlayerActivity.site = site
            if (site == null) {
                Toast.makeText(this@PlayerActivity, getString(R.string.player_unknown_site, siteId), Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val blocker = app.repository.blockerFor(site)
            loadingLabel.text = getString(R.string.loading_site, site.name)
            start(
                site,
                WiDoWebViewClient(
                    site,
                    blocker,
                    ScriptInjector(this@PlayerActivity, site),
                    onNavigationBlocked = { url ->
                        runOnUiThread {
                            Toast.makeText(this@PlayerActivity, getString(R.string.toast_navigation_blocked, url.take(60)), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPageVisible = { runOnUiThread { hideOverlay() } },
                ),
            )
            // Rafraîchit la configuration pour la prochaine ouverture, sans bloquer la lecture.
            launch { app.repository.refresh() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun start(site: Site, wc: WiDoWebViewClient) {
        client = wc
        val wv = WebView(this)
        webView = wv
        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false) // window.open charge dans la même vue, jugé par la politique de navigation
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            when (site.userAgent) {
                "default", "" -> Unit
                "desktop" -> userAgentString = DESKTOP_UA
                else -> userAgentString = site.userAgent
            }
        }
        wv.webViewClient = wc
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                root.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                wv.visibility = View.GONE
                cursor.bringToFront()
            }

            override fun onHideCustomView() = exitCustomView()

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (overlayVisible) return
                topBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                topBar.progress = newProgress
            }

            // Pas de micro ni de caméra pour les sites.
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }
        wv.isFocusable = true
        root.addView(wv, FrameLayout.LayoutParams(MATCH, MATCH))

        cursor = CursorView(this)
        root.addView(cursor, FrameLayout.LayoutParams(MATCH, MATCH))
        cursor.wake()

        topBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
            visibility = View.GONE
        }
        root.addView(topBar, FrameLayout.LayoutParams(MATCH, (4 * resources.displayMetrics.density).toInt(), Gravity.TOP))
        overlay.bringToFront()

        wv.requestFocus()
        wv.loadUrl(site.url)
    }

    private fun exitCustomView() {
        val view = customView ?: return
        root.removeView(view)
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        webView?.visibility = View.VISIBLE
        webView?.requestFocus()
    }

    // --- Télécommande -----------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (webView == null) return super.dispatchKeyEvent(event)
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> return handleBack(event)
            KeyEvent.KEYCODE_MENU -> {
                if (event.action == KeyEvent.ACTION_UP) showPanel()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
            -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) mediaKey(event.keyCode)
                return true
            }
        }
        if (cursorMode) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> return moveCursor(event, 0, -1)
                KeyEvent.KEYCODE_DPAD_DOWN -> return moveCursor(event, 0, 1)
                KeyEvent.KEYCODE_DPAD_LEFT -> return moveCursor(event, -1, 0)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return moveCursor(event, 1, 0)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> return tap(event)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleBack(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    backLongPressed = false
                } else if (!backLongPressed && event.eventTime - event.downTime >= LONG_PRESS_MS) {
                    backLongPressed = true
                    showPanel()
                }
            }
            KeyEvent.ACTION_UP -> {
                if (backLongPressed) backLongPressed = false else goBackOrExit()
            }
        }
        return true
    }

    private fun goBackOrExit() {
        val wv = webView
        when {
            customView != null -> exitCustomView()
            wv != null && wv.canGoBack() -> wv.goBack()
            else -> finish()
        }
    }

    private fun moveCursor(event: KeyEvent, dirX: Int, dirY: Int): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val density = resources.displayMetrics.density
        // Vitesse progressive : précis au premier appui, rapide en maintenant.
        val step = (8f + minOf(event.repeatCount, 25) * 2.5f) * density
        val (overX, overY) = cursor.moveBy(dirX * step, dirY * step)
        if (customView == null && (overX != 0f || overY != 0f)) {
            webView?.scrollBy((overX * 2).toInt(), (overY * 2).toInt()) // le curseur bute sur un bord : la page défile
        }
        return true
    }

    private var tapDownTime = 0L

    private fun tap(event: KeyEvent): Boolean {
        val target: View = customView ?: webView ?: return true
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                tapDownTime = SystemClock.uptimeMillis()
                cursor.pressedState = true
                sendTouch(target, MotionEvent.ACTION_DOWN)
            }
            KeyEvent.ACTION_UP -> {
                cursor.pressedState = false
                sendTouch(target, MotionEvent.ACTION_UP)
            }
        }
        cursor.wake()
        return true
    }

    private fun sendTouch(target: View, action: Int) {
        val ev = MotionEvent.obtain(tapDownTime, SystemClock.uptimeMillis(), action, cursor.px, cursor.py, 0)
        target.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun mediaKey(keyCode: Int) {
        val js = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> "v.play()"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "v.pause()"
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "v.currentTime+=10"
            KeyEvent.KEYCODE_MEDIA_REWIND -> "v.currentTime-=10"
            else -> "(v.paused?v.play():v.pause())"
        }
        // La vidéo la plus grande de la page ; ne traverse pas les iframes d'un autre domaine.
        webView?.evaluateJavascript(
            "(function(){var v=Array.from(document.querySelectorAll('video'))" +
                ".sort(function(a,b){return b.videoWidth*b.videoHeight-a.videoWidth*a.videoHeight})[0];" +
                "if(v){$js;}})();",
            null,
        )
    }

    // --- Menu rapide ------------------------------------------------------------------------

    private fun showPanel() {
        val c = client
        val current = site
        val entries = mutableListOf<Pair<String, () -> Unit>>()
        entries += getString(R.string.panel_resume) to {}
        entries += getString(R.string.panel_reload) to { webView?.reload() }
        entries += getString(if (cursorMode) R.string.panel_cursor_on else R.string.panel_cursor_off) to { toggleCursor() }

        // La dernière redirection refusée : l'autoriser pour ce site, ou l'ouvrir une seule fois.
        val blockedUrl = c?.lastBlockedUrl
        val blockedHost = blockedUrl?.let { Hosts.of(it) }
        if (blockedUrl != null && blockedHost != null && current != null) {
            entries += getString(R.string.panel_allow, blockedHost) to { allowDomain(current, blockedHost, blockedUrl) }
            entries += getString(R.string.panel_open_once, blockedHost) to {
                c.allowOnce(blockedUrl)
                webView?.loadUrl(blockedUrl)
            }
        }

        entries += getString(R.string.panel_save_page) to { saveCurrentPage() }
        entries += getString(R.string.panel_menu) to { finish() }
        entries += getString(R.string.panel_blocked, c?.blockedRequests?.get() ?: 0, c?.blockedNavigations?.get() ?: 0) to {}

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.panel_title)
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .show()
    }

    private fun toggleCursor() {
        cursorMode = !cursorMode
        cursor.visibility = if (cursorMode) View.VISIBLE else View.GONE
        if (cursorMode) cursor.wake()
    }

    /** Autorise `host` pour ce site (gardé localement), recharge les règles puis ouvre la page refusée. */
    private fun allowDomain(current: Site, host: String, thenOpen: String) {
        val repository = (application as WiDoApp).repository
        val error = repository.allowDomain(current.id, host)
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, getString(R.string.toast_allowed, host), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val updated = repository.findSite(current.id) ?: return@launch
            site = updated
            client?.update(updated, repository.blockerFor(updated))
            webView?.loadUrl(thenOpen)
        }
    }

    /** Enregistre la page affichée comme site perso (son adresse racine). */
    private fun saveCurrentPage() {
        val url = webView?.url
        val host = url?.let { Hosts.of(it) }
        val scheme = url?.let { Hosts.schemeOf(it) }
        if (host == null || (scheme != "https" && scheme != "http")) return
        val name = host.removePrefix("www.")
        val error = (application as WiDoApp).repository.addLocalSite(name, "$scheme://$host")
        Toast.makeText(this, error ?: getString(R.string.toast_saved, name), Toast.LENGTH_LONG).show()
    }

    // --- Écran de chargement ----------------------------------------------------------------

    private fun buildLoadingOverlay(): FrameLayout {
        val density = resources.displayMetrics.density
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.wido_logo)
            layoutParams = LinearLayout.LayoutParams((150 * density).toInt(), (150 * density).toInt())
        }
        loadingLabel = TextView(this).apply {
            setText(R.string.player_loading)
            setTextColor(0xFFF4F7FC.toInt())
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, (22 * density).toInt(), 0, 0)
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(ACCENT)
            layoutParams = LinearLayout.LayoutParams((280 * density).toInt(), (4 * density).toInt()).apply {
                topMargin = (18 * density).toInt()
            }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(logo)
            addView(loadingLabel)
            addView(bar)
        }
        // Le logo « respire » pendant l'attente.
        overlayAnimator = ObjectAnimator.ofPropertyValuesHolder(
            logo,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.6f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.95f, 1.04f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.95f, 1.04f),
        ).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        return FrameLayout(this).apply {
            setBackgroundColor(0xFF070B14.toInt())
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
    }

    private fun hideOverlay() {
        if (!overlayVisible) return
        overlayVisible = false
        overlayAnimator?.cancel()
        overlay.animate().alpha(0f).setDuration(250).withEndAction {
            if (overlay.parent != null) root.removeView(overlay)
        }.start()
    }

    // --- Cycle de vie -----------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        overlayAnimator?.cancel()
        customCallback?.onCustomViewHidden()
        webView?.let {
            root.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SITE = "site"
        private const val ACCENT = 0xFF1E90FF.toInt()
        private const val OVERLAY_TIMEOUT_MS = 20_000L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val LONG_PRESS_MS = 600L
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        fun intent(context: Context, siteId: String): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_SITE, siteId)
    }
}
