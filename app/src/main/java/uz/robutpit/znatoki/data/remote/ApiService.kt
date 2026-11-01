package uz.robutpit.znatoki.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.*
import uz.robutpit.znatoki.domain.LiveKitToken
import uz.robutpit.znatoki.domain.model.*

/** REST `/api/v1` по общему контракту. */
interface ApiService {
    @GET("time") suspend fun time(): TimeDto
    @GET("config") suspend fun config(): ServerConfig

    @POST("auth/register") suspend fun register(@Body b: RegisterDto): AuthTokens
    @POST("auth/login") suspend fun login(@Body b: LoginDto): AuthTokens
    @POST("auth/google") suspend fun loginGoogle(@Body b: GoogleDto): AuthTokens
    @POST("auth/refresh") suspend fun refresh(@Body b: RefreshDto): AuthTokens
    @POST("auth/forgot") suspend fun forgot(@Body b: EmailDto)
    @POST("auth/reset") suspend fun reset(@Body b: ResetDto)
    @POST("auth/verify-email") suspend fun verifyEmail(@Body b: CodeDto)
    @GET("me") suspend fun me(): Profile
    @PATCH("me") suspend fun patchMe(@Body b: PatchMeDto): Profile
    @DELETE("me") suspend fun deleteMe()
    @GET("me/history") suspend fun history(): List<GameHistoryItem>

    @GET("players/{id}") suspend fun player(@Path("id") id: String): PlayerShort
    @GET("players/search") suspend fun search(@Query("q") q: String): List<PlayerShort>
    @GET("leaderboard") suspend fun leaderboard(@Query("scope") scope: String, @Query("season") season: String?): List<LeaderboardEntry>
    @GET("question-of-day") suspend fun questionOfDay(): QuestionOfDay?

    @GET("friends") suspend fun friends(): List<Friend>
    @POST("friends/requests") suspend fun friendRequest(@Body b: PlayerIdDto)
    @POST("friends/requests/{id}/accept") suspend fun acceptFriend(@Path("id") id: String)
    @POST("friends/requests/{id}/decline") suspend fun declineFriend(@Path("id") id: String)
    @DELETE("friends/{playerId}") suspend fun removeFriend(@Path("playerId") id: String)
    @POST("blocks") suspend fun block(@Body b: PlayerIdDto)
    @DELETE("blocks/{playerId}") suspend fun unblock(@Path("playerId") id: String)
    @POST("reports") suspend fun report(@Body b: ReportDto)

    @GET("teams") suspend fun myTeams(): List<Team>
    @POST("teams") suspend fun createTeam(@Body b: TeamDto): Team
    @GET("teams/{id}") suspend fun team(@Path("id") id: String): Team
    @PATCH("teams/{id}") suspend fun patchTeam(@Path("id") id: String, @Body b: PatchTeamDto): Team
    @POST("teams/{id}/invites") suspend fun inviteToTeam(@Path("id") id: String, @Body b: PlayerIdDto)
    @GET("invites") suspend fun invites(): List<Invite>
    @POST("invites/{id}/accept") suspend fun acceptInvite(@Path("id") id: String): InviteAnswerDto
    @POST("invites/{id}/decline") suspend fun declineInvite(@Path("id") id: String)
    @DELETE("teams/{id}/members/{playerId}") suspend fun removeMember(@Path("id") id: String, @Path("playerId") playerId: String)
    @POST("teams/{id}/leave") suspend fun leaveTeam(@Path("id") id: String)
    @POST("teams/{id}/start-room") suspend fun startTeamRoom(@Path("id") id: String): RoomIdDto
    @POST("rooms/{id}/invites") suspend fun inviteToGame(@Path("id") roomId: String, @Body b: PlayerIdDto)

    @POST("rooms") suspend fun createRoom(@Body settings: RoomSettings): RoomIdDto
    @GET("rooms") suspend fun openRooms(@Query("public") pub: Int = 1): List<OpenRoom>
    @POST("rooms/join") suspend fun join(@Body b: CodeDto): RoomIdDto
    @GET("rooms/{id}") suspend fun room(@Path("id") id: String): JsonObject
    @GET("rooms/{id}/livekit-token") suspend fun livekitToken(@Path("id") id: String): LiveKitToken
    @POST("matchmaking/queue") suspend fun queue(): RoomIdDto
    @DELETE("matchmaking/queue") suspend fun leaveQueue()

    @GET("packs") suspend fun packs(): List<QuestionPack>
    @POST("packs") suspend fun createPack(@Body b: PackDto): QuestionPack
    @POST("questions") suspend fun suggest(@Body q: Question): Question
    @GET("questions/mine") suspend fun myQuestions(): List<Question>
    @POST("questions/{id}/feedback") suspend fun feedback(@Path("id") id: String, @Body fb: QuestionFeedback)
    @POST("questions/generate") suspend fun generate(@Body b: GenerateDto): GenerateResult
    @POST("devices") suspend fun device(@Body b: DeviceDto)
}

@Serializable data class TimeDto(val now: Long)
@Serializable data class RegisterDto(val email: String, val password: String, val nickname: String)
@Serializable data class LoginDto(val email: String, val password: String)
@Serializable data class GoogleDto(val idToken: String)
@Serializable data class RefreshDto(val refreshToken: String)
@Serializable data class EmailDto(val email: String)
@Serializable data class ResetDto(val token: String, val password: String)
@Serializable data class CodeDto(val code: String)
@Serializable data class PatchMeDto(val nickname: String? = null, val avatar: Int? = null)
@Serializable data class PlayerIdDto(val playerId: String)
@Serializable data class ReportDto(val targetType: String, val targetId: String, val reason: String)
@Serializable data class TeamDto(val name: String, val emblem: Int)
@Serializable data class PatchTeamDto(val name: String? = null, val emblem: Int? = null, val captainId: String? = null)
@Serializable data class InviteAnswerDto(val roomId: String? = null)
@Serializable data class RoomIdDto(val roomId: String)
@Serializable data class PackDto(val title: String, val questionIds: List<String>)
@Serializable data class GenerateDto(val topic: String, val difficulty: Int, val count: Int, val lang: String)
@Serializable data class DeviceDto(val provider: String, val token: String)
@Serializable data class ErrorEnvelope(val error: ApiError)
