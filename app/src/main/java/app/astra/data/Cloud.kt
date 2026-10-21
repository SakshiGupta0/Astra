// Sign-in, backup of what the AI may use, and calls to the AI gateway. Firebase only when configured.
package app.astra.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.BuildConfig
import app.astra.core.Json
import app.astra.core.Story
import app.astra.core.StoryRules
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

data class AskResult(val answer: String, val storyUsed: List<String>, val remaining: Int?, val model: String, val promptVersion: String, val safety: String)

sealed class CloudError(message: String) : Exception(message) {
    class NotSetUp : CloudError("Ask ASTRA is still being set up. Your chart, tracker and Today work on this phone.")
    class Offline : CloudError("You seem to be offline. Ask again when you're connected.")
    class Limit(message: String, val resetsAt: String?) : CloudError(message)
    class Failed(message: String) : CloudError(message)
}

/** What the screens need from the server. Replaced by a fake in tests. */
interface AskBackend {
    val available: Boolean
    suspend fun syncFacts(factsJson: String, day: String)
    suspend fun syncStory(story: Story)
    suspend fun ask(question: String, storyIds: List<String>): AskResult
    suspend fun todayLine(): AskResult
}

class Cloud(private val context: Context) : AskBackend {
    override val available: Boolean = BuildConfig.FIREBASE && FirebaseApp.getApps(context).isNotEmpty()

    var accountLabel: String? by mutableStateOf(null)
        private set
    var backedUpWithGoogle: Boolean by mutableStateOf(false)
        private set

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val functions get() = FirebaseFunctions.getInstance("asia-south1")
    private var lastFactsDay: String? = null

    init { if (available) refreshAccount() }

    private fun refreshAccount() {
        val u = auth.currentUser
        backedUpWithGoogle = u != null && !u.isAnonymous
        accountLabel = u?.email ?: u?.displayName
    }

    private suspend fun uid(): String {
        if (!available) throw CloudError.NotSetUp()
        auth.currentUser?.let { return it.uid }
        val u = withTimeout(20_000) { auth.signInAnonymously().await() }.user ?: throw CloudError.Failed("Couldn't start a session. Please try again.")
        refreshAccount()
        return u.uid
    }

    override suspend fun syncFacts(factsJson: String, day: String) {
        if (!available) return
        val id = uid()
        withTimeout(20_000) { db.document("users/$id/facts/current").set(mapOf("json" to factsJson, "day" to day, "updated_at" to FieldValue.serverTimestamp())).await() }
        lastFactsDay = day
    }

    override suspend fun syncStory(story: Story) {
        if (!available) return
        val id = uid()
        val items = StoryRules.items(story).map { mapOf("id" to it.id, "kind" to it.kind, "text" to it.text) }
        withTimeout(20_000) { db.document("users/$id/story/items").set(mapOf("consent" to story.enabled, "items" to items, "updated_at" to FieldValue.serverTimestamp())).await() }
    }

    override suspend fun ask(question: String, storyIds: List<String>): AskResult =
        call(mapOf("mode" to "ask", "question" to question, "storyIds" to storyIds))

    override suspend fun todayLine(): AskResult = call(mapOf("mode" to "today_line"))

    @Suppress("UNCHECKED_CAST")
    private suspend fun call(data: Map<String, Any?>): AskResult {
        uid()
        try {
            val res = withTimeout(130_000) { functions.getHttpsCallable("ask").call(data).await() }
            val m = res.data as Map<String, Any?>
            return AskResult(
                answer = m["answer"] as String,
                storyUsed = (m["storyUsed"] as List<String>?) ?: emptyList(),
                remaining = (m["remaining"] as Number?)?.toInt(),
                model = (m["model"] as String?) ?: "", promptVersion = (m["promptVersion"] as String?) ?: "",
                safety = (m["safety"] as String?) ?: "ok"
            )
        } catch (e: FirebaseFunctionsException) {
            val details = e.details as? Map<String, Any?>
            throw when (e.code) {
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> CloudError.Limit(e.message ?: "Daily limit reached.", details?.get("resetsAt") as String?)
                FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                    if (e.cause is java.io.IOException) CloudError.Offline() else CloudError.Failed(e.message ?: "Please try again.")
                else -> CloudError.Failed(e.message ?: "Please try again.")
            }
        } catch (e: java.io.IOException) {
            throw CloudError.Offline()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw CloudError.Failed("That took too long. Please try again.")
        }
    }

    /** Back up with Google: links the current anonymous account, or switches to an existing one (returns true if switched). */
    suspend fun linkGoogle(credential: AuthCredential): Pair<String, Boolean> {
        uid()
        val user = auth.currentUser!!
        var switched = false
        val result = try {
            user.linkWithCredential(credential).await()
        } catch (e: FirebaseAuthUserCollisionException) {
            switched = true
            auth.signInWithCredential(credential).await()
        }
        lastFactsDay = null
        refreshAccount()
        return (result.user?.email ?: result.user?.displayName ?: "your Google account") to switched
    }

    /** Full copy of story and saved readings, only for users who chose to back up with Google. */
    suspend fun backup(storyJson: String, readingsJson: String) {
        if (!available || !backedUpWithGoogle) return
        val id = uid()
        withTimeout(20_000) { db.document("users/$id/backup/main").set(mapOf("story" to storyJson, "readings" to readingsJson, "updated_at" to FieldValue.serverTimestamp())).await() }
    }

    suspend fun restore(): Pair<String?, String?>? {
        if (!available || !backedUpWithGoogle) return null
        val id = uid()
        val snap = withTimeout(20_000) { db.document("users/$id/backup/main").get().await() }
        return if (snap.exists()) snap.getString("story") to snap.getString("readings") else null
    }

    fun factsFreshFor(day: String) = lastFactsDay == day

    companion object {
        fun toJson(facts: Map<String, Any?>) = Json.write(facts)
    }
}
