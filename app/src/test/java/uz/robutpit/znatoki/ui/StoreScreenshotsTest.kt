package uz.robutpit.znatoki.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.robutpit.znatoki.data.local.ThemeMode
import uz.robutpit.znatoki.design.Club
import uz.robutpit.znatoki.design.ClubTheme
import uz.robutpit.znatoki.design.components.*
import uz.robutpit.znatoki.domain.game.SpinMath
import uz.robutpit.znatoki.domain.model.*
import uz.robutpit.znatoki.feature.game.*
import java.io.File

/** Скриншоты для карточки RuStore: 1080×1920, в store/screenshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class StoreScreenshotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val now = 1_000_000L
    private val players = listOf(
        PlayerInRoom("me", "Знаток", 4, isCaptain = true),
        PlayerInRoom("p1", "Дилноза", 3),
        PlayerInRoom("b1", "Бот Улугбек", 12, isBot = true),
        PlayerInRoom("p2", "Тимур", 5),
        PlayerInRoom("p3", "Аня", 1),
    )
    private val sectors = (0 until 13).map { i ->
        Sector(i, when (i) { 2 -> SectorType.BLITZ; 5, 10 -> SectorType.BLACKBOX; 8 -> SectorType.SUPERBLITZ; else -> SectorType.NORMAL }, played = i in setOf(0, 4, 7, 11))
    }

    private fun shot(name: String, caption: String, dark: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ClubTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                ClubBackground {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(22.dp))
                        Text(caption, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 25.sp, lineHeight = 30.sp), color = Club.colors.brass, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        content()
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(800)
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val dir = File("../store/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun s1_table() {
        val spin = SpinMath.makeSpin(11, 13, setOf(0, 4, 7, 11), 0f)
        shot("1_volchok", "Волчок выбирает вопрос") {
            ScoreBoard(Score(4, 3), Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TeamPanel(players, setOf("p1"), "me", null, {})
            RouletteTable(sectors, spin, { now }, spin.sectorIndex, null, effects = false, reduceMotion = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SectorBadge(sectors[spin.sectorIndex].type)
        }
    }

    @Test fun s2_discuss() = shot("2_obsuzhdenie", "Минута на обсуждение командой") {
        ScoreBoard(Score(2, 2), Modifier.fillMaxWidth())
        TeamPanel(players, setOf("p2", "me"), "me", null, {})
        Spacer(Modifier.height(6.dp))
        CircularTimer(now + 37_000, 60_000, { now }, size = 104.dp)
        Spacer(Modifier.height(10.dp))
        QuestionCard(GameQuestion("q", SectorType.NORMAL, "Этот газ составляет примерно 78 % атмосферы Земли, хотя его название, предложенное Лавуазье, означает «безжизненный». Назовите газ.", topic = "Наука"), false)
    }

    @Test fun s3_choose() = shot("3_kapitan", "Капитан выбирает, кто отвечает") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularTimer(now + 7_000, 10_000, { now }, size = 84.dp, warnAtMs = 3_000)
            Spacer(Modifier.width(14.dp))
            Text("Выбор отвечающего", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        ChooseAnswererCard(players, "me") {}
    }

    @Test fun s4_blackbox() = shot("4_chernyy_yashchik", "Чёрный ящик, блиц и суперблиц") {
        QuestionCard(GameQuestion("q", SectorType.BLACKBOX, "В ящике — головной убор, который в Узбекистане носят и мужчины, и женщины; самый известный узор на нём — стручки перца. Что в ящике?"), false)
        Spacer(Modifier.height(10.dp))
        QuestionCard(GameQuestion("b", SectorType.BLITZ, "Столица Австралии?", subIndex = 2, subTotal = 3), false)
    }

    @Test fun s5_reveal() = shot("5_otvet", "Честная проверка и апелляция") {
        RevealCard(
            Reveal("Тюбетейка", "Узор «калампир» — перец. Тюбетейку носят и мужчины, и женщины.", "Оригинальный вопрос «Клуба знатоков»", Verdict.CORRECT, Score(1, 0), "Тюбетейка", "q"),
            GameQuestion("q", SectorType.BLACKBOX, "…"), canAppeal = false, onAppeal = {}, feedbackDone = false, onFeedback = {}, showFeedback = true,
        )
        Spacer(Modifier.height(10.dp))
        BlackBox(open = true, Modifier.fillMaxWidth(0.6f))
    }

    @Test fun s6_win() = shot("6_pobeda", "Рейтинг, сезоны и достижения", dark = false) {
        Spacer(Modifier.height(40.dp))
        GameOverCard(GameOver("experts", listOf(RatingDelta("me", 18, 1612)), GameStats(10, 6, 25)), Score(6, 4), "me", {})
    }

    @Test fun s8_audience() = shot("8_pomoshch_zala", "Помощь зала — подсказки зрителей") {
        ScoreBoard(Score(3, 4), Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        AudienceResults(listOf(AudienceSuggestion("a", "Азот", 9), AudienceSuggestion("b", "Кислород", 4), AudienceSuggestion("c", "Аргон", 2)), now + 14_000, { now })
        Spacer(Modifier.height(10.dp))
        VoteProgress(ActiveVote("v", VoteKind.EXTRA_MINUTE, null, now + 6_000, yes = 3, no = 1, eligible = 5))
    }

    @Test fun s7_chat() = shot("7_chat", "Голос и чат команды") {
        TeamPanel(players, setOf("p1"), "me", null, {})
        Spacer(Modifier.height(8.dp))
        ChatPanel(
            listOf(
                ChatMessage("1", "team", "p1", "Дилноза", "Тут игра слов — «безжизненный»", 0),
                ChatMessage("2", "team", "b1", "Бот Улугбек", "Версия: Азот", 0),
                ChatMessage("3", "team", "me", "Знаток", "Согласен, азот. Капитан, отвечаем!", 0),
                ChatMessage("4", "team", "p2", "Тимур", "Есть ещё 20 секунд, проверим аргон?", 0),
            ), "me", {}, {}, {}, Modifier.height(420.dp),
        )
    }
}
