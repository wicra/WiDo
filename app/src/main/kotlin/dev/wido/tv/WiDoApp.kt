package dev.wido.tv

import android.app.Application
import android.content.ComponentCallbacks2

class WiDoApp : Application() {
    val settings by lazy { Settings(this) }
    val repository by lazy { ConfigRepository(this, settings) }
    val favicons by lazy { FaviconRepository(this) }
    val updates by lazy { UpdateManager(this, BuildConfig.GITHUB_REPO) }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) WebViewPool.clear()
    }
}
