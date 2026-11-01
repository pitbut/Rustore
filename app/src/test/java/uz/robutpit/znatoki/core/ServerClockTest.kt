package uz.robutpit.znatoki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerClockTest {
    @Test fun `offset compensates half of round trip`() {
        // локально отправили в 1000, получили в 1200 (rtt 200), сервер сказал 5100 → сервер в момент 1100 = 5100
        val (off, rtt) = ServerClock.computeOffset(1000, 1200, 5100)
        assertEquals(200, rtt)
        assertEquals(4000, off)
    }

    @Test fun `negative offset when device clock is ahead`() {
        val (off, _) = ServerClock.computeOffset(10_000, 10_100, 7_050)
        assertEquals(-3000, off)
    }

    @Test fun `remaining time is computed from server deadline`() {
        val clock = ServerClock()
        var local = 50_000L
        clock.timeSource = TimeSource { local }
        clock.applySample(49_900, 50_100, 60_000) // offset = 60000 - 50000 = 10000
        assertEquals(60_000, clock.serverNow())
        assertEquals(30_000, clock.remainingMs(90_000))
        local += 31_000
        assertEquals(0, clock.remainingMs(90_000))
        assertEquals(90_000 - 10_000, clock.toLocal(90_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects receive before send`() { ServerClock.computeOffset(10, 5, 0) }
}
