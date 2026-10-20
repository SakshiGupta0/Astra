// ASTRA core v0.1.0 — Vedic reference tables: rashis, nakshatras, houses.
package app.astra.core

enum class Rashi(val sanskrit: String, val english: String) {
    MESHA("Mesha", "Aries"), VRISHABHA("Vrishabha", "Taurus"), MITHUNA("Mithuna", "Gemini"),
    KARKA("Karka", "Cancer"), SIMHA("Simha", "Leo"), KANYA("Kanya", "Virgo"),
    TULA("Tula", "Libra"), VRISHCHIKA("Vrishchika", "Scorpio"), DHANU("Dhanu", "Sagittarius"),
    MAKARA("Makara", "Capricorn"), KUMBHA("Kumbha", "Aquarius"), MEENA("Meena", "Pisces");

    companion object { fun of(lon: Double): Rashi = entries[Ephemeris.signOf(lon)] }
}

/** The 27 nakshatras with their Vimshottari lords. */
enum class Nakshatra(val title: String, val lord: Graha) {
    ASHWINI("Ashwini", Graha.KETU), BHARANI("Bharani", Graha.VENUS), KRITTIKA("Krittika", Graha.SUN),
    ROHINI("Rohini", Graha.MOON), MRIGASHIRA("Mrigashira", Graha.MARS), ARDRA("Ardra", Graha.RAHU),
    PUNARVASU("Punarvasu", Graha.JUPITER), PUSHYA("Pushya", Graha.SATURN), ASHLESHA("Ashlesha", Graha.MERCURY),
    MAGHA("Magha", Graha.KETU), PURVA_PHALGUNI("Purva Phalguni", Graha.VENUS), UTTARA_PHALGUNI("Uttara Phalguni", Graha.SUN),
    HASTA("Hasta", Graha.MOON), CHITRA("Chitra", Graha.MARS), SWATI("Swati", Graha.RAHU),
    VISHAKHA("Vishakha", Graha.JUPITER), ANURADHA("Anuradha", Graha.SATURN), JYESHTHA("Jyeshtha", Graha.MERCURY),
    MULA("Mula", Graha.KETU), PURVA_ASHADHA("Purva Ashadha", Graha.VENUS), UTTARA_ASHADHA("Uttara Ashadha", Graha.SUN),
    SHRAVANA("Shravana", Graha.MOON), DHANISHTA("Dhanishta", Graha.MARS), SHATABHISHA("Shatabhisha", Graha.RAHU),
    PURVA_BHADRAPADA("Purva Bhadrapada", Graha.JUPITER), UTTARA_BHADRAPADA("Uttara Bhadrapada", Graha.SATURN), REVATI("Revati", Graha.MERCURY);

    companion object {
        const val SPAN = 360.0 / 27.0
        fun of(lon: Double): Nakshatra = entries[(Ephemeris.norm360(lon) / SPAN).toInt().coerceIn(0, 26)]
        fun pada(lon: Double): Int = ((Ephemeris.norm360(lon) % SPAN) / (SPAN / 4.0)).toInt().coerceIn(0, 3) + 1
        /** Fraction (0..1) of the nakshatra already traversed. */
        fun fraction(lon: Double): Double = (Ephemeris.norm360(lon) % SPAN) / SPAN
    }
}

object Houses {
    /** Whole-sign house (1..12) of a sign counted from a reference sign. */
    fun fromSign(signIndex: Int, referenceSignIndex: Int): Int = ((signIndex - referenceSignIndex + 12) % 12) + 1
    fun fromLon(lon: Double, referenceLon: Double): Int = fromSign(Ephemeris.signOf(lon), Ephemeris.signOf(referenceLon))
}

/** A position formatted for display: "Simha 14°23′". */
fun formatSidereal(lon: Double): String {
    val r = Rashi.of(lon)
    val inSign = Ephemeris.norm360(lon) % 30.0
    val d = inSign.toInt()
    val m = ((inSign - d) * 60.0).toInt()
    return "${r.sanskrit} ${d}°${"%02d".format(m)}′"
}
