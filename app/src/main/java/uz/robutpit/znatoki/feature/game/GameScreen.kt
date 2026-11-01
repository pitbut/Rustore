package uz.robutpit.znatoki.feature.game

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import uz.robutpit.znatoki.R
import uz.robutpit.znatoki.core.LocaleHelper
import uz.robutpit.znatoki.data.local.MicMode
import uz.robutpit.znatoki.design.Club
import uz.robutpit.znatoki.design.components.*
import uz.robutpit.znatoki.domain.ConnectionState
import uz.robutpit.znatoki.domain.model.*
import uz.robutpit.znatoki.voice.SpeechInput

@Composable
fun GameScreen(onExit: () -> Unit, vm: GameViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val serverNow: () -> Long = remember { { vm.clock.serverNow() } }
    var confirmLeave by remember { mutableStateOf(false) }
    var chatOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<PlayerInRoom?>(null) }
    var dismissedVote by remember { mutableStateOf<String?>(null) }
    var audienceDismissedAt by remember { mutableLongStateOf(0L) }
    var askMic by remember { mutableStateOf(false) }
    var inviteOpen by remember { mutableStateOf(false) }
    val speech = remember { SpeechInput(ctx) }
    DisposableEffect(Unit) { onDispose { speech.stop() } }

    // ---- разрешение на микрофон (с объяснением) ----
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onMicPermission(it) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(ui.isSpectator) {
        if (!ui.isSpectator) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (granted) vm.onMicPermission(true) else askMic = true
        }
    }

    // ---- события ----
    val msgExtra = stringResource(R.string.vote_extra_result)
    val msgKickOk = stringResource(R.string.vote_kick_passed)
    val msgFailed = stringResource(R.string.vote_failed)
    val msgKickedMe = stringResource(R.string.kicked_me)
    val msgMuted = stringResource(R.string.mic_muted_by_host)
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is GameEvent.Error -> snack.showSnackbar(if (e.code == "mic_muted_by_host") msgMuted else errorRes(e.code)?.let(ctx::getString) ?: ctx.getString(R.string.err_generic))
                is GameEvent.VoteDone -> snack.showSnackbar(
                    if (!e.result.passed) msgFailed else if (e.result.kind == VoteKind.KICK) msgKickOk else msgExtra
                )
                GameEvent.KickedOut -> { snack.showSnackbar(msgKickedMe); vm.leave(); onExit() }
                else -> Unit
            }
        }
    }

    BackHandler { confirmLeave = true }

    val room = ui.room
    ClubBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snack) },
            topBar = { GameTopBar(ui, onLeave = { confirmLeave = true }, onShare = { code -> shareCode(ctx, code) }) },
            bottomBar = {
                if (room != null && room.phase.phase != Phase.LOBBY && room.phase.phase != Phase.GAME_OVER) {
                    ActionBar(
                        ui = ui, serverNow = serverNow,
                        onMic = vm::toggleMic, onPtt = vm::pushToTalk,
                        onReady = vm::readyAnswer, onExtra = vm::extraMinute, onAudience = vm::callAudience,
                        onChat = { chatOpen = true },
                        onOpenAudience = { audienceDismissedAt = 0L },
                    )
                }
            },
        ) { pad ->
            if (room == null) {
                Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = Club.colors.brass)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.room_connecting))
                }
                return@Scaffold
            }
            BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
                val wide = maxWidth > maxHeight && maxWidth >= 600.dp
                val stage: @Composable (Modifier) -> Unit = { m ->
                    Stage(
                        ui = ui, vm = vm, serverNow = serverNow, reduceMotion = reduceMotion, modifier = m,
                        onVoice = { cb ->
                            val lang = LocaleHelper.current(ctx)
                            speech.start(lang, onPartial = {}, onResult = { it?.let(cb) })
                        },
                        voiceAvailable = speech.available, onExit = { vm.leave(); onExit() },
                        onPlayerMenu = { menuFor = it }, onInvite = { inviteOpen = true },
                    )
                }
                val team: @Composable () -> Unit = {
                    if (room.phase.phase != Phase.LOBBY) TeamPanel(room.players, ui.speaking, ui.myId, room.superblitzPlayerId, { menuFor = it })
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        stage(Modifier.weight(1.3f).fillMaxHeight())
                        Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                            ScoreBoard(room.score, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            team()
                            if (room.phase.phase != Phase.LOBBY) {
                                ChatPanel(
                                    ui.chat.filter { it.scope == if (ui.isSpectator) "hall" else "team" }, ui.myId,
                                    onSend = { vm.chat(if (ui.isSpectator) "hall" else "team", it) },
                                    onReport = vm::reportMessage, onBlock = { vm.blockPlayer(it.authorId) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (room.phase.phase != Phase.LOBBY) {
                            ScoreBoard(room.score, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                            team()
                        }
                        stage(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }

    // ---- диалоги ----
    if (askMic) AlertDialog(
        onDismissRequest = { askMic = false },
        icon = { Icon(Icons.Filled.Mic, null) },
        title = { Text(stringResource(R.string.mic_rationale_title)) },
        text = { Text(stringResource(R.string.mic_rationale_text)) },
        confirmButton = {
            TextButton(onClick = {
                askMic = false; micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                if (android.os.Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text(stringResource(R.string.action_allow)) }
        },
        dismissButton = { TextButton(onClick = { askMic = false }) { Text(stringResource(R.string.mic_without)) } },
    )

    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(stringResource(R.string.leave_title)) },
        text = { Text(stringResource(R.string.leave_text)) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave(); onExit() }) { Text(stringResource(R.string.action_leave)) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.action_stay)) } },
    )

    if (chatOpen && room != null) {
        ModalBottomSheet(onDismissRequest = { chatOpen = false }) {
            val scopeName = if (ui.isSpectator) "hall" else "team"
            Text(stringResource(if (ui.isSpectator) R.string.chat_hall else R.string.chat_team), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            ChatPanel(
                ui.chat.filter { it.scope == scopeName }, ui.myId, onSend = { vm.chat(scopeName, it) },
                onReport = vm::reportMessage, onBlock = { vm.blockPlayer(it.authorId) },
                modifier = Modifier.fillMaxWidth().height(420.dp).imePadding(),
            )
        }
    }

    room?.vote?.let { v ->
        val eligible = !ui.isSpectator && v.targetId != ui.myId
        if (eligible && dismissedVote != v.voteId) VoteDialog(
            vote = v, targetName = room.players.firstOrNull { it.id == v.targetId }?.nickname, serverNow = serverNow,
            onVote = { vm.castVote(v.voteId, it) }, onDismiss = { dismissedVote = v.voteId },
        )
    }

    if (room != null && ui.isSpectator && room.audienceEndsAt > serverNow() && audienceDismissedAt != room.audienceEndsAt) {
        AudienceHelpDialog(
            room.audience, room.audienceEndsAt, serverNow, ui.myId,
            onSuggest = vm::suggest, onVote = vm::voteSuggestion, onDismiss = { audienceDismissedAt = room.audienceEndsAt },
        )
    }

    menuFor?.let { p ->
        PlayerMenu(
            p, ui,
            onDismiss = { menuFor = null },
            onCaptain = { vm.setCaptain(p.id); menuFor = null },
            onKick = { vm.kick(p.id); menuFor = null },
            onReport = { vm.reportPlayer(p.id, "behaviour"); menuFor = null; scope.launch { snack.showSnackbar(ctx.getString(R.string.report_sent)) } },
            onBlock = { vm.blockPlayer(p.id); menuFor = null },
        )
    }

    if (inviteOpen) InviteFriendsDialog(vm, onDismiss = { inviteOpen = false })
}

private fun shareCode(ctx: android.content.Context, code: String) {
    val text = ctx.getString(R.string.share_room_text, code, "https://game.robutpit.com/join/$code")
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

@Composable
private fun GameTopBar(ui: GameUi, onLeave: () -> Unit, onShare: (String) -> Unit) {
    val room = ui.room
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onLeave) { Icon(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.action_leave)) }
        Column(Modifier.weight(1f)) {
            Text(room?.title ?: stringResource(R.string.room_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (txt, col) = when (ui.connection) {
                    ConnectionState.CONNECTED -> stringResource(R.string.conn_ok) to Club.colors.success
                    ConnectionState.RECONNECTING -> stringResource(R.string.conn_reconnecting) to Club.colors.brass
                    else -> stringResource(R.string.conn_connecting) to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(Modifier.size(8.dp).background(col, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(txt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (room != null) {
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.Visibility, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" ${room.spectators}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (room != null) {
            AssistChip(onClick = { onShare(room.code) }, label = { Text(room.code) }, leadingIcon = { Icon(Icons.Filled.Share, null, Modifier.size(16.dp)) },
                modifier = Modifier.semantics { contentDescription = "Share ${room.code}" })
        }
    }
}


@Composable
private fun Stage(
    ui: GameUi,
    vm: GameViewModel,
    serverNow: () -> Long,
    reduceMotion: Boolean,
    modifier: Modifier,
    onVoice: ((String) -> Unit) -> Unit,
    voiceAvailable: Boolean,
    onExit: () -> Unit,
    onPlayerMenu: (PlayerInRoom) -> Unit,
    onInvite: () -> Unit,
) {
    val room = ui.room ?: return
    val phase = room.phase.phase
    val group = when (phase) {
        Phase.LOBBY -> 0
        Phase.SPIN, Phase.INTRO -> 1
        Phase.DISCUSS, Phase.CHOOSE_ANSWERER, Phase.ANSWER, Phase.CHECK, Phase.VOTE_EXTRA_MINUTE, Phase.AUDIENCE_HELP -> 2
        Phase.REVEAL, Phase.SCORE -> 3
        Phase.GAME_OVER -> 4
        Phase.PAUSED -> 5
    }
    AnimatedContent(
        targetState = group,
        transitionSpec = {
            if (reduceMotion) fadeIn(tween(120)).togetherWith(fadeOut(tween(120)))
            else (slideInVertically(tween(420)) { it / 3 } + fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 0.92f))
                .togetherWith(fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.04f))
        },
        modifier = modifier, label = "stage",
    ) { g ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (g) {
                0 -> LobbyPanel(ui, vm, onPlayerMenu, onInvite)
                1 -> SpinStage(ui, vm, serverNow, reduceMotion)
                2 -> QuestionStage(ui, vm, serverNow, onVoice, voiceAvailable)
                3 -> room.reveal?.let { r ->
                    RevealCard(
                        r, room.question, canAppeal = r.appealable && !ui.isSpectator, onAppeal = vm::appeal,
                        feedbackDone = r.questionId in ui.feedbackSent, onFeedback = { fb -> r.questionId?.let { vm.feedback(it, fb) } },
                        showFeedback = !ui.isSpectator,
                    )
                    if (room.question?.type == SectorType.BLACKBOX) BlackBox(open = true, Modifier.fillMaxWidth(0.5f))
                }
                4 -> room.gameOver?.let { GameOverCard(it, room.score, ui.myId, onExit) }
                else -> ClubCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.paused_title), style = MaterialTheme.typography.headlineSmall)
                    Text(room.pausedReason ?: stringResource(R.string.paused_text), style = MaterialTheme.typography.bodyMedium)
                    if (room.phase.endsAt > 0) CircularTimer(room.phase.endsAt, 180_000, serverNow, size = 64.dp)
                }
            }
        }
    }
}

@Composable
private fun SpinStage(ui: GameUi, vm: GameViewModel, serverNow: () -> Long, reduceMotion: Boolean) {
    val room = ui.room ?: return
    val phase = room.phase.phase
    val highlight = if (phase == Phase.INTRO) room.phase.sector.takeIf { it >= 0 } ?: room.spin?.sectorIndex else null
    val spinInFlight = room.spin?.let { serverNow() - it.startedAt < it.durationMs } == true
    RouletteTable(
        room.sectors, room.spin, serverNow, highlight, vm.sound, ui.settings.spinEffects, reduceMotion,
        Modifier.fillMaxWidth().widthIn(max = 480.dp),
    )
    if (phase == Phase.SPIN) {
        if (ui.isCaptain && !spinInFlight) {
            ClubButton(stringResource(R.string.spin_action), vm::spin, Modifier.fillMaxWidth(), icon = Icons.Filled.Autorenew, large = true)
        } else {
            Text(
                stringResource(if (spinInFlight) R.string.spin_spinning else R.string.spin_waiting, ui.captain?.nickname ?: ""),
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else if (phase == Phase.INTRO && highlight != null) {
        val s = room.sectors.getOrNull(highlight)
        if (s != null) {
            SectorBadge(s.type)
            Text(stringResource(R.string.sector_n, highlight + 1) + (s.topic?.let { " · $it" } ?: ""), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun QuestionStage(ui: GameUi, vm: GameViewModel, serverNow: () -> Long, onVoice: ((String) -> Unit) -> Unit, voiceAvailable: Boolean) {
    val room = ui.room ?: return
    val phase = room.phase.phase
    val q = room.question
    val total = remember(room.phase.endsAt, phase) { (room.phase.endsAt - serverNow()).coerceAtLeast(1) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularTimer(
            room.phase.endsAt, total, serverNow, size = 96.dp,
            warnAtMs = if (phase == Phase.DISCUSS) 10_000 else 3_000,
            onSecond = if (phase == Phase.DISCUSS) vm::onDiscussSecond else null,
        )
        Column {
            Text(
                when (phase) {
                    Phase.DISCUSS -> stringResource(R.string.phase_discuss)
                    Phase.CHOOSE_ANSWERER -> stringResource(R.string.phase_choose)
                    Phase.ANSWER -> stringResource(R.string.phase_answer)
                    Phase.CHECK -> stringResource(R.string.phase_check)
                    else -> ""
                }, style = MaterialTheme.typography.titleLarge,
            )
            if (!ui.isSpectator) {
                Text(stringResource(R.string.time_bank, room.timeBankSec), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.extra_left, room.extraMinutesLeft), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (ui.mutedByHost) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MicOff, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.mic_muted_by_host))
            }
        }
    }
    room.superblitzPlayerId?.let { id ->
        val name = room.players.firstOrNull { it.id == id }?.nickname ?: ""
        Text(stringResource(R.string.superblitz_player, if (id == ui.myId) stringResource(R.string.player_you) else name), style = MaterialTheme.typography.titleMedium, color = Club.colors.brass)
    }
    q?.let { QuestionCard(it, blackboxOpen = false) }
    if (phase == Phase.CHOOSE_ANSWERER) {
        if (ui.iChooseAnswerer) ChooseAnswererCard(room.players, ui.myId, vm::chooseAnswerer)
        else Text(stringResource(R.string.choose_waiting, ui.captain?.nickname ?: ""), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
    if (phase == Phase.ANSWER) {
        if (ui.iAnswer) AnswerInput(onSubmit = vm::answer, onVoice = onVoice, voiceAvailable = voiceAvailable)
        else Text(stringResource(R.string.answer_waiting, room.players.firstOrNull { it.id == ui.answererId }?.nickname ?: ""), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
    if (phase == Phase.CHECK) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), color = Club.colors.brass, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.phase_check_text))
        }
    }
    if (!ui.isSpectator && (room.audience.isNotEmpty() || room.audienceEndsAt > serverNow())) AudienceResults(room.audience, room.audienceEndsAt, serverNow)
}

@Composable
private fun ActionBar(
    ui: GameUi,
    serverNow: () -> Long,
    onMic: () -> Unit,
    onPtt: (Boolean) -> Unit,
    onReady: () -> Unit,
    onExtra: () -> Unit,
    onAudience: () -> Unit,
    onChat: () -> Unit,
    onOpenAudience: () -> Unit,
) {
    val room = ui.room ?: return
    val discuss = room.phase.phase == Phase.DISCUSS
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!ui.isSpectator) {
                val micDesc = stringResource(if (ui.micOn) R.string.cd_mic_on else R.string.cd_mic_off)
                if (ui.settings.micMode == MicMode.PUSH_TO_TALK) {
                    FilledIconButton(
                        onClick = {}, enabled = !ui.mutedByHost,
                        modifier = Modifier.size(56.dp).semantics { contentDescription = micDesc }.pointerInput(Unit) {
                            detectTapGestures(onPress = { onPtt(true); tryAwaitRelease(); onPtt(false) })
                        },
                    ) { Icon(if (ui.micOn) Icons.Filled.Mic else Icons.Filled.MicNone, null) }
                } else {
                    FilledIconToggleButton(checked = ui.micOn, onCheckedChange = { onMic() }, enabled = !ui.mutedByHost, modifier = Modifier.size(56.dp).semantics { contentDescription = micDesc }) {
                        Icon(if (ui.micOn) Icons.Filled.Mic else Icons.Filled.MicOff, null)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // досрочный ответ: не в блице и только пока на таймере больше 10 секунд
                    val single = (room.question?.subTotal ?: 1) <= 1
                    var earlyAllowed by remember(room.phase.endsAt) { mutableStateOf(room.phase.endsAt - serverNow() > 10_000) }
                    LaunchedEffect(room.phase.endsAt, discuss) {
                        while (discuss && earlyAllowed) { kotlinx.coroutines.delay(250); earlyAllowed = room.phase.endsAt - serverNow() > 10_000 }
                    }
                    if (discuss && ui.canFinishDiscussion && single && earlyAllowed) ClubButton(stringResource(R.string.action_ready_answer), onReady, Modifier.weight(1f))
                    if (discuss) OutlinedButton(onClick = onExtra, Modifier.heightIn(min = 48.dp), enabled = room.vote == null && (room.extraMinutesLeft > 0 || room.timeBankSec > 0)) {
                        Icon(Icons.Filled.MoreTime, stringResource(R.string.action_extra_minute))
                    }
                    if (discuss && ui.isCaptain && room.settings.audienceHelp && room.audienceHelpLeft > 0) OutlinedButton(onClick = onAudience, Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Groups, stringResource(R.string.action_call_audience))
                    }
                }
            } else {
                Text(stringResource(R.string.spectator_mode), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                if (room.audienceEndsAt > serverNow()) ClubButton(stringResource(R.string.audience_help_title), onOpenAudience)
            }
            IconButton(onClick = onChat, Modifier.size(52.dp)) {
                BadgedBox(badge = { if (ui.chat.isNotEmpty()) Badge { Text("${ui.chat.count { it.scope == if (ui.isSpectator) "hall" else "team" }.coerceAtMost(99)}") } }) {
                    Icon(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.cd_chat))
                }
            }
        }
    }
}

@Composable
private fun PlayerMenu(
    p: PlayerInRoom, ui: GameUi, onDismiss: () -> Unit,
    onCaptain: () -> Unit, onKick: () -> Unit, onReport: () -> Unit, onBlock: () -> Unit,
) {
    val lobby = ui.phase == Phase.LOBBY
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { PlayerAvatar(p.avatar, size = 56.dp, isBot = p.isBot) },
        title = { Text(p.nickname) },
        text = {
            Column {
                Text(stringResource(R.string.player_rating, p.rating), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                if (lobby && ui.isHost && !p.isCaptain) MenuItem(Icons.Filled.Star, stringResource(R.string.player_make_captain), onCaptain)
                if (!lobby && !ui.isSpectator) MenuItem(Icons.Filled.PersonRemove, stringResource(R.string.player_kick), onKick)
                if (!p.isBot) {
                    MenuItem(Icons.Filled.Flag, stringResource(R.string.action_report), onReport)
                    MenuItem(Icons.Filled.Block, stringResource(R.string.action_block), onBlock)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(text, Modifier.weight(1f))
    }
}

@Composable
private fun InviteFriendsDialog(vm: GameViewModel, onDismiss: () -> Unit) {
    var friends by remember { mutableStateOf<List<Friend>?>(null) }
    val invited = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { friends = vm.friends() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lobby_invite_friends)) },
        text = {
            val list = friends
            if (list == null) CircularProgressIndicator()
            else if (list.isEmpty()) Text(stringResource(R.string.friends_empty))
            else Column(Modifier.verticalScroll(rememberScrollState())) {
                list.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayerAvatar(f.player.avatar, size = 36.dp, online = f.player.online)
                        Spacer(Modifier.width(8.dp))
                        Text(f.player.nickname, Modifier.weight(1f))
                        TextButton(onClick = { invited += f.player.id; vm.inviteFriend(f.player.id) }, enabled = f.player.id !in invited) {
                            Text(stringResource(if (f.player.id in invited) R.string.invited else R.string.action_invite))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun LobbyPanel(ui: GameUi, vm: GameViewModel, onPlayerMenu: (PlayerInRoom) -> Unit, onInvite: () -> Unit) {
    val room = ui.room ?: return
    if (ui.isSpectator) {
        ClubCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.spectator_waiting), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    uz.robutpit.znatoki.feature.lobby.LobbyContent(
        room = room, myId = ui.myId, isHost = ui.isHost, chat = ui.chat,
        onReady = vm::setReady, onStart = vm::start, onAddBot = vm::addBot,
        onPlayerMenu = onPlayerMenu, onInvite = onInvite,
        chatPanel = {
            ChatPanel(
                ui.chat.filter { it.scope == "team" }, ui.myId, onSend = { vm.chat("team", it) },
                onReport = vm::reportMessage, onBlock = { vm.blockPlayer(it.authorId) }, modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

/** Капитан выбирает, кто из команды отвечает. Не выбрал за 10 с — отвечает сам. */
@Composable
internal fun ChooseAnswererCard(players: List<PlayerInRoom>, myId: String, onChoose: (String) -> Unit) {
    var chosen by remember { mutableStateOf<String?>(null) }
    ClubCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.choose_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.choose_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        players.forEach { p ->
            val selected = chosen == p.id
            OutlinedButton(
                onClick = { if (chosen == null) { chosen = p.id; onChoose(p.id) } },
                enabled = chosen == null || selected,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 3.dp),
                border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Club.colors.brass else MaterialTheme.colorScheme.outlineVariant),
            ) {
                PlayerAvatar(p.avatar, size = 36.dp, isBot = p.isBot, captain = p.isCaptain, online = p.online)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (p.id == myId) stringResource(R.string.choose_me) else p.nickname,
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                )
                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Club.colors.brass)
            }
        }
    }
}
