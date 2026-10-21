package dev.wido.tv.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.wido.tv.R
import dev.wido.tv.UiSound
import kotlinx.coroutines.delay

/** Ce qu'une étape demande à l'utilisateur : lire (OK pour continuer) ou faire le geste lui-même. */
private enum class Kind { Info, Click, Hold, Menu, DoubleBack }

private class StepSpec(val kind: Kind, val title: Int, val body: Int, val cap: Int?)

private val STEPS = listOf(
    StepSpec(Kind.Info, R.string.tuto_intro_t, R.string.tuto_intro_b, null),
    StepSpec(Kind.Click, R.string.tuto_click_t, R.string.tuto_click_b, R.string.tuto_cap_ok),
    StepSpec(Kind.Hold, R.string.tuto_hold_t, R.string.tuto_hold_b, R.string.tuto_cap_hold),
    StepSpec(Kind.Menu, R.string.tuto_menu_t, R.string.tuto_menu_b, R.string.tuto_cap_menu),
    StepSpec(Kind.DoubleBack, R.string.tuto_back_t, R.string.tuto_back_b, R.string.tuto_cap_back),
    StepSpec(Kind.Info, R.string.tuto_play_t, R.string.tuto_play_b, R.string.tuto_cap_arrows),
    StepSpec(Kind.Info, R.string.tuto_cookies_t, R.string.tuto_cookies_b, null),
    StepSpec(Kind.Info, R.string.tuto_done_t, R.string.tuto_done_b, null),
)

/**
 * Tutoriel de la télécommande : chaque geste est demandé et essayé pour de vrai (OK, OK maintenu, Menu, deux
 * Retour rapides), puis quelques rappels. On peut passer une étape ou quitter à tout moment (boutons du bas,
 * ▼ pour les atteindre) ; Retour quitte aussi, sauf pendant l'exercice du double Retour.
 */
@Composable
fun RemoteTutorial(onClose: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var succeeded by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(false) }
    var cardFocused by remember { mutableStateOf(false) }
    var lastBack by remember { mutableLongStateOf(0L) }
    val card = remember { FocusRequester() }
    val step = STEPS[index]

    fun next() {
        if (index >= STEPS.lastIndex) {
            onClose()
        } else {
            index++
            succeeded = false
            retry = false
        }
    }

    fun succeed() {
        if (!succeeded) {
            succeeded = true
            UiSound.select()
        }
    }
    // Le geste réussi s'affiche un instant, puis on passe à la suite.
    LaunchedEffect(succeeded) {
        if (succeeded) {
            delay(1_000)
            next()
        }
    }
    LaunchedEffect(index) {
        delay(60)
        runCatching { card.requestFocus() }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .halo(listOf(Glow(Palette.Accent, 0.2f, 0.3f, 0.55f, 0.7f), Glow(Palette.Violet, 0.85f, 0.2f, 0.6f, 0.7f)))
                .onPreviewKeyEvent { e ->
                    val n = e.nativeKeyEvent
                    val code = n.keyCode
                    if (code == KeyEvent.KEYCODE_BACK) {
                        if (n.action == KeyEvent.ACTION_UP) {
                            if (step.kind == Kind.DoubleBack) {
                                // L'exercice : deux appuis rapides ; un seul appui n'est pas « quitter ».
                                if (n.eventTime - lastBack < 450) {
                                    lastBack = 0L
                                    succeed()
                                } else {
                                    lastBack = n.eventTime
                                    retry = true
                                }
                            } else {
                                onClose()
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (!cardFocused) return@onPreviewKeyEvent false
                    val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    if (!ok && code != KeyEvent.KEYCODE_MENU) return@onPreviewKeyEvent false
                    if (succeeded) return@onPreviewKeyEvent true
                    when (step.kind) {
                        Kind.Info -> if (ok && n.action == KeyEvent.ACTION_UP) next()
                        Kind.Click -> if (ok && n.action == KeyEvent.ACTION_UP) succeed()
                        Kind.Hold -> if (ok && n.action == KeyEvent.ACTION_DOWN && n.repeatCount > 0 && n.eventTime - n.downTime >= 550) succeed()
                        Kind.Menu -> if (code == KeyEvent.KEYCODE_MENU && n.action == KeyEvent.ACTION_UP) succeed()
                        Kind.DoubleBack -> Unit
                    }
                    true
                },
        ) {
            Column(
                Modifier.align(Alignment.Center).widthIn(max = 920.dp).padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Text(stringResource(R.string.tuto_step, index + 1, STEPS.size), color = Palette.TextMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(PanelShape)
                        .background(Palette.Glass)
                        .border(2.dp, if (cardFocused) Color.White.copy(alpha = 0.7f) else Palette.GlassBorder, PanelShape)
                        .focusRequester(card)
                        .onFocusChanged { cardFocused = it.isFocused }
                        .focusable()
                        .padding(horizontal = 44.dp, vertical = 38.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    step.cap?.let { KeyCap(stringResource(it), succeeded) }
                    Text(stringResource(step.title), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, textAlign = TextAlign.Center)
                    Text(stringResource(step.body), color = Palette.TextSecondary, fontSize = 19.sp, lineHeight = 27.sp, textAlign = TextAlign.Center)
                    when {
                        succeeded -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlyphIcon(Glyph.Check, Palette.Ok, 30.dp)
                            Text(stringResource(R.string.tuto_ok), color = Palette.Ok, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        retry -> Text(stringResource(R.string.tuto_back_retry), color = Palette.Warning, fontSize = 16.sp, textAlign = TextAlign.Center)
                        step.kind == Kind.Info -> Text(stringResource(R.string.tuto_press_ok), color = Palette.TextMuted, fontSize = 15.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(stringResource(R.string.tuto_skip), { next() }, primary = false, compact = true, glyph = Glyph.Next)
                    ActionButton(stringResource(R.string.tuto_quit), onClose, primary = false, compact = true, glyph = Glyph.Close)
                }
                Text(stringResource(R.string.tuto_hint_buttons), color = Palette.TextMuted, fontSize = 13.sp)
            }
        }
    }
}

/** Une touche de télécommande dessinée en grand ; elle devient verte quand le geste est réussi. */
@Composable
private fun KeyCap(label: String, ok: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (ok) Palette.Ok.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f))
            .border(2.dp, if (ok) Palette.Ok else Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 34.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = if (ok) Palette.Ok else Color.White)
    }
}
