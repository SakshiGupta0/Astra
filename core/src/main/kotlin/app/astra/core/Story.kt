// ASTRA core — My Story: the user's central, visible, editable memory (PRD section 3, F-06).
package app.astra.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class LifeStage(val label: String) {
    STUDENT("Student"), EARLY_CAREER("Starting my career"), WORKING("Working professional"), BUSINESS("Running a business"),
    HOMEMAKER("Homemaker"), BETWEEN("Between things"), RETIRED("Retired")
}

enum class LifeArea(val label: String, val keywords: List<String>) {
    CAREER("Career", listOf("career", "job", "work", "office", "promotion", "boss", "offer", "interview", "naukri", "business", "startup", "salary", "switch", "kaam")),
    RELATIONSHIPS("Marriage & relationships", listOf("marriage", "married", "marry", "shaadi", "shadi", "rishta", "match", "partner", "relationship", "boyfriend", "girlfriend", "husband", "wife", "love", "spouse", "divorce", "शादी")),
    MONEY("Money", listOf("money", "paisa", "finance", "loan", "debt", "invest", "savings", "property", "house", "flat", "rich")),
    FAMILY("Family", listOf("family", "parents", "mother", "father", "mom", "dad", "papa", "mummy", "brother", "sister", "children", "kids", "son", "daughter", "ghar", "in-laws")),
    STUDIES("Studies", listOf("study", "studies", "exam", "college", "admission", "course", "padhai", "result", "degree", "abroad")),
    HEALTH("Health & wellbeing", listOf("health", "sehat", "stress", "sleep", "anxiety", "tired", "fitness", "wellbeing")),
    SELF("Self & purpose", listOf("purpose", "myself", "confidence", "spiritual", "direction", "meaning", "growth"));

    companion object {
        fun detect(text: String): Set<LifeArea> {
            val t = " " + text.lowercase() + " "
            return entries.filter { a -> a.keywords.any { k -> t.contains(k) } }.toSet()
        }
    }
}

data class StoryAbout(
    val lifeStage: LifeStage? = null,
    val areas: Set<LifeArea> = emptySet(),
    /** "What's going on right now?" in the user's own words. */
    val nowLine: String = ""
)

enum class ThreadStatus { OPEN, RESOLVED }

data class StoryThread(
    val id: String,
    val title: String,
    val note: String = "",
    val area: LifeArea? = null,
    val status: ThreadStatus = ThreadStatus.OPEN,
    val createdOn: LocalDate,
    val outcome: String = "",
    /** When ASTRA next asks how it's going (null = createdOn + the story's check-in gap). */
    val nextCheckin: LocalDate? = null,
    /** A check-in has been asked and not answered yet. */
    val checkinPending: Boolean = false,
    /** The user's latest one-line update while the thread is still open. */
    val lastUpdate: String = "",
    val lastCheckin: LocalDate? = null
)

enum class CheckinAnswer { RESOLVED, STILL_OPEN, DISMISSED }

data class LifeEvent(val id: String, val title: String, val month: YearMonth, val note: String = "")

data class ReadingDigest(val id: String, val date: LocalDate, val text: String)

data class StoryConsent(val granted: Boolean, val copyVersion: String, val at: Instant, val offerPoint: String)

data class Story(
    val consent: StoryConsent? = null,
    val about: StoryAbout = StoryAbout(),
    val threads: List<StoryThread> = emptyList(),
    val events: List<LifeEvent> = emptyList(),
    val digests: List<ReadingDigest> = emptyList(),
    /** How many times the offer has been shown (onboarding = 1, after first saved reading = 2). */
    val offersShown: Int = 0,
    /** Days between check-ins on an open thread; 0 turns check-ins off. */
    val checkinDays: Int = StoryRules.DEFAULT_CHECKIN_DAYS,
    /** Last day a check-in was started (at most one a day). */
    val checkinRolledOn: LocalDate? = null,
    /** Answer to "ASTRA already knew my situation" (1 disagree .. 5 agree), asked once after two weeks. */
    val surveyAnswer: Int? = null,
    val surveyAskedOn: LocalDate? = null
) {
    val enabled: Boolean get() = consent?.granted == true
    val isEmpty: Boolean get() = about == StoryAbout() && threads.isEmpty() && events.isEmpty() && digests.isEmpty()
}

