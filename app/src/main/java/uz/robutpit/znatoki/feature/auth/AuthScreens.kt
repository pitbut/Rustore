package uz.robutpit.znatoki.feature.auth

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.robutpit.znatoki.R
import uz.robutpit.znatoki.core.AppException
import uz.robutpit.znatoki.core.asApp
import uz.robutpit.znatoki.data.local.SettingsRepository
import uz.robutpit.znatoki.data.repo.SessionRepository
import uz.robutpit.znatoki.design.Club
import uz.robutpit.znatoki.design.components.*
import uz.robutpit.znatoki.domain.GameGateway
import javax.inject.Inject

data class AuthUi(
    val loading: Boolean = false,
    val error: AppException? = null,
    val done: Boolean = false,
    val sent: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val session: SessionRepository,
    private val gateway: GameGateway,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUi())
    val ui: StateFlow<AuthUi> = _ui

    private fun run(block: suspend () -> Unit, onOk: (AuthUi) -> AuthUi = { it.copy(done = true) }) {
        if (_ui.value.loading) return
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try { block(); _ui.update { onOk(it.copy(loading = false)) } } catch (e: Exception) { _ui.update { it.copy(loading = false, error = e.asApp()) } }
        }
    }

    fun login(email: String, password: String) = run({ session.login(email, password) })
    fun loginGoogle(activity: android.app.Activity, mock: Boolean) = run({
        if (mock) { session.loginGoogle("mock"); return@run }
        when (val r = GoogleSignIn.signIn(activity)) {
            is GoogleSignIn.Result.Token -> session.loginGoogle(r.idToken)
            GoogleSignIn.Result.Cancelled -> throw AppException("cancelled", "")
            GoogleSignIn.Result.Unavailable -> throw AppException("google_unavailable")
            is GoogleSignIn.Result.Failed -> throw AppException("google_failed", r.message)
        }
    })
    fun register(email: String, password: String, nick: String) = run({ session.register(email, password, nick) })
    fun forgot(email: String) = run({ gateway.forgotPassword(email.trim()) }) { it.copy(sent = true) }
    fun reset(token: String, password: String) = run({ gateway.resetPassword(token.trim(), password) })
    fun verify(code: String) = run({ gateway.verifyEmail(code); session.refreshProfile() })
    fun clearError() = _ui.update { it.copy(error = null) }
    fun finishOnboarding() = viewModelScope.launch { settings.update { it.copy(onboardingDone = true) } }
}

// ---------------- Onboarding ----------------
private data class OnbPage(val icon: ImageVector, val title: Int, val text: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val pages = listOf(
        OnbPage(Icons.Filled.Explore, R.string.onb_title_1, R.string.onb_text_1),
        OnbPage(Icons.Filled.Groups, R.string.onb_title_2, R.string.onb_text_2),
        OnbPage(Icons.Filled.Lightbulb, R.string.onb_title_3, R.string.onb_text_3),
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    ClubBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(120.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = Club.colors.brass)
            HorizontalPager(pager, Modifier.weight(1f)) { i ->
                val p = pages[i]
                Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(Club.colors.felt), contentAlignment = Alignment.Center) {
                        Icon(p.icon, null, Modifier.size(48.dp), tint = Club.colors.brass)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(p.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(p.text), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { i ->
                    Box(Modifier.size(if (pager.currentPage == i) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (pager.currentPage == i) Club.colors.brass else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            Spacer(Modifier.height(24.dp))
            val last = pager.currentPage == pages.lastIndex
            ClubButton(
                stringResource(if (last) R.string.onb_start else R.string.action_next),
                onClick = { if (last) { vm.finishOnboarding(); onDone() } else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth(), large = true,
            )
            TextButton(onClick = { vm.finishOnboarding(); onDone() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_skip)) }
        }
    }
}

// ---------------- Login ----------------
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onRegister: () -> Unit, onForgot: () -> Unit, isMock: Boolean, vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(ui.done) { if (ui.done) onLoggedIn() }
    AuthFrame(title = stringResource(R.string.login_title)) {
        if (isMock) MockHint()
        val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
        if ((GoogleSignIn.available || isMock) && activity != null) {
            OutlinedButton(
                onClick = { vm.loginGoogle(activity, isMock) }, enabled = !ui.loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text("G", style = MaterialTheme.typography.titleLarge, color = Club.colors.brass)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.login_google), style = MaterialTheme.typography.labelLarge)
            }
            Text(stringResource(R.string.login_google_terms), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(Modifier.weight(1f))
                Text(stringResource(R.string.login_or), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelMedium)
                HorizontalDivider(Modifier.weight(1f))
            }
        }
        EmailField(email) { email = it; vm.clearError() }
        PasswordField(pass, ImeAction.Done) { pass = it; vm.clearError() }
        ui.error?.let { ErrorLine(it) }
        ClubButton(stringResource(R.string.login_action), { vm.login(email, pass) }, Modifier.fillMaxWidth(), loading = ui.loading, enabled = email.isNotBlank() && pass.isNotBlank())
        TextButton(onClick = onForgot, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.login_forgot)) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ClubOutlinedButton(stringResource(R.string.login_create), onRegister, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MockHint() {
    Surface(color = Club.colors.felt.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium) {
        Text(stringResource(R.string.mock_login_hint), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------- Register ----------------
@Composable
fun RegisterScreen(onRegistered: () -> Unit, onBack: () -> Unit, onOpenLegal: (String) -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var nick by rememberSaveable { mutableStateOf("") }
    var agree by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ui.done) { if (ui.done) onRegistered() }
    AuthFrame(title = stringResource(R.string.register_title), onBack = onBack) {
        OutlinedTextField(
            nick, { nick = it.take(24); vm.clearError() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.field_nickname)) },
            singleLine = true, leadingIcon = { Icon(Icons.Filled.Person, null) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            supportingText = { Text(stringResource(R.string.field_nickname_hint)) },
        )
        EmailField(email) { email = it; vm.clearError() }
        PasswordField(pass, ImeAction.Done, showHint = true) { pass = it; vm.clearError() }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(agree, { agree = it })
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.register_agree), style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { onOpenLegal("terms") }) { Text(stringResource(R.string.legal_terms)) }
                    TextButton(onClick = { onOpenLegal("privacy") }) { Text(stringResource(R.string.legal_privacy)) }
                }
            }
        }
        ui.error?.let { ErrorLine(it) }
        ClubButton(
            stringResource(R.string.register_action), { vm.register(email, pass, nick) }, Modifier.fillMaxWidth(),
            loading = ui.loading, enabled = agree && email.isNotBlank() && pass.length >= 8 && nick.length >= 2,
        )
    }
}

