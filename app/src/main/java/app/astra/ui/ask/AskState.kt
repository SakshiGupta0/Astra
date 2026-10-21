package app.astra.ui.ask

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.App
import app.astra.core.Reading
import app.astra.core.StoryItem
import app.astra.core.StoryRules
import app.astra.data.AskResult
import app.astra.data.Cloud
import app.astra.data.CloudError
import app.astra.ui.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class Turn(val id: String, val question: String) {
    var answer by mutableStateOf<AskResult?>(null)
    var storyItems by mutableStateOf<List<StoryItem>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var savedId by mutableStateOf<String?>(null)
    /** "Add to My Story?" suggestion: null = not offered, false = dismissed, true = added. */
    var suggestionAnswered by mutableStateOf<Boolean?>(null)
}

/** Conversation for this app session (kept across tab switches; not stored unless saved). */
object AskState {
    val turns = mutableStateListOf<Turn>()
    var remaining by mutableStateOf<Int?>(null)
    /** A question handed over from another screen ("Ask about this"), placed in the box for the user to send. */
    var draft by mutableStateOf<String?>(null)
    private var storySyncedThisSession = false

    suspend fun ask(app: App, session: Session, question: String) {
        val q = question.trim()
        if (q.length < 2) return
        val turn = Turn(StoryRules.newId(), q)
        turns += turn
        val started = System.currentTimeMillis()
        try {
            val backend = app.backend
            if (!backend.available) throw CloudError.NotSetUp()
            val day = session.today.toString()
            val fresh = (backend as? Cloud)?.factsFreshFor(day) == true
            if (!fresh) backend.syncFacts(withContext(Dispatchers.Default) { session.factsJson() }, day)
            val story = app.story.story
            if (story.enabled && !storySyncedThisSession) { backend.syncStory(story); storySyncedThisSession = true }
            val picked = StoryRules.select(story, q)
            val res = backend.ask(q, picked.map { it.id })
            val all = StoryRules.items(story).associateBy { it.id }
            turn.storyItems = res.storyUsed.mapNotNull { all[it] }
            turn.answer = res
            if (res.remaining != null) remaining = res.remaining
            app.analytics.log("answer_completed", "mode" to "ask", "intent" to intent(q), "story_items_used_count" to res.storyUsed.size,
                "language" to session.profile.language.name.lowercase(), "latency_ms" to (System.currentTimeMillis() - started), "safety" to res.safety)
        } catch (e: CloudError) {
            turn.error = e.message
            app.analytics.log("answer_failed", "mode" to "ask", "reason" to e.javaClass.simpleName)
        } catch (e: Exception) {
            turn.error = "Something went wrong (${e.message ?: e.javaClass.simpleName}). Please try again."
            app.analytics.log("answer_failed", "mode" to "ask", "reason" to e.javaClass.simpleName)
        } finally {
            turn.loading = false
        }
    }

    fun save(app: App, turn: Turn): Reading? {
        val a = turn.answer ?: return null
        val r = Reading(turn.id, "ask", turn.question, a.answer, a.storyUsed,
            app.profiles.profile?.language?.name?.lowercase() ?: "en", a.model, a.promptVersion, Instant.now())
        if (!app.readings.save(r)) return null
        turn.savedId = r.id
        app.analytics.log("reading_saved", "type" to "ask")
        // Keep a one-line note in My Story so later answers know what was asked before (only with consent).
        if (app.story.story.enabled) app.story.update { StoryRules.addDigest(it, "Asked: ${turn.question.take(120)}", java.time.LocalDate.now()) }
        return r
    }

    /** Rough intent class for analytics only (PRD F-07), worked out on the phone; the question itself is never sent. */
    fun intent(q: String): String {
        val t = q.lowercase()
        return when {
            Regex("\\b(when|kab|kitne|how long|end|khatam|start)\\b").containsMatchIn(t) -> "timing"
            Regex("\\b(remedy|remedies|upaya|mantra)\\b").containsMatchIn(t) -> "remedy"
            Regex("\\b(dasha|transit|sade sati|saturn|jupiter|rahu|ketu|tracker|period|tone)\\b").containsMatchIn(t) -> "tracker"
            Regex("\\b(what is|explain|meaning of|kya hota)\\b").containsMatchIn(t) -> "educational"
            else -> "chart"
        }
    }

    /** Suggests a new open question for My Story when the question touches a life area the story doesn't cover yet. */
    fun suggestion(story: app.astra.core.Story, q: String): String? {
        if (!story.enabled || story.threads.size >= StoryRules.MAX_THREADS) return null
        val areas = app.astra.core.LifeArea.detect(q)
        if (areas.isEmpty()) return null
        if (story.threads.any { t -> t.status == app.astra.core.ThreadStatus.OPEN && t.area?.let { a -> a in areas } == true }) return null
        val title = q.trim().trimEnd('?', '.', '!').take(80)
        return title.ifBlank { null }
    }

    fun resetForTests() { turns.clear(); remaining = null; storySyncedThisSession = false; draft = null }
}
