package uz.robutpit.znatoki.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uz.robutpit.znatoki.core.AppException
import uz.robutpit.znatoki.core.ServerClock
import uz.robutpit.znatoki.data.local.AppDatabase
import uz.robutpit.znatoki.data.mock.FakeGameServer
import uz.robutpit.znatoki.data.remote.TokenStore
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.domain.model.Profile
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Unknown : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val profile: Profile) : AuthState
}

@Singleton
class SessionRepository @Inject constructor(
    private val gateway: GameGateway,
    private val tokens: TokenStore,
    private val clock: ServerClock,
    private val db: AppDatabase,
) {
    private val _auth = MutableStateFlow<AuthState>(AuthState.Unknown)
    val auth: StateFlow<AuthState> = _auth

    val profile: Profile? get() = (_auth.value as? AuthState.LoggedIn)?.profile

    /** Синхронизация часов: GET /time, offset = server - (send + rtt/2). */
    suspend fun syncClock() {
        runCatching {
            val send = System.currentTimeMillis()
            val server = gateway.serverTime()
            val recv = System.currentTimeMillis()
            clock.applySample(send, recv, server)
        }
    }

    suspend fun restore() {
        syncClock()
        val t = tokens.tokens
        if (t == null) { _auth.value = AuthState.LoggedOut; return }
        if (gateway is FakeGameServer) gateway.restore(tokens.lastEmail ?: "me@example.com", tokens.lastNickname ?: "Знаток", tokens.lastAvatar)
        _auth.value = try {
            AuthState.LoggedIn(gateway.me().also(::remember))
        } catch (e: AppException) {
            // выходим только если сервер окончательно отклонил сессию (TokenRefresher уже стёр токены);
            // нет интернета или сервер недоступен — остаёмся в аккаунте с кэшированным профилем
            if (e.code == AppException.UNAUTHORIZED && tokens.tokens == null) AuthState.LoggedOut
            else tokens.lastNickname?.let { AuthState.LoggedIn(Profile("me", tokens.lastEmail ?: "", it, tokens.lastAvatar)) } ?: AuthState.LoggedOut
        }
    }

    private fun remember(p: Profile) {
        tokens.lastEmail = p.email; tokens.lastNickname = p.nickname; tokens.lastAvatar = p.avatar
    }

    suspend fun login(email: String, password: String) {
        tokens.tokens = gateway.login(email.trim(), password)
        val p = gateway.me(); remember(p)
        _auth.value = AuthState.LoggedIn(p)
    }

    suspend fun loginGoogle(idToken: String) {
        tokens.tokens = gateway.loginGoogle(idToken)
        val p = gateway.me(); remember(p)
        _auth.value = AuthState.LoggedIn(p)
    }

    suspend fun register(email: String, password: String, nickname: String) {
        tokens.tokens = gateway.register(email.trim(), password, nickname.trim())
        val p = gateway.me(); remember(p)
        _auth.value = AuthState.LoggedIn(p)
    }

    suspend fun refreshProfile(): Profile {
        val p = gateway.me(); remember(p)
        _auth.value = AuthState.LoggedIn(p)
        return p
    }

    suspend fun updateProfile(nickname: String? = null, avatar: Int? = null) {
        val p = gateway.updateMe(nickname, avatar); remember(p)
        _auth.value = AuthState.LoggedIn(p)
    }

    suspend fun logout() {
        runCatching { gateway.logout() }
        tokens.tokens = null
        db.cache().clear()
        _auth.value = AuthState.LoggedOut
    }

    suspend fun deleteAccount() {
        gateway.deleteAccount()
        tokens.tokens = null; tokens.lastNickname = null; tokens.lastEmail = null
        db.cache().clear(); db.questions().clear()
        _auth.value = AuthState.LoggedOut
    }
}
