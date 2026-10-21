package app.astra.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.core.Story
import app.astra.core.StoryCodec
import java.io.File

/** My Story lives on the phone; a copy of the consented items is synced to the user's own Firestore space for the AI. */
class StoryRepository(dir: File, private val onChange: (Story) -> Unit = {}) {
    private val file = File(dir, "story.json")

    var story: Story by mutableStateOf(load())
        private set

    var lastError: String? by mutableStateOf(null)
        private set

    private fun load(): Story = runCatching { if (file.exists()) StoryCodec.decode(file.readText()) else null }.getOrNull() ?: Story()

    fun update(transform: (Story) -> Story): Boolean = try {
        val next = transform(story)
        file.writeAtomically(StoryCodec.encode(next))
        story = next; lastError = null
        onChange(next); true
    } catch (e: IllegalArgumentException) {
        lastError = e.message; false
    } catch (e: Exception) {
        lastError = "Couldn't save your story on this phone (${e.message}). Please try again."; false
    }

    fun clearFile() { file.delete(); story = Story() }
}
