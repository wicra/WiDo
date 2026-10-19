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
import java.util.concurrent.atomic.AtomicInteger

/** Applique le blocage de requêtes, la politique de navigation et l'injection de scripts. */
class WiDoWebViewClient(
    private val site: Site,
    private val blocker: HostBlocker,
    private val injector: ScriptInjector,
    private val onNavigationBlocked: (String) -> Unit,
) : WebViewClient() {

    val blockedRequests = AtomicInteger()
    val blockedNavigations = AtomicInteger()

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val host = request.url.host ?: return null
        if (!blocker.isBlocked(host)) return null
        blockedRequests.incrementAndGet()
        return WebResourceResponse(
            "text/plain", "utf-8", 403, "Blocked by WiDo", emptyMap(), ByteArrayInputStream(ByteArray(0)),
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (NavigationPolicy.isAllowed(url, site)) return false
        blockedNavigations.incrementAndGet()
        onNavigationBlocked(url)
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = injector.inject(view)

    override fun onPageFinished(view: WebView, url: String?) = injector.inject(view)

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
