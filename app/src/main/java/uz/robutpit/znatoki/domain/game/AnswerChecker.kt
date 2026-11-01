package uz.robutpit.znatoki.domain.game

import uz.robutpit.znatoki.domain.model.Verdict
import java.text.Normalizer
import kotlin.math.min

/**
 * Проверка ответа по контракту: нормализация → точное совпадение → fuzzy → (неоднозначно → ИИ-судья).
 * На сервере последний шаг — модель; в моке — эвристика.
 */
object AnswerChecker {
    private val articles = setOf("the", "a", "an", "le", "la", "les", "der", "die", "das")

    fun normalize(s: String): String {
        val lower = s.lowercase().replace('ё', 'е').replace('ў', 'у').replace('қ', 'к').replace('ғ', 'г').replace('ҳ', 'х')
        val noMarks = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        val words = noMarks.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(' ').filter { it.isNotBlank() && it !in articles }
        return words.joinToString(" ")
    }

    fun allowedTypos(len: Int): Int = when {
        len <= 4 -> 0
        len <= 8 -> 1
        len <= 14 -> 2
        else -> 3
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** null — нужна проверка ИИ-судьёй. */
    fun check(given: String, answer: String, accepted: List<String>, rejected: List<String> = emptyList()): Verdict? {
        val g = normalize(given)
        if (g.isEmpty()) return Verdict.INCORRECT
        if (rejected.any { normalize(it) == g }) return Verdict.INCORRECT
        val variants = (listOf(answer) + accepted).map(::normalize).filter { it.isNotEmpty() }
        if (variants.any { it == g }) return Verdict.CORRECT
        var best = Int.MAX_VALUE
        var bestLen = 0
        for (v in variants) {
            val d = levenshtein(g, v)
            if (d < best) { best = d; bestLen = v.length }
            // ответ целиком содержит ключевой вариант (например «Пётр Первый» vs «Пётр»)
            if (v.length >= 5 && (g.contains(v) || (g.length >= 5 && v.contains(g) && g.length * 2 >= v.length))) return Verdict.CORRECT
        }
        val allowed = allowedTypos(bestLen)
        if (best <= allowed) return Verdict.CORRECT
        if (best <= allowed + 2 && bestLen >= 6) return null
        return Verdict.INCORRECT
    }
}
