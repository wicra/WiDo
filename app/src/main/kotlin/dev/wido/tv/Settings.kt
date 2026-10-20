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
class Settings(context: Context) {
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
        get() = prefs.getBoolean("resume", true)
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
    }

    private companion object {
        const val KEY_CONFIG_URL = "config_url"
    }
}
