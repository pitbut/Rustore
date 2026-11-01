package uz.robutpit.znatoki.feature.game

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import uz.robutpit.znatoki.R
import uz.robutpit.znatoki.core.ClickGuard
import uz.robutpit.znatoki.design.Club
import uz.robutpit.znatoki.design.components.*
import uz.robutpit.znatoki.domain.model.*

// ---------------- Табло ----------------
@Composable
fun ScoreBoard(score: Score, modifier: Modifier = Modifier) {
    val c = Club.colors
    val cd = stringResource(R.string.cd_score, score.experts, score.viewers)
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.woodDark.copy(alpha = 0.85f))
            .border(1.dp, c.brass.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.score_experts), style = MaterialTheme.typography.labelMedium, color = c.experts)
            ScoreDigit(score.experts, c.experts)
        }
        Text(":", style = MaterialTheme.typography.displaySmall, color = c.brass, modifier = Modifier.padding(horizontal = 8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.score_viewers), style = MaterialTheme.typography.labelMedium, color = c.viewers)
            ScoreDigit(score.viewers, c.viewers)
        }
    }
}

@Composable
private fun ScoreDigit(v: Int, color: Color) {
    AnimatedContent(
        v, transitionSpec = { (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut()) }, label = "score",
    ) { value -> Text("$value", style = MaterialTheme.typography.displaySmall, color = color) }
}

// ---------------- Панель команды ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TeamPanel(
    players: List<PlayerInRoom>,
    speaking: Set<String>,
    myId: String,
    superblitzId: String?,
    onPlayerMenu: (PlayerInRoom) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        players.forEach { p ->
            val desc = buildString {
                append(p.nickname)
                if (p.isCaptain) append(", ").append(stringResource(R.string.player_captain))
                if (p.isBot) append(", ").append(stringResource(R.string.player_bot))
                if (p.id in speaking) append(", ").append(stringResource(R.string.player_speaking))
                if (!p.online) append(", ").append(stringResource(R.string.player_offline))
            }
            Column(
                Modifier
                    .widthIn(min = 52.dp, max = 72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = { if (p.id != myId) onPlayerMenu(p) }, onLongClick = { if (p.id != myId) onPlayerMenu(p) })
                    .padding(4.dp)
                    .semantics(mergeDescendants = true) { contentDescription = desc },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    PlayerAvatar(
                        p.avatar, size = 48.dp, speaking = p.id in speaking, online = p.online, isBot = p.isBot, captain = p.isCaptain,
                        modifier = if (superblitzId == p.id) Modifier.border(2.dp, Club.colors.brass, CircleShape) else Modifier,
                    )
                    if (p.micMutedByHost) Icon(Icons.Filled.MicOff, null, Modifier.size(16.dp).align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.error, CircleShape).padding(2.dp), tint = Color.White)
                }
                Text(
                    if (p.id == myId) stringResource(R.string.player_you) else p.nickname.removePrefix("Бот "),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------- Вопрос ----------------
@Composable
fun QuestionCard(q: GameQuestion, blackboxOpen: Boolean, modifier: Modifier = Modifier) {
    ClubCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectorBadge(q.type)
            Spacer(Modifier.weight(1f))
            if (q.subTotal > 1) Text(stringResource(R.string.question_part, q.subIndex + 1, q.subTotal), style = MaterialTheme.typography.labelLarge, color = Club.colors.brass)
            else q.topic?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (q.type == SectorType.BLACKBOX) {
            BlackBox(blackboxOpen, Modifier.fillMaxWidth(0.55f).align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(q.text, transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(150))) }, label = "q") { text ->
            Text(text, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
        q.hint?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (q.subTotal > 1) { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.blitz_rule), style = MaterialTheme.typography.bodySmall, color = Club.colors.brass) }
    }
}

