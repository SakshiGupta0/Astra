// Reusable ASTRA components (design guide, section 6: core component styling).
package app.astra.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import app.astra.ui.theme.CardFill
import app.astra.ui.theme.CosmicBackground
import app.astra.ui.theme.Gold
import app.astra.ui.theme.GoldDeep
import app.astra.ui.theme.GoldLeaf
import app.astra.ui.theme.mysticClock
import app.astra.ui.theme.ornate
import app.astra.ui.theme.sparkle

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.astra.ui.theme.Astra
import app.astra.ui.theme.Dimens
import kotlin.random.Random

@Composable
fun AIcon(@DrawableRes id: Int, contentDescription: String?, tint: Color = Astra.colors.text, size: Dp = 22.dp, modifier: Modifier = Modifier) =
    Icon(painterResource(id), contentDescription, modifier.size(size), tint = tint)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Astra.colors
    val sheen by mysticClock(5_000, still = 2f, label = "sheen")
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Dimens.buttonRadius))
            .background(if (enabled) GoldLeaf else SolidColor(c.gold.copy(alpha = 0.35f)))
            .drawWithContent {
                drawContent()
                if (enabled && sheen < 0.3f) {
                    val x = size.width * (sheen / 0.3f) * 1.4f - size.width * 0.2f
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), x - 60f, x + 60f))
                }
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, style = Astra.type.bodyStrong, color = c.onGold.copy(alpha = if (enabled) 1f else 0.6f), textAlign = TextAlign.Center) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Astra.colors
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Dimens.buttonRadius))
            .background(c.lavender.copy(alpha = if (c.dark) 0.08f else 0.05f))
            .border(BorderStroke(1.dp, c.lavender.copy(alpha = 0.7f)), RoundedCornerShape(Dimens.buttonRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, style = Astra.type.bodyStrong, color = c.text, textAlign = TextAlign.Center) }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = Dimens.touch).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Astra.type.label, color = Astra.colors.lavender)
    }
}

@Composable
fun AstraCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(18.dp), glint: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(if (Astra.colors.dark) 10.dp else 3.dp, RoundedCornerShape(Dimens.cardRadius), ambientColor = Color.Black, spotColor = if (Astra.colors.dark) Color.Black else GoldDeep)
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(CardFill)
            .ornate(Dimens.cardRadius, glint = glint)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** Small capitals line above a title: "YOUR DAY", "TODAY'S CARD · III". */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Astra.colors.accent) =
    Text(text.uppercase(), style = Astra.type.overline, color = color, modifier = modifier)

/** Lotus divider between sections (decorative). */
@Composable
fun LotusDivider(modifier: Modifier = Modifier) {
    val c = Astra.colors
    val gold = if (c.dark) Gold else GoldDeep
    Canvas(modifier.fillMaxWidth().height(22.dp).clearAndSetSemantics { }) {
        val cx = size.width / 2; val cy = size.height / 2; val s = size.height / 22f
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, gold), 0f, cx - 26 * s), Offset(0f, cy), Offset(cx - 26 * s, cy), 1f)
        drawLine(Brush.horizontalGradient(listOf(gold, Color.Transparent), cx + 26 * s, size.width), Offset(cx + 26 * s, cy), Offset(size.width, cy), 1f)
        val petal = Path().apply { moveTo(cx, cy - 10 * s); quadraticTo(cx + 7 * s, cy - 2 * s, cx, cy + 9 * s); quadraticTo(cx - 7 * s, cy - 2 * s, cx, cy - 10 * s); close() }
        drawPath(petal, gold, alpha = 0.3f); drawPath(petal, gold, style = Stroke(1.2f))
        val l = Path().apply { moveTo(cx, cy + 9 * s); quadraticTo(cx - 12 * s, cy + 4 * s, cx - 18 * s, cy - 6 * s); quadraticTo(cx - 8 * s, cy - 2 * s, cx, cy + 9 * s) }
        val r = Path().apply { moveTo(cx, cy + 9 * s); quadraticTo(cx + 12 * s, cy + 4 * s, cx + 18 * s, cy - 6 * s); quadraticTo(cx + 8 * s, cy - 2 * s, cx, cy + 9 * s) }
        drawPath(l, gold, style = Stroke(1.1f)); drawPath(r, gold, style = Stroke(1.1f))
        sparkle(Offset(cx - 34 * s, cy), 3 * s, gold); sparkle(Offset(cx + 34 * s, cy), 3 * s, gold)
    }
}

/** Compact pill for concepts such as Transit, Retrograde or Supportive. */
@Composable
fun Pill(text: String, color: Color = Astra.colors.lavender, filled: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) { Text(text, style = Astra.type.meta, color = Astra.colors.text) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val gold = if (Astra.colors.dark) Gold else GoldDeep
        Canvas(Modifier.size(14.dp).clearAndSetSemantics { }) { sparkle(Offset(size.width / 2, size.height / 2), size.minDimension / 2, gold) }
        Box(Modifier.width(8.dp))
        Text(text, style = Astra.type.heading, color = Astra.colors.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Loading(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), color = Astra.colors.gold, strokeWidth = 2.dp)
        Box(Modifier.width(12.dp))
        Text(text, style = Astra.type.label, color = Astra.colors.textSecondary)
    }
}

@Composable
fun Notice(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int = app.astra.R.drawable.ic_info) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Astra.colors.lavender.copy(alpha = 0.12f)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AIcon(icon, null, tint = Astra.colors.lavender, size = 18.dp)
        Text(text, style = Astra.type.label, color = Astra.colors.text)
    }
}

/** Star field for atmospheric surfaces: twinkling stars, glimmers and a passing shooting star (still when motion is reduced). */
@Composable
fun Starfield(modifier: Modifier = Modifier, count: Int = 70, seed: Int = 7) = CosmicBackground(modifier, seed = seed, stars = count, nebula = false)

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(key, style = Astra.type.label, color = Astra.colors.textSecondary, modifier = Modifier.weight(0.42f))
        Text(value, style = Astra.type.label, color = Astra.colors.text, modifier = Modifier.weight(0.58f), textAlign = TextAlign.End)
    }
}
