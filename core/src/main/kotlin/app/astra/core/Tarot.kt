// ASTRA core — tarot (PRD F-09, F-10). The deck, the draw and the meanings are deterministic code and data;
// the AI only interprets a draw that has already been saved. Card meanings are tarot's, never Vedic astrology's.
package app.astra.core

import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.Random

enum class Arcana { MAJOR, MINOR }

enum class Suit(val title: String, val element: String, val theme: String) {
    WANDS("Wands", "fire", "drive, ideas and work"),
    CUPS("Cups", "water", "feelings and relationships"),
    SWORDS("Swords", "air", "thoughts, decisions and conflict"),
    PENTACLES("Pentacles", "earth", "money, body and practical life")
}

data class TarotCard(
    /** Stable id: maj00..maj21, wands01..wands14 (11 Page, 12 Knight, 13 Queen, 14 King). */
    val id: String,
    val name: String,
    val arcana: Arcana,
    val suit: Suit?,
    /** 0-21 for majors, 1-14 for minors. */
    val number: Int,
    val upright: String,
    val reversed: String
)

enum class Spread(val title: String, val positions: List<String>) {
    ONE("One card", listOf("Your card")),
    THREE("Three cards", listOf("What's behind it", "Where you are", "What can help")),
    DAILY("Today's card", listOf("Today"))
}

data class DrawnCard(val cardId: String, val reversed: Boolean, val position: String) {
    val card: TarotCard get() = Tarot.card(cardId)
    val orientation: String get() = if (reversed) "reversed" else "upright"
    val meaning: String get() = if (reversed) card.reversed else card.upright
}

/** A saved draw. Immutable, and saved before any interpretation so the AI can't change the cards. */
data class TarotDraw(
    val id: String,
    val spread: Spread,
    val cards: List<DrawnCard>,
    val deckVersion: String,
    val drawnAt: Instant,
    val question: String = "",
    /** Calendar day for the daily card. */
    val day: LocalDate? = null
)

object Tarot {
    const val DECK_VERSION = "astra-deck-1"
    /** Chance a card comes up reversed. */
    const val REVERSED_CHANCE = 0.5

    private val MAJORS = listOf(
        "The Fool" to ("new beginnings, trust, a leap into the unknown" to "hesitation, holding back, a leap taken without looking"),
        "The Magician" to ("skill, focus, using what you already have" to "scattered energy, untapped talent, mixed motives"),
        "The High Priestess" to ("intuition, inner knowing, quiet patience" to "ignoring your instincts, noise drowning out the inner voice"),
        "The Empress" to ("nurture, abundance, creativity and care" to "neglecting yourself, creative block, over-giving"),
        "The Emperor" to ("structure, steadiness, responsibility" to "rigidity, control, or a lack of structure"),
        "The Hierophant" to ("tradition, teachers, shared values" to "questioning convention, finding your own way"),
        "The Lovers" to ("connection, values, a heartfelt choice" to "inner conflict, values out of step, a choice postponed"),
        "The Chariot" to ("determination, direction, moving forward" to "pulling in two directions, losing the reins"),
        "Strength" to ("gentle courage, patience, inner steadiness" to "self-doubt, impatience, strength turned inward"),
        "The Hermit" to ("reflection, solitude, seeking your own answers" to "isolation, or avoiding needed time alone"),
        "Wheel of Fortune" to ("cycles, change, a turning point" to "resisting change, feeling stuck in a cycle"),
        "Justice" to ("fairness, honesty, cause and effect" to "imbalance, avoiding accountability"),
        "The Hanged Man" to ("pause, surrender, seeing things differently" to "stalling, resisting a needed pause"),
        "Death" to ("endings that make room, transition, clearing space" to "clinging to what has ended, slow change"),
        "Temperance" to ("balance, moderation, blending" to "excess, impatience, things out of balance"),
        "The Devil" to ("attachments, habits, what holds you" to "loosening a hold, reclaiming choice"),
        "The Tower" to ("sudden change, revelation, structures shifting" to "change resisted, an inner shake-up"),
        "The Star" to ("hope, healing, renewed faith" to "discouragement, disconnection from hope"),
        "The Moon" to ("uncertainty, dreams, the unclear" to "confusion lifting, fears easing"),
        "The Sun" to ("joy, warmth, clarity and success" to "joy dimmed, clarity delayed"),
        "Judgement" to ("awakening, reckoning, an inner calling" to "self-criticism, ignoring a calling"),
        "The World" to ("completion, wholeness, a cycle fulfilled" to "loose ends, seeking closure")
    )

