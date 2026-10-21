// ASTRA v0.3.0
package app.astra

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.core.Story
import app.astra.data.AskBackend
import app.astra.data.AskResult
import app.astra.data.CloudError
import app.astra.data.Cloud
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

class App : Application() {
    lateinit var profiles: ProfileRepository; private set
    lateinit var places: PlaceRepository; private set
    lateinit var story: StoryRepository; private set
    lateinit var readings: ReadingRepository; private set
    lateinit var notify: NotifyRepository; private set
    lateinit var cloud: Cloud; private set

    /** Where Ask ASTRA sends questions. Tests swap in a fake. */
    var backend: AskBackend by mutableStateOf<AskBackend>(NoBackend)

    /** Last sync problem, shown quietly in Me → Account. */
    var syncNote: String? by mutableStateOf(null)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileRepository(filesDir)
        places = PlaceRepository(this)
        story = StoryRepository(filesDir) { s -> syncStory(s) }
        readings = ReadingRepository(filesDir) { backupLater() }
        notify = NotifyRepository(this)
        cloud = Cloud(this)
        backend = cloud
        Notifications.createChannels(this)
        Notifications.schedule(this)
        // Warm the offline place list in the background so search is instant when onboarding reaches it.
        if (profiles.profile == null) scope.launch { runCatching { places.index() } }
    }

    fun syncStory(s: Story = story.story) {
        scope.launch {
            runCatching { backend.syncStory(s) }.onFailure { syncNote = "Your story will sync when you're online." }.onSuccess { syncNote = null }
        }
        backupLater()
    }

    fun backupLater() {
        if (!cloud.backedUpWithGoogle) return
        scope.launch {
            runCatching { cloud.backup(app.astra.core.StoryCodec.encode(story.story), app.astra.core.ReadingCodec.encode(readings.readings)) }
                .onFailure { syncNote = "Backup will finish when you're online." }
        }
    }

    /** After switching to an existing Google account: bring back its story and readings if this phone has none. */
    suspend fun restoreIfEmpty(): Boolean {
        val local = story.story.isEmpty && readings.readings.isEmpty()
        val remote = cloud.restore() ?: return false
        if (!local) { backupLater(); return false }
        val st = remote.first?.let { app.astra.core.StoryCodec.decode(it) }
        val rd = remote.second?.let { app.astra.core.ReadingCodec.decode(it) } ?: emptyList()
        if (st != null) story.update { st }
        rd.reversed().forEach { readings.save(it) }
        return st != null || rd.isNotEmpty()
    }

    /** Removes everything ASTRA keeps on this phone. */
    fun deleteEverything() {
        profiles.clear(); story.clearFile(); readings.clearFile()
        notify.reset(); Notifications.schedule(this)
        getSharedPreferences("today_line", MODE_PRIVATE).edit().clear().apply()
    }
}

/** Used until Firebase is configured, and in tests that don't need the AI. */
object NoBackend : AskBackend {
    override val available = false
    override suspend fun syncFacts(factsJson: String, day: String) {}
    override suspend fun syncStory(story: Story) {}
    override suspend fun ask(question: String, storyIds: List<String>): AskResult = throw CloudError.NotSetUp()
    override suspend fun todayLine(): AskResult = throw CloudError.NotSetUp()
}
