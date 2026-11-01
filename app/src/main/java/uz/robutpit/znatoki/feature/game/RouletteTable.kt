package uz.robutpit.znatoki.feature.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import uz.robutpit.znatoki.R
import uz.robutpit.znatoki.design.Club
import uz.robutpit.znatoki.design.Lora
import uz.robutpit.znatoki.domain.game.SpinMath
import uz.robutpit.znatoki.domain.model.Sector
import uz.robutpit.znatoki.domain.model.SectorType
import uz.robutpit.znatoki.domain.model.SpinResult
import uz.robutpit.znatoki.sound.Sfx
import uz.robutpit.znatoki.sound.SoundFx
import kotlin.math.cos
import kotlin.math.sin

/**
 * Игровой стол с волчком. Клиент ДЕТЕРМИНИРОВАННО проигрывает анимацию к результату сервера:
 * угол(t) = SpinMath.angleAt(spin, serverNow − spin.startedAt). При лаге — анимация «догоняет»,
 * а не пересчитывается; финальный угол всегда точно совпадает с сектором сервера.
 */
@Composable
fun RouletteTable(
    sectors: List<Sector>,
    spin: SpinResult?,
    serverNow: () -> Long,
    highlight: Int?,
    sound: SoundFx?,
    effects: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onSpinFinished: (() -> Unit)? = null,
) {
    val n = sectors.size.coerceAtLeast(1)
    val angle = remember { mutableFloatStateOf(spin?.let { SpinMath.finalAngle(it) } ?: 0f) }
    var spinning by remember { mutableStateOf(false) }
    val glow = rememberInfiniteTransition(label = "glow")
    val glowA by glow.animateFloat(0.35f, 0.85f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "ga")

    LaunchedEffect(spin?.seed, spin?.startedAt) {
        val s = spin ?: return@LaunchedEffect
        val startLocal = if (s.startedAt > 0) s.startedAt else serverNow()
        val already = serverNow() - startLocal
        if (reduceMotion || already >= s.durationMs) {
            angle.floatValue = SpinMath.finalAngle(s)
            if (already < s.durationMs + 1500) sound?.play(Sfx.GONG)
            onSpinFinished?.invoke()
            return@LaunchedEffect
        }
        spinning = true
        var lastBoundary = SpinMath.boundaryIndex(SpinMath.angleAt(s, already, n), n)
        var lastTickAt = 0L
        while (true) {
            val elapsed = withFrameMillis { serverNow() - startLocal }
            val a = SpinMath.angleAt(s, elapsed, n)
            angle.floatValue = a
            val b = SpinMath.boundaryIndex(a, n)
            if (b != lastBoundary && effects) {
                // тик на каждом пересечении границы; темп падает вместе со скоростью сам собой
                if (elapsed - lastTickAt > 28) {
                    val p = elapsed.toFloat() / s.durationMs
                    sound?.play(Sfx.TICK, volume = 0.55f + 0.45f * p, rate = 1.15f - 0.3f * p)
                    if (p > 0.82f) sound?.vibrate(12, 60)
                    lastTickAt = elapsed
                }
                lastBoundary = b
            }
            if (elapsed >= s.durationMs) break
        }
        angle.floatValue = SpinMath.finalAngle(s)
        spinning = false
        if (effects) { sound?.play(Sfx.GONG); sound?.vibrate(50, 140) }
        onSpinFinished?.invoke()
    }

    val c = Club.colors
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val numStyle = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFF3DDA6))
    val numbers = remember(n, measurer) { (1..n).map { measurer.measure("$it", numStyle) } }
    val cd = stringResource(R.string.cd_roulette, highlight?.plus(1) ?: 0)

    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = cd }
            .drawWithCache {
                val r = size.minDimension / 2
                val center = Offset(size.width / 2, size.height / 2)
                val rimBrush = Brush.radialGradient(listOf(c.wood, c.woodDark), center, r)
                val feltBrush = Brush.radialGradient(listOf(c.felt, c.feltDark), center, r * 0.86f)
                val hubBrush = Brush.radialGradient(listOf(Color(0xFFF3DDA6), c.brass, c.brassDeep), center, r * 0.2f)
                val needle = Path().apply {
                    moveTo(center.x, center.y - r * 0.5f)
                    lineTo(center.x + r * 0.055f, center.y)
                    lineTo(center.x, center.y + r * 0.13f)
                    lineTo(center.x - r * 0.055f, center.y)
                    close()
                }
                val needleShadow = Path().apply { addPath(needle, Offset(r * 0.015f, r * 0.02f)) }
                val w = 360f / n
                val r1 = r * 0.5f
                val r2 = r * 0.84f
                // клинья секторов: путь строим один раз
                val wedges = List(n) { i -> wedgePath(center, r1, r2, -90f + i * w + 0.6f, w - 1.2f) }
                val bolt = boltPath(r * 0.06f)
                val star = starPath(r)
                val needleBrush = Brush.linearGradient(listOf(Color(0xFFF3DDA6), c.brass, c.brassDeep), center - Offset(0f, r * 0.5f), center)
                onDrawBehind {
                    drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.99f, center + Offset(0f, r * 0.02f))
                    drawCircle(rimBrush, r * 0.98f, center)
                    drawCircle(c.brass.copy(alpha = 0.7f), r * 0.9f, center, style = Stroke(r * 0.012f))
                    drawCircle(feltBrush, r * 0.86f, center)
                    for (i in 0 until n) {
                        val s = sectors.getOrNull(i)
                        val played = s?.played == true
                        val base = when (s?.type) {
                            SectorType.BLITZ -> Color(0xFF2D5E57)
                            SectorType.SUPERBLITZ -> Color(0xFF3A4F6A)
                            SectorType.BLACKBOX -> Color(0xFF202A2C)
                            SectorType.SPECIAL13 -> Color(0xFF5B3F2A)
                            else -> if (i % 2 == 0) Color(0xFF235650) else Color(0xFF1C4741)
                        }
                        drawPath(wedges[i], if (played) c.woodDark else base)
                        if (played) {
                            // «крышка» на сыгранном секторе
                            drawPath(wedges[i], c.wood.copy(alpha = 0.55f))
                            drawPath(wedges[i], Color.Black.copy(alpha = 0.25f), style = Stroke(r * 0.01f))
                        }
                        if (highlight == i && !spinning) drawPath(wedges[i], c.brass.copy(alpha = glowA * 0.6f))
                        drawPath(wedges[i], c.brass.copy(alpha = 0.35f), style = Stroke(r * 0.006f))
                        val mid = Math.toRadians((-90f + i * w + w / 2).toDouble())
                        val numR = r * 0.76f
                        val t = numbers[i]
                        val nx = center.x + (cos(mid) * numR).toFloat() - t.size.width / 2
                        val ny = center.y + (sin(mid) * numR).toFloat() - t.size.height / 2
                        drawText(t, topLeft = Offset(nx, ny), alpha = if (played) 0.35f else 1f)
                        val iconR = r * 0.61f
                        val ic = Offset(center.x + (cos(mid) * iconR).toFloat(), center.y + (sin(mid) * iconR).toFloat())
                        drawSectorIcon(s?.type ?: SectorType.NORMAL, ic, r, bolt, star, if (played) 0.3f else 1f, c.brass)
                    }
                    // волчок
                    rotate(angle.floatValue, center) {
                        drawPath(needleShadow, Color.Black.copy(alpha = 0.35f))
                        drawPath(needle, needleBrush)
                    }
                    drawCircle(Color.Black.copy(alpha = 0.3f), r * 0.17f, center + Offset(0f, r * 0.015f))
                    drawCircle(hubBrush, r * 0.16f, center)
                    drawCircle(c.woodDark, r * 0.06f, center)
                    drawCircle(c.brass, r * 0.035f, center)
                }
            }
    ) { }
}

