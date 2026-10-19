package dev.wido.tv

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import dev.wido.core.PlayerFormat

/**
 * Affichage à l'écran, au bas de la page : barre de lecture, volume, ou simple message (zoom…).
 * Disparaît seul après `hideSeconds`.
 */
class OsdView(context: Context) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val title = TextView(context).apply {
        setTextColor(0xFFF4F7FC.toInt())
        textSize = 20f
    }
    private val detail = TextView(context).apply {
        setTextColor(0xFF9AAAC4.toInt())
        textSize = 16f
        gravity = Gravity.END
    }
    private val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progressTintList = ColorStateList.valueOf(0xFF1E90FF.toInt())
        progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
    }
    private val hideRunnable = Runnable { animate().alpha(0f).setDuration(200).withEndAction { visibility = GONE }.start() }

    /** Secondes d'affichage ; mis à jour depuis les réglages à chaque ouverture du lecteur. */
    var hideSeconds = 4

    init {
        orientation = VERTICAL
        visibility = GONE
        val pad = (20 * density).toInt()
        setPadding(pad, (14 * density).toInt(), pad, (16 * density).toInt())
        background = GradientDrawable().apply {
            setColor(0xE60B1020.toInt())
            cornerRadius = 18 * density
            setStroke((1 * density).toInt(), 0x331E90FF)
        }
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(detail, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (6 * density).toInt()).apply { topMargin = (10 * density).toInt() })
    }

    fun layoutParamsForRoot() = FrameLayout.LayoutParams(
        (640 * density).toInt(),
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
    ).apply { bottomMargin = (36 * density).toInt() }

    /** `passive` : rafraîchit les valeurs d'une barre déjà visible, sans la maintenir affichée. */
    fun showMedia(state: MediaState, passive: Boolean = false) {
        if (passive && !isShowing) return
        title.text = if (state.paused) "❚❚  Pause" else "▶  Lecture"
        detail.text = "${PlayerFormat.time(state.time)} / ${PlayerFormat.time(state.duration)}   ·   🔊 ${PlayerFormat.percent(state.volume)} %"
        bar.progress = if (state.duration > 0) (state.time / state.duration * 1000).toInt().coerceIn(0, 1000) else 0
        bar.visibility = View.VISIBLE
        if (!passive) reveal()
    }

    fun showVolume(level: Int, max: Int) {
        title.text = if (level == 0) "🔇  Volume" else "🔊  Volume"
        detail.text = if (max > 0) "${level * 100 / max} %" else "$level"
        bar.progress = if (max > 0) level * 1000 / max else 0
        bar.visibility = View.VISIBLE
        reveal()
    }

    fun showText(text: String) {
        title.text = text
        detail.text = ""
        bar.visibility = View.GONE
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
}
