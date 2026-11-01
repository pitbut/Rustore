package uz.robutpit.znatoki.design.components

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uz.robutpit.znatoki.R
import uz.robutpit.znatoki.core.AppException
import uz.robutpit.znatoki.core.ClickGuard
import uz.robutpit.znatoki.design.Club

/** Системная настройка «убрать анимации». */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

@Composable
fun ClubBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    val c = Club.colors
    Box(
        modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(c.felt.copy(alpha = if (c.isDark) 0.35f else 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.5f, -size.height * 0.05f), radius = size.maxDimension * 0.8f,
                    )
                )
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
    }
}

@Composable
fun ClubButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    large: Boolean = false,
) {
    val guard = remember { ClickGuard() }
    val haptic = LocalHapticFeedback.current
    val c = Club.colors
    val shape = RoundedCornerShape(if (large) 24.dp else 18.dp)
    Button(
        onClick = { if (!loading && guard.allow()) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() } },
        enabled = enabled && !loading,
        modifier = modifier
            .heightIn(min = if (large) 64.dp else 52.dp)
            .then(if (enabled) Modifier.background(Brush.verticalGradient(listOf(c.brass, c.brassDeep)), shape) else Modifier),
        shape = shape,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = if (loading) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (enabled) BorderStroke(1.dp, c.brass.copy(alpha = 0.6f)) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val fg = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = fg)
            else if (icon != null) Icon(icon, null, tint = fg)
            if (icon != null || loading) Spacer(Modifier.width(10.dp))
            Text(text, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

@Composable
fun ClubOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val guard = remember { ClickGuard() }
    OutlinedButton(
        onClick = { if (guard.allow()) onClick() }, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Club.colors.brass.copy(alpha = if (enabled) 0.7f else 0.2f)),
    ) {
        if (icon != null) { Icon(icon, null, tint = Club.colors.brass); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ClubCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val border = BorderStroke(1.dp, Club.colors.cardStroke)
    if (onClick != null) {
        val guard = remember { ClickGuard(400) }
        Card(onClick = { if (guard.allow()) onClick() }, modifier = modifier, shape = shape, colors = colors, border = border, elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun ClubScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    snackbar: SnackbarHostState? = null,
    floating: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    ClubBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
            floatingActionButton = floating,
            content = content,
        )
    }
}

// ---------------- Аватары и эмблемы (оригинальные, рисуются вектором) ----------------
private val avatarPalette = listOf(
    Color(0xFFD4A853), Color(0xFF4FB3AA), Color(0xFFE5735C), Color(0xFF7FC4D6), Color(0xFF9C7FD6), Color(0xFF6CC48A),
    Color(0xFFE09F3E), Color(0xFF5D8FD8), Color(0xFFD86B9C), Color(0xFF8FA65A), Color(0xFFC9895B), Color(0xFF6BB5C9),
    Color(0xFF8A9BA0), Color(0xFF97A8AD), Color(0xFFA4B5BA), Color(0xFFB1C2C7),
)

const val AVATAR_COUNT = 12

@Composable
fun PlayerAvatar(
    index: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    speaking: Boolean = false,
    online: Boolean = true,
    isBot: Boolean = false,
    captain: Boolean = false,
    contentDescription: String? = null,
) {
    val color = avatarPalette[index.mod(avatarPalette.size)]
    val c = Club.colors
    val ring by animateFloatAsState(if (speaking) 1f else 0f, tween(180), label = "ring")
    val p = if (speaking) {
        val pulse = rememberInfiniteTransition(label = "pulse")
        pulse.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "p").value
    } else 1f
    Box(modifier.size(size).semantics { if (contentDescription != null) this.contentDescription = contentDescription }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            if (ring > 0f) drawCircle(c.success.copy(alpha = 0.55f * ring), radius = r * (if (speaking) p else 1f), style = Stroke(r * 0.12f))
            val inner = r * 0.84f
            drawCircle(Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f))), radius = inner)
            val fg = Color(0xFF0D1719).copy(alpha = 0.78f)
            val cx = center.x; val cy = center.y
            if (isBot || index >= AVATAR_COUNT) {
                // бот: «табло» с двумя глазами и антенной
                drawRoundRect(fg, Offset(cx - inner * 0.5f, cy - inner * 0.32f), Size(inner, inner * 0.7f), androidx.compose.ui.geometry.CornerRadius(inner * 0.18f))
                drawCircle(color, inner * 0.11f, Offset(cx - inner * 0.22f, cy + 0.02f * inner))
                drawCircle(color, inner * 0.11f, Offset(cx + inner * 0.22f, cy + 0.02f * inner))
                drawLine(fg, Offset(cx, cy - inner * 0.32f), Offset(cx, cy - inner * 0.6f), inner * 0.08f, StrokeCap.Round)
                drawCircle(fg, inner * 0.09f, Offset(cx, cy - inner * 0.64f))
            } else when (index % 6) {
                0 -> { // «мудрец»: голова + плечи
                    drawCircle(fg, inner * 0.3f, Offset(cx, cy - inner * 0.18f))
                    drawArc(fg, 180f, 180f, true, Offset(cx - inner * 0.62f, cy + inner * 0.2f), Size(inner * 1.24f, inner * 1.1f))
                }
                1 -> { // звезда-восьмилучевик
                    for (k in 0 until 8) {
                        val a = Math.toRadians(k * 45.0)
                        drawLine(fg, center, Offset(cx + (kotlin.math.cos(a) * inner * 0.6f).toFloat(), cy + (kotlin.math.sin(a) * inner * 0.6f).toFloat()), inner * 0.14f, StrokeCap.Round)
                    }
                    drawCircle(color, inner * 0.16f)
                }
                2 -> { // полумесяц
                    drawCircle(fg, inner * 0.55f, Offset(cx - inner * 0.08f, cy))
                    drawCircle(color, inner * 0.46f, Offset(cx + inner * 0.16f, cy - inner * 0.08f))
                }
                3 -> { // книга
                    drawRoundRect(fg, Offset(cx - inner * 0.55f, cy - inner * 0.35f), Size(inner * 1.1f, inner * 0.75f), androidx.compose.ui.geometry.CornerRadius(inner * 0.08f))
                    drawLine(color, Offset(cx, cy - inner * 0.32f), Offset(cx, cy + inner * 0.38f), inner * 0.07f)
                }
                4 -> { // ромб-кристалл
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy - inner * 0.6f); lineTo(cx + inner * 0.45f, cy); lineTo(cx, cy + inner * 0.6f); lineTo(cx - inner * 0.45f, cy); close()
                    }
                    drawPath(path, fg)
                }
                else -> { // перо
                    drawOval(fg, Offset(cx - inner * 0.2f, cy - inner * 0.6f), Size(inner * 0.4f, inner * 1.0f))
                    drawLine(fg, Offset(cx, cy + inner * 0.3f), Offset(cx - inner * 0.2f, cy + inner * 0.62f), inner * 0.08f, StrokeCap.Round)
                }
            }
            if (captain) {
                drawCircle(c.brass, r * 0.24f, Offset(this.size.width - r * 0.28f, r * 0.28f))
                drawCircle(Color(0xFF1A1405), r * 0.1f, Offset(this.size.width - r * 0.28f, r * 0.28f))
            }
            if (!online) drawCircle(Color.Black.copy(alpha = 0.5f), inner)
        }
    }
}

