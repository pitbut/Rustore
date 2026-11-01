package uz.robutpit.znatoki.feature.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.robutpit.znatoki.R
import uz.robutpit.znatoki.core.AppException
import uz.robutpit.znatoki.core.Load
import uz.robutpit.znatoki.core.asApp
import uz.robutpit.znatoki.core.runLoad
import uz.robutpit.znatoki.data.repo.CacheRepository
import uz.robutpit.znatoki.design.Club
import uz.robutpit.znatoki.design.components.*
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.domain.model.*
import uz.robutpit.znatoki.feature.game.ReasonDialog
import uz.robutpit.znatoki.feature.home.rememberErrorMessages
import javax.inject.Inject

// ---------------- Друзья ----------------
@HiltViewModel
class FriendsViewModel @Inject constructor(private val gateway: GameGateway, private val cache: CacheRepository) : ViewModel() {
    val friends = MutableStateFlow<Load<List<Friend>>>(Load.Loading)
    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<PlayerShort>>(emptyList())
    val searching = MutableStateFlow(false)
    private val _msg = MutableSharedFlow<AppException>(extraBufferCapacity = 4)
    val errors: SharedFlow<AppException> = _msg
    private val _info = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val info: SharedFlow<Int> = _info
    private var searchJob: Job? = null

    init { refresh() }

    fun refresh() = viewModelScope.launch { cache.friends().collect { friends.value = it } }

    fun search(q: String) {
        query.value = q
        searchJob?.cancel()
        if (q.length < 2) { results.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(350) // debounce
            searching.value = true
            results.value = runCatching { gateway.searchPlayers(q) }.getOrDefault(emptyList())
            searching.value = false
        }
    }

    private fun act(info: Int? = null, block: suspend () -> Unit) = viewModelScope.launch {
        try { block(); info?.let { _info.emit(it) }; refresh() } catch (e: Exception) { _msg.emit(e.asApp()) }
    }

    fun request(p: PlayerShort) = act(R.string.friends_request_sent) { gateway.sendFriendRequest(p.id) }
    fun answer(f: Friend, accept: Boolean) = act { gateway.answerFriendRequest(f.requestId ?: return@act, accept) }
    fun remove(f: Friend) = act { gateway.removeFriend(f.player.id) }
    fun block(p: PlayerShort) = act(R.string.player_blocked) { gateway.block(p.id) }
    fun unblock(p: PlayerShort) = act { gateway.unblock(p.id) }
    fun report(p: PlayerShort, reason: String) = act(R.string.report_sent) { gateway.report("player", p.id, reason) }
    fun inviteToGame(p: PlayerShort, onRoom: (String) -> Unit) = viewModelScope.launch {
        try {
            val room = gateway.createRoom(RoomSettings(access = RoomAccess.CODE, teamSize = 6))
            gateway.inviteToGame(p.id, room)
            _info.emit(R.string.invite_sent)
            onRoom(room)
        } catch (e: Exception) { _msg.emit(e.asApp()) }
    }
}

