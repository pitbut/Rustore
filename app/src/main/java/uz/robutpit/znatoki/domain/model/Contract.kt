package uz.robutpit.znatoki.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Модели ОБЩЕГО КОНТРАКТА. Источник истины — сервер; клиент только отображает. */

@Serializable
enum class Phase {
    LOBBY, SPIN, INTRO, DISCUSS,
    /** Капитан выбирает, кто из команды отвечает (10 с). Не выбрал — отвечает сам. */
    CHOOSE_ANSWERER,
    ANSWER, CHECK, REVEAL, SCORE, GAME_OVER,
    VOTE_EXTRA_MINUTE, AUDIENCE_HELP, PAUSED
}

@Serializable
enum class SectorType {
    @SerialName("normal") NORMAL,
    @SerialName("blitz") BLITZ,
    @SerialName("superblitz") SUPERBLITZ,
    @SerialName("blackbox") BLACKBOX,
    @SerialName("special13") SPECIAL13;

    val subQuestions: Int get() = if (this == BLITZ || this == SUPERBLITZ) 3 else 1
}

@Serializable
enum class Verdict {
    @SerialName("correct") CORRECT,
    @SerialName("incorrect") INCORRECT,
    @SerialName("disputed") DISPUTED
}

@Serializable
enum class Role { @SerialName("player") PLAYER, @SerialName("spectator") SPECTATOR }

@Serializable
enum class BotLevel { @SerialName("novice") NOVICE, @SerialName("medium") MEDIUM, @SerialName("erudite") ERUDITE }

@Serializable
enum class RoomAccess { @SerialName("open") OPEN, @SerialName("code") CODE, @SerialName("team") TEAM }

@Serializable
enum class VoteKind { @SerialName("extra_minute") EXTRA_MINUTE, @SerialName("kick") KICK, @SerialName("pause") PAUSE, @SerialName("audience") AUDIENCE }

@Serializable
data class RoomSettings(
    val access: RoomAccess = RoomAccess.OPEN,
    val teamSize: Int = 6,
    val fillWithBots: Boolean = true,
    val botLevel: BotLevel = BotLevel.MEDIUM,
    val lang: String = "ru",
    val topics: List<String> = emptyList(),
    val difficulty: Int = 3,
    val discussSec: Int = 60,
    val sectors: Int = 13,
    val extraMinuteLimit: Int = 1,
    val audienceHelp: Boolean = true,
    val rated: Boolean = false,
    val allowSpectators: Boolean = true,
    val packId: String? = null,
    val superblitzByCaptain: Boolean = false,
    val special13: Boolean = false,
)

@Serializable
data class Sector(
    val index: Int,
    val type: SectorType,
    val played: Boolean = false,
    val topic: String? = null,
)

@Serializable
data class PlayerInRoom(
    val id: String,
    val nickname: String,
    val avatar: Int = 0,
    val isBot: Boolean = false,
    val isCaptain: Boolean = false,
    val ready: Boolean = false,
    val online: Boolean = true,
    val micMutedByHost: Boolean = false,
    val rating: Int = 1500,
)

@Serializable
data class GameQuestion(
    val id: String,
    val type: SectorType,
    val text: String,
    val imageUrl: String? = null,
    val hint: String? = null,
    val subIndex: Int = 0,
    val subTotal: Int = 1,
    val topic: String? = null,
)

@Serializable
data class SpinResult(
    val sectorIndex: Int,
    val startAngle: Float,
    val totalRotationDeg: Float,
    val durationMs: Long,
    val seed: Long,
    val startedAt: Long = 0L,
)

@Serializable
data class Reveal(
    val answer: String,
    val comment: String = "",
    val source: String = "",
    val verdict: Verdict,
    val scoreDelta: Score = Score(),
    val givenAnswer: String? = null,
    val questionId: String? = null,
    val appealable: Boolean = false,
)

@Serializable
data class Score(val experts: Int = 0, val viewers: Int = 0)

@Serializable
data class ActiveVote(
    val voteId: String,
    val kind: VoteKind,
    val targetId: String? = null,
    val endsAt: Long,
    val yes: Int = 0,
    val no: Int = 0,
    val eligible: Int = 0,
    val myVote: Boolean? = null,
    val initiatorId: String? = null,
)

@Serializable
data class VoteResult(val voteId: String, val kind: VoteKind, val passed: Boolean, val yes: Int, val no: Int, val targetId: String? = null)

@Serializable
data class AudienceSuggestion(val id: String, val text: String, val votes: Int = 0, val fromAi: Boolean = false, val authorId: String? = null)

@Serializable
data class ChatMessage(
    val id: String,
    val scope: String, // team | hall
    val authorId: String,
    val authorName: String,
    val text: String,
    val ts: Long,
)

@Serializable
data class RatingDelta(val playerId: String, val delta: Int, val newRating: Int)

