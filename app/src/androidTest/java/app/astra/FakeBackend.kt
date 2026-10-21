package app.astra

import app.astra.core.Story
import app.astra.core.StoryRules
import app.astra.data.AskBackend
import app.astra.data.AskResult

/** Stands in for the AI gateway in emulator tests: deterministic, records what the app sent. */
class FakeBackend : AskBackend {
    override val available = true
    var factsSyncs = 0
    var lastFactsJson: String? = null
    var lastStory: Story? = null
    val questions = mutableListOf<Pair<String, List<String>>>()

    override suspend fun syncFacts(factsJson: String, day: String) { factsSyncs++; lastFactsJson = factsJson }
    override suspend fun syncStory(story: Story) { lastStory = story }
    override suspend fun ask(question: String, storyIds: List<String>): AskResult {
        questions += question to storyIds
        val used = storyIds.filter { id -> StoryRules.items(lastStory ?: Story()).any { it.id == id } }
        return AskResult(
            "Your chart shows you are in the Venus mahadasha. In Vedic tradition this period is associated with relationships and comfort. " +
                "The chart can't tell you when or whether a particular event will happen. Something to reflect on: what would make this month feel steady?",
            used, 14, "fake", "interp-test", "ok")
    }
    override suspend fun todayLine() = AskResult("A calm day to take one small step on what matters to you.", emptyList(), 0, "fake", "interp-test", "ok")
}
