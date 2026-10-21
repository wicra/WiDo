package dev.wido.tv.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wido.core.Navigation
import dev.wido.tv.KeyAction
import dev.wido.tv.ListStatus
import dev.wido.tv.R
import dev.wido.tv.Settings
import dev.wido.tv.WiDoApp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

private val SPEEDS = listOf(0.6f, 1.0f, 1.5f, 2.2f)
private val SIZES = listOf(1.3f, 1.8f, 2.5f)
private val HIDES = listOf(0, 2, 3, 5, 10)
private val ZOOMS = (100..200 step 10).toList()
private val SEEKS = listOf(5, 10, 15, 30)
private val OSDS = listOf(2, 4, 6, 10)
private val UI_SCALES = listOf(1f, 1.15f, 1.3f)
private val NAVIGATIONS = listOf(Navigation.STRICT, Navigation.RELAXED, Navigation.MIXED)

/** Couleurs des touches de la télécommande. */
private val KEY_RED = Color(0xFFE5484D)
private val KEY_GREEN = Color(0xFF30A46C)
private val KEY_YELLOW = Color(0xFFF5C342)
private val KEY_BLUE = Color(0xFF1E90FF)

/** Indice de la valeur la plus proche : les réglages décimaux ne sont pas comparés à l'égalité stricte. */
private fun nearest(options: List<Float>, value: Float): Int =
    options.indices.minByOrNull { kotlin.math.abs(options[it] - value) } ?: 0

/** Passe à la valeur suivante ou précédente, en s'arrêtant aux extrémités. */
private fun <T> clampedStep(options: List<T>, current: T, step: Int): T =
    options[((options.indexOf(current).takeIf { it >= 0 } ?: 0) + step).coerceIn(0, options.lastIndex)]

/**
 * Onglet Réglages, rangé par thème (TV : thèmes en colonne à gauche ; téléphone : une seule liste). Chaque
 * choix est une pastille : on la sélectionne avec les flèches puis OK. La valeur choisie est en verre
 * clair, la pastille focalisée en blanc. Les changements s'appliquent à l'ouverture du site suivant.
 */
