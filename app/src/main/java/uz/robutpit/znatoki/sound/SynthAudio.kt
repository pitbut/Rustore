package uz.robutpit.znatoki.sound

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Все звуки и музыка приложения синтезируются процедурно (оригинальные, без сторонних лицензий).
 * Результат пишется в WAV во внутренний кэш при первом запуске.
 */
object SynthAudio {
    const val RATE = 22050

    fun tick(): FloatArray = render(0.035) { t ->
        val env = exp(-t * 140)
        (sin(2 * PI * 2300 * t) * 0.6 + sin(2 * PI * 4100 * t) * 0.25) * env
    }

    fun gong(): FloatArray = render(2.6) { t ->
        val partials = doubleArrayOf(1.0, 2.01, 2.76, 3.9, 5.4)
        val amps = doubleArrayOf(1.0, 0.55, 0.4, 0.22, 0.12)
        var s = 0.0
        for (i in partials.indices) s += amps[i] * sin(2 * PI * 110 * partials[i] * t + i) * exp(-t * (1.2 + i * 0.7))
        s * 0.42 * (1 - exp(-t * 300))
    }

    fun spinUp(): FloatArray {
        val rnd = Random(7)
        var lp = 0.0
        return render(1.1) { t ->
            val p = t / 1.1
            val noise = rnd.nextDouble(-1.0, 1.0)
            val cutoff = 0.05 + p * 0.35
            lp += (noise - lp) * cutoff
            val tone = sin(2 * PI * (180 + 520 * p * p) * t) * 0.25
            (lp * 0.8 + tone) * (p.pow(0.5)) * (1 - p).pow(0.15) * 0.6
        }
    }

    fun warning(): FloatArray = render(0.42) { t ->
        val on = if (t < 0.16 || (t > 0.24 && t < 0.4)) 1.0 else 0.0
        sin(2 * PI * 880 * t) * 0.45 * on * (1 - exp(-t * 400))
    }

    fun correct(): FloatArray = render(0.9) { t ->
        val f = if (t < 0.15) 659.25 else if (t < 0.3) 783.99 else 1046.5
        (sin(2 * PI * f * t) + 0.3 * sin(4 * PI * f * t)) * 0.35 * exp(-(t % 0.3) * 4) * (if (t > 0.3) exp(-(t - 0.3) * 3) else 1.0)
    }

    fun wrong(): FloatArray = render(0.7) { t ->
        val f = 220 - 60 * t
        (sin(2 * PI * f * t) + 0.5 * sin(2 * PI * f * 1.5 * t)) * 0.35 * exp(-t * 3)
    }

    fun click(): FloatArray = render(0.03) { t -> sin(2 * PI * 1500 * t) * exp(-t * 200) * 0.4 }

    /** Спокойная «клубная» петля 16 с: Am – F – C – G, мягкий пэд + колокольчики. */
    fun ambientLoop(): FloatArray {
        val chords = arrayOf(
            doubleArrayOf(220.0, 261.63, 329.63), doubleArrayOf(174.61, 220.0, 261.63),
            doubleArrayOf(196.0, 261.63, 329.63), doubleArrayOf(196.0, 246.94, 293.66),
        )
        val bells = doubleArrayOf(659.25, 523.25, 587.33, 493.88)
        val total = 16.0
        return render(total) { t ->
            val idx = (t / 4).toInt().coerceIn(0, 3)
            val local = t - idx * 4
            val env = (1 - exp(-local * 1.6)) * (1 - exp(-(4 - local) * 2.5))
            var s = 0.0
            for (f in chords[idx]) s += sin(2 * PI * f * t) * 0.18 + sin(2 * PI * f * 2.0 * t + 0.3) * 0.04
            val bellT = local % 2.0
            val bell = sin(2 * PI * bells[idx] * 2 * t) * exp(-bellT * 3.5) * 0.08
            (s * env + bell) * 0.55
        }
    }

    private inline fun render(seconds: Double, f: (Double) -> Double): FloatArray {
        val n = (seconds * RATE).toInt()
        return FloatArray(n) { i -> f(i.toDouble() / RATE).toFloat().coerceIn(-1f, 1f) }
    }

    fun writeWav(samples: FloatArray, file: File) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s * Short.MAX_VALUE).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        FileOutputStream(file).use { it.write(header.array()); it.write(data.array()) }
    }
}
