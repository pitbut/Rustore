package uz.robutpit.znatoki.data.remote

import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import uz.robutpit.znatoki.domain.model.AuthTokens

/**
 * Продление сессии строго по одному запросу за раз.
 * Сервер выдаёт одноразовые refresh-токены и при повторном использовании старого отзывает всю сессию,
 * поэтому параллельные 401 не должны продлевать сессию дважды.
 * Сессия сбрасывается только если сервер ответил «токен недействителен» (400/401/403),
 * а не при отсутствии интернета.
 */
class TokenRefresher(private val tokens: TokenStore, private val refreshApi: () -> ApiService) {
    private val lock = Any()

    sealed interface Result {
        data class Ok(val tokens: AuthTokens) : Result
        data object Revoked : Result
        data object NetworkError : Result
    }

    /** @param failedAccess access-токен, с которым запрос получил 401 (null — продлить принудительно). */
    fun refresh(failedAccess: String?): Result = synchronized(lock) {
        val cur = tokens.tokens ?: return Result.Revoked
        // пока ждали блокировку, другой поток уже обновил токен — просто используем новый
        if (failedAccess != null && cur.accessToken != failedAccess) return Result.Ok(cur)
        try {
            val fresh = runBlocking { refreshApi().refresh(RefreshDto(cur.refreshToken)) }
            tokens.tokens = fresh
            Result.Ok(fresh)
        } catch (e: HttpException) {
            if (e.code() in setOf(400, 401, 403)) { tokens.tokens = null; Result.Revoked } else Result.NetworkError
        } catch (e: Exception) {
            Result.NetworkError
        }
    }

    /** Токен истекает в ближайшую минуту (по полю exp внутри JWT). */
    fun accessExpiresSoon(): Boolean {
        val t = tokens.tokens?.accessToken ?: return false
        return runCatching {
            val payload = String(android.util.Base64.decode(t.split(".")[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))
            val exp = Regex("\"exp\"\\s*:\\s*(\\d+)").find(payload)!!.groupValues[1].toLong()
            exp * 1000 - System.currentTimeMillis() < 60_000
        }.getOrDefault(false)
    }
}
