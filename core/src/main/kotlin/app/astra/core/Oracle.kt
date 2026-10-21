// ASTRA core — oracle decks (ASTRA's own, separate from tarot): Yes / No, Guidance, Vibe and Nakshatra.
// Single-card draws, deterministic content, never predictive or fear-based (covered by the copy test).
package app.astra.core

import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.Random

enum class OracleDeck(val title: String, val blurb: String) {
    YES_NO("Yes / No", "A quick answer card for a simple question."),
    GUIDANCE("Guidance", "A message to carry with you today."),
    VIBE("Vibe", "The energy of the moment: a colour, an element, a word."),
    NAKSHATRA("Nakshatra", "One of the 27 lunar mansions of Vedic astrology.")
}

enum class Leaning { YES, NO, MAYBE }
enum class Element(val label: String) { FIRE("Fire"), WATER("Water"), AIR("Air"), EARTH("Earth"), ETHER("Ether") }

data class OracleCard(
    val id: String,
    val deck: OracleDeck,
    /** The big word on the card: "YES", "PATIENCE", "RADIANT", "ROHINI". */
    val title: String,
    /** One line under it. */
    val line: String,
    val leaning: Leaning? = null,
    val element: Element? = null,
    /** Two colours for the vibe card's field (ARGB). */
    val colors: List<Long> = emptyList(),
    /** Nakshatra details. */
    val nakshatra: Nakshatra? = null,
    val meaning: String = "",
    val symbol: String = "",
    val deity: String = ""
)

data class OracleDraw(val id: String, val deck: OracleDeck, val cardId: String, val drawnAt: Instant, val question: String = "", val day: LocalDate? = null) {
    val card: OracleCard get() = Oracle.card(cardId)
}

object Oracle {
    const val VERSION = "astra-oracle-1"

    private val YES_NO = listOf(
        Triple("YES", "The way is open. Walk it with care.", Leaning.YES),
        Triple("YES, GENTLY", "Go ahead, at a pace that feels kind.", Leaning.YES),
        Triple("TRUST IT", "What you already sense is worth following.", Leaning.YES),
        Triple("BEGIN", "Take the first small step today.", Leaning.YES),
        Triple("NO", "This path asks more than it gives. Choose another.", Leaning.NO),
        Triple("NOT THIS WAY", "The aim may be right; the route isn't.", Leaning.NO),
        Triple("LET GO", "Loosen your grip and see what stays.", Leaning.NO),
        Triple("NOT YET", "Give it one more moon.", Leaning.MAYBE),
        Triple("WAIT", "Stillness first. The answer ripens.", Leaning.MAYBE),
        Triple("ASK AGAIN", "Shape the question more clearly, then ask.", Leaning.MAYBE),
        Triple("LOOK CLOSER", "Something here hasn't shown itself yet.", Leaning.MAYBE),
        Triple("YOUR CHOICE", "Both doors are open. Which one feels like you?", Leaning.MAYBE)
    )

