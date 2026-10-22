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
    private const val MAX_TRACE = 400
    /** Taille maximale d'un rapport envoyé (le relais en refuse au-delà de 8 000 signes). */
    private const val SEND_MAX = 7_500

    private lateinit var dir: File
    private val lastFile get() = File(dir, "last-crash.txt")
    private val eventsFile get() = File(dir, "events.txt")
    private val seenFile get() = File(dir, "seen")
    private val exitsFile get() = File(dir, "exits-checked")
    private val traceFile get() = File(dir, "trace.txt")

    /** Journal détaillé activé (Réglages → Général) : les gestes et décisions de l'appli sont notés, sans noms de sites. */
    @Volatile var tracing = false

    private val trace = ArrayDeque<String>()
    private var flushScheduled = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun install(context: Context) {
        dir = File(context.filesDir, "crash").apply { mkdirs() }
        runCatching { readExitReasons(context) }
        runCatching { traceFile.takeIf { it.exists() }?.readLines()?.takeLast(MAX_TRACE)?.let { synchronized(trace) { trace.addAll(it) } } }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Mémoire pleine : d'abord une ligne minuscule, la pile complète demande de la place qui manque.
            if (error is OutOfMemoryError) runCatching {
                lastFile.writeText("Mémoire de l'appli épuisée (OutOfMemoryError) sur le fil ${thread.name}\n${error.message}\n")
            }
            runCatching {
                val trace = error.stackTraceToString().take(MAX_CHARS)
                lastFile.writeText(header() + "Fil : ${thread.name}\n\n$trace")
                seenFile.delete()
                flushTrace()
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
        val exits = am.getHistoricalProcessExitReasons(null, 0, 12).filter { it.timestamp > since }.sortedBy { it.timestamp }
        if (exits.isEmpty()) return
        exitsFile.writeText(exits.maxOf { it.timestamp }.toString())
        val crashes = setOf(
            android.app.ApplicationExitInfo.REASON_CRASH,
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
            android.app.ApplicationExitInfo.REASON_ANR,
        )
        for (exit in exits) {
            val main = exit.processName == context.packageName
            // Le processus annexe des pages s'arrête avec l'appli ; on ne le note que s'il a planté.
            if (!main && exit.reason !in crashes) continue
            val front = exit.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
            val crashed = exit.reason in crashes ||
                (front && exit.reason in setOf(android.app.ApplicationExitInfo.REASON_LOW_MEMORY, android.app.ApplicationExitInfo.REASON_SIGNALED))
            if (main) lastExitCrashed = crashed
            val line = "Fermeture par Android : ${reasonName(exit.reason)}" +
                (if (!main) " (moteur des pages)" else if (front) " (appli à l'écran)" else " (en arrière-plan)") +
                (if (exit.pss > 0) " · mémoire ${exit.pss / 1024} Mo" else "") +
                (exit.description?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
            eventAt(exit.timestamp, line)
            if (crashed) {
                // Trace du plantage natif ou de l'ANR quand Android la fournit.
                val trace = runCatching { exit.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                lastFile.writeText(header(exit.timestamp) + line + "\n\n" + (trace?.take(MAX_CHARS) ?: memory(context)))
                seenFile.delete()
            }
        }
    }

    /**
     * La dernière fermeture de l'appli était-elle un vrai plantage ? null : inconnu (Android 10 ou moins,
     * ou rien de neuf depuis). Une fermeture pour mémoire en arrière-plan, une mise à jour… n'en sont pas.
     */
    @Volatile var lastExitCrashed: Boolean? = null
        private set

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
        android.app.ApplicationExitInfo.REASON_OTHER -> "autre raison du système"
        14 -> "appli gelée par le système"
        15 -> "état de l'appli modifié"
        16 -> "mise à jour de l'appli"
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

    /**
     * Note un geste ou une décision de l'appli, si le journal détaillé est activé. Jamais de nom de site ni
     * d'adresse : seulement ce qui s'est passé (touche, cible du curseur, état du lecteur, mémoire).
     */
    fun trace(what: String) {
        if (!tracing || !::dir.isInitialized) return
        val line = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())}  $what"
        synchronized(trace) {
            trace.addLast(line)
            while (trace.size > MAX_TRACE) trace.removeFirst()
        }
        if (!flushScheduled) {
            flushScheduled = true
            handler.postDelayed({
                flushScheduled = false
                kotlin.concurrent.thread { flushTrace() }
            }, 4_000)
        }
    }

    private fun flushTrace() {
        runCatching {
            val lines = synchronized(trace) { trace.toList() }
            if (lines.isNotEmpty()) traceFile.writeText(lines.joinToString("\n"))
        }
    }

    /** Mémoire du moment : l'appli (PSS), le tas, ce qui reste à l'appareil. */
    fun memoryLine(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val pss = runCatching { am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))[0].totalPss / 1024 }.getOrDefault(-1)
        val rt = Runtime.getRuntime()
        return "mémoire : appli ${pss} Mo, tas ${(rt.totalMemory() - rt.freeMemory()) / 1_048_576}/${rt.maxMemory() / 1_048_576} Mo, libre ${info.availMem / 1_048_576} Mo" +
            (if (info.lowMemory) " (FAIBLE)" else "")
    }

    /**
     * Le rapport complet, ou null s'il n'y a rien. `forSend` : tient dans la limite d'un envoi (les lignes les
     * plus récentes du journal détaillé sont gardées).
     */
    fun report(forSend: Boolean = false): String? {
        if (!::dir.isInitialized) return null
        val crash = lastFile.takeIf { it.exists() }?.readText()
        val events = eventsFile.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
        val lines = synchronized(trace) { trace.toList() }
        if (crash == null && events == null && lines.isEmpty()) return null
        val base = buildString {
            append(crash ?: header())
            if (events != null) append("\n\nDerniers événements :\n").append(events)
        }
        if (lines.isEmpty()) return base
        val title = "\n\nJournal détaillé (le plus récent en bas) :\n"
        if (!forSend) return base + title + lines.joinToString("\n")
        // Envoi : on garde les lignes les plus récentes qui tiennent.
        val room = SEND_MAX - base.length - title.length
        if (room <= 0) return base.take(SEND_MAX)
        val kept = ArrayDeque<String>()
        var used = 0
        for (line in lines.asReversed()) {
            if (used + line.length + 1 > room) break
            kept.addFirst(line)
            used += line.length + 1
        }
        return base + title + kept.joinToString("\n")
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
        traceFile.delete()
        synchronized(trace) { trace.clear() }
    }

    private fun stamp(time: Long = System.currentTimeMillis()) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(time))

    private fun header(time: Long = System.currentTimeMillis()) =
        "WiDo ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n${stamp(time)}\n"
}
