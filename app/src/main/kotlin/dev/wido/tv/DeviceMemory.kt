package dev.wido.tv

import android.app.ActivityManager
import android.content.Context

/** Appareils à peu de mémoire (clés TV d'1 Go…), où Android ferme vite l'appli pendant une vidéo. */
object DeviceMemory {
    private var low: Boolean? = null

    fun isLow(context: Context): Boolean = low ?: run {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        (am.isLowRamDevice || info.totalMem < 1_700L * 1_048_576).also { low = it }
    }
}
