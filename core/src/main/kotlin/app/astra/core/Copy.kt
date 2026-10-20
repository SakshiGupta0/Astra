// ASTRA core — fixed, deterministic interpretation copy shown in the app (no AI).
// Attributed to tradition, never predictive, never fear-based. Covered by the copy-rules test.
package app.astra.core

object Copy {
    val GRAHA_THEME = mapOf(
        Graha.SUN to "self, vitality and authority",
        Graha.MOON to "the mind, feelings and daily rhythm",
        Graha.MARS to "energy, courage and drive",
        Graha.MERCURY to "communication, learning and trade",
        Graha.JUPITER to "growth, wisdom and guidance",
        Graha.VENUS to "relationships, comfort and beauty",
        Graha.SATURN to "discipline, time and responsibility",
        Graha.RAHU to "ambition and the unfamiliar",
        Graha.KETU to "detachment and inner work"
    )

    /** Traditional themes of each house (bhava). */
    val HOUSE_THEME = mapOf(
        1 to "self and body", 2 to "family, speech and savings", 3 to "courage, effort and siblings",
        4 to "home, mother and inner peace", 5 to "creativity, children and learning", 6 to "work, health routines and obstacles",
        7 to "partnership and marriage", 8 to "change, research and the hidden", 9 to "fortune, teachers and dharma",
        10 to "career and public life", 11 to "gains, friends and aspirations", 12 to "rest, letting go and faraway places"
    )

    val TONE_TEXT = mapOf(
        Tone.SUPPORTIVE to "Traditionally a stretch where effort tends to meet support. A time many people use to start and build.",
        Tone.MIXED to "Supportive and demanding influences sit side by side. Traditionally a time to choose priorities carefully.",
        Tone.DEMANDING to "Traditionally a slower, consolidating stretch that suits discipline, patience and finishing what you have started. Outcomes are not fixed."
    )

    val SATURN_PHASE_TEXT = mapOf(
        SaturnPhase.SADE_SATI_RISING to "The first of three phases of Sade Sati. Tradition links it with reorganising, letting go of what no longer fits, and building patience.",
        SaturnPhase.SADE_SATI_PEAK to "The middle phase of Sade Sati, when Saturn transits your Moon sign. Traditionally a time for responsibility, steady effort and emotional maturity.",
        SaturnPhase.SADE_SATI_SETTING to "The last phase of Sade Sati. Tradition associates it with consolidating lessons and rebuilding at a steadier pace.",
        SaturnPhase.ARDHASHTAMA to "Saturn in the 4th from your Moon. Tradition links it with attention to home, comfort and inner steadiness.",
        SaturnPhase.ASHTAMA to "Saturn in the 8th from your Moon. Traditionally a time for caution, patience and quiet inner work.",
        SaturnPhase.OTHER to "Saturn's transit through this part of your chart."
    )

    const val RETROGRADE_TEXT = "A planet appears to move backwards from Earth's view. Traditionally a time to review, revisit and refine its themes rather than start fresh."
    const val NOT_PREDICTION = "This is how the tradition describes the period. It does not predict events."

    const val MOON_CHART_NOTE = "Your birth time is unknown, so this chart starts from your Moon sign (Chandra lagna) instead of the Lagna."

    fun houseFromMoonLine(g: Graha, house: Int): String =
        "${g.english} in your ${ordinal(house)} from Moon — tradition links this house with ${HOUSE_THEME.getValue(house)}."

    /** Short deterministic line for the Today screen (no AI in M2). */
    fun todayLine(moonHouse: Int, nakshatra: Nakshatra): String =
        "The Moon is in your ${ordinal(moonHouse)} from Moon today, in ${nakshatra.title}. Tradition links this house with ${HOUSE_THEME.getValue(moonHouse)}."

    fun allText(): List<String> = GRAHA_THEME.values + HOUSE_THEME.values + TONE_TEXT.values + SATURN_PHASE_TEXT.values +
        listOf(RETROGRADE_TEXT, NOT_PREDICTION, MOON_CHART_NOTE) +
        Graha.entries.flatMap { g -> (1..12).map { houseFromMoonLine(g, it) } } +
        (1..12).flatMap { h -> Nakshatra.entries.map { todayLine(h, it) } }
}
