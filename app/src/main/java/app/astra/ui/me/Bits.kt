package app.astra.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.astra.ui.theme.Astra

/** A labelled on/off row with the switch on the right; the whole row is tappable. */
@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, tag: String? = null) {
    val c = Astra.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Switch) { onChange(!checked) }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Astra.type.bodyStrong, color = c.text)
            if (subtitle != null) Text(subtitle, style = Astra.type.meta, color = c.textSecondary)
        }
        Switch(checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = c.onGold, checkedTrackColor = c.gold, uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.surface, uncheckedBorderColor = c.border))
    }
}

/** Small selectable pill; selected state shown with a check mark as well as colour. */
@Composable
fun SelectPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Astra.colors
    Text(
        (if (selected) "✓ " else "") + text, style = Astra.type.label, color = c.text,
        modifier = modifier.clip(RoundedCornerShape(50)).background(if (selected) c.gold.copy(alpha = 0.18f) else c.surface)
            .border(1.dp, if (selected) c.accent else c.border, RoundedCornerShape(50))
            .clickable(role = Role.Checkbox, onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}
