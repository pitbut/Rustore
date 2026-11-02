package uz.robutpit.znatoki.feature.game

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.robutpit.znatoki.core.LocaleHelper
import uz.robutpit.znatoki.core.Profanity
import uz.robutpit.znatoki.core.ServerClock
import uz.robutpit.znatoki.data.local.AppSettings
import uz.robutpit.znatoki.data.local.MicMode
import uz.robutpit.znatoki.data.local.SettingsRepository
import uz.robutpit.znatoki.data.repo.GameSession
import uz.robutpit.znatoki.domain.ConnectionState
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.domain.model.*
import uz.robutpit.znatoki.sound.Sfx
import uz.robutpit.znatoki.sound.SoundFx
import uz.robutpit.znatoki.voice.*
import javax.inject.Inject

data class GameUi(
    val room: RoomState? = null,
    val chat: List<ChatMessage> = emptyList(),
    val speaking: Set<String> = emptySet(),
    val micOn: Boolean = false,
    val voiceStatus: VoiceStatus = VoiceStatus.OFF,
    val connection: ConnectionState = ConnectionState.CONNECTING,
    val settings: AppSettings = AppSettings(),
    val myId: String = "me",
    val role: Role = Role.PLAYER,
    val feedbackSent: Set<String> = emptySet(),
) {
    val me: PlayerInRoom? get() = room?.players?.firstOrNull { it.id == myId }
    val captain: PlayerInRoom? get() = room?.players?.firstOrNull { it.isCaptain }
    val isCaptain: Boolean get() = me?.isCaptain == true
    val isHost: Boolean get() = room?.hostId == myId
    val isSpectator: Boolean get() = role == Role.SPECTATOR
    val phase: Phase get() = room?.phase?.phase ?: Phase.LOBBY
    /** Кто отвечает сейчас: в суперблице — выбранный игрок, иначе капитан. */
    val answererId: String? get() = room?.answererId ?: room?.superblitzPlayerId ?: captain?.id
    val iAnswer: Boolean get() = !isSpectator && answererId == myId
    /** «Ответ готов» во время обсуждения жмёт капитан (в суперблице — выбранный игрок). */
    val canFinishDiscussion: Boolean get() = !isSpectator && (room?.superblitzPlayerId ?: captain?.id) == myId
    /** Капитан выбирает отвечающего прямо сейчас. */
    val iChooseAnswerer: Boolean get() = isCaptain && phase == Phase.CHOOSE_ANSWERER
    val mutedByHost: Boolean get() = me?.micMutedByHost == true
}

sealed interface GameEvent {
    data class Error(val code: String) : GameEvent
    data class VoteDone(val result: VoteResult) : GameEvent
    data object KickedOut : GameEvent
    data class PlayerKicked(val name: String) : GameEvent
    data object Info : GameEvent
}

