package uz.robutpit.znatoki.push

import android.content.Context

/**
 * Абстракция пушей. Сейчас подключена заглушка; чтобы включить FCM — добавьте google-services.json
 * и реализацию FcmPushProvider; для RuStore — RuStore Push SDK и RuStorePushProvider.
 * Токен регистрируется на сервере через `POST /devices {provider, token}`.
 */
interface PushProvider {
    val name: String // "fcm" | "rustore"
    suspend fun token(): String?
    fun init(context: Context) {}
}

class NoopPushProvider : PushProvider {
    override val name = "none"
    override suspend fun token(): String? = null
}
