package uz.robutpit.znatoki.domain.game

import uz.robutpit.znatoki.domain.model.Phase
import uz.robutpit.znatoki.domain.model.RoomState
import uz.robutpit.znatoki.domain.model.ServerEvent

/**
 * Конечный автомат клиента: применяет события сервера к снимку комнаты.
 * Клиент ничего не решает — только переносит данные сервера в состояние.
 * - дубликаты (seq ≤ текущего) игнорируются;
 * - разрыв последовательности → needResync (клиент шлёт `game:ack` с последним seq и ждёт `room:state`).
 */
object GameReducer {

    data class Result(val state: RoomState?, val needResync: Boolean = false, val applied: Boolean = true)

    fun reduce(state: RoomState?, event: ServerEvent): Result {
        if (event is ServerEvent.RoomStateEvt) {
            if (state != null && event.seq < state.seq && state.roomId == event.state.roomId) return Result(state, applied = false)
            return Result(event.state.copy(seq = event.seq))
        }
        if (event is ServerEvent.ErrorEvt || event is ServerEvent.HostSpeechEvt) return Result(state, applied = false)
        if (state == null) return Result(null, needResync = true, applied = false)
        if (event.seq <= state.seq) return Result(state, applied = false)
        val gap = event.seq > state.seq + 1
        val s = apply(state, event).copy(seq = event.seq)
        return Result(s, needResync = gap)
    }

    private fun apply(s: RoomState, e: ServerEvent): RoomState = when (e) {
        is ServerEvent.PhaseEvt -> {
            val p = e.info.phase
            var n = s.copy(phase = e.info)
            if (p == Phase.SPIN) n = n.copy(question = null, reveal = null, audience = emptyList(), superblitzPlayerId = null, answererId = null)
            if (p == Phase.DISCUSS || p == Phase.CHOOSE_ANSWERER) n = n.copy(answererId = if (p == Phase.DISCUSS) null else n.answererId)
            if (p == Phase.INTRO) n = n.copy(reveal = null)
            if (e.info.sector >= 0 && p == Phase.REVEAL) {
                n = n.copy(sectors = n.sectors.map { if (it.index == e.info.sector) it.copy(played = true) else it })
            }
            n
        }
        is ServerEvent.SpinResultEvt -> s.copy(spin = e.spin)
        is ServerEvent.QuestionEvt -> s.copy(question = e.question, reveal = null, answererId = null)
        is ServerEvent.AnswererEvt -> s.copy(answererId = e.playerId)
        is ServerEvent.RevealEvt -> s.copy(reveal = e.reveal)
        is ServerEvent.ScoreEvt -> s.copy(score = e.score)
        is ServerEvent.VoteStartedEvt -> s.copy(vote = e.vote)
        is ServerEvent.VoteProgressEvt -> s.copy(vote = s.vote?.takeIf { it.voteId == e.voteId }?.copy(yes = e.yes, no = e.no) ?: s.vote)
        is ServerEvent.VoteResultEvt -> s.copy(vote = s.vote?.takeIf { it.voteId != e.result.voteId })
        is ServerEvent.AudienceStartedEvt -> s.copy(audienceEndsAt = e.endsAt, audience = emptyList())
        is ServerEvent.AudienceSuggestionsEvt -> s.copy(audience = e.list)
        is ServerEvent.TimeExtendedEvt -> s.copy(phase = s.phase.copy(endsAt = e.newEndsAt))
        is ServerEvent.PlayerKickedEvt -> s.copy(players = s.players.filterNot { it.id == e.playerId })
        is ServerEvent.PlayerStatusEvt -> s.copy(players = s.players.map { if (it.id == e.playerId) it.copy(online = e.online) else it })
        is ServerEvent.ChatEvt -> s
        is ServerEvent.GameOverEvt -> s.copy(gameOver = e.over, phase = s.phase.copy(phase = Phase.GAME_OVER))
        is ServerEvent.RoomStateEvt, is ServerEvent.ErrorEvt, is ServerEvent.HostSpeechEvt -> s
    }
}