    private val GUIDANCE = listOf(
        "PATIENCE" to "Begin before you feel ready. The path forms under your feet.",
        "TRUST" to "You have walked through harder things than this.",
        "REST" to "A quiet hour now will carry you further than a rushed day.",
        "COURAGE" to "Say the true thing kindly, and let it land.",
        "CLARITY" to "Write it down. What is clear on paper is clear in the heart.",
        "GRATITUDE" to "Name three small things that went right today.",
        "BOUNDARIES" to "A gentle no makes room for a wholehearted yes.",
        "FOCUS" to "Choose one thing and give it your full attention.",
        "SOFTNESS" to "Speak to yourself the way you would to someone you love.",
        "PLAY" to "Do one thing today just because it delights you.",
        "ROOTS" to "Call someone who knew you before all this.",
        "FLOW" to "Stop pushing the river. Notice where it wants to go.",
        "DISCIPLINE" to "Small steady steps build what bursts of effort cannot.",
        "RELEASE" to "Put down one worry you cannot act on today.",
        "CURIOSITY" to "Ask one more question before you decide.",
        "PRESENCE" to "Three slow breaths. Then begin again.",
        "HONESTY" to "Let your words and your actions walk together.",
        "GENEROSITY" to "Give something small without being asked.",
        "BALANCE" to "Rest is part of the work, not a reward for it.",
        "ABUNDANCE" to "Notice what you already have enough of.",
        "RENEWAL" to "Today can be the first page, not the next one.",
        "DEVOTION" to "Show up for what you love, especially on ordinary days.",
        "SURRENDER" to "Do your part fully, then let the rest unfold.",
        "WONDER" to "Look up tonight. You are part of something vast.",
        "STRENGTH" to "Steady is strong. You don't have to be loud.",
        "KINDNESS" to "Someone near you needs a warm word today.",
        "INTUITION" to "Your first quiet feeling knows something. Listen.",
        "SIMPLICITY" to "Remove one thing, and see what breathes.",
        "CONNECTION" to "Reach out first. Someone is waiting to hear from you.",
        "FORGIVENESS" to "Loosen the knot, for your own sake.",
        "CREATIVITY" to "Make something imperfect today.",
        "GROUNDING" to "Feet on the floor. You are here, now.",
        "HOPE" to "The light is closer than it looks from here.",
        "WISDOM" to "Not every question needs an answer today.",
        "PROTECTION" to "Guard your energy as you would something precious.",
        "JOY" to "Let yourself enjoy the happy moment fully.",
        "LEARNING" to "Every misstep carries a lesson. Collect it gently.",
        "SERVICE" to "Ask how you can help, and mean it.",
        "SELF-WORTH" to "You don't need to earn your place. You belong.",
        "TIMING" to "Some seeds need a season. Keep watering.",
        "CHANGE" to "What is ending is making room.",
        "DIRECTION" to "Ask what you'd do if you weren't afraid.",
        "HARMONY" to "Find the note you share with the person you disagree with.",
        "COMPLETION" to "Finish one thing before you start the next."
    )

    private val VIBE = listOf(
        Triple("RADIANT", Element.FIRE, "Speak up and be seen." to listOf(0xFFE8A87C, 0xFFF7D794)),
        Triple("SPARKED", Element.FIRE, "Start the thing that excites you." to listOf(0xFFF08A5D, 0xFFF7D794)),
        Triple("BOLD", Element.FIRE, "Take up a little more space today." to listOf(0xFFC8553D, 0xFFE8A87C)),
        Triple("WARM", Element.FIRE, "Let people feel your care." to listOf(0xFFE8A87C, 0xFFF4C9A8)),
        Triple("FLUID", Element.WATER, "Bend, don't break. Go with the current." to listOf(0xFF5B8DEF, 0xFFA78BFA)),
        Triple("TENDER", Element.WATER, "Feel it fully; it will pass through." to listOf(0xFFA78BFA, 0xFFF5C6E0)),
        Triple("DREAMY", Element.WATER, "Make room for imagination." to listOf(0xFF7C6FD6, 0xFFB8C6FF)),
        Triple("HEALING", Element.WATER, "Be gentle with what is mending." to listOf(0xFF6CC5B0, 0xFFA78BFA)),
        Triple("DEEP", Element.WATER, "Go beneath the surface today." to listOf(0xFF23306B, 0xFF5B8DEF)),
        Triple("CURIOUS", Element.AIR, "Ask, explore, wander a little." to listOf(0xFF9AD0EC, 0xFFF5F3FF)),
        Triple("CLEAR", Element.AIR, "Say it simply. Decide cleanly." to listOf(0xFFB8E1FF, 0xFFF5F3FF)),
        Triple("LIGHT", Element.AIR, "Laugh at something. Let it be easy." to listOf(0xFFF5F3FF, 0xFFFBE7B2)),
        Triple("SOCIAL", Element.AIR, "Connection fuels you today." to listOf(0xFF9AD0EC, 0xFFF7D794)),
        Triple("RESTLESS", Element.AIR, "Move your body before you decide." to listOf(0xFF7FB3D5, 0xFFA78BFA)),
        Triple("STEADY", Element.EARTH, "Slow, sure and reliable wins." to listOf(0xFF8A9A5B, 0xFFC9A45C)),
        Triple("GROUNDED", Element.EARTH, "Feet first. Practical steps." to listOf(0xFF7D6B4E, 0xFFC9A45C)),
        Triple("ABUNDANT", Element.EARTH, "Notice how much is already here." to listOf(0xFF6E8B3D, 0xFFF7D794)),
        Triple("NOURISHED", Element.EARTH, "Eat well, rest well, tend your space." to listOf(0xFF9C7A54, 0xFFF4C9A8)),
        Triple("BUILDING", Element.EARTH, "One brick at a time." to listOf(0xFF8A7B6A, 0xFFC9A45C)),
        Triple("STILL", Element.ETHER, "Silence has something to say." to listOf(0xFF2A1B4E, 0xFFA78BFA)),
        Triple("MYSTIC", Element.ETHER, "Notice signs and synchronicities." to listOf(0xFF602D89, 0xFFF7D794)),
        Triple("EXPANSIVE", Element.ETHER, "Think bigger than usual." to listOf(0xFF3B2A7A, 0xFF9AD0EC)),
        Triple("REFLECTIVE", Element.ETHER, "Look back to see forward." to listOf(0xFF1B1446, 0xFFC4B2FF)),
        Triple("DEVOTED", Element.ETHER, "Give your heart to one practice." to listOf(0xFF602D89, 0xFFF4C9A8))
    )

