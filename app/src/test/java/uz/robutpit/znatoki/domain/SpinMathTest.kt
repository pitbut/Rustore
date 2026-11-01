package uz.robutpit.znatoki.domain

import org.junit.Assert.*
import org.junit.Test
import uz.robutpit.znatoki.domain.game.CubicBezier
import uz.robutpit.znatoki.domain.game.SpinMath
import kotlin.random.Random

class SpinMathTest {

    @Test fun `final angle lands in server sector for 1000 random seeds`() {
        val rnd = Random(42)
        repeat(1000) {
            val n = if (rnd.nextBoolean()) 13 else 7
            val played = (0 until n).filter { rnd.nextFloat() < 0.4f }.toSet().let { if (it.size == n) it - 0 else it }
            val start = rnd.nextFloat() * 720f - 360f
            val spin = SpinMath.makeSpin(rnd.nextLong(), n, played, start)
            assertFalse("landed on played sector", spin.sectorIndex in played)
            // клиент проигрывает анимацию; к концу угол должен указывать ровно на сектор сервера
            val end = SpinMath.angleAt(spin, spin.durationMs, n)
            assertEquals(spin.sectorIndex, SpinMath.sectorAt(end, n))
            assertEquals(SpinMath.finalAngle(spin), end, 0.0001f)
            // и любой момент после окончания (лаг/перемотка) — тот же результат
            assertEquals(spin.sectorIndex, SpinMath.sectorAt(SpinMath.angleAt(spin, spin.durationMs + 5_000, n), n))
        }
    }

    @Test fun `rotations are between 3 and 6 turns`() {
        repeat(500) { s ->
            val spin = SpinMath.makeSpin(s.toLong(), 13, emptySet(), 0f)
            assertTrue(spin.totalRotationDeg >= 3 * 360f && spin.totalRotationDeg < 7 * 360f)
        }
    }

    @Test fun `animation is deterministic for the same seed`() {
        val a = SpinMath.makeSpin(777, 13, setOf(1, 2), 30f)
        val b = SpinMath.makeSpin(777, 13, setOf(1, 2), 30f)
        assertEquals(a, b)
        for (t in 0..a.durationMs step 50) assertEquals(SpinMath.angleAt(a, t, 13), SpinMath.angleAt(b, t, 13), 0f)
    }

    @Test fun `progress is monotonic and starts at zero`() {
        var prev = -1f
        for (i in 0..1000) {
            val p = SpinMath.progress(i / 1000f)
            assertTrue("not monotonic at $i", p >= prev - 1e-5f)
            prev = p
        }
        assertEquals(0f, SpinMath.progress(0f), 1e-6f)
        assertEquals(1f, SpinMath.progress(1f), 1e-6f)
    }

    @Test fun `wobble never moves the final angle`() {
        assertEquals(0f, SpinMath.wobble(1f, 10f), 0f)
        assertEquals(0f, SpinMath.wobble(0.5f, 10f), 0f)
        assertTrue(kotlin.math.abs(SpinMath.wobble(0.95f, 10f)) <= 10f)
    }

    @Test fun `sector index mapping`() {
        assertEquals(0, SpinMath.sectorAt(0f, 13))
        assertEquals(12, SpinMath.sectorAt(-1f, 13))
        assertEquals(1, SpinMath.sectorAt(360f / 13 + 0.1f, 13))
        assertEquals(3, SpinMath.sectorAt(360f * 3 + 180f, 7))
    }

    @Test fun `skips played sectors clockwise`() {
        val n = 13
        val played = (0 until n).toSet() - 4
        repeat(50) { assertEquals(4, SpinMath.makeSpin(it.toLong(), n, played, 0f).sectorIndex) }
    }

    @Test fun `cubic bezier endpoints`() {
        val b = CubicBezier(0.18f, 0.62f, 0.16f, 1f)
        assertEquals(0f, b.y(0f), 0f); assertEquals(1f, b.y(1f), 0f)
        assertTrue(b.y(0.5f) > 0.5f) // ease-out
    }
}
