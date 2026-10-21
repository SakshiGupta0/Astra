// A tarot card from astra-deck-1: original art from TarotArt, with the numeral and name set in the card's own bands.
package app.astra.ui.tarot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.core.Arcana
import app.astra.core.TarotCard
import app.astra.ui.theme.Inter
import app.astra.ui.theme.Playfair

private val ROMAN = listOf("0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX", "XXI")

fun cardNumeral(c: TarotCard): String = if (c.arcana == Arcana.MAJOR) ROMAN[c.number] else when (c.number) {
    1 -> "ACE"; 11 -> "PAGE"; 12 -> "KNIGHT"; 13 -> "QUEEN"; 14 -> "KING"; else -> ROMAN[c.number]
}

/** True when the phone asks for reduced motion (animator duration scale 0). */
@Composable
fun reducedMotion(): Boolean {
    val ctx = LocalContext.current
    return runCatching { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
}

/**
 * A card at [width]. Face down shows the ASTRA back. Reversed cards turn the art upside down; the name stays readable.
 * The flip is a short turn on the vertical axis, skipped when the phone asks for reduced motion.
 */
@Composable
fun TarotCardView(card: TarotCard?, reversed: Boolean, faceUp: Boolean, width: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val still = reducedMotion()
    val turn by animateFloatAsState(if (faceUp) 180f else 0f, if (still) tween(0) else tween(450), label = "flip")
    val showFace = turn > 90f && card != null
    val label = when {
        card == null || !faceUp -> "Face-down card"
        else -> card.name + if (reversed) ", reversed" else ""
    }
    Box(
        modifier.width(width).aspectRatio(140f / 240f)
            .graphicsLayer { rotationY = turn; cameraDistance = 12f * density }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label }
    ) {
        if (!showFace) Image(painterResource(TarotArt.BACK), null, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { CardFace(card!!, reversed) }
    }
}

@Composable
private fun CardFace(card: TarotCard, reversed: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        Image(painterResource(TarotArt.of(card.id)), null, Modifier.fillMaxSize().then(if (reversed) Modifier.rotate(180f) else Modifier))
        val gold = Color(0xFFF7D794)
        val moon = Color(0xFFF5F3FF)
        // Bands left clear in the art: numeral around y 12-32 of 240, name around y 204-228.
        Text(cardNumeral(card), Modifier.fillMaxWidth().offset(y = h * (13f / 240f)), textAlign = TextAlign.Center, maxLines = 1,
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = (w.value * 0.072f).sp, letterSpacing = (w.value * 0.008f).sp, color = gold))
        Text(card.name, Modifier.fillMaxWidth().offset(y = h * (206f / 240f)), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Medium, fontSize = (w.value * (if (card.name.length > 16) 0.078f else 0.09f)).sp, color = moon))
    }
}

/** Small static card for lists (saved readings). */
@Composable
fun MiniCard(card: TarotCard, reversed: Boolean, width: Dp = 44.dp) = TarotCardView(card, reversed, faceUp = true, width = width)
