package uz.robutpit.znatoki.domain

import org.junit.Assert.*
import org.junit.Test
import uz.robutpit.znatoki.domain.game.GameReducer
import uz.robutpit.znatoki.domain.model.*

class GameReducerTest {
    private val base = RoomState(
        roomId = "r1", code = "ABCD", hostId = "me", settings = RoomSettings(),
        players = listOf(PlayerInRoom("me", "Я", isCaptain = true), PlayerInRoom("b", "Бот", isBot = true)),
        sectors = (0 until 13).map { Sector(it, SectorType.NORMAL) },
    )

    @Test fun `room state snapshot replaces everything`() {
        val r = GameReducer.reduce(null, ServerEvent.RoomStateEvt(base, 5))
        assertEquals(5, r.state!!.seq)
        assertFalse(r.needResync)
    }

    @Test fun `event before snapshot requests resync`() {
        val r = GameReducer.reduce(null, ServerEvent.ScoreEvt(Score(1, 0), 3))
        assertTrue(r.needResync)
        assertNull(r.state)
    }

    @Test fun `duplicates are ignored`() {
        val s = GameReducer.reduce(null, ServerEvent.RoomStateEvt(base, 10)).state
        val r = GameReducer.reduce(s, ServerEvent.ScoreEvt(Score(3, 3), 10))
        assertFalse(r.applied)
        assertEquals(Score(), r.state!!.score)
    }

    @Test fun `gap in seq triggers resync but applies event`() {
        val s = GameReducer.reduce(null, ServerEvent.RoomStateEvt(base, 1)).state
        val r = GameReducer.reduce(s, ServerEvent.ScoreEvt(Score(1, 0), 4))
        assertTrue(r.needResync)
        assertEquals(1, r.state!!.score.experts)
    }

    @Test fun `full phase cycle`() {
        var s = GameReducer.reduce(null, ServerEvent.RoomStateEvt(base, 1)).state
        var seq = 1L
        fun ev(e: (Long) -> ServerEvent) { seq++; s = GameReducer.reduce(s, e(seq)).state }
        ev { ServerEvent.PhaseEvt(PhaseInfo(Phase.SPIN, 1000, 1), it) }
        ev { ServerEvent.SpinResultEvt(SpinResult(3, 0f, 1200f, 4000, 1), it) }
        ev { ServerEvent.PhaseEvt(PhaseInfo(Phase.INTRO, 2000, 1, 3), it) }
        ev { ServerEvent.QuestionEvt(GameQuestion("q", SectorType.NORMAL, "Текст"), it) }
        ev { ServerEvent.PhaseEvt(PhaseInfo(Phase.DISCUSS, 60_000, 1, 3), it) }
        ev { ServerEvent.TimeExtendedEvt(60, 120_000, it) }
        assertEquals(120_000, s!!.phase.endsAt)
        ev { ServerEvent.VoteStartedEvt(ActiveVote("v", VoteKind.KICK, "b", 9999, eligible = 1), it) }
        ev { ServerEvent.VoteProgressEvt("v", 1, 0, it) }
        assertEquals(1, s!!.vote!!.yes)
        ev { ServerEvent.VoteResultEvt(VoteResult("v", VoteKind.KICK, true, 1, 0, "b"), it) }
        assertNull(s!!.vote)
        ev { ServerEvent.PlayerKickedEvt("b", it) }
        assertEquals(1, s!!.players.size)
        ev { ServerEvent.PhaseEvt(PhaseInfo(Phase.CHOOSE_ANSWERER, 65_000, 1, 3), it) }
        ev { ServerEvent.AnswererEvt("me", it) }
        assertEquals("me", s!!.answererId)
        ev { ServerEvent.PhaseEvt(PhaseInfo(Phase.ANSWER, 70_000, 1, 3), it) }
        assertEquals("answerer kept in ANSWER", "me", s!!.answererId)
        ev { ServerEvent.RevealEvt(Reveal("Ответ", verdict = Verdict.CORRECT, scoreDelta = Score(1, 0)), it) }
        ev { ServerEvent.PhaseEvt(PhaseInfo(Phase.REVEAL, 80_000, 1, 3), it) }
        assertTrue(s!!.sectors[3].played)
        ev { ServerEvent.ScoreEvt(Score(1, 0), it) }
        ev { ServerEvent.PhaseEvt(PhaseInfo(Phase.SPIN, 90_000, 2), it) }
        assertNull("question cleared on new spin", s!!.question)
        assertNull(s!!.reveal)
        assertNull("answerer reset", s!!.answererId)
        ev { ServerEvent.GameOverEvt(GameOver("experts"), it) }
        assertEquals(Phase.GAME_OVER, s!!.phase.phase)
        assertEquals(seq, s!!.seq)
    }

    @Test fun `older snapshot of same room ignored, other room accepted`() {
        val s = GameReducer.reduce(null, ServerEvent.RoomStateEvt(base, 20)).state
        assertFalse(GameReducer.reduce(s, ServerEvent.RoomStateEvt(base.copy(title = "old"), 5)).applied)
        val other = GameReducer.reduce(s, ServerEvent.RoomStateEvt(base.copy(roomId = "r2"), 1))
        assertEquals("r2", other.state!!.roomId)
    }
}
