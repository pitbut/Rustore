package uz.robutpit.znatoki.feature.lobby

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import javax.inject.Inject

// ---------------- Создание комнаты ----------------
@HiltViewModel
class CreateRoomViewModel @Inject constructor(private val gateway: GameGateway, cache: CacheRepository) : ViewModel() {
    val settings = MutableStateFlow(RoomSettings(rated = false))
    val packs: StateFlow<Load<List<QuestionPack>>> = cache.packs().stateIn(viewModelScope, SharingStarted.Eagerly, Load.Loading)
    val creating = MutableStateFlow(false)
    val error = MutableStateFlow<AppException?>(null)
    private val _created = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val created: SharedFlow<String> = _created

    fun update(f: (RoomSettings) -> RoomSettings) = settings.update(f)

    fun create() {
        if (creating.value) return
        creating.value = true; error.value = null
        viewModelScope.launch {
            try { _created.emit(gateway.createRoom(settings.value)) } catch (e: Exception) { error.value = e.asApp() }
            creating.value = false
        }
    }
}

@Composable
fun CreateRoomScreen(onBack: () -> Unit, onCreated: (String) -> Unit, vm: CreateRoomViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsState()
    val creating by vm.creating.collectAsState()
    val error by vm.error.collectAsState()
    val packs by vm.packs.collectAsState()
    LaunchedEffect(Unit) { vm.created.collect(onCreated) }
    ClubScaffold(title = stringResource(R.string.create_title), onBack = onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingGroup(stringResource(R.string.create_access)) {
                ChipRow(
                    listOf(RoomAccess.OPEN to R.string.access_open, RoomAccess.CODE to R.string.access_code, RoomAccess.TEAM to R.string.access_team),
                    s.access,
                ) { a -> vm.update { it.copy(access = a) } }
            }
            SettingGroup(stringResource(R.string.create_team_size, s.teamSize)) {
                Slider(s.teamSize.toFloat(), { v -> vm.update { it.copy(teamSize = v.toInt()) } }, valueRange = 1f..6f, steps = 4)
            }
            SwitchRow(stringResource(R.string.create_bots), s.fillWithBots) { b -> vm.update { it.copy(fillWithBots = b) } }
            if (s.fillWithBots) SettingGroup(stringResource(R.string.create_bot_level)) {
                ChipRow(listOf(BotLevel.NOVICE to R.string.bot_novice, BotLevel.MEDIUM to R.string.bot_medium, BotLevel.ERUDITE to R.string.bot_erudite), s.botLevel) { l -> vm.update { it.copy(botLevel = l) } }
            }
            SettingGroup(stringResource(R.string.create_lang)) {
                ChipRow(listOf("ru" to R.string.lang_ru, "uz" to R.string.lang_uz), s.lang) { l -> vm.update { it.copy(lang = l) } }
            }
            SettingGroup(stringResource(R.string.create_difficulty, s.difficulty)) {
                Slider(s.difficulty.toFloat(), { v -> vm.update { it.copy(difficulty = v.toInt()) } }, valueRange = 1f..5f, steps = 3)
            }
            SettingGroup(stringResource(R.string.create_timer)) {
                ChipRow(listOf(30 to R.string.sec_30, 60 to R.string.sec_60, 90 to R.string.sec_90), s.discussSec) { t -> vm.update { it.copy(discussSec = t) } }
            }
            SettingGroup(stringResource(R.string.create_sectors)) {
                ChipRow(listOf(7 to R.string.sectors_7, 13 to R.string.sectors_13), s.sectors) { n -> vm.update { it.copy(sectors = n) } }
            }
            SettingGroup(stringResource(R.string.create_extra_limit, s.extraMinuteLimit)) {
                Slider(s.extraMinuteLimit.toFloat(), { v -> vm.update { it.copy(extraMinuteLimit = v.toInt()) } }, valueRange = 0f..3f, steps = 2)
            }
            SwitchRow(stringResource(R.string.create_audience), s.audienceHelp) { b -> vm.update { it.copy(audienceHelp = b) } }
            SwitchRow(stringResource(R.string.create_rated), s.rated) { b -> vm.update { it.copy(rated = b) } }
            SwitchRow(stringResource(R.string.create_spectators), s.allowSpectators) { b -> vm.update { it.copy(allowSpectators = b) } }
            if (s.allowSpectators) SwitchRow(stringResource(R.string.create_spectators_hear), s.spectatorsHearTeam, stringResource(R.string.create_spectators_hear_sub)) { b -> vm.update { it.copy(spectatorsHearTeam = b) } }
            SwitchRow(stringResource(R.string.create_superblitz_captain), s.superblitzByCaptain) { b -> vm.update { it.copy(superblitzByCaptain = b) } }
            SwitchRow(stringResource(R.string.create_special13), s.special13) { b -> vm.update { it.copy(special13 = b) } }
            SettingGroup(stringResource(R.string.create_pack)) {
                when (val p = packs) {
                    is Load.Ready -> {
                        val topics = p.data.filter { it.category == "topic" && it.id.startsWith("topic-") }
                        Text(stringResource(R.string.create_topics), style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            topics.forEach { t ->
                                Chip(t.title, t.title in s.topics, {
                                    vm.update { st -> st.copy(topics = if (t.title in st.topics) st.topics - t.title else st.topics + t.title) }
                                })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.create_pack_pick), style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(stringResource(R.string.create_pack_any), s.packId == null, { vm.update { it.copy(packId = null) } })
                            p.data.filter { it.category != "topic" }.forEach { pk ->
                                Chip(pk.title, s.packId == pk.id, { vm.update { it.copy(packId = pk.id) } })
                            }
                        }
                    }
                    is Load.Loading -> Skeleton(height = 48.dp)
                    is Load.Failed -> Text(errorText(p.error.code), color = MaterialTheme.colorScheme.error)
                }
            }
            error?.let { Text(errorText(it.code, it.message), color = MaterialTheme.colorScheme.error) }
            ClubButton(stringResource(R.string.create_action), vm::create, Modifier.fillMaxWidth(), loading = creating, large = true)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    ClubCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, Int>>, selected: T, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) -> Chip(stringResource(label), v == selected, { onPick(v) }) }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    ClubCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Switch(checked, onChange)
        }
    }
}

