// Tarot studio (PRD F-09, F-10): a deliberate draw and reveal, tarot's own meanings offline, then an AI reading
// of the saved draw, or a Cross-Reading of a three-card draw with your chart in five fixed sections.
package app.astra.ui.tarot

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.astra.App
import app.astra.core.Spread
import app.astra.core.StoryRules
import app.astra.ui.Session
import app.astra.ui.ask.LibraryScreen
import app.astra.ui.components.AField
import app.astra.ui.components.AstraCard
import app.astra.ui.components.Loading
import app.astra.ui.components.Notice
import app.astra.ui.components.Pill
import app.astra.ui.components.PrimaryButton
import app.astra.ui.components.SecondaryButton
import app.astra.ui.components.Segmented
import app.astra.ui.components.Starfield
import app.astra.ui.components.TextLink
import app.astra.ui.theme.Astra
import kotlinx.coroutines.launch

const val TAROT_NOTE = "Tarot is its own tradition, separate from your Vedic chart. A reading is a mirror for reflection, not a prediction."

@Composable
fun TarotScreen(app: App, session: Session) {
    var showLibrary by remember { mutableStateOf(false) }
    if (showLibrary) { LibraryScreen(app, onBack = { showLibrary = false }); return }
    val c = Astra.colors
    val draw = TarotState.draw
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(c.backgroundAlt)) {
            Starfield(Modifier.fillMaxWidth().height(96.dp), count = 30, seed = 21)
            Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tarot", style = Astra.type.title, color = c.text)
                    Text("Original ASTRA deck · 78 cards", style = Astra.type.meta, color = c.textSecondary)
                }
                TextLink("Saved (${app.readings.readings.size})", { showLibrary = true }, Modifier.testTag("tarotLibrary"))
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (draw == null) NewDraw(app) else DrawView(app, session)
        }
    }
}

@Composable
private fun NewDraw(app: App) {
    val c = Astra.colors
    var question by rememberSaveable { mutableStateOf("") }
    var three by rememberSaveable { mutableStateOf(true) }
    val focus = LocalFocusManager.current
    AstraCard(Modifier.testTag("tarotIntro")) {
        Text("Pull cards for a question", style = Astra.type.heading, color = c.text)
        Text("Hold a question in mind, or just draw. The cards are shuffled with a secure random shuffle and saved before anything is read into them.",
            style = Astra.type.label, color = c.textSecondary)
        AField(question, { question = it.take(StoryRules.MAX_TEXT) }, "Your question (optional)", placeholder = "e.g. What do I need to see about this job?", tag = "tarotQuestion")
        Segmented(listOf("One card", "Three cards"), if (three) 1 else 0, { three = it == 1 }, Modifier.fillMaxWidth())
        Text(if (three) "What's behind it · Where you are · What can help. Three-card draws can also be read with your chart (Cross-Reading)."
            else "One card for a quick reflection.", style = Astra.type.meta, color = c.textSecondary)
        PrimaryButton("Shuffle and draw", {
            focus.clearFocus()
            TarotState.newDraw(app, if (three) Spread.THREE else Spread.ONE, question)
            question = ""
        }, Modifier.fillMaxWidth().testTag("drawCards"))
    }
    Text(TAROT_NOTE, style = Astra.type.meta, color = c.textSecondary)
}

