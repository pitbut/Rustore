package uz.robutpit.znatoki.data.mock

import uz.robutpit.znatoki.domain.model.*
import kotlin.random.Random

/** Статичные данные мока: игроки, боты, реплики. Все имена вымышленные. */
internal object MockData {
    const val ME = "me"

    val people = listOf(
        PlayerShort("p1", "Дилноза", 3, 1712, true),
        PlayerShort("p2", "Тимур_К", 5, 1655, true),
        PlayerShort("p3", "Аня Вершинина", 1, 1590, false),
        PlayerShort("p4", "Бобур", 7, 1820, true),
        PlayerShort("p5", "Сабина", 2, 1490, false),
        PlayerShort("p6", "Шерзод", 4, 1534, true),
        PlayerShort("p7", "Олег Мудров", 6, 1901, false),
        PlayerShort("p8", "Камила", 8, 1622, true),
        PlayerShort("p9", "Жасур", 9, 1477, false),
        PlayerShort("p10", "Мария Лис", 10, 1744, true),
        PlayerShort("p11", "Азиз", 11, 1388, false),
        PlayerShort("p12", "Нигора", 0, 1560, true),
        PlayerShort("p13", "Фарход", 3, 1699, false),
        PlayerShort("p14", "Лола", 5, 1611, true),
        PlayerShort("p15", "Игорь Спицын", 2, 1450, false),
        PlayerShort("p16", "Гулнора", 7, 1803, true),
    )

    data class BotProfile(val name: String, val avatar: Int, val level: BotLevel, val voice: String)

    val bots = listOf(
        BotProfile("Бот Улугбек", 12, BotLevel.ERUDITE, "bot1"),
        BotProfile("Бот Софья", 13, BotLevel.MEDIUM, "bot2"),
        BotProfile("Бот Рустам", 14, BotLevel.MEDIUM, "bot3"),
        BotProfile("Бот Вера", 15, BotLevel.NOVICE, "bot4"),
        BotProfile("Бот Анвар", 12, BotLevel.ERUDITE, "bot5"),
        BotProfile("Бот Лейла", 13, BotLevel.NOVICE, "bot2"),
        BotProfile("Бот Денис", 14, BotLevel.MEDIUM, "bot3"),
        BotProfile("Бот Мадина", 15, BotLevel.MEDIUM, "bot4"),
        BotProfile("Бот Савелий", 12, BotLevel.ERUDITE, "bot1"),
        BotProfile("Бот Зарина", 13, BotLevel.NOVICE, "bot2"),
        BotProfile("Бот Кирилл", 14, BotLevel.MEDIUM, "bot3"),
        BotProfile("Бот Юлдуз", 15, BotLevel.ERUDITE, "bot5"),
    )

    val thinkingLines = listOf(
        "Давайте разберём по словам вопроса.",
        "Тут явно есть подвох.",
        "Мне кажется, ключ — в первой фразе.",
        "Что-то знакомое, но не могу вспомнить.",
        "Логика подсказывает что-то простое.",
        "А если это игра слов?",
        "Есть версия, сейчас скажу.",
        "Капитан, держим время!",
    )

    val wrongGuesses = listOf("Кирпич", "Самовар", "Шахматы", "Компас", "Фонарь", "Глобус", "Колокол", "Часы", "Перо", "Ключ")

    val hallLines = listOf(
        "Ну и вопрос!", "Знатоки, соберитесь!", "Я бы ответил сразу", "Отличная игра 👏", "Время, время!",
        "Капитан молодец", "Ставлю на зрителей", "Красиво раскрутили", "Вот это блиц!",
    )

    val achievements = listOf(
        Achievement("first_game", "Первый стол", "Сыграть первую игру", true),
        Achievement("first_win", "Победа знатоков", "Выиграть игру", true),
        Achievement("captain", "Капитан", "Сыграть 5 игр капитаном", false),
        Achievement("blitz", "Молния", "Ответить на все 3 вопроса блица", false),
        Achievement("box", "Взломщик", "Угадать чёрный ящик", true),
        Achievement("streak", "Серия", "5 верных ответов подряд", false),
        Achievement("author", "Автор", "Вопрос принят в общую базу", false),
    )

    fun botGuessCorrectChance(level: BotLevel, difficulty: Int): Float {
        val base = when (level) {
            BotLevel.NOVICE -> 0.35f
            BotLevel.MEDIUM -> 0.55f
            BotLevel.ERUDITE -> 0.75f
        }
        return (base - (difficulty - 3) * 0.08f).coerceIn(0.08f, 0.92f)
    }

    fun randomCode(rnd: Random = Random): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..5).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }

    fun sectorLayout(count: Int, special13: Boolean): List<Sector> {
        val types = MutableList(count) { SectorType.NORMAL }
        if (count >= 13) {
            types[2] = SectorType.BLITZ
            types[5] = SectorType.BLACKBOX
            types[8] = SectorType.SUPERBLITZ
            types[10] = SectorType.BLACKBOX
            if (special13) types[12] = SectorType.SPECIAL13
        } else {
            types[2] = SectorType.BLITZ
            types[4] = SectorType.BLACKBOX
            types[count - 1] = SectorType.SUPERBLITZ
        }
        return types.mapIndexed { i, t -> Sector(i, t) }
    }
}