// ---------------- Verify e-mail ----------------
@Composable
fun VerifyEmailScreen(onDone: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(ui.done) { if (ui.done) onDone() }
    AuthFrame(title = stringResource(R.string.verify_title)) {
        Text(stringResource(R.string.verify_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            code, { code = it.filter(Char::isDigit).take(6); vm.clearError() }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_code)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        )
        ui.error?.let { ErrorLine(it) }
        ClubButton(stringResource(R.string.verify_action), { vm.verify(code) }, Modifier.fillMaxWidth(), loading = ui.loading, enabled = code.length == 6)
        TextButton(onClick = onDone, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.verify_later)) }
    }
}

// ---------------- Forgot / reset ----------------
@Composable
fun ForgotScreen(onBack: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(ui.done) { if (ui.done) onBack() }
    AuthFrame(title = stringResource(R.string.forgot_title), onBack = onBack) {
        AnimatedContent(ui.sent, label = "forgot") { sent ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!sent) {
                    Text(stringResource(R.string.forgot_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    EmailField(email) { email = it; vm.clearError() }
                    ui.error?.let { ErrorLine(it) }
                    ClubButton(stringResource(R.string.forgot_send), { vm.forgot(email) }, Modifier.fillMaxWidth(), loading = ui.loading, enabled = email.contains('@'))
                } else {
                    Text(stringResource(R.string.forgot_sent), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(token, { token = it.trim() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.field_reset_code)) }, singleLine = true)
                    PasswordField(pass, ImeAction.Done, showHint = true) { pass = it; vm.clearError() }
                    ui.error?.let { ErrorLine(it) }
                    ClubButton(stringResource(R.string.forgot_reset), { vm.reset(token, pass) }, Modifier.fillMaxWidth(), loading = ui.loading, enabled = token.isNotBlank() && pass.length >= 8)
                }
            }
        }
    }
}

// ---------------- Legal (WebView) ----------------
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LegalScreen(kind: String, onBack: () -> Unit, gateway: GameGateway) {
    var url by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(kind) { url = runCatching { gateway.legalUrl(kind) }.getOrNull() }
    ClubScaffold(title = stringResource(if (kind == "privacy") R.string.legal_privacy else R.string.legal_terms), onBack = onBack) { pad ->
        val u = url
        if (u == null) SkeletonList(6, Modifier.padding(pad))
        else AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = u.startsWith("file:")
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    loadUrl(u)
                }
            },
            modifier = Modifier.fillMaxSize().padding(pad),
        )
    }
}

// ---------------- shared ----------------
@Composable
private fun AuthFrame(title: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    ClubScaffold(title = "", onBack = onBack) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(96.dp))
                Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.trim()) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.field_email)) }, singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Email, null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
    )
}

@Composable
private fun PasswordField(value: String, ime: ImeAction, showHint: Boolean = false, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.field_password)) }, singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Lock, null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, stringResource(if (visible) R.string.cd_hide_password else R.string.cd_show_password))
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime),
        supportingText = if (showHint) ({ Text(stringResource(R.string.field_password_hint)) }) else null,
    )
}

@Composable
fun ErrorLine(e: AppException) {
    if (e.code == "cancelled") return
    Text(errorText(e.code, e.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
}
