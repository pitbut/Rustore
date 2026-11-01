package uz.robutpit.znatoki.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
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
import uz.robutpit.znatoki.core.Load
import uz.robutpit.znatoki.core.asApp
import uz.robutpit.znatoki.core.runLoad
import uz.robutpit.znatoki.data.repo.AuthState
import uz.robutpit.znatoki.data.repo.SessionRepository
import uz.robutpit.znatoki.design.Club
import uz.robutpit.znatoki.design.components.*
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.domain.model.*
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(private val session: SessionRepository, private val gateway: GameGateway) : ViewModel() {
    val profile: StateFlow<Profile?> = session.auth.map { (it as? AuthState.LoggedIn)?.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, session.profile)
    val history = MutableStateFlow<Load<List<GameHistoryItem>>>(Load.Loading)
    val error = MutableSharedFlow<AppException>(extraBufferCapacity = 2)

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { session.refreshProfile() }
        history.value = runLoad { gateway.history() }
    }

    fun update(nick: String? = null, avatar: Int? = null) = viewModelScope.launch {
        try { session.updateProfile(nick, avatar) } catch (e: Exception) { error.emit(e.asApp()) }
    }
}

@Composable
fun ProfileScreen(onLeaderboard: () -> Unit, onSettings: () -> Unit, onTeams: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val p by vm.profile.collectAsState()
    val history by vm.history.collectAsState()
    var editNick by rememberSaveable { mutableStateOf(false) }
    var pickAvatar by rememberSaveable { mutableStateOf(false) }
    ClubScaffold(
        title = stringResource(R.string.profile_title), onBack = null,
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings_title)) } },
    ) { pad ->
        val profile = p
        if (profile == null) { SkeletonList(4, Modifier.padding(pad)); return@ClubScaffold }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ClubCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            PlayerAvatar(profile.avatar, size = 84.dp, contentDescription = stringResource(R.string.profile_avatar))
                            SmallFloatingActionButton(onClick = { pickAvatar = true }, Modifier.align(Alignment.BottomEnd).size(32.dp)) { Icon(Icons.Filled.Edit, stringResource(R.string.profile_change_avatar), Modifier.size(16.dp)) }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(profile.nickname, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                                IconButton(onClick = { editNick = true }) { Icon(Icons.Filled.Edit, stringResource(R.string.profile_edit_nick)) }
                            }
                            Text(stringResource(R.string.profile_rating_season, profile.rating, profile.season), style = MaterialTheme.typography.titleMedium, color = Club.colors.brass)
                            if (!profile.emailVerified) Text(stringResource(R.string.profile_unverified), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                val s = profile.stats
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatPill(stringResource(R.string.stat_games), "${s.games}", Modifier.weight(1f))
                    StatPill(stringResource(R.string.stat_wins), "${s.wins}", Modifier.weight(1f))
                    StatPill(stringResource(R.string.stat_correct), "${s.correctPercent}%", Modifier.weight(1f))
                    StatPill(stringResource(R.string.stat_captain), "${s.asCaptain}", Modifier.weight(1f))
                }
            }
            if (profile.stats.favoriteTopics.isNotEmpty()) item {
                ClubCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_topics), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { profile.stats.favoriteTopics.forEach { AssistChip(onClick = {}, label = { Text(it) }) } }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ClubOutlinedButton(stringResource(R.string.leaderboard_title), onLeaderboard, Modifier.weight(1f), Icons.Filled.EmojiEvents)
                    ClubOutlinedButton(stringResource(R.string.teams_title), onTeams, Modifier.weight(1f), Icons.Filled.Groups)
                }
            }
            item { SectionTitle(stringResource(R.string.profile_achievements)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profile.achievements.forEach { a ->
                        ClubCard(Modifier.width(160.dp).alpha(if (a.unlocked) 1f else 0.45f)) {
                            Icon(if (a.unlocked) Icons.Filled.MilitaryTech else Icons.Filled.Lock, null, tint = Club.colors.brass)
                            Text(a.title, style = MaterialTheme.typography.titleSmall)
                            Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.profile_history)) }
            when (val h = history) {
                is Load.Loading -> items(3) { Skeleton(height = 56.dp) }
                is Load.Failed -> item { ErrorState(h.error, { vm.refresh() }) }
                is Load.Ready -> if (h.data.isEmpty()) item { EmptyState(stringResource(R.string.profile_history_empty)) } else items(h.data, key = { it.id }) { g ->
                    ClubCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (g.experts >= 6) Icons.Filled.EmojiEvents else Icons.Filled.SentimentDissatisfied, null, tint = if (g.experts >= 6) Club.colors.brass else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${g.experts} : ${g.viewers}", style = MaterialTheme.typography.titleMedium)
                                Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(g.date)) + if (g.asCaptain) " · " + stringResource(R.string.player_captain) else "", style = MaterialTheme.typography.bodySmall)
                            }
                            Text((if (g.ratingDelta >= 0) "+" else "") + g.ratingDelta, color = if (g.ratingDelta >= 0) Club.colors.success else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
    if (editNick) {
        var nick by rememberSaveable { mutableStateOf(p?.nickname ?: "") }
        AlertDialog(
            onDismissRequest = { editNick = false },
            title = { Text(stringResource(R.string.profile_edit_nick)) },
            text = { OutlinedTextField(nick, { nick = it.take(24) }, singleLine = true, label = { Text(stringResource(R.string.field_nickname)) }) },
            confirmButton = { TextButton(onClick = { vm.update(nick = nick.trim()); editNick = false }, enabled = nick.trim().length >= 2) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editNick = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (pickAvatar) {
        AlertDialog(
            onDismissRequest = { pickAvatar = false },
            title = { Text(stringResource(R.string.profile_change_avatar)) },
            text = {
                LazyVerticalGrid(GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items((0 until AVATAR_COUNT).toList()) { i ->
                        FilledTonalIconButton(onClick = { vm.update(avatar = i); pickAvatar = false }, Modifier.size(64.dp)) {
                            PlayerAvatar(i, size = 56.dp, contentDescription = stringResource(R.string.cd_avatar_n, i + 1))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickAvatar = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

// ---------------- Рейтинг ----------------
@HiltViewModel
class LeaderboardViewModel @Inject constructor(private val gateway: GameGateway) : ViewModel() {
    val scope = MutableStateFlow(LeaderboardScope.GLOBAL)
    val data = MutableStateFlow<Load<List<LeaderboardEntry>>>(Load.Loading)
    init { load(LeaderboardScope.GLOBAL) }
    fun load(s: LeaderboardScope) = viewModelScope.launch { scope.value = s; data.value = Load.Loading; data.value = runLoad { gateway.leaderboard(s) } }
}

@Composable
fun LeaderboardScreen(onBack: () -> Unit, myId: String, vm: LeaderboardViewModel = hiltViewModel()) {
    val scope by vm.scope.collectAsState()
    val data by vm.data.collectAsState()
    val tabs = listOf(LeaderboardScope.GLOBAL to R.string.lb_global, LeaderboardScope.FRIENDS to R.string.lb_friends, LeaderboardScope.TEAM to R.string.lb_team, LeaderboardScope.SEASON to R.string.lb_season)
    ClubScaffold(title = stringResource(R.string.leaderboard_title), onBack = onBack) { pad ->
        Column(Modifier.padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == scope }, edgePadding = 16.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                tabs.forEach { (s, label) -> Tab(selected = s == scope, onClick = { vm.load(s) }, text = { Text(stringResource(label)) }) }
            }
            when (val d = data) {
                is Load.Loading -> SkeletonList(6)
                is Load.Failed -> ErrorState(d.error, { vm.load(scope) })
                is Load.Ready -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(d.data, key = { it.player.id }) { e ->
                        val me = e.player.id == myId
                        ClubCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${e.place}", style = MaterialTheme.typography.headlineSmall, color = if (e.place <= 3) Club.colors.brass else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                                PlayerAvatar(e.player.avatar, size = 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.player.nickname + if (me) " (${stringResource(R.string.player_you)})" else "", style = MaterialTheme.typography.titleMedium, color = if (me) Club.colors.brass else MaterialTheme.colorScheme.onSurface)
                                    Text(stringResource(R.string.lb_games, e.games), style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${e.rating}", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