@Composable
fun SectorBadge(type: SectorType) {
    val (label, icon) = when (type) {
        SectorType.NORMAL -> stringResource(R.string.sector_normal) to Icons.Filled.Mail
        SectorType.BLITZ -> stringResource(R.string.sector_blitz) to Icons.Filled.Bolt
        SectorType.SUPERBLITZ -> stringResource(R.string.sector_superblitz) to Icons.Filled.ElectricBolt
        SectorType.BLACKBOX -> stringResource(R.string.sector_blackbox) to Icons.Filled.Inventory2
        SectorType.SPECIAL13 -> stringResource(R.string.sector_special) to Icons.Filled.Star
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Club.colors.felt).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = Club.colors.brass)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------------- Ответ ----------------
@Composable
fun RevealCard(
    reveal: Reveal,
    question: GameQuestion?,
    canAppeal: Boolean,
    onAppeal: () -> Unit,
    feedbackDone: Boolean,
    onFeedback: (QuestionFeedback) -> Unit,
    showFeedback: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Club.colors
    val (verdictText, color) = when (reveal.verdict) {
        Verdict.CORRECT -> stringResource(R.string.verdict_correct) to c.success
        Verdict.DISPUTED -> stringResource(R.string.verdict_disputed) to c.brass
        Verdict.INCORRECT -> stringResource(R.string.verdict_incorrect) to MaterialTheme.colorScheme.error
    }
    var reportOpen by remember { mutableStateOf(false) }
    ClubCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (reveal.verdict == Verdict.INCORRECT) Icons.Filled.Cancel else Icons.Filled.CheckCircle, null, tint = color)
            Spacer(Modifier.width(8.dp))
            Text(verdictText, style = MaterialTheme.typography.titleLarge, color = color)
        }
        reveal.givenAnswer?.let {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.reveal_given, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.reveal_answer), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(reveal.answer, style = MaterialTheme.typography.headlineMedium, color = c.brass)
        if (reveal.comment.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(reveal.comment, style = MaterialTheme.typography.bodyMedium) }
        if (reveal.source.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.reveal_source, reveal.source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (canAppeal) {
            Spacer(Modifier.height(10.dp))
            ClubOutlinedButton(stringResource(R.string.reveal_appeal), onAppeal, Modifier.fillMaxWidth(), Icons.Filled.Gavel)
        }
        if (showFeedback && question != null) {
            Spacer(Modifier.height(10.dp))
            if (feedbackDone) Text(stringResource(R.string.feedback_thanks), style = MaterialTheme.typography.bodySmall, color = c.success)
            else Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onFeedback(QuestionFeedback(liked = true)) }) { Icon(Icons.Filled.ThumbUp, stringResource(R.string.feedback_like)) }
                IconButton(onClick = { onFeedback(QuestionFeedback(liked = false)) }) { Icon(Icons.Filled.ThumbDown, stringResource(R.string.feedback_dislike)) }
                TextButton(onClick = { onFeedback(QuestionFeedback(tooEasy = true)) }) { Text(stringResource(R.string.feedback_easy)) }
                TextButton(onClick = { onFeedback(QuestionFeedback(tooHard = true)) }) { Text(stringResource(R.string.feedback_hard)) }
                IconButton(onClick = { reportOpen = true }) { Icon(Icons.Filled.Flag, stringResource(R.string.feedback_report)) }
            }
        }
    }
    if (reportOpen) ReasonDialog(
        title = stringResource(R.string.feedback_report),
        reasons = listOf(R.string.reason_wrong_answer, R.string.reason_unclear, R.string.reason_offensive, R.string.reason_copyright).map { stringResource(it) },
        onDismiss = { reportOpen = false },
        onPick = { reportOpen = false; onFeedback(QuestionFeedback(report = true, reason = it)) },
    )
}

