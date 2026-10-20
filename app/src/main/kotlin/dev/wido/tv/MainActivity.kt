package dev.wido.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wido.tv.ui.MenuScreen
import dev.wido.tv.ui.WiDoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launched = handleIntent(intent)
        // Option « Rouvrir le dernier site » : seulement au lancement depuis l'accueil de la TV.
        val settings = (application as WiDoApp).settings
        if (!launched && savedInstanceState == null && intent.action == Intent.ACTION_MAIN && settings.startupLastSite) {
            settings.lastSiteId?.let { startActivity(PlayerActivity.intent(this, it)) }
        }
        setContent {
            WiDoTheme {
                val vm: MenuViewModel = viewModel()
                MenuScreen(vm) { site -> startActivity(PlayerActivity.intent(this, site.id)) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ViewModelProvider(this)[MenuViewModel::class.java].onForeground()
    }

    override fun onResume() {
        super.onResume()
        // Une fois l'accueil affiché, une WebView se prépare au calme : le prochain site s'ouvrira vite.
        window.decorView.postDelayed({ if (!isFinishing) WebViewPool.prepare(this) }, PREPARE_DELAY_MS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Extras acceptés (utiles depuis `adb` / Home Assistant) :
     * - `config_url` : change l'URL de configuration (https uniquement, mémorisée) ;
     * - `site` : ouvre directement ce site.
     */
    private fun handleIntent(intent: Intent): Boolean {
        val app = application as WiDoApp
        intent.getStringExtra(EXTRA_CONFIG_URL)?.let { app.settings.configUrl = it }
        val site = intent.getStringExtra(PlayerActivity.EXTRA_SITE) ?: return false
        intent.removeExtra(PlayerActivity.EXTRA_SITE)
        startActivity(PlayerActivity.intent(this, site))
        return true
    }

    private companion object {
        const val EXTRA_CONFIG_URL = "config_url"
        const val PREPARE_DELAY_MS = 1_500L
    }
}
