package uz.robutpit.znatoki.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.robutpit.znatoki.data.local.ThemeMode
import uz.robutpit.znatoki.design.ClubTheme
import uz.robutpit.znatoki.design.components.*
import uz.robutpit.znatoki.domain.game.SpinMath
import uz.robutpit.znatoki.domain.model.*
import uz.robutpit.znatoki.feature.game.*
import java.io.File

/** UI-тесты ключевых компонентов + скриншоты в app/build/screenshots (для визуальной проверки). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_000_000L
    private val sectors = (0 until 13).map { i ->
        Sector(i, when (i) { 2 -> SectorType.BLITZ; 5, 10 -> SectorType.BLACKBOX; 8 -> SectorType.SUPERBLITZ; else -> SectorType.NORMAL }, played = i in setOf(0, 4, 7))
    }
    private val players = listOf(
        PlayerInRoom("me", "Знаток", 4, isCaptain = true),
        PlayerInRoom("b1", "Бот Улугбек", 12, isBot = true),
        PlayerInRoom("p2", "Дилноза", 3),
        PlayerInRoom("b2", "Бот Софья", 13, isBot = true, online = false),
        PlayerInRoom("p3", "Тимур", 5, micMutedByHost = true),
    )

    private fun shot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        // бесконечные анимации (пульс «говорит», таймер) — управляем часами вручную
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ClubTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                ClubBackground { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun roulette_table() {
        val spin = SpinMath.makeSpin(5, 13, setOf(0, 4, 7), 0f)
        shot("01_roulette") {
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreBoard(Score(3, 2), Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TeamPanel(players, setOf("b1"), "me", null, {})
                RouletteTable(sectors, spin, { now }, spin.sectorIndex, null, effects = false, reduceMotion = true, modifier = Modifier.fillMaxWidth())
                SectorBadge(sectors[spin.sectorIndex].type)
            }
        }
        rule.onNodeWithContentDescription("Счёт: знатоки 3, зрители 2").assertExists()
    }

    @Test fun question_and_timer() {
        shot("02_question") {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoreBoard(Score(1, 1), Modifier.fillMaxWidth())
                TeamPanel(players, setOf("p2"), "me", null, {})
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularTimer(now + 42_000, 60_000, { now }, size = 96.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Обсуждение", style = MaterialTheme.typography.titleLarge)
                }
                QuestionCard(GameQuestion("q1", SectorType.NORMAL, "Этот газ составляет примерно 78 % атмосферы Земли, хотя его название означает «безжизненный». Назовите газ.", topic = "Наука"), false)
                AudienceResults(listOf(AudienceSuggestion("a", "Азот", 7), AudienceSuggestion("b", "Кислород", 3), AudienceSuggestion("c", "Аргон", 1)), now + 12_000, { now })
            }
        }
        rule.onNodeWithText("Азот").assertExists()
        rule.onNodeWithContentDescription("Осталось 42 секунд").assertExists()
    }

    @Test fun blackbox_question() {
        shot("03_blackbox") {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                QuestionCard(GameQuestion("q55", SectorType.BLACKBOX, "В ящике — головной убор, который в Узбекистане носят и мужчины, и женщины. Что в ящике?"), false)
            }
        }
        rule.onNodeWithText("Чёрный ящик").assertExists()
    }

    @Test fun reveal_with_appeal_and_feedback() {
        var appealed = false
        var fb: QuestionFeedback? = null
        shot("04_reveal") {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                RevealCard(
                    Reveal("Азот", "Лавуазье назвал его azote — «нежизненный».", "Оригинальный вопрос «Клуба знатоков»", Verdict.INCORRECT, Score(0, 1), "Азод", "q2", appealable = true),
                    GameQuestion("q2", SectorType.NORMAL, "..."), canAppeal = true, onAppeal = { appealed = true },
                    feedbackDone = false, onFeedback = { fb = it }, showFeedback = true,
                )
            }
        }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithText("Подать апелляцию").performClick()
        rule.onNodeWithContentDescription("Понравился").performClick()
        assertTrue(appealed)
        assertEquals(true, fb?.liked)
    }

    @Test fun vote_dialog_requires_confirmation() {
        var vote: Boolean? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ClubTheme {
                VoteDialog(ActiveVote("v1", VoteKind.EXTRA_MINUTE, null, now + 8_000, yes = 2, no = 1, eligible = 5), null, { now }, { vote = it }, {})
            }
        }
        // в первые 700 мс кнопки неактивны — защита от случайного нажатия
        rule.onNodeWithText("За").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithText("За").assertIsEnabled().performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(null, vote) // нужен второй тап
        rule.onNodeWithText("Подтвердить").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(true, vote)
    }

    @Test fun game_over_light_theme() {
        shot("05_game_over_light", dark = false) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                GameOverCard(GameOver("experts", listOf(RatingDelta("me", 14, 1578)), GameStats(9, 6, 15)), Score(6, 3), "me", {})
            }
        }
        rule.onNodeWithText("Победа знатоков!").assertExists()
        rule.onNodeWithText("Рейтинг +14 → 1578").assertExists()
    }

    @Test fun chat_and_answer_input() {
        var sent: String? = null
        shot("06_chat_answer") {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                AnswerInput(onSubmit = { t, _ -> sent = t }, onVoice = {}, voiceAvailable = true)
                Spacer(Modifier.height(12.dp))
                ChatPanel(
                    listOf(
                        ChatMessage("1", "team", "b1", "Бот Улугбек", "Версия: Азот", 0),
                        ChatMessage("2", "team", "me", "Знаток", "Согласен, азот", 0),
                        ChatMessage("3", "team", "p2", "Дилноза", "Тут явно игра слов", 0),
                    ), "me", {}, {}, {}, Modifier.height(320.dp),
                )
            }
        }
        rule.onNodeWithText("Сказать ответ голосом").assertHasClickAction()
        rule.onNodeWithText("Ответ команды").performTextInput("Азот")
        rule.mainClock.advanceTimeBy(1200)
        rule.onNodeWithText("Отправить ответ").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals("Азот", sent)
    }

    @Test fun empty_and_error_states() {
        shot("07_states") {
            Column(Modifier.fillMaxSize()) {
                EmptyState("Пока нет друзей", "Найдите знатоков по нику")
                ErrorState(uz.robutpit.znatoki.core.AppException("network"), {})
                SkeletonList(2)
            }
        }
        rule.onNodeWithText("Повторить").assertHasClickAction()
    }
}
