// ASTRA's mystical layer (signed-off direction: celestial + yantra, rich but calm motion).
// Cosmic background with twinkling stars, glimmers and a rare shooting star; a turning yantra mandala;
// ornate gold frames with filigree corners and a passing glint. Everything stops when the phone asks for reduced motion.
package app.astra.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

val GoldLight = Color(0xFFFBE7B2)
val Gold = Color(0xFFF7D794)
val GoldDeep = Color(0xFFC9A45C)
val Amber = Color(0xFFE8A87C)
val Nebula = Color(0xFF2A1B4E)
val NebulaBright = Color(0xFF602D89)

/** Gold-leaf gradient for primary actions and highlights. */
val GoldLeaf: Brush get() = Brush.linearGradient(listOf(GoldLight, Gold, GoldDeep))

/** Ambient motion switch: emulator tests turn it off so screens settle (same path as reduced motion). */
object MysticMotion { var enabled by androidx.compose.runtime.mutableStateOf(true) }

/** True when the phone's animator scale is 0 (Settings → Accessibility → Remove animations), or motion is switched off. */
@Composable
fun reducedMotion(): Boolean {
    val ctx = LocalContext.current
    val off = remember { runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
    return off || !MysticMotion.enabled
}

/** A looping 0..1 clock, or a fixed value when motion is reduced. */
@Composable
fun mysticClock(periodMs: Int, still: Float = 0.3f, label: String = "clock"): State<Float> {
    if (reducedMotion()) return remember { mutableFloatStateOf(still) }
    val t = rememberInfiniteTransition(label = label)
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = label)
}

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Float, val gold: Boolean)
private class Glimmer(val x: Float, val y: Float, val size: Float, val phase: Float)

/**
 * Deep-sky background: nebula glow, twinkling and slowly drifting stars, a few four-point glimmers, and a shooting star
 * now and then. Light theme: faint gold stars on parchment.
 */
@Composable
fun CosmicBackground(modifier: Modifier = Modifier, seed: Int = 7, stars: Int = 90, glimmers: Int = 7, nebula: Boolean = true, content: @Composable BoxScope.() -> Unit = {}) {
    val c = Astra.colors
    val field = remember(seed, stars) {
        val r = Random(seed)
        List(stars) { Star(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat() * 1.3f, r.nextFloat(), 0.5f + r.nextFloat() * 1.5f, r.nextFloat() > 0.85f) }
    }
    val sparks = remember(seed, glimmers) { val r = Random(seed * 31 + 1); List(glimmers) { Glimmer(r.nextFloat(), r.nextFloat() * 0.85f, 4f + r.nextFloat() * 6f, r.nextFloat()) } }
    val clock by mysticClock(24_000, label = "sky")
    Box(modifier.drawBehind {
        if (nebula) {
            drawRect(Brush.radialGradient(listOf(if (c.dark) Nebula.copy(alpha = 0.9f) else c.lavender.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.78f, size.height * 0.08f), radius = size.maxDimension * 0.65f))
            drawRect(Brush.radialGradient(listOf(if (c.dark) NebulaBright.copy(alpha = 0.22f) else GoldDeep.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.55f), radius = size.maxDimension * 0.5f))
        }
        val t = clock
        field.forEach { s ->
            val tw = 0.5f + 0.5f * sin(2f * PI.toFloat() * (t * s.speed * 6f + s.phase))
            val x = ((s.x + t * 0.04f * s.speed) % 1f) * size.width
            val y = s.y * size.height
            val base = if (c.dark) 0.25f + 0.65f * tw else 0.12f + 0.25f * tw
            drawCircle(if (s.gold || !c.dark) GoldDeep.let { if (c.dark) Gold else it } else c.text, s.r.dp.toPx() * (0.8f + 0.3f * tw), Offset(x, y), alpha = base)
        }
        sparks.forEach { g ->
            val p = ((t * 3f + g.phase) % 1f)
            val a = if (p < 0.12f) sin(p / 0.12f * PI.toFloat()) else 0f
            if (a > 0.01f) sparkle(Offset(g.x * size.width, g.y * size.height), g.size.dp.toPx() * (0.6f + 0.4f * a), if (c.dark) GoldLight else GoldDeep, a)
        }
        // A shooting star crosses once per cycle.
        val sp = (t * 2f) % 1f
        if (c.dark && sp < 0.06f) {
            val k = sp / 0.06f
            val start = Offset(size.width * 0.15f, size.height * 0.05f)
            val end = Offset(size.width * 0.75f, size.height * 0.28f)
            val head = Offset(start.x + (end.x - start.x) * k, start.y + (end.y - start.y) * k)
            val tail = Offset(head.x - (end.x - start.x) * 0.18f, head.y - (end.y - start.y) * 0.18f)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, GoldLight.copy(alpha = 0.9f * (1 - k))), tail, head), tail, head, 2.dp.toPx(), StrokeCap.Round)
        }
    }, content = content)
}

