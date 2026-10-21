// ASTRA oracle cards (own designs): Yes / No (sun, moon, eclipse), Guidance (lotus crest), Vibe (colour field and
// element), Nakshatra (stylised star pattern). Same proportions and frame language as the classic tarot.
package app.astra.ui.tarot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.astra.core.Element
import app.astra.core.Leaning
import app.astra.core.Oracle
import app.astra.core.OracleCard
import app.astra.core.OracleDeck
import app.astra.ui.theme.Cinzel
import app.astra.ui.theme.Gold
import app.astra.ui.theme.GoldLight
import app.astra.ui.theme.Inter
import app.astra.ui.theme.Playfair
import app.astra.ui.theme.mysticClock
import app.astra.ui.theme.ornate
import app.astra.ui.theme.reducedMotion
import app.astra.ui.theme.sparkle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val MOON = Color(0xFFF5F3FF)
private val LAV = Color(0xFFC4B2FF)

@Composable
fun OracleCardView(card: OracleCard?, faceUp: Boolean, width: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val still = reducedMotion()
    val turn by animateFloatAsState(if (faceUp) 180f else 0f, if (still) tween(0) else tween(650), label = "oracleFlip")
    val showFace = turn > 90f && card != null
    val radius = width * 0.07f
    Box(
        modifier.width(width).aspectRatio(TarotArt.ASPECT)
            .graphicsLayer { rotationY = turn; cameraDistance = 14f * density }
            .shadow(width * 0.06f, RoundedCornerShape(radius), ambientColor = Color.Black, spotColor = Color.Black)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = if (!faceUp || card == null) "Face-down card" else "${card.title}. ${card.line}" }
    ) {
        if (!showFace) Image(painterResource(TarotArt.BACK), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        else Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.clip(RoundedCornerShape(radius)).ornate(radius, glint = true, strong = true)) {
            when (card!!.deck) {
                OracleDeck.YES_NO -> YesNoFace(card)
                OracleDeck.GUIDANCE -> GuidanceFace(card)
                OracleDeck.VIBE -> VibeFace(card)
                OracleDeck.NAKSHATRA -> NakshatraFace(card)
            }
        }
    }
}

@Composable
private fun Face(bg: Brush, content: @Composable (Dp) -> Unit) = BoxWithConstraints(Modifier.fillMaxSize().background(bg)) { content(maxWidth) }

private fun t(w: Dp, k: Float) = (w.value * k).sp

@Composable
private fun YesNoFace(card: OracleCard) = Face(
    when (card.leaning) {
        Leaning.YES -> Brush.radialGradient(listOf(Color(0xFFFBE7B2), Color(0xFFC9A45C), Color(0xFF2A1B4E), Color(0xFF140F38)))
        Leaning.NO -> Brush.radialGradient(listOf(Color(0xFF3A215F), Color(0xFF1B1446), Color(0xFF0B0A26)))
        else -> Brush.radialGradient(listOf(Color(0xFF3B2A7A), Color(0xFF1B1446), Color(0xFF0B0A26)))
    }
) { w ->
    val spin by mysticClock(30_000, label = "rays")
    Column(Modifier.fillMaxSize().padding(w * 0.1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension * 0.24f
            when (card.leaning) {
                Leaning.YES -> {
                    drawCircle(GoldLight, r, c)
                    for (i in 0 until 16) { val a = (i * 22.5 + spin * 360) * PI / 180; val l = if (i % 2 == 0) 1.9f else 1.55f
                        drawLine(GoldLight, Offset(c.x + (r * 1.25f * cos(a)).toFloat(), c.y + (r * 1.25f * sin(a)).toFloat()), Offset(c.x + (r * l * cos(a)).toFloat(), c.y + (r * l * sin(a)).toFloat()), size.width * 0.012f) }
                }
                Leaning.NO -> {
                    drawCircle(Brush.radialGradient(listOf(GoldLight.copy(alpha = 0.9f), Color.Transparent), c, r * 1.8f), r * 1.8f, c)
                    drawCircle(Color(0xFF0B0A26), r, c)
                }
                else -> {
                    val p = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
                    val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r * 0.45f, c.y - r * 1.05f, c.x + r * 1.55f, c.y + r * 0.95f)) }
                    drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, p, cut), LAV)
                }
            }
            sparkle(Offset(size.width * 0.15f, size.height * 0.2f), size.width * 0.04f, GoldLight, 0.8f)
            sparkle(Offset(size.width * 0.85f, size.height * 0.75f), size.width * 0.03f, GoldLight, 0.7f)
        }
        Text(card.title, style = TextStyle(fontFamily = Cinzel, fontSize = t(w, if (card.title.length > 6) 0.11f else 0.17f), letterSpacing = t(w, 0.02f),
            color = if (card.leaning == Leaning.YES) Color(0xFFFFF3D2) else LAV), textAlign = TextAlign.Center)
        Text(card.line, style = TextStyle(fontFamily = Inter, fontSize = t(w, 0.065f), color = MOON.copy(alpha = 0.9f)), textAlign = TextAlign.Center)
    }
}

