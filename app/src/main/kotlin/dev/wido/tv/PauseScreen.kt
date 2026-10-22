package dev.wido.tv

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * L'écran de pause (TV), le même dans la page du site et dans le lecteur léger : fond de WiDo assombri,
 * titre, détail (site, temps restant, heure de fin) et rappel des touches en bas. Créé transparent : à
 * l'appelant de le faire apparaître.
 */
object PauseScreen {
    fun build(context: Context, title: String, detail: String, hint: String, scale: Float): View {
        val density = context.resources.displayMetrics.density
        fun text(value: String, size: Float, color: Int, weight: Int, top: Int) = TextView(context).apply {
            text = value
            setTextColor(color)
            textSize = size * scale
            useJakarta(weight)
            setPadding(0, (top * density).toInt(), 0, 0)
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                text(title, 34f, 0xFFFFFFFF.toInt(), 800, 0).apply {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    letterSpacing = -0.02f
                },
            )
            if (detail.isNotEmpty()) addView(text(detail, 17f, 0xCCFFFFFF.toInt(), 600, 10))
            addView(text(hint, 14f, 0x99FFFFFF.toInt(), 600, 18))
        }
        return FrameLayout(context).apply {
            background = PauseBackdrop.drawable(context.resources)
            val pad = (56 * density).toInt()
            addView(
                column,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
                    .apply { setMargins(pad, 0, pad, (48 * density).toInt()) },
            )
            alpha = 0f
            isFocusable = false
        }
    }
}
