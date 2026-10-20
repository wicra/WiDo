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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Applique le blocage de requêtes, la politique de navigation et l'injection de scripts. */
class WiDoWebViewClient(
    site: Site,
    blocker: HostBlocker,
    private val injector: ScriptInjector,
    private val onNavigationBlocked: (String) -> Unit,
    private val onPageVisible: () -> Unit,
    private val onPageStart: () -> Unit = {},
    /** Mode mixte : la page refusée s'ouvre dans la fenêtre publicitaire. */
    private val onAdWindow: (String) -> Unit = {},
) : WebViewClient() {
    @Volatile private var site = site
    @Volatile private var blocker = blocker
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
    fun update(site: Site, blocker: HostBlocker) {
        this.site = site
        this.blocker = blocker
    }

    /** Laisse passer cette navigation, une seule fois. */
    fun allowOnce(url: String) {
        allowOnceUrl = url
    }

    val blockedRequests = AtomicInteger()
    val blockedNavigations = AtomicInteger()

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val host = request.url.host ?: return null
        if (!blocker.isBlocked(host)) {
            // Domaine tiers non bloqué : retenu pour proposer de le bloquer.
            if (NavigationPolicy.allowedDomains(site).none { Hosts.matches(host, it) }) {
                requestCounts.getOrPut(host.lowercase()) { AtomicInteger() }.incrementAndGet()
            }
            return null
        }
        blockedRequests.incrementAndGet()
        return WebResourceResponse(
            "text/plain", "utf-8", 403, "Blocked by WiDo", emptyMap(), ByteArrayInputStream(ByteArray(0)),
        )
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
        onPageStart()
        injector.inject(view)
    }

    override fun onPageCommitVisible(view: WebView, url: String?) = onPageVisible()

    override fun onPageFinished(view: WebView, url: String?) {
        injector.inject(view)
        onPageVisible()
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        val message = android.text.Html.escapeHtml(error.description?.toString().orEmpty())
        view.loadDataWithBaseURL(
            null,
            "<html><body style='background:#0B0D12;color:#fff;font:28px sans-serif;padding:64px'>" +
                "<h1>${android.text.Html.escapeHtml(site.name)}</h1><p>$message</p></body></html>",
            "text/html", "utf-8", null,
        )
    }
}
