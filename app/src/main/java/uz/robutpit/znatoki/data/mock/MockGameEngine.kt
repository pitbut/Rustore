package uz.robutpit.znatoki.data.mock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uz.robutpit.znatoki.domain.game.AnswerChecker
import uz.robutpit.znatoki.domain.game.SpinMath
import uz.robutpit.znatoki.domain.model.*
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Серверная логика одной комнаты для мок-режима. Эмулирует авторитарный сервер:
 * фазы, таймеры (в серверном времени), волчок, проверку ответов, голосования, помощь зала, ботов.
 * Все входящие действия обрабатываются последовательно через [inbox], поэтому гонок нет.
 */
internal class MockGameEngine(
    val roomId: String,
    val code: String,
    initialSettings: RoomSettings,
    private val questions: List<Question>,
    private val now: () -> Long,
    parentScope: CoroutineScope,
    private val me: PlayerInRoom,
    private val myRole: Role,
    private val emitRaw: (ServerEvent) -> Unit,
    private val onFinished: (GameOver, Score, Boolean) -> Unit,
    private val rnd: Random = Random(System.nanoTime()),
    private val autoStart: Boolean = myRole == Role.SPECTATOR,
    private val timeScale: Float = 1f,
) {
    private sealed interface Msg {
        data class Client(val a: ClientAction) : Msg
        data object BotSpin : Msg
        data class BotReadyAnswer(val playerId: String) : Msg
        data class BotAnswer(val playerId: String, val text: String) : Msg
        data class BotVote(val voteId: String, val playerId: String, val yes: Boolean) : Msg
        data class VoteTimeout(val voteId: String) : Msg
        data object AudienceTick : Msg
        data object BotCallAudience : Msg
    }

    private val scope = CoroutineScope(parentScope.coroutineContext + Job(parentScope.coroutineContext[Job]))
    private val inbox = Channel<Msg>(Channel.UNLIMITED)
    private var seq = 0L
    private val emitLock = Any()

    private var st: RoomState
    private var lastAngle = 0f
    private val usedQuestions = mutableSetOf<String>()

    // голосования
    private val votes = mutableMapOf<String, Boolean>()
    private var voteEligible = setOf<String>()
    private val kickLastInit = mutableMapOf<String, Long>()
    private val kickFails = mutableMapOf<String, Int>()
    private val kickBlocked = mutableSetOf<String>()

    // текущий раунд
    private var currentAnswerer: String = MockData.ME
    private var myLastTeamMessage: String? = null
    private var botChatJob: Job? = null
    private var hallChatJob: Job? = null
    private var audienceJob: Job? = null
    private var currentQuestion: Question? = null
    private var currentPart: QuestionPart? = null
    private var roundsPlayed = 0
    private var correctCount = 0
    private val hostAudienceSuggestions = mutableListOf<AudienceSuggestion>()
    private val myAudienceVotes = mutableSetOf<String>()

    init {
        val players = mutableListOf<PlayerInRoom>()
        if (myRole == Role.PLAYER) players += me.copy(isCaptain = true, ready = false)
        val needBots = if (myRole == Role.SPECTATOR) initialSettings.teamSize.coerceAtLeast(3)
        else if (initialSettings.fillWithBots) initialSettings.teamSize - players.size else 0
        val pool = MockData.bots.shuffled(rnd)
        repeat(needBots.coerceAtLeast(0)) { i ->
            val b = pool[i % pool.size]
            players += PlayerInRoom(
                id = "bot$i", nickname = b.name, avatar = b.avatar, isBot = true,
                isCaptain = players.isEmpty(), ready = true, rating = 1400 + rnd.nextInt(400),
            )
        }
        st = RoomState(
            roomId = roomId, code = code,
            title = if (myRole == Role.SPECTATOR) "Открытая игра $code" else "Комната $code",
            hostId = if (myRole == Role.PLAYER) MockData.ME else players.first().id,
            settings = initialSettings,
            players = players,
            spectators = if (myRole == Role.SPECTATOR) 3 + rnd.nextInt(25) else rnd.nextInt(0, 3),
            sectors = MockData.sectorLayout(initialSettings.sectors, initialSettings.special13),
            extraMinutesLeft = initialSettings.extraMinuteLimit,
            audienceHelpLeft = if (initialSettings.audienceHelp) 1 else 0,
        )
    }

    fun start() {
        scope.launch { runRoom() }
        if (myRole == Role.SPECTATOR) startHallChat()
    }

    fun submit(a: ClientAction) { inbox.trySend(Msg.Client(a)) }

    fun stop() { scope.cancel() }

    // ---------------- emit helpers ----------------
    private fun emit(make: (Long) -> ServerEvent) {
        synchronized(emitLock) { seq += 1; emitRaw(make(seq)) }
    }

    private fun snapshot() = emit { ServerEvent.RoomStateEvt(st.copy(seq = it), it) }
    private fun error(code: String) = emitRaw(ServerEvent.ErrorEvt(code))
    private fun say(text: String, voice: String = "host") = emit { ServerEvent.HostSpeechEvt(text, voice, it) }
    private fun chat(scopeName: String, authorId: String, name: String, text: String) {
        if (scopeName == "team" && myRole == Role.SPECTATOR) return // зрители не видят чат команды
        val msg = ChatMessage("m${System.nanoTime()}", scopeName, authorId, name, text, now())
        emit { ServerEvent.ChatEvt(msg, it) }
    }

    private fun ms(v: Long) = (v * timeScale).toLong()
    private fun captain() = st.players.firstOrNull { it.isCaptain } ?: st.players.first()
    private fun isBot(id: String) = st.players.firstOrNull { it.id == id }?.isBot ?: true
    private fun bots() = st.players.filter { it.isBot }

    // ---------------- main loop ----------------
    private suspend fun runRoom() {
        snapshot()
        if (autoStart) {
            launchLater(1500) { inbox.trySend(Msg.Client(ClientAction.Start)) }
        }
        lobby()
        say("Добро пожаловать в Клуб знатоков! Играем до шести очков. Знатоки, удачи!")
        game()
    }

    private fun launchLater(delayMs: Long, block: suspend () -> Unit): Job = scope.launch { delay(ms(delayMs)); block() }

    private suspend fun lobby() {
        while (scope.isActive) {
            val m = inbox.receive()
            if (m is Msg.Client) {
                when (val a = m.a) {
                    is ClientAction.Start -> {
                        if (myRole == Role.PLAYER && st.hostId != MockData.ME) { error("not_host"); continue }
                        if (st.players.isEmpty()) { error("no_players"); continue }
                        return
                    }
                    is ClientAction.Ready -> { st = st.copy(players = st.players.map { if (it.id == MockData.ME) it.copy(ready = a.ready) else it }); snapshot() }
                    is ClientAction.UpdateSettings -> {
                        st = st.copy(settings = a.settings, sectors = MockData.sectorLayout(a.settings.sectors, a.settings.special13),
                            extraMinutesLeft = a.settings.extraMinuteLimit, audienceHelpLeft = if (a.settings.audienceHelp) 1 else 0)
                        snapshot()
                    }
                    is ClientAction.SetCaptain -> { st = st.copy(players = st.players.map { it.copy(isCaptain = it.id == a.playerId) }); snapshot() }
                    is ClientAction.AddBot -> addBot(a.level)
                    else -> handleCommon(m)
                }
            } else handleCommon(m)
        }
    }

    private fun addBot(level: BotLevel) {
        if (st.players.size >= st.settings.teamSize) { error("room_full"); return }
        val used = st.players.map { it.nickname }.toSet()
        val b = MockData.bots.firstOrNull { it.level == level && it.name !in used } ?: MockData.bots.first { it.name !in used }
        st = st.copy(players = st.players + PlayerInRoom("bot${System.nanoTime() % 100000}", b.name, b.avatar, isBot = true, ready = true, rating = 1450))
        snapshot()
    }

    /** Фаза с серверным дедлайном. [onMsg] возвращает true — фаза завершается досрочно. */
    private suspend fun phase(p: Phase, durationMs: Long, sector: Int = -1, onMsg: suspend (Msg) -> Boolean = { false }) {
        val endsAt = now() + ms(durationMs)
        st = st.copy(phase = PhaseInfo(p, endsAt, roundsPlayed + 1, sector))
        emit { ServerEvent.PhaseEvt(st.phase, it) }
        waitUntilPhaseEnd(onMsg)
    }

    private suspend fun waitUntilPhaseEnd(onMsg: suspend (Msg) -> Boolean) {
        while (scope.isActive) {
            val rem = st.phase.endsAt - now()
            if (rem <= 0) return
            val m = withTimeoutOrNull(rem) { inbox.receive() } ?: continue
            if (onMsg(m)) return
            handleCommon(m)
        }
    }

    /** Пауза без смены фазы — обрабатываем общие сообщения (чат, голосования). */
    private suspend fun idle(durationMs: Long) {
        val until = now() + durationMs
        while (scope.isActive) {
            val rem = until - now()
            if (rem <= 0) return
            val m = withTimeoutOrNull(rem) { inbox.receive() } ?: continue
            handleCommon(m)
        }
    }

    private suspend fun game() {
        st = st.copy(score = Score(), sectors = st.sectors.map { it.copy(played = false) })
        snapshot()
        while (scope.isActive) {
            if (st.sectors.all { it.played }) {
                st = st.copy(sectors = st.sectors.map { it.copy(played = false) }); snapshot()
                say("Все сектора сыграны — открываем стол заново!")
            }
            // ---- SPIN ----
            val cap = captain()
            if (cap.isBot) launchLater(1500L + rnd.nextLong(1500)) { inbox.trySend(Msg.BotSpin) }
            phase(Phase.SPIN, 30_000) { m ->
                (m is Msg.Client && m.a is ClientAction.Spin && cap.id == MockData.ME) || m is Msg.BotSpin
            }
            val played = st.sectors.filter { it.played }.map { it.index }.toSet()
            val spin = SpinMath.makeSpin(rnd.nextLong(), st.sectors.size, played, lastAngle, now())
            val spinScaled = spin.copy(durationMs = ms(spin.durationMs))
            lastAngle = SpinMath.normalize(SpinMath.finalAngle(spin))
            st = st.copy(spin = spinScaled)
            emit { ServerEvent.SpinResultEvt(spinScaled, it) }
            idle(spinScaled.durationMs + ms(700))

            val sector = st.sectors[spin.sectorIndex]
            val q = pickQuestion(sector.type)
            currentQuestion = q
            st = st.copy(sectors = st.sectors.map { if (it.index == sector.index) it.copy(topic = q.topic) else it })

            // ---- INTRO ----
            val intro = when (sector.type) {
                SectorType.BLITZ -> "Сектор ${sector.index + 1}. Блиц! Три вопроса, по двадцать секунд на каждый."
                SectorType.SUPERBLITZ -> "Сектор ${sector.index + 1}. Суперблиц! Отвечает один знаток, остальные молчат."
                SectorType.BLACKBOX -> "Внимание! Чёрный ящик!"
                SectorType.SPECIAL13 -> "Сектор тринадцать! Вопрос от зрителей."
                SectorType.NORMAL -> "Сектор ${sector.index + 1}. Тема: ${q.topic}."
            }
            say(intro)
            if (sector.type == SectorType.SUPERBLITZ) chooseSuperblitzPlayer()
            phase(Phase.INTRO, 3500, sector.index)

            val parts = if (q.parts.isNotEmpty() && sector.type.subQuestions > 1) q.parts.take(3)
            else listOf(QuestionPart(q.text, q.answer, q.accepted, q.comment))
            var over = false
            for ((k, part) in parts.withIndex()) {
                currentPart = part
                over = playQuestion(q, part, k, parts.size, sector)
                if (over) break
            }
            // снять ограничения суперблица
            if (st.players.any { it.micMutedByHost }) {
                st = st.copy(players = st.players.map { it.copy(micMutedByHost = false) }, superblitzPlayerId = null)
                snapshot()
            }
            if (over) { finish(); return }
        }
    }

    private fun chooseSuperblitzPlayer() {
        val team = st.players
        val chosen = if (st.settings.superblitzByCaptain && captain().id == MockData.ME) MockData.ME else team[rnd.nextInt(team.size)].id
        st = st.copy(
            superblitzPlayerId = chosen,
            players = team.map { it.copy(micMutedByHost = it.id != chosen) },
        )
        snapshot()
        val name = team.first { it.id == chosen }.nickname
        say("На суперблиц выходит $name.")
    }

    private fun pickQuestion(type: SectorType): Question {
        val wanted = when (type) {
            SectorType.SPECIAL13 -> SectorType.NORMAL
            else -> type
        }
        val s = st.settings
        val pool = questions.filter { it.type == wanted && it.id !in usedQuestions && (s.topics.isEmpty() || it.topic in s.topics || wanted != SectorType.NORMAL) }
            .ifEmpty { questions.filter { it.type == wanted && it.id !in usedQuestions } }
            .ifEmpty { usedQuestions.removeAll(questions.filter { it.type == wanted }.map { it.id }.toSet()); questions.filter { it.type == wanted } }
        // ближе к выбранной сложности
        val sorted = pool.sortedBy { kotlin.math.abs(it.difficulty - s.difficulty) + rnd.nextFloat() * 2 }
        val q = sorted.first()
        usedQuestions += q.id
        return q
    }

    /** @return true — игра окончена. */
    private suspend fun playQuestion(q: Question, part: QuestionPart, k: Int, total: Int, sector: Sector): Boolean {
        val multi = total > 1
        st = st.copy(question = GameQuestion(q.id, sector.type, part.text, q.imageUrl, q.hint, k, total, q.topic), reveal = null, audience = emptyList(), audienceEndsAt = 0)
        emit { ServerEvent.QuestionEvt(st.question!!, it) }
        if (multi) say(if (k == 0) part.text else "Вопрос ${k + 1}. ${part.text}") else if (myRole == Role.SPECTATOR || sector.type == SectorType.BLACKBOX) say(part.text)
        myLastTeamMessage = null
        hostAudienceSuggestions.clear(); myAudienceVotes.clear()

        // ---- DISCUSS ----
        currentAnswerer = st.superblitzPlayerId ?: captain().id
        val discussMs = if (multi) 20_000L else st.settings.discussSec * 1000L
        startBotChat(part, q.difficulty, discussMs, sector.type == SectorType.SUPERBLITZ)
        val cap = captain()
        if (cap.isBot && !multi) {
            if (st.audienceHelpLeft > 0 && rnd.nextFloat() < (if (myRole == Role.SPECTATOR) 0.45f else 0.15f)) launchLater(8000) { inbox.trySend(Msg.BotCallAudience) }
            if (rnd.nextFloat() < 0.25f) launchLater(discussMs / 2) { inbox.trySend(Msg.BotReadyAnswer(cap.id)) }
        }
        phase(Phase.DISCUSS, discussMs, sector.index) { m ->
            when {
                m is Msg.Client && m.a is ClientAction.ReadyAnswer -> {
                    if (currentAnswerer != MockData.ME) { error("not_captain"); false } else { earlyAnswer(); true }
                }
                m is Msg.BotReadyAnswer -> { earlyAnswer(); true }
                m is Msg.Client && m.a is ClientAction.CallAudience -> { if (cap.id == MockData.ME) callAudience() else error("not_captain"); false }
                m is Msg.BotCallAudience -> { callAudience(); false }
                else -> false
            }
        }
        botChatJob?.cancel()
        if (st.vote?.kind == VoteKind.EXTRA_MINUTE) resolveVote(st.vote!!.voteId, timeout = true)

        // ---- ANSWER ----
        var given = ""
        var viaVoice = false
        val answerer = currentAnswerer
        if (isBot(answerer)) launchLater(1500L + rnd.nextLong(2500)) { inbox.trySend(Msg.BotAnswer(answerer, botAnswer(part, q.difficulty, answerer))) }
        say(if (answerer == MockData.ME) "Ваш ответ?" else "Время! Ваш ответ?")
        phase(Phase.ANSWER, 10_000, sector.index) { m ->
            when {
                m is Msg.Client && m.a is ClientAction.Answer -> {
                    if (answerer != MockData.ME) { error("not_answerer"); false } else { given = m.a.text.take(200); viaVoice = m.a.viaVoice; true }
                }
                m is Msg.BotAnswer && m.playerId == answerer -> { given = m.text; true }
                else -> false
            }
        }
        val who = st.players.firstOrNull { it.id == answerer }?.nickname ?: ""
        if (given.isNotBlank()) chat("team", answerer, who, "Ответ: $given")

        // ---- CHECK ----
        phase(Phase.CHECK, if (given.isBlank()) 800 else 1800, sector.index)
        val raw = AnswerChecker.check(given, part.answer, part.accepted, q.rejected)
        val verdict = raw ?: aiJudge(given, part)
        val correct = verdict != Verdict.INCORRECT
        val delta = if (correct) Score(1, 0) else Score(0, 1)
        st = st.copy(score = Score(st.score.experts + delta.experts, st.score.viewers + delta.viewers))
        roundsPlayed++
        if (correct) correctCount++

        // ---- REVEAL ----
        val reveal = Reveal(
            answer = part.answer, comment = part.comment.ifBlank { q.comment }, source = q.source, verdict = verdict,
            scoreDelta = delta, givenAnswer = given.ifBlank { null }, questionId = q.id,
            appealable = verdict == Verdict.INCORRECT && given.isNotBlank() && st.appealsLeft > 0 && myRole == Role.PLAYER,
        )
        st = st.copy(reveal = reveal)
        if (k == total - 1) st = st.copy(sectors = st.sectors.map { if (it.index == sector.index) it.copy(played = true) else it })
        emit { ServerEvent.RevealEvt(reveal, it) }
        say(
            when (verdict) {
                Verdict.CORRECT -> "Правильный ответ: ${part.answer}. Очко знатокам!"
                Verdict.DISPUTED -> "Ответ засчитан с пометкой спорного. Правильный ответ: ${part.answer}."
                Verdict.INCORRECT -> "Правильный ответ: ${part.answer}. Очко получают зрители."
            }
        )
        phase(Phase.REVEAL, if (multi) 5000 else 9000, if (k == total - 1) sector.index else -1) { m ->
            if (m is Msg.Client && m.a is ClientAction.Appeal) { appeal(given, part); false } else false
        }

        // ---- SCORE ----
        emit { ServerEvent.ScoreEvt(st.score, it) }
        phase(Phase.SCORE, 2200, sector.index)
        return st.score.experts >= 6 || st.score.viewers >= 6
    }

    private fun earlyAnswer() {
        val remSec = ((st.phase.endsAt - now()) / 1000).toInt()
        val banked = (remSec / 5) * 5
        if (banked > 0) {
            st = st.copy(timeBankSec = st.timeBankSec + banked)
            snapshot()
            say("Досрочный ответ! $banked секунд уходят в копилку команды.")
        }
    }

    private fun aiJudge(given: String, part: QuestionPart): Verdict {
        // эмуляция ИИ-судьи: близкие формулировки — спорные (засчитываются с пометкой)
        val g = AnswerChecker.normalize(given)
        val a = AnswerChecker.normalize(part.answer)
        val d = AnswerChecker.levenshtein(g, a)
        return if (d <= AnswerChecker.allowedTypos(a.length) + 2) Verdict.DISPUTED else Verdict.INCORRECT
    }

    private fun appeal(given: String, part: QuestionPart) {
        val r = st.reveal ?: return
        if (!r.appealable || st.appealsLeft <= 0) { error("appeal_unavailable"); return }
        st = st.copy(appealsLeft = st.appealsLeft - 1)
        // «более сильная модель»: шире допуск, смотрит на корень слова
        val g = AnswerChecker.normalize(given)
        val variants = (listOf(part.answer) + part.accepted).map(AnswerChecker::normalize)
        val ok = variants.any { v ->
            val stem = v.take((v.length * 0.7f).roundToInt().coerceAtLeast(3))
            AnswerChecker.levenshtein(g, v) <= AnswerChecker.allowedTypos(v.length) + 3 || g.startsWith(stem)
        }
        if (ok) {
            st = st.copy(score = Score(st.score.experts + 1, (st.score.viewers - 1).coerceAtLeast(0)))
            correctCount++
            val nr = r.copy(verdict = Verdict.CORRECT, scoreDelta = Score(1, 0), appealable = false, comment = "Апелляция принята. " + r.comment)
            st = st.copy(reveal = nr)
            emit { ServerEvent.RevealEvt(nr, it) }
            emit { ServerEvent.ScoreEvt(st.score, it) }
            say("Апелляция принята! Очко переходит знатокам.")
        } else {
            val nr = r.copy(appealable = false, comment = "Апелляция отклонена. " + r.comment)
            st = st.copy(reveal = nr)
            emit { ServerEvent.RevealEvt(nr, it) }
            say("Апелляция отклонена.")
        }
    }

    private fun botAnswer(part: QuestionPart, difficulty: Int, botId: String): String {
        if (captain().id == botId && myLastTeamMessage != null && rnd.nextFloat() < 0.7f) return myLastTeamMessage!!
        val level = MockData.bots.firstOrNull { b -> st.players.any { it.id == botId && it.nickname == b.name } }?.level ?: BotLevel.MEDIUM
        val chance = MockData.botGuessCorrectChance(level, difficulty)
        return if (rnd.nextFloat() < chance) (listOf(part.answer) + part.accepted).random(rnd) else MockData.wrongGuesses.random(rnd)
    }

    private fun startBotChat(part: QuestionPart, difficulty: Int, discussMs: Long, superblitz: Boolean) {
        botChatJob?.cancel()
        val speakers = bots().filter { !superblitz || it.id == st.superblitzPlayerId }
        if (speakers.isEmpty()) return
        botChatJob = scope.launch {
            val end = now() + ms(discussMs) - ms(4000)
            var versionSaid = false
            while (isActive && now() < end) {
                delay(ms(2500L + rnd.nextLong(4500)))
                if (now() >= end) break
                val b = speakers.random(rnd)
                val profile = MockData.bots.firstOrNull { it.name == b.nickname }
                val level = profile?.level ?: BotLevel.MEDIUM
                if (!versionSaid && rnd.nextFloat() < 0.55f) {
                    val good = rnd.nextFloat() < MockData.botGuessCorrectChance(level, difficulty)
                    val v = if (good) part.answer else MockData.wrongGuesses.random(rnd)
                    val text = "Версия: $v"
                    chat("team", b.id, b.nickname, text)
                    if (myRole == Role.PLAYER) say(text, profile?.voice ?: "bot1")
                    versionSaid = rnd.nextFloat() < 0.5f
                } else {
                    chat("team", b.id, b.nickname, MockData.thinkingLines.random(rnd))
                }
            }
        }
    }

    private fun startHallChat() {
        hallChatJob = scope.launch {
            while (isActive) {
                delay(ms(6000L + rnd.nextLong(9000)))
                chat("hall", "v${rnd.nextInt(99)}", "Зритель ${rnd.nextInt(10, 99)}", MockData.hallLines.random(rnd))
            }
        }
    }

    // ---------------- помощь зала ----------------
    private fun callAudience() {
        if (!st.settings.audienceHelp || st.audienceHelpLeft <= 0) { error("audience_unavailable"); return }
        if (st.phase.phase != Phase.DISCUSS) { error("wrong_phase"); return }
        val endsAt = now() + ms(30_000)
        st = st.copy(audienceHelpLeft = st.audienceHelpLeft - 1, audienceEndsAt = endsAt, audience = emptyList())
        emit { ServerEvent.AudienceStartedEvt(endsAt, it) }
        say("Команда обращается за помощью к залу!")
        audienceJob?.cancel()
        audienceJob = scope.launch {
            val live = st.spectators > 0
            while (isActive && now() < endsAt) {
                delay(ms(if (live) 3000L + rnd.nextLong(3000) else 5000L))
                inbox.trySend(Msg.AudienceTick)
                if (!live) break
            }
        }
    }

    private fun audienceTick() {
        if (now() > st.audienceEndsAt) return
        val part = currentPart ?: return
        val live = st.spectators > 0
        if (!live) {
            if (hostAudienceSuggestions.none { it.fromAi }) {
                val v = if (rnd.nextFloat() < 0.55f) part.answer else MockData.wrongGuesses.random(rnd)
                hostAudienceSuggestions += AudienceSuggestion("ai", v, 0, fromAi = true)
            }
        } else {
            if (hostAudienceSuggestions.size < 5 && rnd.nextFloat() < 0.7f) {
                val v = if (rnd.nextFloat() < 0.45f) part.answer else MockData.wrongGuesses.random(rnd)
                if (hostAudienceSuggestions.none { it.text.equals(v, true) }) hostAudienceSuggestions += AudienceSuggestion("s${hostAudienceSuggestions.size}", v, 1)
            }
            if (hostAudienceSuggestions.isNotEmpty()) {
                val i = rnd.nextInt(hostAudienceSuggestions.size)
                val s = hostAudienceSuggestions[i]
                hostAudienceSuggestions[i] = s.copy(votes = s.votes + 1 + rnd.nextInt(3))
            }
        }
        publishAudience()
    }

    private fun publishAudience() {
        val list = hostAudienceSuggestions.sortedByDescending { it.votes }
        st = st.copy(audience = list)
        emit { ServerEvent.AudienceSuggestionsEvt(list, it) }
    }

    // ---------------- голосования ----------------
    private fun startVote(kind: VoteKind, target: String?, initiator: String) {
        if (st.vote != null) { error("vote_in_progress"); return }
        val team = st.players.map { it.id }.filter { it != target }.toSet()
        voteEligible = team
        votes.clear()
        val voteId = "v${System.nanoTime()}"
        val dur = if (kind == VoteKind.KICK) 20_000L else 10_000L
        val vote = ActiveVote(voteId, kind, target, now() + ms(dur), 0, 0, team.size, initiatorId = initiator)
        st = st.copy(vote = vote)
        emit { ServerEvent.VoteStartedEvt(vote, it) }
        castVote(voteId, initiator, true)
        for (b in bots().filter { it.id in team && it.id != initiator }) {
            val yesProb = when (kind) {
                VoteKind.EXTRA_MINUTE -> 0.75f
                VoteKind.KICK -> if (target != null && isBot(target)) 0.55f else 0.2f
                else -> 0.5f
            }
            val yes = rnd.nextFloat() < yesProb
            launchLater(1000L + rnd.nextLong((dur * 0.6).toLong())) { inbox.trySend(Msg.BotVote(voteId, b.id, yes)) }
        }
        launchLater(dur) { inbox.trySend(Msg.VoteTimeout(voteId)) }
    }

    private fun castVote(voteId: String, playerId: String, yes: Boolean) {
        val v = st.vote ?: return
        if (v.voteId != voteId || playerId !in voteEligible || playerId in votes) return
        votes[playerId] = yes
        val y = votes.values.count { it }; val n = votes.values.count { !it }
        st = st.copy(vote = v.copy(yes = y, no = n, myVote = votes[MockData.ME]))
        emit { ServerEvent.VoteProgressEvt(voteId, y, n, it) }
        if (votes.size >= voteEligible.size) resolveVote(voteId, timeout = false)
    }

    private fun resolveVote(voteId: String, timeout: Boolean) {
        val v = st.vote ?: return
        if (v.voteId != voteId) return
        val y = votes.values.count { it }; val n = votes.values.count { !it }
        val passed = when (v.kind) {
            VoteKind.KICK -> y * 2 > voteEligible.size && y >= 2
            else -> y * 2 > voteEligible.size
        }
        st = st.copy(vote = null)
        emit { ServerEvent.VoteResultEvt(VoteResult(voteId, v.kind, passed, y, n, v.targetId), it) }
        when (v.kind) {
            VoteKind.EXTRA_MINUTE -> if (passed && st.phase.phase == Phase.DISCUSS) {
                val add = when {
                    st.extraMinutesLeft > 0 -> { st = st.copy(extraMinutesLeft = st.extraMinutesLeft - 1); 60 }
                    st.timeBankSec > 0 -> { val a = minOf(60, st.timeBankSec); st = st.copy(timeBankSec = st.timeBankSec - a); a }
                    else -> 0
                }
                if (add > 0) {
                    val newEnds = st.phase.endsAt + add * 1000L
                    st = st.copy(phase = st.phase.copy(endsAt = newEnds))
                    emit { ServerEvent.TimeExtendedEvt(add, newEnds, it) }
                    snapshot()
                    say("Команда получает ещё $add секунд.")
                }
            }
            VoteKind.KICK -> {
                val initiator = v.initiatorId ?: return
                if (passed && v.targetId != null) {
                    kickFails[initiator] = 0
                    val wasCaptain = st.players.firstOrNull { it.id == v.targetId }?.isCaptain == true
                    var players = st.players.filterNot { it.id == v.targetId }
                    if (wasCaptain && players.isNotEmpty()) players = players.mapIndexed { i, p -> p.copy(isCaptain = i == 0) }
                    st = st.copy(players = players)
                    emit { ServerEvent.PlayerKickedEvt(v.targetId, it) }
                    snapshot()
                } else {
                    val f = (kickFails[initiator] ?: 0) + 1
                    kickFails[initiator] = f
                    if (f >= 3) kickBlocked += initiator
                }
            }
            else -> Unit
        }
    }

    // ---------------- общие сообщения ----------------
    private fun handleCommon(m: Msg) {
        when (m) {
            is Msg.BotVote -> castVote(m.voteId, m.playerId, m.yes)
            is Msg.VoteTimeout -> resolveVote(m.voteId, timeout = true)
            is Msg.AudienceTick -> audienceTick()
            is Msg.Client -> when (val a = m.a) {
                is ClientAction.Chat -> {
                    val text = a.text.trim().take(300)
                    if (text.isEmpty()) return
                    if (a.scope == "team" && myRole != Role.PLAYER) { error("forbidden"); return }
                    if (a.scope == "team") myLastTeamMessage = text
                    chat(a.scope, MockData.ME, me.nickname, uz.robutpit.znatoki.core.Profanity.mask(text))
                }
                is ClientAction.VoteCast -> castVote(a.voteId, MockData.ME, a.yes)
                is ClientAction.ExtraMinuteRequest -> {
                    if (myRole != Role.PLAYER) { error("forbidden"); return }
                    if (st.phase.phase != Phase.DISCUSS) { error("wrong_phase"); return }
                    if (st.extraMinutesLeft <= 0 && st.timeBankSec <= 0) { error("no_extra_time"); return }
                    startVote(VoteKind.EXTRA_MINUTE, null, MockData.ME)
                }
                is ClientAction.KickStart -> {
                    val t = now()
                    when {
                        myRole != Role.PLAYER -> error("forbidden")
                        a.targetId == MockData.ME -> error("kick_self")
                        MockData.ME in kickBlocked -> error("kick_blocked")
                        t - (kickLastInit[MockData.ME] ?: Long.MIN_VALUE / 2) < 120_000 -> error("kick_cooldown")
                        st.players.size < 3 -> error("kick_not_enough")
                        else -> { kickLastInit[MockData.ME] = t; startVote(VoteKind.KICK, a.targetId, MockData.ME) }
                    }
                }
                is ClientAction.AudienceSuggest -> {
                    if (myRole != Role.SPECTATOR) { error("forbidden"); return }
                    if (now() > st.audienceEndsAt) { error("audience_closed"); return }
                    if (hostAudienceSuggestions.none { it.authorId == MockData.ME }) {
                        hostAudienceSuggestions += AudienceSuggestion("my${System.nanoTime()}", uz.robutpit.znatoki.core.Profanity.mask(a.text.take(80)), 1, authorId = MockData.ME)
                        publishAudience()
                    } else error("already_suggested")
                }
                is ClientAction.AudienceVote -> {
                    if (myRole != Role.SPECTATOR || a.suggestionId in myAudienceVotes) return
                    val i = hostAudienceSuggestions.indexOfFirst { it.id == a.suggestionId }
                    if (i >= 0) {
                        myAudienceVotes += a.suggestionId
                        hostAudienceSuggestions[i] = hostAudienceSuggestions[i].let { it.copy(votes = it.votes + 1) }
                        publishAudience()
                    }
                }
                is ClientAction.Ack -> if (a.seq < seq) snapshot()
                is ClientAction.ChatReport -> Unit
                is ClientAction.Leave -> stop()
                is ClientAction.Spin, is ClientAction.ReadyAnswer, is ClientAction.Answer, is ClientAction.CallAudience -> error("wrong_phase")
                is ClientAction.Appeal -> error("appeal_unavailable")
                is ClientAction.Start, is ClientAction.Ready, is ClientAction.UpdateSettings, is ClientAction.SetCaptain, is ClientAction.AddBot -> error("game_started")
                is ClientAction.Join -> snapshot()
            }
            else -> Unit
        }
    }

    private fun finish() {
        botChatJob?.cancel(); audienceJob?.cancel()
        val winner = if (st.score.experts >= 6) "experts" else "viewers"
        val rated = st.settings.rated && roundsPlayed >= 6 && myRole == Role.PLAYER
        val iAmCaptain = captain().id == MockData.ME
        val deltas = if (rated) {
            val teamRating = st.players.map { it.rating }.average()
            val qRating = 1500.0 + (st.settings.difficulty - 3) * 100
            val expected = 1.0 / (1.0 + 10.0.pow((qRating - teamRating) / 400.0))
            val actual = correctCount.toDouble() / roundsPlayed
            val botWeight = if (st.players.any { it.isBot }) 0.5 else 1.0
            st.players.filter { !it.isBot }.map { p ->
                var d = 32.0 * roundsPlayed / 6.0 * (actual - expected) * botWeight
                if (p.isCaptain) d *= 1.1
                val di = d.roundToInt()
                RatingDelta(p.id, di, p.rating + di)
            }
        } else emptyList()
        val over = GameOver(winner, deltas, GameStats(roundsPlayed, correctCount, st.timeBankSec, st.players.firstOrNull { !it.isBot }?.id))
        st = st.copy(gameOver = over, phase = PhaseInfo(Phase.GAME_OVER, 0, roundsPlayed, -1))
        emit { ServerEvent.PhaseEvt(st.phase, it) }
        emit { ServerEvent.GameOverEvt(over, it) }
        say(if (winner == "experts") "Победа знатоков со счётом ${st.score.experts} : ${st.score.viewers}! Поздравляем!" else "Победили зрители со счётом ${st.score.viewers} : ${st.score.experts}. До новых встреч за столом!")
        onFinished(over, st.score, iAmCaptain)
    }
}
