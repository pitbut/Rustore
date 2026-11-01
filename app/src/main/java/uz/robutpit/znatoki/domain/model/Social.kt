package uz.robutpit.znatoki.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresAt: Long = 0)

@Serializable
data class Profile(
    val id: String,
    val email: String,
    val nickname: String,
    val avatar: Int = 0,
    val emailVerified: Boolean = false,
    val rating: Int = 1500,
    val season: String = "2026-S4",
    val stats: PlayerStats = PlayerStats(),
    val achievements: List<Achievement> = emptyList(),
)

@Serializable
data class PlayerStats(
    val games: Int = 0,
    val wins: Int = 0,
    val correctPercent: Int = 0,
    val asCaptain: Int = 0,
    val favoriteTopics: List<String> = emptyList(),
)

@Serializable
data class Achievement(val id: String, val title: String, val description: String, val unlocked: Boolean)

@Serializable
data class GameHistoryItem(val id: String, val date: Long, val experts: Int, val viewers: Int, val ratingDelta: Int, val asCaptain: Boolean)

@Serializable
data class PlayerShort(val id: String, val nickname: String, val avatar: Int = 0, val rating: Int = 1500, val online: Boolean = false)

@Serializable
enum class FriendState { FRIEND, INCOMING, OUTGOING, BLOCKED }

@Serializable
data class Friend(val player: PlayerShort, val state: FriendState, val requestId: String? = null)

@Serializable
data class Team(
    val id: String,
    val name: String,
    val emblem: Int = 0,
    val ownerId: String,
    val captainId: String,
    val members: List<PlayerShort>,
    val rating: Int = 1500,
)

@Serializable
enum class InviteKind { TEAM, GAME, FRIEND }

@Serializable
data class Invite(
    val id: String,
    val kind: InviteKind,
    val from: PlayerShort,
    val teamId: String? = null,
    val teamName: String? = null,
    val roomId: String? = null,
    val roomCode: String? = null,
    val createdAt: Long,
)

@Serializable
data class LeaderboardEntry(val place: Int, val player: PlayerShort, val rating: Int, val games: Int)

@Serializable
enum class LeaderboardScope { GLOBAL, FRIENDS, TEAM, SEASON }

@Serializable
data class OpenRoom(val roomId: String, val code: String, val title: String, val players: Int, val teamSize: Int, val score: Score, val inGame: Boolean, val spectators: Int)

@Serializable
data class QuestionOfDay(val text: String, val answer: String, val topic: String)

@Serializable
data class ServerConfig(
    val minClientVersion: Int = 1,
    val latestVersion: String = "1.0.0",
    val features: Map<String, Boolean> = emptyMap(),
    val privacyUrl: String = "",
    val termsUrl: String = "",
    val supportEmail: String = "",
)

@Serializable
enum class QuestionStatus { draft, pending, approved, rejected, hidden }

@Serializable
enum class QuestionOrigin { user, ai, imported, admin }

/** Полная модель вопроса (контракт). Ответ приходит только в собственных/админских вопросах. */
@Serializable
data class Question(
    val id: String,
    val type: SectorType = SectorType.NORMAL,
    val lang: String = "ru",
    val topic: String = "",
    val text: String,
    val imageUrl: String? = null,
    val hint: String? = null,
    val answer: String,
    val accepted: List<String> = emptyList(),
    val rejected: List<String> = emptyList(),
    val comment: String = "",
    val source: String = "",
    val author: String = "",
    val license: String = "CC BY-SA 4.0",
    val difficulty: Int = 3,
    val rating: Int = 1500,
    val status: QuestionStatus = QuestionStatus.approved,
    val origin: QuestionOrigin = QuestionOrigin.admin,
    val createdAt: Long = 0,
    val moderationReason: String? = null,
    /** Для блица/суперблица: 3 подвопроса */
    val parts: List<QuestionPart> = emptyList(),
)

@Serializable
data class QuestionPart(val text: String, val answer: String, val accepted: List<String> = emptyList(), val comment: String = "")

@Serializable
data class QuestionPack(
    val id: String,
    val title: String,
    val category: String, // topic | imported | ai | user | mine
    val count: Int,
    val questionIds: List<String> = emptyList(),
    val author: String = "",
)

@Serializable
data class QuestionFeedback(val liked: Boolean? = null, val tooEasy: Boolean = false, val tooHard: Boolean = false, val report: Boolean = false, val reason: String? = null)

@Serializable
data class GenerateResult(val drafts: List<Question>, val quotaLeft: Int, val quotaTotal: Int)

@Serializable
data class ApiError(val code: String, val message: String)
