package uz.robutpit.znatoki.domain

import org.junit.Assert.*
import org.junit.Test
import uz.robutpit.znatoki.domain.game.AnswerChecker
import uz.robutpit.znatoki.domain.model.Verdict

class AnswerCheckerTest {
    @Test fun normalization() {
        assertEquals("ежик в тумане", AnswerChecker.normalize("  Ёжик, в ТУМАНЕ!! "))
        assertEquals("beatles", AnswerChecker.normalize("The Beatles"))
    }

    @Test fun `exact and accepted`() {
        assertEquals(Verdict.CORRECT, AnswerChecker.check("байкал", "Байкал", emptyList()))
        assertEquals(Verdict.CORRECT, AnswerChecker.check("Тамерлан", "Амир Темур", listOf("тамерлан", "тимур")))
    }

    @Test fun `typos within tolerance`() {
        assertEquals(Verdict.CORRECT, AnswerChecker.check("Бетховнн", "Бетховен", emptyList()))
        assertEquals(Verdict.INCORRECT, AnswerChecker.check("Моцарт", "Бетховен", emptyList()))
        assertEquals(Verdict.INCORRECT, AnswerChecker.check("", "Бетховен", emptyList()))
    }

    @Test fun `ambiguous goes to ai judge`() {
        assertNull(AnswerChecker.check("Баромтор", "Барометр", emptyList()))
    }

    @Test fun `rejected list wins`() {
        assertEquals(Verdict.INCORRECT, AnswerChecker.check("кирпич", "Кирпич", emptyList(), rejected = listOf("кирпич")))
    }

    @Test fun levenshtein() {
        assertEquals(3, AnswerChecker.levenshtein("kitten", "sitting"))
        assertEquals(0, AnswerChecker.levenshtein("", ""))
    }
}
