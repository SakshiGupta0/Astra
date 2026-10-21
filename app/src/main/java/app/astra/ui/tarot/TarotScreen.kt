// Reading room (PRD F-09, F-10): the classic tarot (shuffle, choose from the fan, turn your cards), AI reading of the
// saved draw or a Cross-Reading with your chart, and ASTRA's oracle decks. Adapts from phone to tablet.
package app.astra.ui.tarot

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.astra.App
import app.astra.core.Oracle
import app.astra.core.OracleDeck
import app.astra.core.ReplyLanguage
import app.astra.core.Spread
import app.astra.core.StoryRules
import app.astra.ui.Session
import app.astra.ui.ask.LibraryScreen
import app.astra.ui.components.AField
import app.astra.ui.components.AstraCard
import app.astra.ui.components.Loading
import app.astra.ui.components.LotusDivider
import app.astra.ui.components.Notice
import app.astra.ui.components.Overline
import app.astra.ui.components.Pill
import app.astra.ui.components.PrimaryButton
import app.astra.ui.components.SecondaryButton
import app.astra.ui.components.Segmented
import app.astra.ui.components.TextLink
import app.astra.ui.theme.Astra
import app.astra.ui.theme.CosmicBackground
import app.astra.ui.theme.Mandala
import app.astra.ui.theme.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val TAROT_NOTE = "Tarot is its own tradition, separate from your Vedic chart. A reading is a mirror for reflection, not a prediction."

@Composable
fun TarotScreen(app: App, session: Session) {
    var showLibrary by remember { mutableStateOf(false) }
    if (showLibrary) { LibraryScreen(app, onBack = { showLibrary = false }); return }
    val c = Astra.colors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Header: deep sky with a turning yantra
            CosmicBackground(Modifier.fillMaxWidth().height(if (wide) 170.dp else 150.dp), seed = 21, stars = 40) {
                Mandala(Modifier.size(if (wide) 320.dp else 240.dp).align(Alignment.TopEnd).padding(top = 0.dp).graphicsLayer { translationX = 70.dp.toPx(); translationY = -70.dp.toPx() }, alpha = 0.45f)
                Row(Modifier.align(Alignment.BottomStart).statusBarsPadding().padding(start = 20.dp, end = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Overline("Reading room")
                        Text("Tarot & oracles", style = Astra.type.hero, color = c.text)
                    }
                    TextLink("Saved (${app.readings.readings.size})", { showLibrary = true }, Modifier.testTag("tarotLibrary"))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DeckChips()
                val deck = TarotState.oracleDeck
                AnimatedContent(targetState = deck to TarotState.phase, transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) }, label = "room") { (d, ph) ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (d != null) OracleRoom(app, d, wide)
                        else when (ph) {
                            Phase.IDLE -> NewDraw()
                            Phase.SHUFFLING -> Shuffling()
                            Phase.FAN -> Fan(app, maxWidth)
                            Phase.SPREAD -> SpreadView(app, session, wide, maxWidth)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckChips() {
    val c = Astra.colors
    val options = listOf<Pair<OracleDeck?, String>>(null to "Classic tarot") + OracleDeck.entries.map { it to it.title }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (d, label) ->
            val sel = TarotState.oracleDeck == d
            Text(label, style = Astra.type.label, color = if (sel) c.onGold else c.text,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .then(if (sel) Modifier.background(app.astra.ui.theme.GoldLeaf) else Modifier.background(c.backgroundAlt).border(1.dp, c.border, RoundedCornerShape(50)))
                    .clickable(role = Role.Tab) { TarotState.oracleDeck = d; TarotState.oracle = null; TarotState.oracleRevealed = false }
                    .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 12.dp).testTag("deck_${d?.name ?: "CLASSIC"}"))
        }
    }
}

@Composable
private fun NewDraw() {
    val c = Astra.colors
    var question by rememberSaveable { mutableStateOf("") }
    var three by rememberSaveable { mutableStateOf(true) }
    val focus = LocalFocusManager.current
    AstraCard(Modifier.testTag("tarotIntro"), glint = true) {
        Overline("Classic tarot · Pamela Colman Smith, 1909")
        Text("Hold a question in mind", style = Astra.type.heading, color = c.text)
        AField(question, { question = it.take(StoryRules.MAX_TEXT) }, "Your question (optional)", placeholder = "e.g. What do I need to see about this job?", tag = "tarotQuestion")
        Segmented(listOf("One card", "Three cards"), if (three) 1 else 0, { three = it == 1 }, Modifier.fillMaxWidth())
        Text(if (three) "What's behind it · Where you are · What can help. Three-card draws can also be read with your chart." else "One card for a quick reflection.",
            style = Astra.type.meta, color = c.textSecondary)
        PrimaryButton("Shuffle the deck", { focus.clearFocus(); TarotState.startShuffle(if (three) Spread.THREE else Spread.ONE, question) },
            Modifier.fillMaxWidth().testTag("drawCards"))
    }
    Text(TAROT_NOTE, style = Astra.type.meta, color = c.textSecondary)
}

