package uz.robutpit.znatoki.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import uz.robutpit.znatoki.domain.model.*

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

/**
 * Единый интерфейс к серверу. Реализуют:
 *  - [uz.robutpit.znatoki.data.mock.FakeGameServer] — встроенный мок (боты, вопросы из assets);
 *  - [uz.robutpit.znatoki.data.remote.RemoteGameGateway] — REST (Retrofit) + Socket.IO + LiveKit-токены.
 * Все методы бросают [uz.robutpit.znatoki.core.AppException].
 */
interface GameGateway {
    val isMock: Boolean

    // --- время/конфиг ---
    suspend fun serverTime(): Long
    suspend fun config(): ServerConfig
    suspend fun legalUrl(kind: String): String

    // --- auth ---
    suspend fun register(email: String, password: String, nickname: String): AuthTokens
    suspend fun login(email: String, password: String): AuthTokens
    suspend fun forgotPassword(email: String)
    suspend fun resetPassword(token: String, password: String)
    suspend fun verifyEmail(code: String)
    suspend fun me(): Profile
    suspend fun updateMe(nickname: String? = null, avatar: Int? = null): Profile
    suspend fun deleteAccount()
    suspend fun logout()

    // --- игроки / рейтинг ---
    suspend fun player(id: String): PlayerShort
    suspend fun searchPlayers(q: String): List<PlayerShort>
    suspend fun leaderboard(scope: LeaderboardScope, season: String? = null): List<LeaderboardEntry>
    suspend fun history(): List<GameHistoryItem>
    suspend fun questionOfDay(): QuestionOfDay?

    // --- друзья ---
    suspend fun friends(): List<Friend>
    suspend fun sendFriendRequest(playerId: String)
    suspend fun answerFriendRequest(requestId: String, accept: Boolean)
    suspend fun removeFriend(playerId: String)
    suspend fun block(playerId: String)
    suspend fun unblock(playerId: String)
    suspend fun report(targetType: String, targetId: String, reason: String)

    // --- команды и приглашения ---
    suspend fun myTeams(): List<Team>
    suspend fun createTeam(name: String, emblem: Int): Team
    suspend fun team(id: String): Team
    suspend fun updateTeam(id: String, name: String? = null, emblem: Int? = null, captainId: String? = null): Team
    suspend fun inviteToTeam(teamId: String, playerId: String)
    suspend fun invites(): List<Invite>
    suspend fun answerInvite(inviteId: String, accept: Boolean): String? // roomId для игровых приглашений
    suspend fun removeTeamMember(teamId: String, playerId: String)
    suspend fun leaveTeam(teamId: String)
    suspend fun startTeamRoom(teamId: String): String // roomId
    suspend fun inviteToGame(playerId: String, roomId: String)

    // --- комнаты ---
    suspend fun createRoom(settings: RoomSettings): String
    suspend fun openRooms(): List<OpenRoom>
    suspend fun joinByCode(code: String): String
    suspend fun quickPlay(): String
    suspend fun cancelQuickPlay()
    suspend fun livekitToken(roomId: String): LiveKitToken

    // --- вопросы ---
    suspend fun packs(): List<QuestionPack>
    suspend fun createPack(title: String, questionIds: List<String>): QuestionPack
    suspend fun suggestQuestion(q: Question): Question
    suspend fun myQuestions(): List<Question>
    suspend fun feedback(questionId: String, fb: QuestionFeedback)
    suspend fun generate(topic: String, difficulty: Int, count: Int, lang: String): GenerateResult
    suspend fun registerDevice(provider: String, token: String)

    // --- realtime (/game) ---
    val connection: StateFlow<ConnectionState>
    val events: Flow<ServerEvent>
    val myId: String?
    fun send(action: ClientAction)
    fun disconnectRealtime()
}

@kotlinx.serialization.Serializable
data class LiveKitToken(val url: String, val token: String)
