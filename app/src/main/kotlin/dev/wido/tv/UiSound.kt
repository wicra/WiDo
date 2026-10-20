package dev.wido.tv

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sons de l'interface (TV), dans l'esprit des interfaces de salon les plus soignées : feutrés, courts,
 * jamais agressifs. Un « tock » boisé au passage du focus, une goutte ronde à la validation, un
 * claquement de doigts adouci avec son écho à l'ouverture d'un site, deux notes descendantes au retour.
 * Attaques arrondies et aigus filtrés : pas de clic sec. Synthétisés au premier lancement (pas de
 * fichiers audio à fournir), joués en sons système : ils ne coupent jamais la vidéo.
 */
object UiSound {
    enum class Kind { Focus, Select, Open, Back }

    /** Niveaux du réglage « Sons de l'interface ». */
    const val OFF = 0
    const val SOFT = 1
    const val NORMAL = 2

    private var pool: SoundPool? = null
    private val ids = IntArray(Kind.entries.size)
    private val ready = BooleanArray(Kind.entries.size)
    private var lastFocus = 0L

    /** 0 = coupé. */
    @Volatile private var volume = 0f

    fun init(context: Context, level: Int) {
        setLevel(level)
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
        sp.setOnLoadCompleteListener { _, id, status ->
            val index = ids.indexOf(id)
            if (status == 0 && index >= 0) ready[index] = true
        }
        pool = sp
        val dir = File(context.cacheDir, "ui-sounds").apply { mkdirs() }
        // Les sons d'une version précédente ne servent plus.
        dir.listFiles()?.filterNot { it.name.endsWith("-v$VERSION.wav") }?.forEach { it.delete() }
        Thread {
            Kind.entries.forEach { kind ->
                runCatching {
                    val file = File(dir, "${kind.name.lowercase()}-v$VERSION.wav")
                    if (!file.exists()) file.writeBytes(wav(render(kind)))
                    ids[kind.ordinal] = sp.load(file.path, 1)
                }
            }
        }.start()
    }

    fun setLevel(level: Int) {
        volume = when (level) {
            OFF -> 0f
            SOFT -> 0.22f
            else -> 0.45f
        }
    }

    fun play(kind: Kind) {
        val v = volume
        if (v <= 0f || !ready[kind.ordinal]) return
        // Flèche maintenue : pas une rafale de tics.
        if (kind == Kind.Focus) {
            val now = SystemClock.uptimeMillis()
            if (now - lastFocus < 45) return
            lastFocus = now
        }
        val gain = v * when (kind) {
            Kind.Focus -> 0.5f
            Kind.Select -> 0.8f
            Kind.Open -> 0.85f
            Kind.Back -> 0.7f
        }
        pool?.play(ids[kind.ordinal], gain, gain, 1, 0, 1f)
    }

    fun focus() = play(Kind.Focus)
    fun select() = play(Kind.Select)
    fun open() = play(Kind.Open)
    fun back() = play(Kind.Back)

    // --- Synthèse -------------------------------------------------------------------------

    private const val VERSION = 3
    private const val RATE = 44_100

    private fun render(kind: Kind): FloatArray = when (kind) {
        // Tock : une note boisée très brève, aigus filtrés.
        Kind.Focus -> soften(
            synth(0.04) { t, _ -> (sin(2 * PI * 1_050 * t) * exp(-t / 0.004) + sin(2 * PI * 2_100 * t) * exp(-t / 0.002) * 0.15) * rise(t, 0.0015) },
            cutoff = 4_000.0,
        )
        // Goutte : une note ronde qui monte légèrement (la4 → mi5), avec une octave discrète.
        Kind.Select -> {
            var phase = 0.0
            soften(
                synth(0.16) { t, dt ->
                    phase += 2 * PI * (660 - 220 * exp(-t / 0.018)) * dt
                    (sin(phase) + sin(2 * phase) * 0.12) * exp(-t / 0.055) * rise(t, 0.004)
                },
                cutoff = 3_000.0,
            )
        }
        // Claquement de doigts adouci, avec l'écho de la pièce.
        Kind.Open -> soften(snapWithEchoes(), cutoff = 7_000.0)
        // Retour : deux notes qui descendent (ré5 → la4), qui se chevauchent.
        Kind.Back -> soften(
            synth(0.2) { t, _ ->
                val second = t - 0.06
                sin(2 * PI * 587 * t) * exp(-t / 0.045) * rise(t, 0.003) +
                    if (second > 0) sin(2 * PI * 440 * second) * exp(-second / 0.06) * rise(second, 0.003) * 0.9 else 0.0
            },
            cutoff = 3_000.0,
        )
    }

