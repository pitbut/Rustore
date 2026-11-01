package uz.robutpit.znatoki.data.remote

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.HttpException
import uz.robutpit.znatoki.core.AppException
import uz.robutpit.znatoki.domain.ConnectionState
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.domain.LiveKitToken
import uz.robutpit.znatoki.domain.model.*
import java.io.IOException

/**
 * Реальный клиент: REST (Retrofit) + Socket.IO `/game`.
 * Переподключение: socket.io сам переподключается; при reconnect клиент шлёт `room:join` и `game:ack {seq}`,
 * сервер отвечает полным `room:state`.
 */
class RemoteGameGateway(
    private val api: ApiService,
    private val tokens: TokenStore,
    private val baseUrl: String,
    private val okHttp: OkHttpClient,
    private val json: Json,
) : GameGateway {
    override val isMock = false

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string()
        val err = body?.let { runCatching { json.decodeFromString<ErrorEnvelope>(it).error }.getOrNull() }
        val code = err?.code ?: if (e.code() == 401) AppException.UNAUTHORIZED else if (e.code() == 404) AppException.NOT_FOUND else AppException.UNKNOWN
        throw AppException(code, err?.message ?: "HTTP ${e.code()}", e)
    } catch (e: IOException) {
        throw AppException(AppException.NETWORK, "Нет соединения с сервером", e)
    } catch (e: kotlinx.serialization.SerializationException) {
        throw AppException("bad_response", "Неожиданный ответ сервера", e)
    }

    override suspend fun serverTime() = call { api.time().now }
    override suspend fun config() = call { api.config() }
    override suspend fun legalUrl(kind: String) = "$baseUrl/legal/$kind"

    override suspend fun register(email: String, password: String, nickname: String) = call { api.register(RegisterDto(email, password, nickname)) }
    override suspend fun login(email: String, password: String) = call { api.login(LoginDto(email, password)) }
    override suspend fun forgotPassword(email: String) = call { api.forgot(EmailDto(email)) }
    override suspend fun resetPassword(token: String, password: String) = call { api.reset(ResetDto(token, password)) }
    override suspend fun verifyEmail(code: String) = call { api.verifyEmail(CodeDto(code)) }
    override suspend fun me() = call { api.me() }.also { cachedId = it.id }
    override suspend fun updateMe(nickname: String?, avatar: Int?) = call { api.patchMe(PatchMeDto(nickname, avatar)) }
    override suspend fun deleteAccount() = call { api.deleteMe() }
    override suspend fun logout() { disconnectRealtime() }

    override suspend fun player(id: String) = call { api.player(id) }
    override suspend fun searchPlayers(q: String) = call { api.search(q) }
    override suspend fun leaderboard(scope: LeaderboardScope, season: String?) = call { api.leaderboard(scope.name.lowercase(), season) }
    override suspend fun history() = call { api.history() }
    override suspend fun questionOfDay() = try { call { api.questionOfDay() } } catch (e: AppException) { null }

    override suspend fun friends() = call { api.friends() }
    override suspend fun sendFriendRequest(playerId: String) = call { api.friendRequest(PlayerIdDto(playerId)) }
    override suspend fun answerFriendRequest(requestId: String, accept: Boolean) = call { if (accept) api.acceptFriend(requestId) else api.declineFriend(requestId) }
    override suspend fun removeFriend(playerId: String) = call { api.removeFriend(playerId) }
    override suspend fun block(playerId: String) = call { api.block(PlayerIdDto(playerId)) }
    override suspend fun unblock(playerId: String) = call { api.unblock(playerId) }
    override suspend fun report(targetType: String, targetId: String, reason: String) = call { api.report(ReportDto(targetType, targetId, reason)) }

    override suspend fun myTeams() = call { api.myTeams() }
    override suspend fun createTeam(name: String, emblem: Int) = call { api.createTeam(TeamDto(name, emblem)) }
    override suspend fun team(id: String) = call { api.team(id) }
    override suspend fun updateTeam(id: String, name: String?, emblem: Int?, captainId: String?) = call { api.patchTeam(id, PatchTeamDto(name, emblem, captainId)) }
    override suspend fun inviteToTeam(teamId: String, playerId: String) = call { api.inviteToTeam(teamId, PlayerIdDto(playerId)) }
    override suspend fun invites() = call { api.invites() }
    override suspend fun answerInvite(inviteId: String, accept: Boolean) = call { if (accept) api.acceptInvite(inviteId).roomId else { api.declineInvite(inviteId); null } }
    override suspend fun removeTeamMember(teamId: String, playerId: String) = call { api.removeMember(teamId, playerId) }
    override suspend fun leaveTeam(teamId: String) = call { api.leaveTeam(teamId) }
    override suspend fun startTeamRoom(teamId: String) = call { api.startTeamRoom(teamId).roomId }
    override suspend fun inviteToGame(playerId: String, roomId: String) = call { api.inviteToGame(roomId, PlayerIdDto(playerId)) }

    override suspend fun createRoom(settings: RoomSettings) = call { api.createRoom(settings).roomId }
    override suspend fun openRooms() = call { api.openRooms() }
    override suspend fun joinByCode(code: String) = call { api.join(CodeDto(code)).roomId }
    override suspend fun quickPlay() = call { api.queue().roomId }
    override suspend fun cancelQuickPlay() = call { api.leaveQueue() }
    override suspend fun livekitToken(roomId: String) = call { api.livekitToken(roomId) }

    override suspend fun packs() = call { api.packs() }
    override suspend fun createPack(title: String, questionIds: List<String>) = call { api.createPack(PackDto(title, questionIds)) }
    override suspend fun suggestQuestion(q: Question) = call { api.suggest(q) }
    override suspend fun myQuestions() = call { api.myQuestions() }
    override suspend fun feedback(questionId: String, fb: QuestionFeedback) = call { api.feedback(questionId, fb) }
    override suspend fun generate(topic: String, difficulty: Int, count: Int, lang: String) = call { api.generate(GenerateDto(topic, difficulty, count, lang)) }
    override suspend fun registerDevice(provider: String, token: String) = call { api.device(DeviceDto(provider, token)) }

    // ---------------- Socket.IO ----------------
    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connection: StateFlow<ConnectionState> = _connection
    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 512)
    override val events: Flow<ServerEvent> = _events
    private var cachedId: String? = null
    override val myId: String? get() = cachedId

    private var socket: Socket? = null
    private var lastJoin: ClientAction.Join? = null
    @Volatile private var lastSeq: Long = 0

    private fun ensureSocket(): Socket {
        socket?.let { return it }
        val opts = IO.Options().apply {
            transports = arrayOf("websocket")
            reconnection = true
            reconnectionDelay = 1000
            reconnectionDelayMax = 8000
            callFactory = okHttp
            webSocketFactory = okHttp
            auth = mapOf("token" to (tokens.tokens?.accessToken ?: ""))
        }
        val s = IO.socket("$baseUrl/game", opts)
        s.on(Socket.EVENT_CONNECT) {
            _connection.value = ConnectionState.CONNECTED
            lastJoin?.let { j -> emitJoin(s, j); s.emit("game:ack", JSONObject().put("seq", lastSeq)) }
        }
        s.on(Socket.EVENT_DISCONNECT) { _connection.value = ConnectionState.RECONNECTING }
        s.on(Socket.EVENT_CONNECT_ERROR) { _connection.value = ConnectionState.RECONNECTING }
        s.io().on(io.socket.client.Manager.EVENT_RECONNECT_ATTEMPT) {
            // обновляем токен для повторного рукопожатия
            opts.auth = mapOf("token" to (tokens.tokens?.accessToken ?: ""))
        }
        registerHandlers(s)
        socket = s
        return s
    }

    private fun parse(args: Array<Any?>): JsonObject = (args.firstOrNull()?.toString() ?: "{}").let { json.parseToJsonElement(it).jsonObject }
    private fun JsonObject.seq() = this["seq"]?.jsonPrimitive?.longOrNull ?: 0L
    private inline fun <reified T> JsonObject.decode(): T = json.decodeFromJsonElement(this)

    private fun on(s: Socket, name: String, map: (JsonObject, Long) -> ServerEvent) {
        s.on(name) { args ->
            runCatching {
                val o = parse(args)
                val seq = o.seq()
                if (seq > lastSeq) lastSeq = seq
                _events.tryEmit(map(o, seq))
            }
        }
    }

    private fun registerHandlers(s: Socket) {
        on(s, "room:state") { o, seq -> ServerEvent.RoomStateEvt((o["state"]?.jsonObject ?: o).decode(), seq) }
        on(s, "game:phase") { o, seq -> ServerEvent.PhaseEvt(o.decode(), seq) }
        on(s, "game:spin_result") { o, seq -> ServerEvent.SpinResultEvt(o.decode(), seq) }
        on(s, "game:question") { o, seq -> ServerEvent.QuestionEvt(o.decode(), seq) }
        on(s, "game:reveal") { o, seq -> ServerEvent.RevealEvt(o.decode(), seq) }
        on(s, "game:score") { o, seq -> ServerEvent.ScoreEvt(o.decode(), seq) }
        on(s, "vote:started") { o, seq -> ServerEvent.VoteStartedEvt(o.decode(), seq) }
        on(s, "vote:progress") { o, seq -> ServerEvent.VoteProgressEvt(o["voteId"]!!.jsonPrimitive.content, o["yes"]!!.jsonPrimitive.int, o["no"]!!.jsonPrimitive.int, seq) }
        on(s, "vote:result") { o, seq -> ServerEvent.VoteResultEvt(o.decode(), seq) }
        on(s, "audience:started") { o, seq -> ServerEvent.AudienceStartedEvt(o["endsAt"]!!.jsonPrimitive.long, seq) }
        on(s, "audience:suggestions") { o, seq -> ServerEvent.AudienceSuggestionsEvt(json.decodeFromJsonElement(o["suggestions"] ?: JsonArray(emptyList())), seq) }
        on(s, "time:extended") { o, seq -> ServerEvent.TimeExtendedEvt(o["addedSec"]!!.jsonPrimitive.int, o["newEndsAt"]!!.jsonPrimitive.long, seq) }
        on(s, "player:kicked") { o, seq -> ServerEvent.PlayerKickedEvt(o["playerId"]!!.jsonPrimitive.content, seq) }
        on(s, "player:status") { o, seq -> ServerEvent.PlayerStatusEvt(o["id"]!!.jsonPrimitive.content, o["online"]!!.jsonPrimitive.boolean, seq) }
        on(s, "chat:message") { o, seq -> ServerEvent.ChatEvt(o.decode(), seq) }
        on(s, "game:over") { o, seq -> ServerEvent.GameOverEvt(o.decode(), seq) }
        on(s, "host:say") { o, seq -> ServerEvent.HostSpeechEvt(o["text"]!!.jsonPrimitive.content, o["voiceStyle"]?.jsonPrimitive?.content ?: "host", seq) }
        s.on("error") { args -> runCatching { _events.tryEmit(ServerEvent.ErrorEvt(parse(args)["code"]?.jsonPrimitive?.content ?: "error")) } }
    }

    private fun emitJoin(s: Socket, j: ClientAction.Join) {
        val o = JSONObject().put("role", j.role.name.lowercase())
        j.roomId?.let { o.put("roomId", it) }; j.code?.let { o.put("code", it) }
        s.emit("room:join", o)
    }

    override fun send(action: ClientAction) {
        val s = ensureSocket()
        if (!s.connected()) { _connection.value = ConnectionState.CONNECTING; s.connect() }
        fun e(name: String, o: JSONObject = JSONObject()) = s.emit(name, o)
        when (action) {
            is ClientAction.Join -> { lastJoin = action; lastSeq = 0; if (s.connected()) emitJoin(s, action) }
            ClientAction.Leave -> { e("room:leave"); lastJoin = null }
            is ClientAction.Ready -> e("room:ready", JSONObject().put("ready", action.ready))
            ClientAction.Start -> e("room:start")
            is ClientAction.UpdateSettings -> e("room:settings", JSONObject(json.encodeToString(RoomSettings.serializer(), action.settings)))
            ClientAction.Spin -> e("game:spin")
            ClientAction.ReadyAnswer -> e("game:ready_answer")
            is ClientAction.Answer -> e("game:answer", JSONObject().put("text", action.text).put("viaVoice", action.viaVoice))
            ClientAction.ExtraMinuteRequest -> e("game:extra_minute_request")
            ClientAction.CallAudience -> e("game:call_audience")
            ClientAction.Appeal -> e("game:appeal")
            is ClientAction.AudienceSuggest -> e("audience:suggest", JSONObject().put("text", action.text))
            is ClientAction.AudienceVote -> e("audience:vote", JSONObject().put("suggestionId", action.suggestionId))
            is ClientAction.VoteCast -> e("vote:cast", JSONObject().put("voteId", action.voteId).put("yes", action.yes))
            is ClientAction.KickStart -> e("kick:start", JSONObject().put("targetId", action.targetId))
            is ClientAction.Chat -> e("chat:message", JSONObject().put("scope", action.scope).put("text", action.text))
            is ClientAction.ChatReport -> e("chat:report", JSONObject().put("messageId", action.messageId))
            is ClientAction.Ack -> e("game:ack", JSONObject().put("seq", action.seq))
            is ClientAction.SetCaptain -> e("room:captain", JSONObject().put("playerId", action.playerId))
            is ClientAction.AddBot -> e("room:add_bot", JSONObject().put("level", action.level.name.lowercase()))
        }
    }

    override fun disconnectRealtime() {
        socket?.let { it.off(); it.disconnect() }
        socket = null
        lastJoin = null
        _connection.value = ConnectionState.DISCONNECTED
    }
}