@Serializable
data class GameStats(
    val rounds: Int = 0,
    val correct: Int = 0,
    val timeBankLeft: Int = 0,
    val bestPlayerId: String? = null,
)

@Serializable
data class GameOver(val winner: String, val ratingDeltas: List<RatingDelta> = emptyList(), val stats: GameStats = GameStats())

@Serializable
data class PhaseInfo(val phase: Phase, val endsAt: Long = 0L, val round: Int = 0, val sector: Int = -1)

/** Полный снимок комнаты (`room:state`). */
@Serializable
data class RoomState(
    val roomId: String,
    val code: String,
    val title: String = "",
    val hostId: String,
    val settings: RoomSettings,
    val players: List<PlayerInRoom>,
    val spectators: Int = 0,
    val phase: PhaseInfo = PhaseInfo(Phase.LOBBY),
    val sectors: List<Sector> = emptyList(),
    val score: Score = Score(),
    val question: GameQuestion? = null,
    val reveal: Reveal? = null,
    val spin: SpinResult? = null,
    val vote: ActiveVote? = null,
    val audience: List<AudienceSuggestion> = emptyList(),
    val audienceEndsAt: Long = 0L,
    val timeBankSec: Int = 0,
    val extraMinutesLeft: Int = 1,
    val audienceHelpLeft: Int = 1,
    val appealsLeft: Int = 1,
    val pausedReason: String? = null,
    val superblitzPlayerId: String? = null,
    /** Кто отвечает на текущий вопрос (выбран капитаном / суперблиц / сам капитан). */
    val answererId: String? = null,
    val gameOver: GameOver? = null,
    val seq: Long = 0,
)

/** События сервер → клиент (Socket.IO `/game`). Все несут seq. */
sealed interface ServerEvent {
    val seq: Long
    data class RoomStateEvt(val state: RoomState, override val seq: Long) : ServerEvent
    data class PhaseEvt(val info: PhaseInfo, override val seq: Long) : ServerEvent
    data class SpinResultEvt(val spin: SpinResult, override val seq: Long) : ServerEvent
    data class QuestionEvt(val question: GameQuestion, override val seq: Long) : ServerEvent
    data class RevealEvt(val reveal: Reveal, override val seq: Long) : ServerEvent
    data class ScoreEvt(val score: Score, override val seq: Long) : ServerEvent
    data class VoteStartedEvt(val vote: ActiveVote, override val seq: Long) : ServerEvent
    data class VoteProgressEvt(val voteId: String, val yes: Int, val no: Int, override val seq: Long) : ServerEvent
    data class VoteResultEvt(val result: VoteResult, override val seq: Long) : ServerEvent
    data class AudienceStartedEvt(val endsAt: Long, override val seq: Long) : ServerEvent
    data class AudienceSuggestionsEvt(val list: List<AudienceSuggestion>, override val seq: Long) : ServerEvent
    data class TimeExtendedEvt(val addedSec: Int, val newEndsAt: Long, override val seq: Long) : ServerEvent
    data class AnswererEvt(val playerId: String, override val seq: Long) : ServerEvent
    data class PlayerKickedEvt(val playerId: String, override val seq: Long) : ServerEvent
    data class PlayerStatusEvt(val playerId: String, val online: Boolean, override val seq: Long) : ServerEvent
    data class ChatEvt(val message: ChatMessage, override val seq: Long) : ServerEvent
    data class GameOverEvt(val over: GameOver, override val seq: Long) : ServerEvent
    data class HostSpeechEvt(val text: String, val voiceStyle: String, override val seq: Long) : ServerEvent
    data class ErrorEvt(val code: String, override val seq: Long = -1) : ServerEvent
}

/** Действия клиент → сервер. */
sealed interface ClientAction {
    data class Join(val roomId: String?, val code: String?, val role: Role) : ClientAction
    data object Leave : ClientAction
    data class Ready(val ready: Boolean) : ClientAction
    data object Start : ClientAction
    data class UpdateSettings(val settings: RoomSettings) : ClientAction
    data object Spin : ClientAction
    data object ReadyAnswer : ClientAction
    data class ChooseAnswerer(val playerId: String) : ClientAction
    data class Answer(val text: String, val viaVoice: Boolean) : ClientAction
    data object ExtraMinuteRequest : ClientAction
    data object CallAudience : ClientAction
    data object Appeal : ClientAction
    data class AudienceSuggest(val text: String) : ClientAction
    data class AudienceVote(val suggestionId: String) : ClientAction
    data class VoteCast(val voteId: String, val yes: Boolean) : ClientAction
    data class KickStart(val targetId: String) : ClientAction
    data class Chat(val scope: String, val text: String) : ClientAction
    data class ChatReport(val messageId: String) : ClientAction
    data class Ack(val seq: Long) : ClientAction
    data class SetCaptain(val playerId: String) : ClientAction
    data class AddBot(val level: BotLevel) : ClientAction
}
