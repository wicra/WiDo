package dev.wido.tv

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Envoi du rapport de plantage au dépôt, à la demande de l'utilisateur (bouton « Envoyer le rapport »).
 * L'appli ne connaît que l'adresse d'un petit relais (`wido.crashUrl`) qui détient la clé GitHub : aucune
 * clé n'est embarquée. Rien ne part sans appui sur le bouton, et les noms de sites en sont retirés.
 */
object CrashReporter {
    /** Adresse du relais, ou vide si aucun n'est déployé. */
    val relayUrl: String get() = BuildConfig.CRASH_REPORT_URL

    val canSend: Boolean get() = relayUrl.startsWith("https://")

    enum class Result { Sent, Failed }

    private val SITE_LINES = Regex("""^(.*(?:Lecteur ouvert|Page arrêtée)[^:]*:).*$""", RegexOption.MULTILINE)

    /** Le rapport sans les noms de sites ni leurs adresses (c'est ce qui est envoyé, jamais l'original). */
    fun redacted(report: String): String = SITE_LINES.replace(report) { "${it.groupValues[1]} (site masqué)" }

    /** Bloquant : à appeler hors du fil principal. */
    fun send(report: String): Result = runCatching {
        val body = JSONObject().put("report", redacted(report)).put("version", BuildConfig.VERSION_NAME).toString()
        val connection = (URL(relayUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode in 200..299) Result.Sent else Result.Failed
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(Result.Failed)

    /** Repli sans relais : la page « Nouvelle issue » de GitHub, pré-remplie (à valider soi-même, compte GitHub requis). */
    fun issueUri(report: String): Uri {
        val clean = redacted(report)
        val title = "[plantage] ${BuildConfig.VERSION_NAME}"
        return Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}/issues/new").buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", "```text\n${clean.take(4_500).replace("```", "'''")}\n```")
            .build()
    }
}
