package dev.wido.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Les fichiers de config livrés dans le dépôt doivent rester valides pour le vrai parseur. */
class ShippedConfigTest {
    private fun load(path: String) = ConfigParser.parse(File("../$path").readText())

    @Test fun defaultConfigIsValid() {
        val p = load("config/wido.json")
        assertTrue(p.warnings.isEmpty(), p.warnings.toString())
        assertTrue(p.config.sites.isNotEmpty())
    }

    @Test fun bundledConfigIsValid() {
        val p = load("app/src/main/assets/default-config.json")
        assertTrue(p.warnings.isEmpty(), p.warnings.toString())
    }

    @Test fun exampleConfigIsValid() {
        val p = load("config/wido.example.json")
        assertTrue(p.warnings.isEmpty(), p.warnings.toString())
        assertEquals(listOf("site-a", "nas", "ancien-site"), p.config.sites.map { it.id })
    }
}
