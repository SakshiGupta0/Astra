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
import app.astra.core.Tarot
import app.astra.core.TarotDraw
import app.astra.core.DrawCodec
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.withTimeout

data class AskResult(val answer: String, val storyUsed: List<String>, val remaining: Int?, val model: String, val promptVersion: String, val safety: String, val mode: String = "ask")

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
    /** Saves a card draw to the user's space before any interpretation (PRD F-09). */
    suspend fun syncDraw(draw: TarotDraw)
    /** Tarot reading of a saved draw; one of the day's 15 messages. */
    suspend fun tarot(drawId: String, question: String, storyIds: List<String>): AskResult
    /** Cross-Reading of a saved three-card draw with the chart; 2 a day. */
    suspend fun cross(drawId: String, question: String, storyIds: List<String>): AskResult
}

class Cloud(private val context: Context) : AskBackend {
    /** Sign-in, story sync and backup need Firebase. */
    val firebaseReady: Boolean = BuildConfig.FIREBASE && FirebaseApp.getApps(context).isNotEmpty()
    /** Ask ASTRA also needs the AI server address. */
    override val available: Boolean = firebaseReady && BuildConfig.AI_URL.startsWith("https://")

    var accountLabel: String? by mutableStateOf(null)
        private set
    var backedUpWithGoogle: Boolean by mutableStateOf(false)
        private set

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private var lastFactsDay: String? = null

    init { if (firebaseReady) refreshAccount() }

    private fun refreshAccount() {
        val u = auth.currentUser
        backedUpWithGoogle = u != null && !u.isAnonymous
        accountLabel = u?.email ?: u?.displayName
    }

    private suspend fun uid(): String {
        if (!firebaseReady) throw CloudError.NotSetUp()
        auth.currentUser?.let { return it.uid }
        val u = withTimeout(20_000) { auth.signInAnonymously().await() }.user ?: throw CloudError.Failed("Couldn't start a session. Please try again.")
        refreshAccount()
        return u.uid
    }

    override suspend fun syncFacts(factsJson: String, day: String) {
        if (!firebaseReady) return
        val id = uid()
        withTimeout(20_000) { db.document("users/$id/facts/current").set(mapOf("json" to factsJson, "day" to day, "updated_at" to FieldValue.serverTimestamp())).await() }
        lastFactsDay = day
    }

    override suspend fun syncStory(story: Story) {
        if (!firebaseReady) return
        val id = uid()
        val items = StoryRules.items(story).map { mapOf("id" to it.id, "kind" to it.kind, "text" to it.text) }
        withTimeout(20_000) { db.document("users/$id/story/items").set(mapOf("consent" to story.enabled, "items" to items, "updated_at" to FieldValue.serverTimestamp())).await() }
    }

    override suspend fun ask(question: String, storyIds: List<String>): AskResult =
        call(mapOf("mode" to "ask", "question" to question, "storyIds" to storyIds))

    override suspend fun todayLine(): AskResult = call(mapOf("mode" to "today_line"))

    override suspend fun syncDraw(draw: TarotDraw) {
        if (!firebaseReady) return
        val id = uid()
        withTimeout(20_000) {
            db.document("users/$id/draws/${draw.id}").set(mapOf(
                "facts" to Json.write(Tarot.facts(draw)), "json" to DrawCodec.encode(draw), "at" to draw.drawnAt.toString(),
                "updated_at" to FieldValue.serverTimestamp())).await()
        }
    }

    override suspend fun tarot(drawId: String, question: String, storyIds: List<String>): AskResult =
        call(mapOf("mode" to "tarot", "drawId" to drawId, "question" to question, "storyIds" to storyIds))

    override suspend fun cross(drawId: String, question: String, storyIds: List<String>): AskResult =
        call(mapOf("mode" to "cross", "drawId" to drawId, "question" to question, "storyIds" to storyIds))

    /** Evidence of a My Story consent change, with the copy version shown (PRD 3.3, DPDP). */
    suspend fun recordConsent(granted: Boolean, copyVersion: String, offerPoint: String, at: java.time.Instant) {
        if (!firebaseReady) return
        val id = uid()
        withTimeout(20_000) {
            db.collection("users/$id/consents").add(mapOf(
                "purpose" to "my_story", "granted" to granted, "copy_version" to copyVersion, "offer_point" to offerPoint,
                "at" to at.toString(), "recorded_at" to FieldValue.serverTimestamp())).await()
        }
    }