/** One item the AI may be given, with a stable id so the answer can show "From your story". */
data class StoryItem(val id: String, val kind: String, val text: String)

object StoryRules {
    const val CONSENT_COPY_VERSION = "story-consent-1"
    const val MAX_TEXT = 200
    const val MAX_THREADS = 30
    const val MAX_EVENTS = 100
    const val MAX_DIGESTS = 50
    const val MAX_ITEMS_PER_ANSWER = 6
    const val DEFAULT_CHECKIN_DAYS = 21
    val CHECKIN_CHOICES = listOf(7, 14, 21, 30, 0)

    fun newId(): String = UUID.randomUUID().toString().substring(0, 12)
    fun clip(s: String) = s.trim().replace(Regex("\\s+"), " ").take(MAX_TEXT)

    fun grant(story: Story, offerPoint: String, at: Instant = Instant.now()) =
        story.copy(consent = StoryConsent(true, CONSENT_COPY_VERSION, at, offerPoint))

    /** Turning My Story off stops all use immediately. Items are kept until the user deletes them. */
    fun revoke(story: Story, at: Instant = Instant.now()) =
        story.copy(consent = story.consent?.copy(granted = false, at = at) ?: StoryConsent(false, CONSENT_COPY_VERSION, at, "settings"))

    fun deleteAll(story: Story) = Story(consent = story.consent?.copy(granted = false), offersShown = story.offersShown)

    fun addThread(story: Story, title: String, note: String = "", area: LifeArea? = null, today: LocalDate): Story {
        val t = StoryThread(newId(), clip(title), clip(note), area ?: LifeArea.detect("$title $note").firstOrNull(), ThreadStatus.OPEN, today)
        require(t.title.isNotEmpty()) { "Title is empty" }
        return story.copy(threads = (listOf(t) + story.threads).take(MAX_THREADS))
    }

    fun updateThread(story: Story, t: StoryThread) = story.copy(threads = story.threads.map { if (it.id == t.id) t.copy(title = clip(t.title), note = clip(t.note), outcome = clip(t.outcome)) else it })
    fun removeThread(story: Story, id: String) = story.copy(threads = story.threads.filterNot { it.id == id })

    fun addEvent(story: Story, title: String, month: YearMonth, note: String = "") =
        story.copy(events = (story.events + LifeEvent(newId(), clip(title), month, clip(note))).sortedByDescending { it.month }.take(MAX_EVENTS))
    fun removeEvent(story: Story, id: String) = story.copy(events = story.events.filterNot { it.id == id })

    fun addDigest(story: Story, text: String, date: LocalDate) =
        story.copy(digests = (listOf(ReadingDigest(newId(), date, clip(text))) + story.digests).take(MAX_DIGESTS))
    fun removeDigest(story: Story, id: String) = story.copy(digests = story.digests.filterNot { it.id == id })

    /** Everything in the story as AI-ready items (only used when consent is on). */
    fun items(story: Story): List<StoryItem> {
        if (!story.enabled) return emptyList()
        val out = ArrayList<StoryItem>()
        val a = story.about
        if (a.lifeStage != null) out += StoryItem("about.stage", "about", "Life stage: ${a.lifeStage.label}")
        if (a.areas.isNotEmpty()) out += StoryItem("about.areas", "about", "Cares most about: ${a.areas.joinToString { it.label }}")
        if (a.nowLine.isNotBlank()) out += StoryItem("about.now", "about", "Going on right now: ${a.nowLine}")
        story.threads.forEach { t ->
            out += StoryItem("thread.${t.id}", "thread",
                "${if (t.status == ThreadStatus.OPEN) "Open question" else "Resolved"} (since ${t.createdOn}): ${t.title}" +
                    (if (t.note.isNotBlank()) " — ${t.note}" else "") + (if (t.outcome.isNotBlank()) " Outcome: ${t.outcome}" else "") +
                    (if (t.status == ThreadStatus.OPEN && t.lastUpdate.isNotBlank()) " Latest update (${t.lastCheckin ?: ""}): ${t.lastUpdate}" else ""))
        }
        story.events.forEach { e -> out += StoryItem("event.${e.id}", "event", "Life event (${e.month}): ${e.title}" + if (e.note.isNotBlank()) " — ${e.note}" else "") }
        story.digests.take(10).forEach { d -> out += StoryItem("digest.${d.id}", "digest", "Earlier reading (${d.date}): ${d.text}") }
        return out
    }

