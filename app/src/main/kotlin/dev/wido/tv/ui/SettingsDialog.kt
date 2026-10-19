package dev.wido.tv.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.tv.KeyAction
import dev.wido.tv.R
import dev.wido.tv.Settings

/** Le choix suivant (`step` = 1) ou précédent (-1) dans une liste circulaire, à partir de la valeur la plus proche. */
private fun <T> cycle(options: List<T>, current: T, step: Int, distance: (T, T) -> Double): T {
    val index = options.indices.minByOrNull { distance(options[it], current) } ?: 0
    return options[(index + step + options.size) % options.size]
}

private fun <T : Comparable<T>> cycleOf(options: List<T>, current: T, step: Int): T =
    cycle(options, current, step) { a, b -> if (a == b) 0.0 else 1.0 }

private fun cycleFloat(options: List<Float>, current: Float, step: Int): Float =
    cycle(options, current, step) { a, b -> kotlin.math.abs((a - b).toDouble()) }

/**
 * Réglages de confort. Chaque ligne se règle avec ◀ ▶ (ou OK pour passer à la valeur suivante) et
 * s'applique à l'ouverture du prochain site.
 */
@Composable
fun SettingsDialog(settings: Settings, onDismiss: () -> Unit) {
    // Les réglages ne sont pas un état Compose : ce compteur force le redessin après chaque changement.
    var version by remember { mutableIntStateOf(0) }
    val refresh = version

    val speeds = listOf(0.6f, 1.0f, 1.5f, 2.2f)
    val sizes = listOf(1.3f, 1.8f, 2.5f)
    val hides = listOf(0, 2, 3, 5, 10)
    val zooms = (100..200 step 10).toList()
    val seeks = listOf(5, 10, 15, 30)
    val osds = listOf(2, 4, 6, 10)

    @Composable fun on(v: Boolean) = stringResource(if (v) R.string.v_on else R.string.v_off)
    @Composable fun KeyAction.label() = stringResource(
        when (this) {
            KeyAction.NONE -> R.string.a_none
            KeyAction.MENU -> R.string.a_menu
            KeyAction.RELOAD -> R.string.a_reload
            KeyAction.TOGGLE_CURSOR -> R.string.a_cursor
            KeyAction.ZOOM_IN -> R.string.a_zoom_in
            KeyAction.ZOOM_OUT -> R.string.a_zoom_out
            KeyAction.QUICK_MENU -> R.string.a_quick_menu
            KeyAction.SAVE_PAGE -> R.string.a_save_page
            KeyAction.SCROLL_TOP -> R.string.a_top
        },
    )
    fun changed(block: (Int) -> Unit): (Int) -> Unit = { step ->
        block(step)
        version++
    }

    DialogFrame(
        title = stringResource(R.string.settings_title),
        onDismiss = onDismiss,
        width = 760.dp,
        buttons = {
            ActionButton(stringResource(R.string.settings_close), onDismiss)
            ActionButton(stringResource(R.string.settings_reset), { settings.resetComfort(); version++ }, primary = false)
        },
    ) {
        Text(stringResource(R.string.settings_hint), color = Palette.TextSecondary, fontSize = 13.sp)
                // `refresh` est lu ici pour que la liste soit recalculée à chaque changement.
                if (refresh >= 0) {
                    ChoiceRow(
                        stringResource(R.string.s_cursor_speed),
                        stringResource(
                            when (cycleFloat(speeds, settings.cursorSpeed, 0)) {
                                0.6f -> R.string.v_speed_slow
                                1.0f -> R.string.v_speed_normal
                                1.5f -> R.string.v_speed_fast
                                else -> R.string.v_speed_very_fast
                            },
                        ),
                        changed { s -> settings.cursorSpeed = cycleFloat(speeds, settings.cursorSpeed, s) },
                    )
                    ChoiceRow(
                        stringResource(R.string.s_cursor_size),
                        stringResource(
                            when (cycleFloat(sizes, settings.cursorSize, 0)) {
                                1.3f -> R.string.v_size_small
                                1.8f -> R.string.v_size_medium
                                else -> R.string.v_size_large
                            },
                        ),
                        changed { s -> settings.cursorSize = cycleFloat(sizes, settings.cursorSize, s) },
                    )
                    ChoiceRow(
                        stringResource(R.string.s_cursor_hide),
                        if (settings.cursorHideSeconds == 0) stringResource(R.string.v_never) else stringResource(R.string.v_seconds, settings.cursorHideSeconds),
                        changed { s -> settings.cursorHideSeconds = cycleOf(hides, settings.cursorHideSeconds, s) },
                    )
                    ChoiceRow(stringResource(R.string.s_snap), on(settings.snapNavigation), changed { settings.snapNavigation = !settings.snapNavigation })
                    ChoiceRow(
                        stringResource(R.string.s_zoom),
                        stringResource(R.string.v_percent, settings.defaultZoom),
                        changed { s -> settings.defaultZoom = cycleOf(zooms, settings.defaultZoom, s) },
                    )
                    ChoiceRow(
                        stringResource(R.string.s_seek),
                        stringResource(R.string.v_seconds, settings.seekSeconds),
                        changed { s -> settings.seekSeconds = cycleOf(seeks, settings.seekSeconds, s) },
                    )
                    ChoiceRow(
                        stringResource(R.string.s_osd),
                        stringResource(R.string.v_seconds, settings.osdSeconds),
                        changed { s -> settings.osdSeconds = cycleOf(osds, settings.osdSeconds, s) },
                    )
                    ChoiceRow(
                        stringResource(R.string.s_volume),
                        stringResource(if (settings.customVolume) R.string.v_volume_wido else R.string.v_volume_system),
                        changed { settings.customVolume = !settings.customVolume },
                    )
                    ChoiceRow(
                        stringResource(R.string.s_fs_keys),
                        stringResource(if (settings.fullscreenPlayerKeys) R.string.v_fs_player else R.string.v_fs_cursor),
                        changed { settings.fullscreenPlayerKeys = !settings.fullscreenPlayerKeys },
                    )
                    val actions = KeyAction.entries
                    ChoiceRow(stringResource(R.string.s_key_red), settings.keyRed.label(), changed { s -> settings.keyRed = cycleOf(actions, settings.keyRed, s) })
                    ChoiceRow(stringResource(R.string.s_key_green), settings.keyGreen.label(), changed { s -> settings.keyGreen = cycleOf(actions, settings.keyGreen, s) })
                    ChoiceRow(stringResource(R.string.s_key_yellow), settings.keyYellow.label(), changed { s -> settings.keyYellow = cycleOf(actions, settings.keyYellow, s) })
                    ChoiceRow(stringResource(R.string.s_key_blue), settings.keyBlue.label(), changed { s -> settings.keyBlue = cycleOf(actions, settings.keyBlue, s) })
                    ChoiceRow(
                        stringResource(R.string.s_startup),
                        stringResource(if (settings.startupLastSite) R.string.v_startup_last else R.string.v_startup_menu),
                        changed { settings.startupLastSite = !settings.startupLastSite },
                    )
                    ChoiceRow(
                        stringResource(R.string.s_new_relaxed),
                        stringResource(if (settings.newSitesRelaxed) R.string.v_relaxed else R.string.v_strict),
                        changed { settings.newSitesRelaxed = !settings.newSitesRelaxed },
                    )
                }
    }
}

/** Une ligne de réglage : ◀ ▶ changent la valeur, OK passe à la suivante. */
@Composable
private fun ChoiceRow(label: String, value: String, onStep: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .onKeyEvent { e ->
                val native = e.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { onStep(-1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { onStep(1); true }
                    else -> false
                }
            }
            .tvFocusable(PillShape, glow = Palette.Accent) { onStep(1) }
            .background(Palette.Surface)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.TextPrimary, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Text("◀  $value  ▶", color = Palette.Highlight, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