private fun wedgePath(c: Offset, r1: Float, r2: Float, startDeg: Float, sweepDeg: Float): Path = Path().apply {
    arcTo(androidx.compose.ui.geometry.Rect(c, r2), startDeg, sweepDeg, true)
    arcTo(androidx.compose.ui.geometry.Rect(c, r1), startDeg + sweepDeg, -sweepDeg, false)
    close()
}

private fun boltPath(s: Float): Path = Path().apply {
    moveTo(s * 0.2f, -s); lineTo(-s * 0.55f, s * 0.15f); lineTo(-s * 0.05f, s * 0.15f)
    lineTo(-s * 0.25f, s); lineTo(s * 0.55f, -s * 0.2f); lineTo(s * 0.05f, -s * 0.2f); close()
}

private fun DrawScope.drawSectorIcon(type: SectorType, at: Offset, r: Float, bolt: Path, star: Path, alpha: Float, brass: Color) {
    val col = brass.copy(alpha = alpha)
    when (type) {
        SectorType.NORMAL -> {
            // конверт
            val w = r * 0.075f; val h = r * 0.05f
            drawRect(col, Offset(at.x - w, at.y - h), Size(w * 2, h * 2), style = Stroke(r * 0.01f))
            drawLine(col, Offset(at.x - w, at.y - h), at, r * 0.01f)
            drawLine(col, Offset(at.x + w, at.y - h), at, r * 0.01f)
        }
        SectorType.BLITZ -> translate(at.x, at.y) { drawPath(bolt, col) }
        SectorType.SUPERBLITZ -> {
            translate(at.x - r * 0.04f, at.y) { drawPath(bolt, col) }
            translate(at.x + r * 0.04f, at.y) { drawPath(bolt, col) }
        }
        SectorType.BLACKBOX -> {
            val s = r * 0.065f
            drawRect(Color.Black.copy(alpha = alpha), Offset(at.x - s, at.y - s), Size(s * 2, s * 2))
            drawRect(col, Offset(at.x - s, at.y - s), Size(s * 2, s * 2), style = Stroke(r * 0.01f))
        }
        SectorType.SPECIAL13 -> translate(at.x, at.y) { drawPath(star, col) }
    }
}

