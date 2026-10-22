package dev.wido.tv

import android.content.Context
import dev.wido.core.Hosts
import dev.wido.core.Navigation
import dev.wido.core.Zoom

/** Ce que fait une touche de couleur de la télécommande. */
enum class KeyAction {
    NONE, MENU, RELOAD, TOGGLE_CURSOR, ZOOM_IN, ZOOM_OUT, QUICK_MENU, SAVE_PAGE, SCROLL_TOP, NATIVE_PLAYER;

    companion object {
        fun parse(name: String?, default: KeyAction): KeyAction = entries.firstOrNull { it.name == name } ?: default
    }
}

/** Réglages locaux, modifiables depuis l'écran « Réglages » du menu. */
class Settings(private val context: Context) {
    private val prefs = context.getSharedPreferences("wido", Context.MODE_PRIVATE)

    /** Ce que l'app retient en usage (dernière page de chaque site, ordre des cartes) : jamais touché par « Réinitialiser ». */
    private val state = context.getSharedPreferences("wido_state", Context.MODE_PRIVATE)

    // --- Configuration distante -------------------------------------------------------------
    var configUrl: String
        get() = prefs.getString(KEY_CONFIG_URL, null) ?: BuildConfig.CONFIG_URL
        set(value) {
            if (Hosts.schemeOf(value) == "https" && Hosts.of(value) != null) {
                prefs.edit().putString(KEY_CONFIG_URL, value).apply()
            }
        }

    fun resetConfigUrl() = prefs.edit().remove(KEY_CONFIG_URL).apply()

    // --- Curseur ----------------------------------------------------------------------------
    /** Multiplicateur de vitesse du curseur. */
    var cursorSpeed: Float
        get() = prefs.getFloat("cursor_speed", 1.0f)
        set(v) = prefs.edit().putFloat("cursor_speed", v).apply()

    /** Taille de la flèche, en multiple de sa taille de base. */
    var cursorSize: Float
        get() = prefs.getFloat("cursor_size", 1.8f)
        set(v) = prefs.edit().putFloat("cursor_size", v).apply()

    /** Secondes avant que le curseur ne se masque ; 0 = jamais. */
    var cursorHideSeconds: Int
        get() = prefs.getInt("cursor_hide", 3)
        set(v) = prefs.edit().putInt("cursor_hide", v).apply()

    /** Une flèche saute au prochain lien ou bouton, au lieu d'avancer pixel par pixel. */
    var snapNavigation: Boolean
        get() = prefs.getBoolean("snap", true)
        set(v) = prefs.edit().putBoolean("snap", v).apply()

    // --- Affichage --------------------------------------------------------------------------
    var defaultZoom: Int
        get() = Zoom.clamp(prefs.getInt("zoom", Zoom.DEFAULT))
        set(v) = prefs.edit().putInt("zoom", Zoom.clamp(v)).apply()

    fun zoomFor(siteId: String): Int = Zoom.clamp(prefs.getInt("zoom_$siteId", defaultZoom))

    fun setZoomFor(siteId: String, percent: Int) = prefs.edit().putInt("zoom_$siteId", Zoom.clamp(percent)).apply()

    // --- Lecture ----------------------------------------------------------------------------
    var seekSeconds: Int
        get() = prefs.getInt("seek", 10)
        set(v) = prefs.edit().putInt("seek", v).apply()

    /** Secondes d'affichage de la barre de lecture et de l'indicateur de volume. */
    var osdSeconds: Int
        get() = prefs.getInt("osd", 4)
        set(v) = prefs.edit().putInt("osd", v).apply()

    /** Reprendre et « Sur la TV » : la vidéo démarre et passe en plein écran toute seule. */
    var autoFullscreen: Boolean
        get() = prefs.getBoolean("auto_fullscreen", true)
        set(v) = prefs.edit().putBoolean("auto_fullscreen", v).apply()

    /**
     * Page vidéo ouverte dans le lecteur, effacée à sa fermeture normale. Encore là à la réouverture :
     * la session précédente s'est mal terminée (plantage, appli tuée) ; on n'y relance pas la lecture seule.
     */
    var openPage: String?
        get() = state.getString("open_page", null)
        set(v) = state.edit().putString("open_page", v).apply()

    /**
     * Un site est en train de s'ouvrir (lecteur créé, page pas encore affichée). Encore là au démarrage
     * suivant : l'appli a été arrêtée pendant l'ouverture, l'accueil le dit au lieu de rien expliquer.
     */
    var openingSite: Boolean
        get() = state.getBoolean("opening_site", false)
        set(v) {
            state.edit().putBoolean("opening_site", v).commit()
        }

