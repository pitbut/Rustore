package uz.robutpit.znatoki.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.robutpit.znatoki.R
import uz.robutpit.znatoki.core.AppException
import uz.robutpit.znatoki.core.asApp
import uz.robutpit.znatoki.data.repo.AuthState
import uz.robutpit.znatoki.data.repo.SessionRepository
import uz.robutpit.znatoki.design.Club
import uz.robutpit.znatoki.design.components.*
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.domain.model.*
import uz.robutpit.znatoki.sound.SoundFx
import javax.inject.Inject

data class HomeUi(
    val profile: Profile? = null,
    val invites: List<Invite> = emptyList(),
    val teams: List<Team> = emptyList(),
    val top: List<LeaderboardEntry> = emptyList(),
    val qod: QuestionOfDay? = null,
    val loading: Boolean = true,
    val searching: Boolean = false,
    val error: AppException? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gateway: GameGateway,
    session: SessionRepository,
    val sound: SoundFx,
) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui
    private val _nav = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val navToRoom: SharedFlow<String> = _nav
    private val _errors = MutableSharedFlow<AppException>(extraBufferCapacity = 4)
    val errors: SharedFlow<AppException> = _errors

    init {
        viewModelScope.launch { session.auth.collect { a -> if (a is AuthState.LoggedIn) _ui.update { it.copy(profile = a.profile) } } }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null) }
        try {
            val inv = gateway.invites()
            val teams = gateway.myTeams()
            val top = runCatching { gateway.leaderboard(LeaderboardScope.GLOBAL).take(3) }.getOrDefault(emptyList())
            val q = runCatching { gateway.questionOfDay() }.getOrNull()
            _ui.update { it.copy(invites = inv, teams = teams, top = top, qod = q, loading = false) }
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, error = e.asApp()) }
        }
    }

    fun quickPlay() {
        if (_ui.value.searching) return
        _ui.update { it.copy(searching = true) }
        viewModelScope.launch {
            try { _nav.emit(gateway.quickPlay()) } catch (e: Exception) { _errors.emit(e.asApp()) }
            _ui.update { it.copy(searching = false) }
        }
    }

    fun cancelSearch() = viewModelScope.launch { runCatching { gateway.cancelQuickPlay() }; _ui.update { it.copy(searching = false) } }

    fun joinCode(code: String) = viewModelScope.launch {
        try { _nav.emit(gateway.joinByCode(code)) } catch (e: Exception) { _errors.emit(e.asApp()) }
    }

    fun answerInvite(inv: Invite, accept: Boolean) = viewModelScope.launch {
        try {
            val room = gateway.answerInvite(inv.id, accept)
            _ui.update { it.copy(invites = it.invites - inv) }
            if (room != null) _nav.emit(room)
            if (inv.kind == InviteKind.TEAM && accept) _ui.update { it.copy(teams = gateway.myTeams()) }
        } catch (e: Exception) { _errors.emit(e.asApp()) }
    }

    fun startTeam(team: Team) = viewModelScope.launch {
        try { _nav.emit(gateway.startTeamRoom(team.id)) } catch (e: Exception) { _errors.emit(e.asApp()) }
    }
}