@Composable
fun SettingsPane(
    settings: Settings,
    lists: ListStatus,
    onUpdateLists: (force: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Les réglages ne sont pas un état Compose : ce compteur force le redessin après chaque changement.
    var version by remember { mutableIntStateOf(0) }
    val refresh = version
    fun changed(block: () -> Unit) {
        block()
        version++
    }

    val app = LocalContext.current.applicationContext as WiDoApp
    val remote = app.remote
    val phone = LocalPhone.current
    val peers by app.link.peers.collectAsState()
    var showCrash by remember { mutableStateOf(false) }
    val on = stringResource(R.string.v_on)
    val off = stringResource(R.string.v_off)

    // Chaque thème des réglages ; la TV les range en colonne à gauche, le téléphone les enchaîne.
    val blocking: @Composable () -> Unit = {
        SettingRow(stringResource(R.string.s_recommended)) {
            Segmented(listOf(on, off), if (settings.recommendedLists) 0 else 1) {
                changed { settings.recommendedLists = it == 0 }
                onUpdateLists(false)
            }
        }
        if (settings.recommendedLists) {
            SettingRow(stringResource(R.string.s_light_lists)) {
                Segmented(listOf(stringResource(R.string.v_lists_full), stringResource(R.string.v_lists_light)), if (settings.lightLists) 1 else 0) {
                    changed { settings.lightLists = it == 1 }
                    onUpdateLists(false)
                }
            }
        }
        SettingRow(stringResource(R.string.s_youtube)) {
            Segmented(listOf(on, off), if (settings.youtubeAdblock) 0 else 1) { changed { settings.youtubeAdblock = it == 0 } }
        }
        SettingRow(stringResource(R.string.s_sponsor)) {
            Segmented(listOf(on, off), if (settings.sponsorBlock) 0 else 1) { changed { settings.sponsorBlock = it == 0 } }
        }
        ListsRow(lists, onUpdate = { onUpdateLists(true) })
        val blocked = remember(refresh) { settings.blockedThisMonth() }
        if (blocked > 0) {
            Text(stringResource(R.string.stats_total, java.text.NumberFormat.getIntegerInstance().format(blocked)), color = Palette.TextSecondary, fontSize = 13.sp)
        }
    }
    val player: @Composable () -> Unit = {
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
        SettingRow(stringResource(R.string.s_auto_native)) {
            Segmented(listOf(on, off), if (settings.autoNative) 0 else 1) { changed { settings.autoNative = it == 0 } }
        }
        SettingRow(stringResource(R.string.s_auto_fullscreen)) {
            Segmented(listOf(on, off), if (settings.autoFullscreen) 0 else 1) { changed { settings.autoFullscreen = it == 0 } }
        }
        SettingRow(stringResource(R.string.s_resume)) {
            Segmented(
                listOf(stringResource(R.string.v_resume_last), stringResource(R.string.v_resume_home)),
                if (settings.resumeLastPage) 0 else 1,
            ) { changed { settings.resumeLastPage = it == 0 } }
        }
        if (!phone) {
            SettingRow(stringResource(R.string.s_volume)) {
                Segmented(
                    listOf(stringResource(R.string.v_volume_wido), stringResource(R.string.v_volume_system)),
                    if (settings.customVolume) 0 else 1,
                ) { changed { settings.customVolume = it == 0 } }
            }
            SettingRow(stringResource(R.string.s_field_qr)) {
                Segmented(listOf(on, off), if (settings.fieldPhoneQr) 0 else 1) { changed { settings.fieldPhoneQr = it == 0 } }
            }
            SettingRow(stringResource(R.string.s_fs_keys)) {
                Segmented(
                    listOf(stringResource(R.string.v_fs_player), stringResource(R.string.v_fs_cursor)),
                    if (settings.fullscreenPlayerKeys) 0 else 1,
                ) { changed { settings.fullscreenPlayerKeys = it == 0 } }
            }
        }
    }
    val look: @Composable () -> Unit = {
        SettingRow(stringResource(R.string.s_ambience)) {
            Segmented(
                listOf(R.string.v_amb_blue, R.string.v_amb_violet, R.string.v_amb_amber, R.string.v_amb_black).map { stringResource(it) },
                Ambience.ALL.indexOf(settings.ambience).coerceAtLeast(0),
            ) { changed { settings.ambience = Ambience.ALL[it] } }
        }
        if (!phone) {
            SettingRow(stringResource(R.string.s_ui_sounds)) {
                Segmented(
                    listOf(off, stringResource(R.string.v_sounds_soft), stringResource(R.string.v_sounds_normal)),
                    settings.uiSounds.coerceIn(0, 2),
                ) { changed { settings.uiSounds = it } }
            }
            SettingRow(stringResource(R.string.s_ui_scale)) {
                Segmented(
                    listOf(stringResource(R.string.v_scale_normal), stringResource(R.string.v_scale_large), stringResource(R.string.v_scale_huge)),
                    nearest(UI_SCALES, settings.uiScale),
                ) { changed { settings.uiScale = UI_SCALES[it] } }
            }
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
                Segmented(
                    HIDES.map { if (it == 0) stringResource(R.string.v_never) else stringResource(R.string.v_seconds, it) },
                    HIDES.indexOf(settings.cursorHideSeconds).coerceAtLeast(0),
                ) { changed { settings.cursorHideSeconds = HIDES[it] } }
            }
            SettingRow(stringResource(R.string.s_snap)) {
                Segmented(listOf(on, off), if (settings.snapNavigation) 0 else 1) { changed { settings.snapNavigation = it == 0 } }
            }
            SettingRow(stringResource(R.string.s_onboarding)) {
                Chip(stringResource(R.string.v_onboarding_show), { settings.onboardingDone = false })
            }
        }
    }
    val keys: @Composable () -> Unit = {
        KeyRow(R.string.s_key_red, KEY_RED, settings.keyRed) { changed { settings.keyRed = it } }
        KeyRow(R.string.s_key_green, KEY_GREEN, settings.keyGreen) { changed { settings.keyGreen = it } }
        KeyRow(R.string.s_key_yellow, KEY_YELLOW, settings.keyYellow) { changed { settings.keyYellow = it } }
        KeyRow(R.string.s_key_blue, KEY_BLUE, settings.keyBlue) { changed { settings.keyBlue = it } }
    }
    val screens: @Composable () -> Unit = {
        if (!phone) {
            SettingRow(stringResource(R.string.s_screen_link)) {
                Segmented(listOf(on, off), if (settings.screenLink) 0 else 1) {
                    changed { settings.screenLink = it == 0 }
                    remote.restart()
                }
            }
            if (settings.screenLink) {
                SettingRow(stringResource(R.string.s_device_name)) {
                    DeviceNameField(settings.deviceName) {
                        changed { settings.deviceName = it }
                        remote.readvertise()
                    }
                }
                LinkPairingCard(remote.pairUri, remote.address)
                LinkedDevices(
                    peers,
                    onLevel = { peer, level -> app.link.setLevel(peer.id, level) },
                    onForget = { peer -> app.link.forget(peer.id) },
                )
            } else {
                Text(stringResource(R.string.link_off_hint), color = Palette.TextSecondary, fontSize = 13.sp)
            }
            SectionLabel(stringResource(R.string.sec_phone), Modifier.padding(top = 10.dp))
            SettingRow(stringResource(R.string.s_phone_remote)) {
                Segmented(listOf(on, off), if (settings.phoneRemote) 0 else 1) {
                    changed { settings.phoneRemote = it == 0 }
                    remote.restart()
                }
            }
            if (settings.phoneRemote) {
                RemotePairing(remote.url) {
                    changed { settings.newRemoteToken() }
                    remote.restart()
                }
            } else {
                Text(stringResource(R.string.remote_off_hint), color = Palette.TextSecondary, fontSize = 13.sp)
            }
        } else {
            SettingRow(stringResource(R.string.s_device_name_phone)) {
                DeviceNameField(settings.deviceName) { changed { settings.deviceName = it } }
            }
            SettingRow(stringResource(R.string.s_tv_notification)) {
                Segmented(listOf(on, off), if (settings.tvNotification) 0 else 1) { changed { settings.tvNotification = it == 0 } }
            }
            SettingRow(stringResource(R.string.s_background_play)) {
                Segmented(listOf(on, off), if (settings.backgroundPlay) 0 else 1) { changed { settings.backgroundPlay = it == 0 } }
            }
        }
    }
    val general: @Composable () -> Unit = {
        if (!phone) SettingRow(stringResource(R.string.s_startup)) {
            Segmented(
                listOf(stringResource(R.string.v_startup_menu), stringResource(R.string.v_startup_last)),
                if (settings.startupLastSite) 1 else 0,
            ) { changed { settings.startupLastSite = it == 1 } }
        }
        if (!phone) SettingRow(stringResource(R.string.s_double_back)) {
            Segmented(listOf(on, off), if (settings.doubleBackToSites) 0 else 1) { changed { settings.doubleBackToSites = it == 0 } }
        }
        SettingRow(stringResource(R.string.s_new_relaxed)) {
            Segmented(
                listOf(stringResource(R.string.v_strict), stringResource(R.string.v_relaxed), stringResource(R.string.v_mixed)),
                NAVIGATIONS.indexOf(settings.newSitesNavigation).coerceAtLeast(0),
            ) { changed { settings.newSitesNavigation = NAVIGATIONS[it] } }
        }
        SettingRow(stringResource(R.string.s_crash_report)) {
            val report = remember(refresh) { dev.wido.tv.CrashLog.report() }
            if (report == null) {
                Text(stringResource(R.string.crash_none), color = Palette.TextSecondary, fontSize = 14.sp)
            } else {
                Chip(stringResource(R.string.crash_show), { showCrash = true }, glyph = Glyph.Warning)
                Chip(stringResource(R.string.crash_clear), { changed { dev.wido.tv.CrashLog.clear() } }, idleText = Color.White.copy(alpha = 0.6f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActionButton(stringResource(R.string.settings_reset), { changed { settings.resetComfort() } }, primary = false, idleText = Color.White.copy(alpha = 0.7f))
            Text(stringResource(R.string.settings_hint), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }

    val sections = listOfNotNull(
        SettingsSection(R.string.sec_blocking, Glyph.Blocked, blocking),
        SettingsSection(R.string.sec_player, Glyph.Play, player),
        SettingsSection(R.string.sec_look, Glyph.ZoomIn, look),
        if (phone) null else SettingsSection(R.string.sec_keys, Glyph.Menu, keys),
        SettingsSection(if (phone) R.string.sec_device else R.string.sec_screens, if (phone) Glyph.Phone else Glyph.Tv, screens),
        SettingsSection(R.string.sec_general, Glyph.Sites, general),
    )

    if (showCrash) dev.wido.tv.CrashLog.report()?.let { CrashReportDialog(it) { showCrash = false } }

    if (phone) {
        // Téléphone : tous les thèmes à la suite, dans une seule liste.
        Box(modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (refresh < 0) return@Column
                sections.forEachIndexed { index, section ->
                    SectionLabel(stringResource(section.title), if (index > 0) Modifier.padding(top = 10.dp) else Modifier)
                    section.content()
                }
            }
        }
        return
    }

    // TV : les thèmes en colonne à gauche (le thème sélectionné s'affiche dès qu'il a le focus), leurs réglages à droite.
    var selected by remember { mutableIntStateOf(0) }
    Row(modifier.fillMaxSize().padding(start = 40.dp, end = 32.dp, top = 12.dp)) {
        Column(Modifier.width(250.dp).padding(end = 22.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sections.forEachIndexed { index, section -> SectionTab(section, index == selected) { selected = index } }
        }
        Box(Modifier.weight(1f).fillMaxSize()) {
            androidx.compose.runtime.key(selected) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 56.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (refresh < 0) return@Column
                    val section = sections[selected.coerceIn(0, sections.lastIndex)]
                    Text(stringResource(section.title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = Modifier.padding(bottom = 4.dp))
                    section.content()
                }
            }
            // La liste s'efface sous le bas de l'écran.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Background.copy(alpha = 0.9f)))),
            )
        }
    }
}

/**
 * Le rapport de plantage : en texte, et en QR code sur la TV (le scanner avec le téléphone pour le
 * transmettre sans câble). Sur téléphone : copier ou partager.
 */
@Composable
private fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    DialogFrame(
        title = stringResource(R.string.s_crash_report),
        onDismiss = onDismiss,
        buttons = {
            if (dev.wido.tv.CrashReporter.canSend) {
                // Un seul appui : le rapport part, sans les noms de sites, vers le dépôt (via le relais).
                ActionButton(
                    stringResource(if (sent) R.string.crash_sent else if (sending) R.string.crash_sending else R.string.crash_send),
                    {
                        if (!sending && !sent) {
                            sending = true
                            scope.launch {
                                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.wido.tv.CrashReporter.send(report) }
                                sending = false
                                sent = result == dev.wido.tv.CrashReporter.Result.Sent
                                android.widget.Toast.makeText(context, if (sent) R.string.crash_send_ok else R.string.crash_send_failed, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    glyph = Glyph.Check,
                )
            } else if (phone) {
                // Pas de relais déployé : la page « Nouvelle issue » de GitHub, pré-remplie, à valider soi-même.
                ActionButton(stringResource(R.string.crash_report_github), {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, dev.wido.tv.CrashReporter.issueUri(report))) }
                }, glyph = Glyph.Check)
            }
            if (phone) {
                ActionButton(stringResource(R.string.crash_copy), {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("WiDo", report))
                    android.widget.Toast.makeText(context, R.string.crash_copied, android.widget.Toast.LENGTH_SHORT).show()
                })
                ActionButton(stringResource(R.string.crash_share), {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, report)
                    context.startActivity(android.content.Intent.createChooser(send, null))
                }, primary = false)
            }
            ActionButton(stringResource(R.string.action_close), onDismiss, primary = false)
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (!phone) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(10.dp)) { QrCode(report.take(QR_CHARS), 220.dp) }
                    Text(stringResource(R.string.crash_scan), color = Palette.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(240.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dev.wido.tv.CrashReporter.canSend) {
                Text(stringResource(R.string.crash_send_note), color = Palette.TextMuted, fontSize = 12.sp)
            }
            Text(
                report.take(3_000),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Palette.TextSecondary,
            )
            }
        }
    }
}