    /**
     * Picks the story items relevant to a question: life stage and "right now" always, then threads and events
     * in the life areas the question touches, then the most recent open threads. Never more than [MAX_ITEMS_PER_ANSWER].
     */
    fun select(story: Story, question: String, max: Int = MAX_ITEMS_PER_ANSWER): List<StoryItem> {
        if (!story.enabled) return emptyList()
        val all = items(story).associateBy { it.id }
        val qAreas = LifeArea.detect(question)
        val picked = LinkedHashSet<String>()
        listOf("about.stage", "about.now").forEach { if (it in all) picked += it }
        val openThreads = story.threads.filter { it.status == ThreadStatus.OPEN }
        openThreads.filter { it.area != null && it.area in qAreas }.forEach { picked += "thread.${it.id}" }
        story.threads.filter { it.status == ThreadStatus.RESOLVED && it.area != null && it.area in qAreas }.forEach { picked += "thread.${it.id}" }
        story.events.filter { LifeArea.detect(it.title + " " + it.note).any { a -> a in qAreas } }.forEach { picked += "event.${it.id}" }
        if ("about.areas" in all && qAreas.isEmpty()) picked += "about.areas"
        openThreads.forEach { picked += "thread.${it.id}" }
        return picked.mapNotNull { all[it] }.take(max)
    }

    fun checkinDue(story: Story, t: StoryThread): LocalDate = t.nextCheckin ?: t.createdOn.plusDays(story.checkinDays.toLong().coerceAtLeast(1))

    /**
     * Once a day: if an open thread's check-in is due, mark it pending and schedule the next one a full gap later.
     * Returns the updated story and the thread to ask about (to notify), or null. At most one thread a day,
     * so the user never gets more than one check-in a day and never more than one per thread per gap.
     */
    fun rollCheckins(story: Story, today: LocalDate): Pair<Story, StoryThread?> {
        if (!story.enabled || story.checkinDays <= 0 || story.checkinRolledOn == today) return story to null
        val due = story.threads.filter { it.status == ThreadStatus.OPEN && !it.checkinPending && !checkinDue(story, it).isAfter(today) }
            .minByOrNull { checkinDue(story, it) } ?: return story to null
        val rolled = due.copy(checkinPending = true, nextCheckin = today.plusDays(story.checkinDays.toLong()))
        return story.copy(threads = story.threads.map { if (it.id == due.id) rolled else it }, checkinRolledOn = today) to rolled
    }

    /** Threads waiting for an answer, shown on Today. Only while My Story is on. */
    fun pendingCheckins(story: Story): List<StoryThread> =
        if (!story.enabled) emptyList() else story.threads.filter { it.status == ThreadStatus.OPEN && it.checkinPending }

    fun answerCheckin(story: Story, id: String, answer: CheckinAnswer, text: String, today: LocalDate): Story {
        val note = clip(text)
        return story.copy(threads = story.threads.map { t ->
            if (t.id != id) t else when (answer) {
                CheckinAnswer.RESOLVED -> t.copy(status = ThreadStatus.RESOLVED, outcome = note.ifBlank { t.outcome }, checkinPending = false, lastCheckin = today)
                CheckinAnswer.STILL_OPEN -> t.copy(lastUpdate = note.ifBlank { t.lastUpdate }, checkinPending = false, lastCheckin = today,
                    nextCheckin = today.plusDays(story.checkinDays.toLong().coerceAtLeast(1)))
                CheckinAnswer.DISMISSED -> t.copy(checkinPending = false, nextCheckin = today.plusDays(story.checkinDays.toLong().coerceAtLeast(1)))
            }
        })
    }