    /** Nakshatra meaning, symbol, deity and theme. Lords and spans come from the calculation core. */
    private val NAK = listOf(
        listOf("The horse-headed", "a horse's head", "the Ashwini twins", "swift beginnings and healing"),
        listOf("The bearer", "a vessel", "Yama", "carrying, restraint and transformation"),
        listOf("The cutter", "a flame", "Agni", "purification and courage"),
        listOf("The red one", "a chariot", "Brahma", "growth, beauty and nourishment"),
        listOf("The deer's head", "a deer's head", "Soma", "curiosity and gentle seeking"),
        listOf("The moist one", "a teardrop", "Rudra", "storms that clear the air"),
        listOf("Return of the light", "a quiver of arrows", "Aditi", "renewal and coming home"),
        listOf("The nourisher", "a flower", "Brihaspati", "care, nourishment and wisdom"),
        listOf("The entwiner", "a coiled serpent", "the Nagas", "insight and depth"),
        listOf("The great one", "a throne", "the Pitris", "heritage and dignity"),
        listOf("The former red one", "a hammock", "Bhaga", "rest, pleasure and creativity"),
        listOf("The latter red one", "a bed", "Aryaman", "partnership and kindness"),
        listOf("The hand", "an open hand", "Savitar", "skill and craft"),
        listOf("The brilliant", "a bright jewel", "Tvashtar", "design and beauty"),
        listOf("The independent", "a young shoot in the wind", "Vayu", "freedom and flexibility"),
        listOf("The forked branch", "a triumphal arch", "Indra and Agni", "purpose and determination"),
        listOf("The follower of Radha", "a lotus", "Mitra", "friendship and devotion"),
        listOf("The eldest", "an earring", "Indra", "responsibility and protection"),
        listOf("The root", "a bundle of roots", "Nirriti", "getting to the root of things"),
        listOf("Early victory", "a winnowing fan", "Apas", "conviction and renewal"),
        listOf("Later victory", "an elephant's tusk", "the Vishvadevas", "lasting achievement"),
        listOf("The listener", "an ear", "Vishnu", "listening and learning"),
        listOf("The wealthiest", "a drum", "the Vasus", "rhythm and abundance"),
        listOf("A hundred healers", "an empty circle", "Varuna", "healing and solitude"),
        listOf("The former blessed feet", "a two-faced figure", "Aja Ekapada", "intensity and inner fire"),
        listOf("The latter blessed feet", "twin serpents", "Ahirbudhnya", "depth and calm wisdom"),
        listOf("The wealthy", "a fish", "Pushan", "safe journeys and completion")
    )

