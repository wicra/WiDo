package dev.wido.tv

import android.app.Application
import android.content.ComponentCallbacks2

class WiDoApp : Application() {
    val settings by lazy { Settings(this) }
    val repository by lazy { ConfigRepository(this, settings) }
    val favicons by lazy { FaviconRepository(this) }
    val thumbnails by lazy { Thumbnails(this) }
    /** Lien avec les autres écrans (téléphone, autres TV) : appairage, pilotage, synchro. */
    val link by lazy { ScreenLink(this) }

    /** TV ou téléphone : l'interface s'adapte (télécommande et curseur, ou écran tactile). */
    val isTv: Boolean get() = link.isTv

    val remote by lazy {
        RemoteServer(settings, { link }) {
            dev.wido.core.RemoteProtocol.Labels(
                touchpad = getString(R.string.remote_touchpad),
                keys = getString(R.string.remote_keys),
                text = getString(R.string.remote_text),
                link = getString(R.string.remote_link),
                touchpadHint = getString(R.string.remote_touchpad_hint),
                scroll = getString(R.string.remote_scroll),
                placeholder = getString(R.string.phone_placeholder),
                send = getString(R.string.phone_send),
                sendEnter = getString(R.string.phone_send_enter),
                linkPlaceholder = getString(R.string.remote_link_placeholder),
                open = getString(R.string.remote_open),
                sent = getString(R.string.phone_sent),
                failed = getString(R.string.phone_failed),
            )
        }
    }
    val updates by lazy { UpdateManager(this, BuildConfig.GITHUB_REPO) }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) WebViewPool.clear()
    }
}
