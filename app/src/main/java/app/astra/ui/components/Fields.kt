package app.astra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.astra.App
import app.astra.R
import app.astra.core.Place
import app.astra.core.PlaceIndex
import app.astra.core.SavedPlace
import app.astra.ui.theme.Astra
import kotlinx.coroutines.delay

@Composable
fun AField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text, error: String? = null, placeholder: String? = null, tag: String? = null
) {
    val c = Astra.colors
    Column(modifier) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            label = { Text(label, style = Astra.type.label) },
            placeholder = placeholder?.let { { Text(it, style = Astra.type.body, color = c.textSecondary) } },
            isError = error != null,
            textStyle = Astra.type.body.copy(color = c.text),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.accent, unfocusedBorderColor = c.border, focusedLabelColor = c.accent, unfocusedLabelColor = c.textSecondary,
                cursorColor = c.accent, focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, errorBorderColor = c.error
            ),
            modifier = Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier)
        )
        if (error != null) Text(error, style = Astra.type.meta, color = c.error, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    }
}

/** Single-choice chips with a visible selected state that does not rely on colour alone (check mark + border). */
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Astra.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val sel = value == selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (sel) c.accent.copy(alpha = 0.14f) else c.surface)
                    .border(if (sel) 1.5.dp else 1.dp, if (sel) c.accent else c.border, RoundedCornerShape(14.dp))
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .semantics { this.selected = sel }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = Astra.type.body, color = c.text, modifier = Modifier.weight(1f))
                if (sel) AIcon(R.drawable.ic_check, "Selected", tint = c.accent, size = 20.dp)
            }
        }
    }
}

/** Offline city search. Shows the chosen place with its time zone so people can confirm it. */
@Composable
fun PlaceSearch(selected: SavedPlace?, onSelect: (SavedPlace) -> Unit, modifier: Modifier = Modifier, label: String = "Town or city") {
    val app = LocalContext.current.applicationContext as App
    var index by remember { mutableStateOf<PlaceIndex?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    LaunchedEffect(Unit) { runCatching { index = app.places.index() }.onFailure { loadError = "The place list couldn't be opened (${it.message})." } }
    LaunchedEffect(query, index) {
        delay(120)
        results = index?.search(query, 8) ?: emptyList()
    }
    val c = Astra.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected != null) {
            AstraCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AIcon(R.drawable.ic_map_pin, null, tint = c.accent, size = 20.dp)
                    Column(Modifier.weight(1f)) {
                        Text(selected.label, style = Astra.type.bodyStrong, color = c.text, modifier = Modifier.testTag("selectedPlace"))
                        Text("Time zone: ${selected.zoneId}", style = Astra.type.meta, color = c.textSecondary)
                    }
                }
            }
        }
        AField(query, { query = it }, if (selected == null) label else "Search a different place", placeholder = "e.g. Pune, Bareilly, London", tag = "placeQuery")
        when {
            loadError != null -> Notice(loadError!!)
            index == null && query.isNotBlank() -> Loading("Opening place list…")
            query.trim().length >= 2 && results.isEmpty() && index != null -> Notice("No match. Try the nearest larger town; for a chart, a few kilometres makes little difference.")
        }
        results.forEach { p ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(SavedPlace.of(p)); query = "" }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AIcon(R.drawable.ic_map_pin, null, tint = c.textSecondary, size = 18.dp)
                Column(Modifier.weight(1f)) {
                    Text(p.label, style = Astra.type.body, color = c.text)
                    Text(p.zoneId, style = Astra.type.meta, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Astra.colors
    Row(modifier.clip(RoundedCornerShape(50)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(50)).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(if (sel) c.gold else c.surface)
                    .clickable(role = Role.Tab) { onSelect(i) }.semantics { this.selected = sel },
                contentAlignment = Alignment.Center
            ) { Text(label, style = Astra.type.label, color = if (sel) c.onGold else c.text) }
        }
    }
}
