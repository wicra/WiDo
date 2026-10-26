package dev.wido.tv

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dev.wido.core.PlayerOptions
import dev.wido.core.TrackOption
import java.util.Locale

/**
 * Ce qu'on peut régler dans le lecteur natif (qualité, piste audio, sous-titres, vitesse) et l'application d'un
 * choix, pour le menu de la TV comme pour le téléphone. Le choix de langue est retenu pour les films suivants.
 */
class PlayerTracks(
    private val context: Context,
    private val player: ExoPlayer,
    private val selector: DefaultTrackSelector,
    private val settings: Settings,
    /** Plafond de l'appareil (1080 en mode économe). */
    private val maxHeight: Int,
) {
    /** Qualité imposée (hauteur d'image), null : automatique. */
    private var fixedHeight: Int? = null

    /** Les langues retenues (films précédents) : appliquées avant que le flux ne choisisse ses pistes. */
    fun applyPreferences() {
        val audio = settings.preferredAudioLanguage
        val text = settings.preferredTextLanguage
        selector.setParameters(
            selector.buildUponParameters().apply {
                if (audio != null) setPreferredAudioLanguage(audio)
                when (text) {
                    null -> Unit
                    PlayerOptions.OFF -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    else -> setPreferredTextLanguage(text)
                }
            },
        )
    }

    fun options(): PlayerOptions {
        val groups = player.currentTracks.groups
        val heights = tracksOf(groups, C.TRACK_TYPE_VIDEO).map { (g, i) -> g.getTrackFormat(i).height }
        val now = player.videoSize.height.takeIf { it > 0 }
        val auto = if (fixedHeight == null && now != null) context.getString(R.string.opt_auto_now, now) else context.getString(R.string.opt_auto)
        val quality = PlayerOptions.qualities(heights, maxHeight, fixedHeight, auto)

        val audioTracks = tracksOf(groups, C.TRACK_TYPE_AUDIO)
        val audio = if (audioTracks.size < 2) emptyList() else distinctLabels(
            audioTracks.map { (g, i) -> TrackOption(idOf(groups, g, i), audioLabel(g.getTrackFormat(i)), g.isTrackSelected(i)) },
        )

        val textTracks = tracksOf(groups, C.TRACK_TYPE_TEXT)
        val textOff = C.TRACK_TYPE_TEXT in selector.parameters.disabledTrackTypes || textTracks.none { (g, i) -> g.isTrackSelected(i) }
        val subtitles = if (textTracks.isEmpty()) emptyList() else listOf(TrackOption(PlayerOptions.OFF, context.getString(R.string.opt_subtitles_off), textOff)) +
            distinctLabels(textTracks.map { (g, i) -> TrackOption(idOf(groups, g, i), textLabel(g.getTrackFormat(i)), !textOff && g.isTrackSelected(i)) })

        return PlayerOptions(quality, audio, subtitles, player.playbackParameters.speed)
    }

    /** Applique un choix ([PlayerOptions.KINDS]) ; faux si l'identifiant ne correspond plus à rien. */
    fun apply(kind: String, id: String): Boolean {
        val groups = player.currentTracks.groups
        when (kind) {
            PlayerOptions.QUALITY -> {
                val h = if (id == PlayerOptions.AUTO) null else id.toIntOrNull() ?: return false
                fixedHeight = h
                selector.setParameters(
                    selector.buildUponParameters().apply {
                        if (h == null) {
                            setMaxVideoSize(if (maxHeight < Int.MAX_VALUE) maxHeight * 16 / 9 else Int.MAX_VALUE, maxHeight)
                            setMinVideoSize(0, 0)
                        } else {
                            // Seulement les variantes de cette hauteur : le débit s'adapte encore entre elles.
                            setMaxVideoSize(Int.MAX_VALUE, h)
                            setMinVideoSize(0, h)
                        }
                    },
                )
                CrashLog.event("Lecteur léger : qualité ${h?.let { "${it}p" } ?: "auto"} choisie")
            }
            PlayerOptions.AUDIO -> {
                val (g, i) = trackFor(groups, id, C.TRACK_TYPE_AUDIO) ?: return false
                selector.setParameters(selector.buildUponParameters().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)))
                settings.preferredAudioLanguage = g.getTrackFormat(i).language
            }
            PlayerOptions.SUBTITLES -> {
                if (id == PlayerOptions.OFF) {
                    selector.setParameters(selector.buildUponParameters().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true))
                    settings.preferredTextLanguage = PlayerOptions.OFF
                } else {
                    val (g, i) = trackFor(groups, id, C.TRACK_TYPE_TEXT) ?: return false
                    selector.setParameters(
                        selector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)),
                    )
                    settings.preferredTextLanguage = g.getTrackFormat(i).language
                }
            }
            PlayerOptions.SPEED -> {
                val speed = id.toFloatOrNull()?.takeIf { it in PlayerOptions.SPEEDS } ?: return false
                player.setPlaybackSpeed(speed)
            }
            else -> return false
        }
        return true
    }

    /** Libellé court du réglage en cours, pour une tuile du menu (« Français · Sous-titres : Anglais »). */
    fun summary(o: PlayerOptions = options()): String {
        val parts = listOfNotNull(
            o.audio.firstOrNull { it.selected }?.label,
            o.subtitles.firstOrNull { it.selected }?.let { if (it.id == PlayerOptions.OFF) null else context.getString(R.string.opt_subtitles_short, it.label) },
            o.quality.firstOrNull { it.selected }?.takeIf { it.id != PlayerOptions.AUTO }?.label,
            o.speed.takeIf { it != 1f }?.let { PlayerOptions.speedLabel(it, decimalComma()) },
        )
        return parts.joinToString(" · ")
    }

    fun decimalComma() = Locale.getDefault().language == "fr"

    private fun tracksOf(groups: List<Tracks.Group>, type: Int) =
        groups.filter { it.type == type }.flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }

    private fun idOf(groups: List<Tracks.Group>, g: Tracks.Group, i: Int) = "${groups.indexOf(g)}:$i"

    private fun trackFor(groups: List<Tracks.Group>, id: String, type: Int): Pair<Tracks.Group, Int>? {
        val gi = id.substringBefore(':').toIntOrNull() ?: return null
        val ti = id.substringAfter(':').toIntOrNull() ?: return null
        val g = groups.getOrNull(gi)?.takeIf { it.type == type && ti in 0 until it.length && it.isTrackSupported(ti) } ?: return null
        return g to ti
    }

    private fun language(format: Format): String? {
        val tag = format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED } ?: return null
        val name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault()).takeIf { it.isNotBlank() && it != tag } ?: return tag
        return name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    private fun audioLabel(f: Format): String {
        val base = f.label?.takeIf { it.isNotBlank() } ?: language(f) ?: context.getString(R.string.opt_track)
        val extra = when {
            f.channelCount >= 6 -> " 5.1"
            else -> ""
        }
        val described = if (f.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) " " + context.getString(R.string.opt_described) else ""
        return base + extra + described
    }

    private fun textLabel(f: Format): String {
        val base = f.label?.takeIf { it.isNotBlank() } ?: language(f) ?: context.getString(R.string.opt_track)
        val forced = if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) " " + context.getString(R.string.opt_forced) else ""
        val cc = if (f.roleFlags and (C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0) " " + context.getString(R.string.opt_cc) else ""
        return base + forced + cc
    }

    /** Deux pistes au même nom (« Français », « Français ») : numérotées pour qu'on les distingue. */
    private fun distinctLabels(list: List<TrackOption>): List<TrackOption> {
        val counts = list.groupingBy { it.label }.eachCount()
        val seen = HashMap<String, Int>()
        return list.map { o ->
            if ((counts[o.label] ?: 0) < 2) return@map o
            val n = (seen[o.label] ?: 0) + 1
            seen[o.label] = n
            o.copy(label = "${o.label} $n")
        }
    }
}
