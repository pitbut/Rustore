package uz.robutpit.znatoki.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import uz.robutpit.znatoki.domain.model.AuthTokens
import javax.inject.Inject
import javax.inject.Singleton

/** Токены хранятся в EncryptedSharedPreferences (ключ в Android Keystore). */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "secure_tokens", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // повреждённый keystore (бывает после восстановления бэкапа) — начинаем с чистого листа
        context.deleteSharedPreferences("secure_tokens")
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "secure_tokens", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var tokens: AuthTokens?
        get() {
            val a = prefs.getString("access", null) ?: return null
            val r = prefs.getString("refresh", null) ?: return null
            return AuthTokens(a, r, prefs.getLong("exp", 0))
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) clear() else {
                    putString("access", value.accessToken); putString("refresh", value.refreshToken); putLong("exp", value.expiresAt)
                }
            }.apply()
        }

    /** Минимальные данные для восстановления мок-сессии (не секрет). */
    var lastEmail: String?
        get() = prefs.getString("email", null)
        set(v) { prefs.edit().putString("email", v).apply() }
    var lastNickname: String?
        get() = prefs.getString("nick", null)
        set(v) { prefs.edit().putString("nick", v).apply() }
    var lastAvatar: Int
        get() = prefs.getInt("avatar", 0)
        set(v) { prefs.edit().putInt("avatar", v).apply() }
}