/** Ce qu'un QR code lisible tient du rapport (le début : version, appareil, erreur). */
private const val QR_CHARS = 1_200

/** Un thème des réglages : titre, icône et contenu. */
private class SettingsSection(val title: Int, val glyph: Glyph, val content: @Composable () -> Unit)

/** Un thème dans la colonne de gauche (TV) : il s'ouvre dès qu'il reçoit le focus. */
@Composable
private fun SectionTab(section: SettingsSection, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusable(
                RowShape,
                focusScale = 1.03f,
                lift = 8.dp,
                onFocusChange = {
                    focused = it
                    if (it) onSelect()
                },
                onClick = onSelect,
            )
            .background(background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphIcon(section.glyph, if (focused) Palette.Ink else Color.White.copy(alpha = if (selected) 1f else 0.7f), 20.dp)
        Text(
            stringResource(section.title),
            color = if (focused) Palette.Ink else Color.White.copy(alpha = if (selected) 1f else 0.75f),
            fontSize = 16.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** QR code d'appairage de la télécommande sur téléphone, et bouton pour changer le code. */
@Composable
private fun RemotePairing(url: String?, onNewCode: () -> Unit) {
    if (url == null) {
        Text(stringResource(R.string.phone_no_network), color = Palette.Warning, fontSize = 14.sp)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(10.dp)) { QrCode(url, 150.dp) }
        Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.remote_scan), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(url.substringBefore("?"), color = Palette.TextSecondary, fontSize = 13.sp)
            ActionButton(stringResource(R.string.remote_new_code), onNewCode, primary = false, compact = true)
        }
    }
}

