package dev.wido.tv

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import androidx.webkit.WebViewFeature
import dev.wido.core.MiniHttpServer
import dev.wido.tv.ui.ActionButton
import dev.wido.tv.ui.CrashReportDialog
import dev.wido.tv.ui.Glow
import dev.wido.tv.ui.Glyph
import dev.wido.tv.ui.GlyphIcon
import dev.wido.tv.ui.Palette
import dev.wido.tv.ui.WiDoTheme
import dev.wido.tv.ui.halo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.coroutines.resume

/**
 * Réglages → Test → « Lancer le test complet » : WiDo s'examine sur l'appareil, de bout en bout, et rend un
 * rapport propre (à lire, à scanner avec le téléphone ou à envoyer) : appareil et mémoire, réseau et débit,
 * décodeurs vidéo, vraie lecture d'un flux de test dans le lecteur léger (démarrage, images perdues, image
 * figée, recherche), moteur des pages (réactivité du curseur), configuration de WiDo et serveur local.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class DiagnosticActivity : ComponentActivity() {
    enum class Status { RUNNING, OK, WARN, FAIL }

    class Step(val title: String) {
        var status by mutableStateOf(Status.RUNNING)
        var summary by mutableStateOf("")
        /** Ce que l'étape a noté (avertissements et échecs en tête), montré quand elle a le focus. */
        var lines by mutableStateOf(emptyList<String>())
    }

    /** Ce qu'une étape note : des lignes, et le pire résultat rencontré. */
    private class Scope {
        var status = Status.OK
        var summary = ""
        val lines = mutableListOf<String>()

        fun line(text: String) {
            lines += text
        }

        fun warn(text: String) {
            if (status == Status.OK) status = Status.WARN
            lines += "⚠ $text"
        }

        fun fail(text: String) {
            status = Status.FAIL
            lines += "✗ $text"
        }
    }

    private val steps = mutableStateListOf<Step>()
    private var report by mutableStateOf<String?>(null)
    private var testPlayer by mutableStateOf<ExoPlayer?>(null)
    private var job: Job? = null
    private val out = StringBuilder()
    /** Chiffres clés du test (résumé technique en fin de rapport), dans l'ordre où ils sont mesurés. */
    private val metrics = linkedMapOf<String, String>()

    private fun metric(key: String, value: Any?) {
        if (value != null) metrics[key] = value.toString()
    }
    private val app get() = application as WiDoApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { WiDoTheme { Screen() } }
        start()
    }

    override fun onDestroy() {
        job?.cancel()
        releasePlayer()
        super.onDestroy()
    }

    private fun start() {
        job?.cancel()
        steps.clear()
        report = null
        out.setLength(0)
        metrics.clear()
        job = lifecycleScope.launch {
            CrashLog.trace("test complet : début (${CrashLog.memoryLine(this@DiagnosticActivity)})")
            val started = SystemClock.elapsedRealtime()
            out.append("Test complet de WiDo\n").append(CrashLog.reportHeader())
            step(getString(R.string.test_step_device)) { device() }
            step(getString(R.string.test_step_network)) { network() }
            step(getString(R.string.test_step_codecs)) { codecs() }
            step(getString(R.string.test_step_playback)) { playback() }
            step(getString(R.string.test_step_web)) { web() }
            step(getString(R.string.test_step_features)) { features() }
            step(getString(R.string.test_step_open)) { siteOpening() }
            step(getString(R.string.test_step_leaks)) { webLeaks() }
            step(getString(R.string.test_step_app)) { appState() }
            step(getString(R.string.test_step_memory)) { memoryAfter() }
            val ok = steps.count { it.status == Status.OK }
            val warn = steps.count { it.status == Status.WARN }
            val fail = steps.count { it.status == Status.FAIL }
            val summary = "Résumé : $ok OK · $warn à surveiller · $fail en échec · ${(SystemClock.elapsedRealtime() - started) / 1000} s"
            out.insert(out.indexOf("\n") + 1, "$summary\n")
            // Les chiffres clés, une ligne chacun, en tête (juste après le résumé) : de quoi comparer deux rapports d'un
            // coup d'œil, et ils tiennent toujours dans un envoi, même quand le journal détaillé est long.
            val tech = StringBuilder("\n== Résumé technique\n")
            metrics.forEach { (k, v) -> tech.append("  ").append(k).append(" = ").append(v).append('\n') }
            val headerEnd = out.indexOf("\n", out.indexOf(summary)) + 1
            out.insert(headerEnd, tech)
            CrashLog.lastCrash()?.let { out.append("\n== Dernière erreur enregistrée\n").append(it.take(3_000).trimEnd()).append('\n') }
            CrashLog.recentEvents()?.let { out.append("\n== Derniers événements\n").append(it).append('\n') }
            CrashLog.recentTrace(60).takeIf { it.isNotEmpty() }?.let { out.append("\n== Journal détaillé (60 dernières lignes)\n").append(it.joinToString("\n")).append('\n') }
            CrashLog.event("Test complet : $ok OK, $warn à surveiller, $fail en échec")
            report = out.toString()
        }
    }

    private suspend fun step(title: String, block: suspend Scope.() -> Unit) {
        val step = Step(title)
        steps += step
        val scope = Scope()
        val t0 = SystemClock.elapsedRealtime()
        try {
            scope.block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            scope.fail("erreur inattendue : ${e.javaClass.simpleName} ${e.message.orEmpty()}")
        }
        step.summary = scope.summary
        step.lines = scope.lines.filter { it.startsWith("✗") || it.startsWith("⚠") } + scope.lines.filterNot { it.startsWith("✗") || it.startsWith("⚠") }
        step.status = scope.status
        val label = when (scope.status) {
            Status.OK -> "OK"
            Status.WARN -> "À SURVEILLER"
            Status.FAIL -> "ÉCHEC"
            Status.RUNNING -> ""
        }
        out.append("\n== $title — $label (${SystemClock.elapsedRealtime() - t0} ms)\n")
        if (scope.summary.isNotEmpty()) out.append("  ").append(scope.summary).append('\n')
        scope.lines.forEach { out.append("  ").append(it).append('\n') }
        CrashLog.trace("test complet : $title → $label")
    }

    // --- Étapes ------------------------------------------------------------------------------

    private fun Scope.device() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val total = info.totalMem / MB
        val free = info.availMem / MB
        line("${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${if (app.isTv) "TV" else "téléphone"}")
        line("Mémoire : $total Mo, libre $free Mo, seuil bas ${info.threshold / MB} Mo" + (if (am.isLowRamDevice) " · appareil déclaré à faible mémoire" else ""))
        line("Tas de l'appli : ${Runtime.getRuntime().maxMemory() / MB} Mo (classe ${am.memoryClass} / ${am.largeMemoryClass} Mo)")
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val mode = display.mode
        line("Écran : ${mode.physicalWidth}×${mode.physicalHeight} à ${"%.0f".format(mode.refreshRate)} Hz (${display.supportedModes.size} modes)")
        line("Stockage libre : ${filesDir.usableSpace / MB} Mo")
        line("Moteur web : ${runCatching { WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } }.getOrNull() ?: "inconnu"}")
        summary = "$total Mo de mémoire, $free Mo libres"
        metric("version", BuildConfig.VERSION_NAME)
        metric("appareil", "${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT}")
        metric("memoire_mo", total)
        metric("memoire_libre_mo", free)
        metric("faible_memoire", app.lowMemory)
        metric("ecran", "${mode.physicalWidth}x${mode.physicalHeight}")
        metric("moteur_web", runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull())
        // Constat sur l'appareil, pas un défaut de WiDo : noté sans changer le verdict.
        if (app.lowMemory) line("Appareil à peu de mémoire : WiDo passe en mode léger (lecteur léger pour les films, listes légères)")
        if (free < 250) warn("moins de 250 Mo libres : fermer les autres applis aide")
    }

    private suspend fun Scope.network() = withContext(Dispatchers.IO) {
        line("Adresse locale : ${MiniHttpServer.localAddress() ?: "aucune (pas de réseau local)"}")
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val kind = when {
            caps == null -> "aucun"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            else -> "autre"
        }
        line("Connexion : $kind" + (caps?.let { " · ${it.linkDownstreamBandwidthKbps / 1000} Mb/s annoncés" } ?: ""))
        if (caps == null) {
            fail("pas de connexion")
            summary = "pas de connexion"
            return@withContext
        }
        val dns = timed { InetAddress.getAllByName("github.com").size }
        if (dns.second == null) fail("DNS : échec") else line("DNS : ${dns.first} ms")
        val pings = (1..3).mapNotNull { timed { get(PING_URL, 0) }.takeIf { it.second != null }?.first }
        if (pings.isEmpty()) {
            fail("HTTPS : aucune réponse")
        } else {
            val median = pings.sorted()[pings.size / 2]
            line("HTTPS : $median ms (${pings.joinToString(" / ")})")
            if (median > 800) warn("réseau lent à répondre ($median ms)")
        }
        // Débit : les premiers segments du flux de test, dans sa meilleure qualité.
        val speed = runCatching { throughput() }.getOrNull()
        if (speed == null) {
            warn("débit : mesure impossible")
            summary = "connecté ($kind)"
        } else {
            line("Débit : ${"%.1f".format(speed)} Mb/s")
            metric("debit_mbps", "%.1f".format(java.util.Locale.ROOT, speed))
            summary = "$kind · ${"%.1f".format(speed)} Mb/s"
            if (speed < 8) warn("débit faible pour du 1080p (8 Mb/s conseillés)")
        }
    }

    /** Mb/s mesurés sur ~3 segments de la meilleure qualité du flux de test. */
    private fun throughput(): Double? {
        val master = String(get(TEST_STREAM, 200_000) ?: return null)
        val variant = bestVariant(master) ?: return null
        val variantUrl = URL(URL(TEST_STREAM), variant).toString()
        val playlist = String(get(variantUrl, 200_000) ?: return null)
        val segments = playlist.lines().filter { it.isNotBlank() && !it.startsWith("#") }.take(3)
        var bytes = 0L
        val t0 = SystemClock.elapsedRealtime()
        for (segment in segments) bytes += get(URL(URL(variantUrl), segment).toString(), 20_000_000)?.size ?: return null
        val ms = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1)
        return bytes * 8.0 / 1000.0 / ms
    }

    private fun bestVariant(master: String): String? {
        var best: String? = null
        var bandwidth = -1L
        val lines = master.lines()
        lines.forEachIndexed { i, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@forEachIndexed
            val bw = Regex("""BANDWIDTH=(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val uri = lines.getOrNull(i + 1)?.takeIf { it.isNotBlank() && !it.startsWith("#") } ?: return@forEachIndexed
            if (bw > bandwidth) {
                bandwidth = bw
                best = uri
            }
        }
        return best
    }

    private fun Scope.codecs() {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        fun hardware(info: android.media.MediaCodecInfo) =
            if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
        val found = mutableListOf<String>()
        for ((mime, label) in CODECS) {
            val list = decoders.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            if (list.isEmpty()) {
                line("$label : aucun décodeur")
                continue
            }
            val hw = list.firstOrNull { hardware(it) }
            val video = (hw ?: list.first()).getCapabilitiesForType(mime).videoCapabilities
            val fhd = video?.areSizeAndRateSupported(1920, 1080, 60.0) == true
            val uhd = video?.areSizeAndRateSupported(3840, 2160, 30.0) == true
            line("$label : ${if (hw != null) "matériel (${hw.name})" else "logiciel seulement (${list.first().name})"} · 1080p60 ${if (fhd) "oui" else "non"} · 4K ${if (uhd) "oui" else "non"}")
            if (hw != null) found += label
            if (mime == MimeTypes.VIDEO_H264 && hw == null) fail("pas de décodeur H.264 matériel : la vidéo sera saccadée")
            if (mime == MimeTypes.VIDEO_H264 && hw != null && !fhd) warn("le décodeur H.264 ne garantit pas le 1080p à 60 images/s")
        }
        summary = if (found.isEmpty()) "aucun décodeur matériel" else "matériel : ${found.joinToString(", ")}"
    }

    /** Lecture réelle du flux de test dans le lecteur réglé comme le lecteur léger : 20 s, puis une recherche. */
    private suspend fun Scope.playback() {
        val before = CrashLog.memoryLine(this@DiagnosticActivity)
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(10_000).setReadTimeoutMs(10_000)
        val player = NativePlayerActivity.buildPlayer(this@DiagnosticActivity, http, app.lowMemory, 10_000).first
        var firstFrameAt = 0L
        var error: PlaybackException? = null
        var rebuffers = 0
        var wasReady = false
        var decoder = "?"
        var dropped = 0
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (firstFrameAt == 0L) firstFrameAt = SystemClock.elapsedRealtime()
            }

            override fun onPlayerError(e: PlaybackException) {
                error = e
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) wasReady = true
                if (state == Player.STATE_BUFFERING && wasReady) rebuffers++
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                decoder = decoderName
            }

            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                dropped += droppedFrames
            }
        })
        testPlayer = player
        try {
            val t0 = SystemClock.elapsedRealtime()
            player.setMediaItem(MediaItem.Builder().setUri(TEST_STREAM).setMimeType(MimeTypes.APPLICATION_M3U8).build())
            player.prepare()
            player.playWhenReady = true
            withTimeoutOrNull(20_000) { while (firstFrameAt == 0L && error == null) delay(100) }
            error?.let {
                fail("lecture impossible : ${it.errorCodeName}")
                summary = "échec : ${it.errorCodeName}"
                return
            }
            if (firstFrameAt == 0L) {
                fail("aucune image au bout de 20 s")
                summary = "pas d'image"
                return
            }
            val startup = firstFrameAt - t0
            line("Première image : $startup ms · décodeur $decoder")
            metric("lecture_premiere_image_ms", startup)
            metric("lecture_decodeur", decoder)
            if (startup > 5_000) warn("démarrage lent ($startup ms)")
            // 20 s de lecture : images affichées, perdues, et secondes où le son avance sans image.
            var frozenSeconds = 0
            var lastRendered = -1
            var lastPosition = player.currentPosition
            var rendered0 = -1
            repeat(20) {
                delay(1_000)
                val counters = player.videoDecoderCounters ?: return@repeat
                counters.ensureUpdated()
                if (rendered0 < 0) rendered0 = counters.renderedOutputBufferCount
                val position = player.currentPosition
                if (player.isPlaying && counters.renderedOutputBufferCount == lastRendered && position - lastPosition >= 800) frozenSeconds++
                lastRendered = counters.renderedOutputBufferCount
                lastPosition = position
            }
            val counters = player.videoDecoderCounters?.also { it.ensureUpdated() }
            val shown = (counters?.renderedOutputBufferCount ?: 0) - rendered0.coerceAtLeast(0)
            val lost = counters?.droppedBufferCount ?: dropped
            val fps = shown / 19.0
            val size = player.videoSize
            line("Image : ${size.width}×${size.height} · ${"%.1f".format(fps)} images/s affichées · $lost perdues · ${counters?.maxConsecutiveDroppedBufferCount ?: 0} perdues d'affilée au pire")
            line("Rechargements pendant la lecture : $rebuffers")
            // Les qualités du flux, celles que l'appareil sait lire, et ce que le lecteur a choisi : de quoi dire
            // pourquoi l'image reste en basse qualité (débit estimé trop bas, décodeur qui refuse un profil…).
            val variants = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) to group.isTrackSupported(it) } }
                .sortedByDescending { it.first.height }
            if (variants.isNotEmpty()) {
                line(
                    "Qualités du flux : " + variants.joinToString(" · ") { (format, ok) ->
                        "${format.height}p" + (if (format.bitrate > 0) " ${"%.1f".format(format.bitrate / 1e6)} Mb/s" else "") + if (ok) "" else " (non lisible)"
                    },
                )
                val playable = variants.filter { it.second }.map { it.first.height }
                val target = playable.filter { it <= 1080 }.maxOrNull() ?: 0
                if (size.height in 1 until 720 && target >= 720) warn("restée en ${size.height}p alors que le ${target}p est lisible")
                if (playable.isEmpty()) warn("aucune qualité du flux n'est lisible par les décodeurs")
            }
            app.settings.lastBitrate.takeIf { it > 0 }?.let { line("Débit estimé par le lecteur : ${"%.1f".format(it * 4 / 3 / 1e6)} Mb/s") }
            if (frozenSeconds > 0) fail("image figée pendant $frozenSeconds s alors que le son avançait")
            val ratio = if (shown + lost > 0) lost * 100.0 / (shown + lost) else 0.0
            when {
                ratio > 15 -> fail("${"%.0f".format(ratio)} % d'images perdues : vidéo saccadée")
                ratio > 5 -> warn("${"%.0f".format(ratio)} % d'images perdues")
            }
            if (rebuffers > 0) warn("le flux s'est rechargé $rebuffers fois (réseau)")
            // Recherche : 2 min plus loin, temps avant de rejouer.
            val seekAt = SystemClock.elapsedRealtime()
            val readyBefore = firstFrameAt
            firstFrameAt = 0L
            player.seekTo(player.currentPosition + 120_000)
            val ready = withTimeoutOrNull(15_000) {
                while (firstFrameAt == 0L && error == null) delay(100)
                true
            } == true && error == null
            if (ready) {
                val seek = firstFrameAt - seekAt
                metric("lecture_recherche_ms", seek)
                line("Recherche (+2 min) : $seek ms")
                if (seek > 4_000) warn("recherche lente ($seek ms)")
            } else {
                firstFrameAt = readyBefore
                warn("recherche : pas d'image au bout de 15 s")
            }
            summary = "démarrage $startup ms · ${"%.0f".format(ratio)} % d'images perdues" + if (frozenSeconds > 0) " · image figée" else ""
            metric("lecture_image", "${size.width}x${size.height}")
            metric("lecture_images_perdues_pct", "%.1f".format(java.util.Locale.ROOT, ratio))
            metric("lecture_image_figee_s", frozenSeconds)
        } finally {
            line("Mémoire avant : $before")
            line("Mémoire pendant : ${CrashLog.memoryLine(this@DiagnosticActivity)}")
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        val player = testPlayer ?: return
        testPlayer = null
        runCatching { player.release() }
    }

    /** Moteur des pages : ouverture d'une page de 300 liens, puis temps de réponse d'un appel (celui du curseur aimanté). */
    private suspend fun Scope.web() {
        line("Scripts au démarrage de la page : ${if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) "oui" else "non"} · passerelle des frames : ${if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) "oui" else "non"}")
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) warn("moteur web ancien : mets à jour « Android System WebView »")
        val t0 = SystemClock.elapsedRealtime()
        val wv = WebView(this@DiagnosticActivity)
        val created = SystemClock.elapsedRealtime() - t0
        wv.settings.javaScriptEnabled = true
        wv.alpha = 0f
        wv.isFocusable = false
        addContentView(wv, ViewGroup.LayoutParams(1280, 720))
        try {
            val links = (1..300).joinToString("") { "<a href='#$it' style='display:inline-block;width:180px;height:60px;margin:6px'>Lien $it</a>" }
            val loaded = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                    wv.loadDataWithBaseURL("https://wido.test/", "<html><body style='background:#000'>$links</body></html>", "text/html", "utf-8", null)
                }
            } != null
            val load = SystemClock.elapsedRealtime() - t0
            line("Création du moteur : $created ms · page de 300 liens : $load ms")
            if (!loaded) {
                fail("la page de test ne s'est pas ouverte en 15 s")
                summary = "page de test bloquée"
                return
            }
            val script = "(function(){var n=document.querySelectorAll('a'),c=0;for(var i=0;i<n.length;i++){var r=n[i].getBoundingClientRect();if(r.width>8&&r.bottom>0&&r.top<innerHeight)c++;}return c;})()"
            val times = mutableListOf<Long>()
            repeat(20) {
                val start = SystemClock.elapsedRealtime()
                withTimeoutOrNull(2_000) { suspendCancellableCoroutine { cont -> wv.evaluateJavascript(script) { if (cont.isActive) cont.resume(it) } } }
                times += SystemClock.elapsedRealtime() - start
            }
            val avg = times.average().toLong()
            val worst = times.maxOrNull() ?: 0L
            line("Réponse de la page (curseur) : $avg ms en moyenne, $worst ms au pire")
            summary = "page $load ms · curseur $avg ms"
            metric("page_300_liens_ms", load)
            metric("aller_retour_page_ms", avg)
            if (created > 2_000) warn("moteur web long à démarrer ($created ms)")
            if (avg > 60) warn("page lente à répondre : le curseur aimanté sera moins vif")
        } finally {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
    }

    /** Un moteur web caché, de la taille de l'écran, pour les étapes qui exercent les scripts de WiDo. */
    private fun hiddenWebView(): WebView = WebView(this@DiagnosticActivity).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        alpha = 0f
        isFocusable = false
        addContentView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun dropWebView(wv: WebView) {
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.destroy()
    }

    private suspend fun WebView.loadTestPage(): Boolean {
        val html = assets.open("diagnostic/selftest.html").bufferedReader().use { it.readText() }
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                loadDataWithBaseURL("https://wido.test/", html, "text/html", "utf-8", null)
            }
        } != null
    }

    private suspend fun WebView.eval(script: String): String? = withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { cont -> evaluateJavascript(script) { if (cont.isActive) cont.resume(it) } }
    }

    /**
     * Les fonctions de WiDo, exercées pour de vrai sur une page de test (assets/diagnostic) avec les scripts de l'appli :
     * curseur aimanté, carrousels défilants et animés, clavier du téléphone, passage au lecteur léger, fenêtre de
     * cookies, puis « Masquer un élément » d'une page à l'autre.
     */
    private suspend fun Scope.features() {
        val wv = hiddenWebView()
        try {
            if (!wv.loadTestPage()) {
                fail("la page de test ne s'est pas ouverte")
                return
            }
            val injector = ScriptInjector(this@DiagnosticActivity, TEST_SITE)
            wv.eval(injector.internalScript())
            wv.eval(assets.open("diagnostic/selftest.js").bufferedReader().use { it.readText() })
            var result: org.json.JSONObject? = null
            withTimeoutOrNull(45_000) {
                while (true) {
                    delay(300)
                    val raw = wv.eval("window.__widoSelfTest&&window.__widoSelfTest.done?JSON.stringify(window.__widoSelfTest):null")
                    val text = raw?.takeIf { it != "null" }?.let { runCatching { org.json.JSONTokener(it).nextValue() as? String }.getOrNull() }
                    if (text != null) {
                        result = org.json.JSONObject(text)
                        break
                    }
                }
            }
            val r = result
            if (r == null) {
                fail("les tests de la page ne se sont pas terminés en 45 s")
                return
            }
            var passed = 0
            var failed = 0
            val cases = r.optJSONArray("cases") ?: org.json.JSONArray()
            for (i in 0 until cases.length()) {
                val c = cases.getJSONObject(i)
                val name = c.optString("name")
                val detail = c.optString("detail").takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                if (c.optBoolean("ok")) {
                    passed++
                    line("✓ $name$detail")
                } else {
                    failed++
                    fail("$name$detail")
                }
            }
            // « Masquer un élément » : masqué sur cette page, puis retrouvé sur une « autre page » où il est emballé
            // autrement et forcé visible par le site.
            // L'encadré d'abord à l'écran : on vise ce qui est sous le curseur, comme à la télécommande (les étapes
            // précédentes ont fait défiler la page jusqu'au carrousel).
            val pick = wv.eval("(function(){var a=document.getElementById('ad');a.scrollIntoView({block:'center'});var r=a.getBoundingClientRect();return JSON.stringify([(r.left+r.width/2)/innerWidth,(r.top+r.height/2)/innerHeight]);})()")
            val point = pick?.let { runCatching { org.json.JSONArray(org.json.JSONTokener(it).nextValue() as String) }.getOrNull() }
            val hideOk = if (point != null) {
                val raw = wv.eval("(${PageScripts.HIDE})(${point.getDouble(0)},${point.getDouble(1)})")
                val selectors = raw?.let { runCatching { org.json.JSONArray(org.json.JSONTokener(it).nextValue() as String) }.getOrNull() }
                    ?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                wv.eval("window.__widoSelfTestRebuild()")
                wv.eval(injector.hideScript(selectors))
                delay(300)
                val shown = wv.eval("getComputedStyle(document.querySelector('#adwrap .ad-box')).display")
                line("Masquer un élément : ${selectors.size} signature(s) enregistrée(s)")
                shown == "\"none\""
            } else {
                false
            }
            if (hideOk) {
                passed++
                line("✓ Masquer un élément : reste masqué sur une autre page, même forcé visible par le site")
            } else {
                failed++
                fail("Masquer un élément : l'élément réapparaît sur une autre page")
            }
            val m = r.optJSONObject("metrics")
            if (m != null) {
                line("Curseur aimanté : ${m.optDouble("snapAvgMs")} ms en moyenne, ${m.optDouble("snapMaxMs")} ms au pire (${m.optInt("snapCalls")} appels)")
                metric("curseur_ms_moyen", m.optDouble("snapAvgMs"))
                metric("curseur_ms_pire", m.optDouble("snapMaxMs"))
                if (m.optDouble("snapMaxMs") > 150) warn("curseur aimanté lent par moments (${m.optDouble("snapMaxMs")} ms)")
            }
            metric("fonctions_ok", passed)
            metric("fonctions_echec", failed)
            summary = "$passed réussies, $failed en échec"
        } finally {
            dropWebView(wv)
        }
    }

    /** Ouverture d'un site : le temps de préparer le blocage (listes, masquages, règles d'adresse) du premier site. */
    private suspend fun Scope.siteOpening() {
        val repository = app.repository
        val site = repository.ensureLoaded().sites.firstOrNull { it.enabled } ?: repository.localSites.value.firstOrNull { it.enabled }
        if (site == null) {
            line("Aucun site configuré")
            summary = "aucun site"
            return
        }
        val before = CrashLog.memoryLine(this@DiagnosticActivity)
        val t0 = SystemClock.elapsedRealtime()
        repository.blockerFor(site)
        val t1 = SystemClock.elapsedRealtime()
        repository.cosmeticFor(site)
        val t2 = SystemClock.elapsedRealtime()
        repository.urlMatcherFor(site)
        val t3 = SystemClock.elapsedRealtime()
        val total = t3 - t0
        line("Blocage réseau : ${t1 - t0} ms · masquages : ${t2 - t1} ms · règles d'adresse : ${t3 - t2} ms")
        line("Mémoire avant : $before")
        line("Mémoire après : ${CrashLog.memoryLine(this@DiagnosticActivity)}")
        metric("ouverture_site_ms", total)
        summary = "prêt en $total ms"
        when {
            total > 10_000 -> fail("plus de 10 s pour préparer un site")
            total > 3_000 -> warn("préparation lente ($total ms) : l'ouverture d'un site se fera attendre")
        }
    }

    /** Le moteur web rend-il sa mémoire ? Trois pages de test ouvertes puis fermées, mémoire de l'appli avant et après. */
    private suspend fun Scope.webLeaks() {
        fun pss() = android.os.Debug.getPss() / 1024
        Runtime.getRuntime().gc()
        delay(300)
        val before = pss()
        repeat(3) {
            val wv = hiddenWebView()
            wv.loadTestPage()
            delay(300)
            dropWebView(wv)
        }
        Runtime.getRuntime().gc()
        delay(1_000)
        val after = pss()
        val delta = after - before
        line("Mémoire de l'appli : $before Mo avant, $after Mo après trois ouvertures et fermetures")
        metric("fuite_moteur_web_mo", delta)
        summary = "${if (delta >= 0) "+" else ""}$delta Mo"
        if (delta > 30) warn("le moteur web garde $delta Mo après fermeture : risque de fermeture par Android après plusieurs sites")
    }

    private suspend fun Scope.appState() {
        val settings = app.settings
        val config = runCatching { app.repository.ensureLoaded() }.getOrNull()
        val lists = app.repository.lists.value
        line("Sites : ${config?.sites?.size ?: 0} de la configuration, ${app.repository.localSites.value.size} ajoutés")
        line("Listes de blocage : ${lists.lists} (${lists.hosts} domaines, ${lists.selectors} masquages, ${lists.advanced} règles)" + (lists.updatedAt?.let { " · mises à jour il y a ${(System.currentTimeMillis() - it) / 3_600_000} h" } ?: " · jamais téléchargées"))
        line(
            "Réglages : lecteur léger auto ${yes(settings.autoNative)} · listes légères ${yes(settings.lightLists)} · curseur aimanté ${yes(settings.snapNavigation)} · " +
                "saut ${settings.seekSeconds} s · interface ×${settings.uiScale} · journal détaillé ${yes(settings.detailedLog)}",
        )
        if (config == null) fail("configuration illisible")
        if (lists.updatedAt == null) warn("listes de blocage jamais téléchargées")
        if (!settings.autoNative && app.lowMemory) warn("lecteur léger automatique désactivé sur un appareil à peu de mémoire")
        if (!settings.detailedLog) line("Astuce : active le journal détaillé (Réglages → Général) pour un rapport plus précis")
        // Serveur local (télécommande sur téléphone, QR codes) : il répond-il sur l'appareil lui-même ?
        val server = MiniHttpServer("WiDoTest") { _, _ -> MiniHttpServer.Response(200, body = "ok") }
        val port = server.start(0)
        val answered = withContext(Dispatchers.IO) { port?.let { get("http://127.0.0.1:$it/", 100)?.let { body -> String(body) } } }
        server.stop()
        if (answered == "ok") line("Serveur local : répond (port $port)") else warn("serveur local : pas de réponse (télécommande sur téléphone et QR codes indisponibles)")
        summary = "${config?.sites?.size ?: 0} sites · ${lists.hosts} domaines bloqués"
    }

    private fun Scope.memoryAfter() {
        val line = CrashLog.memoryLine(this@DiagnosticActivity)
        line(line)
        summary = line.removePrefix("mémoire : ")
        if (line.contains("FAIBLE")) warn("Android signale que la mémoire est faible")
    }

    // --- Outils ------------------------------------------------------------------------------

    private fun yes(value: Boolean) = if (value) "oui" else "non"

    /** Durée (ms) et résultat d'un bloc, ou null en cas d'échec. */
    private inline fun <T> timed(block: () -> T?): Pair<Long, T?> {
        val t0 = SystemClock.elapsedRealtime()
        val value = runCatching(block).getOrNull()
        return (SystemClock.elapsedRealtime() - t0) to value
    }

    /** GET bloquant (hors du fil principal) : le corps (au plus `limit` octets), ou null. */
    private fun get(url: String, limit: Int): ByteArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = true
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (buffer.size() <= limit) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buffer.write(chunk, 0, n)
                }
                buffer.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    // --- Écran ---------------------------------------------------------------------------------

    @Composable
    private fun Screen() {
        var showReport by remember { mutableStateOf(false) }
        val done = report
        val firstButton = remember { FocusRequester() }
        val cancelButton = remember { FocusRequester() }
        // La télécommande a toujours un bouton sous la main : Annuler pendant le test, Voir le rapport à la fin.
        LaunchedEffect(done) {
            delay(100)
            runCatching { (if (done != null) firstButton else cancelButton).requestFocus() }
        }
        Box(
            Modifier.fillMaxSize().halo(
                listOf(
                    Glow(Palette.Accent.copy(alpha = 0.55f), 0.08f, 0.0f, 0.55f, 0.55f),
                    Glow(Palette.Violet.copy(alpha = 0.45f), 0.95f, 1.0f, 0.55f, 0.6f),
                ),
            ),
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.test_title), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        stringResource(if (done == null) R.string.test_running else R.string.test_done),
                        color = Palette.TextSecondary,
                        fontSize = 15.sp,
                    )
                    // Chaque étape prend le focus (◀ depuis les boutons) : ▲ ▼ font défiler la liste et déplient le détail.
                    // Pendant le test, la liste suit l'étape en cours.
                    val scroll = rememberScrollState()
                    LaunchedEffect(steps.size, done) {
                        if (done == null) scroll.animateScrollTo(scroll.maxValue)
                    }
                    Column(Modifier.fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        steps.forEach { StepRow(it) }
                    }
                }
                Column(Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val player = testPlayer
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (player != null) {
                            AndroidView(
                                factory = { context -> PlayerView(context).apply { useController = false } },
                                update = { it.player = player },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(stringResource(R.string.test_video_area), color = Palette.TextMuted, fontSize = 13.sp)
                        }
                    }
                    if (done != null) {
                        ActionButton(stringResource(R.string.test_show_report), { showReport = true }, Modifier.focusRequester(firstButton), glyph = Glyph.Check)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionButton(stringResource(R.string.test_again), { start() }, primary = false, glyph = Glyph.Restart)
                            ActionButton(stringResource(R.string.action_close), { finish() }, primary = false)
                        }
                    } else {
                        ActionButton(stringResource(R.string.action_cancel), { finish() }, Modifier.focusRequester(cancelButton), primary = false)
                    }
                }
            }
        }
        if (showReport && done != null) {
            CrashReportDialog(done, onDismiss = { showReport = false }, title = stringResource(R.string.test_report_title), sendText = { done.take(SEND_MAX) })
        }
    }

    @Composable
    private fun StepRow(step: Step) {
        var focused by remember { mutableStateOf(false) }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (focused) Palette.GlassStrong else Palette.GlassFaint)
                .border(1.5.dp, if (focused) Palette.Accent else Color.Transparent, RoundedCornerShape(14.dp))
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val (glyph, tint) = when (step.status) {
                Status.RUNNING -> Glyph.Dot to Palette.Accent
                Status.OK -> Glyph.Check to Color(0xFF5BE39A)
                Status.WARN -> Glyph.Warning to Color(0xFFFFB86B)
                Status.FAIL -> Glyph.Close to Color(0xFFFF6B6B)
            }
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { GlyphIcon(glyph, tint, 22.dp) }
            Column(Modifier.weight(1f)) {
                Text(step.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val detail = if (step.status == Status.RUNNING) stringResource(R.string.test_step_running) else step.summary
                if (detail.isNotEmpty()) Text(detail, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = if (focused) 4 else 2)
                if (focused && step.lines.isNotEmpty()) {
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        step.lines.take(STEP_LINES).forEach { line ->
                            val tone = when {
                                line.startsWith("✗") -> Color(0xFFFF6B6B)
                                line.startsWith("⚠") -> Color(0xFFFFB86B)
                                else -> Palette.TextMuted
                            }
                            Text(line, color = tone, fontSize = 12.sp, lineHeight = 15.sp)
                        }
                        if (step.lines.size > STEP_LINES) Text("…", color = Palette.TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    private companion object {
        const val MB = 1_048_576L
        /** Lignes de détail montrées sous une étape qui a le focus (le reste est dans le rapport). */
        const val STEP_LINES = 12
        /** Site fictif de la page de test (les scripts internes ne dépendent pas du site). */
        val TEST_SITE = dev.wido.core.Site(id = "selftest", name = "Test", url = "https://wido.test/")
        /** Flux HLS de test public (Big Buck Bunny, plusieurs qualités jusqu'au 1080p). */
        const val TEST_STREAM = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        const val PING_URL = "https://www.google.com/generate_204"
        /** Le relais des rapports refuse au-delà de 8 000 signes. */
        const val SEND_MAX = 7_500
        val CODECS = listOf(
            MimeTypes.VIDEO_H264 to "H.264",
            MimeTypes.VIDEO_H265 to "H.265 (HEVC)",
            MimeTypes.VIDEO_VP9 to "VP9",
            MimeTypes.VIDEO_AV1 to "AV1",
        )
    }
}
