// Tarot studio state for this app session: the current draw, which cards are turned over, and AI readings of it.
package app.astra.ui.tarot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.App
import app.astra.core.Reading
import app.astra.core.ReplyLanguage
import app.astra.core.Spread
import app.astra.core.Oracle
import app.astra.core.StoryItem
import app.astra.core.StoryRules
import app.astra.core.Tarot
import app.astra.core.TarotDraw
import app.astra.data.AskResult
import app.astra.data.Cloud
import app.astra.data.CloudError
import app.astra.ui.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

class ReadingTurn(val mode: String) {
    var answer by mutableStateOf<AskResult?>(null)
    var storyItems by mutableStateOf<List<StoryItem>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var savedId by mutableStateOf<String?>(null)
    var rating by mutableStateOf<Int?>(null)
}

enum class Phase { IDLE, SHUFFLING, FAN, SPREAD }

object TarotState {
    /** Null = the classic tarot; otherwise the oracle deck being used. */
    var oracleDeck by mutableStateOf<app.astra.core.OracleDeck?>(null)
    var oracle by mutableStateOf<app.astra.core.OracleDraw?>(null)
    var oracleRevealed by mutableStateOf(false)

    var phase by mutableStateOf(Phase.IDLE)
    private var shuffled: List<app.astra.core.DrawnCard> = emptyList()
    /** Fan positions the user has tapped, in order. */
    val picks = mutableStateListOf<Int>()
    var pendingSpread by mutableStateOf(Spread.THREE)
    private var pendingQuestion = ""
    /** Cards shown in the fan. */
    const val FAN_SIZE = 21

    /** Shuffles the whole deck; the user then chooses their cards from the fan. */
    fun startShuffle(spread: Spread, question: String) {
        shuffled = Tarot.shuffle(); picks.clear(); pendingSpread = spread; pendingQuestion = question
        draw = null; reading = null; cross = null; revealed.clear(); phase = Phase.SHUFFLING
    }

    fun shuffleDone() { if (phase == Phase.SHUFFLING) phase = Phase.FAN }

    /** Taps a card in the fan; when enough are chosen, the draw is made from them (and saved before any reading). */
    fun pick(app: App, i: Int) {
        if (phase != Phase.FAN || i in picks || i !in 0 until FAN_SIZE) return
        picks += i
        if (picks.size == pendingSpread.positions.size) {
            val d = Tarot.fromPicks(shuffled, picks.toList(), pendingSpread, pendingQuestion)
            open(d, faceUp = false)
            app.analytics.log("tarot_drawn", "spread" to pendingSpread.name.lowercase())
        }
    }

    fun chooseForMe(app: App) { var k = 0; while (phase == Phase.FAN && k < FAN_SIZE) { if (k !in picks) pick(app, k); k++ } }

    fun drawOracle(app: App, deck: app.astra.core.OracleDeck, question: String) {
        oracle = Oracle.draw(deck, question); oracleRevealed = false
        app.analytics.log("tarot_drawn", "spread" to "oracle_" + deck.name.lowercase())
    }

    var draw by mutableStateOf<TarotDraw?>(null)
        private set
    val revealed = mutableStateListOf<Boolean>()
    var reading by mutableStateOf<ReadingTurn?>(null)
        private set
    var cross by mutableStateOf<ReadingTurn?>(null)
        private set
    var crossLeft by mutableStateOf<Int?>(null)
    private var syncedDrawId: String? = null

    val allRevealed: Boolean get() = draw != null && revealed.isNotEmpty() && revealed.all { it }

    fun newDraw(app: App, spread: Spread, question: String): TarotDraw {
        val d = Tarot.draw(spread, question)
        open(d, faceUp = false)
        app.analytics.log("tarot_drawn", "spread" to spread.name.lowercase())
        return d
    }

    /** Opens an existing draw (today's card, or a saved reading). */
    fun open(d: TarotDraw, faceUp: Boolean) {
        draw = d; phase = Phase.SPREAD; oracleDeck = null
        revealed.clear(); repeat(d.cards.size) { revealed += faceUp }
        reading = null; cross = null
    }

    fun reveal(i: Int) { if (i in revealed.indices) revealed[i] = true }
    fun revealAll() { for (i in revealed.indices) revealed[i] = true }

    fun reset() { draw = null; revealed.clear(); reading = null; cross = null; phase = Phase.IDLE; picks.clear(); oracle = null; oracleRevealed = false }

