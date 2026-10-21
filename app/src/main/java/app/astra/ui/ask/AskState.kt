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
}

/** Conversation for this app session (kept across tab switches; not stored unless saved). */
object AskState {
    val turns = mutableStateListOf<Turn>()
    var remaining by mutableStateOf<Int?>(null)
    private var storySyncedThisSession = false

    suspend fun ask(app: App, session: Session, question: String) {
        val q = question.trim()
        if (q.length < 2) return
        val turn = Turn(StoryRules.newId(), q)
        turns += turn
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
        } catch (e: CloudError) {
            turn.error = e.message
        } catch (e: Exception) {
            turn.error = "Something went wrong (${e.message ?: e.javaClass.simpleName}). Please try again."
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
        // Keep a one-line note in My Story so later answers know what was asked before (only with consent).
        if (app.story.story.enabled) app.story.update { StoryRules.addDigest(it, "Asked: ${turn.question.take(120)}", java.time.LocalDate.now()) }
        return r
    }

    fun resetForTests() { turns.clear(); remaining = null; storySyncedThisSession = false }
}