    /** Montée douce (demi-cosinus) : l'attaque ne claque pas. */
    private fun rise(t: Double, seconds: Double): Double = if (t >= seconds) 1.0 else 0.5 - 0.5 * kotlin.math.cos(PI * t / seconds)

    /** Filtre passe-bas (un pôle) puis remise au même niveau crête : un son plus feutré. */
    private fun soften(samples: FloatArray, cutoff: Double): FloatArray {
        val a = 1 - exp(-2 * PI * cutoff / RATE)
        var y = 0.0
        val out = FloatArray(samples.size) { i ->
            y += a * (samples[i] - y)
            y.toFloat()
        }
        val peak = out.maxOf { abs(it) }.coerceAtLeast(1e-6f)
        for (i in out.indices) out[i] = out[i] / peak * 0.9f
        return out
    }

    private fun snapWithEchoes(): FloatArray {
        val random = java.util.Random(11)
        val n = (0.03 * RATE).toInt()
        val core = snap(n, random, air = 0.45)
        val soft = snap(n, random, air = 0.15)
        val out = DoubleArray((0.45 * RATE).toInt())
        for (i in 0 until n) out[i] += core[i]
        // Échos de la pièce : de plus en plus loin, de plus en plus faibles.
        for ((ms, gain) in listOf(51 to 0.12, 117 to 0.08, 172 to 0.09, 258 to 0.05)) {
            val at = ms * RATE / 1000
            for (i in 0 until n) out[at + i] += soft[i] * gain
        }
        val peak = out.maxOf { abs(it) }.coerceAtLeast(1e-6)
        val fade = (0.004 * RATE).toInt()
        return FloatArray(out.size) { i ->
            val tail = if (i > out.size - fade) (out.size - i).toDouble() / fade else 1.0
            (out[i] / peak * 0.9 * tail).toFloat()
        }
    }

    private fun snap(n: Int, random: java.util.Random, air: Double): DoubleArray {
        val noise = DoubleArray(n) { random.nextDouble() * 2 - 1 }
        val mid = bandPass(noise, 2_300.0, 1.8)
        val high = bandPass(noise, 6_800.0, 1.4)
        return DoubleArray(n) { i ->
            val t = i.toDouble() / RATE
            val attack = rise(t, 0.0012)
            (mid[i] * exp(-t / 0.0045) * 2.6 + high[i] * exp(-t / 0.0025) * 3.4 * air + sin(2 * PI * 2_300 * t) * exp(-t / 0.006) * 0.3) * attack
        }
    }

    /** Filtre passe-bande du second ordre (biquad). */
    private fun bandPass(x: DoubleArray, f0: Double, q: Double): DoubleArray {
        val w = 2 * PI * f0 / RATE
        val alpha = sin(w) / (2 * q)
        val a0 = 1 + alpha
        val a1 = -2 * kotlin.math.cos(w)
        val a2 = 1 - alpha
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        return DoubleArray(x.size) { i ->
            val v = x[i]
            val y = (alpha * v - alpha * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1
            x1 = v
            y2 = y1
            y1 = y
            y
        }
    }

    private inline fun synth(seconds: Double, sample: (t: Double, dt: Double) -> Double): FloatArray {
        val n = (seconds * RATE).toInt()
        val dt = 1.0 / RATE
        val fade = (0.004 * RATE).toInt()
        return FloatArray(n) { i ->
            val t = i * dt
            // Fondu de quelques millisecondes à la fin : pas de clic parasite.
            val tail = if (i > n - fade) (n - i).toDouble() / fade else 1.0
            (sample(t, dt) * tail).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    /** PCM 16 bits mono dans un en-tête WAV. */
    private fun wav(samples: FloatArray): ByteArray {
        val data = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(data)
        samples.forEach { buffer.putShort((it * Short.MAX_VALUE).toInt().toShort()) }
        return buffer.array()
    }
}
