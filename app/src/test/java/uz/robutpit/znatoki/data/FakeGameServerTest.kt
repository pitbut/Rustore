package uz.robutpit.znatoki.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import uz.robutpit.znatoki.data.mock.FakeGameServer
import uz.robutpit.znatoki.domain.game.GameReducer
import uz.robutpit.znatoki.domain.game.SpinMath
import uz.robutpit.znatoki.domain.model.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Интеграционный тест мок-сервера: клиент проходит целые игры до 6 очков
 * (время ускорено в 100 раз), задействуя все типы секторов и все игровые механики.
 */
class FakeGameServerTest {
    private val raw = File("src/main/assets/sample_questions.json").readText()
    private val bank: Map<String, Question> = Json { ignoreUnknownKeys = true }.decodeFromString<List<Question>>(raw).associateBy { it.id }

    private class Stats {
        val types = mutableSetOf<SectorType>()
        val votes = mutableSetOf<VoteKind>()
        var audienceSuggestions = 0
        var timeExtended = 0
        var kicked = 0
        var appeals = 0
        var reveals = 0
        var spinsChecked = 0
        var chooses = 0
        var botAnswerers = 0
        var timeoutCaptain = 0
        var answerPhaseMs = 0L
        var errors = mutableListOf<String>()
        var over: GameOver? = null
        var score = Score()
    }

