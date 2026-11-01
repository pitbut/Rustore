package uz.robutpit.znatoki.data.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.domain.game.GameReducer
import uz.robutpit.znatoki.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

data class HostLine(val text: String, val voiceStyle: String, val seq: Long)

/**
 * Живая игровая сессия: постоянно слушает события сервера, применяет их через [GameReducer]
 * и раздаёт UI готовое состояние. Живёт в app-scope, поэтому переживает поворот экрана и пересоздание ViewModel.
 */
@Singleton
class GameSession @Inject constructor(private val gateway: GameGateway) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<RoomState?>(null)
    val state: StateFlow<RoomState?> = _state

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chat: StateFlow<List<ChatMessage>> = _chat

    private val _host = MutableSharedFlow<HostLine>(extraBufferCapacity = 64)
    val hostLines: SharedFlow<HostLine> = _host

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors: SharedFlow<String> = _errors

    private val _voteResults = MutableSharedFlow<VoteResult>(extraBufferCapacity = 8)
    val voteResults: SharedFlow<VoteResult> = _voteResults

    private val _kicked = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val kicked: SharedFlow<String> = _kicked

    var role: Role = Role.PLAYER; private set
    val connection get() = gateway.connection
    val myId: String get() = gateway.myId ?: "me"

    private val blockedAuthors = mutableSetOf<String>()

    init {
        scope.launch {
            gateway.events.collect { e -> handle(e) }
        }
    }

    private fun handle(e: ServerEvent) {
        when (e) {
            is ServerEvent.ErrorEvt -> { _errors.tryEmit(e.code); return }
            is ServerEvent.HostSpeechEvt -> { _host.tryEmit(HostLine(e.text, e.voiceStyle, e.seq)); return }
            is ServerEvent.ChatEvt -> if (e.message.authorId !in blockedAuthors) _chat.update { (it + e.message).takeLast(200) }
            is ServerEvent.VoteResultEvt -> _voteResults.tryEmit(e.result)
            is ServerEvent.PlayerKickedEvt -> _kicked.tryEmit(e.playerId)
            else -> Unit
        }
        val r = GameReducer.reduce(_state.value, e)
        if (r.applied) _state.value = r.state
        if (r.needResync) gateway.send(ClientAction.Ack(_state.value?.seq ?: 0))
    }

    fun join(roomId: String?, code: String? = null, asRole: Role = Role.PLAYER) {
        val cur = _state.value
        if (cur != null && cur.roomId == roomId && role == asRole) return // уже в комнате (поворот экрана)
        role = asRole
        _state.value = null
        _chat.value = emptyList()
        gateway.send(ClientAction.Join(roomId, code, asRole))
    }

    fun send(a: ClientAction) = gateway.send(a)

    fun blockAuthor(id: String) { blockedAuthors += id; _chat.update { l -> l.filterNot { it.authorId == id } } }

    fun leave() {
        gateway.send(ClientAction.Leave)
        _state.value = null
        _chat.value = emptyList()
    }

    /** Переподключение (после смены сети/возврата из фона): просим полный снимок. */
    fun resync() { _state.value?.let { gateway.send(ClientAction.Join(it.roomId, null, role)) } }
}
