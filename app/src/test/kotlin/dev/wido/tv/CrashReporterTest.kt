package dev.wido.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ce que le rapport envoyé au projet contient, et ce qu'il ne contient jamais. */
class CrashReporterTest {
    private val report = """
        WiDo 1.0.3 · SkyworthDigital TV Stick · Android 11 (API 30)
        2026-10-21 20:04:36
        Fermeture par Android : mémoire insuffisante (appli à l'écran) · mémoire 238 Mo

        Derniers événements :
        2026-10-21 19:25:24  Lecteur ouvert : Monsite (monsite.example)
        2026-10-21 19:26:04  Mémoire faible signalée par Android (niveau 10)
        2026-10-21 19:30:00  Page arrêtée (moteur du navigateur) : Monsite monsite.example
    """.trimIndent()

    @Test fun siteNamesAndAddressesAreRemoved() {
        val sent = CrashReporter.redacted(report)
        assertFalse(sent.contains("monsite"))
        assertFalse(sent.contains("Monsite"))
        assertTrue(sent.contains("Lecteur ouvert : (site masqué)"))
        assertTrue(sent.contains("Page arrêtée (moteur du navigateur) : (site masqué)"))
    }

    @Test fun everythingUsefulIsKept() {
        val sent = CrashReporter.redacted(report)
        assertTrue(sent.startsWith("WiDo 1.0.3 · SkyworthDigital TV Stick · Android 11 (API 30)"))
        assertTrue(sent.contains("Fermeture par Android : mémoire insuffisante (appli à l'écran) · mémoire 238 Mo"))
        assertTrue(sent.contains("Mémoire faible signalée par Android (niveau 10)"))
    }

    @Test fun aReportWithoutSitesIsUntouched() {
        val plain = "WiDo 1.0.3 · X · Android 11\nFil : main\n\njava.lang.IllegalStateException: boum"
        assertEquals(plain, CrashReporter.redacted(plain))
    }
}
