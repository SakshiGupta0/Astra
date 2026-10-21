// My Story in Me: consent, about, open questions, life events, sharing and deletion (PRD section 3).
package app.astra.ui.me

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.astra.App
import app.astra.R
import app.astra.core.LifeArea
import app.astra.core.LifeStage
import app.astra.core.StoryRules
import app.astra.core.ThreadStatus
import app.astra.ui.Session
import app.astra.ui.components.AField
import app.astra.ui.components.AIcon
import app.astra.ui.components.AstraCard
import app.astra.ui.components.Notice
import app.astra.ui.components.SecondaryButton
import app.astra.ui.components.SectionTitle
import app.astra.ui.components.TextLink
import app.astra.ui.theme.Astra
import java.time.YearMonth
import app.astra.data.StoryPdf

const val STORY_EXPLAINER = "Tell ASTRA once what's going on — a decision, a hope, a worry — and every answer, alert and reading starts from there. You can see, edit or delete every line, and switch it off any time."

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorySection(app: App, session: Session) {
    val c = Astra.colors
    val story = app.story.story
    var confirmDelete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    SectionTitle("My Story")
    AstraCard(Modifier.animateContentSize().testTag("storyCard")) {
        ToggleRow("Let ASTRA remember my story", if (story.enabled) "On · used only when it fits your question" else "Off",
            story.enabled, { on -> app.story.update { if (on) StoryRules.grant(it, "settings") else StoryRules.revoke(it) } }, tag = "storySwitch")
        if (!story.enabled) {
            Text(STORY_EXPLAINER, style = Astra.type.label, color = c.textSecondary)
            if (!story.isEmpty) Text("Your earlier story is kept on this phone but not used. Delete it below if you like.", style = Astra.type.meta, color = c.textSecondary)
        }
        app.story.lastError?.let { Notice(it) }
    }
    if (story.enabled) {
        AstraCard {
            Text("About me", style = Astra.type.heading, color = c.text)
            Text("Life stage", style = Astra.type.meta, color = c.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeStage.entries.forEach { s -> SelectPill(s.label, story.about.lifeStage == s, { app.story.update { it.copy(about = it.about.copy(lifeStage = if (it.about.lifeStage == s) null else s)) } }) }
            }
            Text("What matters most right now", style = Astra.type.meta, color = c.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeArea.entries.forEach { a -> SelectPill(a.label, a in story.about.areas, { app.story.update { st -> st.copy(about = st.about.copy(areas = if (a in st.about.areas) st.about.areas - a else st.about.areas + a)) } }) }
            }
            var now by remember(story.about.nowLine) { mutableStateOf(story.about.nowLine) }
            AField(now, { now = it.take(StoryRules.MAX_TEXT) }, "What's going on right now? (optional)", tag = "storyNow")
            if (now.trim() != story.about.nowLine) TextLink("Save", { app.story.update { it.copy(about = it.about.copy(nowLine = StoryRules.clip(now))) } })
        }
        ThreadsCard(app, session)
        CheckinGap(app)
        EventsCard(app)
        if (story.digests.isNotEmpty()) AstraCard {
            Text("Earlier readings", style = Astra.type.heading, color = c.text)
            story.digests.take(10).forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${d.date} · ${d.text}", style = Astra.type.label, color = c.text, modifier = Modifier.weight(1f))
                    Box(Modifier.size(40.dp).clickable { app.story.update { StoryRules.removeDigest(it, d.id) } }, contentAlignment = Alignment.Center) { AIcon(R.drawable.ic_x, "Remove", size = 16.dp, tint = c.textSecondary) }
                }
            }
        }
        SecondaryButton("Share my story with an astrologer", { sharing = true }, Modifier.fillMaxWidth().testTag("shareStory"))
    }
    if (!story.isEmpty) TextLink("Delete my story", { confirmDelete = true })

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete My Story?") },
        text = { Text("This removes everything in My Story from this phone and from your private copy on the server, and switches it off.") },
        confirmButton = { TextButton({ confirmDelete = false; app.story.update { StoryRules.deleteAll(it) } }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
    )
    if (sharing) ShareDialog(app, session) { sharing = false }
}

@Composable
private fun ThreadsCard(app: App, session: Session) {
    val c = Astra.colors
    val story = app.story.story
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AstraCard(Modifier.animateContentSize()) {
        Text("On my mind", style = Astra.type.heading, color = c.text)
        Text("Questions and decisions you're living with. ASTRA brings them in when they fit.", style = Astra.type.meta, color = c.textSecondary)
        story.threads.forEach { t ->
            var open by remember { mutableStateOf(false) }
            var outcome by remember { mutableStateOf(t.outcome) }
            Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (t.status == ThreadStatus.RESOLVED) "✓ " else "• ") + t.title, style = Astra.type.bodyStrong,
                        color = if (t.status == ThreadStatus.RESOLVED) c.textSecondary else c.text, modifier = Modifier.weight(1f))
                    t.area?.let { Text(it.label.substringBefore(" &"), style = Astra.type.meta, color = c.textSecondary) }
                }
                if (t.note.isNotBlank()) Text(t.note, style = Astra.type.label, color = c.textSecondary)
                if (t.outcome.isNotBlank()) Text("Outcome: ${t.outcome}", style = Astra.type.label, color = c.textSecondary)
                if (open) {
                    if (t.status == ThreadStatus.OPEN) {
                        AField(outcome, { outcome = it }, "What happened? (optional)")
                        Row { TextLink("Mark as resolved", { app.story.update { StoryRules.updateThread(it, t.copy(status = ThreadStatus.RESOLVED, outcome = outcome)) } })
                            TextLink("Delete", { app.story.update { StoryRules.removeThread(it, t.id) } }) }
                    } else Row {
                        TextLink("Reopen", { app.story.update { StoryRules.updateThread(it, t.copy(status = ThreadStatus.OPEN)) } })
                        TextLink("Delete", { app.story.update { StoryRules.removeThread(it, t.id) } })
                    }
                }
            }
        }
        AField(title, { title = it.take(StoryRules.MAX_TEXT) }, "Add something on your mind", placeholder = "e.g. Deciding on a job offer", tag = "threadTitle")
        if (title.isNotBlank()) {
            AField(note, { note = it.take(StoryRules.MAX_TEXT) }, "A little more detail (optional)")
            TextLink("Add", {
                if (app.story.update { StoryRules.addThread(it, title, note, today = session.today) }) {
                    title = ""; note = ""; app.analytics.log("story_item_added", "type" to "thread", "source" to "user")
                }
            }, Modifier.testTag("addThread"))
        }
    }
}