    val deck: List<OracleCard> =
        YES_NO.mapIndexed { i, (t, l, lean) -> OracleCard("yn%02d".format(i + 1), OracleDeck.YES_NO, t, l, leaning = lean) } +
        GUIDANCE.mapIndexed { i, (t, l) -> OracleCard("gd%02d".format(i + 1), OracleDeck.GUIDANCE, t, l) } +
        VIBE.mapIndexed { i, (t, e, lc) -> OracleCard("vb%02d".format(i + 1), OracleDeck.VIBE, t, lc.first, element = e, colors = lc.second) } +
        Nakshatra.entries.mapIndexed { i, n ->
            val (meaning, symbol, deity, theme) = NAK[i]
            OracleCard("nk%02d".format(i + 1), OracleDeck.NAKSHATRA, n.title.uppercase(), "Tradition links it with $theme.",
                nakshatra = n, meaning = meaning, symbol = symbol, deity = deity)
        }

    private val byId = deck.associateBy { it.id }
    fun card(id: String): OracleCard = byId[id] ?: throw IllegalArgumentException("Unknown oracle card $id")
    fun cards(d: OracleDeck): List<OracleCard> = deck.filter { it.deck == d }

    private val secure = SecureRandom()

    fun draw(d: OracleDeck, question: String = "", at: Instant = Instant.now(), rng: Random = secure, day: LocalDate? = null): OracleDraw {
        val list = cards(d)
        return OracleDraw(StoryRules.newId(), d, list[rng.nextInt(list.size)].id, at, StoryRules.clip(question), day)
    }

    /** One guidance card per calendar day, kept for the day. */
    fun dailyGuidance(existing: OracleDraw?, day: LocalDate, at: Instant = Instant.now(), rng: Random = secure): OracleDraw =
        if (existing != null && existing.deck == OracleDeck.GUIDANCE && existing.day == day) existing
        else draw(OracleDeck.GUIDANCE, "", at, rng, day)

    /** "Mesha 0°00′ – 13°20′" for a nakshatra card. */
    fun span(n: Nakshatra): String {
        val start = n.ordinal * Nakshatra.SPAN; val end = start + Nakshatra.SPAN
        fun f(x: Double): String { val r = Rashi.of(x + 1e-9); val d = x - Rashi.entries.indexOf(r) * 30.0; return "${r.sanskrit} ${d.toInt()}°${"%02d".format(Math.round((d - d.toInt()) * 60).toInt() % 60)}′" }
        return "${f(start)} – ${f(end - 1e-6)}"
    }

    /** Stylised star pattern for a nakshatra card (positions 0..1), stable per card. */
    fun starPattern(n: Nakshatra): List<Pair<Float, Float>> {
        val counts = intArrayOf(3, 3, 6, 5, 3, 1, 4, 3, 6, 6, 2, 2, 5, 1, 1, 4, 4, 3, 9, 4, 4, 3, 4, 7, 2, 2, 6)
        val r = Random(n.ordinal * 7919L + 13)
        return List(counts[n.ordinal]) { 0.15f + r.nextFloat() * 0.7f to 0.15f + r.nextFloat() * 0.7f }
    }

    fun allText(): List<String> = deck.flatMap { listOf(it.title, it.line, it.meaning, it.symbol, it.deity) } + OracleDeck.entries.flatMap { listOf(it.title, it.blurb) }
}

object OracleCodec {
    fun encode(d: OracleDraw): String = Json.write(mapOf("id" to d.id, "deck" to d.deck.name, "card" to d.cardId, "at" to d.drawnAt.toString(), "q" to d.question, "day" to d.day?.toString()))
    @Suppress("UNCHECKED_CAST")
    fun decode(s: String): OracleDraw? = try {
        val m = Json.parse(s) as Map<String, Any?>
        OracleDraw(m["id"] as String, OracleDeck.valueOf(m["deck"] as String), (m["card"] as String).also { Oracle.card(it) }, Instant.parse(m["at"] as String),
            (m["q"] as String?) ?: "", (m["day"] as String?)?.let { LocalDate.parse(it) })
    } catch (e: Exception) { null }
}
