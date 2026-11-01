package uz.robutpit.znatoki.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import uz.robutpit.znatoki.domain.model.*
import java.io.File

class ContractParsingTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test fun `parses game phase and spin result`() {
        val p = json.decodeFromString<PhaseInfo>("""{"phase":"DISCUSS","endsAt":1730000000000,"round":3,"sector":5,"seq":44}""")
        assertEquals(Phase.DISCUSS, p.phase); assertEquals(5, p.sector)
        val s = json.decodeFromString<SpinResult>("""{"sectorIndex":7,"startAngle":12.5,"totalRotationDeg":1590.0,"durationMs":5200,"seed":991}""")
        assertEquals(7, s.sectorIndex); assertEquals(0L, s.startedAt)
    }

    @Test fun `question event carries no answer`() {
        val q = json.decodeFromString<GameQuestion>("""{"id":"q1","type":"blackbox","text":"Что в ящике?","subIndex":0,"answer":"секрет"}""")
        assertEquals(SectorType.BLACKBOX, q.type)
        assertFalse(GameQuestion::class.java.declaredFields.any { it.name == "answer" })
    }

    @Test fun `reveal verdict lowercase`() {
        val r = json.decodeFromString<Reveal>("""{"answer":"Азот","comment":"","source":"","verdict":"disputed","scoreDelta":{"experts":1,"viewers":0}}""")
        assertEquals(Verdict.DISPUTED, r.verdict)
        assertEquals(1, r.scoreDelta.experts)
    }

    @Test fun `room state with unknown fields`() {
        val raw = """{"roomId":"r","code":"AB12C","hostId":"u1","settings":{"teamSize":4,"sectors":7,"futureFlag":true},
            "players":[{"id":"u1","nickname":"Аня","isCaptain":true}],"phase":{"phase":"LOBBY"},"score":{"experts":2,"viewers":1},"seq":9}"""
        val st = json.decodeFromString<RoomState>(raw)
        assertEquals(7, st.settings.sectors)
        assertEquals("Аня", st.players.single().nickname)
    }

    @Test fun `error envelope`() {
        val e = json.decodeFromString<uz.robutpit.znatoki.data.remote.ErrorEnvelope>("""{"error":{"code":"room_full","message":"Комната заполнена"}}""")
        assertEquals("room_full", e.error.code)
    }

    @Test fun `bundled 60 questions are valid`() {
        val f = File("src/main/assets/sample_questions.json")
        val list = json.decodeFromString<List<Question>>(f.readText())
        assertEquals(60, list.size)
        assertEquals(list.size, list.map { it.id }.toSet().size)
        assertTrue(list.count { it.type == SectorType.BLITZ } >= 3)
        assertTrue(list.count { it.type == SectorType.SUPERBLITZ } >= 3)
        assertTrue(list.count { it.type == SectorType.BLACKBOX } >= 3)
        list.filter { it.type == SectorType.BLITZ || it.type == SectorType.SUPERBLITZ }.forEach { assertEquals(3, it.parts.size) }
        list.forEach { assertTrue(it.answer.isNotBlank() && it.text.isNotBlank()) }
    }
}
