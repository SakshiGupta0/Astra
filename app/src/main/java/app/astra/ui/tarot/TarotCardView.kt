// A classic tarot card (Pamela Colman Smith, 1909) in the ASTRA frame: dark mat, gold rule, lavender hairline,
// a slow gold glint and corner glimmers. Face down shows ASTRA's yantra back. Reversed cards turn the whole card.
package app.astra.ui.tarot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.astra.core.TarotCard
import app.astra.ui.theme.Gold
import app.astra.ui.theme.GoldLight
import app.astra.ui.theme.mysticClock
import app.astra.ui.theme.ornate
import app.astra.ui.theme.reducedMotion
import app.astra.ui.theme.sparkle

/**
 * A card at [width]. The flip is a 3D turn on its edge (skipped with reduced motion); [glow] lifts a chosen card.
 */
@Composable
fun TarotCardView(card: TarotCard?, reversed: Boolean, faceUp: Boolean, width: Dp, modifier: Modifier = Modifier, glow: Boolean = false, onClick: (() -> Unit)? = null) {
    val still = reducedMotion()
    val turn by animateFloatAsState(if (faceUp) 180f else 0f, if (still) tween(0) else tween(650), label = "flip")
    val lift by animateFloatAsState(if (glow) 1f else 0f, if (still) tween(0) else tween(400), label = "lift")
    val showFace = turn > 90f && card != null
    val label = when {
        card == null || !faceUp -> "Face-down card"
        else -> card.name + if (reversed) ", reversed" else ""
    }
    val radius = width * 0.07f
    Box(
        modifier.width(width).aspectRatio(TarotArt.ASPECT)
            .graphicsLayer { rotationY = turn; cameraDistance = 14f * density; translationY = -lift * 14.dp.toPx() }
            .shadow((8 + 14 * lift).dp, RoundedCornerShape(radius), ambientColor = Color.Black, spotColor = if (lift > 0f) Gold else Color.Black)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label }
    ) {
        if (!showFace) Image(painterResource(TarotArt.BACK), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        else Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f; rotationZ = if (reversed) 180f else 0f }) { CardFace(card!!, radius, width) }
        if (lift > 0f) Box(Modifier.fillMaxSize().ornate(radius, strong = true))
    }
}

@Composable
private fun CardFace(card: TarotCard, radius: Dp, width: Dp) {
    val glint by mysticClock(8_000, still = 2f, label = "cardGlint")
    val mat = width * 0.045f
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(Color(0xFF231A55), Color(0xFF0D0A2A))))
            .ornate(radius, strong = true)
            .padding(mat)
    ) {
        Image(painterResource(TarotArt.of(card.id)), null,
            Modifier.fillMaxSize().clip(RoundedCornerShape(radius * 0.45f))
                .drawWithContent {
                    drawContent()
                    if (glint < 0.25f) {
                        val p = glint / 0.25f
                        val x = size.width * (p * 1.6f - 0.3f)
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, GoldLight.copy(alpha = 0.38f), Color.Transparent),
                            Offset(x - size.width * 0.25f, 0f), Offset(x + size.width * 0.25f, size.height * 0.4f)))
                        sparkle(Offset(x, size.height * 0.2f + p * size.height * 0.5f), size.width * 0.05f, GoldLight, (1f - kotlin.math.abs(p - 0.5f) * 2f).coerceIn(0f, 1f))
                    }
                },
            contentScale = ContentScale.FillBounds)
    }
}

/** Small static card for lists (saved readings). */
@Composable
fun MiniCard(card: TarotCard, reversed: Boolean, width: Dp = 44.dp) =
    Box(Modifier.width(width).aspectRatio(TarotArt.ASPECT).rotate(if (reversed) 180f else 0f).clip(RoundedCornerShape(4.dp))) {
        Image(painterResource(TarotArt.of(card.id)), card.name, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    }
