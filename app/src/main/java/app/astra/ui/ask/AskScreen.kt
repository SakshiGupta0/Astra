// Ask ASTRA: answers grounded in your calculated chart and, with your consent, your story (PRD F-07).
package app.astra.ui.ask

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.astra.App
import app.astra.R
import app.astra.core.ThreadStatus
import app.astra.ui.Session
import app.astra.ui.components.AField
import app.astra.ui.components.AIcon
import app.astra.ui.components.AstraCard
import app.astra.ui.components.Loading
import app.astra.ui.components.Notice
import app.astra.ui.components.Pill
import app.astra.ui.components.PrimaryButton
import app.astra.ui.components.SecondaryButton
import app.astra.ui.components.TextLink
import app.astra.ui.theme.Astra
import kotlinx.coroutines.launch

@Composable
fun AskScreen(app: App, session: Session, onOpenStory: () -> Unit) {
    var showLibrary by remember { mutableStateOf(false) }
    if (showLibrary) { LibraryScreen(app, onBack = { showLibrary = false }); return }
    val c = Astra.colors
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val turns = AskState.turns
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size) }
    val busy = turns.any { it.loading }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Close the keyboard on send so the answer has the whole screen.
    fun send(q: String) { if (q.isBlank() || busy) return; input = ""; focus.clearFocus(); keyboard?.hide(); scope.launch { AskState.ask(app, session, q) } }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ask ASTRA", style = Astra.type.title, color = c.text)
                Text("Answers from your calculated chart" + if (app.story.story.enabled) " and your story" else "", style = Astra.type.meta, color = c.textSecondary)
            }
            TextLink("Saved (${app.readings.readings.size})", { showLibrary = true }, Modifier.testTag("openLibrary"))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            item {
                if (!app.backend.available) Notice("Ask ASTRA is still being set up. Your chart, tracker and Today work on this phone in the meantime.")
                else if (turns.isEmpty()) Intro(app, ::send)
            }
            items(turns, key = { it.id }) { t -> TurnCard(app, t, onRetry = { turns.remove(t); send(t.question) }, onOpenStory = onOpenStory) }
        }
        AskState.remaining?.let { Text("$it questions left today", style = Astra.type.meta, color = c.textSecondary, modifier = Modifier.padding(horizontal = 20.dp)) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AField(input, { input = it.take(500) }, "Ask about your chart, period or day", Modifier.weight(1f), tag = "askInput")
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(if (input.isBlank() || busy || !app.backend.available) c.gold.copy(alpha = 0.35f) else c.gold)
                    .clickable(enabled = input.isNotBlank() && !busy && app.backend.available) { send(input) }.testTag("askSend"),
                contentAlignment = Alignment.Center
            ) { AIcon(R.drawable.ic_arrow_right, "Send", tint = c.onGold) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Intro(app: App, send: (String) -> Unit) {
    val c = Astra.colors
    val story = app.story.story
    val suggestions = buildList {
        story.threads.filter { it.status == ThreadStatus.OPEN }.take(2).forEach { add("What does my chart say about ${it.title.replaceFirstChar { ch -> ch.lowercase() }}?") }
        add("What does my current period mean for me?")
        add("When does this phase end?")
        add("What does Saturn's transit mean for me?")
        add("What should I focus on this month?")
    }.take(5)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ask anything about your chart, your periods or today. ASTRA reads your calculated chart" +
            (if (story.enabled) " and the parts of your story that fit the question" else "") + ", and never guesses placements or dates.",
            style = Astra.type.body, color = c.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEachIndexed { i, s ->
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).clickable { send(s) }.padding(horizontal = 12.dp, vertical = 10.dp).testTag("suggestion$i")) {
                    Text(s, style = Astra.type.label, color = c.text)
                }
            }
        }
    }
}

@Composable
private fun TurnCard(app: App, t: Turn, onRetry: () -> Unit, onOpenStory: () -> Unit) {
    val c = Astra.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)).background(c.backgroundAlt).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(t.question, style = Astra.type.body, color = c.text)
            }
        }
        when {
            t.loading -> Loading("Reading your chart…")
            t.error != null -> {
                Notice(t.error!!)
                TextLink("Try again", onRetry)
            }
            t.answer != null -> AnswerCard(app, t, onOpenStory)
        }
    }
}

@Composable
private fun AnswerCard(app: App, t: Turn, onOpenStory: () -> Unit) {
    val c = Astra.colors
    val a = t.answer!!
    var showItems by remember { mutableStateOf(false) }
    var offer by remember { mutableStateOf(false) }
    AstraCard(Modifier.animateContentSize().testTag("answer")) {
        Text(a.answer, style = Astra.type.body, color = c.text)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (t.storyItems.isNotEmpty()) Box(Modifier.clickable { showItems = !showItems }.testTag("fromStory")) {
                Pill("From your story · ${t.storyItems.size}", c.lavender, filled = true)
            }
            Box(Modifier.weight(1f))
            if (a.safety != "crisis") {
                if (t.savedId == null) TextLink("Save", {
                    AskState.save(app, t)
                    val s = app.story.story
                    if (!s.enabled && s.offersShown < 2) offer = true
                }, Modifier.testTag("saveAnswer"))
                else Text("Saved", style = Astra.type.label, color = c.textSecondary)
            }
        }
        if (showItems) t.storyItems.forEach { Text("• ${it.text}", style = Astra.type.meta, color = c.textSecondary) }
        if (a.safety != "crisis") Text("A reflection on your chart in the tradition's terms, not a prediction.", style = Astra.type.meta, color = c.textSecondary)
    }
    if (offer) StoryOffer(app, onOpenStory, onDone = { offer = false })
}

/** Second (and last) offer of My Story, after the first saved reading (PRD 3.3). */
@Composable
private fun StoryOffer(app: App, onOpenStory: () -> Unit, onDone: () -> Unit) {
    val c = Astra.colors
    AstraCard(Modifier.testTag("storyOffer")) {
        Text("Want ASTRA to remember your story?", style = Astra.type.heading, color = c.text)
        Text("Tell it once what's going on — a decision, a hope, a worry — and every answer, alert and reading starts from there. You see and edit everything, and can switch it off any time.",
            style = Astra.type.label, color = c.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Set up My Story", { app.story.update { it.copy(offersShown = 2) }; onDone(); onOpenStory() }, Modifier.weight(1f))
            SecondaryButton("Not now", { app.story.update { it.copy(offersShown = 2) }; onDone() }, Modifier.weight(1f))
        }
    }
}

@Composable
fun LibraryScreen(app: App, onBack: () -> Unit) {
    val c = Astra.colors
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { AIcon(R.drawable.ic_chevron_left, "Back") }
            Text("Saved readings", style = Astra.type.title, color = c.text)
        }
        if (app.readings.readings.isEmpty()) Text("Answers you save appear here.", style = Astra.type.body, color = c.textSecondary, modifier = Modifier.padding(20.dp))
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(app.readings.readings, key = { it.id }) { r ->
                var open by remember { mutableStateOf(false) }
                AstraCard(Modifier.animateContentSize(), onClick = { open = !open }) {
                    Text(r.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(), style = Astra.type.meta, color = c.textSecondary)
                    Text(r.question, style = Astra.type.bodyStrong, color = c.text)
                    Text(if (open) r.answer else r.answer.take(140) + if (r.answer.length > 140) "…" else "", style = Astra.type.label, color = c.text)
                    if (open) Row { Box(Modifier.weight(1f)); TextLink("Delete", { app.readings.delete(r.id) }) }
                }
            }
        }
    }
}