@HiltViewModel
class GameViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: GameSession,
    val clock: ServerClock,
    private val voice: VoiceManager,
    private val host: HostSpeaker,
    val sound: SoundFx,
    private val settingsRepo: SettingsRepository,
    private val gateway: GameGateway,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val roomId: String = savedState["roomId"] ?: ""
    private val role: Role = if (savedState.get<String>("role") == "spectator") Role.SPECTATOR else Role.PLAYER
    private val feedbackSent = MutableStateFlow<Set<String>>(emptySet())

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GameEvent> = _events

    val ui: StateFlow<GameUi> = combine(
        session.state, session.chat, voice.speaking, voice.micOn, voice.status,
    ) { room, chat, speaking, mic, vs -> GameUi(room, chat, speaking, mic, vs) }
        .combine(session.connection) { u, c -> u.copy(connection = c) }
        .combine(settingsRepo.settings) { u, s -> u.copy(settings = s, myId = session.myId, role = role) }
        .combine(feedbackSent) { u, f -> u.copy(feedbackSent = f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameUi(role = role, myId = session.myId))

    private var lastPhase: Phase? = null
    private var lastRevealId: String? = null
    private var warned = false
    private var voiceStarted = false
    private var settings = AppSettings()

    init {
        session.join(roomId, null, role)
        host.setLang(LocaleHelper.current(context))
        viewModelScope.launch { settingsRepo.settings.collect { settings = it } }
        viewModelScope.launch { session.hostLines.collect { host.say(it.text, it.voiceStyle) } }
        viewModelScope.launch { session.errors.collect { _events.emit(GameEvent.Error(it)) } }
        viewModelScope.launch { session.voteResults.collect { _events.emit(GameEvent.VoteDone(it)) } }
        viewModelScope.launch {
            session.kicked.collect { id ->
                if (id == session.myId) _events.emit(GameEvent.KickedOut)
            }
        }
        // мок-голос: «говорит» тот, кто только что написал в чат команды
        viewModelScope.launch {
            var seen = 0
            session.chat.collect { list ->
                if (list.size < seen) seen = 0
                list.drop(seen).forEach { m -> if (m.scope == "team") (voice as? MockVoiceManager)?.pulse(m.authorId) }
                seen = list.size
            }
        }
        viewModelScope.launch { session.state.collect { onState(it) } }
    }

    private fun onState(s: RoomState?) {
        s ?: return
        val phase = s.phase.phase
        if (phase != lastPhase) {
            host.discussionActive = phase == Phase.DISCUSS
            val me = s.players.firstOrNull { it.id == session.myId }
            when (phase) {
                Phase.DISCUSS -> warned = false
                // капитану — сигнал «выбирайте отвечающего», выбранному игроку — «отвечаете вы»
                Phase.CHOOSE_ANSWERER -> if (me?.isCaptain == true && role == Role.PLAYER) { sound.play(Sfx.WARNING); sound.vibrate(80, 180) }
                Phase.ANSWER -> if (s.answererId == session.myId && role == Role.PLAYER) { sound.play(Sfx.WARNING); sound.vibrate(120, 200) }
                Phase.GAME_OVER -> sound.play(Sfx.GONG)
                else -> Unit
            }
            if (phase != Phase.LOBBY) sound.stopMusic()
            lastPhase = phase
        }
        s.reveal?.let { r ->
            val id = "${r.questionId}:${s.question?.subIndex}:${r.verdict}"
            if (id != lastRevealId && phase == Phase.REVEAL) {
                lastRevealId = id
                sound.play(if (r.verdict == Verdict.INCORRECT) Sfx.WRONG else Sfx.CORRECT)
                if (r.verdict != Verdict.INCORRECT) sound.vibrate(40, 120)
            }
        }
        // суперблиц: микрофон принудительно выключен ведущим
        val me = s.players.firstOrNull { it.id == session.myId }
        if (me?.micMutedByHost == true && voice.micOn.value) voice.setMic(false)
    }

    /** Секундный тик таймера обсуждения: сигнал за 10 секунд до конца. */
    fun onDiscussSecond(sec: Int) {
        if (ui.value.phase == Phase.DISCUSS && sec == 10 && !warned) {
            warned = true
            sound.play(Sfx.WARNING); sound.vibrate(60, 160)
        }
    }

    /** Пользователь выдал RECORD_AUDIO — подключаем голос и foreground-service. */
    fun onMicPermission(granted: Boolean) {
        if (!granted || role == Role.SPECTATOR || voiceStarted) return
        voiceStarted = true
        viewModelScope.launch {
            // сервер выдаёт голосовой токен только тем, кто уже в комнате — ждём снимок комнаты
            kotlinx.coroutines.withTimeoutOrNull(20_000) { session.state.first { it?.roomId == roomId } }
            // несколько попыток: вход в комнату мог ещё не дойти до сервера, или сеть моргнула
            for (attempt in 0 until 4) {
                voice.connect(roomId, settings.noiseSuppression)
                if (voice.status.value == VoiceStatus.ON) break
                kotlinx.coroutines.delay(1500L * (attempt + 1))
            }
            // голос недоступен — играем через чат, без foreground-service
            if (voice.status.value != VoiceStatus.ON) {
                voiceStarted = false
                _events.emit(GameEvent.Error("voice_unavailable")) // честно говорим, что голоса нет — играем через чат
                return@launch
            }
            GameAudioService.start(context)
            if (settings.micMode == MicMode.OPEN && ui.value.mutedByHost.not()) voice.setMic(true)
        }
    }

    fun toggleMic() {
        if (ui.value.mutedByHost) { _events.tryEmit(GameEvent.Error("mic_muted_by_host")); return }
        if (voice.status.value != VoiceStatus.ON && voice.status.value != VoiceStatus.RECONNECTING) {
            // повторная попытка подключить голос по нажатию на микрофон
            if (!voiceStarted && role == Role.PLAYER) { onMicPermission(true); return }
            _events.tryEmit(GameEvent.Error("voice_unavailable")); return
        }
        voice.setMic(!voice.micOn.value)
    }

    fun pushToTalk(pressed: Boolean) { if (!ui.value.mutedByHost) voice.setMic(pressed) }

    // ---- действия (сервер авторитарен; клиент только отправляет намерения) ----
    fun spin() { sound.play(Sfx.SPIN_UP); session.send(ClientAction.Spin) }
    fun readyAnswer() = session.send(ClientAction.ReadyAnswer)
    fun chooseAnswerer(playerId: String) = session.send(ClientAction.ChooseAnswerer(playerId))
    fun answer(text: String, viaVoice: Boolean) { if (text.isNotBlank()) session.send(ClientAction.Answer(text.trim(), viaVoice)) }
    fun extraMinute() = session.send(ClientAction.ExtraMinuteRequest)
    fun callAudience() = session.send(ClientAction.CallAudience)
    fun appeal() = session.send(ClientAction.Appeal)
    fun suggest(text: String) { if (text.isNotBlank()) session.send(ClientAction.AudienceSuggest(Profanity.mask(text.trim()))) }
    fun voteSuggestion(id: String) = session.send(ClientAction.AudienceVote(id))
    fun castVote(voteId: String, yes: Boolean) = session.send(ClientAction.VoteCast(voteId, yes))
    fun kick(targetId: String) = session.send(ClientAction.KickStart(targetId))
    fun chat(scope: String, text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) session.send(ClientAction.Chat(scope, Profanity.mask(t.take(300))))
    }
    fun reportMessage(m: ChatMessage) {
        session.send(ClientAction.ChatReport(m.id))
        viewModelScope.launch { runCatching { gateway.report("message", m.id, "chat") } }
    }
    fun reportPlayer(id: String, reason: String) = viewModelScope.launch { runCatching { gateway.report("player", id, reason) } }
    fun blockPlayer(id: String) {
        session.blockAuthor(id)
        viewModelScope.launch { runCatching { gateway.block(id) } }
    }
    fun setReady(r: Boolean) = session.send(ClientAction.Ready(r))
    fun start() = session.send(ClientAction.Start)
    fun setCaptain(id: String) = session.send(ClientAction.SetCaptain(id))
    fun addBot(level: BotLevel) = session.send(ClientAction.AddBot(level))
    fun updateSettings(s: RoomSettings) = session.send(ClientAction.UpdateSettings(s))
    fun inviteFriend(playerId: String) = viewModelScope.launch { runCatching { gateway.inviteToGame(playerId, roomId) } }
    suspend fun friends(): List<Friend> = runCatching { gateway.friends().filter { it.state == FriendState.FRIEND } }.getOrDefault(emptyList())

    fun feedback(questionId: String, fb: QuestionFeedback) {
        feedbackSent.update { it + questionId }
        viewModelScope.launch { runCatching { gateway.feedback(questionId, fb) } }
    }

    fun leave() {
        session.leave()
        stopVoice()
    }

    private fun stopVoice() {
        voice.disconnect()
        host.stop()
        GameAudioService.stop(context)
        voiceStarted = false
    }

    override fun onCleared() {
        // ViewModel уничтожается только при уходе с экрана (не при повороте) — выходим из комнаты
        if (session.state.value?.roomId == roomId) session.leave()
        stopVoice()
        super.onCleared()
    }
}