    /** Saves the draw to the user's space before any interpretation (the server reads it from there). */
    private suspend fun ensureSynced(app: App, session: Session, d: TarotDraw, withFacts: Boolean) {
        val backend = app.backend
        if (withFacts) {
            val day = session.today.toString()
            if ((backend as? Cloud)?.factsFreshFor(day) != true) backend.syncFacts(withContext(Dispatchers.Default) { session.factsJson() }, day)
        }
        if (syncedDrawId != d.id) { backend.syncDraw(d); syncedDrawId = d.id }
        if (app.story.story.enabled) backend.syncStory(app.story.story)
    }

    suspend fun interpret(app: App, session: Session, mode: String) {
        val d = draw ?: return
        val turn = ReadingTurn(mode)
        if (mode == "cross") { cross = turn; app.analytics.log("cross_reading_started") } else reading = turn
        val started = System.currentTimeMillis()
        try {
            val backend = app.backend
            if (!backend.available) throw CloudError.NotSetUp()
            // Facts are needed for the Cross-Reading; a tarot-only reading still needs the language from them.
            ensureSynced(app, session, d, withFacts = true)
            val story = app.story.story
            val picked = StoryRules.select(story, d.question.ifBlank { "tarot reading" })
            val res = if (mode == "cross") backend.cross(d.id, d.question, picked.map { it.id }) else backend.tarot(d.id, d.question, picked.map { it.id })
            val all = StoryRules.items(story).associateBy { it.id }
            turn.storyItems = res.storyUsed.mapNotNull { all[it] }
            turn.answer = res
            if (mode == "cross") { crossLeft = res.remaining; app.analytics.log("cross_reading_completed", "safety" to res.safety) }
            app.analytics.log("answer_completed", "mode" to mode, "intent" to mode, "story_items_used_count" to res.storyUsed.size,
                "language" to session.profile.language.name.lowercase(), "latency_ms" to (System.currentTimeMillis() - started), "safety" to res.safety)
        } catch (e: CloudError) {
            turn.error = e.message
            app.analytics.log("answer_failed", "mode" to mode, "reason" to e.javaClass.simpleName)
        } catch (e: Exception) {
            turn.error = "Something went wrong (${e.message ?: e.javaClass.simpleName}). Please try again."
            app.analytics.log("answer_failed", "mode" to mode, "reason" to e.javaClass.simpleName)
        } finally { turn.loading = false }
    }

    fun save(app: App, turn: ReadingTurn): Reading? {
        val d = draw ?: return null
        val a = turn.answer ?: return null
        val r = Reading(StoryRules.newId(), turn.mode, d.question.ifBlank { d.spread.title }, a.answer, a.storyUsed,
            app.profiles.profile?.language?.name?.lowercase() ?: "en", a.model, a.promptVersion, Instant.now(), d, turn.rating)
        if (!app.readings.save(r)) return null
        turn.savedId = r.id
        app.analytics.log("reading_saved", "type" to turn.mode)
        if (app.story.story.enabled) app.story.update {
            StoryRules.addDigest(it, (if (turn.mode == "cross") "Cross-Reading" else "Tarot") + ": " + d.cards.joinToString { c -> c.card.name } +
                (if (d.question.isNotBlank()) " — ${d.question.take(80)}" else ""), LocalDate.now())
        }
        return r
    }

    fun rate(app: App, turn: ReadingTurn, rating: Int) {
        turn.rating = rating
        app.analytics.log("cross_reading_rated", "rating" to rating)
        turn.savedId?.let { id -> app.readings.readings.firstOrNull { it.id == id }?.let { app.readings.save(it.copy(rating = rating)) } }
    }

    /** Section labels the server uses for a Cross-Reading in each reply language. */
    fun crossLabels(lang: ReplyLanguage): List<String> = if (lang == ReplyLanguage.HI)
        listOf("आपकी कुंडली", "आपके कार्ड", "जहाँ दोनों मिलते हैं", "जहाँ दोनों अलग हैं", "सोचने के लिए सवाल")
    else listOf("Your chart", "Your cards", "Where they meet", "Where they differ", "Questions to sit with")

    /** Splits a Cross-Reading into its five sections; null if the labels aren't all there in order. */
    fun sections(text: String, labels: List<String>): List<Pair<String, String>>? {
        val starts = ArrayList<Int>()
        var from = 0
        for (l in labels) { val i = text.indexOf("$l:", from); if (i < 0) return null; starts += i; from = i + 1 }
        return labels.mapIndexed { k, l ->
            val s = starts[k] + l.length + 1
            val e = if (k + 1 < starts.size) starts[k + 1] else text.length
            l to text.substring(s, e).trim()
        }
    }

    fun resetForTests() { reset(); crossLeft = null; syncedDrawId = null; oracleDeck = null }
}
