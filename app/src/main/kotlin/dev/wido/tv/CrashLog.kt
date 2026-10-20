package dev.wido.tv

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal des plantages, gardé sur l'appareil (rien n'est envoyé) : la dernière erreur de l'appli et
 * les derniers arrêts du moteur des pages. Réglages → Général → « Rapport de plantage » l'affiche, en
 * texte et en QR code (sur TV), pour le transmettre sans câble.
 */
object CrashLog {
    private const val MAX_CHARS = 6_000
    private const val MAX_EVENTS = 10

    private lateinit var dir: File
    private val lastFile get() = File(dir, "last-crash.txt")
    private val eventsFile get() = File(dir, "events.txt")
    private val seenFile get() = File(dir, "seen")

    fun install(context: Context) {
        dir = File(context.filesDir, "crash").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = error.stackTraceToString().take(MAX_CHARS)
                lastFile.writeText(header() + "Fil : ${thread.name}\n\n$trace")
                seenFile.delete()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Un incident qui n'a pas fermé l'appli (ex. le moteur d'une page arrêté). */
    fun event(what: String) {
        if (!::dir.isInitialized) return
        runCatching {
            val line = "${stamp()}  $what"
            val lines = (eventsFile.takeIf { it.exists() }?.readLines().orEmpty() + line).takeLast(MAX_EVENTS)
            eventsFile.writeText(lines.joinToString("\n"))
        }
    }

    /** Le rapport complet, ou null s'il n'y a rien. */
    fun report(): String? {
        if (!::dir.isInitialized) return null
        val crash = lastFile.takeIf { it.exists() }?.readText()
        val events = eventsFile.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
        if (crash == null && events == null) return null
        return buildString {
            append(crash ?: header())
            if (events != null) append("\n\nIncidents récents :\n").append(events)
        }
    }

    /** Une erreur que l'utilisateur n'a pas encore vue (pour le signaler une fois au démarrage). */
    fun hasUnseenCrash(): Boolean = ::dir.isInitialized && lastFile.exists() && !seenFile.exists()

    fun markSeen() {
        if (::dir.isInitialized) runCatching { seenFile.writeText("") }
    }

    fun clear() {
        if (!::dir.isInitialized) return
        lastFile.delete()
        eventsFile.delete()
        seenFile.delete()
    }

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())

    private fun header() =
        "WiDo ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n${stamp()}\n"
}
