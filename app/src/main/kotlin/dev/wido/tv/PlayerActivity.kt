package dev.wido.tv

import android.app.AlertDialog
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
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

        val loading = TextView(this).apply {
            setText(R.string.player_loading)
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        root.addView(loading, FrameLayout.LayoutParams(MATCH, MATCH))

        val siteId = intent.getStringExtra(EXTRA_SITE).orEmpty()
        val app = application as WiDoApp
        lifecycleScope.launch {
            val config = app.repository.ensureLoaded()
            val site = config.sites.firstOrNull { it.id == siteId && it.enabled }
            if (site == null) {
                Toast.makeText(this@PlayerActivity, getString(R.string.player_unknown_site, siteId), Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val blocker = app.repository.blockerFor(site)
            root.removeView(loading)
            start(site, WiDoWebViewClient(site, blocker, ScriptInjector(this@PlayerActivity, site)) { url ->
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, getString(R.string.toast_navigation_blocked, url.take(60)), Toast.LENGTH_SHORT).show()
                }
            })
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

            // Pas de micro ni de caméra pour les sites.
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }
        wv.isFocusable = true
        root.addView(wv, FrameLayout.LayoutParams(MATCH, MATCH))

        cursor = CursorView(this)
        root.addView(cursor, FrameLayout.LayoutParams(MATCH, MATCH))
        cursor.wake()

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
                sendTouch(target, MotionEvent.ACTION_DOWN)
            }
            KeyEvent.ACTION_UP -> sendTouch(target, MotionEvent.ACTION_UP)
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
        val items = arrayOf(
            getString(R.string.panel_resume),
            getString(R.string.panel_reload),
            getString(if (cursorMode) R.string.panel_cursor_on else R.string.panel_cursor_off),
            getString(R.string.panel_menu),
            getString(R.string.panel_blocked, c?.blockedRequests?.get() ?: 0, c?.blockedNavigations?.get() ?: 0),
        )
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.panel_title)
            .setItems(items) { _, which ->
                when (which) {
                    1 -> webView?.reload()
                    2 -> {
                        cursorMode = !cursorMode
                        cursor.visibility = if (cursorMode) View.VISIBLE else View.GONE
                        if (cursorMode) cursor.wake()
                    }
                    3 -> finish()
                }
            }
            .show()
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
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val LONG_PRESS_MS = 600L
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        fun intent(context: Context, siteId: String): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_SITE, siteId)
    }
}
