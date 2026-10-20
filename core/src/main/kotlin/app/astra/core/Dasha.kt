// ASTRA core v0.1.0 — Vimshottari dasha.
package app.astra.core

import java.time.Duration
import java.time.Instant

object Vimshottari {
    val ORDER = listOf(Graha.KETU, Graha.VENUS, Graha.SUN, Graha.MOON, Graha.MARS, Graha.RAHU, Graha.JUPITER, Graha.SATURN, Graha.MERCURY)
    val YEARS = mapOf(
        Graha.KETU to 7.0, Graha.VENUS to 20.0, Graha.SUN to 6.0, Graha.MOON to 10.0, Graha.MARS to 7.0,
        Graha.RAHU to 18.0, Graha.JUPITER to 16.0, Graha.SATURN to 19.0, Graha.MERCURY to 17.0
    )
    const val TOTAL_YEARS = 120.0
    /** Length of a dasha year in days. Disclosed in method panel; 365.25 is the common convention. */
    const val YEAR_DAYS = 365.25

    fun next(g: Graha): Graha = ORDER[(ORDER.indexOf(g) + 1) % 9]
}

data class DashaPeriod(
    val lord: Graha,
    val level: Int, // 1 = maha, 2 = antar, 3 = pratyantar
    val start: Instant,
    val end: Instant,
    val parentLords: List<Graha> = emptyList()
) {
    fun contains(t: Instant) = !t.isBefore(start) && t.isBefore(end)
    val path: List<Graha> get() = parentLords + lord
}

data class DashaNow(val maha: DashaPeriod, val antar: DashaPeriod, val pratyantar: DashaPeriod)

/**
 * Vimshottari timeline seeded by the natal Moon's sidereal longitude.
 * The first mahadasha starts before birth, by the fraction of the birth nakshatra already traversed.
 */
class DashaTimeline(val birth: Instant, val moonLon: Double) {
    val birthNakshatra: Nakshatra = Nakshatra.of(moonLon)
    val firstLord: Graha = birthNakshatra.lord
    private val elapsedFraction = Nakshatra.fraction(moonLon)

    /** Remaining years of the first mahadasha at birth. */
    val balanceYears: Double = Vimshottari.YEARS.getValue(firstLord) * (1.0 - elapsedFraction)

    val mahadashas: List<DashaPeriod> by lazy {
        val out = ArrayList<DashaPeriod>()
        var lord = firstLord
        var start = plusYears(birth, -Vimshottari.YEARS.getValue(firstLord) * elapsedFraction)
        repeat(10) { // a little over one 120-year cycle
            val end = plusYears(start, Vimshottari.YEARS.getValue(lord))
            out += DashaPeriod(lord, 1, start, end)
            start = end; lord = Vimshottari.next(lord)
        }
        out
    }

    /** Sub-periods of any period: proportional split, starting with the period's own lord. */
    fun subPeriods(p: DashaPeriod): List<DashaPeriod> {
        val out = ArrayList<DashaPeriod>(9)
        val totalMillis = Duration.between(p.start, p.end).toMillis().toDouble()
        var lord = p.lord
        var start = p.start
        for (i in 0 until 9) {
            val share = Vimshottari.YEARS.getValue(lord) / Vimshottari.TOTAL_YEARS
            val end = if (i == 8) p.end else start.plusMillis(Math.round(totalMillis * share))
            out += DashaPeriod(lord, p.level + 1, start, end, p.path)
            start = end; lord = Vimshottari.next(lord)
        }
        return out
    }

    fun at(t: Instant): DashaNow? {
        val maha = mahadashas.firstOrNull { it.contains(t) } ?: return null
        val antar = subPeriods(maha).first { it.contains(t) }
        val praty = subPeriods(antar).first { it.contains(t) }
        return DashaNow(maha, antar, praty)
    }

    companion object {
        fun plusYears(t: Instant, years: Double): Instant =
            t.plusMillis(Math.round(years * Vimshottari.YEAR_DAYS * 86_400_000.0))
    }
}
