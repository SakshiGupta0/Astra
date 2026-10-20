// Reusable ASTRA components (design guide, section 6: core component styling).
package app.astra.ui.components

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
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Dimens.buttonRadius))
            .background(if (enabled) c.gold else c.gold.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, style = Astra.type.bodyStrong, color = c.onGold.copy(alpha = if (enabled) 1f else 0.6f)) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Astra.colors
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Dimens.buttonRadius))
            .border(BorderStroke(1.dp, c.border), RoundedCornerShape(Dimens.buttonRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, style = Astra.type.bodyStrong, color = c.text) }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = Dimens.touch).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Astra.type.label, color = Astra.colors.lavender)
    }
}

@Composable
fun AstraCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    val c = Astra.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(c.surface)
            .border(1.dp, c.border.copy(alpha = 0.6f), RoundedCornerShape(Dimens.cardRadius))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
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

/** Subtle star field for atmospheric surfaces (onboarding, Today header). Static: no motion. */
@Composable
fun Starfield(modifier: Modifier = Modifier, count: Int = 70, seed: Int = 7) {
    val c = Astra.colors
    val stars = remember(seed, count) { val r = Random(seed); List(count) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Canvas(modifier) {
        stars.forEach { (x, y, s) ->
            drawCircle(if (s > 0.85f) c.gold else c.text, radius = 0.6f + s * 1.4f, center = Offset(x * size.width, y * size.height),
                alpha = if (c.dark) 0.25f + s * 0.45f else 0.12f + s * 0.2f)
        }
    }
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(key, style = Astra.type.label, color = Astra.colors.textSecondary, modifier = Modifier.weight(0.42f))
        Text(value, style = Astra.type.label, color = Astra.colors.text, modifier = Modifier.weight(0.58f), textAlign = TextAlign.End)
    }
}
