package uz.robutpit.znatoki.voice

import android.content.Context
import android.media.AudioManager
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrackOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uz.robutpit.znatoki.domain.GameGateway

enum class VoiceStatus { OFF, CONNECTING, ON, RECONNECTING, ERROR }

/** Голос команды. Токен — только с сервера; секретов LiveKit в приложении нет. */
interface VoiceManager {
    val status: StateFlow<VoiceStatus>
    val speaking: StateFlow<Set<String>>
    val micOn: StateFlow<Boolean>
    suspend fun connect(roomId: String, noiseSuppression: Boolean)
    fun setMic(enabled: Boolean)
    fun setSpeakerphone(on: Boolean)
    fun disconnect()
}

class LiveKitVoiceManager(private val context: Context, private val gateway: GameGateway) : VoiceManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var room: Room? = null
    private var eventsJob: Job? = null
    override val status = MutableStateFlow(VoiceStatus.OFF)
    override val speaking = MutableStateFlow<Set<String>>(emptySet())
    override val micOn = MutableStateFlow(false)
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override suspend fun connect(roomId: String, noiseSuppression: Boolean) {
        if (room != null) return
        status.value = VoiceStatus.CONNECTING
        try {
            val t = gateway.livekitToken(roomId)
            val r = LiveKit.create(
                context.applicationContext,
                RoomOptions(
                    adaptiveStream = true, dynacast = false,
                    audioTrackCaptureDefaults = LocalAudioTrackOptions(
                        noiseSuppression = noiseSuppression, echoCancellation = true, autoGainControl = true,
                        highPassFilter = true, typingNoiseDetection = true,
                    ),
                ),
            )
            room = r
            eventsJob = scope.launch {
                r.events.collect { e ->
                    when (e) {
                        is RoomEvent.ActiveSpeakersChanged -> speaking.value = e.speakers.mapNotNull { it.identity?.value }.toSet()
                        is RoomEvent.Reconnecting -> status.value = VoiceStatus.RECONNECTING
                        is RoomEvent.Reconnected -> status.value = VoiceStatus.ON
                        is RoomEvent.Disconnected -> status.value = VoiceStatus.OFF
                        else -> Unit
                    }
                }
            }
            r.connect(t.url, t.token)
            status.value = VoiceStatus.ON
            setMic(micOn.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status.value = VoiceStatus.ERROR
            disconnect()
        }
    }

    override fun setMic(enabled: Boolean) {
        micOn.value = enabled
        val r = room ?: return
        scope.launch { runCatching { r.localParticipant.setMicrophoneEnabled(enabled) } }
    }

    @Suppress("DEPRECATION")
    override fun setSpeakerphone(on: Boolean) { audio.isSpeakerphoneOn = on }

    override fun disconnect() {
        eventsJob?.cancel(); eventsJob = null
        room?.let { it.disconnect(); it.release() }
        room = null
        speaking.value = emptySet()
        if (status.value != VoiceStatus.ERROR) status.value = VoiceStatus.OFF
    }
}

/** Голос в мок-режиме: индикаторы «говорит» следуют за репликами ботов, микрофон — локальный флаг. */
class MockVoiceManager : VoiceManager {
    override val status = MutableStateFlow(VoiceStatus.OFF)
    override val speaking = MutableStateFlow<Set<String>>(emptySet())
    override val micOn = MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val until = mutableMapOf<String, Long>()

    override suspend fun connect(roomId: String, noiseSuppression: Boolean) { status.value = VoiceStatus.ON }
    override fun setMic(enabled: Boolean) {
        micOn.value = enabled
        pulse("me", if (enabled) 0 else -1)
    }
    override fun setSpeakerphone(on: Boolean) {}
    override fun disconnect() { status.value = VoiceStatus.OFF; speaking.value = emptySet() }

    /** Вызывается при реплике участника — он «говорит» ~2.5 с. */
    fun pulse(id: String, durationMs: Long = 2500) {
        if (durationMs < 0) { until.remove(id); speaking.value = speaking.value - id; return }
        if (durationMs == 0L) return
        until[id] = System.currentTimeMillis() + durationMs
        speaking.value = speaking.value + id
        scope.launch {
            delay(durationMs + 50)
            val now = System.currentTimeMillis()
            if ((until[id] ?: 0) <= now) { until.remove(id); speaking.value = speaking.value - id }
        }
    }
}
