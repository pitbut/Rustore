package uz.robutpit.znatoki.data.mock

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import uz.robutpit.znatoki.core.AppException
import uz.robutpit.znatoki.domain.ConnectionState
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.domain.LiveKitToken
import uz.robutpit.znatoki.domain.model.*
import java.util.UUID
import kotlin.random.Random

/**
 * Встроенный мок-сервер: эмулирует REST и Socket.IO из контракта целиком на устройстве.
 * Нужен, чтобы приложение полностью игралось, пока настоящий сервер не готов.
 */
class FakeGameServer(
    private val questionsJson: () -> String,
    private val latencyMs: Long = 250,
    private val timeScale: Float = 1f,
) : GameGateway {

    constructor(context: Context) : this({ context.assets.open("sample_questions.json").bufferedReader().use { it.readText() } })

    override val isMock = true

    /** Мок «сервера» живёт со смещением часов, чтобы проверять логику offset. */
    private val serverOffsetMs = 1_250L
    private fun serverNow() = System.currentTimeMillis() + serverOffsetMs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val rnd = Random(System.nanoTime())

    private val questionBank: List<Question> by lazy { json.decodeFromString(questionsJson()) }

    // --- «база данных» мока ---
    private var profile: Profile? = null
    private var password: String = ""
    private val friends = mutableListOf(
        Friend(MockData.people[0], FriendState.FRIEND),
        Friend(MockData.people[1], FriendState.FRIEND),
        Friend(MockData.people[3], FriendState.FRIEND),
        Friend(MockData.people[6], FriendState.FRIEND),
        Friend(MockData.people[7], FriendState.INCOMING, "fr1"),
    )
    private val teams = mutableListOf(
        Team("t1", "Северный ветер", 2, "p4", "p4", listOf(MockData.people[3], MockData.people[0], MockData.people[1]), 1730),
    )
    private val invites = mutableListOf<Invite>()
    private val history = mutableListOf<GameHistoryItem>()
    private val myQuestions = mutableListOf<Question>()
    private val myPacks = mutableListOf<QuestionPack>()
    private var generateQuota = 20
    private val rooms = mutableMapOf<String, Pair<String, RoomSettings>>() // id -> code, settings
    private var engine: MockGameEngine? = null

    private suspend fun lag() { if (latencyMs > 0) delay(latencyMs / 2 + rnd.nextLong(latencyMs)) }
    private fun requireAuth(): Profile = profile ?: throw AppException(AppException.UNAUTHORIZED, "Требуется вход")

    // ---------------- time / config ----------------
    override suspend fun serverTime(): Long { delay(40); return serverNow() }

    override suspend fun config(): ServerConfig = ServerConfig(
        minClientVersion = 1, latestVersion = "1.0.0",
        features = mapOf("questionOfDay" to true, "aiGenerator" to true, "voice" to false),
        privacyUrl = "https://game.robutpit.com/legal/privacy", termsUrl = "https://game.robutpit.com/legal/terms",
        supportEmail = "gtnz1071980@yandex.com",
    )

    override suspend fun legalUrl(kind: String): String = "file:///android_asset/legal/$kind.html"

    // ---------------- auth ----------------
    private fun tokens() = AuthTokens("mock-access-" + UUID.randomUUID(), "mock-refresh-" + UUID.randomUUID(), serverNow() + 3_600_000)

    override suspend fun register(email: String, password: String, nickname: String): AuthTokens {
        lag()
        if (!email.contains('@')) throw AppException("invalid_email", "Некорректный e-mail")
        if (password.length < 8) throw AppException("weak_password", "Пароль слишком короткий")
        if (nickname.length !in 2..24) throw AppException("invalid_nickname", "Ник от 2 до 24 символов")
        if (MockData.people.any { it.nickname.equals(nickname, true) }) throw AppException("nickname_taken", "Ник занят")
        this.password = password
        profile = Profile("me", email, nickname, avatar = 0, emailVerified = false, rating = 1500, achievements = MockData.achievements.map { it.copy(unlocked = false) })
        seedInvites()
        return tokens()
    }

    override suspend fun login(email: String, password: String): AuthTokens {
        lag()
        if (!email.contains('@') || password.length < 8) throw AppException("invalid_credentials", "Неверный e-mail или пароль")
        if (profile == null) {
            profile = Profile(
                "me", email, email.substringBefore('@').take(16).replaceFirstChar { it.uppercase() }, avatar = 4, emailVerified = true, rating = 1564,
                stats = PlayerStats(23, 14, 58, 7, listOf("История", "Наука", "География")), achievements = MockData.achievements,
            )
            seedHistory(); seedInvites()
        }
        return tokens()
    }

    override suspend fun loginGoogle(idToken: String): AuthTokens {
        lag()
        if (profile == null) {
            profile = Profile("me", "google.user@gmail.com", "Знаток", avatar = 2, emailVerified = true, rating = 1500, achievements = MockData.achievements.map { it.copy(unlocked = false) })
            seedInvites()
        }
        return tokens()
    }

    /** Восстановление сессии по сохранённому токену (мок не хранит состояние между запусками). */
    fun restore(email: String, nickname: String, avatar: Int) {
        if (profile == null) {
            profile = Profile("me", email, nickname, avatar, true, 1564, stats = PlayerStats(23, 14, 58, 7, listOf("История", "Наука")), achievements = MockData.achievements)
            seedHistory(); seedInvites()
        }
    }

    private fun seedInvites() {
        if (invites.isNotEmpty()) return
        invites += Invite("i1", InviteKind.TEAM, MockData.people[3], teamId = "t1", teamName = "Северный ветер", createdAt = serverNow() - 3_600_000)
        invites += Invite("i2", InviteKind.GAME, MockData.people[0], roomId = "r-invite", roomCode = "DLN42", createdAt = serverNow() - 600_000)
        rooms["r-invite"] = "DLN42" to RoomSettings(access = RoomAccess.CODE, teamSize = 4)
    }

    private fun seedHistory() {
        if (history.isNotEmpty()) return
        val t = serverNow()
        repeat(6) { i ->
            val e = rnd.nextInt(2, 7); val v = if (e == 6) rnd.nextInt(0, 6) else 6
            history += GameHistoryItem("h$i", t - (i + 1) * 86_400_000L, e, v, if (e == 6) rnd.nextInt(5, 20) else -rnd.nextInt(3, 15), i % 3 == 0)
        }
    }

    override suspend fun forgotPassword(email: String) { lag(); if (!email.contains('@')) throw AppException("invalid_email") }
    override suspend fun resetPassword(token: String, password: String) { lag(); if (password.length < 8) throw AppException("weak_password"); this.password = password }
    override suspend fun verifyEmail(code: String) {
        lag()
        if (code.trim().length != 6) throw AppException("invalid_code", "Код из 6 цифр")
        profile = requireAuth().copy(emailVerified = true)
    }

    override suspend fun me(): Profile { lag(); return requireAuth() }

    override suspend fun updateMe(nickname: String?, avatar: Int?): Profile {
        lag()
        val p = requireAuth()
        if (nickname != null && nickname.length !in 2..24) throw AppException("invalid_nickname")
        profile = p.copy(nickname = nickname ?: p.nickname, avatar = avatar ?: p.avatar)
        return profile!!
    }

    override suspend fun deleteAccount() { lag(); profile = null; history.clear(); invites.clear(); myQuestions.clear() }
    override suspend fun logout() { profile = null; engine?.stop(); engine = null }

    // ---------------- players ----------------
    override suspend fun player(id: String): PlayerShort { lag(); return MockData.people.firstOrNull { it.id == id } ?: throw AppException(AppException.NOT_FOUND) }

    override suspend fun searchPlayers(q: String): List<PlayerShort> {
        lag()
        if (q.isBlank()) return emptyList()
        return MockData.people.filter { it.nickname.contains(q.trim(), ignoreCase = true) }
    }

    override suspend fun leaderboard(scope: LeaderboardScope, season: String?): List<LeaderboardEntry> {
        lag()
        val me = requireAuth()
        val mine = PlayerShort("me", me.nickname, me.avatar, me.rating, true)
        val base = when (scope) {
            LeaderboardScope.GLOBAL, LeaderboardScope.SEASON -> MockData.people + mine
            LeaderboardScope.FRIENDS -> friends.filter { it.state == FriendState.FRIEND }.map { it.player } + mine
            LeaderboardScope.TEAM -> (teams.firstOrNull()?.members ?: emptyList()) + mine
        }
        val adj = if (scope == LeaderboardScope.SEASON) base.map { it.copy(rating = 1500 + (it.rating - 1500) / 2) } else base
        return adj.sortedByDescending { it.rating }.mapIndexed { i, p -> LeaderboardEntry(i + 1, p, p.rating, 10 + (p.rating % 37)) }
    }

    override suspend fun history(): List<GameHistoryItem> { lag(); return history.sortedByDescending { it.date } }

    override suspend fun questionOfDay(): QuestionOfDay? {
        lag()
        val normal = questionBank.filter { it.type == SectorType.NORMAL }
        val q = normal[(serverNow() / 86_400_000L % normal.size).toInt()]
        return QuestionOfDay(q.text, q.answer, q.topic)
    }

    // ---------------- friends ----------------
    override suspend fun friends(): List<Friend> {
        lag()
        return friends.map { f -> f.copy(player = f.player.copy(online = MockData.people.firstOrNull { it.id == f.player.id }?.online ?: false)) }
    }

    override suspend fun sendFriendRequest(playerId: String) {
        lag()
        if (friends.any { it.player.id == playerId && it.state != FriendState.BLOCKED }) throw AppException("already_requested", "Запрос уже отправлен")
        val p = MockData.people.firstOrNull { it.id == playerId } ?: throw AppException(AppException.NOT_FOUND)
        friends += Friend(p, FriendState.OUTGOING, "fr" + System.nanoTime())
        // мок: через пару секунд друг принимает запрос
        scope.launch {
            delay(4000)
            val i = friends.indexOfFirst { it.player.id == playerId && it.state == FriendState.OUTGOING }
            if (i >= 0) friends[i] = friends[i].copy(state = FriendState.FRIEND)
        }
    }

    override suspend fun answerFriendRequest(requestId: String, accept: Boolean) {
        lag()
        val i = friends.indexOfFirst { it.requestId == requestId }
        if (i < 0) throw AppException(AppException.NOT_FOUND)
        if (accept) friends[i] = friends[i].copy(state = FriendState.FRIEND, requestId = null) else friends.removeAt(i)
    }

    override suspend fun removeFriend(playerId: String) { lag(); friends.removeAll { it.player.id == playerId && it.state != FriendState.BLOCKED } }

    override suspend fun block(playerId: String) {
        lag()
        friends.removeAll { it.player.id == playerId }
        val p = MockData.people.firstOrNull { it.id == playerId } ?: PlayerShort(playerId, "Игрок")
        friends += Friend(p, FriendState.BLOCKED)
    }

    override suspend fun unblock(playerId: String) { lag(); friends.removeAll { it.player.id == playerId && it.state == FriendState.BLOCKED } }
    override suspend fun report(targetType: String, targetId: String, reason: String) { lag() }

    // ---------------- teams ----------------
    private fun meShort(): PlayerShort = requireAuth().let { PlayerShort("me", it.nickname, it.avatar, it.rating, true) }

    override suspend fun myTeams(): List<Team> { lag(); return teams.filter { t -> t.members.any { it.id == "me" } } }

    override suspend fun createTeam(name: String, emblem: Int): Team {
        lag()
        if (name.trim().length !in 2..30) throw AppException("invalid_name", "Название от 2 до 30 символов")
        val t = Team("t" + System.nanoTime(), name.trim(), emblem, "me", "me", listOf(meShort()), requireAuth().rating)
        teams += t
        return t
    }

    override suspend fun team(id: String): Team { lag(); return teams.firstOrNull { it.id == id } ?: throw AppException(AppException.NOT_FOUND) }

    override suspend fun updateTeam(id: String, name: String?, emblem: Int?, captainId: String?): Team {
        lag()
        val i = teams.indexOfFirst { it.id == id }
        if (i < 0) throw AppException(AppException.NOT_FOUND)
        val t = teams[i]
        if (captainId != null && t.members.none { it.id == captainId }) throw AppException("not_member")
        teams[i] = t.copy(name = name ?: t.name, emblem = emblem ?: t.emblem, captainId = captainId ?: t.captainId)
        return teams[i]
    }

    override suspend fun inviteToTeam(teamId: String, playerId: String) {
        lag()
        val i = teams.indexOfFirst { it.id == teamId }
        if (i < 0) throw AppException(AppException.NOT_FOUND)
        if (teams[i].members.size >= 6) throw AppException("team_full", "В команде максимум 6 человек")
        val p = MockData.people.firstOrNull { it.id == playerId } ?: throw AppException(AppException.NOT_FOUND)
        // мок: приглашённый принимает через 3 секунды
        scope.launch {
            delay(3000)
            val j = teams.indexOfFirst { it.id == teamId }
            if (j >= 0 && teams[j].members.none { it.id == playerId }) teams[j] = teams[j].copy(members = teams[j].members + p)
        }
    }

    override suspend fun invites(): List<Invite> { lag(); return invites.sortedByDescending { it.createdAt } }

    override suspend fun answerInvite(inviteId: String, accept: Boolean): String? {
        lag()
        val inv = invites.firstOrNull { it.id == inviteId } ?: throw AppException(AppException.NOT_FOUND)
        invites.remove(inv)
        if (!accept) return null
        return when (inv.kind) {
            InviteKind.TEAM -> {
                val i = teams.indexOfFirst { it.id == inv.teamId }
                if (i >= 0 && teams[i].members.none { it.id == "me" }) teams[i] = teams[i].copy(members = teams[i].members + meShort())
                null
            }
            InviteKind.GAME -> inv.roomId
            InviteKind.FRIEND -> null
        }
    }

    override suspend fun removeTeamMember(teamId: String, playerId: String) {
        lag()
        val i = teams.indexOfFirst { it.id == teamId }
        if (i < 0) throw AppException(AppException.NOT_FOUND)
        if (teams[i].ownerId != "me") throw AppException("forbidden", "Только создатель команды")
        teams[i] = teams[i].copy(members = teams[i].members.filterNot { it.id == playerId })
    }

    override suspend fun leaveTeam(teamId: String) {
        lag()
        val i = teams.indexOfFirst { it.id == teamId }
        if (i < 0) return
        val rest = teams[i].members.filterNot { it.id == "me" }
        if (rest.isEmpty()) teams.removeAt(i) else teams[i] = teams[i].copy(members = rest, captainId = if (teams[i].captainId == "me") rest.first().id else teams[i].captainId, ownerId = if (teams[i].ownerId == "me") rest.first().id else teams[i].ownerId)
    }

    override suspend fun startTeamRoom(teamId: String): String {
        lag()
        val t = team(teamId)
        val settings = RoomSettings(access = RoomAccess.TEAM, teamSize = 6, fillWithBots = true)
        // мок: участники команды «заходят» ботами — их места занимают боты
        return newRoom(settings.copy(teamSize = t.members.size.coerceIn(2, 6)))
    }

    override suspend fun inviteToGame(playerId: String, roomId: String) { lag() }

    // ---------------- rooms ----------------
    private fun newRoom(settings: RoomSettings): String {
        val id = "r" + System.nanoTime()
        rooms[id] = MockData.randomCode(rnd) to settings
        return id
    }

    override suspend fun createRoom(settings: RoomSettings): String {
        lag()
        if (settings.teamSize !in 1..6) throw AppException(AppException.INVALID)
        return newRoom(settings)
    }

    override suspend fun openRooms(): List<OpenRoom> {
        lag()
        val list = mutableListOf<OpenRoom>()
        repeat(3) { i ->
            val id = "live$i"
            if (rooms[id] == null) rooms[id] = MockData.randomCode(Random(i + 7)) to RoomSettings(teamSize = 4 + i % 3, fillWithBots = true, discussSec = 30)
            val (code, s) = rooms.getValue(id)
            list += OpenRoom(id, code, listOf("Вечерний турнир", "Клуб «Эрудит»", "Тренировка новичков")[i], s.teamSize, s.teamSize, Score(i, i + 1), inGame = true, spectators = 4 + i * 9)
        }
        list += OpenRoom("lobby-open", "OPEN1", "Ищем знатоков", 2, 6, Score(), inGame = false, spectators = 0)
        rooms.getOrPut("lobby-open") { "OPEN1" to RoomSettings() }
        return list
    }

    override suspend fun joinByCode(code: String): String {
        lag()
        val c = code.trim().uppercase()
        if (c.length !in 4..6) throw AppException("invalid_code", "Код комнаты — 4–6 символов")
        rooms.entries.firstOrNull { it.value.first == c }?.let { return it.key }
        // мок: любой корректный код открывает новую комнату
        val id = "r" + System.nanoTime()
        rooms[id] = c to RoomSettings(access = RoomAccess.CODE)
        return id
    }

    override suspend fun quickPlay(): String { lag(); delay(900); return newRoom(RoomSettings(teamSize = 4, botLevel = BotLevel.MEDIUM, rated = true)) }
    override suspend fun cancelQuickPlay() {}

    override suspend fun livekitToken(roomId: String): LiveKitToken { lag(); return LiveKitToken("wss://mock.livekit.local", "mock-token") }

    // ---------------- questions ----------------
    override suspend fun packs(): List<QuestionPack> {
        lag()
        val byTopic = questionBank.filter { it.type == SectorType.NORMAL }.groupBy { it.topic }
            .map { (t, qs) -> QuestionPack("topic-$t", t, "topic", qs.size, qs.map { it.id }, "Клуб знатоков") }
        val special = listOf(
            QuestionPack("blitz", "Блицы и суперблицы", "topic", questionBank.count { it.type == SectorType.BLITZ || it.type == SectorType.SUPERBLITZ }, author = "Клуб знатоков"),
            QuestionPack("box", "Чёрные ящики", "topic", questionBank.count { it.type == SectorType.BLACKBOX }, author = "Клуб знатоков"),
            QuestionPack("imported-1", "Архив турнира «Осень-2025»", "imported", 36, author = "Импорт"),
            QuestionPack("ai-1", "ИИ: наука и техника", "ai", 24, author = "Генератор"),
            QuestionPack("user-1", "Вопросы от Бобура", "user", 12, author = "Бобур"),
        )
        return myPacks + special + byTopic
    }

    override suspend fun createPack(title: String, questionIds: List<String>): QuestionPack {
        lag()
        if (title.isBlank()) throw AppException("invalid_title")
        val p = QuestionPack("my" + System.nanoTime(), title.trim(), "mine", questionIds.size, questionIds, requireAuth().nickname)
        myPacks += p
        return p
    }

    override suspend fun suggestQuestion(q: Question): Question {
        lag()
        if (q.text.isBlank() || q.answer.isBlank()) throw AppException(AppException.INVALID, "Нужны текст и ответ")
        val saved = q.copy(id = "u" + System.nanoTime(), status = QuestionStatus.pending, origin = QuestionOrigin.user, author = requireAuth().nickname, createdAt = serverNow())
        myQuestions += saved
        // мок ИИ-модерации
        scope.launch {
            delay(5000)
            val i = myQuestions.indexOfFirst { it.id == saved.id }
            if (i < 0) return@launch
            val norm = uz.robutpit.znatoki.domain.game.AnswerChecker.normalize(saved.text)
            val reason = when {
                saved.text.length < 30 -> "Слишком короткая формулировка: вопросу не хватает подводки."
                norm.contains(uz.robutpit.znatoki.domain.game.AnswerChecker.normalize(saved.answer)) -> "Ответ содержится в тексте вопроса."
                uz.robutpit.znatoki.core.Profanity.containsProfanity(saved.text) -> "Недопустимая лексика."
                saved.source.isBlank() -> null.also { }
                else -> null
            }
            myQuestions[i] = if (reason == null) myQuestions[i].copy(status = QuestionStatus.approved) else myQuestions[i].copy(status = QuestionStatus.rejected, moderationReason = reason)
        }
        return saved
    }

    override suspend fun myQuestions(): List<Question> { lag(); return myQuestions.sortedByDescending { it.createdAt } }

    override suspend fun feedback(questionId: String, fb: QuestionFeedback) { lag() }

    override suspend fun generate(topic: String, difficulty: Int, count: Int, lang: String): GenerateResult {
        lag(); delay(1200)
        if (generateQuota <= 0) throw AppException(AppException.QUOTA, "Дневная квота исчерпана")
        val n = count.coerceIn(1, minOf(10, generateQuota))
        generateQuota -= n
        val pool = questionBank.filter { it.type == SectorType.NORMAL }
            .sortedBy { (if (it.topic.contains(topic, true)) 0 else 1) * 10 + kotlin.math.abs(it.difficulty - difficulty) + rnd.nextFloat() }
        val drafts = pool.take(n).mapIndexed { i, q ->
            q.copy(
                id = "ai" + System.nanoTime() + i, status = QuestionStatus.draft, origin = QuestionOrigin.ai, author = "ИИ-генератор",
                topic = topic.ifBlank { q.topic }, comment = "Черновик ИИ на основе: ${q.comment}", lang = lang,
            )
        }
        return GenerateResult(drafts, generateQuota, 20)
    }

    override suspend fun registerDevice(provider: String, token: String) {}

    // ---------------- realtime ----------------
    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connection: StateFlow<ConnectionState> = _connection
    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 512)
    override val events: Flow<ServerEvent> = _events
    override val myId: String? get() = profile?.id

    override fun send(action: ClientAction) {
        when (action) {
            is ClientAction.Join -> join(action)
            is ClientAction.Leave -> { engine?.stop(); engine = null; _connection.value = ConnectionState.DISCONNECTED }
            else -> engine?.submit(action) ?: _events.tryEmit(ServerEvent.ErrorEvt("not_in_room"))
        }
    }

    private fun join(a: ClientAction.Join) {
        val p = profile ?: run { _events.tryEmit(ServerEvent.ErrorEvt("unauthorized")); return }
        val id = a.roomId ?: rooms.entries.firstOrNull { it.value.first == a.code }?.key
        val room = id?.let { rooms[it] }
        if (id == null || room == null) { _events.tryEmit(ServerEvent.ErrorEvt("room_not_found")); return }
        if (engine?.roomId == id) { engine?.submit(a); return }
        engine?.stop()
        _connection.value = ConnectionState.CONNECTING
        val spectator = a.role == Role.SPECTATOR || id.startsWith("live")
        val eng = MockGameEngine(
            roomId = id, code = room.first, initialSettings = room.second, questions = questionBank,
            now = ::serverNow, parentScope = scope,
            me = PlayerInRoom("me", p.nickname, p.avatar, rating = p.rating),
            myRole = if (spectator) Role.SPECTATOR else Role.PLAYER,
            emitRaw = { _events.tryEmit(it) },
            onFinished = { over, score, captain -> onGameFinished(over, score, captain) },
            timeScale = timeScale,
        )
        engine = eng
        _connection.value = ConnectionState.CONNECTED
        eng.start()
    }

    private fun onGameFinished(over: GameOver, score: Score, captain: Boolean) {
        val p = profile ?: return
        val myDelta = over.ratingDeltas.firstOrNull { it.playerId == "me" }?.delta ?: 0
        history += GameHistoryItem("h" + System.nanoTime(), serverNow(), score.experts, score.viewers, myDelta, captain)
        val s = p.stats
        val games = s.games + 1
        val correct = ((s.correctPercent * s.games / 100.0) + (over.stats.correct.toDouble() / over.stats.rounds.coerceAtLeast(1))).let { (it / games * 100).toInt() }
        profile = p.copy(
            rating = p.rating + myDelta,
            stats = s.copy(games = games, wins = s.wins + if (over.winner == "experts") 1 else 0, correctPercent = correct.coerceIn(0, 100), asCaptain = s.asCaptain + if (captain) 1 else 0),
        )
    }

    override fun disconnectRealtime() { engine?.stop(); engine = null; _connection.value = ConnectionState.DISCONNECTED }
}
