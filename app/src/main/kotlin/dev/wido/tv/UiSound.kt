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
 * Sons de l'interface (TV) : un tic doux au passage du focus, un « toc » franc à la validation, un
 * claquement de doigts avec son écho à l'ouverture d'un site, deux notes au retour. Synthétisés au premier lancement (pas de
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
            SOFT -> 0.3f
            else -> 0.65f
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
        val gain = if (kind == Kind.Focus) v * 0.7f else v
        pool?.play(ids[kind.ordinal], gain, gain, 1, 0, 1f)
    }

    fun focus() = play(Kind.Focus)
    fun select() = play(Kind.Select)
    fun open() = play(Kind.Open)
    fun back() = play(Kind.Back)

    // --- Synthèse -------------------------------------------------------------------------

    private const val VERSION = 2
    private const val RATE = 44_100

    private fun render(kind: Kind): FloatArray = when (kind) {
        // Tic : une note brève et claire, amortie en quelques millisecondes.
        Kind.Focus -> synth(0.035) { t, _ ->
            sin(2 * PI * 1_900 * t) * exp(-t / 0.006) * 0.45 + sin(2 * PI * 3_800 * t) * exp(-t / 0.0025) * 0.15
        }
        // Toc : une frappe grave dont la hauteur chute, avec une attaque nette.
        Kind.Select -> {
            var phase = 0.0
            synth(0.14) { t, dt ->
                phase += 2 * PI * (120 + 300 * exp(-t / 0.012)) * dt
                sin(phase) * exp(-t / 0.045) * 0.9 + sin(2 * PI * 2_400 * t) * exp(-t / 0.002) * 0.25
            }
        }
        // Claquement de doigts : attaque très brève vers 2,5 kHz, un peu d'air aigu, puis quelques échos.
        Kind.Open -> snapWithEchoes()
        // Retour : deux notes qui descendent, plus douces que la validation.
        Kind.Back -> synth(0.12) { t, _ ->
            val f = if (t < 0.05) 880.0 else 620.0
            val local = if (t < 0.05) t else t - 0.05
            sin(2 * PI * f * t) * exp(-local / 0.025) * 0.4
        }
    }

    private fun snapWithEchoes(): FloatArray {
        val random = java.util.Random(11)
        val n = (0.03 * RATE).toInt()
        val core = snap(n, random, air = 1.0)
        val soft = snap(n, random, air = 0.3)
        val out = DoubleArray((0.45 * RATE).toInt())
        for (i in 0 until n) out[i] += core[i]
        // Échos de la pièce : de plus en plus loin, de plus en plus faibles.
        for ((ms, gain) in listOf(51 to 0.19, 117 to 0.13, 172 to 0.16, 258 to 0.09)) {
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
        val mid = bandPass(noise, 2_470.0, 2.2)
        val high = bandPass(noise, 6_800.0, 1.4)
        return DoubleArray(n) { i ->
            val t = i.toDouble() / RATE
            val attack = (t / 0.0005).coerceAtMost(1.0)
            (mid[i] * exp(-t / 0.0035) * 2.6 + high[i] * exp(-t / 0.0025) * 3.4 * air + sin(2 * PI * 2_470 * t) * exp(-t / 0.005) * 0.3) * attack
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