@Composable
private fun GuidanceFace(card: OracleCard) = Face(Brush.verticalGradient(listOf(Color(0xFF1B1446), Color(0xFF2A1B4E), Color(0xFF3A215F)))) { w ->
    Column(Modifier.fillMaxSize().padding(w * 0.1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(2f)) {
            val cx = size.width / 2; val cy = size.height * 0.62f; val s = size.width / 120f
            val petal = Path().apply { moveTo(cx, cy - 34 * s); quadraticTo(cx + 12 * s, cy - 12 * s, cx, cy + 4 * s); quadraticTo(cx - 12 * s, cy - 12 * s, cx, cy - 34 * s); close() }
            drawPath(petal, Gold, alpha = 0.25f); drawPath(petal, Gold, style = Stroke(s))
            for (d in listOf(-1f, 1f)) {
                val p1 = Path().apply { moveTo(cx, cy + 4 * s); quadraticTo(cx + d * 22 * s, cy - 2 * s, cx + d * 28 * s, cy - 22 * s); quadraticTo(cx + d * 10 * s, cy - 16 * s, cx, cy + 4 * s) }
                val p2 = Path().apply { moveTo(cx, cy + 4 * s); quadraticTo(cx + d * 30 * s, cy + 6 * s, cx + d * 42 * s, cy - 6 * s); quadraticTo(cx + d * 20 * s, cy - 8 * s, cx, cy + 4 * s) }
                drawPath(p1, Gold, style = Stroke(s)); drawPath(p2, LAV, style = Stroke(s))
            }
            drawLine(Gold.copy(alpha = 0.7f), Offset(size.width * 0.08f, cy + 10 * s), Offset(size.width * 0.92f, cy + 10 * s), s * 0.6f)
            sparkle(Offset(cx, cy - 44 * s), 4 * s, GoldLight)
        }
        Text(card.title, style = TextStyle(fontFamily = Cinzel, fontSize = t(w, 0.065f), letterSpacing = t(w, 0.025f), color = Gold), textAlign = TextAlign.Center)
        Text(card.line, style = TextStyle(fontFamily = Playfair, fontStyle = FontStyle.Italic, fontSize = t(w, 0.1f), lineHeight = t(w, 0.13f), color = MOON), textAlign = TextAlign.Center)
        Text("a message to carry today", style = TextStyle(fontFamily = Inter, fontSize = t(w, 0.05f), color = MOON.copy(alpha = 0.7f)))
    }
}