    /** "Three weeks ago you added …" — the check-in question. */
    fun checkinQuestion(t: StoryThread, today: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(t.lastCheckin ?: t.createdOn, today)
        val ago = when {
            days < 2 -> "Recently"
            days < 14 -> "$days days ago"
            days < 60 -> "${days / 7} weeks ago"
            else -> "${days / 30} months ago"
        }
        return if (t.lastCheckin == null) "$ago you added \"${t.title}\" to your story. What's happened since?"
        else "$ago you said \"${t.title}\" was still open. Any update?"
    }

    /** The repeat-story survey is asked once, two weeks after My Story was turned on. */
    fun surveyDue(story: Story, today: LocalDate): Boolean {
        val since = story.consent?.takeIf { it.granted }?.at ?: return false
        if (story.surveyAskedOn != null || story.surveyAnswer != null) return false
        return java.time.temporal.ChronoUnit.DAYS.between(since.atZone(java.time.ZoneOffset.UTC).toLocalDate(), today) >= 14
    }

    /** Shareable summary for a human astrologer, with only the parts the user picks. */
    fun export(
        story: Story, profile: Profile, chart: ChartSnapshot, at: Instant,
        includeBirth: Boolean = true, includeChart: Boolean = true, includeDasha: Boolean = true,
        includeThreads: Boolean = true, includeEvents: Boolean = true
    ): String {
        val sb = StringBuilder()
        sb.appendLine("My details (from the ASTRA app)")
        if (includeBirth) {
            sb.appendLine()
            sb.appendLine("Born: ${profile.birthDate} " + when (profile.timeStatus) {
                BirthTimeStatus.KNOWN -> "at ${profile.birthTime}"
                BirthTimeStatus.APPROXIMATE -> "around ${profile.birthTime} (±${profile.windowMinutes} min)"
                BirthTimeStatus.UNKNOWN -> "(time not known)"
            })
            sb.appendLine("Place: ${profile.birthPlace.label} (${"%.4f".format(profile.birthPlace.latitude)}, ${"%.4f".format(profile.birthPlace.longitude)})")
        }
        if (includeChart) {
            sb.appendLine()
            sb.appendLine("Chart (Lahiri, whole-sign):")
            chart.lagnaRashi?.let { sb.appendLine("Lagna: ${it.sanskrit}") }
            sb.appendLine("Moon: ${chart.moon.rashi.sanskrit}, ${chart.moon.nakshatra.title} pada ${chart.moon.pada}")
            chart.placements.forEach { sb.appendLine("${it.graha.english}: ${it.rashi.sanskrit} ${"%.1f".format(it.degreeInSign)}°${if (it.retrograde && it.graha != Graha.RAHU && it.graha != Graha.KETU) " (R)" else ""}") }
        }
        if (includeDasha) chart.dasha.at(at)?.let { d ->
            sb.appendLine()
            sb.appendLine("Current dasha: ${d.maha.lord.english} / ${d.antar.lord.english} / ${d.pratyantar.lord.english}")
            sb.appendLine("${d.antar.lord.english} sub-period: ${d.antar.start.toString().take(10)} to ${d.antar.end.toString().take(10)}")
        }
        if (includeThreads && story.threads.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("What's on my mind:")
            story.threads.forEach { sb.appendLine("- ${it.title}${if (it.note.isNotBlank()) ": ${it.note}" else ""}${if (it.status == ThreadStatus.RESOLVED) " (resolved${if (it.outcome.isNotBlank()) ": ${it.outcome}" else ""})" else ""}") }
        }
        if (includeEvents && story.events.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Life events:")
            story.events.forEach { sb.appendLine("- ${it.month}: ${it.title}") }
        }
        return sb.toString().trimEnd()
    }
}