@Composable
private fun Shuffling() {
    val c = Astra.colors
    val still = reducedMotion()
    LaunchedEffect(Unit) { delay(if (still) 0 else 1600); TarotState.shuffleDone() }
    val k = if (still) 0.5f else {
        val t = rememberInfiniteTransition(label = "riffle")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "k").value
    }
    Box(Modifier.fillMaxWidth().height(260.dp).testTag("shuffling"), contentAlignment = Alignment.Center) {
        Mandala(Modifier.size(260.dp), alpha = 0.35f)
        TarotCardView(null, false, false, 92.dp, Modifier.graphicsLayer { translationX = -40.dp.toPx() * k; rotationZ = -10f * k })
        TarotCardView(null, false, false, 92.dp, Modifier.graphicsLayer { translationX = 40.dp.toPx() * k; rotationZ = 10f * k })
        TarotCardView(null, false, false, 92.dp)
    }
    Text("Shuffling the 78 cards…", style = Astra.type.label, color = c.textSecondary, modifier = Modifier.fillMaxWidth())
    TextLink("Skip", { TarotState.shuffleDone() })
}

@Composable
private fun Fan(app: App, available: Dp) {
    val c = Astra.colors
    val need = TarotState.pendingSpread.positions.size
    val left = need - TarotState.picks.size
    val cardW = min(available * 0.2f, 110.dp)
    val n = TarotState.FAN_SIZE
    Text(if (left == need) "Choose $need ${if (need == 1) "card" else "cards"} from the fan" else "$left more to choose",
        style = Astra.type.heading, color = c.text, modifier = Modifier.fillMaxWidth())
    Box(Modifier.fillMaxWidth().height(cardW * 2.6f).testTag("fan"), contentAlignment = Alignment.BottomCenter) {
        Mandala(Modifier.size(cardW * 3.4f).align(Alignment.Center), alpha = 0.25f)
        for (i in 0 until n) {
            val angle = -54f + i * 108f / (n - 1)
            val chosen = i in TarotState.picks
            TarotCardView(null, false, false, cardW,
                Modifier.padding(bottom = cardW * 0.15f)
                    .graphicsLayer { rotationZ = angle; transformOrigin = TransformOrigin(0.5f, 2.2f) }
                    .testTag("fan$i"),
                glow = chosen, onClick = { TarotState.pick(app, i) })
        }
    }
    TextLink("Choose for me", { TarotState.chooseForMe(app) }, Modifier.testTag("chooseForMe"))
}

@Composable
private fun ColumnScope.SpreadView(app: App, session: Session, wide: Boolean, available: Dp) {
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(14.dp)) { SpreadCards(available * 0.55f) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { ReadingPanel(app, session) }
    } else {
        SpreadCards(available - 40.dp)
        ReadingPanel(app, session)
    }
}

@Composable
private fun SpreadCards(width: Dp) {
    val c = Astra.colors
    val d = TarotState.draw ?: return
    if (d.question.isNotBlank()) Text("“${d.question}”", style = Astra.type.heading, color = c.text)
    Overline(d.spread.title + if (d.day != null) " · ${d.day}" else "", color = c.lavender)
    val cardW = if (d.cards.size == 1) min(width * 0.55f, 260.dp) else min((width - 28.dp) / 3, 230.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
        d.cards.forEachIndexed { i, dc ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TarotCardView(dc.card, dc.reversed, TarotState.revealed.getOrElse(i) { false }, cardW, Modifier.testTag("card$i"), onClick = { TarotState.reveal(i) })
                Text(dc.position.uppercase(), style = Astra.type.overline, color = c.lavender)
            }
        }
    }
    if (!TarotState.allRevealed) {
        Text("Tap each card to turn it over.", style = Astra.type.label, color = c.textSecondary, modifier = Modifier.fillMaxWidth())
        TextLink("Turn all over", { TarotState.revealAll() }, Modifier.testTag("revealAll"))
    }
}