// ---------------- Лобби (внутри экрана комнаты) ----------------
@Composable
fun LobbyContent(
    room: RoomState,
    myId: String,
    isHost: Boolean,
    chat: List<ChatMessage>,
    onReady: (Boolean) -> Unit,
    onStart: () -> Unit,
    onAddBot: (BotLevel) -> Unit,
    onPlayerMenu: (PlayerInRoom) -> Unit,
    onInvite: () -> Unit,
    chatPanel: @Composable () -> Unit,
) {
    val me = room.players.firstOrNull { it.id == myId }
    val s = room.settings
    ClubCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.lobby_code), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(room.code, style = MaterialTheme.typography.displaySmall, color = Club.colors.brass)
            }
            ClubOutlinedButton(stringResource(R.string.action_invite), onInvite, icon = Icons.Filled.PersonAdd)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.lobby_summary, s.discussSec, s.sectors, s.difficulty) +
                (if (s.rated) " · " + stringResource(R.string.lobby_rated) else " · " + stringResource(R.string.lobby_training)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ClubCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.lobby_players, room.players.size, s.teamSize), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (isHost && room.players.size < s.teamSize) {
                var open by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { open = true }) { Icon(Icons.Filled.SmartToy, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.lobby_add_bot)) }
                    DropdownMenu(open, { open = false }) {
                        listOf(BotLevel.NOVICE to R.string.bot_novice, BotLevel.MEDIUM to R.string.bot_medium, BotLevel.ERUDITE to R.string.bot_erudite).forEach { (l, r) ->
                            DropdownMenuItem(text = { Text(stringResource(r)) }, onClick = { open = false; onAddBot(l) })
                        }
                    }
                }
            }
        }
        room.players.forEach { p ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(p.avatar, size = 44.dp, isBot = p.isBot, captain = p.isCaptain, online = p.online)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (p.id == myId) "${p.nickname} (${stringResource(R.string.player_you)})" else p.nickname, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            if (p.isCaptain) stringResource(R.string.player_captain) else null,
                            if (p.isBot) stringResource(R.string.player_bot) else null,
                            stringResource(R.string.player_rating, p.rating),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (p.ready) Icons.Filled.CheckCircle else Icons.Filled.HourglassEmpty,
                    stringResource(if (p.ready) R.string.lobby_ready else R.string.lobby_not_ready),
                    tint = if (p.ready) Club.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (p.id != myId) IconButton(onClick = { onPlayerMenu(p) }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_player_menu)) }
            }
        }
    }
    if (me != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ClubOutlinedButton(
                stringResource(if (me.ready) R.string.lobby_unready else R.string.lobby_set_ready), { onReady(!me.ready) }, Modifier.weight(1f),
                icon = if (me.ready) Icons.Filled.Close else Icons.Filled.Check,
            )
            if (isHost) ClubButton(stringResource(R.string.lobby_start), onStart, Modifier.weight(1f), icon = Icons.Filled.PlayArrow)
        }
        if (isHost && room.players.any { !it.ready && it.id != myId }) Text(stringResource(R.string.lobby_not_all_ready), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ClubCard(Modifier.fillMaxWidth().height(320.dp)) {
        Text(stringResource(R.string.chat_team), style = MaterialTheme.typography.titleMedium)
        chatPanel()
    }
}

// ---------------- Открытые игры (зрители) ----------------
@HiltViewModel
class OpenGamesViewModel @Inject constructor(private val gateway: GameGateway) : ViewModel() {
    val rooms = MutableStateFlow<Load<List<OpenRoom>>>(Load.Loading)
    init { refresh() }
    fun refresh() = viewModelScope.launch { rooms.value = Load.Loading; rooms.value = runLoad { gateway.openRooms() } }
}

@Composable
fun OpenGamesScreen(onBack: () -> Unit, onWatch: (String) -> Unit, onJoin: (String) -> Unit, vm: OpenGamesViewModel = hiltViewModel()) {
    val st by vm.rooms.collectAsState()
    ClubScaffold(title = stringResource(R.string.open_games_title), onBack = onBack, actions = {
        IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh)) }
    }) { pad ->
        when (val s = st) {
            is Load.Loading -> SkeletonList(4, Modifier.padding(pad))
            is Load.Failed -> ErrorState(s.error, { vm.refresh() }, Modifier.padding(pad))
            is Load.Ready -> if (s.data.isEmpty()) EmptyState(stringResource(R.string.open_games_empty), modifier = Modifier.padding(pad))
            else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.data, key = { it.roomId }) { r ->
                    ClubCard(Modifier.fillMaxWidth(), onClick = { if (r.inGame) onWatch(r.roomId) else onJoin(r.roomId) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    stringResource(R.string.open_games_line, r.players, r.teamSize, r.spectators),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (r.inGame) Text("${r.score.experts} : ${r.score.viewers}", style = MaterialTheme.typography.headlineSmall, color = Club.colors.brass)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(if (r.inGame) R.string.open_games_watch else R.string.open_games_join),
                            style = MaterialTheme.typography.labelLarge, color = Club.colors.brass,
                        )
                    }
                }
            }
        }
    }
}
