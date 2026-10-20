package dev.wido.tv

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.wido.core.HostBlocker
import dev.wido.core.NavigationPolicy
import dev.wido.core.Site
import java.io.ByteArrayInputStream
import dev.wido.core.Hosts
import dev.wido.core.MediaDetector
import dev.wido.core.MediaStream
import dev.wido.core.RequestType
import dev.wido.core.UrlMatcher
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Applique le blocage de requêtes, la politique de navigation et l'injection de scripts. */
class WiDoWebViewClient(
    site: Site,
    blocker: HostBlocker,
    urls: UrlMatcher,
    private val injector: ScriptInjector,
    private val onNavigationBlocked: (String) -> Unit,
    private val onPageVisible: () -> Unit,
    private val onPageStart: () -> Unit = {},
    /** Mode mixte : la page refusée s'ouvre dans la fenêtre publicitaire. */
    private val onAdWindow: (String) -> Unit = {},
    /** Un flux vidéo lisible par le lecteur natif a été repéré (appelé hors du fil principal). */
    private val onMediaStream: (MediaStream) -> Unit = {},
    /** La page n'a pas pu s'ouvrir (code d'erreur de la WebView, adresse) : l'app affiche son propre écran. */
    private val onPageError: (Int, String) -> Unit = { _, _ -> },
    /** Le moteur de la page s'est arrêté (plantage, ou tué faute de mémoire) : sans ça, Android ferme toute l'appli. */
    private val onRendererGone: () -> Unit = {},
) : WebViewClient() {
    /** Meilleur flux vidéo repéré sur la page affichée. */
    @Volatile var stream: MediaStream? = null
        private set

    @Volatile private var site = site
    @Volatile private var blocker = blocker
    @Volatile private var urls = urls
    /** Hôte de la page affichée : décide des règles `domain=` et de ce qui est « tiers ». */
    @Volatile private var pageHost: String = Hosts.of(site.url).orEmpty()
    @Volatile private var allowOnceUrl: String? = null

    /** Dernière navigation refusée : proposée dans le menu rapide pour être autorisée. */
    @Volatile var lastBlockedUrl: String? = null
        private set

    /** Retire la navigation refusée du menu rapide (« Ignorer »). */
    fun dismissBlocked() {
        lastBlockedUrl = null
    }

    private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val blockedNavigationHosts = CopyOnWriteArrayList<String>()

    /** Ce que le site a tenté depuis le dernier appel : (redirections refusées, domaines tiers contactés). */
    fun drainSeen(): Pair<List<String>, Map<String, Int>> {
        val navigations = blockedNavigationHosts.toList().also { blockedNavigationHosts.clear() }
        val requests = requestCounts.mapValues { it.value.get() }.also { requestCounts.clear() }
        return navigations to requests
    }

    /** Nouvelles règles (après avoir autorisé un domaine) sans recréer la page. */
    fun update(site: Site, blocker: HostBlocker, urls: UrlMatcher = this.urls) {
        this.site = site
        this.blocker = blocker
        this.urls = urls
    }

    /** Laisse passer cette navigation, une seule fois. */
    fun allowOnce(url: String) {
        allowOnceUrl = url
    }

    val blockedRequests = AtomicInteger()
    val blockedNavigations = AtomicInteger()

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val host = request.url.host ?: return null
        if (!blocker.isBlocked(host) && !blockedByAddress(request, host)) {
            MediaDetector.detect(request.url.toString())?.let { found ->
                val next = MediaDetector.better(stream, found)
                if (next != stream) {
                    stream = next
                    onMediaStream(next)
                }
            }
            // Domaine tiers non bloqué : retenu pour proposer de le bloquer.
            if (NavigationPolicy.allowedDomains(site).none { Hosts.matchesPattern(host, it) }) {
                requestCounts.getOrPut(host.lowercase()) { AtomicInteger() }.incrementAndGet()
            }
            return null
        }
        blockedRequests.incrementAndGet()
        return WebResourceResponse(
            "text/plain", "utf-8", 403, "Blocked by WiDo", emptyMap(), ByteArrayInputStream(ByteArray(0)),
        )
    }

    /** Règles d'adresse (chemin, `domain=`, type) ; jamais sur la page principale elle-même. */
    private fun blockedByAddress(request: WebResourceRequest, host: String): Boolean {
        if (request.isForMainFrame) return false
        val url = request.url.toString()
        val page = pageHost
        val type = RequestType.guess(url, request.requestHeaders["Accept"] ?: request.requestHeaders["accept"], false)
        return urls.shouldBlock(url, page, type, Hosts.site(host) != Hosts.site(page))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (url == allowOnceUrl) {
            allowOnceUrl = null
            return false
        }
        when (NavigationPolicy.decide(url, site, blocker)) {
            NavigationPolicy.Decision.ALLOW -> return false
            NavigationPolicy.Decision.AD_WINDOW -> {
                onAdWindow(url)
                return true
            }
            NavigationPolicy.Decision.BLOCK -> Unit
        }
        blockedNavigations.incrementAndGet()
        Hosts.of(url)?.let { blockedNavigationHosts += it }
        lastBlockedUrl = url
        onNavigationBlocked(url)
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        url?.let { Hosts.of(it) }?.let { pageHost = it }
        stream = null
        onPageStart()
        injector.inject(view)
    }

    override fun onPageCommitVisible(view: WebView, url: String?) = onPageVisible()

    // Navigation interne d'une application web (YouTube, Twitch…) : nouvelle vidéo, nouveau flux.
    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (!isReload) stream = null
    }

    override fun onPageFinished(view: WebView, url: String?) {
        injector.inject(view)
        onPageVisible()
    }

    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        onRendererGone()
        return true
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        // Fond sombre à la place de la page d'erreur grise du navigateur ; l'app affiche l'explication.
        view.loadDataWithBaseURL(null, "<html><body style='background:#0B0B12'></body></html>", "text/html", "utf-8", null)
        onPageError(error.errorCode, request.url.toString())
    }
}