@Composable
fun FriendsScreen(onRoom: (String) -> Unit, vm: FriendsViewModel = hiltViewModel()) {
    val friends by vm.friends.collectAsState()
    val q by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val searching by vm.searching.collectAsState()
    val snack = remember { SnackbarHostState() }
    val errText = rememberErrorMessages()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var menuFor by remember { mutableStateOf<Friend?>(null) }
    var reportFor by remember { mutableStateOf<PlayerShort?>(null) }
    LaunchedEffect(Unit) { vm.errors.collect { snack.showSnackbar(errText(it)) } }
    LaunchedEffect(Unit) { vm.info.collect { snack.showSnackbar(ctx.getString(it)) } }

    ClubScaffold(title = stringResource(R.string.friends_title), onBack = null, snackbar = snack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    q, vm::search, Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
                    label = { Text(stringResource(R.string.friends_search)) },
                )
            }
            if (q.length >= 2) {
                if (results.isEmpty() && !searching) item { Text(stringResource(R.string.friends_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(results, key = { "s" + it.id }) { p ->
                    val known = (friends as? Load.Ready)?.data?.firstOrNull { it.player.id == p.id }
                    PlayerRow(p) {
                        if (known == null) FilledTonalButton(onClick = { vm.request(p) }) { Text(stringResource(R.string.friends_add)) }
                        else Text(stringResource(stateLabel(known.state)), style = MaterialTheme.typography.labelMedium)
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            }
            when (val f = friends) {
                is Load.Loading -> items(4) { Skeleton(height = 64.dp) }
                is Load.Failed -> item { ErrorState(f.error, { vm.refresh() }) }
                is Load.Ready -> {
                    val incoming = f.data.filter { it.state == FriendState.INCOMING }
                    val list = f.data.filter { it.state == FriendState.FRIEND }.sortedByDescending { it.player.online }
                    val outgoing = f.data.filter { it.state == FriendState.OUTGOING }
                    val blocked = f.data.filter { it.state == FriendState.BLOCKED }
                    if (incoming.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.friends_incoming)) }
                        items(incoming, key = { "i" + it.player.id }) { fr ->
                            PlayerRow(fr.player) {
                                IconButton(onClick = { vm.answer(fr, false) }) { Icon(Icons.Filled.Close, stringResource(R.string.action_decline)) }
                                IconButton(onClick = { vm.answer(fr, true) }) { Icon(Icons.Filled.Check, stringResource(R.string.action_accept), tint = Club.colors.success) }
                            }
                        }
                    }
                    item { SectionTitle(stringResource(R.string.friends_list, list.size)) }
                    if (list.isEmpty()) item { EmptyState(stringResource(R.string.friends_empty), stringResource(R.string.friends_empty_hint), icon = Icons.Filled.PersonSearch) }
                    items(list, key = { "f" + it.player.id }) { fr ->
                        PlayerRow(fr.player, showOnline = true) {
                            IconButton(onClick = { vm.inviteToGame(fr.player, onRoom) }) { Icon(Icons.Filled.SportsEsports, stringResource(R.string.friends_invite_game)) }
                            IconButton(onClick = { menuFor = fr }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_player_menu)) }
                        }
                    }
                    if (outgoing.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.friends_outgoing)) }
                        items(outgoing, key = { "o" + it.player.id }) { fr -> PlayerRow(fr.player) { Text(stringResource(R.string.friends_pending), style = MaterialTheme.typography.labelMedium) } }
                    }
                    if (blocked.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.friends_blocked)) }
                        items(blocked, key = { "b" + it.player.id }) { fr -> PlayerRow(fr.player) { TextButton(onClick = { vm.unblock(fr.player) }) { Text(stringResource(R.string.action_unblock)) } } }
                    }
                }
            }
        }
    }
    menuFor?.let { fr ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(fr.player.nickname) },
            text = {
                Column {
                    TextButton(onClick = { vm.remove(fr); menuFor = null }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.friends_remove)) }
                    TextButton(onClick = { reportFor = fr.player; menuFor = null }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_report)) }
                    TextButton(onClick = { vm.block(fr.player); menuFor = null }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_block)) }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    reportFor?.let { p ->
        ReasonDialog(
            stringResource(R.string.action_report),
            listOf(R.string.reason_abuse, R.string.reason_cheating, R.string.reason_spam, R.string.reason_nickname).map { stringResource(it) },
            onDismiss = { reportFor = null }, onPick = { vm.report(p, it); reportFor = null },
        )
    }
}

private fun stateLabel(s: FriendState) = when (s) {
    FriendState.FRIEND -> R.string.friends_state_friend
    FriendState.INCOMING -> R.string.friends_state_incoming
    FriendState.OUTGOING -> R.string.friends_pending
    FriendState.BLOCKED -> R.string.friends_state_blocked
}

@Composable
fun PlayerRow(p: PlayerShort, showOnline: Boolean = false, actions: @Composable RowScope.() -> Unit) {
    ClubCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(p.avatar, size = 44.dp, online = !showOnline || p.online)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.nickname, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.player_rating, p.rating) + if (showOnline) " · " + stringResource(if (p.online) R.string.status_online else R.string.status_offline) else "",
                    style = MaterialTheme.typography.bodySmall, color = if (showOnline && p.online) Club.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            actions()
        }
    }
}

// ---------------- Команды ----------------
@HiltViewModel
class TeamsViewModel @Inject constructor(private val gateway: GameGateway) : ViewModel() {
    val teams = MutableStateFlow<Load<List<Team>>>(Load.Loading)
    val friends = MutableStateFlow<List<Friend>>(emptyList())
    private val _errors = MutableSharedFlow<AppException>(extraBufferCapacity = 4)
    val errors: SharedFlow<AppException> = _errors
    private val _info = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val info: SharedFlow<Int> = _info
    private val _room = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val room: SharedFlow<String> = _room

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        teams.value = runLoad { gateway.myTeams() }
        friends.value = runCatching { gateway.friends().filter { it.state == FriendState.FRIEND } }.getOrDefault(emptyList())
    }

    private fun act(info: Int? = null, block: suspend () -> Unit) = viewModelScope.launch {
        try { block(); info?.let { _info.emit(it) }; teams.value = runLoad { gateway.myTeams() } } catch (e: Exception) { _errors.emit(e.asApp()) }
    }

    fun create(name: String, emblem: Int) = act { gateway.createTeam(name, emblem) }
    fun invite(t: Team, p: PlayerShort) = act(R.string.invite_sent) { gateway.inviteToTeam(t.id, p.id) }
    fun setCaptain(t: Team, p: PlayerShort) = act { gateway.updateTeam(t.id, captainId = p.id) }
    fun removeMember(t: Team, p: PlayerShort) = act { gateway.removeTeamMember(t.id, p.id) }
    fun leave(t: Team) = act { gateway.leaveTeam(t.id) }
    fun gather(t: Team) = viewModelScope.launch {
        try { _info.emit(R.string.team_gather_sent); _room.emit(gateway.startTeamRoom(t.id)) } catch (e: Exception) { _errors.emit(e.asApp()) }
    }
}