private fun starPath(r: Float): Path = Path().apply {
    for (k in 0 until 10) {
        val rr = if (k % 2 == 0) r * 0.075f else r * 0.032f
        val a = Math.toRadians(-90.0 + k * 36)
        val x = (cos(a) * rr).toFloat(); val y = (sin(a) * rr).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Анимированный чёрный ящик: замок + крышка, которая открывается на reveal. */
@Composable
fun BlackBox(open: Boolean, modifier: Modifier = Modifier) {
    val lid by animateFloatAsState(if (open) -115f else 0f, spring(dampingRatio = 0.45f, stiffness = 120f), label = "lid")
    val shake = rememberInfiniteTransition(label = "shake")
    val wob by shake.animateFloat(-2f, 2f, infiniteRepeatable(tween(220), RepeatMode.Reverse), label = "w")
    val c = Club.colors
    Canvas(modifier.aspectRatio(1.3f)) {
        val w = size.width; val h = size.height
        val bodyTop = h * 0.42f
        rotate(if (open) 0f else wob * 0.3f, Offset(w / 2, h)) {
            drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(w * 0.12f, bodyTop + h * 0.04f), Size(w * 0.78f, h * 0.52f), androidx.compose.ui.geometry.CornerRadius(w * 0.03f))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2A2F31), Color(0xFF0E1112)), bodyTop, h * 0.95f), Offset(w * 0.1f, bodyTop), Size(w * 0.8f, h * 0.52f), androidx.compose.ui.geometry.CornerRadius(w * 0.03f))
            drawRoundRect(c.brass, Offset(w * 0.1f, bodyTop), Size(w * 0.8f, h * 0.52f), androidx.compose.ui.geometry.CornerRadius(w * 0.03f), style = Stroke(w * 0.012f))
            if (open) drawRect(Brush.verticalGradient(listOf(c.brass.copy(alpha = 0.55f), Color.Transparent), bodyTop - h * 0.3f, bodyTop + h * 0.1f), Offset(w * 0.12f, bodyTop - h * 0.3f), Size(w * 0.76f, h * 0.32f))
            rotate(lid, Offset(w * 0.1f, bodyTop)) {
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF353B3D), Color(0xFF1A1E1F)), bodyTop - h * 0.16f, bodyTop), Offset(w * 0.08f, bodyTop - h * 0.16f), Size(w * 0.84f, h * 0.16f), androidx.compose.ui.geometry.CornerRadius(w * 0.03f))
                drawRoundRect(c.brass, Offset(w * 0.08f, bodyTop - h * 0.16f), Size(w * 0.84f, h * 0.16f), androidx.compose.ui.geometry.CornerRadius(w * 0.03f), style = Stroke(w * 0.012f))
            }
            if (!open) {
                drawRoundRect(c.brass, Offset(w * 0.44f, bodyTop - h * 0.03f), Size(w * 0.12f, h * 0.14f), androidx.compose.ui.geometry.CornerRadius(w * 0.015f))
                drawArc(c.brass, 180f, 180f, false, Offset(w * 0.46f, bodyTop - h * 0.1f), Size(w * 0.08f, h * 0.12f), style = Stroke(w * 0.014f))
                drawCircle(Color(0xFF1A1405), w * 0.012f, Offset(w * 0.5f, bodyTop + h * 0.04f))
            }
        }
    }
}
