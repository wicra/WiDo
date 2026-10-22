package dev.wido.tv

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import dev.wido.tv.ui.QuickAction
import dev.wido.tv.ui.Glyph
import android.os.SystemClock
import android.widget.TextView
import android.media.AudioManager
import android.util.TypedValue
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import dev.wido.core.MediaStream
import dev.wido.core.NowPlaying
import dev.wido.core.RemoteCommand
import dev.wido.core.RemoteKey

/**
 * « Lire dans WiDo » : le flux vidéo repéré dans la page, lu par le lecteur natif d'Android (Media3).
 * Décodage matériel, avance instantanée, vitesse et sous-titres dans les réglages du lecteur, grands
 * sous-titres lisibles de loin, cadrage au choix (touche Menu), reprise à la seconde près.
 *
 * Le flux est demandé avec les mêmes en-têtes que la page (Referer, Cookie, agent utilisateur) : sans
 * eux, beaucoup de serveurs vidéo refusent la lecture.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class NativePlayerActivity : ComponentActivity(), RemoteTarget {
    private lateinit var player: ExoPlayer
    private lateinit var view: PlayerView
    private lateinit var osd: OsdView
    private lateinit var settings: Settings
    private var resizeIndex = 0
    private var positionKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = (application as WiDoApp).settings
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null) {
            finish()
            return
        }
        val kind = runCatching { MediaStream.Kind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }.getOrDefault(MediaStream.Kind.PROGRESSIVE)
        val referer = intent.getStringExtra(EXTRA_REFERER)
        positionKey = intent.getStringExtra(EXTRA_PAGE) ?: url

        val headers = buildMap {
            referer?.let {
                put("Referer", it)
                dev.wido.core.Hosts.schemeOf(it)?.let { scheme -> dev.wido.core.Hosts.of(it)?.let { host -> put("Origin", "$scheme://$host") } }
            }
            intent.getStringExtra(EXTRA_COOKIES)?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
        }
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
            .apply { intent.getStringExtra(EXTRA_USER_AGENT)?.let { setUserAgent(it) } }

        val lowMemory = (application as WiDoApp).lowMemory
        // Un décodeur matériel qui refuse le flux : le suivant (logiciel au besoin) prend le relais.
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        trackSelector = DefaultTrackSelector(this).apply {
            // Clé d'1 Go : pas de 4K (le décodage et ses tampons ne tiennent pas avec le reste).
            if (lowMemory) setParameters(buildUponParameters().setMaxVideoSize(1920, 1080))
        }
        player = ExoPlayer.Builder(this, renderers)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .apply { if (lowMemory) setLoadControl(lightLoadControl()) }
            .setSeekBackIncrementMs(settings.seekSeconds * 1000L)
            .setSeekForwardIncrementMs(settings.seekSeconds * 1000L)
            .build()
        player.addAnalyticsListener(diagnostics)

        view = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            this.player = this@NativePlayerActivity.player
            keepScreenOn = true
            // Flux qui se recharge : la roue tourne, plutôt qu'une image arrêtée sans explication.
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setShowSubtitleButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            controllerShowTimeoutMs = settings.osdSeconds * 1000 + 1000
            resizeMode = RESIZE_MODES[resizeIndex]
            // Sous-titres lisibles à 3 mètres : grands, sur fond noir.
            subtitleView?.apply {
                setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                setStyle(CaptionStyleCompat(Color.WHITE, 0xB3000000.toInt(), Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, Color.BLACK, null))
                setBottomPaddingFraction(0.06f)
            }
        }
        // Les messages (cadrage…) dans la pastille de verre de WiDo, par-dessus l'image.
        val app = application as WiDoApp
        osd = OsdView(this, compact = !app.isTv, scale = if (app.isTv) settings.uiScale else 1f).apply { hideSeconds = settings.osdSeconds }
        // « ← Site » : visible avec la barre de lecture, atteint par la flèche du haut ; revient à la page à la même seconde.
        val density = resources.displayMetrics.density
        val backButton = TextView(this).apply {
            text = getString(R.string.native_back_site)
            textSize = 16f * (if (app.isTv) settings.uiScale else 1f)
            setTextColor(Color.WHITE)
            setPadding((18 * density).toInt(), (10 * density).toInt(), (18 * density).toInt(), (10 * density).toInt())
            setBackgroundColor(0x99000000.toInt())
            isFocusable = true
            visibility = android.view.View.GONE
            setOnClickListener { finish() }
            setOnFocusChangeListener { v, focused ->
                (v as TextView).setTextColor(if (focused) 0xFF0B0B12.toInt() else Color.WHITE)
                v.setBackgroundColor(if (focused) Color.WHITE else 0x99000000.toInt())
            }
        }
        view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> backButton.visibility = visibility })
        val frame = FrameLayout(this).apply {
            addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(osd, osd.layoutParamsForRoot())
            addView(
                backButton,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP or android.view.Gravity.START)
                    .apply { setMargins((32 * density).toInt(), (28 * density).toInt(), 0, 0) },
            )
        }
        rootFrame = frame
        setContentView(frame)
        WindowInsetsControllerCompat(window, view).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val mime = when (kind) {
            MediaStream.Kind.HLS -> MimeTypes.APPLICATION_M3U8
            MediaStream.Kind.DASH -> MimeTypes.APPLICATION_MPD
            MediaStream.Kind.PROGRESSIVE -> null
        }
        val item = MediaItem.Builder().setUri(url).apply { if (mime != null) setMimeType(mime) }.build()
        player.setMediaItem(item)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                CrashLog.trace("natif : erreur ${error.errorCodeName} (${CrashLog.memoryLine(this@NativePlayerActivity)})")
                // Coupure du réseau, décodeur qui cale, direct trop en retard : on relance à la même seconde (2 fois au plus).
                if (error.errorCode in RETRYABLE && retries < MAX_RETRIES) {
                    retries++
                    CrashLog.event("Lecteur léger : ${error.errorCodeName}, relance n°$retries")
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) player.seekToDefaultPosition()
                    player.prepare()
                    osd.showText(getString(R.string.native_retrying), OsdView.Kind.Warning)
                    return
                }
                CrashLog.event("Lecteur léger : échec ${error.errorCodeName}")
                failed = true
                Toast.makeText(this@NativePlayerActivity, R.string.native_failed, Toast.LENGTH_LONG).show()
                finish()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) CrashLog.trace("natif : image ${videoSize.width}×${videoSize.height}")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // La lecture repart normalement : les relances sont de nouveau permises.
                if (isPlaying) retries = 0
                schedulePause()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = schedulePause()
        })
        // La seconde où en était la page ; à défaut, la dernière position retenue dans le lecteur natif.
        val start = (intent.getDoubleExtra(EXTRA_START, 0.0) * 1000).toLong()
        val resume = if (start >= 1_000) start else settings.nativePositionFor(positionKey).takeIf { it > 10_000 } ?: 0L
        player.prepare()
        if (resume > 0) player.seekTo(resume)
        player.playWhenReady = true
        // Le rappel des touches, une fois, juste après le début.
        view.postDelayed({ if (!isFinishing) osd.showText(getString(R.string.native_hint)) }, 1_200)
    }

    private lateinit var trackSelector: DefaultTrackSelector
    private var retries = 0
    /** Le flux n'a pas pu être lu : la page ne doit pas renvoyer aussitôt vers le lecteur léger. */
    private var failed = false

    /**
     * Tampons réduits pour les clés d'1 Go : 15 à 30 s d'avance et 24 Mo au plus (au lieu de 50 s, et jusqu'à
     * plus de 100 Mo pour un flux 1080p). C'est cette mémoire qui manquait au décodeur : l'image se figeait.
     */
    private fun lightLoadControl() = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 30_000, 2_000, 4_000)
        .setTargetBufferBytes(24 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()

    /** Ce que le journal détaillé retient du décodage : le décodeur choisi, les images perdues. */
    private val diagnostics = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            CrashLog.trace("natif : décodeur vidéo $decoderName")
        }

        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            CrashLog.trace("natif : $droppedFrames images sautées en ${elapsedMs / 1000} s")
        }
    }

    // --- Image figée alors que le son continue ------------------------------------------------

    /**
     * Toutes les secondes pendant la lecture : si plus aucune image n'est affichée alors que la position avance
     * (le son continue), l'image s'est figée. On vide le décodeur (même seconde), on libère la page laissée
     * derrière, et si cela recommence, on passe à une qualité plus légère quand le flux en propose une.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            checkFrozen()
            view.postDelayed(this, WATCHDOG_MS)
        }
    }

    private var watchedCounters: DecoderCounters? = null
    private var lastRendered = -1
    private var stillSince = 0L
    private var freezes = 0
    private var lastFreezeAt = 0L

    private fun checkFrozen() {
        val counters = player.videoDecoderCounters
        if (counters == null || !player.isPlaying || player.playbackState != Player.STATE_READY) {
            lastRendered = -1
            return
        }
        counters.ensureUpdated()
        val rendered = counters.renderedOutputBufferCount
        val position = player.currentPosition
        if (counters !== watchedCounters || rendered != lastRendered) {
            watchedCounters = counters
            lastRendered = rendered
            stillSince = position
            return
        }
        if (position - stillSince < FROZEN_MS) return
        lastRendered = -1
        onFrozen()
    }

    private fun onFrozen() {
        val now = SystemClock.elapsedRealtime()
        freezes = if (now - lastFreezeAt < FREEZE_WINDOW_MS) freezes + 1 else 1
        lastFreezeAt = now
        CrashLog.event("Lecteur léger : image figée, son continu (n°$freezes) · ${CrashLog.memoryLine(this)}")
        NativeHandoff.requestRelease("image figée")
        val lowered = freezes >= 2 && lowerQuality()
        // Une recherche à la même seconde vide le décodeur : l'image repart.
        player.seekTo(player.currentPosition)
        osd.showText(getString(if (lowered) R.string.native_frozen_lower else R.string.native_frozen), OsdView.Kind.Warning)
    }

    /** Limite la vidéo à la qualité juste en dessous de l'actuelle, si le flux en a une. Vrai si c'est fait. */
    private fun lowerQuality(): Boolean {
        val current = player.videoSize.height.takeIf { it > 0 } ?: return false
        val lower = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group.getTrackFormat(it).height } }
            .filter { it in 1 until current }
            .maxOrNull() ?: return false
        CrashLog.event("Lecteur léger : qualité limitée à ${lower}p")
        trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSize(Int.MAX_VALUE, lower))
        return true
    }

    private val debounce = KeyDebounce()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (tv && debounce.isBounce(event)) return true
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            CrashLog.trace("natif : touche ${KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")}, lecture=${player.isPlaying}, position=${player.currentPosition / 1000} s")
        }
        lastInputAt = SystemClock.uptimeMillis()
        // Écran de pause : OK ou lecture relance ; toute autre touche ne fait que le retirer (le volume reste utilisable).
        if (swallowUp == code && event.action == KeyEvent.ACTION_UP) {
            swallowUp = 0
            return true
        }
        if (pauseView != null && event.action == KeyEvent.ACTION_DOWN) {
            hidePauseScreen()
            when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    swallowUp = code
                    player.play()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> Unit
                else -> {
                    swallowUp = code
                    return true
                }
            }
        }
        // Menu ouvert : ses boutons se parcourent aux flèches ; Retour ou Menu le referment.
        if (panel != null) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
                if (event.action == KeyEvent.ACTION_UP) hidePanel()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        // Menu : les mêmes réglages que dans la page (cadrage, épingler, veille…), sans quitter le film.
        if (code == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) showPanel()
            return true
        }
        // Lecture / pause directe, sans passer par la barre.
        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && event.action == KeyEvent.ACTION_DOWN) {
            if (player.isPlaying) player.pause() else player.play()
            return true
        }
        // Barre cachée : les flèches font comme dans le plein écran du site (◀ ▶ avancent ou reculent ; le volume
        // reste aux touches volume de la télécommande) ; OK affiche la barre, et ses boutons se parcourent alors aux flèches. Sinon la première
        // flèche ne ferait qu'afficher la barre.
        if (!view.isControllerFullyVisible && settings.fullscreenPlayerKeys && arrowKey(event)) return true
        return view.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    // --- Menu du lecteur léger -------------------------------------------------------------

    private lateinit var rootFrame: FrameLayout
    private var panel: android.view.View? = null
    private val panelTick = androidx.compose.runtime.mutableIntStateOf(0)
    private val tv get() = (application as WiDoApp).isTv

    private fun framingLabel() = getString(R.string.native_framing, getString(RESIZE_LABELS[resizeIndex]))

    private fun showPanel() {
        if (panel != null) return
        view.hideController()
        hidePauseScreen()
        val stay = { run: () -> Unit -> run(); panelTick.intValue++ }
        val page = intent.getStringExtra(EXTRA_PAGE)
        val siteId = intent.getStringExtra(EXTRA_SITE)
        val actions = listOfNotNull(
            QuickAction(getString(R.string.tile_pause), Glyph.Play, dynamic = { getString(if (player.isPlaying) R.string.tile_pause else R.string.tile_play) }) {
                if (player.isPlaying) player.pause() else player.play()
                hidePanel()
            },
            QuickAction(framingLabel(), Glyph.ZoomIn, dynamic = ::framingLabel) {
                stay {
                    resizeIndex = (resizeIndex + 1) % RESIZE_MODES.size
                    view.resizeMode = RESIZE_MODES[resizeIndex]
                }
            },
            if (page != null && siteId != null) QuickAction(getString(R.string.panel_pin), Glyph.Star) { pinPage(siteId, page); hidePanel() } else null,
            QuickAction(getString(R.string.tile_restart), Glyph.Restart) { player.seekTo(0); hidePanel() },
            QuickAction(sleepLabel(), Glyph.Sleep, dynamic = ::sleepLabel) { stay { cycleSleep() } },
            QuickAction(getString(R.string.menu_back_site), Glyph.Back) { finish() },
        )
        val compose = androidx.compose.ui.platform.ComposeView(this).apply {
            setContent {
                dev.wido.tv.ui.WiDoTheme {
                    dev.wido.tv.ui.QuickMenu(null, null, actions, "", compact = !tv, onDismiss = { hidePanel() }, tick = panelTick.intValue)
                }
            }
        }
        panel = compose
        rootFrame.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        compose.bringToFront()
        osd.bringToFront()
        if (tv) osd.raise((150 * resources.displayMetrics.density).toInt())
    }

    private fun hidePanel() {
        val p = panel ?: return
        panel = null
        rootFrame.removeView(p)
        osd.raise(0)
        view.requestFocus()
    }

    /** Épingle la page du film depuis le lecteur : pas besoin de retourner sur le site. */
    private fun pinPage(siteId: String, page: String) {
        val title = intent.getStringExtra(EXTRA_TITLE)
        (application as WiDoApp).repository.addPin(siteId, title, page)
        osd.showText(getString(R.string.toast_pinned, title?.take(40) ?: page), OsdView.Kind.Success)
    }

    // Veille : désactivé → 30 → 60 → 90 → 120 min ; à l'échéance, le film se met en pause et le lecteur se ferme.
    private var sleepAt = 0L
    private var sleepMinutes = 0
    private val sleepNow = Runnable {
        player.pause()
        Toast.makeText(this, R.string.toast_sleep, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun sleepLabel(): String {
        if (sleepAt == 0L) return getString(R.string.panel_sleep)
        val left = ((sleepAt - SystemClock.elapsedRealtime()) / 60_000L + 1).coerceAtLeast(1)
        return getString(R.string.panel_sleep_left, left.toInt())
    }

    private fun cycleSleep() {
        val steps = listOf(0, 30, 60, 90, 120)
        val next = steps[(steps.indexOf(sleepMinutes) + 1) % steps.size]
        view.removeCallbacks(sleepNow)
        sleepMinutes = next
        if (next == 0) {
            sleepAt = 0L
            osd.showText(getString(R.string.osd_sleep_off))
            return
        }
        sleepAt = SystemClock.elapsedRealtime() + next * 60_000L
        view.postDelayed(sleepNow, next * 60_000L)
        osd.showText(getString(R.string.osd_sleep_set, next))
    }

    // --- Écran de pause (TV) -----------------------------------------------------------------

    private var pauseView: android.view.View? = null
    private var swallowUp = 0
    private var lastInputAt = 0L
    private val showPause = Runnable { showPauseScreen() }

    /** Film en pause depuis un instant : le menu de lecture s'efface, l'écran s'assombrit, titre et temps restant en bas. */
    private fun schedulePause() {
        view.removeCallbacks(showPause)
        if (!tv) return
        val paused = !player.playWhenReady && player.playbackState == Player.STATE_READY
        if (paused) view.postDelayed(showPause, 1_200) else hidePauseScreen()
    }

    private fun showPauseScreen() {
        if (pauseView != null || panel != null || isFinishing) return
        val paused = !player.playWhenReady && player.playbackState == Player.STATE_READY
        if (!paused) return
        if (SystemClock.uptimeMillis() - lastInputAt < 4_000) {
            view.postDelayed(showPause, 1_500)
            return
        }
        val density = resources.displayMetrics.density
        val scale = settings.uiScale
        val left = ((player.duration - player.currentPosition) / 1000.0).coerceAtLeast(0.0)
        val end = android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(System.currentTimeMillis() + (left * 1000).toLong()))
        val host = intent.getStringExtra(EXTRA_PAGE)?.let { dev.wido.core.Hosts.of(it)?.removePrefix("www.") }.orEmpty()
        fun text(value: String, size: Float, color: Int, weight: Int, top: Int = 0) = TextView(this).apply {
            this.text = value
            setTextColor(color)
            textSize = size * scale
            useJakarta(weight)
            setPadding(0, (top * density).toInt(), 0, 0)
        }
        val column = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(text(intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: host, 34f, 0xFFFFFFFF.toInt(), 800).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            addView(
                text(
                    listOfNotNull(host.takeIf { it.isNotEmpty() }, if (player.duration > 0) getString(R.string.osd_remaining, dev.wido.core.PlayerFormat.time(left), end) else null).joinToString("  ·  "),
                    17f, 0xCCFFFFFF.toInt(), 600, 10,
                ),
            )
            addView(text(getString(R.string.native_pause_hint), 14f, 0x99FFFFFF.toInt(), 600, 18))
        }
        val screen = FrameLayout(this).apply {
            background = PauseBackdrop.drawable(resources)
            val pad = (56 * density).toInt()
            addView(
                column,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM)
                    .apply { setMargins(pad, 0, pad, (48 * density).toInt()) },
            )
            alpha = 0f
        }
        pauseView = screen
        CrashLog.trace("natif : écran de pause affiché")
        view.hideController()
        rootFrame.addView(screen, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        screen.animate().alpha(1f).setDuration(450).start()
    }

    private fun hidePauseScreen() {
        view.removeCallbacks(showPause)
        val screen = pauseView ?: return
        pauseView = null
        screen.animate().alpha(0f).setDuration(200).withEndAction { rootFrame.removeView(screen) }.start()
    }

    /** Flèche gauche/droite : saut (de plus en plus grand si la touche est maintenue). Le volume reste aux touches volume. */
    private fun arrowKey(event: KeyEvent): Boolean {
        val seconds = settings.seekSeconds
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val sign = if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                    val jump = sign * seconds * (1 + minOf(event.repeatCount / 4, 4))
                    val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    player.seekTo((player.currentPosition + jump * 1000L).coerceIn(0L, duration))
                    osd.showText(if (jump >= 0) "+$jump s" else "−${-jump} s")
                }
            }
            else -> return false
        }
        return true
    }

    // --- Lien avec les autres écrans : télécommande, « En cours sur la TV » ---

    private var ticks = 0

    private val publish = object : Runnable {
        override fun run() {
            publishNowPlaying()
            // Toutes les ~45 s de lecture : l'image de la vidéo pour « Reprendre ».
            // Pas sur les clés d'1 Go : la copie de l'image vidéo prend de la mémoire au décodeur (elle se fait en quittant).
            if (player.isPlaying && !(application as WiDoApp).lowMemory && ++ticks % 22 == 0) captureFrame()
            view.postDelayed(this, PUBLISH_MS)
        }
    }

    /** Copie l'image affichée par le lecteur (sa surface vidéo) pour la vignette de « Reprendre ». */
    private fun captureFrame() {
        val surface = view.videoSurfaceView as? android.view.SurfaceView ?: return
        val page = intent.getStringExtra(EXTRA_PAGE) ?: return
        if (surface.width == 0 || surface.height == 0 || !surface.holder.surface.isValid) return
        val width = minOf(480, surface.width)
        val height = (surface.height * width.toFloat() / surface.width).toInt().coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val thumbnails = (application as WiDoApp).thumbnails
        runCatching {
            android.view.PixelCopy.request(surface, bitmap, { result ->
                if (result == android.view.PixelCopy.SUCCESS) kotlin.concurrent.thread { thumbnails.saveFrame(page, bitmap) }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
    }

    private fun publishNowPlaying() {
        val app = application as WiDoApp
        if (!app.isTv || !::player.isInitialized) return
        val page = intent.getStringExtra(EXTRA_PAGE) ?: return
        val duration = player.duration
        app.link.nowPlaying.value = NowPlaying(
            siteId = intent.getStringExtra(EXTRA_SITE).orEmpty(),
            url = page,
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            image = intent.getStringExtra(EXTRA_IMAGE),
            position = player.currentPosition / 1000.0,
            duration = if (duration > 0) duration / 1000.0 else 0.0,
            playing = player.isPlaying,
            at = System.currentTimeMillis(),
            native = true,
        )
    }

    override fun onRemote(command: RemoteCommand) {
        if (!::player.isInitialized) return
        when (command) {
            is RemoteCommand.Key -> when (command.key) {
                RemoteKey.PLAY -> player.play()
                RemoteKey.PAUSE -> player.pause()
                RemoteKey.PLAY_PAUSE, RemoteKey.OK -> if (player.isPlaying) player.pause() else player.play()
                RemoteKey.REWIND, RemoteKey.LEFT -> player.seekBack()
                RemoteKey.FORWARD, RemoteKey.RIGHT -> player.seekForward()
                RemoteKey.BACK, RemoteKey.HOME -> finish()
                RemoteKey.MENU -> dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU))
                else -> return
            }
            is RemoteCommand.Seek -> player.seekTo((command.position * 1000).toLong())
            else -> return
        }
        publishNowPlaying()
    }

    override fun onResume() {
        super.onResume()
        if (!::player.isInitialized) return
        (application as WiDoApp).remote.attach(this)
        view.post(publish)
        view.removeCallbacks(watchdog)
        view.postDelayed(watchdog, WATCHDOG_MS)
    }

    override fun onPause() {
        CrashLog.trace("natif : en pause (${CrashLog.memoryLine(this)})")
        if (::player.isInitialized) {
            captureFrame()
            view.removeCallbacks(publish)
            view.removeCallbacks(watchdog)
            (application as WiDoApp).remote.detach(this)
            if ((application as WiDoApp).isTv) (application as WiDoApp).link.nowPlaying.value = null
        }
        super.onPause()
    }

    /** La page reprend à la seconde où le lecteur natif s'est arrêté. */
    override fun finish() {
        if (::player.isInitialized && (player.currentPosition > 0 || failed)) {
            setResult(RESULT_OK, Intent().putExtra(RESULT_POSITION, player.currentPosition / 1000.0).putExtra(RESULT_FAILED, failed))
        }
        super.finish()
    }

    override fun onStop() {
        if (::player.isInitialized) {
            savePosition()
            player.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::view.isInitialized) {
            view.removeCallbacks(showPause)
            view.removeCallbacks(sleepNow)
            view.removeCallbacks(watchdog)
        }
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    /** Retient la position (et l'oublie près de la fin) pour reprendre à la seconde près. */
    private fun savePosition() {
        val duration = player.duration
        val position = player.currentPosition
        val nearEnd = duration > 0 && duration - position < 30_000
        settings.setNativePositionFor(positionKey, if (nearEnd) 0 else position)
        val siteId = intent.getStringExtra(EXTRA_SITE) ?: return
        val page = intent.getStringExtra(EXTRA_PAGE) ?: return
        (application as WiDoApp).repository.recordProgress(
            siteId = siteId,
            url = page,
            title = intent.getStringExtra(EXTRA_TITLE),
            image = intent.getStringExtra(EXTRA_IMAGE),
            positionSeconds = position / 1000.0,
            durationSeconds = if (duration > 0) duration / 1000.0 else 0.0,
        )
    }

    companion object {
        private const val PUBLISH_MS = 2_000L
        /** Position (s) où le lecteur natif s'est arrêté, rendue à la page. */
        const val RESULT_POSITION = "position"
        /** Le flux n'a pas pu être lu (la page ne relance pas le lecteur léger d'elle-même). */
        const val RESULT_FAILED = "failed"
        private const val MAX_RETRIES = 2
        private val RETRYABLE = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_TIMEOUT,
        )
        private const val WATCHDOG_MS = 1_000L
        /** Le son avance de 3 s sans nouvelle image : l'image est figée. */
        private const val FROZEN_MS = 3_000L
        /** Deux images figées en 5 min : la qualité baisse d'un cran. */
        private const val FREEZE_WINDOW_MS = 5 * 60_000L
        private const val EXTRA_START = "start"
        private const val EXTRA_URL = "url"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_REFERER = "referer"
        private const val EXTRA_COOKIES = "cookies"
        private const val EXTRA_USER_AGENT = "ua"
        private const val EXTRA_SITE = "site"
        private const val EXTRA_PAGE = "page"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_IMAGE = "image"

        private val RESIZE_MODES = intArrayOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
        )
        private val RESIZE_LABELS = intArrayOf(R.string.resize_fit, R.string.resize_zoom, R.string.resize_fill)

        fun intent(
            context: Context,
            stream: MediaStream,
            siteId: String,
            pageUrl: String?,
            title: String?,
            image: String?,
            referer: String?,
            cookies: String?,
            userAgent: String?,
            /** Seconde de départ (celle de la page). */
            startSeconds: Double = 0.0,
        ): Intent = Intent(context, NativePlayerActivity::class.java)
            .putExtra(EXTRA_START, startSeconds)
            .putExtra(EXTRA_URL, stream.url)
            .putExtra(EXTRA_KIND, stream.kind.name)
            .putExtra(EXTRA_SITE, siteId)
            .putExtra(EXTRA_PAGE, pageUrl)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_IMAGE, image)
            .putExtra(EXTRA_REFERER, referer)
            .putExtra(EXTRA_COOKIES, cookies)
            .putExtra(EXTRA_USER_AGENT, userAgent)
    }
}