@Composable
private fun VibeFace(card: OracleCard) {
    val a = Color(card.colors[0]); val b = Color(card.colors[1])
    val turn by mysticClock(40_000, label = "vibe")
    Box(Modifier.fillMaxSize().background(Brush.sweepGradient(listOf(a, b, a.copy(alpha = 0.8f), b, a)))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xD9080F24)), Offset(size.width / 2, size.height * 0.42f), size.maxDimension * 0.62f))
            val c = Offset(size.width / 2, size.height * 0.32f); val r = size.width * 0.16f
            val up = card.element == Element.FIRE || card.element == Element.AIR
            val col = Color(0xFFFFF3D2)
            if (card.element == Element.ETHER) drawCircle(col, r, c, style = Stroke(size.width * 0.015f))
            else {
                val s = if (up) -1 else 1
                val p = Path().apply { moveTo(c.x, c.y + s * r); lineTo(c.x + r * 0.95f, c.y - s * r * 0.6f); lineTo(c.x - r * 0.95f, c.y - s * r * 0.6f); close() }
                drawPath(p, col, style = Stroke(size.width * 0.015f))
                if (card.element == Element.AIR || card.element == Element.EARTH) drawLine(col, Offset(c.x - r * 0.6f, c.y - s * r * 0.05f), Offset(c.x + r * 0.6f, c.y - s * r * 0.05f), size.width * 0.015f)
            }
            for (i in 0 until 6) { val ang = (i * 60 + turn * 360) * PI / 180
                sparkle(Offset(c.x + (r * 2f * cos(ang)).toFloat(), c.y + (r * 2f * sin(ang)).toFloat()), size.width * 0.02f, col, 0.6f) }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            Column(Modifier.fillMaxSize().padding(w * 0.1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(card.title, style = TextStyle(fontFamily = Cinzel, fontSize = t(w, if (card.title.length > 8) 0.1f else 0.13f), letterSpacing = t(w, 0.02f), color = MOON), textAlign = TextAlign.Center)
                Text("${card.element?.label} · ${card.line}", style = TextStyle(fontFamily = Inter, fontSize = t(w, 0.06f), color = MOON.copy(alpha = 0.9f)), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = w * 0.03f))
            }
        }
    }
}

@Composable
private fun NakshatraFace(card: OracleCard) = Face(Brush.radialGradient(listOf(Color(0xFF23306B), Color(0xFF121A45), Color(0xFF080F24)))) { w ->
    val n = card.nakshatra!!
    val pts = Oracle.starPattern(n)
    val tw by mysticClock(6_000, label = "naktw")
    Column(Modifier.fillMaxSize().padding(w * 0.09f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Text("${n.ordinal + 1} · ${card.title}", style = TextStyle(fontFamily = Cinzel, fontSize = t(w, 0.06f), letterSpacing = t(w, 0.02f), color = Gold), textAlign = TextAlign.Center)
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.15f)) {
            val sorted = pts.sortedBy { it.first }
            for (i in 0 until sorted.size - 1) drawLine(LAV.copy(alpha = 0.6f), Offset(sorted[i].first * size.width, sorted[i].second * size.height),
                Offset(sorted[i + 1].first * size.width, sorted[i + 1].second * size.height), size.width * 0.006f)
            sorted.forEachIndexed { i, (x, y) ->
                val a = 0.65f + 0.35f * sin((tw * 2 * PI + i).toFloat())
                drawCircle(if (i == sorted.size / 2) Color(0xFFFFB27A) else MOON, size.width * (if (i == sorted.size / 2) 0.035f else 0.022f), Offset(x * size.width, y * size.height), alpha = a)
            }
        }
        Text(card.meaning, style = TextStyle(fontFamily = Playfair, fontSize = t(w, 0.085f), color = MOON), textAlign = TextAlign.Center)
        Text("Ruled by ${n.lord.english} · ${card.symbol}\n${card.line}", style = TextStyle(fontFamily = Inter, fontSize = t(w, 0.05f), lineHeight = t(w, 0.07f), color = MOON.copy(alpha = 0.85f)), textAlign = TextAlign.Center)
    }
}
