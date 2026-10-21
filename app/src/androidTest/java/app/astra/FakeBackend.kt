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

    val draws = mutableListOf<app.astra.core.TarotDraw>()
    val readings = mutableListOf<Pair<String, String>>() // mode to drawId
    var crossLeft = 2
    override suspend fun syncDraw(draw: app.astra.core.TarotDraw) { draws += draw }
    override suspend fun tarot(drawId: String, question: String, storyIds: List<String>): AskResult {
        readings += "tarot" to drawId
        val d = draws.last { it.id == drawId }
        return AskResult("In tarot, " + d.cards.joinToString(" ") { "${it.card.name} in \"${it.position}\" speaks of ${it.meaning}." } +
            " Something to reflect on: what feels most true for you right now?", emptyList(), 13, "fake", "interp-test", "ok", "tarot")
    }
    override suspend fun cross(drawId: String, question: String, storyIds: List<String>): AskResult {
        readings += "cross" to drawId
        val d = draws.last { it.id == drawId }
        crossLeft--
        return AskResult("Your chart: You are in the Venus mahadasha, and the period tone is mixed.\n" +
            "Your cards: In tarot, " + d.cards.joinToString(", ") { it.card.name } + ".\n" +
            "Where they meet: Both point to patient, steady effort.\n" +
            "Where they differ: The chart describes a long period; the cards speak to how you feel now.\n" +
            "Questions to sit with: What would steady effort look like this month?", emptyList(), crossLeft, "fake", "interp-test", "ok", "cross")
    }
}