@Composable
private fun ColumnScope.ReadingPanel(app: App, session: Session) {
    val c = Astra.colors
    val d = TarotState.draw ?: return
    val scope = rememberCoroutineScope()
    if (!TarotState.allRevealed) return
    AstraCard(Modifier.animateContentSize().testTag("cardMeanings")) {
        Overline("In tarot")
        d.cards.forEach { dc ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dc.card.name, style = Astra.type.heading, color = c.text, modifier = Modifier.weight(1f))
                Pill(if (dc.reversed) "Reversed" else "Upright", if (dc.reversed) c.lavender else c.accent, filled = dc.reversed)
            }
            Text("${dc.position} · ${dc.meaning}", style = Astra.type.label, color = c.textSecondary)
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
            SecondaryButton("✦ Cross-Reading with my chart" + (TarotState.crossLeft?.let { " · $it left today" } ?: ""), {
                if (!busy) scope.launch { TarotState.interpret(app, session, "cross") }
            }, Modifier.fillMaxWidth().testTag("crossReading"))
            Text("Your chart and these cards side by side, in five parts. Two a day.", style = Astra.type.meta, color = c.textSecondary)
        }
    }
    reading?.let { TurnView(app, it, cross = false) }
    cross?.let { TurnView(app, it, cross = true) }
    LotusDivider()
    SecondaryButton("New reading", { TarotState.reset() }, Modifier.fillMaxWidth().testTag("newReading"))
    Text(TAROT_NOTE, style = Astra.type.meta, color = c.textSecondary)
}

@Composable
private fun OracleRoom(app: App, deck: OracleDeck, wide: Boolean) {
    val c = Astra.colors
    var question by rememberSaveable(deck) { mutableStateOf("") }
    val draw = TarotState.oracle?.takeIf { it.deck == deck }
    AstraCard(glint = true) {
        Overline("ASTRA oracle · ${deck.title}")
        Text(deck.blurb, style = Astra.type.body, color = c.text)
        if (deck == OracleDeck.YES_NO) AField(question, { question = it.take(StoryRules.MAX_TEXT) }, "A yes-or-no question (optional)", tag = "oracleQuestion")
        PrimaryButton(if (draw == null) "Draw a card" else "Draw again", { TarotState.drawOracle(app, deck, question) }, Modifier.fillMaxWidth().testTag("drawOracle"))
    }
    if (draw != null) {
        val card = draw.card
        val w = if (wide) 300.dp else 220.dp
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Mandala(Modifier.size(w * 1.5f), alpha = 0.25f)
            OracleCardView(card, TarotState.oracleRevealed, w, Modifier.testTag("oracleCard"), onClick = { TarotState.oracleRevealed = true })
        }
        if (!TarotState.oracleRevealed) Text("Tap the card to turn it over.", style = Astra.type.label, color = c.textSecondary, modifier = Modifier.fillMaxWidth())
        else AstraCard(Modifier.testTag("oracleMeaning")) {
            Overline(card.title)
            Text(card.line, style = Astra.type.heading, color = c.text)
            card.nakshatra?.let { n ->
                Text("${card.meaning} · ruled by ${n.lord.english} · symbol: ${card.symbol} · deity: ${card.deity}", style = Astra.type.label, color = c.textSecondary)
                Text(Oracle.span(n), style = Astra.type.meta, color = c.textSecondary)
            }
            Text("An ASTRA oracle card: a prompt for reflection, not a prediction.", style = Astra.type.meta, color = c.textSecondary)
        }
    }
}

@Composable
private fun TurnView(app: App, t: ReadingTurn, cross: Boolean) {
    val c = Astra.colors
    when {
        t.loading -> Loading(if (cross) "Reading your chart and your cards…" else "Reading your cards…")
        t.error != null -> Notice(t.error!!)
        t.answer != null -> {
            val a = t.answer!!
            AstraCard(Modifier.animateContentSize().testTag(if (cross) "crossAnswer" else "tarotAnswer"), glint = true) {
                Overline(if (cross) "Cross-Reading" else "Your reading")
                val parts = if (cross && a.safety == "ok") TarotState.sections(a.answer, TarotState.crossLabels(app.profiles.profile?.language ?: ReplyLanguage.EN)) else null
                if (parts != null) parts.forEach { (label, body) ->
                    Text(label, style = Astra.type.heading, color = c.accent, modifier = Modifier.padding(top = 4.dp))
                    Text(body, style = Astra.type.body, color = c.text)
                } else Text(a.answer, style = Astra.type.body, color = c.text)
                var showItems by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.storyItems.isNotEmpty()) Box(Modifier.clickable { showItems = !showItems }) { Pill("✦ From your story · ${t.storyItems.size}", c.lavender, filled = true) }
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
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.backgroundAlt).clickable { onRate(v) }.heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 12.dp).testTag("rate$v"))
        }
    }
}

