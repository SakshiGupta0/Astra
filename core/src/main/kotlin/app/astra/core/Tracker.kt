// ASTRA core v0.1.0 — Graha Tracker: live positions, sign intervals, retrogrades,
// gochar from the natal Moon, period tone (rule v1), Saturn phases and events.
package app.astra.core

import java.time.Duration
import java.time.Instant

enum class Tone(val label: String) { SUPPORTIVE("Supportive"), MIXED("Mixed"), DEMANDING("Demanding") }

enum class SaturnPhase(val label: String) {
    SADE_SATI_RISING("Sade Sati — rising phase (12th from Moon)"),
    SADE_SATI_PEAK("Sade Sati — peak phase (1st from Moon)"),
    SADE_SATI_SETTING("Sade Sati — setting phase (2nd from Moon)"),
    ARDHASHTAMA("Ardhashtama Shani (4th from Moon)"),
    ASHTAMA("Ashtama Shani (8th from Moon)"),
    OTHER("Saturn transit");

    companion object {
        fun ofHouse(h: Int) = when (h) {
            12 -> SADE_SATI_RISING; 1 -> SADE_SATI_PEAK; 2 -> SADE_SATI_SETTING
            4 -> ARDHASHTAMA; 8 -> ASHTAMA; else -> OTHER
        }
    }
}

object Gochar {
    /** Classical favourable houses counted from the natal Moon. Version-stamped; reviewed in M1. */
    const val RULE_VERSION = "tone-1: moon-based Jupiter + Saturn gochar"
    val FAVOURABLE: Map<Graha, Set<Int>> = mapOf(
        Graha.SUN to setOf(3, 6, 10, 11),
        Graha.MOON to setOf(1, 3, 6, 7, 10, 11),
        Graha.MARS to setOf(3, 6, 11),
        Graha.MERCURY to setOf(2, 4, 6, 8, 10, 11),
        Graha.JUPITER to setOf(2, 5, 7, 9, 11),
        Graha.VENUS to setOf(1, 2, 3, 4, 5, 8, 9, 11, 12),
        Graha.SATURN to setOf(3, 6, 11),
        Graha.RAHU to setOf(3, 6, 11),
        Graha.KETU to setOf(3, 6, 11)
    )
    val SATURN_DEMANDING = setOf(12, 1, 2, 4, 8)

    fun isFavourable(g: Graha, houseFromMoon: Int) = houseFromMoon in FAVOURABLE.getValue(g)

    fun tone(jupiterHouse: Int, saturnHouse: Int): Tone {
        val jFav = isFavourable(Graha.JUPITER, jupiterHouse)
        val sHard = saturnHouse in SATURN_DEMANDING
        return when {
            jFav && !sHard -> Tone.SUPPORTIVE
            sHard && !jFav -> Tone.DEMANDING
            else -> Tone.MIXED
        }
    }

    fun factors(jupiterHouse: Int, saturnHouse: Int): List<String> {
        val out = ArrayList<String>()
        out += if (isFavourable(Graha.JUPITER, jupiterHouse))
            "Jupiter ${ordinal(jupiterHouse)} from your Moon — traditionally supportive"
        else "Jupiter ${ordinal(jupiterHouse)} from your Moon — traditionally neutral"
        val phase = SaturnPhase.ofHouse(saturnHouse)
        out += when {
            phase != SaturnPhase.OTHER -> "${phase.label} — traditionally a demanding, consolidating time"
            isFavourable(Graha.SATURN, saturnHouse) -> "Saturn ${ordinal(saturnHouse)} from your Moon — traditionally supportive"
            else -> "Saturn ${ordinal(saturnHouse)} from your Moon — traditionally neutral"
        }
        return out
    }
}

fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th"
}

data class SignInterval(val graha: Graha, val rashi: Rashi, val start: Instant, val end: Instant, val houseFromMoon: Int)
data class RetroInterval(val graha: Graha, val start: Instant, val end: Instant)
data class ToneSegment(val start: Instant, val end: Instant, val tone: Tone, val jupiterHouse: Int, val saturnHouse: Int, val factors: List<String>)
data class SaturnSegment(val start: Instant, val end: Instant, val phase: SaturnPhase, val house: Int)

enum class EventType { INGRESS, NODES_INGRESS, RETRO_START, RETRO_END, MAHADASHA_CHANGE, ANTARDASHA_CHANGE, TONE_CHANGE }

data class TransitEvent(
    val instant: Instant,
    val type: EventType,
    val graha: Graha?,
    val major: Boolean,
    val title: String,
    val detail: String
)

data class NowPosition(
    val graha: Graha,
    val lon: Double,
    val rashi: Rashi,
    val nakshatra: Nakshatra,
    val retrograde: Boolean,
    val houseFromMoon: Int,
    val houseFromLagna: Int?,
    val favourableFromMoon: Boolean,
    val nextSignChange: Instant?,
    val nextRashi: Rashi?
)