    /** Journal détaillé : les gestes et décisions de l'appli sont notés sur l'appareil (sans noms de sites). */
    var detailedLog: Boolean
        get() = prefs.getBoolean("detailed_log", false)
        set(v) = prefs.edit().putBoolean("detailed_log", v).apply()

    /** Deux appuis rapides sur Retour dans le lecteur : retour direct aux sites (sinon : reculer de page en page). */
    var doubleBackToSites: Boolean
        get() = prefs.getBoolean("double_back", true)
        set(v) = prefs.edit().putBoolean("double_back", v).apply()

    /** Sons de l'interface sur TV : coupés, doux ou normaux ([UiSound]). */
    var uiSounds: Int
        get() = prefs.getInt("ui_sounds", UiSound.NORMAL)
        set(v) {
            prefs.edit().putInt("ui_sounds", v).apply()
            UiSound.setLevel(v)
        }

    /** Les touches de volume règlent le volume du système avec l'indicateur de WiDo. */
    var customVolume: Boolean
        get() = prefs.getBoolean("custom_volume", true)
        set(v) = prefs.edit().putBoolean("custom_volume", v).apply()

    /** En plein écran vidéo : flèches = avance/recul et volume, OK = lecture/pause. */
    var fullscreenPlayerKeys: Boolean
        get() = prefs.getBoolean("fs_keys", true)
        set(v) = prefs.edit().putBoolean("fs_keys", v).apply()

    // --- Touches de couleur -----------------------------------------------------------------
    var keyRed: KeyAction
        get() = KeyAction.parse(prefs.getString("key_red", null), KeyAction.MENU)
        set(v) = prefs.edit().putString("key_red", v.name).apply()
    var keyGreen: KeyAction
        get() = KeyAction.parse(prefs.getString("key_green", null), KeyAction.RELOAD)
        set(v) = prefs.edit().putString("key_green", v.name).apply()
    var keyYellow: KeyAction
        get() = KeyAction.parse(prefs.getString("key_yellow", null), KeyAction.ZOOM_IN)
        set(v) = prefs.edit().putString("key_yellow", v.name).apply()
    var keyBlue: KeyAction
        get() = KeyAction.parse(prefs.getString("key_blue", null), KeyAction.ZOOM_OUT)
        set(v) = prefs.edit().putString("key_blue", v.name).apply()

    // --- Démarrage et nouveaux sites --------------------------------------------------------
    var startupLastSite: Boolean
        get() = prefs.getBoolean("startup_last", false)
        set(v) = prefs.edit().putBoolean("startup_last", v).apply()

    var lastSiteId: String?
        get() = prefs.getString("last_site", null)
        set(v) = prefs.edit().putString("last_site", v).apply()

    /** Mode de redirection proposé par défaut pour un nouveau site perso (strict, souple ou mixte). */
    var newSitesNavigation: String
        get() = prefs.getString("new_navigation", null)?.takeIf { it in Navigation.ALL }
            ?: if (prefs.getBoolean("new_relaxed", false)) Navigation.RELAXED else Navigation.STRICT
        set(v) = prefs.edit().putString("new_navigation", v).apply()

    // --- Blocage ----------------------------------------------------------------------------
    /**
     * Lecteur léger automatique : quand un film est détecté, WiDo passe seul au lecteur intégré (bien
     * moins gourmand que la page du site, décodage matériel, reprise à la seconde). C'est le chemin normal :
     * activé par défaut partout ; la page sert à choisir le film.
     */
    var autoNative: Boolean
        get() = prefs.getBoolean("auto_native", true)
        set(v) = prefs.edit().putBoolean("auto_native", v).apply()

    /**
     * Listes légères : seulement le filtre DNS d'AdGuard et les correctifs d'uBlock (peu de mémoire), sans
     * EasyList, la liste française ni les filtres d'uBlock. Par défaut, activé sur les appareils à peu de mémoire.
     */
    var lightLists: Boolean
        get() = prefs.getBoolean("light_lists", DeviceMemory.isLow(context))
        set(v) = prefs.edit().putBoolean("light_lists", v).apply()

    /** Ajoute EasyList, la liste française et le filtre DNS d'AdGuard aux listes de la configuration. */
    var recommendedLists: Boolean
        get() = prefs.getBoolean("recommended_lists", true)
        set(v) = prefs.edit().putBoolean("recommended_lists", v).apply()

    /** YouTube sans publicité : pubs retirées des réponses du lecteur, sautées si elles passent quand même. */
    var youtubeAdblock: Boolean
        get() = prefs.getBoolean("youtube_adblock", true)
        set(v) = prefs.edit().putBoolean("youtube_adblock", v).apply()

