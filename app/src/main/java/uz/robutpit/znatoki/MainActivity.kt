package uz.robutpit.znatoki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.robutpit.znatoki.core.LocaleHelper
import uz.robutpit.znatoki.data.local.AppSettings
import uz.robutpit.znatoki.data.local.SettingsRepository
import uz.robutpit.znatoki.data.repo.GameSession
import uz.robutpit.znatoki.design.ClubTheme
import uz.robutpit.znatoki.navigation.AppNavHost
import uz.robutpit.znatoki.sound.SoundFx
import uz.robutpit.znatoki.voice.HostSpeaker
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var sound: SoundFx
    @Inject lateinit var host: HostSpeaker
    @Inject lateinit var session: GameSession

    /** Код комнаты из ссылки-приглашения https://game.robutpit.com/join/CODE */
    private val deepLinkCode = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        lifecycleScope.launch {
            settingsRepo.settings.collect { s ->
                sound.soundEnabled = s.sound
                sound.musicEnabled = s.music
                sound.vibrationEnabled = s.vibration
                host.enabled = s.hostVoice
                host.volume = s.hostVolume
                host.basePitch = s.ttsPitch
                host.baseRate = s.ttsRate
                host.setLang(LocaleHelper.current(this@MainActivity))
            }
        }
        setContent {
            val settings by settingsRepo.settings.collectAsState(initial = AppSettings(onboardingDone = true))
            ClubTheme(settings.theme) {
                AppNavHost(deepLinkCode = deepLinkCode.value, onDeepLinkConsumed = { deepLinkCode.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val code = data.pathSegments.getOrNull(1)?.takeIf { data.pathSegments.firstOrNull() == "join" }
        if (code != null && code.length in 4..6 && code.all { it.isLetterOrDigit() }) deepLinkCode.value = code.uppercase()
    }

    override fun onStart() {
        super.onStart()
        session.resync()
    }

    override fun onStop() {
        super.onStop()
        sound.stopMusic()
    }
}