object StoryCodec {
    fun encode(s: Story): String = Json.write(mapOf(
        "v" to 1,
        "consent" to s.consent?.let { mapOf("granted" to it.granted, "copy" to it.copyVersion, "at" to it.at.toString(), "offer" to it.offerPoint) },
        "about" to mapOf("stage" to s.about.lifeStage?.name, "areas" to s.about.areas.map { it.name }, "now" to s.about.nowLine),
        "threads" to s.threads.map { mapOf("id" to it.id, "title" to it.title, "note" to it.note, "area" to it.area?.name, "status" to it.status.name, "created" to it.createdOn.toString(), "outcome" to it.outcome,
            "next" to it.nextCheckin?.toString(), "pending" to it.checkinPending, "update" to it.lastUpdate, "lastCheck" to it.lastCheckin?.toString()) },
        "events" to s.events.map { mapOf("id" to it.id, "title" to it.title, "month" to it.month.toString(), "note" to it.note) },
        "digests" to s.digests.map { mapOf("id" to it.id, "date" to it.date.toString(), "text" to it.text) },
        "offers" to s.offersShown,
        "checkinDays" to s.checkinDays, "rolled" to s.checkinRolledOn?.toString(),
        "survey" to s.surveyAnswer, "surveyAsked" to s.surveyAskedOn?.toString()
    ))

    @Suppress("UNCHECKED_CAST")
    fun decode(text: String): Story? = try {
        val m = Json.parse(text) as Map<String, Any?>
        fun <E : Enum<E>> enumOrNull(values: Array<E>, n: Any?) = values.firstOrNull { it.name == n }
        val c = m["consent"] as Map<String, Any?>?
        val a = (m["about"] as Map<String, Any?>?) ?: emptyMap()
        Story(
            consent = c?.let { StoryConsent(it["granted"] == true, it["copy"] as String, Instant.parse(it["at"] as String), it["offer"] as String) },
            about = StoryAbout(
                enumOrNull(LifeStage.values(), a["stage"]),
                ((a["areas"] as List<Any?>?) ?: emptyList()).mapNotNull { enumOrNull(LifeArea.values(), it) }.toSet(),
                (a["now"] as String?) ?: ""
            ),
            threads = ((m["threads"] as List<Map<String, Any?>>?) ?: emptyList()).map {
                StoryThread(it["id"] as String, it["title"] as String, (it["note"] as String?) ?: "", enumOrNull(LifeArea.values(), it["area"]),
                    enumOrNull(ThreadStatus.values(), it["status"]) ?: ThreadStatus.OPEN, LocalDate.parse(it["created"] as String), (it["outcome"] as String?) ?: "",
                    (it["next"] as String?)?.let { d -> LocalDate.parse(d) }, it["pending"] == true, (it["update"] as String?) ?: "",
                    (it["lastCheck"] as String?)?.let { d -> LocalDate.parse(d) })
            },
            events = ((m["events"] as List<Map<String, Any?>>?) ?: emptyList()).map {
                LifeEvent(it["id"] as String, it["title"] as String, YearMonth.parse(it["month"] as String), (it["note"] as String?) ?: "")
            },
            digests = ((m["digests"] as List<Map<String, Any?>>?) ?: emptyList()).map {
                ReadingDigest(it["id"] as String, LocalDate.parse(it["date"] as String), it["text"] as String)
            },
            offersShown = ((m["offers"] as Number?) ?: 0).toInt(),
            checkinDays = ((m["checkinDays"] as Number?) ?: StoryRules.DEFAULT_CHECKIN_DAYS).toInt(),
            checkinRolledOn = (m["rolled"] as String?)?.let { LocalDate.parse(it) },
            surveyAnswer = (m["survey"] as Number?)?.toInt(),
            surveyAskedOn = (m["surveyAsked"] as String?)?.let { LocalDate.parse(it) }
        )
    } catch (e: Exception) { null }
}
