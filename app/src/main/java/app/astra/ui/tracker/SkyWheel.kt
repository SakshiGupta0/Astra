// The sky wheel on the Graha Tracker: the 12 signs and 27 nakshatras as a gold ring, every graha as a glowing bead
// at its real sidereal longitude, and your Moon at the centre. The wheel turns very slowly; beads pulse.
package app.astra.ui.tracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.astra.core.Graha
import app.astra.core.NowPosition
import app.astra.core.Rashi
import app.astra.ui.Fmt
import app.astra.ui.theme.Astra
import app.astra.ui.theme.Cinzel
import app.astra.ui.theme.Gold
import app.astra.ui.theme.GoldDeep
import app.astra.ui.theme.GoldLight
import app.astra.ui.theme.Inter
import app.astra.ui.theme.mysticClock
import app.astra.ui.theme.sparkle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SkyWheel(positions: List<NowPosition>, moonSign: Rashi, modifier: Modifier = Modifier) {
    val c = Astra.colors
    val measurer = rememberTextMeasurer()
    val spin by mysticClock(600_000, still = 0f, label = "wheel")
    val pulse by mysticClock(3_500, label = "beads")
    val gold = if (c.dark) Gold else GoldDeep
    val desc = "Sky wheel: " + positions.joinToString("; ") { "${it.graha.english} in ${it.rashi.sanskrit}" } + ". Your Moon sign is ${moonSign.sanskrit}."
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = desc }) {
        val cx = size.width / 2; val cy = size.height / 2; val R = size.minDimension / 2 * 0.96f
        val sw = (R / 160f).coerceAtLeast(0.8f)
        // Longitude 0 (start of Mesha) at the top, increasing clockwise; the whole wheel turns very slowly.
        fun at(lon: Double, r: Float): Offset { val a = (lon - 90.0) * PI / 180; return Offset(cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat()) }
        rotate(spin * 360f, Offset(cx, cy)) {
            drawCircle(Brush.radialGradient(listOf(c.purple.copy(alpha = if (c.dark) 0.35f else 0.08f), Color.Transparent), Offset(cx, cy), R), R, Offset(cx, cy))
            drawCircle(gold, R, Offset(cx, cy), alpha = 0.8f, style = Stroke(sw))
            drawCircle(gold, R * 0.84f, Offset(cx, cy), alpha = 0.8f, style = Stroke(sw))
            for (i in 0 until 12) {
                drawLine(gold, at(i * 30.0, R * 0.84f), at(i * 30.0, R), sw, alpha = 0.8f)
                val label = Rashi.entries[i].sanskrit.uppercase()
                val style = TextStyle(fontFamily = Cinzel, fontSize = (R / 15f / density).sp, color = if (Rashi.entries[i] == moonSign) GoldLight else gold.copy(alpha = 0.85f))
                val layout = measurer.measure(label, style)
                val p = at(i * 30.0 + 15.0, R * 0.92f)
                rotate((i * 30f + 15f), p) { drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y - layout.size.height / 2)) }
            }
            for (i in 0 until 27) drawCircle(c.lavender, sw * 1.5f, at(i * 360.0 / 27, R * 0.78f), alpha = 0.8f)
            drawCircle(c.lavender, R * 0.72f, Offset(cx, cy), alpha = 0.35f, style = Stroke(sw * 0.6f))
            // Your Moon sign's segment glows
            val path = Path().apply {
                val a0 = moonSign.ordinal * 30.0; val steps = 12
                moveTo(at(a0, R * 0.84f).x, at(a0, R * 0.84f).y)
                for (k in 0..steps) { val p = at(a0 + 30.0 * k / steps, R); lineTo(p.x, p.y) }
                for (k in steps downTo 0) { val p = at(a0 + 30.0 * k / steps, R * 0.84f); lineTo(p.x, p.y) }
                close()
            }
            drawPath(path, gold, alpha = 0.16f)
            // Planets
            positions.forEachIndexed { i, p ->
                val r = R * (0.62f - (i % 3) * 0.075f)
                val o = at(p.lon, r)
                val col = when (p.graha) {
                    Graha.SUN -> Color(0xFFE8A87C); Graha.MOON -> Color(0xFFF5F3FF); Graha.JUPITER -> GoldLight
                    Graha.SATURN -> Color(0xFF8E93C8); Graha.VENUS -> Color(0xFFF5C6E0); Graha.MARS -> Color(0xFFE07A5F)
                    Graha.MERCURY -> Color(0xFF9AD0EC); else -> c.lavender
                }
                val glow = 0.6f + 0.4f * sin((pulse * 2 * PI + i).toFloat())
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f * glow), Color.Transparent), o, R * 0.09f), R * 0.09f, o)
                drawCircle(col, R * 0.045f, o)
                val t = measurer.measure(Fmt.abbrev(p.graha), TextStyle(fontFamily = Inter, fontSize = (R / 20f / density).sp, color = Color(0xFF14102E)))
                rotate(-spin * 360f, o) { drawText(t, topLeft = Offset(o.x - t.size.width / 2, o.y - t.size.height / 2)) }
            }
        }
        // Centre: your Moon
        drawCircle(if (c.dark) Color(0xFF1B1446) else Color(0xFFFFFDF8), R * 0.32f, Offset(cx, cy))
        drawCircle(gold, R * 0.32f, Offset(cx, cy), style = Stroke(sw))
        val mr = R * 0.13f
        val moon = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(cx - mr, cy - mr - R * 0.03f, cx + mr, cy + mr - R * 0.03f))
        }
        val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - mr * 0.4f, cy - mr * 1.15f - R * 0.03f, cx + mr * 1.6f, cy + mr * 0.85f - R * 0.03f)) }
        drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, moon, cut), if (c.dark) Color(0xFFF5F3FF) else c.purple)
        val label = measurer.measure("your Moon · ${moonSign.sanskrit}", TextStyle(fontFamily = Inter, fontSize = (R / 18f / density).sp, color = c.textSecondary))
        drawText(label, topLeft = Offset(cx - label.size.width / 2, cy + R * 0.14f))
        sparkle(Offset(cx + R * 0.2f, cy - R * 0.18f), R * 0.025f, gold, 0.9f)
    }
}