@Composable
fun TeamEmblem(index: Int, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val color = avatarPalette[(index * 3 + 1).mod(avatarPalette.size)]
    val c = Club.colors
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val shield = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.04f); lineTo(w * 0.92f, h * 0.18f); lineTo(w * 0.86f, h * 0.62f)
            quadraticTo(w * 0.7f, h * 0.88f, w * 0.5f, h * 0.97f); quadraticTo(w * 0.3f, h * 0.88f, w * 0.14f, h * 0.62f)
            lineTo(w * 0.08f, h * 0.18f); close()
        }
        drawPath(shield, Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f))))
        drawPath(shield, c.brass, style = Stroke(w * 0.04f))
        val cx = w / 2; val cy = h * 0.48f
        when (index % 5) {
            0 -> drawCircle(c.brass, w * 0.16f, Offset(cx, cy))
            1 -> for (k in 0 until 3) drawLine(c.brass, Offset(w * 0.3f, cy - w * 0.12f + k * w * 0.12f), Offset(w * 0.7f, cy - w * 0.12f + k * w * 0.12f), w * 0.05f, StrokeCap.Round)
            2 -> drawArc(c.brass, 200f, 140f, false, Offset(cx - w * 0.2f, cy - w * 0.15f), Size(w * 0.4f, w * 0.4f), style = Stroke(w * 0.06f, cap = StrokeCap.Round))
            3 -> { drawLine(c.brass, Offset(cx, cy - w * 0.2f), Offset(cx, cy + w * 0.2f), w * 0.06f, StrokeCap.Round); drawLine(c.brass, Offset(cx - w * 0.2f, cy), Offset(cx + w * 0.2f, cy), w * 0.06f, StrokeCap.Round) }
            else -> drawRect(c.brass, Offset(cx - w * 0.13f, cy - w * 0.13f), Size(w * 0.26f, w * 0.26f))
        }
    }
}

// ---------------- Таймер ----------------
/**
 * Круговой таймер от серверного дедлайна: оставшееся время = endsAt − serverNow(), без локального счётчика.
 */
