package uz.robutpit.znatoki.core

import org.junit.Assert.*
import org.junit.Test

class ProfanityTest {
    @Test fun `masks bad words and keeps clean text`() {
        assertEquals("Привет, знатоки!", Profanity.mask("Привет, знатоки!"))
        assertTrue(Profanity.containsProfanity("ну ты и сука"))
        assertFalse(Profanity.mask("ну ты и сука").contains("сука"))
    }

    @Test fun `click guard blocks double taps`() {
        var t = 0L
        val g = ClickGuard(500) { t }
        assertTrue(g.allow()); t = 100; assertFalse(g.allow()); t = 700; assertTrue(g.allow())
    }
}
