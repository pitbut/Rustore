package uz.robutpit.znatoki.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.robutpit.znatoki.BuildConfig
import uz.robutpit.znatoki.R
import uz.robutpit.znatoki.core.AppException
import uz.robutpit.znatoki.core.LocaleHelper
import uz.robutpit.znatoki.core.ServerClock
import uz.robutpit.znatoki.core.asApp
import uz.robutpit.znatoki.data.local.*
import uz.robutpit.znatoki.data.repo.SessionRepository
import uz.robutpit.znatoki.design.Club
import uz.robutpit.znatoki.design.components.*
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.feature.lobby.SwitchRow
import javax.inject.Inject
import javax.inject.Named
import kotlin.system.exitProcess

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val session: SessionRepository,
    private val gateway: GameGateway,
    val clock: ServerClock,
    @Named("useMock") val useMock: Boolean,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val deleting = MutableStateFlow(false)
    val error = MutableStateFlow<AppException?>(null)
    val support = MutableStateFlow("gtnz1071980@yandex.com")

    init { viewModelScope.launch { runCatching { gateway.config() }.getOrNull()?.supportEmail?.takeIf { it.isNotBlank() }?.let { support.value = it } } }

    fun update(f: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.update(f) }

    fun setLanguage(lang: String) {
        update { it.copy(language = lang) }
        LocaleHelper.set(context, lang)
    }

    fun logout(onDone: () -> Unit) = viewModelScope.launch { session.logout(); onDone() }

    fun deleteAccount(onDone: () -> Unit) = viewModelScope.launch {
        deleting.value = true; error.value = null
        try { session.deleteAccount(); onDone() } catch (e: Exception) { error.value = e.asApp() }
        deleting.value = false
    }

    fun setMock(v: Boolean) {
        DebugPrefs.setUseMock(context, v)
        // граф DI создаётся при старте процесса — перезапускаем приложение
        val i = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(i)
        exitProcess(0)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onLoggedOut: () -> Unit, onLegal: (String) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsState()
    val deleting by vm.deleting.collectAsState()
    val error by vm.error.collectAsState()
    val support by vm.support.collectAsState()
    val ctx = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleteText by rememberSaveable { mutableStateOf("") }
    val currentLang = remember { LocaleHelper.saved(ctx) }

    ClubScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Header(stringResource(R.string.settings_general))
            ClubCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.lang_system), currentLang.isBlank(), { vm.setLanguage("") })
                    Chip(stringResource(R.string.lang_ru), currentLang == "ru", { vm.setLanguage("ru") })
                    Chip(stringResource(R.string.lang_uz), currentLang == "uz", { vm.setLanguage("uz") })
                }
            }
            ClubCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.theme_dark), s.theme == ThemeMode.DARK, { vm.update { it.copy(theme = ThemeMode.DARK) } })
                    Chip(stringResource(R.string.theme_light), s.theme == ThemeMode.LIGHT, { vm.update { it.copy(theme = ThemeMode.LIGHT) } })
                    Chip(stringResource(R.string.theme_system), s.theme == ThemeMode.SYSTEM, { vm.update { it.copy(theme = ThemeMode.SYSTEM) } })
                }
            }
            SwitchRow(stringResource(R.string.settings_notifications), s.notifications) { v -> vm.update { it.copy(notifications = v) } }

            Header(stringResource(R.string.settings_sound))
            SwitchRow(stringResource(R.string.settings_sfx), s.sound) { v -> vm.update { it.copy(sound = v) } }
            SwitchRow(stringResource(R.string.settings_music), s.music) { v -> vm.update { it.copy(music = v) } }
            SwitchRow(stringResource(R.string.settings_spin_fx), s.spinEffects, stringResource(R.string.settings_spin_fx_sub)) { v -> vm.update { it.copy(spinEffects = v) } }
            SwitchRow(stringResource(R.string.settings_vibration), s.vibration) { v -> vm.update { it.copy(vibration = v) } }
            SwitchRow(stringResource(R.string.settings_host_voice), s.hostVoice) { v -> vm.update { it.copy(hostVoice = v) } }
            ClubCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_host_volume, (s.hostVolume * 100).toInt()), style = MaterialTheme.typography.titleMedium)
                Slider(s.hostVolume, { v -> vm.update { it.copy(hostVolume = v) } })
                Text(stringResource(R.string.settings_tts_pitch), style = MaterialTheme.typography.titleMedium)
                Slider(s.ttsPitch, { v -> vm.update { it.copy(ttsPitch = v) } }, valueRange = 0.6f..1.6f)
                Text(stringResource(R.string.settings_tts_rate), style = MaterialTheme.typography.titleMedium)
                Slider(s.ttsRate, { v -> vm.update { it.copy(ttsRate = v) } }, valueRange = 0.6f..1.6f)
                TextButton(onClick = { runCatching { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                    Text(stringResource(R.string.settings_tts_system))
                }
            }

            Header(stringResource(R.string.settings_mic))
            ClubCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_mic_mode), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.mic_open), s.micMode == MicMode.OPEN, { vm.update { it.copy(micMode = MicMode.OPEN) } })
                    Chip(stringResource(R.string.mic_ptt), s.micMode == MicMode.PUSH_TO_TALK, { vm.update { it.copy(micMode = MicMode.PUSH_TO_TALK) } })
                }
            }
            SwitchRow(stringResource(R.string.settings_noise), s.noiseSuppression) { v -> vm.update { it.copy(noiseSuppression = v) } }

            Header(stringResource(R.string.settings_about))
            ClubCard(Modifier.fillMaxWidth()) {
                LinkRow(Icons.Filled.Email, stringResource(R.string.settings_support, support)) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$support")).putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME)) }
                }
                LinkRow(Icons.Filled.PrivacyTip, stringResource(R.string.legal_privacy)) { onLegal("privacy") }
                LinkRow(Icons.Filled.Description, stringResource(R.string.legal_terms)) { onLegal("terms") }
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Header(stringResource(R.string.settings_account))
            ClubOutlinedButton(stringResource(R.string.settings_logout), { vm.logout(onLoggedOut) }, Modifier.fillMaxWidth(), Icons.Filled.Logout)
            OutlinedButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Filled.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_delete_account))
            }
            error?.let { Text(errorText(it.code, it.message), color = MaterialTheme.colorScheme.error) }

            if (BuildConfig.DEBUG) {
                Header(stringResource(R.string.debug_title))
                SwitchRow(stringResource(R.string.debug_mock), vm.useMock, stringResource(R.string.debug_mock_sub, BuildConfig.SERVER_BASE_URL)) { vm.setMock(it) }
                ClubCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.debug_offset, vm.clock.offset.collectAsState().value, vm.clock.lastRttMs), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_text))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(deleteText, { deleteText = it }, singleLine = true, label = { Text(stringResource(R.string.delete_type_hint)) })
            }
        },
        confirmButton = {
            val word = stringResource(R.string.delete_word)
            TextButton(onClick = { confirmDelete = false; vm.deleteAccount(onLoggedOut) }, enabled = deleteText.trim().equals(word, true) && !deleting) {
                Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Club.colors.brass, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
}

@Composable
private fun LinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(text, Modifier.weight(1f))
    }
}
