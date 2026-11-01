package uz.robutpit.znatoki.core

/**
 * Клиентский фильтр нецензурной лексики (дублирует серверный). Простая маскировка корней.
 * Список намеренно короткий и хранится в виде корней; сервер — основной фильтр.
 */
object Profanity {
    private val roots = listOf("хуй", "хуе", "хуё", "пизд", "ебат", "ебан", "ебал", "бляд", "блят", "сука", "мудак", "пидор", "гандон", "залуп", "fuck", "shit", "bitch")
    private val leet = mapOf('0' to 'о', '@' to 'а', '3' to 'з', 'a' to 'а', 'e' to 'е', 'o' to 'о', 'p' to 'р', 'c' to 'с', 'x' to 'х', 'y' to 'у')

    fun containsProfanity(text: String): Boolean = mask(text) != text

    fun mask(text: String): String {
        val words = text.split(Regex("(?<=\\s)|(?=\\s)"))
        return words.joinToString("") { w ->
            val norm = w.lowercase().map { leet[it] ?: it }.joinToString("").replace("ё", "е")
            val normLatin = w.lowercase()
            if (roots.any { norm.contains(it.replace("ё", "е")) || normLatin.contains(it) }) "*".repeat(w.length) else w
        }
    }
}
