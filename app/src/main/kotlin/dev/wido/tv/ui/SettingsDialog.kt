package dev.wido.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.tv.KeyAction
import dev.wido.tv.R
import dev.wido.tv.Settings

private val SPEEDS = listOf(0.6f, 1.0f, 1.5f, 2.2f)
private val SIZES = listOf(1.3f, 1.8f, 2.5f)
private val HIDES = listOf(0, 2, 3, 5, 10)
private val ZOOMS = (100..200 step 10).toList()
private val SEEKS = listOf(5, 10, 15, 30)
private val OSDS = listOf(2, 4, 6, 10)

/** Indice de la valeur la plus proche : les réglages décimaux ne sont pas comparés à l'égalité stricte. */
private fun nearest(options: List<Float>, value: Float): Int =
    options.indices.minByOrNull { kotlin.math.abs(options[it] - value) } ?: 0

/** Passe à la valeur suivante ou précédente, en s'arrêtant aux extrémités. */
private fun <T> clampedStep(options: List<T>, current: T, step: Int): T =
    options[((options.indexOf(current).takeIf { it >= 0 } ?: 0) + step).coerceIn(0, options.lastIndex)]

/**
 * Réglages de confort, rangés par thème. Chaque choix est un bouton visible : on le sélectionne avec les
 * flèches puis OK, sans valeur cachée derrière un ◀ ▶. Les changements s'appliquent à l'ouverture du site suivant.
 */
@Composable
fun SettingsDialog(settings: Settings, onDismiss: () -> Unit) {
    // Les réglages ne sont pas un état Compose : ce compteur force le redessin après chaque changement.
    var version by remember { mutableIntStateOf(0) }
    val refresh = version
    fun changed(block: () -> Unit) {
        block()
        version++
    }

    val on = stringResource(R.string.v_on)
    val off = stringResource(R.string.v_off)

    DialogFrame(
        title = stringResource(R.string.settings_title),
        onDismiss = onDismiss,
        width = 800.dp,
        buttons = {
            ActionButton(stringResource(R.string.settings_close), onDismiss)
            ActionButton(stringResource(R.string.settings_reset), { changed { settings.resetComfort() } }, primary = false)
        },
    ) {
        // `refresh` est lu ici pour que tout soit recalculé à chaque changement.
        if (refresh < 0) return@DialogFrame

        SectionLabel(stringResource(R.string.sec_cursor))
        SettingRow(stringResource(R.string.s_cursor_speed)) {
            Segmented(
                listOf(R.string.v_speed_slow, R.string.v_speed_normal, R.string.v_speed_fast, R.string.v_speed_very_fast).map { stringResource(it) },
                nearest(SPEEDS, settings.cursorSpeed),
            ) { changed { settings.cursorSpeed = SPEEDS[it] } }
        }
        SettingRow(stringResource(R.string.s_cursor_size)) {
            Segmented(
                listOf(R.string.v_size_small, R.string.v_size_medium, R.string.v_size_large).map { stringResource(it) },
                nearest(SIZES, settings.cursorSize),
            ) { changed { settings.cursorSize = SIZES[it] } }
        }
        SettingRow(stringResource(R.string.s_cursor_hide)) {
            Stepper(
                if (settings.cursorHideSeconds == 0) stringResource(R.string.v_never) else stringResource(R.string.v_seconds, settings.cursorHideSeconds),
            ) { step -> changed { settings.cursorHideSeconds = clampedStep(HIDES, settings.cursorHideSeconds, step) } }
        }
        SettingRow(stringResource(R.string.s_snap)) {
            Segmented(listOf(on, off), if (settings.snapNavigation) 0 else 1) { changed { settings.snapNavigation = it == 0 } }
        }

        SectionLabel(stringResource(R.string.sec_player))
        SettingRow(stringResource(R.string.s_zoom)) {
            Stepper(stringResource(R.string.v_percent, settings.defaultZoom)) { step ->
                changed { settings.defaultZoom = clampedStep(ZOOMS, settings.defaultZoom.coerceIn(ZOOMS.first(), ZOOMS.last()), step) }
            }
        }
        SettingRow(stringResource(R.string.s_seek)) {
            Segmented(SEEKS.map { stringResource(R.string.v_seconds, it) }, SEEKS.indexOf(settings.seekSeconds).coerceAtLeast(0)) {
                changed { settings.seekSeconds = SEEKS[it] }
            }
        }
        SettingRow(stringResource(R.string.s_osd)) {
            Segmented(OSDS.map { stringResource(R.string.v_seconds, it) }, OSDS.indexOf(settings.osdSeconds).coerceAtLeast(0)) {
                changed { settings.osdSeconds = OSDS[it] }
            }
        }
        SettingRow(stringResource(R.string.s_volume)) {
            Segmented(
                listOf(stringResource(R.string.v_volume_wido), stringResource(R.string.v_volume_system)),
                if (settings.customVolume) 0 else 1,
            ) { changed { settings.customVolume = it == 0 } }
        }
        SettingRow(stringResource(R.string.s_fs_keys)) {
            Segmented(
                listOf(stringResource(R.string.v_fs_player), stringResource(R.string.v_fs_cursor)),
                if (settings.fullscreenPlayerKeys) 0 else 1,
            ) { changed { settings.fullscreenPlayerKeys = it == 0 } }
        }

        SectionLabel(stringResource(R.string.sec_keys))
        KeyRow(R.string.s_key_red, settings.keyRed) { changed { settings.keyRed = it } }
        KeyRow(R.string.s_key_green, settings.keyGreen) { changed { settings.keyGreen = it } }
        KeyRow(R.string.s_key_yellow, settings.keyYellow) { changed { settings.keyYellow = it } }
        KeyRow(R.string.s_key_blue, settings.keyBlue) { changed { settings.keyBlue = it } }

        SectionLabel(stringResource(R.string.sec_general))
        SettingRow(stringResource(R.string.s_startup)) {
            Segmented(
                listOf(stringResource(R.string.v_startup_menu), stringResource(R.string.v_startup_last)),
                if (settings.startupLastSite) 1 else 0,
            ) { changed { settings.startupLastSite = it == 1 } }
        }
        SettingRow(stringResource(R.string.s_new_relaxed)) {
            Segmented(
                listOf(stringResource(R.string.v_strict), stringResource(R.string.v_relaxed)),
                if (settings.newSitesRelaxed) 1 else 0,
            ) { changed { settings.newSitesRelaxed = it == 1 } }
        }
    }
}