/** A four-point star glimmer. */
fun DrawScope.sparkle(at: Offset, r: Float, color: Color, alpha: Float = 1f) {
    val p = Path().apply {
        moveTo(at.x, at.y - r); quadraticTo(at.x, at.y, at.x + r, at.y); quadraticTo(at.x, at.y, at.x, at.y + r)
        quadraticTo(at.x, at.y, at.x - r, at.y); quadraticTo(at.x, at.y, at.x, at.y - r); close()
    }
    drawPath(p, color, alpha = alpha)
    drawCircle(color, r * 0.18f, at, alpha = alpha)
}

/** A yantra mandala: two lotus rings turning in opposite directions, interlocking triangles and the bindu. */
@Composable
fun Mandala(modifier: Modifier = Modifier, color: Color = Gold, accent: Color = Astra.colors.lavender, alpha: Float = 1f) {
    val spin by mysticClock(110_000, label = "mandala")
    Canvas(modifier) {
        val cx = size.width / 2; val cy = size.height / 2
        val R = size.minDimension / 2
        val sw = (R / 150f).coerceAtLeast(0.6f)
        rotate(spin * 360f, Offset(cx, cy)) {
            drawCircle(color, R * 0.98f, Offset(cx, cy), alpha = 0.8f * alpha, style = Stroke(sw))
            drawCircle(color, R * 0.88f, Offset(cx, cy), alpha = 0.45f * alpha, style = Stroke(sw * 0.6f))
            for (i in 0 until 27) { val a = i * 2 * PI / 27; drawCircle(accent, sw * 1.6f, Offset(cx + (R * 0.93f * cos(a)).toFloat(), cy + (R * 0.93f * sin(a)).toFloat()), alpha = 0.8f * alpha) }
            petals(cx, cy, R * 0.86f, R * 0.6f, 16, R * 0.09f, color, sw, 0.06f * alpha, alpha)
        }
        rotate(-spin * 480f, Offset(cx, cy)) {
            drawCircle(color, R * 0.58f, Offset(cx, cy), alpha = 0.7f * alpha, style = Stroke(sw))
            petals(cx, cy, R * 0.56f, R * 0.32f, 8, R * 0.13f, accent, sw, 0.12f * alpha, alpha)
        }
        drawCircle(color, R * 0.3f, Offset(cx, cy), alpha = 0.8f * alpha, style = Stroke(sw))
        triangle(cx, cy, R * 0.27f, true, color, sw, alpha); triangle(cx, cy, R * 0.27f, false, color, sw, alpha)
        triangle(cx, cy, R * 0.17f, true, accent, sw, alpha); triangle(cx, cy, R * 0.17f, false, accent, sw, alpha)
        drawCircle(color, R * 0.035f, Offset(cx, cy), alpha = alpha)
    }
}

