package dev.wido.tv

import android.content.Context
import dev.wido.core.Hosts

/** Réglages locaux. Seule l'URL de configuration est modifiable (par intent, voir docs/HOME-ASSISTANT.md). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("wido", Context.MODE_PRIVATE)

    var configUrl: String
        get() = prefs.getString(KEY_CONFIG_URL, null) ?: BuildConfig.CONFIG_URL
        set(value) {
            if (Hosts.schemeOf(value) == "https" && Hosts.of(value) != null) {
                prefs.edit().putString(KEY_CONFIG_URL, value).apply()
            }
        }

    fun resetConfigUrl() = prefs.edit().remove(KEY_CONFIG_URL).apply()

    private companion object {
        const val KEY_CONFIG_URL = "config_url"
    }
}