/** Une touche de couleur : l'action actuelle et deux flèches pour parcourir les actions possibles. */
@Composable
private fun KeyRow(labelRes: Int, current: KeyAction, onChange: (KeyAction) -> Unit) {
    val actions = KeyAction.entries
    SettingRow(stringResource(labelRes)) {
        Stepper(stringResource(actionLabel(current))) { step ->
            onChange(actions[(actions.indexOf(current) + step + actions.size) % actions.size])
        }
    }
}

private fun actionLabel(action: KeyAction): Int = when (action) {
    KeyAction.NONE -> R.string.a_none
    KeyAction.MENU -> R.string.a_menu
    KeyAction.RELOAD -> R.string.a_reload
    KeyAction.TOGGLE_CURSOR -> R.string.a_cursor
    KeyAction.ZOOM_IN -> R.string.a_zoom_in
    KeyAction.ZOOM_OUT -> R.string.a_zoom_out
    KeyAction.QUICK_MENU -> R.string.a_quick_menu
    KeyAction.SAVE_PAGE -> R.string.a_save_page
    KeyAction.SCROLL_TOP -> R.string.a_top
}

/** Une ligne : le libellé à gauche, les boutons de choix à droite. */
@Composable
private fun SettingRow(label: String, controls: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.TextPrimary, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = controls)
    }
}

/** Tous les choix côte à côte ; le choix actif est allumé. */
@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    labels.forEachIndexed { index, label -> Chip(label, { onSelect(index) }, selected = index == selected) }
}

/** `◀  valeur  ▶` pour les réglages à nombreuses valeurs : les deux flèches sont de vrais boutons. */
@Composable
private fun Stepper(value: String, onStep: (Int) -> Unit) {
    Chip("◀", { onStep(-1) })
    Box(Modifier.widthIn(min = 150.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text(value, color = Palette.Highlight, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
    Chip("▶", { onStep(1) })
}
