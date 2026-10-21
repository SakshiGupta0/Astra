// Saved readings (PRD F-11): list, filter by kind, reopen with the original cards, delete.
package app.astra.ui.ask

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.astra.App
import app.astra.R
import app.astra.ui.components.AIcon
import app.astra.ui.components.AstraCard
import app.astra.ui.components.ChoiceChips
import app.astra.ui.components.Pill
import app.astra.ui.components.TextLink
import app.astra.ui.tarot.MiniCard
import app.astra.ui.theme.Astra

private val KINDS = listOf("all" to "All", "ask" to "Questions", "tarot" to "Tarot", "cross" to "Cross-Readings")

@Composable
fun LibraryScreen(app: App, onBack: () -> Unit) {
    val c = Astra.colors
    androidx.activity.compose.BackHandler(onBack = onBack)
    var kind by rememberSaveable { mutableStateOf("all") }
    val list = app.readings.readings.filter { kind == "all" || it.type == kind }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { AIcon(R.drawable.ic_chevron_left, "Back") }
            Text("Saved readings", style = Astra.type.title, color = c.text)
        }
        ChoiceChips(KINDS, kind, { kind = it }, Modifier.padding(horizontal = 20.dp))
        if (list.isEmpty()) Text(if (kind == "all") "Answers and readings you save appear here." else "Nothing saved of this kind yet.",
            style = Astra.type.body, color = c.textSecondary, modifier = Modifier.padding(20.dp))
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("libraryList")) {
            items(list, key = { it.id }) { r ->
                var open by remember { mutableStateOf(false) }
                AstraCard(Modifier.animateContentSize(), onClick = { open = !open }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(r.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(), style = Astra.type.meta, color = c.textSecondary, modifier = Modifier.weight(1f))
                        Pill(when (r.type) { "tarot" -> "Tarot"; "cross" -> "Cross-Reading"; else -> "Question" }, if (r.type == "ask") c.border else c.lavender)
                    }
                    Text(r.question, style = Astra.type.bodyStrong, color = c.text)
                    r.draw?.let { d ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { d.cards.forEach { MiniCard(it.card, it.reversed) } }
                        Text(d.cards.joinToString(" · ") { it.card.name + if (it.reversed) " (reversed)" else "" }, style = Astra.type.meta, color = c.textSecondary)
                    }
                    Text(if (open) r.answer else r.answer.take(140) + if (r.answer.length > 140) "…" else "", style = Astra.type.label, color = c.text)
                    if (open) Row { Box(Modifier.weight(1f)); TextLink("Delete", { app.readings.delete(r.id) }) }
                }
            }
        }
    }
}