@Composable
fun ReasonDialog(title: String, reasons: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                reasons.forEach { r ->
                    TextButton(onClick = { onPick(r) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(r, Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ---------------- Ввод ответа ----------------
@Composable
fun AnswerInput(
    onSubmit: (String, Boolean) -> Unit,
    onVoice: ((String) -> Unit) -> Unit,
    voiceAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var viaVoice by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    val guard = remember { ClickGuard(1000) }
    ClubCard(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.answer_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            text, { text = it.take(120); viaVoice = false }, Modifier.fillMaxWidth(), singleLine = true, enabled = !sent,
            label = { Text(stringResource(R.string.answer_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank() && guard.allow()) { sent = true; onSubmit(text, viaVoice) } }),
        )
        // микрофон — отдельной большой кнопкой, чтобы не попадать в поле ввода
        if (voiceAvailable && !sent) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onVoice { recognized -> text = recognized; viaVoice = true } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Club.colors.brass),
            ) {
                Icon(Icons.Filled.KeyboardVoice, null, Modifier.size(28.dp), tint = Club.colors.brass)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.answer_voice_button), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (viaVoice) Text(stringResource(R.string.answer_voice_confirm), style = MaterialTheme.typography.bodySmall, color = Club.colors.brass)
        Spacer(Modifier.height(8.dp))
        ClubButton(
            stringResource(if (sent) R.string.answer_sent else R.string.answer_send),
            { if (guard.allow()) { sent = true; onSubmit(text, viaVoice) } },
            Modifier.fillMaxWidth(), enabled = text.isNotBlank() && !sent, icon = Icons.AutoMirrored.Filled.Send,
        )
    }
}

// ---------------- Голосование ----------------
/**
 * Единый диалог голосования (доп. минута / исключение / пауза).
 * Защита от случайных нажатий: кнопки активируются через 700 мс и требуют подтверждения повторным касанием.
 */
@Composable
fun VoteDialog(
    vote: ActiveVote,
    targetName: String?,
    serverNow: () -> Long,
    onVote: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var armed by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(vote.voteId) { delay(700); armed = true }
    val title = when (vote.kind) {
        VoteKind.EXTRA_MINUTE -> stringResource(R.string.vote_extra_title)
        VoteKind.KICK -> stringResource(R.string.vote_kick_title, targetName ?: "")
        VoteKind.PAUSE -> stringResource(R.string.vote_pause_title)
        VoteKind.AUDIENCE -> stringResource(R.string.vote_audience_title)
    }
    val total = (vote.endsAt - serverNow()).coerceAtLeast(1)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, border = androidx.compose.foundation.BorderStroke(1.dp, Club.colors.cardStroke)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                CircularTimer(vote.endsAt, remember(vote.voteId) { total }, serverNow, size = 64.dp, warnAtMs = 3000)
                Spacer(Modifier.height(12.dp))
                VoteProgress(vote)
                Spacer(Modifier.height(16.dp))
                if (vote.myVote != null) {
                    Text(stringResource(R.string.vote_done), style = MaterialTheme.typography.bodyMedium, color = Club.colors.success)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val noSel = pending == false
                        val yesSel = pending == true
                        OutlinedButton(
                            onClick = { if (pending == false) onVote(false) else pending = false }, enabled = armed,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) { Text(stringResource(if (noSel) R.string.vote_confirm else R.string.vote_no)) }
                        Button(
                            onClick = { if (pending == true) onVote(true) else pending = true }, enabled = armed,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) { Text(stringResource(if (yesSel) R.string.vote_confirm else R.string.vote_yes)) }
                    }
                    if (pending != null) Text(stringResource(R.string.vote_tap_again), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_hide)) }
            }
        }
    }
}

@Composable
fun VoteProgress(vote: ActiveVote) {
    val eligible = vote.eligible.coerceAtLeast(1)
    val yes by animateFloatAsState(vote.yes.toFloat() / eligible, label = "yes")
    val no by animateFloatAsState(vote.no.toFloat() / eligible, label = "no")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(yes.coerceIn(0f, 1f)).background(Club.colors.success))
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxHeight().fillMaxWidth(no.coerceIn(0f, 1f) / (1f - yes).coerceAtLeast(0.01f)).background(MaterialTheme.colorScheme.error))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.vote_for, vote.yes), style = MaterialTheme.typography.labelMedium, color = Club.colors.success)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.vote_against, vote.no), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

