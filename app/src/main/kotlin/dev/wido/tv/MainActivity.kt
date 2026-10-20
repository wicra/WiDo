package dev.wido.tv

import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import dev.wido.core.RemoteCommand
import dev.wido.core.RemoteKey
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wido.tv.ui.MenuScreen
import dev.wido.tv.ui.WiDoTheme

class MainActivity : ComponentActivity(), RemoteTarget {
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
                MenuScreen(
                    vm,
                    onOpen = { site -> startActivity(PlayerActivity.intent(this, site.id)) },
                    onOpenPin = { pin -> startActivity(PlayerActivity.intent(this, pin.siteId, pin.url)) },
                    onOpenWatch = { entry -> startActivity(PlayerActivity.intent(this, entry.siteId, entry.url, entry.position)) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ViewModelProvider(this)[MenuViewModel::class.java].onForeground()
    }

    override fun onPause() {
        (application as WiDoApp).remote.detach(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        (application as WiDoApp).remote.attach(this)
        // Une fois l'accueil affiché, une WebView se prépare au calme : le prochain site s'ouvrira vite.
        window.decorView.postDelayed({ if (!isFinishing) WebViewPool.prepare(this) }, PREPARE_DELAY_MS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // --- Télécommande sur téléphone : sur l'accueil, le pavé et les touches pilotent le focus. ---

    private var moveX = 0f
    private var moveY = 0f

    override fun onRemote(command: RemoteCommand) {
        when (command) {
            is RemoteCommand.Key -> when (command.key) {
                RemoteKey.VOLUME_UP -> volume(AudioManager.ADJUST_RAISE)
                RemoteKey.VOLUME_DOWN -> volume(AudioManager.ADJUST_LOWER)
                RemoteKey.MUTE -> volume(AudioManager.ADJUST_TOGGLE_MUTE)
                RemoteKey.HOME -> Unit
                else -> pressKey(keyCodeOf(command.key))
            }
            // Le pavé tactile devient des flèches : un pas tous les 60 points de glissement.
            is RemoteCommand.Move -> {
                moveX += command.dx
                moveY += command.dy
                while (moveX >= MOVE_STEP) { pressKey(KeyEvent.KEYCODE_DPAD_RIGHT); moveX -= MOVE_STEP }
                while (moveX <= -MOVE_STEP) { pressKey(KeyEvent.KEYCODE_DPAD_LEFT); moveX += MOVE_STEP }
                while (moveY >= MOVE_STEP) { pressKey(KeyEvent.KEYCODE_DPAD_DOWN); moveY -= MOVE_STEP }
                while (moveY <= -MOVE_STEP) { pressKey(KeyEvent.KEYCODE_DPAD_UP); moveY += MOVE_STEP }
            }
            RemoteCommand.Tap -> pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
            is RemoteCommand.Open -> startActivity(PlayerActivity.intent(this, ConfigRepository.LINK_SITE_ID, command.url))
            is RemoteCommand.Type, is RemoteCommand.Scroll, is RemoteCommand.Seek -> Unit
        }
    }

    private fun pressKey(code: Int) {
        val now = SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        dispatchKeyEvent(KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
    }

    private fun volume(direction: Int) {
        (getSystemService(AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    /**
     * Extras acceptés (utiles depuis `adb` / Home Assistant) :
     * - `config_url` : change l'URL de configuration (https uniquement, mémorisée) ;
     * - `site` : ouvre directement ce site (`last` : le dernier site ouvert).
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
        const val MOVE_STEP = 60f
    }
}