@Composable
fun TeamsScreen(onBack: () -> Unit, onRoom: (String) -> Unit, myId: String, vm: TeamsViewModel = hiltViewModel()) {
    val teams by vm.teams.collectAsState()
    val friends by vm.friends.collectAsState()
    val snack = remember { SnackbarHostState() }
    val errText = rememberErrorMessages()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var createOpen by rememberSaveable { mutableStateOf(false) }
    var inviteFor by remember { mutableStateOf<Team?>(null) }
    var leaveFor by remember { mutableStateOf<Team?>(null) }
    LaunchedEffect(Unit) { vm.errors.collect { snack.showSnackbar(errText(it)) } }
    LaunchedEffect(Unit) { vm.info.collect { snack.showSnackbar(ctx.getString(it)) } }
    LaunchedEffect(Unit) { vm.room.collect(onRoom) }

    ClubScaffold(
        title = stringResource(R.string.teams_title), onBack = onBack, snackbar = snack,
        floating = { ExtendedFloatingActionButton(onClick = { createOpen = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.teams_create)) }) },
    ) { pad ->
        when (val t = teams) {
            is Load.Loading -> SkeletonList(3, Modifier.padding(pad))
            is Load.Failed -> ErrorState(t.error, { vm.refresh() }, Modifier.padding(pad))
            is Load.Ready -> if (t.data.isEmpty()) EmptyState(stringResource(R.string.teams_empty), stringResource(R.string.teams_empty_hint), Modifier.padding(pad), Icons.Filled.Groups)
            else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(t.data, key = { it.id }) { team ->
                    ClubCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamEmblem(team.emblem, size = 56.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(team.name, style = MaterialTheme.typography.headlineSmall)
                                Text(stringResource(R.string.team_rating, team.rating), style = MaterialTheme.typography.bodySmall, color = Club.colors.brass)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        team.members.forEach { m ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                PlayerAvatar(m.avatar, size = 36.dp, captain = m.id == team.captainId)
                                Spacer(Modifier.width(10.dp))
                                Text(m.nickname + if (m.id == team.ownerId) " · " + stringResource(R.string.team_owner) else "", Modifier.weight(1f))
                                if (team.ownerId == myId && m.id != myId) {
                                    var open by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_player_menu)) }
                                        DropdownMenu(open, { open = false }) {
                                            if (m.id != team.captainId) DropdownMenuItem(text = { Text(stringResource(R.string.player_make_captain)) }, onClick = { open = false; vm.setCaptain(team, m) })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.team_remove_member)) }, onClick = { open = false; vm.removeMember(team, m) })
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        ClubButton(stringResource(R.string.team_gather), { vm.gather(team) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Campaign)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ClubOutlinedButton(stringResource(R.string.action_invite), { inviteFor = team }, Modifier.weight(1f), Icons.Filled.PersonAdd, enabled = team.members.size < 6)
                            ClubOutlinedButton(stringResource(R.string.team_leave), { leaveFor = team }, Modifier.weight(1f), Icons.Filled.Logout)
                        }
                    }
                }
            }
        }
    }

    if (createOpen) {
        var name by rememberSaveable { mutableStateOf("") }
        var emblem by rememberSaveable { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { createOpen = false },
            title = { Text(stringResource(R.string.teams_create)) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(30) }, singleLine = true, label = { Text(stringResource(R.string.team_name)) })
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.team_emblem), style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((0 until 10).toList()) { i ->
                            FilterChip(selected = emblem == i, onClick = { emblem = i }, label = { TeamEmblem(i, size = 36.dp) }, modifier = Modifier.heightIn(min = 56.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { createOpen = false; vm.create(name, emblem) }, enabled = name.trim().length >= 2) { Text(stringResource(R.string.action_create)) } },
            dismissButton = { TextButton(onClick = { createOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    inviteFor?.let { team ->
        AlertDialog(
            onDismissRequest = { inviteFor = null },
            title = { Text(stringResource(R.string.team_invite_title)) },
            text = {
                val candidates = friends.filter { f -> team.members.none { it.id == f.player.id } }
                if (candidates.isEmpty()) Text(stringResource(R.string.team_invite_none))
                else Column { candidates.forEach { f -> TextButton(onClick = { vm.invite(team, f.player); inviteFor = null }, Modifier.fillMaxWidth()) { Text(f.player.nickname) } } }
            },
            confirmButton = { TextButton(onClick = { inviteFor = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    leaveFor?.let { team ->
        AlertDialog(
            onDismissRequest = { leaveFor = null },
            title = { Text(stringResource(R.string.team_leave_confirm, team.name)) },
            confirmButton = { TextButton(onClick = { vm.leave(team); leaveFor = null }) { Text(stringResource(R.string.team_leave)) } },
            dismissButton = { TextButton(onClick = { leaveFor = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
