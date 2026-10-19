package dev.wido.tv

import android.app.Application

class WiDoApp : Application() {
    val settings by lazy { Settings(this) }
    val repository by lazy { ConfigRepository(this, settings) }
    val updates by lazy { UpdateManager(this, BuildConfig.GITHUB_REPO) }
}