private fun DrawScope.petals(cx: Float, cy: Float, rOut: Float, rIn: Float, n: Int, w: Float, color: Color, sw: Float, fillA: Float, alpha: Float) {
    for (i in 0 until n) {
        val a = i * 2 * PI / n - PI / 2
        val ux = cos(a).toFloat(); val uy = sin(a).toFloat(); val px = -uy; val py = ux
        val tip = Offset(cx + ux * rOut, cy + uy * rOut); val base = Offset(cx + ux * rIn, cy + uy * rIn)
        val mid = (rOut + rIn) / 2
        val p = Path().apply {
            moveTo(tip.x, tip.y)
            quadraticTo(cx + ux * mid + px * w, cy + uy * mid + py * w, base.x, base.y)
            quadraticTo(cx + ux * mid - px * w, cy + uy * mid - py * w, tip.x, tip.y); close()
        }
        drawPath(p, color, alpha = fillA)
        drawPath(p, color, alpha = 0.85f * alpha, style = Stroke(sw))
    }
}

private fun DrawScope.triangle(cx: Float, cy: Float, r: Float, up: Boolean, color: Color, sw: Float, alpha: Float) {
    val s = if (up) -1 else 1
    val p = Path().apply { moveTo(cx, cy + s * r); lineTo(cx + r * 0.866f, cy - s * r / 2); lineTo(cx - r * 0.866f, cy - s * r / 2); close() }
    drawPath(p, color, alpha = 0.9f * alpha, style = Stroke(sw * 1.1f))
}

/**
 * Ornate frame for cards and sheets: a gold hairline, a lavender inner line, filigree flourishes in each corner
 * with a gold bead, and (when [glint]) a soft light that passes along the top edge every few seconds.
 */
fun Modifier.ornate(radius: Dp = 18.dp, glint: Boolean = false, strong: Boolean = false): Modifier = composed {
    val c = Astra.colors
    val clock by mysticClock(7_000, still = 2f, label = "glint")
    drawWithContent {
        drawContent()
        val r = radius.toPx(); val sw = 1.dp.toPx()
        val gold = if (c.dark) Gold else GoldDeep
        drawRoundRect(gold, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), CornerRadius(r), alpha = if (strong) 0.75f else 0.42f, style = Stroke(sw))
        val i = 4.dp.toPx()
        drawRoundRect(c.lavender, Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i), CornerRadius((r - i).coerceAtLeast(2f)), alpha = 0.22f, style = Stroke(sw * 0.6f))
        val k = 14.dp.toPx(); val d = 7.dp.toPx()
        for ((sx, sy) in listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f)) {
            val x0 = if (sx > 0) i else size.width - i; val y0 = if (sy > 0) i else size.height - i
            val p = Path().apply {
                moveTo(x0, y0 + k * sy); cubicTo(x0 + k * 0.2f * sx, y0 + k * 0.45f * sy, x0 + k * 0.45f * sx, y0 + k * 0.2f * sy, x0 + k * sx, y0)
            }
            drawPath(p, gold, alpha = 0.8f, style = Stroke(sw))
            drawCircle(gold, 1.6.dp.toPx(), Offset(x0 + d * sx, y0 + d * sy), alpha = 0.9f)
        }
        if (glint && clock < 1f) {
            val p = clock
            if (p < 0.35f) {
                val x = size.width * (p / 0.35f) * 1.3f - size.width * 0.15f
                val w = size.width * 0.25f
                drawLine(Brush.horizontalGradient(listOf(Color.Transparent, GoldLight.copy(alpha = 0.95f), Color.Transparent), x - w / 2, x + w / 2),
                    Offset(x - w / 2, sw), Offset(x + w / 2, sw), sw * 2.2f)
                sparkle(Offset(x, sw), 5.dp.toPx(), GoldLight, 0.9f)
            }
        }
    }
}

/** Surface fill for ornate cards: a soft indigo-to-nebula gradient (parchment in light mode). */
val CardFill: Brush @Composable get() = Astra.colors.let { c ->
    if (c.dark) Brush.verticalGradient(listOf(Color(0xFF2A1B4E).copy(alpha = 0.92f), Color(0xFF1B1446).copy(alpha = 0.94f)))
    else Brush.verticalGradient(listOf(Color(0xFFFFFDF8), Color(0xFFF9F2E3)))
}

/** Distance helper used by glimmer layouts. */
fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)
