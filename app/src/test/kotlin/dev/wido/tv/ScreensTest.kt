package dev.wido.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.wido.core.DeviceInfo
import dev.wido.core.DeviceRole
import dev.wido.core.NowPlaying
import dev.wido.core.PairRequest
import dev.wido.core.PairingDesk
import dev.wido.core.Pin
import dev.wido.core.Site
import dev.wido.core.WatchEntry
import dev.wido.tv.ui.AddCard
import dev.wido.tv.ui.ContinueTab
import dev.wido.tv.ui.Glow
import dev.wido.tv.ui.Glyph
import dev.wido.tv.ui.Hero
import dev.wido.tv.ui.HomeTab
import dev.wido.tv.ui.MiniPlayer
import dev.wido.tv.ui.PairPrompt
import dev.wido.tv.ui.Palette
import dev.wido.tv.ui.QuickAction
import dev.wido.tv.ui.QuickMenu
import dev.wido.tv.ui.SiteCard
import dev.wido.tv.ui.WiDoTheme
import dev.wido.tv.ui.halo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures d'écran des principaux écrans, sur téléphone (portrait, paysage) et TV. La CI les publie :
 * un défaut d'affichage (texte coupé, icône décentrée, bouton écrasé) se voit avant d'arriver chez soi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreensTest {
    @get:Rule val compose = createComposeRule()

    private val sites = listOf(
        MenuSite(Site("youtube", "YouTube", "https://www.youtube.com", color = "#E52D27"), local = false),
        MenuSite(Site("arte", "Arte", "https://www.arte.tv", color = "#FA481C"), local = false),
        MenuSite(Site("perso-france-tv", "France TV", "https://www.france.tv", color = "#1E5BFF"), local = true),
        MenuSite(Site("perso-twitch", "Twitch", "https://www.twitch.tv", color = "#9146FF"), local = true),
    )
    private val watching = listOf(
        WatchEntry("youtube", "https://www.youtube.com/watch?v=abcdefghijk", "Une très longue vidéo de documentaire sur la mer", position = 620.0, duration = 3600.0, updatedAt = 2),
        WatchEntry("arte", "https://www.arte.tv/fr/videos/1/", "Concert", position = 300.0, duration = 1800.0, updatedAt = 1),
    )
    private val pins = listOf(Pin("arte", "Concerts", "https://www.arte.tv/fr/concerts/"), Pin("youtube", "Abonnements", "https://www.youtube.com/feed/subscriptions"))
    private val now = NowPlaying("youtube", "YouTube", watching[0].url, watching[0].title, position = 620.0, duration = 3600.0, playing = true, at = System.currentTimeMillis())

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/screens/$name.png")

    private fun actions() = listOf(
        QuickAction("Sur la TV", Glyph.Tv) {}, QuickAction("Pause", Glyph.Pause) {}, QuickAction("Suivant", Glyph.Next) {},
        QuickAction("Lecteur", Glyph.Native) {}, QuickAction("Début", Glyph.Restart) {}, QuickAction("Veille", Glyph.Sleep) {},
        QuickAction("Épingler", Glyph.Star) {}, QuickAction("A−", Glyph.ZoomOut) {}, QuickAction("A+", Glyph.ZoomIn) {},
        QuickAction("Sites", Glyph.Sites) {}, QuickAction("Recharger", Glyph.Refresh, more = true) {},
    )

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun telephoneAccueil() {
        compose.setContent {
            WiDoTheme {
                Box(Modifier.fillMaxSize().halo(listOf(Glow(Palette.Accent, 0.1f, 0f, 0.9f, 0.45f)))) {
                    HomeTab(sites, watching, pins, loading = false, update = {}, onOpen = {}, onOptions = {}, onAdd = {}, onOpenPin = {}, onOpenWatch = {}, onLongPress = {}, onRefresh = {})
                }
            }
        }
        shot("telephone-accueil")
    }

    @Test @Config(qualifiers = "w800dp-h360dp-xxhdpi")
    fun telephonePaysage() {
        compose.setContent {
            WiDoTheme {
                Box(Modifier.fillMaxSize().halo(listOf(Glow(Palette.Accent, 0.1f, 0f, 0.9f, 0.45f)))) {
                    HomeTab(sites, watching, pins, loading = false, update = {}, onOpen = {}, onOptions = {}, onAdd = {}, onOpenPin = {}, onOpenWatch = {}, onLongPress = {}, onRefresh = {})
                }
            }
        }
        shot("telephone-paysage")
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun telephoneReprendre() {
        compose.setContent {
            WiDoTheme {
                Box(Modifier.fillMaxSize().halo(listOf(Glow(Palette.Accent, 0.1f, 0f, 0.9f, 0.45f)))) {
                    ContinueTab(watching, sites.associate { it.site.id to it.site }, tvName = "Salon", onOpen = {}, onRemove = {}, onSendToTv = {}, onLongPress = {})
                }
            }
        }
        shot("telephone-reprendre")
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun telephoneMiniLecteurEtMenu() {
        compose.setContent {
            WiDoTheme {
                Box(Modifier.fillMaxSize().halo(listOf(Glow(Palette.Purple, 0.5f, 0.2f, 0.9f, 0.6f)))) {
                    QuickMenu(MediaState(false, 620.0, 3600.0, 1f), null, actions(), "12 bloquées · 3 refusées", compact = true, moreLabel = "Plus…")
                    Column(Modifier.align(Alignment.TopCenter).padding(top = 24.dp)) {
                        MiniPlayer(now, System.currentTimeMillis(), "Salon", onToggle = {}) {}
                    }
                }
            }
        }
        shot("telephone-menu-mini-lecteur")
    }

    @Test @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun tvMenuRapide() {
        compose.setContent {
            WiDoTheme {
                Box(Modifier.fillMaxSize().halo(listOf(Glow(Palette.Accent, 0.2f, 0.3f, 0.6f, 0.7f)))) {
                    QuickMenu(MediaState(false, 620.0, 3600.0, 1f), null, actions(), "12 bloquées · 3 refusées", compact = false, moreLabel = "Plus…")
                }
            }
        }
        shot("tv-menu-rapide")
    }

    @Test @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun tvAccueil() {
        compose.setContent {
            WiDoTheme {
                Column(Modifier.fillMaxSize().halo(listOf(Glow(Palette.Accent, 0.2f, 0.3f, 0.55f, 0.7f), Glow(Palette.Violet, 0.85f, 0.1f, 0.6f, 0.7f)))) {
                    Hero(sites[0], watch = null, onResume = {}, onRemoveWatch = {}, empty = false, onOpen = {}, onOptions = {}, onAdd = {}, pins = pins.filter { it.siteId == "youtube" }, onPin = {})
                    Box(Modifier.weight(1f))
                    Row(Modifier.padding(start = 44.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        sites.forEachIndexed { i, s -> SiteCard(s, i, onClick = {}, onOptions = {}, moving = false, onMove = {}, onMoveDone = {}, onFocus = {}) }
                        AddCard(sites.size, onClick = {}, onFocus = {})
                    }
                }
            }
        }
        shot("tv-accueil")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun tvDemandeDeLiaison() {
        val pending = PairingDesk.Pending(PairRequest(DeviceInfo("p", "Pixel de Sergio", DeviceRole.PHONE), "12345678"), 0)
        compose.setContent { WiDoTheme { PairPrompt(pending) { _, _ -> } } }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/screens/tv-demande-liaison.png")
    }
}
