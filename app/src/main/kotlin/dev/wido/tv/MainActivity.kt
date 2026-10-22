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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.wido.core.LinkProtocol
import dev.wido.tv.ui.MenuScreen
import dev.wido.tv.ui.Onboarding
import dev.wido.tv.ui.PairPromptHost
import dev.wido.tv.ui.PhoneHome
import dev.wido.tv.ui.WiDoTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity(), RemoteTarget {
    private val app get() = application as WiDoApp

    /** QR code d'appairage scanné (`wido://pair…`) : le téléphone s'occupe de se lier à la TV. */
    private val pairTarget = MutableStateFlow<LinkProtocol.PairTarget?>(null)

    /** Une vidéo vient de partir sur la TV : l'onglet TV s'affiche, pour la piloter. */
    private val showTvTab = MutableStateFlow(false)

    /** Double appui sur Retour dans le lecteur : retour à l'onglet Sites (compteur, pour rejouer). */
    private val sitesRequest = MutableStateFlow(0)

    /** Un site perso à modifier dès l'ouverture (son adresse ne répond plus). */
    private val editRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launched = handleIntent(intent)
        // Option « Rouvrir le dernier site » : seulement au lancement depuis l'accueil de la TV.
        val settings = app.settings
        // L'appli s'est fermée sur une erreur la dernière fois : on le dit une fois, le rapport est dans les réglages.
        if (savedInstanceState == null && CrashLog.hasUnseenCrash()) {
            CrashLog.markSeen()
            android.widget.Toast.makeText(this, R.string.crash_toast, android.widget.Toast.LENGTH_LONG).show()
        } else if (savedInstanceState == null && settings.openingSite) {
            // Arrêtée pendant l'ouverture d'un site (souvent la mémoire) : on le dit, au lieu d'un retour muet à l'accueil.
            android.widget.Toast.makeText(this, R.string.open_interrupted, android.widget.Toast.LENGTH_LONG).show()
        }
        if (savedInstanceState == null && settings.openingSite) {
            settings.openingSite = false
            CrashLog.event("Ouverture d'un site interrompue : WiDo s'est arrêté avant l'affichage de la page")
        }
        if (app.isTv) UiSound.init(this, settings.uiSounds)
        if (app.isTv && !launched && savedInstanceState == null && intent.action == Intent.ACTION_MAIN && settings.startupLastSite) {
            settings.lastSiteId?.let { startActivity(PlayerActivity.intent(this, it)) }
        }
        setContent {
            WiDoTheme {
                val vm: MenuViewModel = viewModel()
                val onOpen = { site: dev.wido.core.Site -> openPlayer(PlayerActivity.intent(this, site.id)) }
                val onOpenPin = { pin: dev.wido.core.Pin -> openPlayer(PlayerActivity.intent(this, pin.siteId, pin.url)) }
                val onOpenWatch = { entry: dev.wido.core.WatchEntry -> openPlayer(PlayerActivity.intent(this, entry.siteId, entry.url, entry.position, autoplay = true)) }
                if (app.isTv && !app.settings.onboardingDone) {
                    // Premier lancement : présentation en trois écrans.
                    Onboarding(vm) { app.settings.onboardingDone = true }
                } else if (app.isTv) {
                    val edit by editRequest.collectAsState()
                    val sitesSignal by sitesRequest.collectAsState()
                    MenuScreen(vm, onOpen, onOpenPin, onOpenWatch, editSiteId = edit, onEditHandled = { editRequest.value = null }, sitesSignal = sitesSignal)
                    // Un téléphone du réseau demande à se lier : la TV demande confirmation.
                    PairPromptHost(app.link)
                    // Des sites proposés par un téléphone lié : toujours confirmés ici, à l'écran.
                    dev.wido.tv.ui.SharePromptHost(app.link)
                } else {
                    val target by pairTarget.collectAsState()
                    val showTv by showTvTab.collectAsState()
                    PhoneHome(
                        vm,
                        app.link,
                        onOpen = onOpen,
                        onOpenPin = onOpenPin,
                        onOpenWatch = onOpenWatch,
                        // « Reprendre sur le téléphone » : la page de la TV, à la même seconde.
                        onPlayHere = { now -> startActivity(PlayerActivity.intent(this, ConfigRepository.LINK_SITE_ID, now.url, now.position, autoplay = true)) },
                        pairTarget = target,
                        onPairTargetHandled = { pairTarget.value = null },
                        showTv = showTv,
                        onShowTvHandled = { showTvTab.value = false },
                        editSiteId = editRequest.collectAsState().value,
                        onEditHandled = { editRequest.value = null },
                    )
                }
            }
        }
    }

    /**
     * TV : le lecteur s'ouvre sans animation du système (saccadée avec une page web) ; c'est lui qui
     * agrandit le cercle sélectionné jusqu'à remplir l'écran, sur le fond de l'appli.
     */
    private fun openPlayer(intent: Intent) {
        CrashLog.trace("accueil : ouverture d'un site (${CrashLog.memoryLine(this)})")
        val from = if (app.isTv) dev.wido.tv.ui.LaunchOrigin.bounds else null
        if (app.isTv) UiSound.open()
        if (from == null) {
            startActivity(intent)
            return
        }
        intent.putExtra(PlayerActivity.EXTRA_ORIGIN, intArrayOf(from.left, from.top, from.right, from.bottom))
            .putExtra(PlayerActivity.EXTRA_COLOR, dev.wido.tv.ui.LaunchOrigin.color)
            .putExtra(PlayerActivity.EXTRA_TILE, dev.wido.tv.ui.LaunchOrigin.tile)
        LaunchSnapshot.capture(this) {
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onStart() {
        super.onStart()
        ViewModelProvider(this)[MenuViewModel::class.java].onForeground()
        // Téléphone : recherche des TV, état de celle choisie, synchro de « mes écrans ».
        if (!app.isTv) {
            app.link.startPolling()
            askNotifications()
        }
    }

    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    /** Une fois, quand un écran est lié : les notifications de lecture (TV pilotée, arrière-plan). */
    private fun askNotifications() {
        if (android.os.Build.VERSION.SDK_INT < 33 || app.settings.notificationsAsked || app.link.peers.value.isEmpty()) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        app.settings.notificationsAsked = true
        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onStop() {
        if (!app.isTv) app.link.stopPolling()
        super.onStop()
    }

    private val debounce = KeyDebounce()

    /** Téléphone, onglet TV affiché : les touches de volume règlent le son de la TV. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (app.isTv && debounce.isBounce(event)) return true
        if (!app.isTv && app.link.volumeToTv && app.link.selectedOnline &&
            (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) app.link.key(if (code == KeyEvent.KEYCODE_VOLUME_UP) RemoteKey.VOLUME_UP else RemoteKey.VOLUME_DOWN)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        (application as WiDoApp).remote.detach(this)
        // Téléphone : la TV lit une vidéo, la notification prend le relais (encore visible : démarrage permis).
        if (!app.isTv && !isChangingConfigurations) TvRemoteService.startIfPlaying(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!app.isTv) TvRemoteService.stop(this)
        (application as WiDoApp).remote.attach(this)
        // Une fois l'accueil affiché, une WebView se prépare au calme : le prochain site s'ouvrira vite.
        // Pas sur un appareil à peu de mémoire : une WebView de plus, c'est la vidéo qui serait fermée.
        if (!app.lowMemory) window.decorView.postDelayed({ if (!isFinishing) WebViewPool.prepare(this) }, PREPARE_DELAY_MS)
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
        intent.getStringExtra(EXTRA_EDIT_SITE)?.let {
            intent.removeExtra(EXTRA_EDIT_SITE)
            editRequest.value = it
            return true
        }
        if (intent.getBooleanExtra(EXTRA_SHOW_SITES, false)) {
            intent.removeExtra(EXTRA_SHOW_SITES)
            sitesRequest.value++
            return true
        }
        if (intent.getBooleanExtra(EXTRA_SHOW_TV, false)) {
            intent.removeExtra(EXTRA_SHOW_TV)
            showTvTab.value = true
            return true
        }
        intent.dataString?.let { LinkProtocol.parsePairUri(it) }?.let { target ->
            if (!app.isTv) pairTarget.value = target
            return true
        }
        intent.getStringExtra(EXTRA_CONFIG_URL)?.let { app.settings.configUrl = it }
        val site = intent.getStringExtra(PlayerActivity.EXTRA_SITE) ?: return false
        intent.removeExtra(PlayerActivity.EXTRA_SITE)
        startActivity(PlayerActivity.intent(this, site))
        return true
    }


    companion object {
        private const val EXTRA_SHOW_TV = "show_tv"

        private const val EXTRA_EDIT_SITE = "edit_site"
        private const val EXTRA_SHOW_SITES = "show_sites"

        /** L'accueil, sur l'onglet Sites (double appui sur Retour dans le lecteur). */
        fun showSites(context: android.content.Context): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_SHOW_SITES, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** L'accueil, avec le formulaire d'un site perso ouvert (adresse à corriger). */
        fun editSite(context: android.content.Context, siteId: String): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_EDIT_SITE, siteId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** L'accueil du téléphone, sur l'onglet TV (après « Sur la TV »). */
        fun showTv(context: android.content.Context): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_SHOW_TV, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        private const val EXTRA_CONFIG_URL = "config_url"
        private const val PREPARE_DELAY_MS = 1_500L
        private const val MOVE_STEP = 60f
    }
}