data class Timeline(
    val from: Instant,
    val to: Instant,
    val signIntervals: Map<Graha, List<SignInterval>>,
    val retrogrades: List<RetroInterval>,
    val tone: List<ToneSegment>,
    val saturn: List<SaturnSegment>,
    val mahadashas: List<DashaPeriod>,
    val antardashas: List<DashaPeriod>,
    val events: List<TransitEvent>
)

object TransitSearch {
    private val DAY = Duration.ofDays(1)

    fun signAt(g: Graha, t: Instant) = Ephemeris.signOf(Ephemeris.siderealLongitude(g, Ephemeris.toTime(t)))
    private fun speedAt(g: Graha, t: Instant) = Ephemeris.speed(g, Ephemeris.toTime(t))

    /** Bisection to the instant the sign changes between a (sign sa) and b. Accuracy: 1 minute. */
    fun refineSignChange(g: Graha, a0: Instant, b0: Instant): Instant {
        var a = a0; var b = b0
        val sa = signAt(g, a)
        while (Duration.between(a, b).toSeconds() > 60) {
            val m = a.plusMillis(Duration.between(a, b).toMillis() / 2)
            if (signAt(g, m) == sa) a = m else b = m
        }
        return b
    }

    private fun refineStation(g: Graha, a0: Instant, b0: Instant): Instant {
        var a = a0; var b = b0
        val sa = speedAt(g, a) < 0
        while (Duration.between(a, b).toSeconds() > 600) {
            val m = a.plusMillis(Duration.between(a, b).toMillis() / 2)
            if ((speedAt(g, m) < 0) == sa) a = m else b = m
        }
        return b
    }

    /** Next sign change after t (searches up to maxDays). */
    fun nextSignChange(g: Graha, t: Instant, maxDays: Int = 1200): Instant? {
        val step = if (g == Graha.MOON) Duration.ofHours(2) else DAY
        val s0 = signAt(g, t)
        var a = t
        val limit = t.plus(Duration.ofDays(maxDays.toLong()))
        while (a.isBefore(limit)) {
            val b = a.plus(step)
            if (signAt(g, b) != s0) return refineSignChange(g, a, b)
            a = b
        }
        return null
    }

    fun signIntervals(g: Graha, from: Instant, to: Instant, natalMoonLon: Double): List<SignInterval> {
        val out = ArrayList<SignInterval>()
        var segStart = from
        var cur = signAt(g, from)
        var a = from
        while (a.isBefore(to)) {
            val b = minOf(a.plus(DAY), to)
            val sb = signAt(g, b)
            if (sb != cur) {
                val change = refineSignChange(g, a, b)
                out += SignInterval(g, Rashi.entries[cur], segStart, change, Houses.fromSign(cur, Ephemeris.signOf(natalMoonLon)))
                segStart = change; cur = sb
            }
            a = b
        }
        out += SignInterval(g, Rashi.entries[cur], segStart, to, Houses.fromSign(cur, Ephemeris.signOf(natalMoonLon)))
        return out
    }

    fun retrogrades(g: Graha, from: Instant, to: Instant): List<RetroInterval> {
        val out = ArrayList<RetroInterval>()
        var retro = speedAt(g, from) < 0
        var start: Instant? = if (retro) from else null
        var a = from
        while (a.isBefore(to)) {
            val b = minOf(a.plus(DAY), to)
            val rb = speedAt(g, b) < 0
            if (rb != retro) {
                val st = refineStation(g, a, b)
                if (rb) start = st else { out += RetroInterval(g, start ?: from, st); start = null }
                retro = rb
            }
            a = b
        }
        if (retro) out += RetroInterval(g, start ?: from, to)
        return out
    }
}

class Tracker(val chart: ChartSnapshot) {
    private val moonLon = chart.moon.lon
    private val moonSign = Ephemeris.signOf(moonLon)

    fun now(at: Instant): List<NowPosition> {
        val t = Ephemeris.toTime(at)
        return Graha.entries.map { g ->
            val lon = Ephemeris.siderealLongitude(g, t)
            val h = Houses.fromSign(Ephemeris.signOf(lon), moonSign)
            val next = TransitSearch.nextSignChange(g, at)
            NowPosition(
                graha = g, lon = lon, rashi = Rashi.of(lon), nakshatra = Nakshatra.of(lon),
                retrograde = Ephemeris.isRetrograde(g, t), houseFromMoon = h,
                houseFromLagna = chart.lagnaLon?.let { Houses.fromLon(lon, it) },
                favourableFromMoon = Gochar.isFavourable(g, h),
                nextSignChange = next,
                nextRashi = next?.let { Rashi.entries[TransitSearch.signAt(g, it.plusSeconds(120))] }
            )
        }
    }

