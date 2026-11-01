package uz.robutpit.znatoki.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Источник локального времени (подменяется в тестах). */
fun interface TimeSource { fun nowMs(): Long }

object SystemTimeSource : TimeSource { override fun nowMs(): Long = System.currentTimeMillis() }

/**
 * Держит смещение между часами сервера и устройства.
 * offset = serverTime - (localSend + rtt/2). Все дедлайны (`endsAt`) — в серверном времени.
 */
@Singleton
class ServerClock @Inject constructor() {
    var timeSource: TimeSource = SystemTimeSource
    private val _offset = MutableStateFlow(0L)
    val offset: StateFlow<Long> = _offset
    var lastRttMs: Long = 0; private set
    var synced: Boolean = false; private set

    /** Применить ответ `GET /time`. */
    fun applySample(localSendMs: Long, localReceiveMs: Long, serverTimeMs: Long) {
        val (off, rtt) = computeOffset(localSendMs, localReceiveMs, serverTimeMs)
        lastRttMs = rtt
        _offset.value = off
        synced = true
    }

    fun setOffset(value: Long) { _offset.value = value; synced = true }

    fun serverNow(): Long = timeSource.nowMs() + _offset.value

    fun toLocal(serverMs: Long): Long = serverMs - _offset.value

    /** Сколько миллисекунд осталось до серверного дедлайна (не меньше 0). */
    fun remainingMs(endsAtServer: Long): Long = (endsAtServer - serverNow()).coerceAtLeast(0)

    companion object {
        fun computeOffset(localSendMs: Long, localReceiveMs: Long, serverTimeMs: Long): Pair<Long, Long> {
            require(localReceiveMs >= localSendMs) { "receive before send" }
            val rtt = localReceiveMs - localSendMs
            val estimatedLocalAtServer = localSendMs + rtt / 2
            return (serverTimeMs - estimatedLocalAtServer) to rtt
        }
    }
}