    /** Posts JSON to the AI server (events, feedback, delete-me). Returns the HTTP status. */
    suspend fun post(path: String, body: Map<String, Any?>): Int {
        if (!available) throw CloudError.NotSetUp()
        uid()
        val token = withTimeout(20_000) { auth.currentUser!!.getIdToken(false).await() }.token ?: throw CloudError.Failed("Couldn't confirm your session.")
        return try {
            withContext(Dispatchers.IO) {
                val conn = (URL(BuildConfig.AI_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 30_000; doOutput = true
                    setRequestProperty("content-type", "application/json"); setRequestProperty("authorization", "Bearer $token")
                }
                try {
                    conn.outputStream.use { it.write(Json.write(body).toByteArray()) }
                    conn.responseCode.also { runCatching { (if (it in 200..299) conn.inputStream else conn.errorStream)?.close() } }
                } finally { conn.disconnect() }
            }
        } catch (e: java.io.IOException) { throw CloudError.Offline() }
    }

    suspend fun sendFeedback(text: String) {
        val code = post("/feedback", mapOf("text" to text, "app" to BuildConfig.VERSION_NAME))
        if (code == 429) throw CloudError.Failed("Thanks! You've sent a lot of feedback today. Please send more tomorrow.")
        if (code !in 200..299) throw CloudError.Failed("Couldn't send just now. Please try again.")
    }

    /**
     * Deletes everything about this user on the server: counters, usage events and feedback (AI server),
     * then every document in their Firestore space, then the sign-in account itself.
     */
    suspend fun deleteAccount() {
        if (!firebaseReady) return
        val user = auth.currentUser ?: return
        val id = user.uid
        if (available) { val code = post("/delete-me", emptyMap()); if (code !in 200..299) throw CloudError.Failed("Couldn't reach the server to delete your data. Please try again.") }
        withTimeout(60_000) {
            for (coll in listOf("draws", "consents", "facts", "story", "backup", "notify", "today")) {
                val docs = db.collection("users/$id/$coll").get().await()
                for (d in docs.documents) d.reference.delete().await()
            }
            db.document("users/$id").delete().await()
        }
        try { withTimeout(20_000) { user.delete().await() } }
        catch (e: Exception) { auth.signOut() } // Google accounts may need a fresh sign-in to delete; the data is already gone.
        refreshAccount()
    }

    /** POST to the AI server with the user's Firebase ID token. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun call(data: Map<String, Any?>): AskResult {
        if (!available) throw CloudError.NotSetUp()
        uid()
        val token = withTimeout(20_000) { auth.currentUser!!.getIdToken(false).await() }.token ?: throw CloudError.Failed("Couldn't confirm your session. Please try again.")
        val (status, text) = try {
            withContext(Dispatchers.IO) {
                val conn = (URL(BuildConfig.AI_URL.trimEnd('/') + "/ask").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 120_000; doOutput = true
                    setRequestProperty("content-type", "application/json"); setRequestProperty("authorization", "Bearer $token")
                }
                try {
                    conn.outputStream.use { it.write(Json.write(data).toByteArray()) }
                    val code = conn.responseCode
                    code to ((if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: "")
                } finally { conn.disconnect() }
            }
        } catch (e: java.io.IOException) { throw CloudError.Offline() }
        val m = runCatching { Json.parse(text) as Map<String, Any?> }.getOrNull() ?: throw CloudError.Failed("The AI server sent an unexpected reply ($status). Please try again.")
        if (status != 200) {
            val err = m["error"] as? Map<String, Any?>
            val msg = (err?.get("message") as String?) ?: "Please try again."
            throw if (status == 429) CloudError.Limit(msg, err?.get("resetsAt") as String?) else CloudError.Failed(msg)
        }
        return AskResult(
            answer = m["answer"] as String,
            storyUsed = (m["storyUsed"] as List<String>?) ?: emptyList(),
            remaining = (m["remaining"] as Number?)?.toInt(),
            model = (m["model"] as String?) ?: "", promptVersion = (m["promptVersion"] as String?) ?: "",
            safety = (m["safety"] as String?) ?: "ok",
            mode = (m["mode"] as String?) ?: (data["mode"] as String? ?: "ask")
        )
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
        if (!firebaseReady || !backedUpWithGoogle) return
        val id = uid()
        withTimeout(20_000) { db.document("users/$id/backup/main").set(mapOf("story" to storyJson, "readings" to readingsJson, "updated_at" to FieldValue.serverTimestamp())).await() }
    }

    suspend fun restore(): Pair<String?, String?>? {
        if (!firebaseReady || !backedUpWithGoogle) return null
        val id = uid()
        val snap = withTimeout(20_000) { db.document("users/$id/backup/main").get().await() }
        return if (snap.exists()) snap.getString("story") to snap.getString("readings") else null
    }

    fun factsFreshFor(day: String) = lastFactsDay == day

    companion object {
        fun toJson(facts: Map<String, Any?>) = Json.write(facts)
    }
}
