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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.animation.doOnEnd
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.wido.core.HandoffPlay
import dev.wido.core.Hosts
import dev.wido.core.NowPlaying
import dev.wido.core.Navigation
import dev.wido.core.RemoteCommand
import dev.wido.core.RemoteKey
import dev.wido.core.ResumeUrl
import dev.wido.core.Site
import dev.wido.core.Zoom
import dev.wido.tv.ui.BlockedRedirect
import dev.wido.tv.ui.Glyph
import dev.wido.tv.ui.PairPrompt
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
class PlayerActivity : ComponentActivity(), RemoteTarget {
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
    /** TV : curseur et télécommande. Téléphone : la page se touche directement, un bouton ouvre le menu. */
    private val tv by lazy { (application as WiDoApp).isTv }
    private var menuButton: View? = null
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

    /** État gardé par Android quand il a fermé l'appli en arrière-plan (page et position du moment). */
    private var restored: Bundle? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.url?.let { outState.putString(STATE_URL, it) }
        outState.putDouble(STATE_POSITION, currentPosition())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restored = savedInstanceState
        settings = (application as WiDoApp).settings
        if (tv) UiSound.init(this, settings.uiSounds)
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
            // Lien envoyé depuis le téléphone : le site du menu qui le connaît, ou un site éphémère.
            val linkUrl = intent.getStringExtra(EXTRA_URL)
            // Une page précise (Reprendre, épingle, envoi du téléphone) que le site ne couvre plus (domaine
            // changé, lien éphémère) : le site qui la connaît, sinon un site éphémère. Jamais l'accueil à la place.
            val known = if (siteId == ConfigRepository.LINK_SITE_ID) null else app.repository.findSite(siteId)
            val site = when {
                linkUrl != null && (known == null || ResumeUrl.toRemember(known, linkUrl) == null) -> app.repository.siteForUrl(linkUrl)
                else -> known
            }
            this@PlayerActivity.site = site
            if (site == null) {
                Toast.makeText(this@PlayerActivity, getString(R.string.player_unknown_site, siteId), Toast.LENGTH_LONG).show()
                close("site inconnu")
                return@launch
            }
            if (site.id != ConfigRepository.LINK_SITE_ID) settings.lastSiteId = site.id
            CrashLog.event("Lecteur ouvert : ${site.name}" + (intent.getStringExtra(EXTRA_URL)?.let { " (${Hosts.of(it).orEmpty()})" } ?: ""))
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
                            osd.showText(getString(R.string.toast_navigation_blocked, url.take(60)), OsdView.Kind.Blocked)
                        }
                    },
                    onPageVisible = {
                        runOnUiThread {
                            hideOverlay()
                            captureMeta()
                            startResumeSeek()
                            startAutoPlay()
                            publishNowPlaying(mediaState.value)
                        }
                    },
                    onPageStart = {
                        // On quitte la page (épisode suivant…) : sa position est enregistrée avant d'être oubliée.
                        runOnUiThread { rememberProgress() }
                        cinema = false
                        runOnUiThread { hidePauseScreen() }
                        bridge?.reset()
                        mediaState.value = null
                        streamHinted = false
                    },
                    onAdWindow = { url -> runOnUiThread { adWindow?.open(url) } },
                    onMediaStream = { runOnUiThread { onStreamFound() } },
                    onPageError = { code, failed -> runOnUiThread { showPageError(code, failed) } },
                    onRendererGone = { runOnUiThread { onRendererGone() } },
                ),
            )
            // Rafraîchit la configuration pour la prochaine ouverture, sans bloquer la lecture.
            launch { app.repository.refresh() }
        }
        // TV : un téléphone demande à se lier pendant la lecture → confirmation par-dessus la page.
        if (tv) lifecycleScope.launch { app.link.pairRequests.collect { showPairPrompt(it.firstOrNull()) } }
    }

    private var pairShown = false

    private fun showPairPrompt(pending: dev.wido.core.PairingDesk.Pending?) {
        if (pairShown) {
            pairShown = false
            hidePanel()
        }
        if (pending == null || webView == null) return
        // Pas de fenêtre par-dessus un film qui joue : la demande attend (2 min) et un message le dit.
        if (mediaState.value?.let { !it.paused && it.duration > 0 } == true) {
            osd.showText(getString(R.string.osd_pair_waiting))
            return
        }
        hidePanel()
        val link = (application as WiDoApp).link
        showOverlay { PairPrompt(pending) { approved, level -> link.decidePair(pending, approved, level) } }
        pairShown = true
        val left = dev.wido.core.PairingDesk.EXPIRY_MS - (System.currentTimeMillis() - pending.since)
        root.postDelayed({ link.refreshPairRequests() }, left.coerceAtLeast(0) + 500)
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
                onSkip = { runOnUiThread { osd.showText(getString(R.string.osd_sponsor_skipped), OsdView.Kind.Success) } },
                onField = { focused -> runOnUiThread { onPageField(focused) } },
                onModal = { x, y, gone -> runOnUiThread { onPageModal(x, y, gone) } },
                onMediaEvent = { state ->
                    runOnUiThread {
                        passivePoll = true
                        onMediaState(state)
                    }
                },
            )
            bridge = frameBridge
            WebViewCompat.addWebMessageListener(wv, BRIDGE_NAME, setOf("*"), frameBridge)
            WebViewCompat.addDocumentStartJavaScript(wv, injector?.internalScript().orEmpty(), setOf("*"))
        }
        injector?.includeInternal = !bridged
        // Téléphone, lecture en arrière-plan : la page se croit toujours visible (sinon beaucoup de sites coupent la vidéo).
        if (documentStart && !tv && settings.backgroundPlay) WebViewCompat.addDocumentStartJavaScript(wv, KEEP_VISIBLE_SCRIPT, setOf("*"))
        // Protections (masquage, YouTube) avant le moindre script de la page, quand la WebView le permet.
        val early = injector?.early.orEmpty()
        if (documentStart && early.isNotBlank()) {
            WebViewCompat.addDocumentStartJavaScript(wv, early, setOf("*"))
            injector?.includeEarly = false
        }

        if (site.navigation == Navigation.MIXED) {
            adWindow = AdWindow(this, root, onOpened = { osd.showText(getString(R.string.osd_ad_background)) }) { webView?.requestFocus() }
        }
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
                menuButton?.bringToFront()
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
        if (tv) {
            cursor.wake()
        } else {
            cursorMode = false
            cursor.visibility = View.GONE
        }

        osd = OsdView(this, compact = !tv, scale = if (tv) settings.uiScale else 1f).apply { hideSeconds = settings.osdSeconds }
        root.addView(osd, osd.layoutParamsForRoot())

        topBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
            visibility = View.GONE
        }
        root.addView(topBar, FrameLayout.LayoutParams(MATCH, (4 * resources.displayMetrics.density).toInt(), Gravity.TOP))
        if (!tv) addMenuButton()
        overlay.bringToFront()

        wv.requestFocus()
        // Recréé par Android après l'avoir fermé en arrière-plan : la page et la position du moment, pas
        // celles de l'ouverture ; la vidéo attend OK.
        val back = restored?.getString(STATE_URL)?.takeIf { Hosts.schemeOf(it) == "https" || Hosts.schemeOf(it) == "http" }
        pendingSeek = if (back != null) restored?.getDouble(STATE_POSITION) ?: 0.0 else intent.getDoubleExtra(EXTRA_POSITION, 0.0)
        // Une page épinglée, sinon la dernière page visitée du site (réglage « Reprendre »), sinon son accueil.
        val pinned = back ?: intent.getStringExtra(EXTRA_URL)?.let { ResumeUrl.toRemember(site, it) }
        autoStart = back == null && intent.getBooleanExtra(EXTRA_AUTOPLAY, false)
        // Cette vidéo a fait tomber la session précédente (vrai plantage, selon Android quand il le dit) :
        // pas de lecture ni de plein écran automatiques, sinon le même plantage recommence aussitôt.
        if (autoStart && pinned != null && settings.openPage == pinned && CrashLog.lastExitCrashed != false) {
            autoStart = false
            root.postDelayed({ osd.showText(getString(R.string.osd_autoplay_skipped), OsdView.Kind.Warning) }, 1_500)
        }
        settings.openPage = pinned
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
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            CrashLog.trace("site : touche ${KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")}, curseur=$cursorMode, cinéma=$cinema")
        }

        // L'aide à l'écran se ferme à n'importe quelle touche.
        if (helpView != null) {
            if (event.action == KeyEvent.ACTION_UP) dismissHelp()
            return true
        }

        val code = event.keyCode
        lastInputAt = SystemClock.uptimeMillis()

        // Écran de pause : OK ou lecture relance la vidéo ; toute autre touche ne fait que le retirer
        // (elle n'agit pas en plus : Retour ne quitte pas la page). Le volume reste utilisable.
        if (swallowUp == code && event.action == KeyEvent.ACTION_UP) {
            swallowUp = 0
            return true
        }
        if (pauseView != null && event.action == KeyEvent.ACTION_DOWN) {
            hidePauseScreen()
            when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    swallowUp = code
                    media("play")
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> Unit
                else -> {
                    swallowUp = code
                    return true
                }
            }
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
                    when {
                        code == KeyEvent.KEYCODE_BACK && backLongPressed -> backLongPressed = false
                        // Page perdue : rien derrière l'écran d'erreur, Retour ramène à l'accueil.
                        rendererLost && code == KeyEvent.KEYCODE_BACK -> goHome()
                        rendererLost -> Unit
                        // Page qui ne s'ouvre pas : la touche Retour fait ce que dit le bouton « Retour ».
                        errorBack != null && code == KeyEvent.KEYCODE_BACK -> errorBack?.invoke()
                        else -> hidePanel()
                    }
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
                if (!tv || !settings.customVolume || audio.isVolumeFixed) return super.dispatchKeyEvent(event)
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

        // Plein écran vidéo : les flèches pilotent le lecteur. En pause, elles déplacent le curseur pour
        // atteindre les réglages du lecteur du site (sous-titres, qualité, épisodes…) ; OK clique.
        if ((customView != null || cinema) && settings.fullscreenPlayerKeys) {
            if (mediaState.value?.paused == true) {
                if (pausedCursorKey(event)) return true
            } else if (fullscreenKey(event)) {
                return true
            }
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
        if (event.action == KeyEvent.ACTION_UP) CrashLog.trace("site : retour (aide=${helpView != null}, menu=${panelView != null})")
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
                when {
                    backLongPressed -> backLongPressed = false
                    // Deux appuis rapides : retour direct aux sites, d'où qu'on soit dans la page.
                    settings.doubleBackToSites && event.eventTime - lastBackUp < DOUBLE_BACK_MS -> {
                        lastBackUp = 0L
                        backToSites()
                    }
                    else -> {
                        lastBackUp = event.eventTime
                        goBackOrExit()
                    }
                }
            }
        }
        return true
    }

    private var lastBackUp = 0L
    private var exitHintAt = 0L

    /** L'accueil, onglet Sites. */
    private fun backToSites() {
        if (tv) UiSound.back()
        startActivity(MainActivity.showSites(this))
        close("double Retour")
    }

    private fun goBackOrExit() {
        val wv = webView
        when {
            cinema -> setCinema(false)
            customView != null -> exitCustomView()
            wv != null && wv.canGoBack() -> wv.goBack()
            else -> {
                // Quitter le site d'un seul Retour par mégarde : le premier appui prévient, le second (ou le double
                // appui rapide, qui va aux sites) quitte.
                if (tv && SystemClock.uptimeMillis() - exitHintAt > EXIT_CONFIRM_MS) {
                    exitHintAt = SystemClock.uptimeMillis()
                    osd.showText(getString(R.string.osd_exit_confirm))
                    return
                }
                // Ouvert depuis le téléphone ou Home Assistant sans l'accueil derrière : on y revient au lieu de quitter.
                if (tv) UiSound.back()
                if (isTaskRoot) startActivity(Intent(this, MainActivity::class.java))
                close("Retour (plus de page précédente)")
            }
        }
    }

    private fun colorKey(event: KeyEvent, action: KeyAction): Boolean {
        if (event.action == KeyEvent.ACTION_UP) runKeyAction(action)
        return true
    }

    private fun runKeyAction(action: KeyAction) {
        when (action) {
            KeyAction.NONE -> Unit
            KeyAction.MENU -> close("touche de couleur Menu")
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

    /** Position du curseur avant qu'une fenêtre surgissante ne l'attire, pour l'y remettre à sa fermeture. */
    private var cursorBeforeModal: Pair<Float, Float>? = null

    /**
     * Une fenêtre surgissante (cookies, pop-up) s'affiche : le curseur va sur son bouton principal (et le
     * marque), sans qu'on ait à le chercher sur toute la page ; à sa fermeture, il revient où il était.
     */
    private fun onPageModal(x: Double, y: Double, gone: Boolean) {
        CrashLog.trace(if (gone) "site : fenêtre de la page disparue" else "site : fenêtre de la page détectée")
        val wv = webView ?: return
        if (!tv || !cursorMode || cinema || customView != null || picking) return
        if (gone) {
            cursorBeforeModal?.let { cursor.moveTo(it.first, it.second) }
            cursorBeforeModal = null
            return
        }
        if (x < 0 || y < 0 || wv.width == 0 || wv.height == 0) return
        if (cursorBeforeModal == null) cursorBeforeModal = cursor.px to cursor.py
        cursor.moveTo((x * wv.width).toFloat(), (y * wv.height).toFloat())
        cursor.wake()
        osd.showText(getString(R.string.osd_popup_cursor))
    }

    /** Défile ce qui est sous le curseur (zone défilante d'une fenêtre, sinon la page) ; sinon le curseur avance d'un pas. */
    private fun scrollUnderCursor(dirY: Int) {
        val wv = webView ?: return
        if (wv.width == 0 || wv.height == 0) return
        val fx = cursor.px / wv.width
        val fy = cursor.py / wv.height
        wv.evaluateJavascript("window.__widoScrollAt?window.__widoScrollAt($fx,$fy,${dirY * 0.5}):false") { raw ->
            if (raw != "true") freeMove(0, 0, dirY)
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
            } else if (dirY != 0) {
                // Rien dans cette direction : on défile (la fenêtre surgissante ou la page), comme au doigt.
                scrollUnderCursor(dirY)
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
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seekBy(seek * (1 + minOf(repeat, 4)))
            KeyEvent.KEYCODE_MEDIA_REWIND -> seekBy(-seek * (1 + minOf(repeat, 4)))
            else -> if (repeat == 0) media("toggle")
        }
    }

    /** Flèches et OK en plein écran vidéo. Renvoie vrai si la touche est consommée. */
    private fun fullscreenKey(event: KeyEvent): Boolean {
        val seek = settings.seekSeconds.toDouble()
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (event.action == KeyEvent.ACTION_DOWN) seekBy(-seek * (1 + minOf(event.repeatCount / 4, 4)))
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (event.action == KeyEvent.ACTION_DOWN) seekBy(seek * (1 + minOf(event.repeatCount / 4, 4)))
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) media("toggle")
            else -> return false
        }
        return true
    }

    /** Vidéo en pause en plein écran : le curseur apparaît et se déplace librement, OK clique. */
    private fun pausedCursorKey(event: KeyEvent): Boolean {
        val dir = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> 0 to -1
            KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1
            KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1 to 0
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Curseur pas encore sorti : OK relance simplement la vidéo.
                if (cursor.visibility == View.VISIBLE) return tap(event)
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) media("toggle")
                return true
            }
            else -> return false
        }
        if (cursor.visibility != View.VISIBLE) {
            cursor.visibility = View.VISIBLE
            pauseCursor = true
            osd.showText(getString(R.string.osd_paused_cursor))
        }
        cursor.wake()
        if (event.action == KeyEvent.ACTION_DOWN) freeMove(event.repeatCount, dir.first, dir.second)
        return true
    }

    /** Curseur montré pour la pause seulement : il repart quand la lecture reprend. */
    private var pauseCursor = false

    /** Avance ou recule ; la barre affiche le saut. */
    private fun seekBy(seconds: Double) {
        cancelSeekGuard()
        osd.markSeek(seconds)
        media("seek", seconds)
    }

    private var osdThumb: android.graphics.Bitmap? = null

    /** Titre, site et image de la vidéo pour la barre de lecture. */
    private fun refreshOsdInfo() {
        val current = site ?: return
        val wv = webView ?: return
        osd.setInfo(wv.title?.takeIf { it != wv.url }, current.name)
        val page = ResumeUrl.toRemember(current, wv.url) ?: return
        val image = pageImage
        lifecycleScope.launch {
            val bitmap = (application as WiDoApp).thumbnails.forVideo(page, image)
            osdThumb = bitmap?.asAndroidBitmap()
            osd.setThumb(osdThumb)
        }
    }

    /** Commande envoyée au lecteur vidéo de la page, dans n'importe quelle frame. */
    private fun media(cmd: String, by: Double? = null) =
        mediaJson(JSONObject().put("cmd", cmd).apply { if (by != null) put("by", by) }.toString())

    private fun mediaJson(json: String) {
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
        // La page à laquelle appartient cette position (et non celle qui serait déjà en train de s'ouvrir).
        mediaPage = webView?.url
        mediaTitle = webView?.title
        mediaAt = SystemClock.elapsedRealtime()
        if (backgrounded) BackgroundBridge.update(playbackInfo(state))
        applyAutoStart(state)
        maybeAutoNative()
        schedulePauseScreen(state)
        if (pauseCursor && !state.paused) {
            pauseCursor = false
            if (!cursorMode) cursor.visibility = View.GONE
        }
        publishNowPlaying(state)
        if (!state.paused) captureFrame()
        applyPendingSeek(state)
        // Pas de barre de lecture par-dessus celle du site : seulement le saut demandé (« +30 s »).
        if (!passive) osd.showSeek()
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

    private var panelPending = false

    /** Ouvre le menu rapide ; demande d'abord à la page si elle a un bouton « épisode suivant ». */
    private fun showPanel() {
        if (panelView != null || panelPending) return
        val wv = webView ?: return buildPanel(hasNext = false)
        panelPending = true
        wv.evaluateJavascript("($NEXT_SCRIPT)(false)") { raw ->
            panelPending = false
            buildPanel(hasNext = raw == "true")
        }
    }

    /** Augmente quand une action laisse le menu ouvert : le menu relit ses étiquettes. */
    private val panelTick = mutableIntStateOf(0)

    private fun buildPanel(hasNext: Boolean) {
        if (panelView != null) return
        hidePauseScreen()
        val c = client
        val current = site
        // `stay` : le menu reste ouvert (on ajuste encore : texte plus grand, veille…), le résultat s'affiche par-dessus.
        fun action(label: String, glyph: Glyph, more: Boolean = false, stay: Boolean = false, dynamic: (() -> String)? = null, run: () -> Unit) =
            QuickAction(label, glyph, more, dynamic) {
                if (!stay) hidePanel()
                run()
                // Le menu reste ouvert : son étiquette (Veille 30 min…) suit.
                if (stay) panelTick.intValue++
            }

        val media = mediaState.value?.takeIf { it.duration > 0 }
        // Téléphone lié à une TV : la vidéo passe sur la TV, à la même seconde.
        val screen = if (!tv) (application as WiDoApp).link.selectedPeer() else null
        val toTv = screen?.let { action(getString(R.string.tile_on_tv), Glyph.Tv) { sendToTv() } }
        val native = c?.stream?.let { action(getString(R.string.tile_native), Glyph.Native) { openNative() } }
        val next = if (hasNext) action(getString(R.string.tile_next), Glyph.Next) { nextEpisode() } else null
        val pin = action(getString(R.string.panel_pin), Glyph.Star) { pinCurrentPage() }
        val zoomOut = action(getString(R.string.panel_zoom_out_short), Glyph.ZoomOut, stay = true) { changeZoom(-1) }
        val zoomIn = action(getString(R.string.panel_zoom_in_short), Glyph.ZoomIn, stay = true) { changeZoom(1) }
        val sleep = action(sleepLabel(), Glyph.Sleep, more = media == null, stay = true, dynamic = ::sleepLabel) { cycleSleep() }
        // En dernier de la rangée : on ne quitte pas le lecteur par mégarde en cherchant « Texte + ».
        val sites = action(getString(R.string.panel_sites), Glyph.Sites, more = true) { close("menu rapide : Sites") }
        val hide = if (tv) action(getString(R.string.tile_hide), Glyph.Hide, more = media != null) { startPicking() } else null
        val siteHome = action(getString(R.string.tile_site_home), Glyph.Globe, more = media != null) { current?.let { webView?.loadUrl(it.url) } }
        val reload = action(getString(R.string.panel_reload), Glyph.Refresh, more = media != null) { webView?.reload() }
        val extras = listOfNotNull(
            action(getString(R.string.panel_save_short), Glyph.Save, more = true) { saveCurrentPage() },
            if (tv) action(getString(if (cursorMode) R.string.tile_cursor_on else R.string.tile_cursor_off), Glyph.Cursor, more = true) { toggleCursor() } else null,
            if (tv) action(getString(R.string.panel_phone), Glyph.Phone, more = true) { showPhone() } else null,
            if (tv) action(getString(R.string.panel_help), Glyph.Help, more = true) { showHelp() } else null,
        )
        // Une vidéo : ses commandes d'abord. Une page : ce qu'on fait d'une page.
        val actions = if (media != null) {
            listOfNotNull(
                toTv,
                action(getString(if (media.paused) R.string.tile_play else R.string.tile_pause), if (media.paused) Glyph.Play else Glyph.Pause) { media("toggle") },
                next,
                native,
                action(getString(R.string.tile_restart), Glyph.Restart) { cancelSeekGuard(); mediaJson(JSONObject().put("cmd", "seekTo").put("to", 0).toString()) },
                sleep, pin, zoomOut, zoomIn,
                hide, siteHome, reload,
            ) + extras + sites
        } else {
            listOfNotNull(toTv, native, next, pin, zoomOut, zoomIn, hide, siteHome, reload, sleep) + extras + sites
        }

        // La dernière redirection refusée : l'ouvrir une seule fois, l'autoriser pour ce site, ou l'ignorer.
        val blockedUrl = c?.lastBlockedUrl
        val blockedHost = blockedUrl?.let { Hosts.of(it) }
        val redirect = if (blockedUrl != null && blockedHost != null && current != null) {
            BlockedRedirect(
                title = getString(R.string.panel_redirect_title),
                host = blockedHost,
                openOnce = action(getString(R.string.panel_open_once_short), Glyph.Globe) {
                    c.allowOnce(blockedUrl)
                    webView?.loadUrl(blockedUrl)
                },
                allow = action(getString(R.string.panel_allow_always), Glyph.Check) { allowDomain(current, blockedHost, blockedUrl) },
                ignore = action(getString(R.string.panel_ignore), Glyph.Close) { c.dismissBlocked() },
            )
        } else {
            null
        }
        val counts = getString(R.string.panel_blocked, c?.blockedRequests?.get() ?: 0, c?.blockedNavigations?.get() ?: 0)

        val more = getString(R.string.tile_more)
        showOverlay { QuickMenu(mediaState.value, redirect, actions, counts, compact = !tv, onDismiss = { hidePanel() }, moreLabel = more, tick = panelTick.intValue) }
        // Les messages (Texte +, Veille…) s'affichent au-dessus du menu, pas dessus.
        if (tv) osd.raise((150 * resources.displayMetrics.density).toInt())
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
        errorBack = null
        osd.raise(0)
        root.removeView(panel)
        webView?.requestFocus()
    }

    /** « Épisode suivant » : clique le bouton de la page qui mène à l'épisode suivant. */
    private fun nextEpisode() {
        webView?.evaluateJavascript("($NEXT_SCRIPT)(true)") { raw ->
            osd.showText(getString(if (raw == "true") R.string.osd_next_episode else R.string.osd_next_none), if (raw == "true") OsdView.Kind.Success else OsdView.Kind.Warning)
        }
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
        if (!focused) {
            // Le focus passe souvent d'un champ à l'autre : on attend un peu avant de retirer la carte.
            root.postDelayed(hideFieldCard, FIELD_CARD_GRACE_MS)
            return
        }
        if (!tv || !settings.fieldPhoneQr || fieldCard != null || panelView != null) return
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
                osd.showText(getString(R.string.osd_pick_nothing), OsdView.Kind.Warning)
            } else {
                (application as WiDoApp).repository.addHide(current.id, selector)
                osd.showText(getString(R.string.osd_pick_done), OsdView.Kind.Success)
            }
        }
    }

    // --- Pages épinglées ---------------------------------------------------------------------

    private fun pinCurrentPage() {
        val current = site ?: return
        val url = ResumeUrl.toRemember(current, webView?.url)
        if (url == null) {
            osd.showText(getString(R.string.toast_pin_failed), OsdView.Kind.Warning)
            return
        }
        val title = webView?.title
        (application as WiDoApp).repository.addPin(current.id, title, url)
        osd.showText(getString(R.string.toast_pinned, title?.take(40) ?: url), OsdView.Kind.Success)
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
        close("minuteur de veille")
    }

    /** Autorise `host` pour ce site (gardé localement), recharge les règles puis ouvre la page refusée. */
    private fun allowDomain(current: Site, host: String, thenOpen: String) {
        val repository = (application as WiDoApp).repository
        val error = repository.allowDomain(current.id, host)
        if (error != null) {
            osd.showText(error, OsdView.Kind.Warning)
            return
        }
        osd.showText(getString(R.string.toast_allowed, host), OsdView.Kind.Success)
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
        if (error != null) osd.showText(error, OsdView.Kind.Warning) else osd.showText(getString(R.string.toast_saved, name), OsdView.Kind.Success)
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
        wordmark = logo
        val logoBox = FrameLayout(this).apply {
            addView(logo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(logoBox)
            addView(loadingLabel)
            addView(bar)
        }
        // Le logo « respire » pendant l'attente.
        overlayAnimator = ObjectAnimator.ofPropertyValuesHolder(
            logoBox,
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
            post { playOpening(column) }
        }
    }

    private var wordmark: View? = null

    /**
     * Ouverture depuis l'accueil (TV), comme un seul geste, sans hâte : l'accueil reste à l'écran (sa
     * capture), le vinyle choisi part de sa place exacte, grandit lentement en tournant et glisse vers le
     * centre ; son étiquette se resserre et le logo du site s'efface, puis le disque se fond dans l'écran
     * d'attente, où apparaît le logo WiDo.
     */
    private fun playOpening(content: View) {
        val origin = intent.getIntArrayExtra(EXTRA_ORIGIN)?.takeIf { it.size == 4 } ?: return
        val snapshot = LaunchSnapshot.take()
        val shot = snapshot?.let { bitmap ->
            android.widget.ImageView(this).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_XY
                setImageBitmap(bitmap)
            }.also { overlay.addView(it, FrameLayout.LayoutParams(MATCH, MATCH)) }
        }
        val reveal = RevealView(this, intent.getIntExtra(EXTRA_COLOR, 0xFF1E5BFF.toInt()))
        overlay.addView(reveal, FrameLayout.LayoutParams(MATCH, MATCH))
        content.alpha = 0f
        if (intent.getBooleanExtra(EXTRA_TILE, false)) {
            // Vignette de Reprendre : la vignette elle-même s'agrandit (rectangle aux coins arrondis qui se
            // redresse) jusqu'à l'écran, sur le fond de l'appli ; pas de disque qui sortirait d'un rectangle.
            reveal.post {
                val density = resources.displayMetrics.density
                val w = reveal.width.toFloat()
                val h = reveal.height.toFloat()
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 620
                    interpolator = android.view.animation.PathInterpolator(0.35f, 0f, 0.1f, 1f)
                    addUpdateListener {
                        val f = it.animatedValue as Float
                        reveal.setTile(
                            origin[0] * (1 - f), origin[1] * (1 - f),
                            origin[2] + (w - origin[2]) * f, origin[3] + (h - origin[3]) * f,
                            (18 * density) * (1 - f),
                        )
                    }
                    doOnEnd {
                        shot?.let { overlay.removeView(it) }
                        snapshot?.recycle()
                        content.animate().alpha(1f).setDuration(350).start()
                        reveal.animate().alpha(0f).setDuration(420).withEndAction { overlay.removeView(reveal) }.start()
                    }
                    start()
                }
            }
            return
        }
        site?.let { current ->
            lifecycleScope.launch { (application as WiDoApp).favicons.load(current)?.let { reveal.logo = it.image.asAndroidBitmap() } }
        }
        reveal.post {
            val density = resources.displayMetrics.density
            val cx0 = (origin[0] + origin[2]) / 2f
            val cy0 = (origin[1] + origin[3]) / 2f
            val r0 = (origin[2] - origin[0]) / 2f
            // Où sera le logo de l'écran d'attente (centre de sa boîte), dans les coordonnées du dessin.
            val box = wordmark?.parent as? View
            val here = IntArray(2).also { reveal.getLocationInWindow(it) }
            val target = IntArray(2).also { box?.getLocationInWindow(it) }
            val cx1 = if (box != null && box.width > 0) target[0] - here[0] + box.width / 2f else reveal.width / 2f
            val cy1 = if (box != null && box.height > 0) target[1] - here[1] + box.height / 2f else reveal.height / 2f
            val label0 = r0 * LABEL_RATIO
            val label1 = 30 * density
            val r1 = kotlin.math.hypot(maxOf(cx1, reveal.width - cx1), maxOf(cy1, reveal.height - cy1)) * 1.05f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 980
                // Départ en douceur, longue arrivée qui ralentit : pas d'à-coup.
                interpolator = android.view.animation.PathInterpolator(0.35f, 0f, 0.1f, 1f)
                addUpdateListener {
                    val f = it.animatedValue as Float
                    reveal.logoAlpha = (1f - f * 1.6f).coerceIn(0f, 1f)
                    reveal.set(
                        cx0 + (cx1 - cx0) * f,
                        cy0 + (cy1 - cy0) * f,
                        r0 + (r1 - r0) * f,
                        label0 + (label1 - label0) * f,
                        40f + 240f * f,
                    )
                }
                doOnEnd {
                    shot?.let { overlay.removeView(it) }
                    snapshot?.recycle()
                    content.animate().alpha(1f).setDuration(420).start()
                    reveal.animate().alpha(0f).setDuration(520).withEndAction { overlay.removeView(reveal) }.start()
                }
                start()
            }
        }
    }

    /**
     * Le vinyle de l'ouverture : disque noir, sillons, reflets qui tournent, anneau à la couleur du site
     * et étiquette blanche avec le logo, comme sur l'accueil. Dessiné directement (fluide sur une clé TV).
     */
    private class RevealView(context: Context, color: Int) : View(context) {
        // Dégradés créés une seule fois (rayon 1, centre 0,0), placés à chaque image par une matrice :
        // aucune allocation pendant l'animation.
        private val placement = android.graphics.Matrix()
        private val discShader = android.graphics.RadialGradient(
            0f, 0f, 1f, intArrayOf(0xFF1E1E24.toInt(), 0xFF0B0B0E.toInt()), null, android.graphics.Shader.TileMode.CLAMP,
        )
        private val sheenShader = android.graphics.SweepGradient(
            0f, 0f,
            intArrayOf(0, 0x26FFFFFF, 0, 0, 0x26FFFFFF, 0, 0),
            floatArrayOf(0f, 0.07f, 0.11f, 0.5f, 0.57f, 0.61f, 1f),
        )
        private val disc = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { shader = discShader }
        private val groove = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            this.color = Color.WHITE
            strokeWidth = 1.5f
        }
        private val sheen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { shader = sheenShader }
        private val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            this.color = color
        }
        private val label = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xF7FFFFFF.toInt() }
        private val picture = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        private val dest = android.graphics.RectF()
        private var cx = 0f
        private var cy = 0f
        private var radius = 0f
        private var labelRadius = 0f
        private var spin = 0f

        /** Opacité du logo (il s'efface pendant l'ouverture). */
        var logoAlpha = 1f

        /** Logo du site sur l'étiquette (dès qu'il est chargé). */
        var logo: android.graphics.Bitmap? = null
            set(v) {
                field = v
                invalidate()
            }

        private val tileRect = android.graphics.RectF()
        private val tilePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF0B0B12.toInt() }
        private var tileCorner = 0f
        private var tileMode = false

        /** Ouverture depuis une vignette rectangulaire : un rectangle à coins arrondis qui grandit. */
        fun setTile(l: Float, t: Float, r: Float, b: Float, corner: Float) {
            tileMode = true
            tileRect.set(l, t, r, b)
            tileCorner = corner
            invalidate()
        }

        fun set(x: Float, y: Float, r: Float, labelR: Float, angle: Float) {
            cx = x
            cy = y
            radius = r
            labelRadius = labelR
            spin = angle
            invalidate()
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            if (tileMode) {
                canvas.drawRoundRect(tileRect, tileCorner, tileCorner, tilePaint)
                return
            }
            if (radius <= 0) return
            placement.setScale(radius, radius)
            placement.postTranslate(cx, cy)
            discShader.setLocalMatrix(placement)
            canvas.drawCircle(cx, cy, radius, disc)
            // Sillons : de l'anneau au bord, un trait fin tous les 14 px environ.
            val inner = labelRadius * 1.12f
            val outer = radius * 0.95f
            if (outer > inner) {
                val count = ((outer - inner) / 14f).toInt().coerceIn(4, 40)
                for (i in 0..count) {
                    groove.alpha = if (i % 2 == 0) 0x2A else 0x14
                    canvas.drawCircle(cx, cy, inner + (outer - inner) * i / count, groove)
                }
            }
            placement.setRotate(spin)
            placement.postTranslate(cx, cy)
            sheenShader.setLocalMatrix(placement)
            canvas.drawCircle(cx, cy, radius * 0.95f, sheen)
            ring.strokeWidth = maxOf(3f, labelRadius * 0.06f)
            canvas.drawCircle(cx, cy, labelRadius * 1.06f, ring)
            canvas.drawCircle(cx, cy, labelRadius, label)
            logo?.let { bitmap ->
                val half = labelRadius * 0.62f
                val ratio = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
                dest.set(cx - half, cy - half * ratio, cx + half, cy + half * ratio)
                picture.alpha = (logoAlpha * 255).toInt()
                if (picture.alpha > 0) canvas.drawBitmap(bitmap, null, dest, picture)
            }
        }
    }

    /** Ferme le lecteur en notant pourquoi (fil d'événements du rapport de plantage). */
    private fun close(why: String) {
        CrashLog.event("Lecteur fermé : $why")
        finish()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.wido_return, R.anim.wido_close)
    }

    private fun hideOverlay() {
        if (!overlayVisible) return
        overlayVisible = false
        overlayAnimator?.cancel()
        overlay.animate().alpha(0f).setDuration(250).withEndAction {
            if (overlay.parent != null) root.removeView(overlay)
        }.start()
        // Première utilisation : rappel des commandes.
        if (tv && !settings.helpSeen) {
            settings.helpSeen = true
            root.postDelayed({ showHelp() }, 500)
        }
    }

    // --- Cycle de vie -----------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        if (backgrounded) {
            backgrounded = false
            BackgroundPlayService.stop(this)
        }
        webView?.onResume()
        root.removeCallbacks(progressPoll)
        root.postDelayed(progressPoll, PROGRESS_POLL_MS)
        (application as WiDoApp).remote.attach(this)
    }

    override fun onPause() {
        CrashLog.trace("site : en pause (${CrashLog.memoryLine(this)})")
        captureFrame(force = true)
        (application as WiDoApp).remote.detach(this)
        if (tv) (application as WiDoApp).link.nowPlaying.value = null
        // Téléphone, vidéo en cours, réglage actif : elle continue en arrière-plan (notification de lecture).
        val playing = mediaState.value?.let { !it.paused && it.duration > 0 } == true
        if (!tv && settings.backgroundPlay && playing && !isChangingConfigurations && !isFinishing) {
            backgrounded = true
            BackgroundBridge.controller = { command -> backgroundCommand(command) }
            mediaState.value?.let { BackgroundBridge.update(playbackInfo(it)) }
            BackgroundPlayService.start(this)
        } else {
            root.removeCallbacks(progressPoll)
            webView?.onPause()
        }
        saveSeen()
        rememberPage()
        super.onPause()
    }

    /** La lecture continue alors que l'appli n'est plus à l'écran. */
    private var backgrounded = false

    private fun backgroundCommand(command: String) {
        val seek = settings.seekSeconds.toDouble()
        when (command) {
            "rewind" -> media("seek", -seek)
            "forward" -> media("seek", seek)
            else -> media(command)
        }
        root.postDelayed({ passivePoll = true; media("state") }, 400)
    }

    private fun playbackInfo(state: MediaState) = PlaybackInfo(
        title = webView?.title?.takeIf { it.isNotBlank() } ?: site?.name.orEmpty(),
        subtitle = site?.name.orEmpty(),
        position = state.time,
        duration = state.duration,
        playing = !state.paused,
        art = osdThumb,
    )

    /**
     * Retient la page affichée (et la position d'une vidéo YouTube) pour la prochaine ouverture du site,
     * et la vidéo en cours pour la rangée « Reprendre » de l'accueil.
     */
    private fun rememberPage() {
        val current = site ?: return
        ResumeUrl.toRemember(current, webView?.url, mediaState.value?.time)?.let { settings.setLastUrlFor(current.id, it) }
        rememberProgress()
    }

    /** Page de la dernière position connue de la vidéo, et son titre à ce moment-là. */
    private var mediaPage: String? = null
    private var mediaTitle: String? = null

    /**
     * Enregistre la position dans Reprendre, sous l'adresse de la page où la vidéo jouait. Une page
     * hors des domaines du site (épisode servi par un autre domaine) est gardée aussi : Reprendre la
     * rouvrira avec un site éphémère plutôt que de revenir à l'épisode précédent.
     */
    private fun rememberProgress() {
        val current = site ?: return
        val state = mediaState.value ?: return
        val url = mediaPage ?: return
        if (state.duration <= 0) return
        val page = ResumeUrl.toRemember(current, url)
            ?: url.takeIf { (Hosts.schemeOf(it) == "https" || (Hosts.schemeOf(it) == "http" && current.allowInsecure)) && Hosts.of(it) != null }
            ?: return
        (application as WiDoApp).repository.recordProgress(current.id, page, mediaTitle ?: webView?.title, pageImage, state.time, state.duration)
    }

    // --- Télécommande sur téléphone ----------------------------------------------------------

    override fun onRemote(command: RemoteCommand) {
        when (command) {
            is RemoteCommand.Key -> when (command.key) {
                RemoteKey.HOME -> close("télécommande du téléphone : Accueil")
                else -> pressKey(keyCodeOf(command.key))
            }
            is RemoteCommand.Move -> {
                if (panelView != null || webView == null) return
                if (!cursorMode) toggleCursor()
                val density = resources.displayMetrics.density
                cursor.moveBy(command.dx * density * settings.cursorSpeed, command.dy * density * settings.cursorSpeed)
                cursor.wake()
            }
            RemoteCommand.Tap -> {
                if (panelView == null && !cursorMode) toggleCursor()
                pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
            }
            is RemoteCommand.Seek -> {
                if (mediaState.value == null) {
                    pendingSeek = command.position
                    startResumeSeek()
                } else {
                    mediaJson(JSONObject().put("cmd", "seekTo").put("to", command.position).toString())
                    root.postDelayed({ passivePoll = true; media("state") }, 400)
                }
            }
            is RemoteCommand.Scroll -> webView?.scrollBy(0, (command.dy * resources.displayMetrics.density).toInt())
            is RemoteCommand.Type -> {
                typeText(command.text, command.submit)
                osd.showText(getString(R.string.osd_text_sent))
            }
            is RemoteCommand.Open -> {
                val current = site
                val own = current?.let { ResumeUrl.toRemember(it, command.url) }
                if (own != null) {
                    webView?.loadUrl(own)
                } else {
                    startActivity(intent(this, ConfigRepository.LINK_SITE_ID, command.url))
                    close("lien du téléphone vers un autre site")
                }
                osd.showText(getString(R.string.osd_link_opened))
            }
        }
    }

    /** Une touche de télécommande, comme si on l'avait pressée : elle passe par toute la logique du lecteur. */
    private fun pressKey(code: Int) {
        val now = SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        dispatchKeyEvent(KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
    }

    // --- Vidéo de la page : métadonnées, reprise, lecteur natif ------------------------------

    /** Image de la page (`og:image`), pour la vignette de « Reprendre ». */
    private var pageImage: String? = null

    private fun captureMeta() {
        webView?.evaluateJavascript(META_SCRIPT) { raw ->
            pageImage = unquote(raw)?.takeIf { it.startsWith("https://") }
            refreshOsdInfo()
        }
    }

    /** L'état du lecteur de la page est relevé de temps en temps, sans rien afficher : la progression reste à jour. */
    private val progressPoll = object : Runnable {
        override fun run() {
            if (!osd.isShowing && panelView == null) {
                passivePoll = true
                media("state")
            }
            // Un téléphone suit la lecture (« En cours sur la TV ») : la position est relevée plus souvent.
            root.postDelayed(this, if (tv && (application as WiDoApp).link.followed) FOLLOW_POLL_MS else PROGRESS_POLL_MS)
        }
    }

    /** Position (s) où reprendre la vidéo, demandée par « Reprendre » ; 0 = rien à faire. */
    private var pendingSeek = 0.0
    private var resumeAttempts = 0

    /** Interroge le lecteur jusqu'à ce que la vidéo soit prête, puis y saute (voir [applyPendingSeek]). */
    private val resumeSeek = object : Runnable {
        override fun run() {
            if ((pendingSeek <= 0 && !seekGuarded()) || resumeAttempts++ > RESUME_ATTEMPTS) return
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

    // --- Page qui ne s'ouvre pas ------------------------------------------------------------

    /** Écran WiDo à la place de la page d'erreur du navigateur : ce qui se passe, et quoi faire. */
    /** Ce que fait Retour sur l'écran d'erreur affiché (null : pas d'écran d'erreur). */
    private var errorBack: (() -> Unit)? = null

    private fun showPageError(code: Int, failed: String) {
        hideOverlay()
        hidePanel()
        val current = site
        val back = {
            hidePanel()
            val wv = webView
            if (wv != null && wv.canGoBackOrForward(-2)) wv.goBackOrForward(-2) else goHome()
        }
        val host = Hosts.of(failed).orEmpty().removePrefix("www.")
        val online = runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
        val (title, body) = when {
            !online -> getString(R.string.err_offline_title) to getString(if (tv) R.string.err_offline_body_tv else R.string.err_offline_body)
            code == android.webkit.WebViewClient.ERROR_HOST_LOOKUP -> getString(R.string.err_dns_title) to getString(R.string.err_dns_body, host)
            code == android.webkit.WebViewClient.ERROR_TIMEOUT || code == android.webkit.WebViewClient.ERROR_CONNECT ->
                getString(R.string.err_timeout_title) to getString(R.string.err_timeout_body, host)
            else -> getString(R.string.err_generic_title) to getString(R.string.err_generic_body, host)
        }
        val canEdit = current != null && dev.wido.core.LocalSites.isLocalId(current.id) && online && code == android.webkit.WebViewClient.ERROR_HOST_LOOKUP
        showOverlay {
            dev.wido.tv.ui.PageError(
                title = title,
                body = body,
                retry = getString(R.string.err_retry),
                back = getString(R.string.err_back),
                edit = if (canEdit) getString(R.string.err_edit) else null,
                onRetry = {
                    hidePanel()
                    webView?.loadUrl(failed)
                },
                onBack = back,
                onEdit = {
                    hidePanel()
                    current?.let { startActivity(MainActivity.editSite(this, it.id)) }
                    close("page d'erreur : Modifier l'adresse")
                },
            )
        }
    }

    // --- Moteur de la page arrêté ----------------------------------------------------------

    /**
     * Le moteur de la page s'est arrêté (le lecteur du site a planté, ou la TV manquait de mémoire). La
     * WebView est perdue : on la retire, la position est gardée, et WiDo propose de réessayer au lieu de
     * se fermer d'un coup.
     */
    private var rendererLost = false

    private fun onRendererGone() {
        rendererLost = true
        val current = site
        CrashLog.event("Page arrêtée (moteur du navigateur) : ${current?.name.orEmpty()} ${Hosts.of(webView?.url.orEmpty()).orEmpty()}")
        val dead = webView
        val url = dead?.url
        val position = currentPosition()
        rememberPage()
        webView = null
        dead?.let {
            root.removeView(it)
            runCatching { it.destroy() }
        }
        WebViewPool.clear()
        // Plein écran vidéo du site : sa vue part avec la page.
        customView?.let { root.removeView(it) }
        customView = null
        customCallback = null
        cinema = false
        hidePauseScreen()
        root.removeCallbacks(autoStartPoll)
        root.removeCallbacks(progressPoll)
        mediaState.value = null
        panelView?.let { hidePanel() }
        showOverlay {
            dev.wido.tv.ui.PageError(
                title = getString(R.string.err_crash_title),
                body = getString(R.string.err_crash_body),
                retry = getString(R.string.err_retry),
                back = getString(R.string.err_back),
                edit = null,
                onRetry = {
                    // Une page toute neuve, à la même seconde, lecture lancée à la main (OK).
                    if (current != null) startActivity(intent(this, current.id, url, position))
                    close("page arrêtée : Réessayer")
                },
                onBack = { goHome() },
                onEdit = {},
            )
        }
    }

    /** Retour à l'accueil de WiDo (sans quitter l'appli si le lecteur a été ouvert d'ailleurs). */
    private fun goHome() {
        if (isTaskRoot) startActivity(Intent(this, MainActivity::class.java))
        close("retour à l'accueil (page d'erreur ou arrêtée)")
    }

    // --- Écran de pause (TV) ------------------------------------------------------------------

    private var pauseView: View? = null
    private var swallowUp = 0
    private val showPause = Runnable { showPauseScreen() }

    /** Vidéo en pause depuis quelques secondes : la page s'assombrit, titre et temps restant en bas. */
    private fun schedulePauseScreen(state: MediaState) {
        root.removeCallbacks(showPause)
        if (!tv) return
        if (state.paused && state.duration > 0) root.postDelayed(showPause, PAUSE_SCREEN_MS) else hidePauseScreen()
    }

    /** Dernière touche de la télécommande (l'écran de pause n'interrompt pas quelqu'un qui navigue). */
    private var lastInputAt = 0L

    private fun showPauseScreen() {
        val state = mediaState.value ?: return
        if (state.paused) CrashLog.trace("site : écran de pause demandé")
        if (!state.paused || pauseView != null || panelView != null || helpView != null || picking) return
        if (SystemClock.uptimeMillis() - lastInputAt < PAUSE_IDLE_MS) {
            // Quelqu'un vient d'utiliser la télécommande (choisir un épisode…) : on attend le calme.
            root.removeCallbacks(showPause)
            root.postDelayed(showPause, 1_500)
            return
        }
        val density = resources.displayMetrics.density
        val scale = settings.uiScale
        val left = (state.duration - state.time).coerceAtLeast(0.0)
        val end = android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(System.currentTimeMillis() + (left * 1000).toLong()))
        val title = TextView(this).apply {
            text = webView?.title?.takeIf { it.isNotBlank() && it != webView?.url } ?: site?.name.orEmpty()
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 34f * scale
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            letterSpacing = -0.02f
            useJakarta(800)
        }
        val detail = TextView(this).apply {
            text = listOfNotNull(site?.name, getString(R.string.osd_remaining, dev.wido.core.PlayerFormat.time(left), end)).joinToString("  ·  ")
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 17f * scale
            useJakarta(600)
            setPadding(0, (10 * density).toInt(), 0, 0)
        }
        val hint = TextView(this).apply {
            setText(R.string.pause_hint)
            setTextColor(0x99FFFFFF.toInt())
            textSize = 14f * scale
            useJakarta(600)
            setPadding(0, (18 * density).toInt(), 0, 0)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(detail)
            addView(hint)
        }
        val view = FrameLayout(this).apply {
            background = PauseBackdrop.drawable(resources)
            val pad = (56 * density).toInt()
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(pad, 0, pad, (48 * density).toInt())
            })
            alpha = 0f
            isFocusable = false
        }
        pauseView = view
        root.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        view.bringToFront()
        view.animate().alpha(1f).setDuration(450).start()
    }

    private fun hidePauseScreen() {
        root.removeCallbacks(showPause)
        val view = pauseView ?: return
        pauseView = null
        view.animate().alpha(0f).setDuration(200).withEndAction { root.removeView(view) }.start()
    }

    // --- Lecture et plein écran automatiques (Reprendre, envoyé depuis le téléphone) ---------

    /** La vidéo doit démarrer seule, puis passer en plein écran, dès qu'elle est prête. */
    private var autoStart = false
    private var autoAttempts = 0
    /** Mode cinéma : la vidéo remplit l'écran (quand le site ne peut pas ouvrir son plein écran). */
    private var cinema = false

    private val autoStartPoll = object : Runnable {
        override fun run() {
            if (!autoStart || autoAttempts++ > AUTO_ATTEMPTS) return
            passivePoll = true
            media("state")
            root.postDelayed(this, AUTO_POLL_MS)
        }
    }

    private fun startAutoPlay() {
        if (!autoStart) return
        autoAttempts = 0
        root.removeCallbacks(autoStartPoll)
        root.postDelayed(autoStartPoll, AUTO_POLL_MS)
    }

    private fun applyAutoStart(state: MediaState) {
        if (!autoStart || state.duration <= 0) return
        autoStart = false
        root.removeCallbacks(autoStartPoll)
        if (state.paused) media("play")
        if (settings.autoFullscreen && customView == null) root.postDelayed({ enterFullscreen() }, 700)
    }

    /**
     * Plein écran : le bouton plein écran du site, touché pour de vrai (son propre lecteur, ses
     * sous-titres) ; s'il n'y en a pas de visible, le mode cinéma.
     */
    private fun enterFullscreen() {
        val wv = webView ?: return
        if (customView != null || cinema) return
        wv.evaluateJavascript(FULLSCREEN_BUTTON_SCRIPT) { raw ->
            val target = parseSnapTarget(raw)
            if (target != null) {
                tapAt(target.first * wv.width, target.second * wv.height)
                root.postDelayed({ if (customView == null && !cinema) setCinema(true) }, 1_500)
            } else {
                setCinema(true)
            }
        }
    }

    private fun tapAt(x: Float, y: Float) {
        val wv = webView ?: return
        val down = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val ev = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            wv.dispatchTouchEvent(ev)
            ev.recycle()
        }
    }

    private fun setCinema(on: Boolean) {
        cinema = on
        mediaJson(JSONObject().put("cmd", "cinema").put("on", on).toString())
        if (on) osd.showText(getString(R.string.osd_cinema))
    }

    private fun applyPendingSeek(state: MediaState) {
        guardSeek(state)
        if (pendingSeek <= 0 || state.duration <= 0) return
        val target = pendingSeek
        // Vidéo plus courte que la position : une pub d'avant-film. On attend le vrai film.
        if (state.duration + 5 < target) return
        pendingSeek = 0.0
        if (kotlin.math.abs(state.time - target) > 5) media("seek", target - state.time)
        // Bien des lecteurs se réinitialisent juste après (chargement du flux, plein écran, fin de pub) et
        // repartent du début : pendant quelques secondes, on vérifie et on remet la bonne position.
        guardTarget = target
        guardUntil = SystemClock.elapsedRealtime() + SEEK_GUARD_MS
        guardRetries = 0
        resumeAttempts = 0
        root.removeCallbacks(resumeSeek)
        root.postDelayed(resumeSeek, RESUME_POLL_MS)
    }

    private var guardTarget = 0.0
    private var guardUntil = 0L
    private var guardRetries = 0

    private fun seekGuarded() = SystemClock.elapsedRealtime() < guardUntil

    /** La vidéo est repartie du début juste après la reprise : on la replace (3 fois au plus). */
    private fun guardSeek(state: MediaState) {
        if (!seekGuarded() || state.duration <= 0 || state.duration + 5 < guardTarget) return
        if (state.time < guardTarget - 15 && guardRetries < 3) {
            guardRetries++
            media("seek", guardTarget - state.time)
        }
    }

    /** L'utilisateur avance ou recule lui-même : on ne le contredit pas. */
    private fun cancelSeekGuard() {
        guardUntil = 0L
    }

    private var streamHinted = false

    /** Un flux lisible par le lecteur natif est apparu : on le signale une fois par page. */
    private fun onStreamFound() {
        if (streamHinted) return
        streamHinted = true
        // Peu de mémoire : le lecteur natif consomme bien moins que la page, il évite les fermetures.
        val app = application as WiDoApp
        if (settings.autoNative && maybeAutoNative()) return
        osd.showText(getString(if (app.lowMemory) R.string.osd_native_found_low else R.string.osd_native_found))
    }

    /** « Lire dans WiDo » : le flux repéré s'ouvre dans le lecteur natif, la page se met en pause. */
    /** Page déjà confiée au lecteur léger automatiquement (une seule tentative : pas de boucle si le flux ne passe pas). */
    private var autoNativeTried: String? = null

    /** Page quittée pour le lecteur léger (appareil à peu de mémoire) : elle est rechargée au retour. */
    private var nativeReturnUrl: String? = null
    private var nativeStartPosition = 0.0

    /**
     * Film en cours de lecture dans la page, flux repéré : on passe au lecteur léger, sans que l'utilisateur
     * ait à ouvrir le menu. Renvoie vrai si le passage est lancé. Pas pour une pub (vidéo courte).
     */
    private fun maybeAutoNative(): Boolean {
        if (!settings.autoNative || nativeReturnUrl != null || client?.stream == null) return false
        val state = mediaState.value ?: return false
        val page = webView?.url ?: return false
        if (state.paused || state.duration < AUTO_NATIVE_MIN_SECONDS || page == autoNativeTried) return false
        autoNativeTried = page
        osd.showText(getString(R.string.osd_auto_native), OsdView.Kind.Info)
        root.postDelayed({
            val now = mediaState.value
            if (!isFinishing && nativeReturnUrl == null && now != null && !now.paused && webView?.url == page) openNative()
        }, 1_800)
        return true
    }

    private fun openNative() {
        CrashLog.trace("lecteur natif : ouverture demandée")
        val current = site ?: return
        val wv = webView ?: return
        val stream = client?.stream
        if (stream == null) {
            osd.showText(getString(R.string.osd_native_none), OsdView.Kind.Warning)
            return
        }
        // Le lecteur natif part de la seconde où en était la page.
        val start = currentPosition()
        media("pause")
        val cookies = runCatching { android.webkit.CookieManager.getInstance().getCookie(stream.url) }.getOrNull()
        val lowMemory = (application as WiDoApp).lowMemory
        val page = wv.url
        nativeLauncher.launch(
            NativePlayerActivity.intent(
                this, stream, current.id, ResumeUrl.toRemember(current, wv.url), wv.title, pageImage, wv.url, cookies,
                wv.settings.userAgentString, start,
            ),
        )
        if (lowMemory && page != null) {
            // La page (et son lecteur) pèse plus de 300 Mo : on la vide pendant le film, on la rechargera au retour.
            nativeReturnUrl = page
            nativeStartPosition = start
            root.postDelayed({ if (nativeReturnUrl != null) webView?.loadUrl("about:blank") }, 600)
        }
    }

    /** Retour du lecteur natif : la vidéo de la page se cale sur la seconde où on l'a quitté (une seule entrée dans Reprendre). */
    private val nativeLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val position = result.data?.getDoubleExtra(NativePlayerActivity.RESULT_POSITION, -1.0) ?: -1.0
        val back = nativeReturnUrl
        if (back != null) {
            // La page avait été vidée : on la recharge à la seconde où le film s'est arrêté, la lecture reste à toi (OK).
            nativeReturnUrl = null
            pendingSeek = if (position >= 0) position else nativeStartPosition
            autoStart = false
            webView?.loadUrl(back)
            startResumeSeek()
            // Pas de surprise devant une page qui ne joue pas : on dit où elle en est et ce qu'il reste à faire.
            val resumed = pendingSeek
            root.postDelayed({ if (!isFinishing) osd.showText(getString(R.string.osd_page_reloaded, dev.wido.core.PlayerFormat.time(resumed))) }, 1_500)
            return@registerForActivityResult
        }
        if (position < 0) return@registerForActivityResult
        mediaState.value?.let {
            mediaState.value = it.copy(time = position, paused = true)
            mediaAt = SystemClock.elapsedRealtime()
        }
        mediaJson(JSONObject().put("cmd", "seekTo").put("to", position).toString())
        root.postDelayed({ passivePoll = true; media("state") }, 600)
    }

    // --- Image de la vidéo pour « Reprendre » ---

    private var lastFrameAt = 0L

    /**
     * Capture l'image de la vidéo (zone du lecteur dans la page, ou tout l'écran en plein écran) pour la
     * vignette de « Reprendre ». Toutes les 45 s pendant la lecture, et en quittant la page.
     */
    private fun captureFrame(force: Boolean = false) {
        val current = site ?: return
        val wv = webView ?: return
        val state = mediaState.value ?: return
        if (state.duration <= 0 || panelView != null || helpView != null) return
        val now = SystemClock.elapsedRealtime()
        if (!force && (now - lastFrameAt < FRAME_EVERY_MS || osd.isShowing)) return
        val page = ResumeUrl.toRemember(current, wv.url) ?: return
        lastFrameAt = now
        // Les sites qui changent de vidéo sans recharger la page mettent aussi leur image à jour.
        captureMeta()
        if (customView != null) {
            copyWindow(android.graphics.Rect(0, 0, root.width, root.height), page)
            return
        }
        val loc = IntArray(2)
        wv.getLocationInWindow(loc)
        val full = android.graphics.Rect(loc[0], loc[1], loc[0] + wv.width, loc[1] + wv.height)
        wv.evaluateJavascript(VIDEO_RECT_SCRIPT) { raw ->
            val rect = runCatching {
                val o = JSONObject(unquote(raw) ?: return@runCatching null)
                android.graphics.Rect(
                    loc[0] + (o.getDouble("x") * wv.width).toInt(),
                    loc[1] + (o.getDouble("y") * wv.height).toInt(),
                    loc[0] + (o.getDouble("r") * wv.width).toInt(),
                    loc[1] + (o.getDouble("b") * wv.height).toInt(),
                )
            }.getOrNull()
            copyWindow(rect?.takeIf { it.width() > 80 && it.height() > 45 } ?: full, page)
        }
    }

    /** Copie une zone de la fenêtre (vidéo comprise) dans une petite image, enregistrée hors du fil principal. */
    private fun copyWindow(rect: android.graphics.Rect, page: String) {
        if (rect.width() <= 0 || rect.height() <= 0 || window.decorView.width == 0) return
        val width = minOf(480, rect.width())
        val height = (rect.height() * width.toFloat() / rect.width()).toInt().coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val thumbnails = (application as WiDoApp).thumbnails
        runCatching {
            android.view.PixelCopy.request(window, rect, bitmap, { result ->
                if (result == android.view.PixelCopy.SUCCESS) {
                    kotlin.concurrent.thread {
                        thumbnails.saveFrame(page, bitmap)
                        bitmap.recycle()
                    }
                } else {
                    bitmap.recycle()
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
    }

    /** Heure (horloge système) du dernier état du lecteur, pour estimer la position en cours. */
    private var mediaAt = 0L

    /** Position estimée de la vidéo, maintenant. */
    private fun currentPosition(): Double {
        val state = mediaState.value ?: return 0.0
        val elapsed = if (state.paused) 0.0 else (SystemClock.elapsedRealtime() - mediaAt) / 1000.0
        return (state.time + elapsed).let { if (state.duration > 0) it.coerceAtMost(state.duration) else it }
    }

    /** TV : ce qui passe à l'écran, pour les téléphones liés (« En cours sur la TV »). */
    private fun publishNowPlaying(state: MediaState?) {
        if (!tv) return
        val current = site ?: return
        val url = webView?.url ?: return
        val page = ResumeUrl.toRemember(current, url) ?: url
        (application as WiDoApp).link.nowPlaying.value = NowPlaying(
            siteId = current.id,
            siteName = current.name,
            url = page,
            title = webView?.title.orEmpty(),
            image = pageImage,
            position = state?.time ?: 0.0,
            duration = state?.duration ?: 0.0,
            playing = state != null && !state.paused,
            at = System.currentTimeMillis(),
        )
    }

    /** Téléphone : « Sur la TV ». La page s'ouvre sur la TV choisie, à la même seconde ; ici, la vidéo se met en pause. */
    private fun sendToTv() {
        val app = application as WiDoApp
        val peer = app.link.selectedPeer() ?: return
        val current = site
        val url = webView?.url ?: return
        val page = current?.let { ResumeUrl.toRemember(it, url) } ?: url
        val play = HandoffPlay(page, current?.id, webView?.title, currentPosition())
        osd.showText(getString(R.string.osd_sending_to_tv, peer.name))
        lifecycleScope.launch {
            if (app.link.play(peer, play)) {
                media("pause")
                // La vidéo est sur la TV : le téléphone passe à son onglet TV, pour la piloter.
                startActivity(MainActivity.showTv(this@PlayerActivity))
                close("envoyé sur la TV")
            } else {
                osd.showText(getString(R.string.toast_tv_unreachable, peer.name), OsdView.Kind.Warning)
            }
        }
    }

    /** Téléphone : bouton rond en haut à droite, qui ouvre le menu rapide (il n'y a pas de touche Menu). */
    private fun addMenuButton() {
        val density = resources.displayMetrics.density
        val size = (44 * density).toInt()
        // Trois points dessinés (le caractère « ⋯ » tombe à côté du centre selon la police).
        val button = object : View(this) {
            private val dot = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val r = 2.2f * density
                val gap = 7f * density
                val cy = height / 2f
                val cx = width / 2f
                for (i in -1..1) canvas.drawCircle(cx + i * gap, cy, r, dot)
            }
        }.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x991A1A26.toInt())
                setStroke((1 * density).toInt(), 0x33FFFFFF)
            }
            elevation = 12 * density
            contentDescription = getString(R.string.panel_open_menu)
            setOnClickListener { if (panelView == null) showPanel() else hidePanel() }
        }
        val margin = (14 * density).toInt()
        root.addView(button, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply { setMargins(margin, margin + (8 * density).toInt(), margin, margin) })
        menuButton = button
    }

    /** Requêtes bloquées déjà comptées dans le total du mois. */
    private var blockedCounted = 0

    /** Garde ce que le site a tenté (redirections refusées, domaines tiers) pour proposer des choix dans les réglages du site. */
    private fun saveSeen() {
        val id = site?.id ?: return
        val blocked = client?.blockedRequests?.get() ?: 0
        settings.addBlocked(id, blocked - blockedCounted)
        blockedCounted = blocked
        val (navigations, requests) = client?.drainSeen() ?: return
        (application as WiDoApp).repository.recordSeen(id, navigations, requests)
    }

    override fun onDestroy() {
        if (backgrounded) BackgroundPlayService.stop(this)
        if (BackgroundBridge.controller != null) BackgroundBridge.controller = null
        overlayAnimator?.cancel()
        root.removeCallbacks(sleepWarning)
        root.removeCallbacks(sleepNow)
        root.removeCallbacks(hideFieldCard)
        root.removeCallbacks(progressPoll)
        root.removeCallbacks(resumeSeek)
        root.removeCallbacks(autoStartPoll)
        root.removeCallbacks(showPause)
        adWindow?.close()
        customCallback?.onCustomViewHidden()
        webView?.let {
            root.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        webView = null
        // Fermeture normale : la page n'a pas fait tomber l'appli.
        if (isFinishing) settings.openPage = null else CrashLog.event("Lecteur détruit par Android (sans fermeture demandée)")
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
        /** La vidéo démarre seule et passe en plein écran (Reprendre, envoyé depuis le téléphone). */
        const val EXTRA_AUTOPLAY = "autoplay"
        /** Ouverture depuis l'accueil : position (gauche, haut, droite, bas) et couleur du cercle sélectionné. */
        const val EXTRA_ORIGIN = "origin"
        const val EXTRA_COLOR = "origin_color"
        const val EXTRA_TILE = "origin_tile"
        private const val AUTO_POLL_MS = 800L
        private const val AUTO_ATTEMPTS = 30
        /** Le bouton plein écran visible du lecteur de la page (au premier plan à cet endroit), en fractions de l'écran. */
        private const val FULLSCREEN_BUTTON_SCRIPT = """(function () {
  var re = /(plein[ -]?(é|e)cran|full ?screen)/i;
  var els = document.querySelectorAll('button,[role=button],a,[class*=fullscreen],[class*=Fullscreen]');
  for (var i = 0; i < els.length; i++) {
    var e = els[i];
    var cls = typeof e.className === 'string' ? e.className : '';
    var label = (e.getAttribute('aria-label') || '') + ' ' + (e.getAttribute('title') || '') + ' ' + (e.getAttribute('data-title-no-tooltip') || '') + ' ' + cls;
    if (!re.test(label) || /(exit|quitter|sortir)/i.test(label)) continue;
    var r = e.getBoundingClientRect();
    if (r.width < 8 || r.height < 8) continue;
    var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    if (cx < 0 || cy < 0 || cx > innerWidth || cy > innerHeight) continue;
    var top = document.elementFromPoint(cx, cy);
    if (!top || !(top === e || e.contains(top))) continue;
    return JSON.stringify({ x: cx / innerWidth, y: cy / innerHeight });
  }
  return null;
})()"""
        private const val PROGRESS_POLL_MS = 10_000L
        private const val FOLLOW_POLL_MS = 2_000L
        private const val FRAME_EVERY_MS = 90_000L
        private const val PAUSE_SCREEN_MS = 1_200L
        private const val PAUSE_IDLE_MS = 4_000L
        private const val DOUBLE_BACK_MS = 450L
        private const val EXIT_CONFIRM_MS = 2_500L
        /** En dessous (pub, bande-annonce), on ne quitte pas la page pour le lecteur léger. */
        private const val AUTO_NATIVE_MIN_SECONDS = 600.0
        private const val STATE_URL = "state_url"
        private const val STATE_POSITION = "state_position"
        /** Étiquette du vinyle de l'accueil, rapportée au rayon du disque (50 dp sur 86 dp au focus). */
        private const val LABEL_RATIO = 0.58f
        private const val KEEP_VISIBLE_SCRIPT = """(function () {
  try {
    Object.defineProperty(document, 'hidden', { get: function () { return false; } });
    Object.defineProperty(document, 'visibilityState', { get: function () { return 'visible'; } });
    Object.defineProperty(document, 'webkitHidden', { get: function () { return false; } });
  } catch (e) {}
  ['visibilitychange', 'webkitvisibilitychange', 'blur'].forEach(function (t) {
    window.addEventListener(t, function (e) { e.stopImmediatePropagation(); }, true);
    document.addEventListener(t, function (e) { e.stopImmediatePropagation(); }, true);
  });
})()"""
        /**
         * Trouve (et clique si `go`) le lien ou bouton « épisode suivant » de la page : par son texte,
         * son libellé d'accessibilité ou son titre. Renvoie vrai s'il existe.
         */
        private const val NEXT_SCRIPT = """function (go) {
  var re = /((é|e)pisode suivant|next episode|prochain (é|e)pisode|next up)/i;
  var els = document.querySelectorAll('a,button,[role=button]');
  for (var i = 0; i < els.length; i++) {
    var e = els[i];
    var t = ((e.getAttribute('aria-label') || '') + ' ' + (e.getAttribute('title') || '') + ' ' + (e.textContent || '')).replace(/\s+/g, ' ');
    if (t.length > 120 || !re.test(t)) continue;
    var r = e.getBoundingClientRect();
    if (r.width === 0 && r.height === 0 && !e.href) continue;
    if (go) e.click();
    return true;
  }
  return false;
}"""
        /** La plus grande vidéo visible (sinon la plus grande iframe, où se cache souvent le lecteur), en fractions de l'écran. */
        private const val VIDEO_RECT_SCRIPT = """(function () {
  var best = null, area = 0;
  function visible(r) { return Math.max(0, Math.min(r.right, innerWidth) - Math.max(r.left, 0)) * Math.max(0, Math.min(r.bottom, innerHeight) - Math.max(r.top, 0)); }
  document.querySelectorAll('video').forEach(function (v) { var r = v.getBoundingClientRect(), a = visible(r); if (a > area) { area = a; best = r; } });
  if (!best) document.querySelectorAll('iframe').forEach(function (f) { var r = f.getBoundingClientRect(), a = visible(r); if (a > area) { area = a; best = r; } });
  if (!best || area < 20000) return null;
  return JSON.stringify({ x: Math.max(0, best.left) / innerWidth, y: Math.max(0, best.top) / innerHeight, r: Math.min(innerWidth, best.right) / innerWidth, b: Math.min(innerHeight, best.bottom) / innerHeight });
})()"""
        private const val RESUME_POLL_MS = 1_500L
        private const val RESUME_ATTEMPTS = 40
        private const val SEEK_GUARD_MS = 20_000L
        /** Image de la page : og:image, twitter:image, image_src, thumbnailUrl, puis l'affiche du lecteur (`poster`). */
        private const val META_SCRIPT = """(function () {
  function abs(u) { try { return u ? new URL(u, location.href).href : null; } catch (e) { return null; } }
  var m = document.querySelector('meta[property="og:image"],meta[property="og:image:url"],meta[name="twitter:image"],meta[itemprop="thumbnailUrl"],link[itemprop="thumbnailUrl"],link[rel="image_src"]');
  var u = m ? (m.content || m.href) : null;
  if (!u) document.querySelectorAll('script[type="application/ld+json"]').forEach(function (s) {
    if (u) return;
    try { var j = JSON.parse(s.textContent); var t = (Array.isArray(j) ? j[0] : j).thumbnailUrl; u = Array.isArray(t) ? t[0] : t; } catch (e) {}
  });
  if (!u) { var v = document.querySelector('video[poster]'); u = v ? v.getAttribute('poster') : null; }
  return abs(u);
})()"""
        /** Valeur spéciale de l'extra `site` : rouvre le dernier site ouvert. */
        const val LAST_SITE = "last"
        private const val FIELD_CARD_GRACE_MS = 1_500L
        private val SLEEP_STEPS = listOf(0, 30, 60, 90, 120)
        private const val BRIDGE_NAME = "WiDoBridge"
        private const val ACCENT = 0xFFFFFFFF.toInt()
        private const val OVERLAY_TIMEOUT_MS = 20_000L
        private const val HELP_TIMEOUT_MS = 15_000L
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
        fun intent(context: Context, siteId: String, url: String? = null, positionSeconds: Double = 0.0, autoplay: Boolean = false): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_SITE, siteId).apply {
                if (url != null) putExtra(EXTRA_URL, url)
                if (positionSeconds > 0) putExtra(EXTRA_POSITION, positionSeconds)
                if (autoplay) putExtra(EXTRA_AUTOPLAY, true)
            }
    }
}

/** Code de touche Android d'une touche de la télécommande sur téléphone. */
fun keyCodeOf(key: RemoteKey): Int = when (key) {
    RemoteKey.UP -> KeyEvent.KEYCODE_DPAD_UP
    RemoteKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
    RemoteKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
    RemoteKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
    RemoteKey.OK -> KeyEvent.KEYCODE_DPAD_CENTER
    RemoteKey.BACK -> KeyEvent.KEYCODE_BACK
    RemoteKey.HOME -> KeyEvent.KEYCODE_HOME
    RemoteKey.MENU -> KeyEvent.KEYCODE_MENU
    RemoteKey.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    RemoteKey.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
    RemoteKey.FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
    RemoteKey.VOLUME_UP -> KeyEvent.KEYCODE_VOLUME_UP
    RemoteKey.VOLUME_DOWN -> KeyEvent.KEYCODE_VOLUME_DOWN
    RemoteKey.MUTE -> KeyEvent.KEYCODE_VOLUME_MUTE
    RemoteKey.DEL -> KeyEvent.KEYCODE_DEL
    RemoteKey.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
    RemoteKey.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
}