// ---------------- Помощь зала ----------------
/** Для команды: топ-3 версии зала. */
@Composable
fun AudienceResults(list: List<AudienceSuggestion>, endsAt: Long, serverNow: () -> Long, modifier: Modifier = Modifier) {
    ClubCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Groups, null, tint = Club.colors.viewers)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.audience_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (endsAt > serverNow()) CircularTimer(endsAt, 30_000, serverNow, size = 40.dp, warnAtMs = 5000)
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) Text(stringResource(R.string.audience_waiting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.take(3).forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, color = Club.colors.brass, modifier = Modifier.width(28.dp))
                Text(s.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (s.fromAi) Text(stringResource(R.string.audience_ai), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text("👍 ${s.votes}", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(stringResource(R.string.audience_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Для зрителя: окно «Подскажи команде». */
@Composable
fun AudienceHelpDialog(
    list: List<AudienceSuggestion>,
    endsAt: Long,
    serverNow: () -> Long,
    myId: String,
    onSuggest: (String) -> Unit,
    onVote: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val voted = remember { mutableStateListOf<String>() }
    val mine = list.any { it.authorId == myId }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.audience_help_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    CircularTimer(endsAt, 30_000, serverNow, size = 48.dp, warnAtMs = 5000)
                }
                Text(stringResource(R.string.audience_help_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (!mine) {
                    OutlinedTextField(text, { text = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.audience_version)) })
                    Spacer(Modifier.height(8.dp))
                    ClubButton(stringResource(R.string.audience_send), { onSuggest(text); text = "" }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
                } else Text(stringResource(R.string.audience_sent), color = Club.colors.success)
                Spacer(Modifier.height(12.dp))
                list.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        val can = s.authorId != myId && s.id !in voted
                        FilledTonalButton(onClick = { voted += s.id; onVote(s.id) }, enabled = can, modifier = Modifier.heightIn(min = 48.dp)) { Text("👍 ${s.votes}") }
                    }
                }
                TextButton(onClick = onDismiss, Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_hide)) }
            }
        }
    }
}

// ---------------- Чат ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    myId: String,
    onSend: (String) -> Unit,
    onReport: (ChatMessage) -> Unit,
    onBlock: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var menuFor by remember { mutableStateOf<ChatMessage?>(null) }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Column(modifier) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (messages.isEmpty()) item { Text(stringResource(R.string.chat_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(messages, key = { it.id }) { m ->
                val mine = m.authorId == myId
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = {}, onLongClick = { if (!mine) menuFor = m })
                        .background(if (mine) Club.colors.felt.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.authorName, style = MaterialTheme.typography.labelMedium, color = Club.colors.brass, modifier = Modifier.weight(1f))
                        if (!mine) IconButton(onClick = { menuFor = m }, Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_message_menu), Modifier.size(18.dp)) }
                    }
                    Text(m.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it.take(300) }, Modifier.weight(1f), singleLine = true, placeholder = { Text(stringResource(R.string.chat_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend(text); text = "" }),
            )
            IconButton(onClick = { onSend(text); text = "" }, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_send)) }
        }
    }
    menuFor?.let { m ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(m.authorName) },
            text = { Text(m.text) },
            confirmButton = { TextButton(onClick = { onReport(m); menuFor = null }) { Text(stringResource(R.string.action_report)) } },
            dismissButton = { TextButton(onClick = { onBlock(m); menuFor = null }) { Text(stringResource(R.string.action_block)) } },
        )
    }
}

// ---------------- Итог игры ----------------
@Composable
fun GameOverCard(over: GameOver, score: Score, myId: String, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val experts = over.winner == "experts"
    val delta = over.ratingDeltas.firstOrNull { it.playerId == myId }
    val scale by animateFloatAsState(1f, spring(dampingRatio = 0.5f), label = "s")
    ClubCard(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (experts) Icons.Filled.EmojiEvents else Icons.Filled.Groups, null, Modifier.size((72 * scale).dp), tint = if (experts) Club.colors.brass else Club.colors.viewers)
            Text(stringResource(if (experts) R.string.over_experts_win else R.string.over_viewers_win), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Text("${score.experts} : ${score.viewers}", style = MaterialTheme.typography.displayMedium, color = Club.colors.brass)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(stringResource(R.string.over_rounds), "${over.stats.rounds}")
                StatPill(stringResource(R.string.over_correct), "${over.stats.correct}")
                StatPill(stringResource(R.string.over_bank), stringResource(R.string.seconds_short, over.stats.timeBankLeft))
            }
            Spacer(Modifier.height(8.dp))
            if (delta != null) Text(
                stringResource(R.string.over_rating, (if (delta.delta >= 0) "+" else "") + delta.delta, delta.newRating),
                style = MaterialTheme.typography.titleLarge, color = if (delta.delta >= 0) Club.colors.success else MaterialTheme.colorScheme.error,
            ) else Text(stringResource(R.string.over_unrated), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            ClubButton(stringResource(R.string.over_exit), onExit, Modifier.fillMaxWidth())
        }
    }
}