    private val RANKS = listOf("Ace", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Page", "Knight", "Queen", "King")

    // Upright / reversed, by suit and rank (1..14).
    private val MINOR: Map<Suit, List<Pair<String, String>>> = mapOf(
        Suit.WANDS to listOf(
            "a spark of inspiration, a fresh start in work" to "delays, a spark that needs tending",
            "planning, looking ahead, choosing a path" to "fear of the unknown, plans on hold",
            "expansion, progress, ships coming in" to "obstacles to growth, frustration with pace",
            "celebration, homecoming, stability" to "unsettled foundations, a celebration postponed",
            "competition, friction, clashing ideas" to "avoiding conflict, tension easing",
            "recognition, confidence, a win" to "doubt, waiting for recognition",
            "standing your ground, perseverance" to "feeling overwhelmed, giving ground",
            "speed, movement, news arriving" to "waiting, energy scattered",
            "resilience, the last stretch, holding on" to "fatigue, guarding too closely",
            "heavy responsibility, carrying a lot" to "putting a burden down, delegating",
            "curiosity, enthusiasm, exploring" to "restlessness, ideas without follow-through",
            "bold action, adventure, momentum" to "haste, impatience",
            "warmth, confidence, magnetism" to "self-doubt, spreading yourself thin",
            "vision, leadership, big-picture thinking" to "high expectations, impatience with others"
        ),
        Suit.CUPS to listOf(
            "new feelings, openness, emotional beginnings" to "feelings held in, emotional emptiness",
            "partnership, mutual attraction, harmony" to "imbalance in a bond, misunderstanding",
            "friendship, community, shared joy" to "overindulgence, time away from friends",
            "contemplation, apathy, reconsidering" to "renewed interest, saying yes again",
            "loss, regret, focusing on what's gone" to "acceptance, moving on, seeing what remains",
            "nostalgia, memories, innocence" to "living in the past, ready to move forward",
            "choices, wishful thinking, options" to "clarity, choosing one path",
            "walking away, seeking deeper meaning" to "fear of leaving, drifting",
            "contentment, wishes fulfilled, satisfaction" to "inner dissatisfaction, wanting more",
            "harmony at home, emotional fulfilment" to "strain at home, values misaligned",
            "creative ideas, intuitive messages, tenderness" to "emotional immaturity, creative block",
            "romance, following the heart, charm" to "moodiness, unrealistic expectations",
            "compassion, emotional security, care" to "insecurity, emotions running high",
            "emotional balance, calm, diplomacy" to "emotional distance, holding feelings back"
        ),
        Suit.SWORDS to listOf(
            "clarity, a breakthrough, the truth" to "confusion, clouded judgement",
            "a difficult choice, stalemate" to "indecision lifting, information arriving",
            "heartache, painful truth, grief" to "healing, releasing pain",
            "rest, recovery, retreat" to "restlessness, returning after rest",
            "conflict, winning at a cost" to "reconciliation, making amends",
            "transition, leaving rough waters" to "unfinished business, resisting change",
            "strategy, going it alone" to "coming clean, rethinking an approach",
            "feeling trapped, self-imposed limits" to "release, new perspective",
            "worry, sleepless nights, anxiety" to "hope returning, fears loosening",
            "an ending, hitting bottom, release" to "recovery, rising again",
            "curiosity, new ideas, alertness" to "haste in speech, scattered thinking",
            "ambition, fast action, drive" to "rushing, impulsiveness",
            "clear thinking, independence, honesty" to "coldness, harsh judgement",
            "intellect, authority, truth" to "misused power, rigid thinking"
        ),
        Suit.PENTACLES to listOf(
            "a new opportunity, prosperity, a solid start" to "a missed chance, poor planning",
            "balance, juggling priorities, adaptability" to "overcommitment, things slipping",
            "teamwork, skill, building together" to "lack of teamwork, mediocre effort",
            "security, saving, holding on" to "letting go, greed or overspending",
            "hardship, feeling left out" to "recovery, help arriving",
            "generosity, giving and receiving" to "strings attached, imbalance in giving",
            "patience, long-term view, investment" to "impatience, effort without reward",
            "craft, diligence, skill-building" to "perfectionism, lack of focus",
            "independence, self-sufficiency, comfort" to "overworking, financial dependence",
            "legacy, family wealth, long-term security" to "family money matters, instability",
            "learning, ambition, a new venture" to "procrastination, learning stalled",
            "hard work, routine, reliability" to "boredom, feeling stuck",
            "practicality, nurturing, security" to "work-home imbalance, self-care neglected",
            "abundance, security, discipline" to "stubbornness, over-focus on money"
        )
    )

    val deck: List<TarotCard> = MAJORS.mapIndexed { i, (name, m) ->
        TarotCard("maj" + "%02d".format(i), name, Arcana.MAJOR, null, i, m.first, m.second)
    } + Suit.entries.flatMap { s ->
        MINOR.getValue(s).mapIndexed { i, (up, rev) ->
            val n = i + 1
            TarotCard(s.name.lowercase() + "%02d".format(n), "${RANKS[i]} of ${s.title}", Arcana.MINOR, s, n, up, rev)
        }
    }

    private val byId = deck.associateBy { it.id }
    fun card(id: String): TarotCard = byId[id] ?: throw IllegalArgumentException("Unknown card $id")
    fun cardOrNull(id: String): TarotCard? = byId[id]

    private val secure = SecureRandom()

    /** Fisher-Yates shuffle of the whole deck, then the top cards, each upright or reversed. */
    fun draw(spread: Spread, question: String = "", at: Instant = Instant.now(), rng: Random = secure, day: LocalDate? = null): TarotDraw {
        val order = deck.indices.toMutableList()
        for (i in order.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = order[i]; order[i] = order[j]; order[j] = t
        }
        val cards = spread.positions.mapIndexed { k, pos -> DrawnCard(deck[order[k]].id, rng.nextDouble() < REVERSED_CHANCE, pos) }
        return TarotDraw(StoryRules.newId(), spread, cards, DECK_VERSION, at, StoryRules.clip(question), day)
    }

    /** The whole deck, securely shuffled, each card upright or reversed: what the fan in the reading room shows. */
    fun shuffle(rng: Random = secure): List<DrawnCard> {
        val order = deck.indices.toMutableList()
        for (i in order.size - 1 downTo 1) { val j = rng.nextInt(i + 1); val t = order[i]; order[i] = order[j]; order[j] = t }
        return order.map { DrawnCard(deck[it].id, rng.nextDouble() < REVERSED_CHANCE, "") }
    }

    /** The cards at the fan positions the user tapped, in tap order, placed into the spread's positions. */
    fun fromPicks(shuffled: List<DrawnCard>, picks: List<Int>, spread: Spread, question: String = "", at: Instant = Instant.now()): TarotDraw {
        require(picks.size == spread.positions.size && picks.toSet().size == picks.size && picks.all { it in shuffled.indices }) { "Pick ${spread.positions.size} different cards" }
        val cards = picks.mapIndexed { k, p -> shuffled[p].copy(position = spread.positions[k]) }
        return TarotDraw(StoryRules.newId(), spread, cards, DECK_VERSION, at, StoryRules.clip(question))
    }

    /** One card per calendar day. Returns the saved card for [day] if there is one. */
    fun daily(existing: TarotDraw?, day: LocalDate, at: Instant = Instant.now(), rng: Random = secure): TarotDraw =
        if (existing != null && existing.spread == Spread.DAILY && existing.day == day) existing
        else draw(Spread.DAILY, "", at, rng, day)

    /** What the AI is given for a draw (part of the DATA block). */
    fun facts(d: TarotDraw): Map<String, Any?> = linkedMapOf(
        "deck_version" to d.deckVersion,
        "spread" to d.spread.title,
        "question" to d.question,
        "cards" to d.cards.map { c ->
            linkedMapOf("position" to c.position, "card" to c.card.name, "orientation" to c.orientation, "keywords" to c.meaning,
                "suit" to (c.card.suit?.title ?: "Major Arcana"))
        }
    )

    /** Offline summary shown before (or without) any AI interpretation. */
    fun summary(d: TarotDraw): String = d.cards.joinToString("\n") { c ->
        "${c.position}: ${c.card.name}${if (c.reversed) " (reversed)" else ""} — in tarot, ${c.meaning}."
    }

    fun allText(): List<String> = deck.flatMap { listOf(it.name, it.upright, it.reversed) } + Spread.entries.flatMap { it.positions + it.title }
}

object DrawCodec {
    fun toMap(d: TarotDraw): Map<String, Any?> = mapOf(
        "id" to d.id, "spread" to d.spread.name, "deck" to d.deckVersion, "at" to d.drawnAt.toString(), "q" to d.question,
        "day" to d.day?.toString(),
        "cards" to d.cards.map { mapOf("id" to it.cardId, "rev" to it.reversed, "pos" to it.position) }
    )

    @Suppress("UNCHECKED_CAST")
    fun fromMap(m: Map<String, Any?>): TarotDraw? = try {
        TarotDraw(
            m["id"] as String, Spread.valueOf(m["spread"] as String),
            (m["cards"] as List<Map<String, Any?>>).map { DrawnCard(it["id"] as String, it["rev"] == true, it["pos"] as String).also { c -> Tarot.card(c.cardId) } },
            m["deck"] as String, Instant.parse(m["at"] as String), (m["q"] as String?) ?: "", (m["day"] as String?)?.let { LocalDate.parse(it) }
        )
    } catch (e: Exception) { null }

    fun encode(d: TarotDraw): String = Json.write(toMap(d))
    @Suppress("UNCHECKED_CAST")
    fun decode(s: String): TarotDraw? = try { fromMap(Json.parse(s) as Map<String, Any?>) } catch (e: Exception) { null }
}
