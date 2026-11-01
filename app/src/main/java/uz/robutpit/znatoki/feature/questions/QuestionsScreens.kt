package uz.robutpit.znatoki.feature.questions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.robutpit.znatoki.R
import uz.robutpit.znatoki.core.AppException
import uz.robutpit.znatoki.core.Load
import uz.robutpit.znatoki.core.LocaleHelper
import uz.robutpit.znatoki.core.asApp
import uz.robutpit.znatoki.core.runLoad
import uz.robutpit.znatoki.data.repo.CacheRepository
import uz.robutpit.znatoki.design.Club
import uz.robutpit.znatoki.design.components.*
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.domain.model.*
import uz.robutpit.znatoki.feature.home.rememberErrorMessages
import javax.inject.Inject

@HiltViewModel
class QuestionsViewModel @Inject constructor(private val gateway: GameGateway, private val cache: CacheRepository) : ViewModel() {
    val mine = MutableStateFlow<Load<List<Question>>>(Load.Loading)
    val packs = MutableStateFlow<Load<List<QuestionPack>>>(Load.Loading)
    val local: StateFlow<List<Question>> = cache.localQuestions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val drafts = MutableStateFlow<List<Question>>(emptyList())
    val quota = MutableStateFlow<Pair<Int, Int>?>(null)
    val busy = MutableStateFlow(false)
    private val _errors = MutableSharedFlow<AppException>(extraBufferCapacity = 4)
    val errors: SharedFlow<AppException> = _errors
    private val _info = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val info: SharedFlow<Int> = _info

    init {
        loadMine(); loadPacks()
        // статус модерации обновляется, пока есть вопросы «на проверке»
        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                val m = mine.value
                if (m is Load.Ready && m.data.any { it.status == QuestionStatus.pending }) runCatching { mine.value = Load.Ready(gateway.myQuestions()) }
            }
        }
    }

    fun loadMine() = viewModelScope.launch { mine.value = runLoad { gateway.myQuestions() } }
    fun loadPacks() = viewModelScope.launch { cache.packs().collect { packs.value = it } }

    private fun act(info: Int? = null, block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true
        try { block(); info?.let { _info.emit(it) } } catch (e: Exception) { _errors.emit(e.asApp()) }
        busy.value = false
    }

    fun suggest(q: Question, onDone: () -> Unit) = act(R.string.q_sent) { gateway.suggestQuestion(q); onDone(); mine.value = Load.Ready(gateway.myQuestions()) }

    fun generate(topic: String, difficulty: Int, count: Int, lang: String) = act {
        val r = gateway.generate(topic, difficulty, count, lang)
        drafts.value = r.drafts + drafts.value
        quota.value = r.quotaLeft to r.quotaTotal
    }

    fun saveDraft(q: Question) = act(R.string.q_saved_local) { cache.saveLocal(q); drafts.update { l -> l - q } }
    fun shareDraft(q: Question) = act(R.string.q_sent) { gateway.suggestQuestion(q.copy(status = QuestionStatus.pending)); drafts.update { l -> l - q }; mine.value = Load.Ready(gateway.myQuestions()) }
    fun deleteDraft(q: Question) = drafts.update { it - q }
    fun deleteLocal(q: Question) = act { cache.deleteLocal(q.id) }
    fun createPack(title: String, ids: List<String>) = act(R.string.pack_created) { gateway.createPack(title, ids); packs.value = Load.Ready(gateway.packs()) }
}

@Composable
fun QuestionsScreen(vm: QuestionsViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val errText = rememberErrorMessages()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.errors.collect { snack.showSnackbar(errText(it)) } }
    LaunchedEffect(Unit) { vm.info.collect { snack.showSnackbar(ctx.getString(it)) } }
    val tabs = listOf(R.string.q_tab_mine, R.string.q_tab_suggest, R.string.q_tab_generator, R.string.q_tab_packs)
    ClubScaffold(title = stringResource(R.string.questions_title), onBack = null, snackbar = snack) { pad ->
        Column(Modifier.padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t)) }) }
            }
            when (tab) {
                0 -> MyQuestionsTab(vm) { tab = 1 }
                1 -> SuggestTab(vm) { tab = 0 }
                2 -> GeneratorTab(vm)
                else -> PacksTab(vm)
            }
        }
    }
}

