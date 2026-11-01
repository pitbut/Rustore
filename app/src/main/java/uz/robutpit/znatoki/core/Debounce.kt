package uz.robutpit.znatoki.core

/** Защита от двойных нажатий: пропускает действие не чаще раза в [windowMs]. */
class ClickGuard(private val windowMs: Long = 600, private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var last = Long.MIN_VALUE / 2
    fun allow(): Boolean {
        val t = now()
        if (t - last < windowMs) return false
        last = t
        return true
    }
}
