package dev.wido.tv

import android.content.Context
import android.util.Log
import android.webkit.WebView
import dev.wido.core.CosmeticPlan
import dev.wido.core.Navigation
import dev.wido.core.Site
import org.json.JSONObject
import java.io.IOException

/**
 * Injecte dans la page les scripts intégrés à l'app que le site a choisis, et masque ses
 * sélecteurs CSS. La configuration distante ne peut PAS apporter son propre JavaScript :
 * elle choisit seulement parmi les scripts de `assets/scripts/` (voir docs/SECURITY.md).
 */
class ScriptInjector(private val context: Context, private val site: Site) {
    /** Scripts du site et masquage CSS (`hideSelectors`). */
    val source: String by lazy { build() }

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
        val js = (if (includeInternal) internal + "\n" else "") + (if (includeEarly) early else "") + liveHideScript()
        if (js.isNotBlank()) view.evaluateJavascript(js, null)
    }

    /** Éléments masqués pendant cette séance (« Masquer un élément ») : appliqués aussi aux pages suivantes. */
    private val liveHides = java.util.concurrent.CopyOnWriteArrayList<String>()

    /** Masque `selector` dès maintenant, sur cette page et les suivantes, sans attendre la prochaine ouverture du site. */
    fun addHide(selector: String): String {
        if (selector !in liveHides) liveHides += selector
        return hideScript(listOf(selector))
    }

    private fun liveHideScript(): String = if (liveHides.isEmpty()) "" else "\n" + hideScript(liveHides)

    /**
     * Assemble les scripts de protection. Chacun se protège lui-même : il ne s'exécute qu'une fois
     * par page, et le script YouTube ne fait rien ailleurs que sur YouTube.
     */
    fun earlyScript(cosmetic: CosmeticPlan, youtube: Boolean, sponsorBlock: Boolean): String {
        val parts = mutableListOf<String>()
        // Scripts du site, avant ceux de la page : une pub ne peut plus garder l'ancien window.open.
        // Dans la page principale seulement, sauf le blocage des pop-ups, utile aussi dans les iframes.
        if (source.isNotBlank()) parts += "if (window.top === window) {\n$source\n}"
        if ("block-popups" in site.scripts && site.navigation != Navigation.MIXED) asset("scripts/block-popups.js")?.let { parts += it }
        // Les scriptlets d'abord : ils doivent piéger les variables avant le moindre script de la page.
        if (cosmetic.hasScripts) parts += call("scriptlets.js", cosmetic.scriptsJson())
        if (!cosmetic.isEmpty) parts += call("cosmetic.js", cosmetic.toJson())
        if (youtube) parts += call("youtube.js", JSONObject().put("sponsor", sponsorBlock).toString())
        return parts.joinToString("\n")
    }

    /** `internal/<name>` définit une fonction `run` : on l'appelle avec ses options, sans rien laisser dans la page. */
    private fun call(name: String, options: String): String {
        val body = asset("internal/$name") ?: return ""
        return "(function(){\n$body\nrun($options);\n})();"
    }

    private fun asset(path: String): String? =
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Scripts internes de WiDo (curseur aimanté, lecteur vidéo) : jamais choisis par la configuration. */
    fun internalScript(): String = INTERNAL.joinToString("\n") { name ->
        runCatching { context.assets.open("internal/$name").bufferedReader().use { it.readText() } }.getOrDefault("")
    }

    private fun build(): String {
        val parts = mutableListOf<String>()
        for (name in site.scripts) {
            // Mode mixte : les pop-ups doivent pouvoir s'ouvrir (dans la fenêtre publicitaire).
            if (name == "block-popups" && site.navigation == Navigation.MIXED) continue
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
        hideScript(site.hideSelectors).takeIf { it.isNotEmpty() }?.let { parts += it }
        return parts.joinToString("\n")
    }

    /**
     * Feuille de style qui masque `selectors`, chacun dans sa propre règle : un sélecteur que le navigateur
     * refuse n'annule pas les autres. Le style est remis s'il est retiré de la page (sites qui réécrivent l'en-tête).
     */
    private fun hideScript(selectors: List<String>): String {
        val css = selectors
            .filter { it.isNotBlank() && it.length <= 300 && it.none { c -> c in "{}<>" } }
            .joinToString("\n") { "$it{display:none!important}" }
        if (css.isEmpty()) return ""
        return "(function(){var css=${JSONObject.quote(css)};var key='wido-hide-${css.hashCode().toUInt()}';" +
            "function put(){if(document.getElementById(key))return;var s=document.createElement('style');s.id=key;" +
            "s.textContent=css;(document.head||document.documentElement).appendChild(s);}" +
            "put();if(window.MutationObserver)new MutationObserver(put).observe(document.documentElement,{childList:true,subtree:true});})();"
    }

    private companion object {
        const val TAG = "WiDoScripts"
        val INTERNAL = listOf("snap-core.js", "fields.js", "frame.js")
        val NAME = Regex("^[a-z0-9][a-z0-9-]{0,40}$")
    }
}
