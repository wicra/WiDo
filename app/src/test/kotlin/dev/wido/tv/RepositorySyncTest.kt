package dev.wido.tv

import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Le stockage réel de l'appli (fichiers, registre de synchro), sur un faux Android. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositorySyncTest {
    private val app get() = RuntimeEnvironment.getApplication() as WiDoApp

    @Test fun progressIsStampedAndTravels() {
        val repo = app.repository
        repo.recordProgress("youtube", "https://www.youtube.com/watch?v=abcdefghijk&t=10s", "Vidéo", null, 60.0, 600.0)
        val delta = repo.syncOutgoing(-1, "")
        val key = "w:https://www.youtube.com/watch?v=abcdefghijk"
        assertTrue(delta.records.any { it.k == key })
        // Rien de neuf depuis : rien à envoyer.
        assertTrue(repo.syncOutgoing(delta.seq, delta.epoch).records.isEmpty())
    }

    @Test fun aFinishedVideoElsewhereLeavesContinueWatching() {
        val repo = app.repository
        repo.recordProgress("youtube", "https://www.youtube.com/watch?v=abcdefghijk", "Vidéo", null, 60.0, 600.0)
        val key = "w:https://www.youtube.com/watch?v=abcdefghijk"
        val mine = repo.syncOutgoing(-1, "").records.first { it.k == key }
        // L'autre écran l'a finie plus tard : suppression datée après.
        repo.applySync(listOf(mine.copy(v = JsonNull, t = mine.t + 1_000, s = 0)))
        assertTrue(repo.watching.value.none { it.url.contains("abcdefghijk") })
    }

    @Test fun restoreAfterRemoval() {
        val repo = app.repository
        repo.recordProgress("youtube", "https://www.youtube.com/watch?v=bcdefghijkl", "Autre", null, 120.0, 600.0)
        val entry = repo.watching.value.first()
        repo.removeWatch(entry.url)
        assertTrue(repo.watching.value.isEmpty())
        repo.restoreWatch(entry)
        assertEquals(listOf(entry.url), repo.watching.value.map { it.url })
    }
}
