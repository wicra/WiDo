package dev.wido.tv

import android.content.Context
import android.util.Log
import android.webkit.WebView
import dev.wido.core.Site
import org.json.JSONObject
import java.io.IOException

/**
 * Injecte dans la page les scripts intégrés à l'app que le site a choisis, et masque ses
 * sélecteurs CSS. La configuration distante ne peut PAS apporter son propre JavaScript :
 * elle choisit seulement parmi les scripts de `assets/scripts/` (voir docs/SECURITY.md).
 */
class ScriptInjector(private val context: Context, private val site: Site) {
    private val source: String by lazy { build() }

    /** Faux quand les scripts internes sont déjà injectés dans toutes les frames par la passerelle native. */
    @Volatile var includeInternal = true

    private val internal: String by lazy { internalScript() }

    fun inject(view: WebView) {
        val js = (if (includeInternal) internal + "\n" else "") + source
        if (js.isNotBlank()) view.evaluateJavascript(js, null)
    }

    /** Scripts internes de WiDo (curseur aimanté, lecteur vidéo) : jamais choisis par la configuration. */
    fun internalScript(): String = INTERNAL.joinToString("\n") { name ->
        runCatching { context.assets.open("internal/$name").bufferedReader().use { it.readText() } }.getOrDefault("")
    }

    private fun build(): String {
        val parts = mutableListOf<String>()
        for (name in site.scripts) {
            if (!NAME.matches(name)) {
                Log.w(TAG, "Nom de script invalide ignoré : $name")
                continue
            }
            try {
                parts += context.assets.open("scripts/$name.js").bufferedReader().use { it.readText() }
            } catch (_: IOException) {
                Log.w(TAG, "Script inconnu ignoré : $name")
            }
        }
        val css = site.hideSelectors
            .filter { it.isNotBlank() && it.length <= 200 && it.none { c -> c in "{}<>" } }
            .joinToString("") { "$it{display:none!important}" }
        if (css.isNotEmpty()) {
            parts += "(function(){var s=document.createElement('style');" +
                "s.textContent=${JSONObject.quote(css)};" +
                "(document.head||document.documentElement).appendChild(s);})();"
        }
        return parts.joinToString("\n")
    }

    private companion object {
        const val TAG = "WiDoScripts"
        val INTERNAL = listOf("snap-core.js", "frame.js")
        val NAME = Regex("^[a-z0-9][a-z0-9-]{0,40}$")
    }
}