@Composable
private fun DrawView(app: App, session: Session) {
    val c = Astra.colors
    val d = TarotState.draw ?: return
    val scope = rememberCoroutineScope()
    if (d.question.isNotBlank()) Text("“${d.question}”", style = Astra.type.heading, color = c.text)
    Text(d.spread.title + if (d.day != null) " · ${d.day}" else "", style = Astra.type.meta, color = c.textSecondary)

    // The spread: tap each card to turn it over.
    val width = if (d.cards.size == 1) 150.dp else 100.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        d.cards.forEachIndexed { i, dc ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TarotCardView(dc.card, dc.reversed, TarotState.revealed.getOrElse(i) { false }, width,
                    Modifier.testTag("card$i"), onClick = { TarotState.reveal(i) })
                Text(dc.position, style = Astra.type.meta, color = c.textSecondary)
            }
        }
    }
    if (!TarotState.allRevealed) {
        Text("Tap each card to turn it over.", style = Astra.type.label, color = c.textSecondary, modifier = Modifier.fillMaxWidth())
        TextLink("Turn all over", { TarotState.revealAll() }, Modifier.testTag("revealAll"))
        return
    }

    // Tarot's own meanings, offline and instant.
    AstraCard(Modifier.animateContentSize().testTag("cardMeanings")) {
        d.cards.forEach { dc ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dc.card.name, style = Astra.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                Pill(if (dc.reversed) "Reversed" else "Upright", if (dc.reversed) c.lavender else c.accent, filled = dc.reversed)
            }
            Text("${dc.position} · in tarot: ${dc.meaning}", style = Astra.type.label, color = c.textSecondary)
        }
    }

    val reading = TarotState.reading
    val cross = TarotState.cross
    val busy = reading?.loading == true || cross?.loading == true
    if (!app.backend.available) Notice("AI readings are still being set up. The meanings above come from the deck and work offline.")
    else {
        if (reading == null) PrimaryButton("Ask ASTRA to read these cards", { scope.launch { TarotState.interpret(app, session, "tarot") } },
            Modifier.fillMaxWidth().testTag("readCards"), enabled = !busy)
        if (d.spread == Spread.THREE && cross == null) {
            SecondaryButton("Cross-Reading with my chart" + (TarotState.crossLeft?.let { " · $it left today" } ?: ""), {
                if (!busy) scope.launch { TarotState.interpret(app, session, "cross") }
            }, Modifier.fillMaxWidth().testTag("crossReading"))
            Text("Your chart and these cards side by side, in five parts. Two a day.", style = Astra.type.meta, color = c.textSecondary)
        }
    }
    reading?.let { TurnView(app, it, cross = false) }
    cross?.let { TurnView(app, it, cross = true) }

    SecondaryButton("New reading", { TarotState.reset() }, Modifier.fillMaxWidth().testTag("newReading"))
    Text(TAROT_NOTE, style = Astra.type.meta, color = c.textSecondary)
}

@Composable
private fun TurnView(app: App, t: ReadingTurn, cross: Boolean) {
    val c = Astra.colors
    when {
        t.loading -> Loading(if (cross) "Reading your chart and your cards…" else "Reading your cards…")
        t.error != null -> Notice(t.error!!)
        t.answer != null -> {
            val a = t.answer!!
            AstraCard(Modifier.animateContentSize().testTag(if (cross) "crossAnswer" else "tarotAnswer")) {
                Text(if (cross) "Cross-Reading" else "Your reading", style = Astra.type.heading, color = c.text)
                val parts = if (cross && a.safety == "ok") TarotState.sections(a.answer, TarotState.crossLabels(app.profiles.profile?.language ?: app.astra.core.ReplyLanguage.EN)) else null
                if (parts != null) parts.forEach { (label, body) ->
                    Text(label, style = Astra.type.bodyStrong, color = c.accent, modifier = Modifier.padding(top = 4.dp))
                    Text(body, style = Astra.type.body, color = c.text)
                } else Text(a.answer, style = Astra.type.body, color = c.text)
                var showItems by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.storyItems.isNotEmpty()) Box(Modifier.clickable { showItems = !showItems }) { Pill("From your story · ${t.storyItems.size}", c.lavender, filled = true) }
                    Box(Modifier.weight(1f))
                    if (a.safety != "crisis") {
                        if (t.savedId == null) TextLink("Save", { TarotState.save(app, t) }, Modifier.testTag(if (cross) "saveCross" else "saveTarot"))
                        else Text("Saved", style = Astra.type.label, color = c.textSecondary)
                    }
                }
                if (showItems) t.storyItems.forEach { Text("• ${it.text}", style = Astra.type.meta, color = c.textSecondary) }
                if (cross && a.safety == "ok") Rating(t) { TarotState.rate(app, t, it) }
            }
        }
    }
}

@Composable
private fun Rating(t: ReadingTurn, onRate: (Int) -> Unit) {
    val c = Astra.colors
    if (t.rating != null) { Text("Thanks for the feedback.", style = Astra.type.meta, color = c.textSecondary); return }
    Text("Was this Cross-Reading helpful?", style = Astra.type.label, color = c.text)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1 to "Not really", 3 to "Somewhat", 5 to "Very").forEach { (v, label) ->
            Text(label, style = Astra.type.label, color = c.text,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.backgroundAlt).clickable { onRate(v) }.padding(horizontal = 12.dp, vertical = 8.dp).testTag("rate$v"))
        }
    }
}
