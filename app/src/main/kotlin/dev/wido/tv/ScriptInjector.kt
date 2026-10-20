package dev.wido.tv

import android.content.Context
import android.util.Log
import android.webkit.WebView
import dev.wido.core.CosmeticPlan
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

    /**
     * Scripts de protection ajoutés par l'app (masquage cosmétique, YouTube sans pub) : à injecter au
     * tout début de chaque document. Voir [earlyScript].
     */
    @Volatile var early: String = ""

    /** Faux quand [early] est déjà injecté en début de document par la WebView. */
    @Volatile var includeEarly = true

    fun inject(view: WebView) {
        val js = (if (includeInternal) internal + "\n" else "") + (if (includeEarly) early + "\n" else "") + source
        if (js.isNotBlank()) view.evaluateJavascript(js, null)
    }

    /**
     * Assemble les scripts de protection. Chacun se protège lui-même : il ne s'exécute qu'une fois
     * par page, et le script YouTube ne fait rien ailleurs que sur YouTube.
     */
    fun earlyScript(cosmetic: CosmeticPlan, youtube: Boolean, sponsorBlock: Boolean): String {
        val parts = mutableListOf<String>()
        if (!cosmetic.isEmpty) parts += call("cosmetic.js", cosmetic.toJson())
        if (youtube) parts += call("youtube.js", JSONObject().put("sponsor", sponsorBlock).toString())
        return parts.joinToString("\n")
    }

    /** `internal/<name>` définit une fonction `run` : on l'appelle avec ses options, sans rien laisser dans la page. */
    private fun call(name: String, options: String): String {
        val body = runCatching { context.assets.open("internal/$name").bufferedReader().use { it.readText() } }.getOrDefault("")
        if (body.isBlank()) return ""
        return "(function(){\n$body\nrun($options);\n})();"
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