@Composable
private fun MyQuestionsTab(vm: QuestionsViewModel, onSuggest: () -> Unit) {
    val mine by vm.mine.collectAsState()
    val local by vm.local.collectAsState()
    when (val m = mine) {
        is Load.Loading -> SkeletonList(4)
        is Load.Failed -> ErrorState(m.error, { vm.loadMine() })
        is Load.Ready -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (m.data.isEmpty()) item {
                EmptyState(stringResource(R.string.q_mine_empty), stringResource(R.string.q_mine_empty_hint), icon = Icons.Filled.EditNote) {
                    ClubButton(stringResource(R.string.q_tab_suggest), onSuggest)
                }
            }
            items(m.data, key = { it.id }) { q -> QuestionRow(q) { StatusChip(q.status) } }
            if (local.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.q_local, local.size)) }
                items(local, key = { "l" + it.id }) { q ->
                    QuestionRow(q) { IconButton(onClick = { vm.deleteLocal(q) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) } }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(s: QuestionStatus) {
    val (label, color) = when (s) {
        QuestionStatus.pending -> stringResource(R.string.q_status_pending) to Club.colors.brass
        QuestionStatus.approved -> stringResource(R.string.q_status_approved) to Club.colors.success
        QuestionStatus.rejected -> stringResource(R.string.q_status_rejected) to MaterialTheme.colorScheme.error
        QuestionStatus.draft -> stringResource(R.string.q_status_draft) to MaterialTheme.colorScheme.onSurfaceVariant
        QuestionStatus.hidden -> stringResource(R.string.q_status_hidden) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(label, color = color, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun QuestionRow(q: Question, trailing: @Composable () -> Unit) {
    ClubCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q.topic.ifBlank { "—" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            trailing()
        }
        Text(q.text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.q_answer_line, q.answer), style = MaterialTheme.typography.titleSmall, color = Club.colors.brass)
        q.moderationReason?.let { Text(stringResource(R.string.q_reason, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SuggestTab(vm: QuestionsViewModel, onDone: () -> Unit) {
    val busy by vm.busy.collectAsState()
    var type by rememberSaveable { mutableStateOf(SectorType.NORMAL) }
    var text by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var accepted by rememberSaveable { mutableStateOf("") }
    var comment by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableIntStateOf(3) }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> image = uri?.toString() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.q_suggest_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(SectorType.NORMAL to R.string.sector_normal, SectorType.BLITZ to R.string.sector_blitz, SectorType.SUPERBLITZ to R.string.sector_superblitz, SectorType.BLACKBOX to R.string.sector_blackbox)
                .forEach { (t, l) -> Chip(stringResource(l), type == t, { type = t }) }
        }
        OutlinedTextField(text, { text = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.q_field_text)) }, minLines = 3)
        OutlinedTextField(answer, { answer = it.take(120) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.q_field_answer)) }, singleLine = true)
        OutlinedTextField(accepted, { accepted = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.q_field_accepted)) }, supportingText = { Text(stringResource(R.string.q_field_accepted_hint)) })
        OutlinedTextField(comment, { comment = it.take(500) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.q_field_comment)) })
        OutlinedTextField(source, { source = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.q_field_source)) })
        OutlinedTextField(topic, { topic = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.q_field_topic)) }, singleLine = true)
        Text(stringResource(R.string.create_difficulty, difficulty))
        Slider(difficulty.toFloat(), { difficulty = it.toInt() }, valueRange = 1f..5f, steps = 3)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClubOutlinedButton(stringResource(R.string.q_field_image), { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = Icons.Filled.Image)
            Spacer(Modifier.width(12.dp))
            image?.let { AsyncImage(it, stringResource(R.string.q_field_image), Modifier.size(64.dp), contentScale = ContentScale.Crop) }
        }
        ClubButton(
            stringResource(R.string.q_send), {
                vm.suggest(
                    Question(
                        id = "", type = type, lang = LocaleHelper.current(ctx), topic = topic.trim(), text = text.trim(), answer = answer.trim(),
                        accepted = accepted.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() },
                        comment = comment.trim(), source = source.trim(), difficulty = difficulty, imageUrl = image,
                        status = QuestionStatus.pending, origin = QuestionOrigin.user,
                    )
                ) { text = ""; answer = ""; accepted = ""; comment = ""; source = ""; image = null; onDone() }
            },
            Modifier.fillMaxWidth(), loading = busy, enabled = text.length >= 10 && answer.isNotBlank(),
        )
        Text(stringResource(R.string.q_moderation_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GeneratorTab(vm: QuestionsViewModel) {
    val drafts by vm.drafts.collectAsState()
    val quota by vm.quota.collectAsState()
    val busy by vm.busy.collectAsState()
    var topic by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableIntStateOf(3) }
    var count by rememberSaveable { mutableIntStateOf(5) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ClubCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.gen_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.gen_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(topic, { topic = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.q_field_topic)) })
                Text(stringResource(R.string.create_difficulty, difficulty))
                Slider(difficulty.toFloat(), { difficulty = it.toInt() }, valueRange = 1f..5f, steps = 3)
                Text(stringResource(R.string.gen_count, count))
                Slider(count.toFloat(), { count = it.toInt() }, valueRange = 1f..10f, steps = 8)
                quota?.let { (left, total) -> Text(stringResource(R.string.gen_quota, left, total), style = MaterialTheme.typography.labelLarge, color = Club.colors.brass) }
                Spacer(Modifier.height(8.dp))
                ClubButton(stringResource(R.string.gen_action), { vm.generate(topic.trim(), difficulty, count, LocaleHelper.current(ctx)) }, Modifier.fillMaxWidth(), loading = busy, icon = Icons.Filled.AutoAwesome)
            }
        }
        if (busy && drafts.isEmpty()) items(3) { Skeleton(height = 120.dp) }
        items(drafts, key = { it.id }) { q ->
            QuestionRow(q) {}
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.saveDraft(q) }) { Text(stringResource(R.string.gen_save)) }
                TextButton(onClick = { vm.shareDraft(q) }) { Text(stringResource(R.string.gen_share)) }
                TextButton(onClick = { vm.deleteDraft(q) }) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun PacksTab(vm: QuestionsViewModel) {
    val packs by vm.packs.collectAsState()
    val local by vm.local.collectAsState()
    var createOpen by rememberSaveable { mutableStateOf(false) }
    when (val p = packs) {
        is Load.Loading -> SkeletonList(4)
        is Load.Failed -> ErrorState(p.error, { vm.loadPacks() })
        is Load.Ready -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ClubOutlinedButton(stringResource(R.string.pack_create), { createOpen = true }, Modifier.fillMaxWidth(), Icons.Filled.CreateNewFolder) }
            val groups = p.data.groupBy { it.category }
            listOf("mine" to R.string.pack_cat_mine, "topic" to R.string.pack_cat_topic, "imported" to R.string.pack_cat_imported, "ai" to R.string.pack_cat_ai, "user" to R.string.pack_cat_user).forEach { (cat, label) ->
                val list = groups[cat].orEmpty()
                if (list.isNotEmpty()) {
                    item { SectionTitle(stringResource(label)) }
                    items(list, key = { it.id }) { pk ->
                        ClubCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Folder, null, tint = Club.colors.brass)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pk.title, style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.pack_line, pk.count, pk.author), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (createOpen) {
        var title by rememberSaveable { mutableStateOf("") }
        val selected = remember { mutableStateListOf<String>() }
        AlertDialog(
            onDismissRequest = { createOpen = false },
            title = { Text(stringResource(R.string.pack_create)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(title, { title = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.pack_title)) })
                    Spacer(Modifier.height(8.dp))
                    if (local.isEmpty()) Text(stringResource(R.string.pack_no_local), style = MaterialTheme.typography.bodySmall)
                    local.forEach { q ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(q.id in selected, { if (it) selected += q.id else selected -= q.id })
                            Text(q.text, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.createPack(title, selected.toList()); createOpen = false }, enabled = title.isNotBlank()) { Text(stringResource(R.string.action_create)) } },
            dismissButton = { TextButton(onClick = { createOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