    private fun playGame(seed: Int, settings: RoomSettings): Stats = runBlocking {
        val server = FakeGameServer({ raw }, latencyMs = 0, timeScale = 0.01f)
        server.login("tester@example.com", "password123")
        val roomId = server.createRoom(settings)
        val rnd = Random(seed)
        val st = Stats()
        var state: RoomState? = null
        val done = CompletableDeferred<Unit>()
        var askedExtra = false; var askedAudience = false; var askedKick = false; var appealed = false
        val answered = AtomicInteger()

        val job = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            server.events.collect { e ->
                val r = GameReducer.reduce(state, e)
                if (r.applied) state = r.state
                val s = state ?: return@collect
                when (e) {
                    is ServerEvent.RoomStateEvt -> if (s.phase.phase == Phase.LOBBY && e.seq == 1L) {
                        server.send(ClientAction.Ready(true)); server.send(ClientAction.Start)
                    }
                    is ServerEvent.PhaseEvt -> { if (System.getenv("TRACE") != null) println("T+${System.currentTimeMillis() % 100000} ${e.info.phase} dur=${e.info.endsAt - System.currentTimeMillis() - 1250}"); when (e.info.phase) {
                        Phase.SPIN -> if (s.players.first { it.isCaptain }.id == "me") server.send(ClientAction.Spin)
                        Phase.DISCUSS -> {
                            if (!askedExtra) { askedExtra = true; server.send(ClientAction.ExtraMinuteRequest) }
                            else if (!askedAudience && s.question?.subTotal == 1) { askedAudience = true; server.send(ClientAction.CallAudience) }
                            else if (!askedKick && s.players.size >= 4) { askedKick = true; server.send(ClientAction.KickStart(s.players.last { it.isBot }.id)) }
                            else if (rnd.nextFloat() < 0.3f && (s.superblitzPlayerId ?: "me") == "me") server.send(ClientAction.ReadyAnswer)
                            s.vote?.let { v -> if (v.myVote == null) server.send(ClientAction.VoteCast(v.voteId, true)) }
                        }
                        Phase.CHOOSE_ANSWERER -> if (s.players.first { it.isCaptain }.id == "me") {
                            st.chooses++
                            val roll = rnd.nextFloat()
                            when {
                                roll < 0.4f -> server.send(ClientAction.ChooseAnswerer("me"))
                                roll < 0.8f -> server.send(ClientAction.ChooseAnswerer(s.players.first { it.isBot }.id))
                                else -> Unit // не выбираем — должен отвечать сам капитан
                            }
                        }
                        Phase.ANSWER -> {
                            st.answerPhaseMs = maxOf(st.answerPhaseMs, e.info.endsAt - (System.currentTimeMillis() + 1250))
                            val answerer = s.answererId ?: error("answererId не пришёл до фазы ANSWER")
                            if (s.superblitzPlayerId == null && s.players.firstOrNull { it.id == answerer }?.isBot == true) st.botAnswerers++
                            if (answerer == "me") {
                                val q = bank.getValue(s.question!!.id)
                                val right = if (q.parts.isNotEmpty()) q.parts[s.question!!.subIndex].answer else q.answer
                                answered.incrementAndGet()
                                server.send(ClientAction.Answer(if (rnd.nextFloat() < 0.55f) right else "Определённо не то", false))
                            }
                        }
                        else -> Unit
                    } }
                    is ServerEvent.SpinResultEvt -> {
                        // клиентская анимация должна закончиться точно на секторе сервера
                        val end = SpinMath.angleAt(e.spin, e.spin.durationMs, s.sectors.size)
                        assertEquals(e.spin.sectorIndex, SpinMath.sectorAt(end, s.sectors.size))
                        assertFalse(s.sectors[e.spin.sectorIndex].played)
                        st.spinsChecked++
                    }
                    is ServerEvent.QuestionEvt -> {
                        st.types += e.question.type
                        assertFalse("answer must not leak", e.question.text.contains(bank[e.question.id]?.answer ?: "\u0000") && e.question.type == SectorType.NORMAL)
                    }
                    is ServerEvent.VoteStartedEvt -> { st.votes += e.vote.kind; if (e.vote.initiatorId != "me") server.send(ClientAction.VoteCast(e.vote.voteId, true)) }
                    is ServerEvent.AudienceSuggestionsEvt -> st.audienceSuggestions = maxOf(st.audienceSuggestions, e.list.size)
                    is ServerEvent.TimeExtendedEvt -> st.timeExtended++
                    is ServerEvent.PlayerKickedEvt -> st.kicked++
                    is ServerEvent.RevealEvt -> {
                        st.reveals++
                        if (e.reveal.appealable && !appealed) { appealed = true; st.appeals++; server.send(ClientAction.Appeal) }
                    }
                    is ServerEvent.ErrorEvt -> st.errors += e.code
                    is ServerEvent.GameOverEvt -> { st.over = e.over; st.score = s.score; done.complete(Unit) }
                    else -> Unit
                }
            }
        }
        server.send(ClientAction.Join(roomId, null, Role.PLAYER))
        withTimeout(240_000) { done.await() }
        job.cancel()
        server.disconnectRealtime()
        st
    }

    @Test fun `full games reach 6 points and cover every sector type`() {
        val all = mutableSetOf<SectorType>()
        val votes = mutableSetOf<VoteKind>()
        var extended = 0; var kicked = 0; var audience = 0; var spins = 0; var chooses = 0; var botAns = 0
        for (seed in 1..6) {
            val st = playGame(seed, RoomSettings(teamSize = 5, fillWithBots = true, sectors = 13, discussSec = 30, rated = true))
            assertNotNull(st.over)
            assertTrue("score ${st.score}", st.score.experts == 6 || st.score.viewers == 6)
            assertEquals(if (st.score.experts == 6) "experts" else "viewers", st.over!!.winner)
            all += st.types; votes += st.votes
            chooses += st.chooses; botAns += st.botAnswerers
            assertTrue("answer phase ~30 s scaled", st.answerPhaseMs in 150..400)
            extended += st.timeExtended; kicked += st.kicked; audience += st.audienceSuggestions; spins += st.spinsChecked
            assertFalse("unexpected errors ${st.errors}", st.errors.any { it == "not_in_room" || it == "unauthorized" })
            if (all.containsAll(listOf(SectorType.NORMAL, SectorType.BLITZ, SectorType.SUPERBLITZ, SectorType.BLACKBOX)) && seed >= 2) break
        }
        assertTrue("seen: $all", all.containsAll(listOf(SectorType.NORMAL, SectorType.BLITZ, SectorType.SUPERBLITZ, SectorType.BLACKBOX)))
        assertTrue(VoteKind.EXTRA_MINUTE in votes)
        assertTrue(VoteKind.KICK in votes)
        assertTrue("audience help gave versions", audience > 0)
        assertTrue(spins >= 6)
        assertTrue("captain chose answerer", chooses > 0)
        assertTrue("chosen bot answered", botAns > 0)
        println("types=$all votes=$votes extended=$extended kicked=$kicked audience=$audience spins=$spins")
    }

    @Test fun `seven sector table reopens when all sectors are played`() {
        val st = playGame(99, RoomSettings(teamSize = 3, fillWithBots = true, sectors = 7, discussSec = 30))
        assertNotNull(st.over)
        assertTrue(st.reveals >= 6)
    }

    @Test fun `rest endpoints of the mock`() = runBlocking {
        val s = FakeGameServer({ raw }, latencyMs = 0)
        s.register("new@example.com", "password123", "Новичок")
        assertEquals("Новичок", s.me().nickname)
        assertTrue(s.friends().isNotEmpty())
        assertTrue(s.leaderboard(LeaderboardScope.GLOBAL).first().place == 1)
        val team = s.createTeam("Тестовая", 1)
        assertEquals(1, s.myTeams().count { it.id == team.id })
        val gen = s.generate("История", 3, 4, "ru")
        assertEquals(4, gen.drafts.size); assertEquals(16, gen.quotaLeft)
        val q = s.suggestQuestion(Question(id = "", text = "Короткий", answer = "Ответ"))
        assertEquals(QuestionStatus.pending, q.status)
        assertTrue(s.packs().isNotEmpty())
        assertTrue(s.openRooms().any { it.inGame })
        val off = s.serverTime() - System.currentTimeMillis()
        assertTrue("mock server clock is shifted", off in 1000..1600)
        s.deleteAccount()
        try { s.me(); fail() } catch (e: uz.robutpit.znatoki.core.AppException) { assertEquals("unauthorized", e.code) }
    }
}
