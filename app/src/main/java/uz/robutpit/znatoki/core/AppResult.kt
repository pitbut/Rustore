package uz.robutpit.znatoki.core

/** Ошибка, понятная UI. code — из контракта `{"error":{"code","message"}}`. */
class AppException(val code: String, override val message: String = code, cause: Throwable? = null) : Exception(message, cause) {
    companion object {
        const val NETWORK = "network"
        const val UNAUTHORIZED = "unauthorized"
        const val INVALID = "invalid"
        const val NOT_FOUND = "not_found"
        const val QUOTA = "quota_exceeded"
        const val UNKNOWN = "unknown"
    }
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val error: AppException) : Load<Nothing>
}

suspend fun <T> runLoad(block: suspend () -> T): Load<T> = try {
    Load.Ready(block())
} catch (e: AppException) {
    Load.Failed(e)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    Load.Failed(AppException(AppException.UNKNOWN, e.message ?: "error", e))
}

fun Throwable.asApp(): AppException = this as? AppException ?: AppException(AppException.UNKNOWN, message ?: "error", this)
