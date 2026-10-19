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

    fun inject(view: WebView) {
        if (source.isNotEmpty()) view.evaluateJavascript(source, null)
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
        val NAME = Regex("^[a-z0-9][a-z0-9-]{0,40}$")
    }
}
