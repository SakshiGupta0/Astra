// Today's loops: story check-ins (PRD 9.4), the one-time "ASTRA knew my situation" question (PRD 6.2) and today's card (F-05).
package app.astra.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.astra.App
import app.astra.core.CheckinAnswer
import app.astra.core.StoryRules
import app.astra.core.StoryThread
import app.astra.ui.Session
import app.astra.ui.components.AField
import app.astra.ui.components.AstraCard
import app.astra.ui.components.Pill
import app.astra.ui.components.Overline
import app.astra.ui.components.PrimaryButton
import app.astra.ui.components.SecondaryButton
import app.astra.ui.components.TextLink
import app.astra.ui.tarot.TarotCardView
import app.astra.ui.tarot.OracleCardView
import androidx.compose.foundation.layout.fillMaxWidth
import app.astra.ui.tarot.TarotState
import app.astra.ui.theme.Astra
import java.time.YearMonth

@Composable
fun CheckinCard(app: App, session: Session, t: StoryThread, onOpenTracker: () -> Unit, onResolved: () -> Unit = {}) {
    val c = Astra.colors
    var text by remember(t.id) { mutableStateOf("") }
    var resolvedTitle by remember(t.id) { mutableStateOf<String?>(null) }
    var eventAdded by remember(t.id) { mutableStateOf(false) }
    AstraCard(Modifier.animateContentSize().testTag("checkin")) {
        if (resolvedTitle == null) {
            Text("Check-in", style = Astra.type.meta, color = c.accent)
            Text(StoryRules.checkinQuestion(t, session.today), style = Astra.type.heading, color = c.text)
            AField(text, { text = it.take(StoryRules.MAX_TEXT) }, "In a line (optional)", placeholder = "e.g. Got the offer letter", tag = "checkinText")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Resolved", {
                    val title = text.trim().ifBlank { t.title }
                    if (app.story.update { StoryRules.answerCheckin(it, t.id, CheckinAnswer.RESOLVED, text, session.today) }) {
                        app.analytics.log("checkin_answered", "status" to "resolved"); resolvedTitle = title; onResolved()
                    }
                }, Modifier.weight(1f).testTag("checkinResolved"))
                SecondaryButton("Still open", {
                    if (app.story.update { StoryRules.answerCheckin(it, t.id, CheckinAnswer.STILL_OPEN, text, session.today) })
                        app.analytics.log("checkin_answered", "status" to "open")
                }, Modifier.weight(1f).testTag("checkinOpen"))
            }
            TextLink("Not now", {
                app.story.update { StoryRules.answerCheckin(it, t.id, CheckinAnswer.DISMISSED, "", session.today) }
                app.analytics.log("checkin_answered", "status" to "dismissed")
            })
            Text("Saved to your story. ASTRA brings it into answers when it fits.", style = Astra.type.meta, color = c.textSecondary)
        } else if (!eventAdded) {
            Text("Saved: ${t.title} — resolved.", style = Astra.type.bodyStrong, color = c.text)
            Text("Add it as a life event? You'll see it on your tracker timeline next to what your chart showed this month.", style = Astra.type.label, color = c.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Add life event", {
                    if (app.story.update { StoryRules.addEvent(it, resolvedTitle!!, YearMonth.from(session.today)) }) {
                        eventAdded = true; app.analytics.log("story_item_added", "type" to "event", "source" to "checkin")
                    }
                }, Modifier.weight(1f).testTag("checkinAddEvent"))
                SecondaryButton("No thanks", { eventAdded = true; resolvedTitle = "" }, Modifier.weight(1f))
            }
        } else if (resolvedTitle!!.isNotEmpty()) {
            Text("Added to your life events.", style = Astra.type.bodyStrong, color = c.text)
            TextLink("See it on the tracker", onOpenTracker, Modifier.testTag("checkinSeeTracker"))
        }
    }
}

/** Asked once, two weeks after My Story was turned on (PRD 6.2 "Didn't repeat myself"). */
@Composable
fun SurveyCard(app: App, session: Session) {
    val c = Astra.colors
    var thanks by remember { mutableStateOf(false) }
    AstraCard(Modifier.testTag("survey")) {
        if (thanks) { Text("Thank you — that helps us make ASTRA better.", style = Astra.type.label, color = c.text); return@AstraCard }
        Text("A quick question", style = Astra.type.meta, color = c.accent)
        Text("“ASTRA already knew my situation — I didn't have to explain it again.”", style = Astra.type.heading, color = c.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "Disagree", 3 to "Neutral", 5 to "Agree").forEach { (v, label) ->
                Text(label, style = Astra.type.label, color = c.text,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(c.backgroundAlt).clickable {
                        app.story.update { it.copy(surveyAnswer = v, surveyAskedOn = session.today) }
                        app.analytics.log("repeat_story_survey", "agree_level" to v); thanks = true
                    }.padding(horizontal = 14.dp, vertical = 10.dp).testTag("survey$v"))
            }
        }
        TextLink("Skip", { app.story.update { it.copy(surveyAskedOn = session.today) } })
    }
}

/** Today's two cards: one classic tarot card and one ASTRA guidance card, each kept for the day. Tap to turn them over. */
@Composable
fun DailyCard(app: App, session: Session, onOpenTarot: () -> Unit) {
    val c = Astra.colors
    val draw = remember(session.today) { app.draws.dailyFor(session.today) }
    val guide = remember(session.today) { app.draws.guidanceFor(session.today) }
    var revealed by remember(session.today) { mutableStateOf(app.draws.revealed(session.today)) }
    var guideShown by remember(session.today) { mutableStateOf(app.draws.guidanceRevealed(session.today)) }
    val dc = draw.cards.first()
    AstraCard(Modifier.animateContentSize().testTag("dailyCard"), glint = true) {
        Overline("Today's cards · ${session.today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}")
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = androidx.compose.ui.unit.min((maxWidth - 16.dp) / 2, 190.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TarotCardView(dc.card, dc.reversed, revealed, w, Modifier.testTag("dailyCardFace"), onClick = {
                        if (!revealed) { revealed = true; app.draws.markRevealed(session.today) }
                    })
                    Text("TAROT", style = Astra.type.overline, color = c.lavender)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OracleCardView(guide.card, guideShown, w, Modifier.testTag("guidanceCardFace"), onClick = {
                        if (!guideShown) { guideShown = true; app.draws.markGuidanceRevealed(session.today) }
                    })
                    Text("GUIDANCE", style = Astra.type.overline, color = c.lavender)
                }
            }
        }
        if (!revealed && !guideShown) Text("Two cards are waiting for you. Tap each to turn it over.", style = Astra.type.label, color = c.textSecondary)
        if (revealed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dc.card.name, style = Astra.type.heading, color = c.text)
                if (dc.reversed) Pill("Reversed", c.lavender, filled = true)
            }
            Text("In tarot: ${dc.meaning}.", style = Astra.type.label, color = c.textSecondary)
            TextLink("Ask about today's card", { TarotState.open(draw, faceUp = true); onOpenTarot() }, Modifier.testTag("askDailyCard"))
        }
        if (guideShown) {
            Text(guide.card.title.lowercase().replaceFirstChar { it.uppercase() } + " · " + guide.card.line, style = Astra.type.body, color = c.text, modifier = Modifier.testTag("guidanceText"))
        }
    }
}