    /** Saute les passages sponsorisés des vidéos YouTube (base collaborative SponsorBlock). */
    var sponsorBlock: Boolean
        get() = prefs.getBoolean("sponsorblock", true)
        set(v) = prefs.edit().putBoolean("sponsorblock", v).apply()

    // --- Reprise et ordre --------------------------------------------------------------------
    /** Ouvrir un site là où on l'avait laissé, plutôt que sur son accueil. */
    var resumeLastPage: Boolean
        get() = prefs.getBoolean("resume", false)
        set(v) = prefs.edit().putBoolean("resume", v).apply()

    fun lastUrlFor(siteId: String): String? = state.getString("last_url_$siteId", null)

    fun setLastUrlFor(siteId: String, url: String?) = state.edit().apply {
        if (url == null) remove("last_url_$siteId") else putString("last_url_$siteId", url)
    }.apply()

    /** Position (ms) d'une vidéo dans le lecteur natif, par page : pour reprendre à la seconde près. */
    fun nativePositionFor(key: String): Long = state.getLong("native_" + key.hashCode().toUInt().toString(16), 0)

    fun setNativePositionFor(key: String, positionMs: Long) =
        state.edit().putLong("native_" + key.hashCode().toUInt().toString(16), positionMs).apply()

    /** Ordre des cartes choisi par l'utilisateur (identifiants de sites). */
    var siteOrder: List<String>
        get() = state.getString("site_order", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        set(v) = state.edit().putString("site_order", v.joinToString("\n")).apply()

    /** Un QR code s'affiche quand un champ de la page est sélectionné, pour taper depuis le téléphone. */
    var fieldPhoneQr: Boolean
        get() = prefs.getBoolean("field_qr", true)
        set(v) = prefs.edit().putBoolean("field_qr", v).apply()

    /** Télécommande sur téléphone : serveur local ouvert tant que WiDo est à l'écran. */
    var phoneRemote: Boolean
        get() = prefs.getBoolean("phone_remote", false)
        set(v) = prefs.edit().putBoolean("phone_remote", v).apply()

    /** Jeton permanent de la télécommande sur téléphone (dans l'adresse du QR code). */
    val remoteToken: String
        get() = state.getString("remote_token", null) ?: newRemoteToken()

    /** Change le jeton : les téléphones déjà appairés doivent rescanner le QR code. */
    fun newRemoteToken(): String {
        val token = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        state.edit().putString("remote_token", token).apply()
        return token
    }

    // --- Écrans liés (application sur téléphone, autres TV) -----------------------------------
    /** Les applis WiDo du réseau (téléphone, autres TV) peuvent trouver cette TV et demander à s'y lier. */
    var screenLink: Boolean
        get() = prefs.getBoolean("screen_link", true)
        set(v) = prefs.edit().putBoolean("screen_link", v).apply()

    /** Identifiant stable de cet appareil, tiré au hasard au premier lancement. */
    val deviceId: String
        get() = state.getString("device_id", null) ?: dev.wido.core.LinkProtocol.randomToken(8).also {
            state.edit().putString("device_id", it).apply()
        }

    /** Nom montré aux autres écrans (« Salon », « Pixel de Sergio »…). */
    var deviceName: String
        get() = state.getString("device_name", null) ?: defaultDeviceName()
        set(v) = state.edit().putString("device_name", v.trim().take(40).ifEmpty { defaultDeviceName() }).apply()

    private fun defaultDeviceName(): String =
        runCatching { android.provider.Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL.orEmpty().ifBlank { "WiDo" }

    /** Appareils liés (JSON), vus depuis celui-ci. */
    /** Packs de sites enregistrés sur ce téléphone (partage vers une TV). */
    var sitePacks: List<dev.wido.core.SitePack>
        get() = dev.wido.core.SiteSharing.decodePacks(state.getString("site_packs", null))
        set(v) = state.edit().putString("site_packs", dev.wido.core.SiteSharing.encodePacks(v)).apply()

    var peersJson: String
        get() = state.getString("peers", null) ?: "[]"
        set(v) = state.edit().putString("peers", v).apply()

    /** Groupe de « mes écrans » (JSON), ou null tant qu'aucun n'est lié. */
    var groupJson: String?
        get() = state.getString("group", null)
        set(v) = state.edit().putString("group", v).apply()

    /** Clé du QR code d'appairage de la TV : changée après chaque utilisation. */
    val pairKey: String
        get() = state.getString("pair_key", null) ?: newPairKey()

    fun newPairKey(): String = dev.wido.core.LinkProtocol.randomToken(8).also { state.edit().putString("pair_key", it).apply() }

    /** Téléphone : la TV pilotée par défaut. */
    var selectedScreen: String?
        get() = state.getString("selected_screen", null)
        set(v) = state.edit().putString("selected_screen", v).apply()

    // --- Interface (observés par Compose : l'écran se redessine aussitôt) ----------------------
    private val uiScaleState = androidx.compose.runtime.mutableFloatStateOf(prefs.getFloat("ui_scale", 1f))

    /** Taille de l'interface sur TV : 1 (normale), 1,15 (grande), 1,3 (très grande). */
    var uiScale: Float
        get() = uiScaleState.floatValue
        set(v) {
            prefs.edit().putFloat("ui_scale", v).apply()
            uiScaleState.floatValue = v
        }

    /** Ambiance de couleur (bleu, violet, ambre, noir). */
    var ambience: String
        get() = dev.wido.tv.ui.Ambience.mode
        set(v) {
            val mode = v.takeIf { it in dev.wido.tv.ui.Ambience.ALL } ?: dev.wido.tv.ui.Ambience.BLUE
            prefs.edit().putString("ambience", mode).apply()
            dev.wido.tv.ui.Ambience.mode = mode
        }

    init {
        dev.wido.tv.ui.Ambience.mode = prefs.getString("ambience", null)?.takeIf { it in dev.wido.tv.ui.Ambience.ALL } ?: dev.wido.tv.ui.Ambience.BLUE
    }

    // --- Pubs bloquées (compteur du mois) -----------------------------------------------------
    private fun month() = java.text.SimpleDateFormat("yyyyMM", java.util.Locale.ROOT).format(java.util.Date())

    /** Ajoute les requêtes bloquées d'une visite au compteur du mois (par site et au total). */
    fun addBlocked(siteId: String, count: Int) {
        if (count <= 0) return
        val m = month()
        state.edit()
            .putInt("blocked_${m}_$siteId", state.getInt("blocked_${m}_$siteId", 0) + count)
            .putInt("blocked_$m", state.getInt("blocked_$m", 0) + count)
            .apply()
    }

    /** Pubs et pisteurs bloqués ce mois-ci, pour un site ou au total. */
    fun blockedThisMonth(siteId: String? = null): Int =
        state.getInt(if (siteId == null) "blocked_${month()}" else "blocked_${month()}_$siteId", 0)

    private val onboardingState = androidx.compose.runtime.mutableStateOf(
        // Déjà utilisé avant cette version : pas de présentation.
        state.getBoolean("onboarding_done", prefs.contains("last_site") || prefs.contains("help_seen")),
    )

    /** La présentation du premier lancement a été vue (ou passée). */
    var onboardingDone: Boolean
        get() = onboardingState.value
        set(v) {
            state.edit().putBoolean("onboarding_done", v).apply()
            onboardingState.value = v
        }

    // --- Téléphone : notifications de lecture -------------------------------------------------
    /** Notification qui pilote la TV quand elle lit une vidéo (et volume du téléphone → TV). */
    var tvNotification: Boolean
        get() = prefs.getBoolean("tv_notification", true)
        set(v) = prefs.edit().putBoolean("tv_notification", v).apply()

    /** La vidéo continue (le son) quand on quitte l'appli ou éteint l'écran du téléphone. */
    var backgroundPlay: Boolean
        get() = prefs.getBoolean("background_play", true)
        set(v) = prefs.edit().putBoolean("background_play", v).apply()

    /** La permission des notifications a déjà été demandée une fois. */
    var notificationsAsked: Boolean
        get() = state.getBoolean("notif_asked", false)
        set(v) = state.edit().putBoolean("notif_asked", v).apply()

    var helpSeen: Boolean
        get() = prefs.getBoolean("help_seen", false)
        set(v) = prefs.edit().putBoolean("help_seen", v).apply()

    /** Remet tous les réglages de confort à leur valeur d'origine (ne touche ni l'URL de config, ni les sites). */
    fun resetComfort() {
        val keep = prefs.getString(KEY_CONFIG_URL, null)
        val last = lastSiteId
        prefs.edit().clear().apply()
        if (keep != null) prefs.edit().putString(KEY_CONFIG_URL, keep).apply()
        if (last != null) prefs.edit().putString("last_site", last).apply()
        uiScaleState.floatValue = 1f
        dev.wido.tv.ui.Ambience.mode = dev.wido.tv.ui.Ambience.BLUE
        UiSound.setLevel(UiSound.NORMAL)
    }

    private companion object {
        const val KEY_CONFIG_URL = "config_url"
    }
}