@Composable
fun HomeScreen(
    onRoom: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onOpenGames: () -> Unit,
    onTeams: () -> Unit,
    onLeaderboard: () -> Unit,
    onSettings: () -> Unit,
    deepLinkCode: String?,
    onDeepLinkConsumed: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showCode by rememberSaveable { mutableStateOf(false) }
    val errorMsgs = rememberErrorMessages()
    LaunchedEffect(Unit) { vm.navToRoom.collect(onRoom) }
    LaunchedEffect(Unit) { vm.errors.collect { snack.showSnackbar(errorMsgs(it)) } }
    LaunchedEffect(deepLinkCode) { deepLinkCode?.let { vm.joinCode(it); onDeepLinkConsumed() } }
    DisposableEffect(Unit) { vm.sound.startMusic(); onDispose { } }

    ClubScaffold(
        title = stringResource(R.string.app_name), onBack = null, snackbar = snack,
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings_title)) } },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(340.dp),
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    ui.profile?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayerAvatar(it.avatar, size = 52.dp, contentDescription = it.nickname)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.home_hello, it.nickname), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.home_rating, it.rating), style = MaterialTheme.typography.bodyMedium, color = Club.colors.brass)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    ClubButton(
                        stringResource(if (ui.searching) R.string.home_searching else R.string.home_play),
                        vm::quickPlay, Modifier.fillMaxWidth(), loading = ui.searching, icon = Icons.Filled.PlayArrow, large = true,
                    )
                    AnimatedVisibility(ui.searching) {
                        TextButton(onClick = vm::cancelSearch, Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ClubOutlinedButton(stringResource(R.string.home_create_room), onCreateRoom, Modifier.weight(1f), Icons.Filled.AddCircleOutline)
                        ClubOutlinedButton(stringResource(R.string.home_join_code), { showCode = true }, Modifier.weight(1f), Icons.Filled.Pin)
                    }
                    Spacer(Modifier.height(8.dp))
                    ClubOutlinedButton(stringResource(R.string.home_watch), onOpenGames, Modifier.fillMaxWidth(), Icons.Filled.Visibility)
                }
            }
            if (ui.error != null && !ui.loading) item(span = { GridItemSpan(maxLineSpan) }) { ErrorState(ui.error!!, { vm.refresh() }) }
            if (ui.loading) items(3) { Skeleton(height = 120.dp) }
            if (ui.invites.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.home_invites)) }
                items(ui.invites, key = { it.id }) { inv -> InviteCard(inv, { vm.answerInvite(inv, true) }, { vm.answerInvite(inv, false) }) }
            }
            item {
                ClubCard(onClick = onTeams) {
                    Text(stringResource(R.string.home_my_team), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val t = ui.teams.firstOrNull()
                    if (t == null) {
                        Text(stringResource(R.string.home_no_team), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamEmblem(t.emblem, size = 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.team_members_count, t.members.size), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        ClubButton(stringResource(R.string.team_gather), { vm.startTeam(t) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Campaign)
                    }
                }
            }
            ui.qod?.let { q ->
                item {
                    var reveal by rememberSaveable { mutableStateOf(false) }
                    ClubCard(onClick = { reveal = !reveal }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lightbulb, null, tint = Club.colors.brass)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_qod), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            Text(q.topic, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(q.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (reveal) stringResource(R.string.home_qod_answer, q.answer) else stringResource(R.string.home_qod_tap),
                            style = MaterialTheme.typography.titleMedium, color = if (reveal) Club.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (ui.top.isNotEmpty()) item {
                ClubCard(onClick = onLeaderboard) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = Club.colors.brass)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_leaders), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    ui.top.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${e.place}", style = MaterialTheme.typography.titleMedium, color = Club.colors.brass, modifier = Modifier.width(28.dp))
                            PlayerAvatar(e.player.avatar, size = 32.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(e.player.nickname, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${e.rating}", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }

    if (showCode) JoinCodeDialog(onDismiss = { showCode = false }, onJoin = { showCode = false; vm.joinCode(it) })
}

@Composable
fun InviteCard(inv: Invite, onAccept: () -> Unit, onDecline: () -> Unit) {
    ClubCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(inv.from.avatar, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(inv.from.nickname, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (inv.kind) {
                        InviteKind.TEAM -> stringResource(R.string.invite_team, inv.teamName ?: "")
                        InviteKind.GAME -> stringResource(R.string.invite_game, inv.roomCode ?: "")
                        InviteKind.FRIEND -> stringResource(R.string.invite_friend)
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClubOutlinedButton(stringResource(R.string.action_decline), onDecline, Modifier.weight(1f))
            ClubButton(stringResource(R.string.action_accept), onAccept, Modifier.weight(1f))
        }
    }
}

@Composable
fun JoinCodeDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_join_code)) },
        text = {
            OutlinedTextField(
                code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) }, singleLine = true,
                label = { Text(stringResource(R.string.field_room_code)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
        },
        confirmButton = { TextButton(onClick = { onJoin(code) }, enabled = code.length in 4..6) { Text(stringResource(R.string.action_join)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Перевод AppException в текст для snackbar вне composable-контекста. */
@Composable
fun rememberErrorMessages(): (AppException) -> String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember(ctx) {
        { e: AppException -> errorRes(e.code)?.let(ctx::getString) ?: e.message.takeIf { it.isNotBlank() && it != e.code } ?: ctx.getString(R.string.err_generic) }
    }
}
