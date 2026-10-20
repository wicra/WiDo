package dev.wido.tv

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.wido.core.HandoffPlay
import dev.wido.core.Hosts
import dev.wido.core.Peer
import dev.wido.tv.ui.ActionButton
import dev.wido.tv.ui.DialogFrame
import dev.wido.tv.ui.Palette
import dev.wido.tv.ui.WiDoTheme
import kotlinx.coroutines.launch

/**
 * « Partager → WiDo » depuis n'importe quelle appli (YouTube, navigateur…) : sur téléphone, le lien
 * se regarde ici ou part sur la TV choisie ; sur TV, il s'ouvre tout de suite.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as WiDoApp
        val url = sharedUrl(intent)
        if (url == null) {
            Toast.makeText(this, R.string.share_no_link, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val peer = app.link.selectedPeer()
        if (app.isTv || peer == null) {
            watchHere(url)
            return
        }
        setContent { WiDoTheme { ShareSheet(url, peer, app.link) } }
    }

    private fun watchHere(url: String) {
        startActivity(PlayerActivity.intent(this, ConfigRepository.LINK_SITE_ID, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Composable
    private fun ShareSheet(url: String, peer: Peer, link: ScreenLink) {
        val scope = rememberCoroutineScope()
        var sending by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        // Statut frais de la TV : le bouton dit tout de suite si elle répond.
        var online by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(peer.id) { online = link.refresh(peer).online }
        val sent = stringResource(R.string.toast_sent_to_tv, peer.name)
        DialogFrame(
            title = stringResource(R.string.share_title),
            onDismiss = { finish() },
            buttons = {
                ActionButton(stringResource(R.string.share_here), { watchHere(url) })
                ActionButton(
                    stringResource(if (sending) R.string.share_sending else R.string.panel_send_to_tv, peer.name),
                    {
                        if (!sending) {
                            sending = true
                            failed = false
                            scope.launch {
                                if (link.play(peer, HandoffPlay(url))) {
                                    Toast.makeText(this@ShareActivity, sent, Toast.LENGTH_SHORT).show()
                                    // L'onglet TV de WiDo, pour piloter la vidéo.
                                    startActivity(MainActivity.showTv(this@ShareActivity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    finish()
                                } else {
                                    sending = false
                                    failed = true
                                }
                            }
                        }
                    },
                    primary = false,
                )
            },
        ) {
            Text(Hosts.of(url).orEmpty().removePrefix("www."), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            when {
                failed -> Text(stringResource(R.string.toast_tv_unreachable, peer.name), color = Palette.Warning, fontSize = 13.sp)
                online == false -> Text(stringResource(R.string.tv_offline, peer.name), color = Palette.TextMuted, fontSize = 13.sp)
            }
        }
    }

    private companion object {
        private val URL = Regex("https?://[^\\s<>\"]+")

        /** Le premier lien du texte partagé (souvent « titre + lien »). */
        fun sharedUrl(intent: Intent): String? {
            if (intent.action != Intent.ACTION_SEND) return intent.dataString?.takeIf { URL.matches(it) }
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            return URL.find(text)?.value?.trimEnd('.', ',', ')', ';')?.takeIf { Hosts.of(it) != null }
        }
    }
}
