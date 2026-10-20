package dev.wido.tv

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.wido.core.Hosts
import dev.wido.core.Navigation
import dev.wido.core.ResumeUrl
import dev.wido.core.Site
import dev.wido.core.Zoom
import dev.wido.tv.ui.BlockedRedirect
import dev.wido.tv.ui.PhoneCornerCard
import dev.wido.tv.ui.PhoneOverlay
import dev.wido.tv.ui.QuickAction
import dev.wido.tv.ui.QuickMenu
import dev.wido.tv.ui.WiDoTheme
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Lit un site dans une WebView plein écran, filtrée par les règles de la configuration.
 *
 * Télécommande (voir docs/REMOTE-CONTROL.md) : flèches = curseur aimanté (saute au lien suivant ;
 * maintenues, déplacement libre), OK = clic, Retour = page précédente puis menu, appui long sur Retour
 * (ou Menu) = menu rapide, touches média = lecteur vidéo, touches de couleur = raccourcis configurables,
 * volume = indicateur WiDo, CH+/CH- = défilement de la page.
 */
class PlayerActivity : ComponentActivity() {
    private lateinit var settings: Settings
    private lateinit var audio: AudioManager
    private lateinit var root: FrameLayout
    private lateinit var cursor: CursorView
    private lateinit var osd: OsdView
    private var webView: WebView? = null
    private var client: WiDoWebViewClient? = null
    private var injector: ScriptInjector? = null
    private var bridge: FrameBridge? = null
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

