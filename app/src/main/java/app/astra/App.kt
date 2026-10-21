// ASTRA v0.5.0
package app.astra

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.core.ReadingCodec
import app.astra.core.Story
import app.astra.core.StoryCodec
import app.astra.core.StoryRules
import app.astra.core.StoryThread
import app.astra.data.Analytics
import app.astra.data.AskBackend
import app.astra.data.AskResult
import app.astra.data.CloudError
import app.astra.data.Cloud
import app.astra.data.DrawRepository
import app.astra.data.NotifyRepository
import app.astra.data.PlaceRepository
import app.astra.data.ProfileRepository
import app.astra.data.ReadingRepository
import app.astra.data.StoryRepository
import app.astra.system.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class App : Application() {
    lateinit var profiles: ProfileRepository; private set
    lateinit var places: PlaceRepository; private set
    lateinit var story: StoryRepository; private set
    lateinit var readings: ReadingRepository; private set
    lateinit var notify: NotifyRepository; private set
    lateinit var draws: DrawRepository; private set
    lateinit var cloud: Cloud; private set
    lateinit var analytics: Analytics; private set

    /** Where Ask ASTRA sends questions. Tests swap in a fake. */
    var backend: AskBackend by mutableStateOf<AskBackend>(NoBackend)

    /** Last sync problem, shown quietly in Me → Account. */
    var syncNote: String? by mutableStateOf(null)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Last consent state we recorded, so each change is written once as evidence. */
    private var lastConsent: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileRepository(filesDir)
        places = PlaceRepository(this)
        story = StoryRepository(filesDir) { s -> syncStory(s) }
        readings = ReadingRepository(filesDir) { backupLater() }
        notify = NotifyRepository(this)
        draws = DrawRepository(filesDir)
        cloud = Cloud(this)
        backend = cloud
        // Usage events and consent records go to the real servers only when the real backend is in use (never from tests).
        analytics = Analytics(this, { cloud.takeIf { backend === it } }, scope)
        analytics.installCrashCapture()
        lastConsent = story.story.enabled
        Notifications.createChannels(this)
        Notifications.schedule(this)
        // Warm the offline place list in the background so search is instant when onboarding reaches it.
        if (profiles.profile == null) scope.launch { runCatching { places.index() } }
        analytics.flush()
    }

    fun syncStory(s: Story = story.story) {
        scope.launch {
            runCatching { backend.syncStory(s) }.onFailure { syncNote = "Your story will sync when you're online." }.onSuccess { syncNote = null }
        }
        // Consent evidence (PRD 3.3): one record per change, with the copy version that was shown.
        val now = s.enabled
        if (lastConsent != null && now != lastConsent) {
            val c = s.consent
            analytics.log("story_consent_changed", "state" to if (now) "on" else "off", "offer_point" to (c?.offerPoint ?: "settings"), "consent_version" to (c?.copyVersion ?: StoryRules.CONSENT_COPY_VERSION))
            if (c != null && backend === cloud) scope.launch { runCatching { cloud.recordConsent(now, c.copyVersion, c.offerPoint, c.at) } }
        }
        lastConsent = now
        backupLater()
    }

    fun backupLater() {
        if (!cloud.backedUpWithGoogle) return
        scope.launch {
            runCatching { cloud.backup(StoryCodec.encode(story.story), ReadingCodec.encode(readings.readings)) }
                .onFailure { syncNote = "Backup will finish when you're online." }
        }
    }

    /** Starts at most one check-in a day (see StoryRules.rollCheckins). Returns the thread to ask about, if any. */
    fun rollCheckins(today: LocalDate = LocalDate.now()): StoryThread? {
        val (next, thread) = StoryRules.rollCheckins(story.story, today)
        if (thread != null) story.update { next }
        return thread
    }

    /** After switching to an existing Google account: bring back its story and readings if this phone has none. */
    suspend fun restoreIfEmpty(): Boolean {
        val local = story.story.isEmpty && readings.readings.isEmpty()
        val remote = cloud.restore() ?: return false
        if (!local) { backupLater(); return false }
        val st = remote.first?.let { StoryCodec.decode(it) }
        val rd = remote.second?.let { ReadingCodec.decode(it) } ?: emptyList()
        if (st != null) story.update { st }
        rd.reversed().forEach { readings.save(it) }
        return st != null || rd.isNotEmpty()
    }

    /** Removes everything ASTRA keeps on this phone. */
    fun deleteEverything() {
        profiles.clear(); story.clearFile(); readings.clearFile(); draws.clearFile()
        notify.reset(); Notifications.schedule(this)
        analytics.clear()
        lastConsent = false
        getSharedPreferences("today_line", MODE_PRIVATE).edit().clear().apply()
    }

    /** Deletes the account and everything on the server, then everything on this phone (PRD F-06, DPDP). */
    suspend fun deleteAccount() {
        cloud.deleteAccount()
        deleteEverything()
    }
}

/** Used until Firebase is configured, and in tests that don't need the AI. */
object NoBackend : AskBackend {
    override val available = false
    override suspend fun syncFacts(factsJson: String, day: String) {}
    override suspend fun syncStory(story: Story) {}
    override suspend fun ask(question: String, storyIds: List<String>): AskResult = throw CloudError.NotSetUp()
    override suspend fun todayLine(): AskResult = throw CloudError.NotSetUp()
    override suspend fun syncDraw(draw: app.astra.core.TarotDraw) {}
    override suspend fun tarot(drawId: String, question: String, storyIds: List<String>): AskResult = throw CloudError.NotSetUp()
    override suspend fun cross(drawId: String, question: String, storyIds: List<String>): AskResult = throw CloudError.NotSetUp()
}