@Composable
fun CircularTimer(
    endsAtServer: Long,
    totalMs: Long,
    serverNow: () -> Long,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    warnAtMs: Long = 10_000,
    onSecond: ((Int) -> Unit)? = null,
) {
    var remaining by remember(endsAtServer) { mutableLongStateOf((endsAtServer - serverNow()).coerceAtLeast(0)) }
    LaunchedEffect(endsAtServer) {
        var lastSec = -1
        while (true) {
            withFrameMillis { }
            remaining = (endsAtServer - serverNow()).coerceAtLeast(0)
            val sec = ((remaining + 999) / 1000).toInt()
            if (sec != lastSec) { lastSec = sec; onSecond?.invoke(sec) }
            if (remaining <= 0) break
        }
    }
    val frac = if (totalMs > 0) (remaining.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
    val warn = remaining in 1..warnAtMs
    val c = Club.colors
    val arcColor = if (warn) MaterialTheme.colorScheme.error else c.brass
    val track = MaterialTheme.colorScheme.surfaceVariant
    val secs = ((remaining + 999) / 1000).toInt()
    val cd = stringResource(R.string.cd_timer, secs)
    Box(modifier.size(size).semantics { contentDescription = cd }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.09f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(arcColor, -90f, 360f * frac, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(
            "$secs", style = if (size > 70.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------- Состояния загрузки/ошибок ----------------
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 72.dp) {
    val t = rememberInfiniteTransition(label = "sk")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).drawBehind {
            drawRect(base)
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent), start = Offset(size.width * x, 0f), end = Offset(size.width * (x + 0.6f), size.height)))
        }
    )
}

@Composable
fun SkeletonList(count: Int = 5, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(count) { Skeleton() } }
}

@Composable
fun EmptyState(title: String, subtitle: String? = null, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.Inbox, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Club.colors.felt.copy(alpha = 0.4f)).border(1.dp, Club.colors.cardStroke, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(44.dp), tint = Club.colors.brass)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun ErrorState(error: AppException, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(if (error.code == AppException.NETWORK) R.string.err_network_title else R.string.err_generic_title),
        subtitle = errorText(error.code, error.message),
        icon = Icons.Filled.CloudOff, modifier = modifier,
        action = { ClubOutlinedButton(stringResource(R.string.action_retry), onRetry) },
    )
}

@Composable
fun errorText(code: String, fallback: String? = null): String {
    val id = errorRes(code)
    return if (id != null) stringResource(id) else fallback?.takeIf { it.isNotBlank() && it != code } ?: stringResource(R.string.err_generic)
}

@StringRes
fun errorRes(code: String): Int? = when (code) {
    AppException.NETWORK -> R.string.err_network
    AppException.UNAUTHORIZED -> R.string.err_unauthorized
    AppException.QUOTA -> R.string.err_quota
    "invalid_email" -> R.string.err_invalid_email
    "weak_password" -> R.string.err_weak_password
    "invalid_nickname" -> R.string.err_invalid_nickname
    "nickname_taken" -> R.string.err_nickname_taken
    "invalid_credentials" -> R.string.err_invalid_credentials
    "invalid_code" -> R.string.err_invalid_code
    "room_not_found" -> R.string.err_room_not_found
    "room_full" -> R.string.err_room_full
    "not_captain", "not_answerer" -> R.string.err_not_captain
    "not_host" -> R.string.err_not_host
    "wrong_phase" -> R.string.err_wrong_phase
    "vote_in_progress" -> R.string.err_vote_in_progress
    "no_extra_time" -> R.string.err_no_extra_time
    "audience_unavailable" -> R.string.err_audience_unavailable
    "audience_closed" -> R.string.err_audience_closed
    "already_suggested" -> R.string.err_already_suggested
    "kick_cooldown" -> R.string.err_kick_cooldown
    "kick_blocked" -> R.string.err_kick_blocked
    "kick_not_enough" -> R.string.err_kick_not_enough
    "kick_self" -> R.string.err_kick_self
    "appeal_unavailable" -> R.string.err_appeal_unavailable
    "team_full" -> R.string.err_team_full
    "already_requested" -> R.string.err_already_requested
    "forbidden" -> R.string.err_forbidden
    "game_started" -> R.string.err_game_started
    "google_unavailable" -> R.string.err_google_unavailable
    "google_failed" -> R.string.err_google_failed
    "no_early_in_blitz" -> R.string.err_no_early_in_blitz
    "early_too_late" -> R.string.err_early_too_late
    "invalid_token" -> R.string.err_unauthorized
    else -> null
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Club.colors.brass)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        modifier = modifier.heightIn(min = 40.dp),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Club.colors.brass.copy(alpha = 0.25f), selectedLabelColor = MaterialTheme.colorScheme.onSurface),
    )
}