    fun timeline(from: Instant, to: Instant): Timeline {
        val slow = listOf(Graha.SUN, Graha.MARS, Graha.MERCURY, Graha.JUPITER, Graha.VENUS, Graha.SATURN, Graha.RAHU)
        val intervals = slow.associateWith { TransitSearch.signIntervals(it, from, to, moonLon) }.toMutableMap()
        intervals[Graha.KETU] = intervals.getValue(Graha.RAHU).map {
            val k = (Ephemeris.signOf(it.rashi.ordinal * 30.0 + 180.0))
            SignInterval(Graha.KETU, Rashi.entries[k], it.start, it.end, Houses.fromSign(k, moonSign))
        }
        val retro = listOf(Graha.MERCURY, Graha.VENUS, Graha.MARS, Graha.JUPITER, Graha.SATURN)
            .flatMap { TransitSearch.retrogrades(it, from, to) }

        // Tone segments from the union of Jupiter and Saturn boundaries
        val jup = intervals.getValue(Graha.JUPITER); val sat = intervals.getValue(Graha.SATURN)
        val bounds = (jup.map { it.start } + sat.map { it.start } + to).distinct().sorted()
        val rawTone = bounds.zipWithNext().map { (s, e) ->
            val jh = jup.first { !s.isBefore(it.start) && s.isBefore(it.end) }.houseFromMoon
            val sh = sat.first { !s.isBefore(it.start) && s.isBefore(it.end) }.houseFromMoon
            ToneSegment(s, e, Gochar.tone(jh, sh), jh, sh, Gochar.factors(jh, sh))
        }
        val tone = mergeTone(rawTone)
        val saturn = sat.map { SaturnSegment(it.start, it.end, SaturnPhase.ofHouse(it.houseFromMoon), it.houseFromMoon) }

        val mahas = chart.dasha.mahadashas.filter { it.end.isAfter(from) && it.start.isBefore(to) }
        val antars = mahas.flatMap { chart.dasha.subPeriods(it) }.filter { it.end.isAfter(from) && it.start.isBefore(to) }

        val events = ArrayList<TransitEvent>()
        for ((g, list) in intervals) {
            if (g == Graha.KETU) continue
            for (iv in list.drop(1)) {
                if (g == Graha.RAHU) {
                    val k = intervals.getValue(Graha.KETU).first { it.start == iv.start }
                    events += TransitEvent(iv.start, EventType.NODES_INGRESS, Graha.RAHU, true,
                        "Rahu enters ${iv.rashi.sanskrit}, Ketu enters ${k.rashi.sanskrit}",
                        "Rahu ${ordinal(iv.houseFromMoon)} and Ketu ${ordinal(k.houseFromMoon)} from your Moon")
                } else {
                    val major = g == Graha.JUPITER || g == Graha.SATURN
                    val fav = Gochar.isFavourable(g, iv.houseFromMoon)
                    var detail = "${ordinal(iv.houseFromMoon)} from your Moon" + if (fav) " — traditionally supportive" else ""
                    if (g == Graha.SATURN) {
                        val ph = SaturnPhase.ofHouse(iv.houseFromMoon)
                        if (ph != SaturnPhase.OTHER) detail = "${ordinal(iv.houseFromMoon)} from your Moon — ${ph.label}"
                    }
                    events += TransitEvent(iv.start, EventType.INGRESS, g, major, "${g.english} enters ${iv.rashi.sanskrit}", detail)
                }
            }
        }
        for (r in retro) {
            val major = r.graha == Graha.JUPITER || r.graha == Graha.SATURN
            if (r.start.isAfter(from)) events += TransitEvent(r.start, EventType.RETRO_START, r.graha, major, "${r.graha.english} turns retrograde", "Appears to move backwards until it turns direct")
            if (r.end.isBefore(to)) events += TransitEvent(r.end, EventType.RETRO_END, r.graha, major, "${r.graha.english} turns direct", "Resumes forward motion")
        }
        for (m in mahas) if (m.start.isAfter(from)) events += TransitEvent(m.start, EventType.MAHADASHA_CHANGE, m.lord, true, "${m.lord.english} mahadasha begins", "Major period of ${m.lord.english}, ${Vimshottari.YEARS.getValue(m.lord).toInt()} years")
        for (a in antars) if (a.start.isAfter(from)) events += TransitEvent(a.start, EventType.ANTARDASHA_CHANGE, a.lord, true, "${a.parentLords.first().english}–${a.lord.english} period begins", "Sub-period of ${a.lord.english} within ${a.parentLords.first().english} mahadasha")
        for ((prev, s) in tone.zipWithNext()) if (prev.tone != s.tone) events +=TransitEvent(s.start, EventType.TONE_CHANGE, null, true, "Period tone becomes ${s.tone.label.lowercase()}", s.factors.joinToString("; "))

        return Timeline(from, to, intervals, retro, tone, saturn, mahas, antars, events.sortedBy { it.instant })
    }

    private fun mergeTone(segs: List<ToneSegment>): List<ToneSegment> {
        val out = ArrayList<ToneSegment>()
        for (s in segs) {
            val last = out.lastOrNull()
            if (last != null && last.tone == s.tone && last.jupiterHouse == s.jupiterHouse && last.saturnHouse == s.saturnHouse)
                out[out.size - 1] = last.copy(end = s.end)
            else out += s
        }
        return out
    }
}