/** État des listes de blocage et bouton pour les retélécharger. */
@Composable
private fun ListsRow(lists: ListStatus, onUpdate: () -> Unit) {
    val numbers = java.text.NumberFormat.getIntegerInstance()
    val summary = if (lists.lists == 0 && !lists.updating) {
        ""
    } else {
        stringResource(R.string.lists_status, lists.lists, numbers.format(lists.hosts), numbers.format(lists.selectors), numbers.format(lists.advanced))
    }
    val date = lists.updatedAt?.let {
        stringResource(R.string.lists_updated, android.text.format.DateUtils.getRelativeTimeSpanString(it).toString())
    } ?: stringResource(R.string.lists_never)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (summary.isNotEmpty()) Text(summary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(date, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1)
        }
        ActionButton(
            stringResource(if (lists.updating) R.string.lists_updating else R.string.lists_update),
            { if (!lists.updating) onUpdate() },
            primary = false,
            compact = true,
        )
    }
}

/** Une touche de couleur : sa pastille, l'action actuelle et deux flèches pour parcourir les actions possibles. */
@Composable
private fun KeyRow(labelRes: Int, color: Color, current: KeyAction, onChange: (KeyAction) -> Unit) {
    val actions = KeyAction.entries
    SettingRow(stringResource(labelRes), swatch = color) {
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
    KeyAction.NATIVE_PLAYER -> R.string.a_native
}

/** Nom de l'appareil, tel que les autres écrans le voient. Enregistré en fin de saisie. */
@Composable
private fun DeviceNameField(current: String, onSave: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    // Enregistré une fois la saisie posée (l'annonce sur le réseau n'est pas refaite à chaque lettre).
    androidx.compose.runtime.LaunchedEffect(text) {
        kotlinx.coroutines.delay(1_200)
        if (text.isNotBlank() && text != current) onSave(text)
    }
    GlassTextField(
        value = text,
        onValueChange = { text = it.take(40) },
        label = stringResource(R.string.s_device_name_label),
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        keyboardOptions = KeyboardOptions.Default,
    )
}

/**
 * Une ligne : le libellé dans une colonne fixe, puis les choix à la suite. Sur téléphone, le libellé
 * passe au-dessus et les choix vont à la ligne au besoin.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingRow(label: String, swatch: Color? = null, controls: @Composable RowScope.() -> Unit) {
    if (LocalPhone.current) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = { controls() })
        }
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(240.dp), verticalAlignment = Alignment.CenterVertically) {
            if (swatch != null) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(swatch))
                Spacer(Modifier.width(12.dp))
            }
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = controls)
    }
}

/** Tous les choix côte à côte ; le choix actif est en verre clair. */
@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    labels.forEachIndexed { index, label -> Chip(label, { onSelect(index) }, selected = index == selected) }
}

/** `◀  valeur  ▶` pour les réglages à nombreuses valeurs : les deux flèches sont de vrais boutons. */
@Composable
private fun Stepper(value: String, onStep: (Int) -> Unit) {
    Chip("◀", { onStep(-1) })
    Box(Modifier.widthIn(min = 150.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
    }
    Chip("▶", { onStep(1) })
}
