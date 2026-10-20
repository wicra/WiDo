package dev.wido.tv

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import dev.wido.core.PlayerFormat
import java.util.Date

/**
 * Affichage à l'écran, au bas de la page : barre de lecture (image et titre de la vidéo, temps restant,
 * heure de fin), volume, ou message avec son icône (réussi, attention, bloqué). Disparaît seul après
 * `hideSeconds`. Tous les messages du lecteur passent par ici, dans le style de WiDo.
 */
class OsdView(context: Context, private val compact: Boolean = false, scale: Float = 1f) : LinearLayout(context) {
    enum class Kind { Info, Success, Warning, Blocked, Play, Pause, Volume }

    private val density = resources.displayMetrics.density * scale
    private val textScale = scale

    private val icon = IconView(context)
    private val thumb = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = GONE
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, 8 * density)
        }
    }
    private val title = TextView(context).apply {
        setTextColor(0xFFFFFFFF.toInt())
        textSize = (if (compact) 15f else 18f) * textScale
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        useJakarta(700)
    }
    private val detail = TextView(context).apply {
        setTextColor(0xA6FFFFFF.toInt())
        textSize = (if (compact) 12f else 14f) * textScale
        maxLines = 1
        gravity = Gravity.END
        useJakarta(600)
    }
    private val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progressTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
        progressBackgroundTintList = ColorStateList.valueOf(0x38FFFFFF)
    }
    private val elapsed = smallText(Gravity.START)
    private val remaining = smallText(Gravity.END)
    private val times = LinearLayout(context).apply {
        orientation = HORIZONTAL
        addView(elapsed, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(remaining, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private val hideRunnable = Runnable { animate().alpha(0f).setDuration(200).withEndAction { visibility = GONE }.start() }

    /** Secondes d'affichage ; mis à jour depuis les réglages à chaque ouverture du lecteur. */
    var hideSeconds = 4

    private var videoTitle: String? = null
    private var siteName: String? = null
    private var seekLabel: String? = null
    private var seekAt = 0L

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        val pad = ((if (compact) 14 else 18) * density).toInt()
        setPadding(pad, ((if (compact) 11 else 14) * density).toInt(), pad, ((if (compact) 11 else 14) * density).toInt())
        // Verre du style : panneau sombre translucide, bord clair d'un pixel, coins arrondis.
        background = GradientDrawable().apply {
            setColor(0xEB1A1A26.toInt())
            cornerRadius = (if (compact) 18 else 24) * density
            setStroke((1 * density).toInt(), 0x24FFFFFF)
        }
        elevation = 24 * density
        val thumbW = ((if (compact) 64 else 96) * density).toInt()
        addView(thumb, LayoutParams(thumbW, thumbW * 9 / 16).apply { marginEnd = (14 * density).toInt() })
        val iconSize = ((if (compact) 20 else 24) * density).toInt()
        addView(icon, LayoutParams(iconSize, iconSize).apply { marginEnd = (12 * density).toInt() })
        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            val header = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(detail, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = (12 * density).toInt() })
            }
            addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (5 * density).toInt()).apply { topMargin = ((if (compact) 8 else 10) * density).toInt() })
            addView(times, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (6 * density).toInt() })
        }
        addView(column, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun smallText(g: Int) = TextView(context).apply {
        setTextColor(0xB3FFFFFF.toInt())
        textSize = (if (compact) 11f else 13f) * textScale
        maxLines = 1
        gravity = g
        useJakarta(600)
    }

    /** TV : panneau de 720 dp centré. Téléphone : toute la largeur moins une marge, quelle que soit l'orientation. */
    fun layoutParamsForRoot() = FrameLayout.LayoutParams(
        if (compact) ViewGroup.LayoutParams.MATCH_PARENT else (720 * density).toInt(),
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
    ).apply {
        if (compact) {
            val side = (12 * density).toInt()
            setMargins(side, 0, side, (20 * density).toInt())
        } else {
            bottomMargin = (36 * density).toInt()
        }
    }

    /** Titre de la vidéo et nom du site, affichés dans la barre de lecture. */
    fun setInfo(videoTitle: String?, siteName: String?) {
        this.videoTitle = videoTitle?.trim()?.takeIf { it.isNotEmpty() }
        this.siteName = siteName
    }

    /** Image de la vidéo (celle de « Reprendre »), ou null. */
    fun setThumb(bitmap: Bitmap?) {
        thumb.setImageBitmap(bitmap)
        thumbAvailable = bitmap != null
    }

    private var thumbAvailable = false

    /** Le prochain affichage de la barre montre ce saut (« +30 s »). */
    fun markSeek(seconds: Double) {
        val s = seconds.toInt()
        seekLabel = if (s >= 0) "+$s s" else "−${-s} s"
        seekAt = SystemClock.uptimeMillis()
    }

    /** Le saut qui vient d'être demandé (« +30 s »), seul : le lecteur du site montre déjà sa barre. */
    fun showSeek() {
        val seek = seekLabel?.takeIf { SystemClock.uptimeMillis() - seekAt < 1_500 } ?: return
        seekLabel = null
        showText(seek)
    }

    /** `passive` : rafraîchit les valeurs d'une barre déjà visible, sans la maintenir affichée. */
    fun showMedia(state: MediaState, passive: Boolean = false) {
        if (passive && !isShowing) return
        icon.kind = if (state.paused) Kind.Pause else Kind.Play
        icon.visibility = VISIBLE
        thumb.visibility = if (thumbAvailable) VISIBLE else GONE
        title.text = videoTitle ?: context.getString(if (state.paused) R.string.osd_paused else R.string.osd_playing)
        val seek = seekLabel?.takeIf { SystemClock.uptimeMillis() - seekAt < 1_500 }
        detail.text = seek ?: siteName.orEmpty()
        detail.setTextColor(if (seek != null) 0xFFFFFFFF.toInt() else 0xA6FFFFFF.toInt())
        bar.progress = if (state.duration > 0) (state.time / state.duration * 1000).toInt().coerceIn(0, 1000) else 0
        bar.visibility = VISIBLE
        elapsed.text = if (state.duration > 0) "${PlayerFormat.time(state.time)} / ${PlayerFormat.time(state.duration)}" else PlayerFormat.time(state.time)
        remaining.text = if (state.duration > 0) {
            val left = (state.duration - state.time).coerceAtLeast(0.0)
            val end = DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + (left * 1000).toLong()))
            context.getString(R.string.osd_remaining, PlayerFormat.time(left), end)
        } else {
            ""
        }
        times.visibility = VISIBLE
        if (!passive) reveal()
    }

    fun showVolume(level: Int, max: Int) {
        icon.kind = if (level == 0) Kind.Blocked else Kind.Volume
        icon.visibility = VISIBLE
        thumb.visibility = GONE
        title.text = context.getString(R.string.osd_volume)
        detail.text = if (max > 0) "${level * 100 / max} %" else "$level"
        detail.setTextColor(0xFFFFFFFF.toInt())
        bar.progress = if (max > 0) level * 1000 / max else 0
        bar.visibility = VISIBLE
        times.visibility = GONE
        reveal()
    }

    fun showText(text: String, kind: Kind = Kind.Info) {
        icon.kind = kind
        icon.visibility = if (kind == Kind.Info) GONE else VISIBLE
        thumb.visibility = GONE
        title.text = text
        detail.text = ""
        bar.visibility = GONE
        times.visibility = GONE
        reveal()
    }

    private fun reveal() {
        removeCallbacks(hideRunnable)
        animate().cancel()
        visibility = VISIBLE
        alpha = 1f
        bringToFront()
        postDelayed(hideRunnable, hideSeconds * 1000L)
    }

    val isShowing get() = visibility == VISIBLE

    override fun onDetachedFromWindow() {
        removeCallbacks(hideRunnable)
        super.onDetachedFromWindow()
    }

    /** Petite icône dessinée (centrée, indépendante des polices). */
    private class IconView(context: Context) : View(context) {
        var kind = Kind.Info
            set(value) {
                field = value
                invalidate()
            }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            val s = width.toFloat()
            fun x(f: Float) = f * s
            paint.strokeWidth = s * 0.1f
            paint.color = when (kind) {
                Kind.Success -> 0xFF5BE39A.toInt()
                Kind.Warning, Kind.Blocked -> 0xFFFFB86B.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            val path = Path()
            when (kind) {
                Kind.Info -> Unit
                Kind.Play -> {
                    paint.style = Paint.Style.FILL
                    path.moveTo(x(0.3f), x(0.2f)); path.lineTo(x(0.82f), x(0.5f)); path.lineTo(x(0.3f), x(0.8f)); path.close()
                    canvas.drawPath(path, paint)
                }
                Kind.Pause -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(x(0.26f), x(0.2f), x(0.42f), x(0.8f), x(0.04f), x(0.04f), paint)
                    canvas.drawRoundRect(x(0.58f), x(0.2f), x(0.74f), x(0.8f), x(0.04f), x(0.04f), paint)
                }
                Kind.Success -> {
                    paint.style = Paint.Style.STROKE
                    path.moveTo(x(0.2f), x(0.52f)); path.lineTo(x(0.42f), x(0.74f)); path.lineTo(x(0.82f), x(0.28f))
                    canvas.drawPath(path, paint)
                }
                Kind.Warning -> {
                    paint.style = Paint.Style.STROKE
                    path.moveTo(x(0.5f), x(0.12f)); path.lineTo(x(0.9f), x(0.84f)); path.lineTo(x(0.1f), x(0.84f)); path.close()
                    canvas.drawPath(path, paint)
                    canvas.drawLine(x(0.5f), x(0.4f), x(0.5f), x(0.58f), paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(x(0.5f), x(0.71f), x(0.05f), paint)
                }
                Kind.Blocked -> {
                    paint.style = Paint.Style.STROKE
                    canvas.drawCircle(x(0.5f), x(0.5f), x(0.38f), paint)
                    canvas.drawLine(x(0.24f), x(0.24f), x(0.76f), x(0.76f), paint)
                }
                Kind.Volume -> {
                    paint.style = Paint.Style.FILL
                    path.moveTo(x(0.12f), x(0.38f)); path.lineTo(x(0.3f), x(0.38f)); path.lineTo(x(0.5f), x(0.2f))
                    path.lineTo(x(0.5f), x(0.8f)); path.lineTo(x(0.3f), x(0.62f)); path.lineTo(x(0.12f), x(0.62f)); path.close()
                    canvas.drawPath(path, paint)
                    paint.style = Paint.Style.STROKE
                    canvas.drawArc(x(0.42f), x(0.3f), x(0.78f), x(0.7f), -50f, 100f, false, paint)
                }
            }
        }
    }
}
