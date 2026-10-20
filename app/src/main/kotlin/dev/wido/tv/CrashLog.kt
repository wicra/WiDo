package dev.wido.tv

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal des plantages, gardé sur l'appareil (rien n'est envoyé) : la dernière erreur de l'appli, les
 * fermetures décidées par Android (mémoire, plantage natif du moteur vidéo… lues au démarrage suivant,
 * Android 11 et plus) et un fil des derniers événements du lecteur (ouverture, fermeture et qui l'a
 * demandée, arrêt de page). Réglages → Général → « Rapport de plantage » l'affiche, en
 * texte et en QR code (sur TV), pour le transmettre sans câble.
 */
object CrashLog {
    private const val MAX_CHARS = 6_000
    private const val MAX_EVENTS = 30

    private lateinit var dir: File
    private val lastFile get() = File(dir, "last-crash.txt")
    private val eventsFile get() = File(dir, "events.txt")
    private val seenFile get() = File(dir, "seen")
    private val exitsFile get() = File(dir, "exits-checked")

    fun install(context: Context) {
        dir = File(context.filesDir, "crash").apply { mkdirs() }
        runCatching { readExitReasons(context) }
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
        eventAt(System.currentTimeMillis(), what)
    }

    /**
     * Pourquoi l'appli s'est arrêtée les dernières fois, selon Android : une fermeture sans erreur de
     * WiDo (mémoire, moteur vidéo natif) n'apparaît que là.
     */
    private fun readExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val since = exitsFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L
        val exits = am.getHistoricalProcessExitReasons(null, 0, 8).filter { it.timestamp > since }.sortedBy { it.timestamp }
        if (exits.isEmpty()) return
        exitsFile.writeText(exits.maxOf { it.timestamp }.toString())
        for (exit in exits) {
            val serious = exit.reason in setOf(
                android.app.ApplicationExitInfo.REASON_CRASH,
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
                android.app.ApplicationExitInfo.REASON_ANR,
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY,
                android.app.ApplicationExitInfo.REASON_SIGNALED,
                android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
            ) || (exit.reason == android.app.ApplicationExitInfo.REASON_OTHER && exit.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
            val front = exit.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
            val line = "Fermeture par Android : ${reasonName(exit.reason)}" +
                (if (front) " (appli à l'écran)" else " (en arrière-plan)") +
                " · mémoire ${exit.pss / 1024} Mo" +
                (exit.description?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
            eventAt(exit.timestamp, line)
            if (serious && front) {
                // Trace du plantage natif ou de l'ANR quand Android la fournit.
                val trace = runCatching { exit.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                lastFile.writeText(header(exit.timestamp) + line + "\n\n" + (trace?.take(MAX_CHARS) ?: memory(context)))
                seenFile.delete()
            }
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        android.app.ApplicationExitInfo.REASON_CRASH -> "erreur de l'appli"
        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "plantage natif (moteur web ou vidéo)"
        android.app.ApplicationExitInfo.REASON_ANR -> "appli bloquée (ANR)"
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "mémoire insuffisante"
        android.app.ApplicationExitInfo.REASON_SIGNALED -> "arrêtée par le système (signal)"
        android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "trop de ressources utilisées"
        android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "arrêt demandé (forcer l'arrêt)"
        android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "arrêtée par l'utilisateur"
        android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "fermeture normale"
        android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "un composant dont elle dépend s'est arrêté"
        android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "autorisation modifiée"
        android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "échec au démarrage"
        else -> "autre ($reason)"
    }

    private fun memory(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return "Mémoire de l'appareil : ${info.totalMem / 1_048_576} Mo, libre ${info.availMem / 1_048_576} Mo" +
            (if (am.isLowRamDevice) " · appareil à faible mémoire" else "")
    }

    private fun eventAt(time: Long, what: String) {
        runCatching {
            val line = "${stamp(time)}  $what"
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
            if (events != null) append("\n\nDerniers événements :\n").append(events)
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

    private fun stamp(time: Long = System.currentTimeMillis()) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(time))

    private fun header(time: Long = System.currentTimeMillis()) =
        "WiDo ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n${stamp(time)}\n"
}