@Composable
private fun EventsCard(app: App) {
    val c = Astra.colors
    val story = app.story.story
    var title by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    AstraCard(Modifier.animateContentSize()) {
        Text("Life events", style = Astra.type.heading, color = c.text)
        Text("Moments you'd like ASTRA to know about, such as a move, a new job or a wedding. They appear on your tracker timeline.", style = Astra.type.meta, color = c.textSecondary)
        story.events.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${e.month} · ${e.title}", style = Astra.type.label, color = c.text, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clickable { app.story.update { StoryRules.removeEvent(it, e.id) } }, contentAlignment = Alignment.Center) { AIcon(R.drawable.ic_x, "Remove", size = 16.dp, tint = c.textSecondary) }
            }
        }
        AField(title, { title = it.take(StoryRules.MAX_TEXT) }, "Add a life event", placeholder = "e.g. Moved to Bengaluru")
        if (title.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AField(month, { month = it.filter(Char::isDigit).take(2) }, "Month", Modifier.weight(1f), KeyboardType.Number, placeholder = "MM")
                AField(year, { year = it.filter(Char::isDigit).take(4) }, "Year", Modifier.weight(1.3f), KeyboardType.Number, placeholder = "YYYY")
            }
            val ym = runCatching { YearMonth.of(year.toInt(), month.toInt()) }.getOrNull()?.takeIf { it.year in 1900..YearMonth.now().year + 1 }
            if (ym != null) TextLink("Add", { if (app.story.update { StoryRules.addEvent(it, title, ym) }) {
                title = ""; month = ""; year = ""; app.analytics.log("story_item_added", "type" to "event", "source" to "user")
            } })
        }
    }
}

@Composable
private fun ShareDialog(app: App, session: Session, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var birth by remember { mutableStateOf(true) }
    var chart by remember { mutableStateOf(true) }
    var dasha by remember { mutableStateOf(true) }
    var threads by remember { mutableStateOf(true) }
    var events by remember { mutableStateOf(true) }
    var pdf by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Share my story") },
        text = {
            Column {
                Text("Choose what to include. You'll pick the app to send it with.", style = Astra.type.label)
                listOf(Triple("Birth details", birth) { v: Boolean -> birth = v }, Triple("Chart key facts", chart) { v: Boolean -> chart = v },
                    Triple("Current dasha", dasha) { v: Boolean -> dasha = v }, Triple("On my mind", threads) { v: Boolean -> threads = v },
                    Triple("Life events", events) { v: Boolean -> events = v }).forEach { (label, value, set) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { set(!value) }) { Checkbox(value, { set(it) }); Text(label) }
                }
                Text("Send as", style = Astra.type.label, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectPill("Text", !pdf, { pdf = false })
                    SelectPill("PDF", pdf, { pdf = true }, Modifier.testTag("sharePdf"))
                }
            }
        },
        confirmButton = { TextButton({
            val text = StoryRules.export(app.story.story, session.profile, session.chart, session.at, birth, chart, dasha, threads, events)
            val count = listOf(birth, chart, dasha, threads, events).count { it }
            val send = if (pdf) runCatching {
                val f = StoryPdf.write(ctx, text)
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.getOrNull() else null
            ctx.startActivity(Intent.createChooser(send ?: Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share my story"))
            app.analytics.log("story_exported", "fields_count" to count, "format" to if (send != null) "pdf" else "text")
            onDone()
        }) { Text("Share") } },
        dismissButton = { TextButton(onDone) { Text("Cancel") } }
    )
}

/** How often ASTRA follows up on open questions (default every 3 weeks; can be switched off). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckinGap(app: App) {
    val c = Astra.colors
    val days = app.story.story.checkinDays
    AstraCard {
        Text("Check-ins", style = Astra.type.heading, color = c.text)
        Text("ASTRA asks how an open question is going, then saves your answer to your story.", style = Astra.type.meta, color = c.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StoryRules.CHECKIN_CHOICES.forEach { d ->
                SelectPill(when (d) { 0 -> "Off"; 7 -> "Every week"; 14 -> "Every 2 weeks"; 21 -> "Every 3 weeks"; else -> "Every month" }, days == d,
                    { app.story.update { it.copy(checkinDays = d) } })
            }
        }
    }
}
