package uz.robutpit.znatoki.domain.game

import uz.robutpit.znatoki.domain.model.SpinResult
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Детерминированная математика волчка.
 * Соглашение: 0° — стрелка вверх, угол растёт по часовой стрелке.
 * Сектор i занимает [i*w, (i+1)*w), w = 360/N.
 * Финальный угол = startAngle + totalRotationDeg — его задаёт сервер, клиент лишь проигрывает.
 */
object SpinMath {

    fun sectorWidth(n: Int): Float = 360f / n

    fun normalize(deg: Float): Float {
        val r = deg % 360f
        return if (r < 0) r + 360f else r
    }

    /** Индекс сектора, на который указывает стрелка с углом [deg]. */
    fun sectorAt(deg: Float, n: Int): Int {
        val w = sectorWidth(n)
        return (floor(normalize(deg) / w).toInt()).coerceIn(0, n - 1)
    }

    fun finalAngle(spin: SpinResult): Float = spin.startAngle + spin.totalRotationDeg

    /** Кривая разгон→замедление: cubic-bezier(0.18, 0.62, 0.16, 1.0), с коротким разгоном через квадратичный вход. */
    private val decel = CubicBezier(0.18f, 0.62f, 0.16f, 1.0f)
    private const val ACCEL_PART = 0.08f

    /** Доля пройденного пути (0..1) в момент p∈[0,1] без «качки». */
    fun progress(p: Float): Float {
        val q = p.coerceIn(0f, 1f)
        // Произведение двух монотонных функций: короткий разгон (ease-out квадрат) × замедление Безье.
        val a = if (q < ACCEL_PART) (q / ACCEL_PART).let { it * (2f - it) } else 1f
        return decel.y(q) * a
    }

    /** Затухающая «качка» у финиша, равна 0 на краях — финальный угол не меняется. */
    fun wobble(p: Float, amplitudeDeg: Float): Float {
        val start = 0.86f
        if (p <= start || p >= 1f) return 0f
        val x = (p - start) / (1f - start)
        val decay = (1f - x) * (1f - x)
        return amplitudeDeg * sin(3.0 * PI * x).toFloat() * decay
    }

    /** Угол стрелки в момент [elapsedMs] от начала анимации. Если время вышло — точный финал. */
    fun angleAt(spin: SpinResult, elapsedMs: Long, sectors: Int): Float {
        if (spin.durationMs <= 0 || elapsedMs >= spin.durationMs) return finalAngle(spin)
        if (elapsedMs <= 0) return spin.startAngle
        val p = elapsedMs.toFloat() / spin.durationMs
        val amp = sectorWidth(sectors) * 0.18f
        return spin.startAngle + spin.totalRotationDeg * progress(p) + wobble(p, amp)
    }

    /**
     * Серверный (и мок) генератор результата: случайный сектор по seed, затем — следующий несыгранный по часовой.
     * Клиент эту функцию для решения НЕ использует, только мок-сервер и тесты.
     */
    fun makeSpin(seed: Long, sectors: Int, played: Set<Int>, startAngle: Float, startedAt: Long = 0L): SpinResult {
        require(played.size < sectors) { "all sectors played" }
        val rnd = Random(seed)
        var target = rnd.nextInt(sectors)
        while (target in played) target = (target + 1) % sectors
        val w = sectorWidth(sectors)
        val jitter = (rnd.nextFloat() - 0.5f) * w * 0.6f
        val targetDeg = target * w + w / 2f + jitter
        val rotations = 3 + rnd.nextInt(4) // 3–6 оборотов
        val delta = normalize(targetDeg - normalize(startAngle))
        val total = rotations * 360f + delta
        val duration = 4200L + rnd.nextInt(1800)
        return SpinResult(target, normalize(startAngle), total, duration, seed, startedAt)
    }

    /** Номер «границы сектора» для подсчёта тиков. */
    fun boundaryIndex(deg: Float, n: Int): Int = floor(deg / sectorWidth(n)).toInt()

    fun approxEquals(a: Float, b: Float, eps: Float = 0.01f) = abs(a - b) < eps
}

/** Кривая Безье (как CSS cubic-bezier), решается методом Ньютона + бисекции. Без аллокаций. */
class CubicBezier(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) {
    private fun bx(t: Float) = ((1 - 3 * x2 + 3 * x1) * t + (3 * x2 - 6 * x1)) * t * t + 3 * x1 * t
    private fun by(t: Float) = ((1 - 3 * y2 + 3 * y1) * t + (3 * y2 - 6 * y1)) * t * t + 3 * y1 * t
    private fun dx(t: Float) = 3 * (1 - 3 * x2 + 3 * x1) * t * t + 2 * (3 * x2 - 6 * x1) * t + 3 * x1

    fun y(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var t = x
        repeat(8) {
            val err = bx(t) - x
            val d = dx(t)
            if (abs(err) < 1e-5f) return by(t)
            if (abs(d) < 1e-6f) return@repeat
            t -= err / d
        }
        var lo = 0f; var hi = 1f; t = x
        repeat(30) {
            val v = bx(t)
            if (abs(v - x) < 1e-5f) return by(t)
            if (v < x) lo = t else hi = t
            t = (lo + hi) / 2f
        }
        return by(t)
    }
}