    private var helpView: View? = null
    private var panelView: ComposeView? = null
    /** Fenêtre publicitaire du mode mixte (null pour les autres sites). */
    private var adWindow: AdWindow? = null
    /** Dernier état connu du lecteur vidéo de la page, affiché dans le menu rapide. */
    private val mediaState = mutableStateOf<MediaState?>(null)
    private var passivePoll = false
    private val poll = Runnable {
        if (osd.isShowing) {
            passivePoll = true
            media("state")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = (application as WiDoApp).settings
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

        // `last` : le dernier site ouvert (pratique depuis Home Assistant).
        val siteId = intent.getStringExtra(EXTRA_SITE).orEmpty().let { if (it == LAST_SITE) settings.lastSiteId.orEmpty() else it }
        val app = application as WiDoApp
        lifecycleScope.launch {
            val site = app.repository.findSite(siteId)
            this@PlayerActivity.site = site
            if (site == null) {
                Toast.makeText(this@PlayerActivity, getString(R.string.player_unknown_site, siteId), Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            settings.lastSiteId = site.id
            loadingLabel.text = getString(R.string.loading_site, site.name)
            val blocker = app.repository.blockerFor(site)
            val scripts = ScriptInjector(this@PlayerActivity, site)
            scripts.early = scripts.earlyScript(app.repository.cosmeticFor(site), settings.youtubeAdblock, settings.sponsorBlock)
            injector = scripts
            start(
                site,
                WiDoWebViewClient(
                    site,
                    blocker,
                    app.repository.urlMatcherFor(site),
                    scripts,
                    onNavigationBlocked = { url ->
                        runOnUiThread {
                            Toast.makeText(this@PlayerActivity, getString(R.string.toast_navigation_blocked, url.take(60)), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPageVisible = {
                        runOnUiThread {
                            hideOverlay()
                            captureMeta()
                            startResumeSeek()
                        }
                    },
                    onPageStart = {
                        bridge?.reset()
                        mediaState.value = null
                        streamHinted = false
                    },
                    onAdWindow = { url -> runOnUiThread { adWindow?.open(url) } },
                    onMediaStream = { runOnUiThread { onStreamFound() } },
                ),
            )
            // Rafraîchit la configuration pour la prochaine ouverture, sans bloquer la lecture.
            launch { app.repository.refresh() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun start(site: Site, wc: WiDoWebViewClient) {
        client = wc
        val wv = WebViewPool.obtain(this)
        webView = wv
        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Mode mixte : les pop-ups s'ouvrent dans la fenêtre publicitaire, fermée une fois affichée.
            // Sinon, window.open charge dans la même vue et la politique de navigation tranche.
            val mixed = site.navigation == Navigation.MIXED
            javaScriptCanOpenWindowsAutomatically = mixed
            setSupportMultipleWindows(mixed)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = settings.zoomFor(site.id)
            when (site.userAgent) {
                "default", "" -> Unit
                "desktop" -> userAgentString = DESKTOP_UA
                else -> userAgentString = site.userAgent
            }
        }

        // Passerelle vers toutes les frames (lecteurs dans des iframes) si la WebView l'accepte.
        val documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        val bridged = documentStart && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        if (bridged) {
            val frameBridge = FrameBridge(
                onMedia = { state -> runOnUiThread { onMediaState(state) } },
                onSkip = { runOnUiThread { osd.showText(getString(R.string.osd_sponsor_skipped)) } },
                onField = { focused -> runOnUiThread { onPageField(focused) } },
            )
            bridge = frameBridge
            WebViewCompat.addWebMessageListener(wv, BRIDGE_NAME, setOf("*"), frameBridge)
            WebViewCompat.addDocumentStartJavaScript(wv, injector?.internalScript().orEmpty(), setOf("*"))
        }
        injector?.includeInternal = !bridged
        // Protections (masquage, YouTube) avant le moindre script de la page, quand la WebView le permet.
        val early = injector?.early.orEmpty()
        if (documentStart && early.isNotBlank()) {
            WebViewCompat.addDocumentStartJavaScript(wv, early, setOf("*"))
            injector?.includeEarly = false
        }

        if (site.navigation == Navigation.MIXED) adWindow = AdWindow(this, root) { webView?.requestFocus() }
        wv.webViewClient = wc
        wv.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean =
                adWindow?.adopt(resultMsg) ?: false

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
                osd.bringToFront()
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

        cursor = CursorView(this).apply {
            sizeFactor = settings.cursorSize
            hideDelayMs = settings.cursorHideSeconds * 1000L
        }
        root.addView(cursor, FrameLayout.LayoutParams(MATCH, MATCH))
        cursor.wake()

        osd = OsdView(this).apply { hideSeconds = settings.osdSeconds }
        root.addView(osd, osd.layoutParamsForRoot())

        topBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
            visibility = View.GONE
        }
        root.addView(topBar, FrameLayout.LayoutParams(MATCH, (4 * resources.displayMetrics.density).toInt(), Gravity.TOP))
        overlay.bringToFront()

        wv.requestFocus()
        pendingSeek = intent.getDoubleExtra(EXTRA_POSITION, 0.0)
        // Une page épinglée, sinon la dernière page visitée du site (réglage « Reprendre »), sinon son accueil.
        val pinned = intent.getStringExtra(EXTRA_URL)?.let { ResumeUrl.toRemember(site, it) }
        wv.loadUrl(pinned ?: if (settings.resumeLastPage) ResumeUrl.startUrl(site, settings.lastUrlFor(site.id)) else site.url)
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

        // L'aide à l'écran se ferme à n'importe quelle touche.
        if (helpView != null) {
            if (event.action == KeyEvent.ACTION_UP) dismissHelp()
            return true
        }

        val code = event.keyCode

        // Fenêtre publicitaire (mode mixte) : rien ne la pilote, Retour la ferme tout de suite.
        adWindow?.takeIf { it.isOpen }?.let { ad ->
            if (code == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) ad.close()
            return true
        }

        // « Masquer un élément » : les flèches déplacent le curseur librement, OK masque, Retour annule.
        if (picking && panelView == null) {
            when (code) {
                KeyEvent.KEYCODE_BACK -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        picking = false
                        osd.showText(getString(R.string.osd_pick_cancel))
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) hideAtCursor()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val dx = when (code) { KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1; else -> 0 }
                        val dy = when (code) { KeyEvent.KEYCODE_DPAD_UP -> -1; KeyEvent.KEYCODE_DPAD_DOWN -> 1; else -> 0 }
                        freeMove(event.repeatCount, dx, dy)
                    }
                    return true
                }
            }
        }

        // Menu rapide ouvert : les flèches et OK le pilotent ; Retour ou Menu le referment.
        if (panelView != null) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
                if (event.action == KeyEvent.ACTION_UP) {
                    // Le relâchement de l'appui long qui vient d'ouvrir le menu ne doit pas le refermer.
                    if (code == KeyEvent.KEYCODE_BACK && backLongPressed) backLongPressed = false else hidePanel()
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        when (code) {
            KeyEvent.KEYCODE_BACK -> return handleBack(event)
            KeyEvent.KEYCODE_MENU -> {
                if (event.action == KeyEvent.ACTION_UP) showPanel()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                if (!settings.customVolume || audio.isVolumeFixed) return super.dispatchKeyEvent(event)
                if (event.action == KeyEvent.ACTION_DOWN) {
                    adjustVolume(
                        when (code) {
                            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
                            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
                            else -> AudioManager.ADJUST_TOGGLE_MUTE
                        },
                    )
                }
                return true
            }
            KeyEvent.KEYCODE_PROG_RED -> return colorKey(event, settings.keyRed)
            KeyEvent.KEYCODE_PROG_GREEN -> return colorKey(event, settings.keyGreen)
            KeyEvent.KEYCODE_PROG_YELLOW -> return colorKey(event, settings.keyYellow)
            KeyEvent.KEYCODE_PROG_BLUE -> return colorKey(event, settings.keyBlue)
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) webView?.pageUp(false)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) webView?.pageDown(false)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
            -> {
                if (event.action == KeyEvent.ACTION_DOWN) mediaKey(code, event.repeatCount)
                return true
            }
        }

        // Plein écran vidéo : les flèches pilotent le lecteur.
        if (customView != null && settings.fullscreenPlayerKeys) {
            if (fullscreenKey(event)) return true
        }

        if (cursorMode) {
            when (code) {
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

    private fun colorKey(event: KeyEvent, action: KeyAction): Boolean {
        if (event.action == KeyEvent.ACTION_UP) runKeyAction(action)
        return true
    }

    private fun runKeyAction(action: KeyAction) {
        when (action) {
            KeyAction.NONE -> Unit
            KeyAction.MENU -> finish()
            KeyAction.RELOAD -> webView?.reload()
            KeyAction.TOGGLE_CURSOR -> toggleCursor()
            KeyAction.ZOOM_IN -> changeZoom(1)
            KeyAction.ZOOM_OUT -> changeZoom(-1)
            KeyAction.QUICK_MENU -> showPanel()
            KeyAction.SAVE_PAGE -> saveCurrentPage()
            KeyAction.SCROLL_TOP -> webView?.scrollTo(0, 0)
            KeyAction.NATIVE_PLAYER -> openNative()
        }
    }

    // --- Curseur ----------------------------------------------------------------------------

    private fun moveCursor(event: KeyEvent, dirX: Int, dirY: Int): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true
        // Un appui bref saute au prochain lien ; maintenu, le curseur se déplace librement.
        if (settings.snapNavigation && event.repeatCount == 0 && customView == null) {
            snap(dirX, dirY)
        } else {
            freeMove(event.repeatCount, dirX, dirY)
        }
        return true
    }

    private fun freeMove(repeat: Int, dirX: Int, dirY: Int) {
        val density = resources.displayMetrics.density
        // Vitesse progressive : précis au premier appui, rapide en maintenant.
        val step = (8f + minOf(repeat, 25) * 2.5f) * density * settings.cursorSpeed
        val (overX, overY) = cursor.moveBy(dirX * step, dirY * step)
        if (customView == null) {
            if (repeat == 0) webView?.evaluateJavascript("window.__widoUnmark&&window.__widoUnmark()", null)
            if (overX != 0f || overY != 0f) {
                webView?.scrollBy((overX * 2).toInt(), (overY * 2).toInt()) // le curseur bute sur un bord : la page défile
            }
        }
    }

    /** Demande à la page le lien ou bouton le plus naturel dans cette direction ; sinon déplacement libre. */
    private fun snap(dirX: Int, dirY: Int) {
        val wv = webView ?: return
        if (wv.width == 0 || wv.height == 0) return
        val dir = when {
            dirX < 0 -> "left"
            dirX > 0 -> "right"
            dirY < 0 -> "up"
            else -> "down"
        }
        val fx = cursor.px / wv.width
        val fy = cursor.py / wv.height
        wv.evaluateJavascript("window.__widoSnap?window.__widoSnap('$dir',$fx,$fy):null") { raw ->
            val target = parseSnapTarget(raw)
            if (target != null) {
                cursor.moveTo(target.first * wv.width, target.second * wv.height)
            } else {
                freeMove(0, dirX, dirY)
            }
        }
    }

    private fun parseSnapTarget(raw: String?): Pair<Float, Float>? {
        val text = unquote(raw) ?: return null
        return runCatching {
            val o = JSONObject(text)
            o.getDouble("x").toFloat() to o.getDouble("y").toFloat()
        }.getOrNull()
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

    private fun toggleCursor() {
        cursorMode = !cursorMode
        cursor.visibility = if (cursorMode) View.VISIBLE else View.GONE
        if (cursorMode) cursor.wake()
    }

    // --- Lecteur vidéo, volume, zoom --------------------------------------------------------

    private fun mediaKey(keyCode: Int, repeat: Int) {
        val seek = settings.seekSeconds.toDouble()
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> media("play")
            KeyEvent.KEYCODE_MEDIA_PAUSE -> media("pause")
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> media("seek", seek * (1 + minOf(repeat, 4)))
            KeyEvent.KEYCODE_MEDIA_REWIND -> media("seek", -seek * (1 + minOf(repeat, 4)))
            else -> if (repeat == 0) media("toggle")
        }
    }

    /** Flèches et OK en plein écran vidéo. Renvoie vrai si la touche est consommée. */
    private fun fullscreenKey(event: KeyEvent): Boolean {
        val seek = settings.seekSeconds.toDouble()
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (event.action == KeyEvent.ACTION_DOWN) media("seek", -seek)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (event.action == KeyEvent.ACTION_DOWN) media("seek", seek)
            KeyEvent.KEYCODE_DPAD_UP -> if (event.action == KeyEvent.ACTION_DOWN) videoVolume(1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (event.action == KeyEvent.ACTION_DOWN) videoVolume(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) media("toggle")
            else -> return false
        }
        return true
    }

    private fun videoVolume(direction: Int) {
        if (settings.customVolume && !audio.isVolumeFixed) {
            adjustVolume(if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER)
        } else {
            media("vol", 0.1 * direction)
        }
    }

    /** Commande envoyée au lecteur vidéo de la page, dans n'importe quelle frame. */
    private fun media(cmd: String, by: Double? = null) {
        val json = JSONObject().put("cmd", cmd).apply { if (by != null) put("by", by) }.toString()
        val b = bridge
        if (b != null) {
            b.send(json)
        } else {
            webView?.evaluateJavascript("window.__widoCommand&&window.__widoCommand(${JSONObject.quote(json)})") { raw ->
                parseMediaState(unquote(raw))?.let { onMediaState(it) }
            }
        }
    }

    private fun onMediaState(state: MediaState) {
        val passive = passivePoll
        passivePoll = false
        mediaState.value = state
        applyPendingSeek(state)
        osd.showMedia(state, passive)
        root.removeCallbacks(poll)
        if (osd.isShowing) root.postDelayed(poll, POLL_MS) // la barre avance pendant qu'elle est visible
    }

    /** Règle le volume du système sans le panneau d'Android, et affiche l'indicateur de WiDo. */
    private fun adjustVolume(direction: Int) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val level = if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) 0 else audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        osd.showVolume(level, max)
    }

    private fun changeZoom(direction: Int) {
        val current = site ?: return
        val next = Zoom.step(settings.zoomFor(current.id), direction)
        settings.setZoomFor(current.id, next)
        webView?.settings?.textZoom = next
        osd.showText(getString(R.string.osd_zoom, next))
    }

    /** `evaluateJavascript` renvoie un littéral JSON : on en tire la chaîne, ou null. */
    private fun unquote(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
    }

    // --- Menu rapide ------------------------------------------------------------------------

    private fun showPanel() {
        if (panelView != null) return
        val c = client
        val current = site
        fun action(label: String, run: () -> Unit) = QuickAction(label) {
            hidePanel()
            run()
        }

        val native = c?.stream?.let { listOf(action(getString(R.string.panel_native)) { openNative() }) }.orEmpty()
        val actions = native + listOf(
            action("▶  " + getString(R.string.panel_resume)) {},
            action(getString(R.string.panel_reload)) { webView?.reload() },
            action(getString(R.string.panel_home)) { current?.let { webView?.loadUrl(it.url) } },
            action(getString(if (cursorMode) R.string.panel_cursor_on else R.string.panel_cursor_off)) { toggleCursor() },
            action(getString(R.string.panel_zoom_out_short)) { changeZoom(-1) },
            action(getString(R.string.panel_zoom_in_short)) { changeZoom(1) },
            action(getString(R.string.panel_phone)) { showPhone() },
            action(getString(R.string.panel_pin)) { pinCurrentPage() },
            action(getString(R.string.panel_hide_element)) { startPicking() },
            action(getString(R.string.panel_save_short)) { saveCurrentPage() },
            action(sleepLabel()) { cycleSleep() },
            action(getString(R.string.panel_help)) { showHelp() },
            action(getString(R.string.panel_sites)) { finish() },
        )

        // La dernière redirection refusée : l'ouvrir une seule fois, l'autoriser pour ce site, ou l'ignorer.
        val blockedUrl = c?.lastBlockedUrl
        val blockedHost = blockedUrl?.let { Hosts.of(it) }
        val redirect = if (blockedUrl != null && blockedHost != null && current != null) {
            BlockedRedirect(
                title = getString(R.string.panel_redirect_title),
                host = blockedHost,
                openOnce = action(getString(R.string.panel_open_once_short)) {
                    c.allowOnce(blockedUrl)
                    webView?.loadUrl(blockedUrl)
                },
                allow = action(getString(R.string.panel_allow_always)) { allowDomain(current, blockedHost, blockedUrl) },
                ignore = action(getString(R.string.panel_ignore)) { c.dismissBlocked() },
            )
        } else {
            null
        }
        val counts = getString(R.string.panel_blocked, c?.blockedRequests?.get() ?: 0, c?.blockedNavigations?.get() ?: 0)

        showOverlay { QuickMenu(mediaState.value, redirect, actions, counts) }
    }

    /** Affiche un calque Compose par-dessus la page (menu rapide, téléphone) ; Retour le ferme. */
    private fun showOverlay(content: @Composable () -> Unit) {
        if (panelView != null) return
        val panel = ComposeView(this).apply { setContent { WiDoTheme { content() } } }
        panelView = panel
        root.addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))
        panel.bringToFront()
    }

    private fun hidePanel() {
        val panel = panelView ?: return
        panelView = null
        root.removeView(panel)
        webView?.requestFocus()
    }

    // --- Saisie depuis le téléphone ----------------------------------------------------------

    private fun showPhone() {
        showOverlay {
            PhoneOverlay(
                hint = getString(R.string.phone_hint_type),
                onMessage = { message ->
                    typeText(message.text, message.submit)
                    hidePanel()
                    osd.showText(getString(R.string.osd_text_sent))
                },
                onClose = { hidePanel() },
            )
        }
    }

    /**
     * Écrit `text` dans le champ sélectionné de la page (ou, à défaut, le premier champ de recherche
     * ou de texte visible), comme si on l'avait tapé ; `submit` valide ensuite (Entrée, formulaire).
     */
    private fun typeText(text: String, submit: Boolean) {
        val command = JSONObject().put("cmd", "type").put("text", text).put("submit", submit).toString()
        val b = bridge
        // Avec la passerelle, toutes les frames reçoivent la commande : celle qui a le champ actif écrit.
        if (b != null) b.send(command) else webView?.evaluateJavascript("window.__widoCommand&&window.__widoCommand(${JSONObject.quote(command)})", null)
    }

    // --- QR code sur les champs de la page ---------------------------------------------------

    private var fieldCard: ComposeView? = null
    private val hideFieldCard = Runnable {
        fieldCard?.let { root.removeView(it) }
        fieldCard = null
    }

    /** Un champ de la page est sélectionné : le QR code s'affiche en coin, tant que le champ garde le focus. */
    private fun onPageField(focused: Boolean) {
        root.removeCallbacks(hideFieldCard)
        root.removeCallbacks(progressPoll)
        root.removeCallbacks(resumeSeek)
        if (!focused) {
            // Le focus passe souvent d'un champ à l'autre : on attend un peu avant de retirer la carte.
            root.postDelayed(hideFieldCard, FIELD_CARD_GRACE_MS)
            return
        }
        if (!settings.fieldPhoneQr || fieldCard != null || panelView != null) return
        val card = ComposeView(this).apply {
            isFocusable = false
            setContent {
                WiDoTheme {
                    PhoneCornerCard { message ->
                        typeText(message.text, message.submit)
                        osd.showText(getString(R.string.osd_text_sent))
                    }
                }
            }
        }
        fieldCard = card
        root.addView(card, FrameLayout.LayoutParams(MATCH, MATCH))
        card.bringToFront()
        cursor.bringToFront()
    }

    // --- Masquer un élément ------------------------------------------------------------------

    private var picking = false

    /** Pointe un élément gênant (pop-up, bandeau) avec le curseur ; OK le masque, et il le restera sur ce site. */
    private fun startPicking() {
        picking = true
        if (!cursorMode) toggleCursor()
        cursor.wake()
        osd.showText(getString(R.string.osd_pick_hint))
    }

    private fun hideAtCursor() {
        picking = false
        val wv = webView ?: return
        val current = site ?: return
        if (wv.width == 0 || wv.height == 0) return
        val fx = cursor.px / wv.width
        val fy = cursor.py / wv.height
        wv.evaluateJavascript("($HIDE_SCRIPT)($fx,$fy)") { raw ->
            val selector = unquote(raw)
            if (selector == null) {
                osd.showText(getString(R.string.osd_pick_nothing))
            } else {
                (application as WiDoApp).repository.addHide(current.id, selector)
                osd.showText(getString(R.string.osd_pick_done))
            }
        }
    }

    // --- Pages épinglées ---------------------------------------------------------------------

    private fun pinCurrentPage() {
        val current = site ?: return
        val url = ResumeUrl.toRemember(current, webView?.url)
        if (url == null) {
            Toast.makeText(this, R.string.toast_pin_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val title = webView?.title
        (application as WiDoApp).repository.addPin(current.id, title, url)
        Toast.makeText(this, getString(R.string.toast_pinned, title?.take(40) ?: url), Toast.LENGTH_SHORT).show()
    }

    // --- Minuteur de veille ------------------------------------------------------------------

    private var sleepAt = 0L
    private var sleepMinutes = 0
    private val sleepWarning = Runnable { osd.showText(getString(R.string.osd_sleep_soon)) }
    private val sleepNow = Runnable { goToSleep() }

    private fun sleepLabel(): String {
        if (sleepAt == 0L) return getString(R.string.panel_sleep)
        val left = ((sleepAt - SystemClock.elapsedRealtime()) / 60_000L + 1).coerceAtLeast(1)
        return getString(R.string.panel_sleep_left, left.toInt())
    }

    /** Désactivé → 30 → 60 → 90 → 120 min → désactivé. */
    private fun cycleSleep() {
        val next = SLEEP_STEPS[(SLEEP_STEPS.indexOf(sleepMinutes) + 1) % SLEEP_STEPS.size]
        root.removeCallbacks(sleepWarning)
        root.removeCallbacks(sleepNow)
        root.removeCallbacks(hideFieldCard)
        root.removeCallbacks(progressPoll)
        root.removeCallbacks(resumeSeek)
        adWindow?.close()
        sleepMinutes = next
        if (next == 0) {
            sleepAt = 0L
            osd.showText(getString(R.string.osd_sleep_off))
            return
        }
        val delay = next * 60_000L
        sleepAt = SystemClock.elapsedRealtime() + delay
        root.postDelayed(sleepNow, delay)
        root.postDelayed(sleepWarning, delay - 60_000L)
        osd.showText(getString(R.string.osd_sleep_set, next))
    }

    /** Fin du minuteur : la vidéo se met en pause, l'écran peut s'éteindre, retour à l'accueil. */
    private fun goToSleep() {
        media("pause")
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Toast.makeText(this, R.string.toast_sleep, Toast.LENGTH_LONG).show()
        finish()
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
            client?.update(updated, repository.blockerFor(updated), repository.urlMatcherFor(updated))
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

    // --- Aide à l'écran ---------------------------------------------------------------------

    private fun showHelp() {
        if (helpView != null) return
        val density = resources.displayMetrics.density
        val card = TextView(this).apply {
            text = getString(R.string.help_body)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            useJakarta(600)
            setLineSpacing(0f, 1.3f)
            val pad = (32 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = glassDrawable(density)
            elevation = 30 * density
        }
        helpView = card
        root.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        card.bringToFront()
        card.postDelayed({ dismissHelp() }, HELP_TIMEOUT_MS)
    }

    private fun dismissHelp() {
        val view = helpView ?: return
        helpView = null
        root.removeView(view)
    }

    // --- Écran de chargement ----------------------------------------------------------------

    private fun buildLoadingOverlay(): FrameLayout {
        val density = resources.displayMetrics.density
        // Le logo : le nom de l'app en Plus Jakarta Sans extra-grasse, comme sur l'accueil.
        val logo = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 56f
            letterSpacing = -0.035f
            useJakarta(800)
            gravity = Gravity.CENTER
        }
        loadingLabel = TextView(this).apply {
            setText(R.string.player_loading)
            setTextColor(0xBFFFFFFF.toInt())
            textSize = 18f
            useJakarta(500)
            gravity = Gravity.CENTER
            setPadding(0, (14 * density).toInt(), 0, 0)
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
            background = haloDrawable()
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
        // Première utilisation : rappel des commandes.
        if (!settings.helpSeen) {
            settings.helpSeen = true
            root.postDelayed({ showHelp() }, 500)
        }
    }

    // --- Cycle de vie -----------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        root.postDelayed(progressPoll, PROGRESS_POLL_MS)
    }

    override fun onPause() {
        root.removeCallbacks(progressPoll)
        webView?.onPause()
        saveSeen()
        rememberPage()
        super.onPause()
    }

    /**
     * Retient la page affichée (et la position d'une vidéo YouTube) pour la prochaine ouverture du site,
     * et la vidéo en cours pour la rangée « Reprendre » de l'accueil.
     */
    private fun rememberPage() {
        val current = site ?: return
        val url = webView?.url
        ResumeUrl.toRemember(current, url, mediaState.value?.time)?.let { settings.setLastUrlFor(current.id, it) }
        val state = mediaState.value ?: return
        val page = ResumeUrl.toRemember(current, url) ?: return
        if (state.duration > 0) {
            (application as WiDoApp).repository.recordProgress(current.id, page, webView?.title, pageImage, state.time, state.duration)
        }
    }

    // --- Vidéo de la page : métadonnées, reprise, lecteur natif ------------------------------

    /** Image de la page (`og:image`), pour la vignette de « Reprendre ». */
    private var pageImage: String? = null

    private fun captureMeta() {
        webView?.evaluateJavascript(META_SCRIPT) { raw -> pageImage = unquote(raw)?.takeIf { it.startsWith("https://") } }
    }

    /** L'état du lecteur de la page est relevé de temps en temps, sans rien afficher : la progression reste à jour. */
    private val progressPoll = object : Runnable {
        override fun run() {
            if (!osd.isShowing && panelView == null) {
                passivePoll = true
                media("state")
            }
            root.postDelayed(this, PROGRESS_POLL_MS)
        }
    }

    /** Position (s) où reprendre la vidéo, demandée par « Reprendre » ; 0 = rien à faire. */
    private var pendingSeek = 0.0
    private var resumeAttempts = 0

    /** Interroge le lecteur jusqu'à ce que la vidéo soit prête, puis y saute (voir [applyPendingSeek]). */
    private val resumeSeek = object : Runnable {
        override fun run() {
            if (pendingSeek <= 0 || resumeAttempts++ > RESUME_ATTEMPTS) return
            passivePoll = true
            media("state")
            root.postDelayed(this, RESUME_POLL_MS)
        }
    }

    private fun startResumeSeek() {
        if (pendingSeek <= 0) return
        resumeAttempts = 0
        root.removeCallbacks(resumeSeek)
        root.postDelayed(resumeSeek, RESUME_POLL_MS)
    }

    private fun applyPendingSeek(state: MediaState) {
        if (pendingSeek <= 0 || state.duration <= 0) return
        val target = pendingSeek
        pendingSeek = 0.0
        if (kotlin.math.abs(state.time - target) > 5) media("seek", target - state.time)
    }

    private var streamHinted = false

    /** Un flux lisible par le lecteur natif est apparu : on le signale une fois par page. */
    private fun onStreamFound() {
        if (streamHinted) return
        streamHinted = true
        osd.showText(getString(R.string.osd_native_found))
    }

    /** « Lire dans WiDo » : le flux repéré s'ouvre dans le lecteur natif, la page se met en pause. */
    private fun openNative() {
        val current = site ?: return
        val wv = webView ?: return
        val stream = client?.stream
        if (stream == null) {
            osd.showText(getString(R.string.osd_native_none))
            return
        }
        media("pause")
        val cookies = runCatching { android.webkit.CookieManager.getInstance().getCookie(stream.url) }.getOrNull()
        startActivity(
            NativePlayerActivity.intent(
                this, stream, current.id, ResumeUrl.toRemember(current, wv.url), wv.title, pageImage, wv.url, cookies,
                wv.settings.userAgentString,
            ),
        )
    }

    /** Garde ce que le site a tenté (redirections refusées, domaines tiers) pour proposer des choix dans les réglages du site. */
    private fun saveSeen() {
        val id = site?.id ?: return
        val (navigations, requests) = client?.drainSeen() ?: return
        (application as WiDoApp).repository.recordSeen(id, navigations, requests)
    }

    override fun onDestroy() {
        overlayAnimator?.cancel()
        root.removeCallbacks(poll)
        root.removeCallbacks(sleepWarning)
        root.removeCallbacks(sleepNow)
        root.removeCallbacks(hideFieldCard)
        root.removeCallbacks(progressPoll)
        root.removeCallbacks(resumeSeek)
        adWindow?.close()
        customCallback?.onCustomViewHidden()
        webView?.let {
            root.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    /** Panneau de verre des vues classiques : sombre translucide, bord clair d'un pixel, coins de 28 dp. */
    private fun glassDrawable(density: Float) = GradientDrawable().apply {
        setColor(0xEB1A1A26.toInt())
        cornerRadius = 28 * density
        setStroke((1 * density).toInt(), 0x24FFFFFF)
    }

    /** Fond de l'écran de chargement : halos bleu et violet sur le noir du style, comme les réglages. */
    private fun haloDrawable(): LayerDrawable {
        fun glow(color: Int, x: Float, y: Float, radius: Float) = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(color, color and 0x00FFFFFF)
            setGradientCenter(x, y)
            gradientRadius = radius * resources.displayMetrics.widthPixels
        }
        return LayerDrawable(
            arrayOf<Drawable>(
                GradientDrawable().apply { setColor(0xFF0B0B12.toInt()) },
                glow(0xB31E5BFF.toInt(), 0.15f, 0.1f, 0.42f),
                glow(0xB37A2BE0.toInt(), 0.9f, 0.9f, 0.42f),
            ),
        )
    }

    companion object {
        const val EXTRA_SITE = "site"
        const val EXTRA_URL = "url"
        const val EXTRA_POSITION = "position"
        private const val PROGRESS_POLL_MS = 10_000L
        private const val RESUME_POLL_MS = 1_500L
        private const val RESUME_ATTEMPTS = 20
        private const val META_SCRIPT =
            "(function(){var m=document.querySelector('meta[property=\"og:image\"],meta[name=\"twitter:image\"]');return m?m.content:null;})()"
        /** Valeur spéciale de l'extra `site` : rouvre le dernier site ouvert. */
        const val LAST_SITE = "last"
        private const val FIELD_CARD_GRACE_MS = 1_500L
        private val SLEEP_STEPS = listOf(0, 30, 60, 90, 120)
        private const val BRIDGE_NAME = "WiDoBridge"
        private const val ACCENT = 0xFFFFFFFF.toInt()
        private const val OVERLAY_TIMEOUT_MS = 20_000L
        private const val HELP_TIMEOUT_MS = 15_000L
        private const val POLL_MS = 700L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val LONG_PRESS_MS = 600L
        /**
         * Masque l'élément sous le curseur, ou le calque qui le contient (position fixe, z-index élevé),
         * et renvoie un sélecteur CSS qui le retrouvera aux prochaines visites.
         */
        private const val HIDE_SCRIPT = """function (fx, fy) {
  var el = document.elementFromPoint(fx * innerWidth, fy * innerHeight);
  if (!el || el === document.body || el === document.documentElement) return null;
  var target = el;
  for (var cur = el; cur && cur !== document.body; cur = cur.parentElement) {
    var cs = getComputedStyle(cur);
    if (cs.position === 'fixed' || cs.position === 'sticky' || (parseInt(cs.zIndex, 10) || 0) >= 100) target = cur;
  }
  function selector(e) {
    var parts = [];
    while (e && e.nodeType === 1 && e !== document.body && parts.length < 5) {
      if (e.id && /^[A-Za-z][\w-]*$/.test(e.id)) { parts.unshift('#' + e.id); break; }
      var part = e.tagName.toLowerCase();
      var cls = Array.prototype.filter.call(e.classList, function (c) { return /^[A-Za-z][\w-]{0,39}$/.test(c); }).slice(0, 2);
      if (cls.length) part += '.' + cls.join('.');
      else if (e.parentElement) part += ':nth-child(' + (Array.prototype.indexOf.call(e.parentElement.children, e) + 1) + ')';
      parts.unshift(part);
      e = e.parentElement;
    }
    return parts.join(' > ');
  }
  var s = selector(target);
  if (!s) return null;
  target.style.setProperty('display', 'none', 'important');
  document.documentElement.style.overflow = '';
  if (document.body) document.body.style.overflow = '';
  return s;
}"""
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        /**
         * `url` : une page précise du site (page épinglée, « Reprendre »), vérifiée avant d'être ouverte ;
         * `positionSeconds` : où reprendre sa vidéo.
         */
        fun intent(context: Context, siteId: String, url: String? = null, positionSeconds: Double = 0.0): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_SITE, siteId).apply {
                if (url != null) putExtra(EXTRA_URL, url)
                if (positionSeconds > 0) putExtra(EXTRA_POSITION, positionSeconds)
            }
    }
}
