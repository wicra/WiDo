package dev.wido.tv

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
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

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .setSeekBackIncrementMs(settings.seekSeconds * 1000L)
            .setSeekForwardIncrementMs(settings.seekSeconds * 1000L)
            .build()

        view = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            this.player = this@NativePlayerActivity.player
            keepScreenOn = true
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
                Toast.makeText(this@NativePlayerActivity, R.string.native_failed, Toast.LENGTH_LONG).show()
                finish()
            }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Menu : cadrage de l'image (adapté → zoomé → étiré).
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) {
                resizeIndex = (resizeIndex + 1) % RESIZE_MODES.size
                view.resizeMode = RESIZE_MODES[resizeIndex]
                osd.showText(getString(RESIZE_LABELS[resizeIndex]))
            }
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
            if (player.isPlaying && ++ticks % 22 == 0) captureFrame()
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
    }

    override fun onPause() {
        if (::player.isInitialized) {
            captureFrame()
            view.removeCallbacks(publish)
            (application as WiDoApp).remote.detach(this)
            if ((application as WiDoApp).isTv) (application as WiDoApp).link.nowPlaying.value = null
        }
        super.onPause()
    }

    /** La page reprend à la seconde où le lecteur natif s'est arrêté. */
    override fun finish() {
        if (::player.isInitialized && player.currentPosition > 0) {
            setResult(RESULT_OK, Intent().putExtra(RESULT_POSITION, player.currentPosition / 1000.0))
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
