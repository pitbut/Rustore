package uz.robutpit.znatoki.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class MicMode { OPEN, PUSH_TO_TALK }

data class AppSettings(
    val language: String = "", // "" — системный, "ru", "uz"
    val sound: Boolean = true,
    val music: Boolean = true,
    val hostVolume: Float = 0.9f,
    val hostVoice: Boolean = true,
    val ttsPitch: Float = 1.0f,
    val ttsRate: Float = 1.0f,
    val micMode: MicMode = MicMode.OPEN,
    val noiseSuppression: Boolean = true,
    val vibration: Boolean = true,
    val theme: ThemeMode = ThemeMode.DARK,
    val notifications: Boolean = true,
    val spinEffects: Boolean = true,
    val onboardingDone: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val lang = stringPreferencesKey("lang")
        val sound = booleanPreferencesKey("sound")
        val music = booleanPreferencesKey("music")
        val hostVolume = floatPreferencesKey("host_volume")
        val hostVoice = booleanPreferencesKey("host_voice")
        val pitch = floatPreferencesKey("tts_pitch")
        val rate = floatPreferencesKey("tts_rate")
        val mic = stringPreferencesKey("mic_mode")
        val ns = booleanPreferencesKey("noise_suppression")
        val vib = booleanPreferencesKey("vibration")
        val theme = stringPreferencesKey("theme")
        val notif = booleanPreferencesKey("notifications")
        val spinFx = booleanPreferencesKey("spin_fx")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            language = p[K.lang] ?: "",
            sound = p[K.sound] ?: true,
            music = p[K.music] ?: true,
            hostVolume = p[K.hostVolume] ?: 0.9f,
            hostVoice = p[K.hostVoice] ?: true,
            ttsPitch = p[K.pitch] ?: 1f,
            ttsRate = p[K.rate] ?: 1f,
            micMode = runCatching { MicMode.valueOf(p[K.mic] ?: "OPEN") }.getOrDefault(MicMode.OPEN),
            noiseSuppression = p[K.ns] ?: true,
            vibration = p[K.vib] ?: true,
            theme = runCatching { ThemeMode.valueOf(p[K.theme] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            notifications = p[K.notif] ?: true,
            spinEffects = p[K.spinFx] ?: true,
            onboardingDone = p[K.onboarding] ?: false,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val cur = AppSettings(
                language = p[K.lang] ?: "", sound = p[K.sound] ?: true, music = p[K.music] ?: true,
                hostVolume = p[K.hostVolume] ?: 0.9f, hostVoice = p[K.hostVoice] ?: true, ttsPitch = p[K.pitch] ?: 1f, ttsRate = p[K.rate] ?: 1f,
                micMode = runCatching { MicMode.valueOf(p[K.mic] ?: "OPEN") }.getOrDefault(MicMode.OPEN),
                noiseSuppression = p[K.ns] ?: true, vibration = p[K.vib] ?: true,
                theme = runCatching { ThemeMode.valueOf(p[K.theme] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
                notifications = p[K.notif] ?: true, spinEffects = p[K.spinFx] ?: true, onboardingDone = p[K.onboarding] ?: false,
            )
            val n = transform(cur)
            p[K.lang] = n.language; p[K.sound] = n.sound; p[K.music] = n.music; p[K.hostVolume] = n.hostVolume
            p[K.hostVoice] = n.hostVoice; p[K.pitch] = n.ttsPitch; p[K.rate] = n.ttsRate; p[K.mic] = n.micMode.name
            p[K.ns] = n.noiseSuppression; p[K.vib] = n.vibration; p[K.theme] = n.theme.name; p[K.notif] = n.notifications
            p[K.spinFx] = n.spinEffects; p[K.onboarding] = n.onboardingDone
        }
    }
}

/** Debug-переключатель мок/реальный сервер. Читается синхронно при старте (нужен до создания графа DI). */
object DebugPrefs {
    private const val FILE = "debug_prefs"
    fun useMock(context: Context, default: Boolean): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("use_mock", default)
    fun setUseMock(context: Context, value: Boolean) { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("use_mock", value).commit() }
}
