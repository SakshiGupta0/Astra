// ASTRA core v0.1.0 — natal chart snapshot with honest birth-time handling.
package app.astra.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

object CoreVersion {
    const val CORE = "0.1.0"
    /** Changes whenever any calculation convention changes; stored on every snapshot. */
    const val CALC = "calc-1: lahiri-mean(SE-compatible) | whole-sign | mean-node | vimshottari-365.25"
}

enum class BirthTimeStatus { KNOWN, APPROXIMATE, UNKNOWN }

data class BirthInput(
    val date: LocalDate,
    val time: LocalTime?,
    val timeStatus: BirthTimeStatus,
    /** ± minutes for APPROXIMATE (30, 60 or 120). */
    val windowMinutes: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    /** Manual UTC offset (minutes) when the user knows the clock time differed from the zone's rules. */
    val utcOffsetOverrideMinutes: Int? = null,
    val placeName: String = ""
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude out of range" }
        require(longitude in -180.0..180.0) { "Longitude out of range" }
        require(timeStatus == BirthTimeStatus.UNKNOWN || time != null) { "Birth time required unless unknown" }
        require(timeStatus != BirthTimeStatus.APPROXIMATE || windowMinutes in listOf(30, 60, 120)) { "Window must be 30, 60 or 120 minutes" }
    }
}

enum class LagnaStatus { EXACT, APPROXIMATE, UNCERTAIN, UNAVAILABLE }

data class Placement(
    val graha: Graha,
    val lon: Double,
    val rashi: Rashi,
    val degreeInSign: Double,
    val nakshatra: Nakshatra,
    val pada: Int,
    val retrograde: Boolean,
    val speed: Double,
    val houseFromLagna: Int?,
    val houseFromMoon: Int
)

data class ChartSnapshot(
    val calcVersion: String,
    val settings: Map<String, String>,
    val input: BirthInput,
    /** Instant the positions were computed for (birth time, or local noon if time unknown). */
    val instant: Instant,
    val placements: List<Placement>,
    val lagnaLon: Double?,
    val lagnaStatus: LagnaStatus,
    /** Possible Lagna signs when uncertain (2+ entries), or the single sign otherwise. */
    val lagnaCandidates: List<Rashi>,
    val moonSignCertain: Boolean,
    val moonNakshatraCertain: Boolean,
    val dasha: DashaTimeline,
    /** How far mahadasha boundaries may shift because of birth-time uncertainty (days); 0 when exact. */
    val dashaUncertaintyDays: Double,
    /** True if the starting dasha lord itself depends on the unknown time. */
    val dashaLordUncertain: Boolean,
    val warnings: List<String>
) {
    fun placement(g: Graha) = placements.first { it.graha == g }
    val moon: Placement get() = placement(Graha.MOON)
    val lagnaRashi: Rashi? get() = lagnaLon?.let { Rashi.of(it) }
}

object ChartCalculator {
    val SETTINGS = mapOf(
        "zodiac" to "Sidereal",
        "ayanamsha" to "Lahiri (Chitrapaksha)",
        "houses" to "Whole sign",
        "nodes" to "Mean Rahu / Ketu",
        "dasha" to "Vimshottari, 365.25-day year",
        "ephemeris" to "Astronomy Engine (MIT)"
    )

    data class Resolved(val instant: Instant, val warnings: List<String>)

    fun resolve(input: BirthInput, local: LocalDateTime): Resolved {
        val warnings = ArrayList<String>()
        input.utcOffsetOverrideMinutes?.let {
            return Resolved(local.toInstant(ZoneOffset.ofTotalSeconds(it * 60)), listOf("Using the UTC offset you entered."))
        }
        val zone = ZoneId.of(input.zoneId)
        val rules = zone.rules
        val valid = rules.getValidOffsets(local)
        when {
            valid.isEmpty() -> warnings += "This clock time was skipped by a daylight-saving change in ${input.zoneId}; the next valid time is used."
            valid.size > 1 -> warnings += "This clock time happened twice (daylight-saving change); the earlier one is used."
        }
        val instant = local.atZone(zone).toInstant()
        val offset = rules.getOffset(instant)
        if (offset.totalSeconds % 900 != 0) {
            warnings += "Clocks here used a local time offset (UTC${offset.id}) at this date. If your birth record used a different time standard, enter the offset manually."
        }
        return Resolved(instant, warnings)
    }

    fun compute(input: BirthInput): ChartSnapshot {
        val warnings = ArrayList<String>()
        val center: Instant
        var edges: Pair<Instant, Instant>? = null
        when (input.timeStatus) {
            BirthTimeStatus.KNOWN -> {
                val r = resolve(input, LocalDateTime.of(input.date, input.time)); warnings += r.warnings; center = r.instant
            }
            BirthTimeStatus.APPROXIMATE -> {
                val r = resolve(input, LocalDateTime.of(input.date, input.time)); warnings += r.warnings; center = r.instant
                val w = Duration.ofMinutes(input.windowMinutes.toLong())
                edges = center.minus(w) to center.plus(w)
            }
            BirthTimeStatus.UNKNOWN -> {
                center = resolve(input, LocalDateTime.of(input.date, LocalTime.NOON)).instant
                val start = resolve(input, LocalDateTime.of(input.date, LocalTime.MIDNIGHT)).instant
                val end = resolve(input, LocalDateTime.of(input.date, LocalTime.of(23, 59, 59))).instant
                edges = start to end
                warnings += "Birth time unknown: Lagna and houses are not shown. Planet positions are for local noon."
            }
        }
        val t = Ephemeris.toTime(center)

        // Lagna
        var lagnaLon: Double? = null
        var lagnaStatus = LagnaStatus.UNAVAILABLE
        var candidates = emptyList<Rashi>()
        when (input.timeStatus) {
            BirthTimeStatus.KNOWN -> {
                lagnaLon = Ephemeris.ascendant(t, input.latitude, input.longitude)
                lagnaStatus = LagnaStatus.EXACT; candidates = listOf(Rashi.of(lagnaLon))
            }
            BirthTimeStatus.APPROXIMATE -> {
                candidates = lagnaSignsBetween(edges!!.first, edges.second, input)
                if (candidates.size == 1) {
                    lagnaLon = Ephemeris.ascendant(t, input.latitude, input.longitude)
                    lagnaStatus = LagnaStatus.APPROXIMATE
                } else {
                    lagnaStatus = LagnaStatus.UNCERTAIN
                    warnings += "Your Lagna could be ${candidates.joinToString(" or ") { it.sanskrit }} within your time window, so house positions are not shown."
                }
            }
            BirthTimeStatus.UNKNOWN -> {}
        }

        val moonLon = Ephemeris.siderealLongitude(Graha.MOON, t)
        val placements = Graha.entries.map { g ->
            val lon = Ephemeris.siderealLongitude(g, t)
            Placement(
                graha = g, lon = lon, rashi = Rashi.of(lon), degreeInSign = lon % 30.0,
                nakshatra = Nakshatra.of(lon), pada = Nakshatra.pada(lon),
                retrograde = Ephemeris.isRetrograde(g, t), speed = Ephemeris.speed(g, t),
                houseFromLagna = lagnaLon?.let { Houses.fromLon(lon, it) },
                houseFromMoon = Houses.fromLon(lon, moonLon)
            )
        }

        // Moon certainty and dasha uncertainty across the time window
        var moonSignCertain = true
        var moonNakCertain = true
        var dashaUncertainty = 0.0
        var dashaLordUncertain = false
        val dasha = DashaTimeline(center, moonLon)
        edges?.let { (a, b) ->
            val la = Ephemeris.siderealLongitude(Graha.MOON, Ephemeris.toTime(a))
            val lb = Ephemeris.siderealLongitude(Graha.MOON, Ephemeris.toTime(b))
            moonSignCertain = Rashi.of(la) == Rashi.of(lb)
            moonNakCertain = Nakshatra.of(la) == Nakshatra.of(lb)
            val da = DashaTimeline(a, la); val db = DashaTimeline(b, lb)
            dashaLordUncertain = da.firstLord != db.firstLord
            if (!dashaLordUncertain) {
                dashaUncertainty = maxOf(
                    abs(Duration.between(dasha.mahadashas[0].end, da.mahadashas[0].end).toMinutes() / 1440.0),
                    abs(Duration.between(dasha.mahadashas[0].end, db.mahadashas[0].end).toMinutes() / 1440.0)
                )
            }
            if (!moonSignCertain) warnings += "The Moon changed sign during your possible birth window; Moon sign shown is for the middle of the window."
            else if (!moonNakCertain) warnings += "The Moon changed nakshatra during your possible birth window, so your dasha sequence is uncertain."
        }

        return ChartSnapshot(
            calcVersion = CoreVersion.CALC, settings = SETTINGS, input = input, instant = center,
            placements = placements, lagnaLon = lagnaLon, lagnaStatus = lagnaStatus, lagnaCandidates = candidates,
            moonSignCertain = moonSignCertain, moonNakshatraCertain = moonNakCertain,
            dasha = dasha, dashaUncertaintyDays = dashaUncertainty, dashaLordUncertain = dashaLordUncertain,
            warnings = warnings
        )
    }

    /** Distinct Lagna signs passed through between two instants (sampled every 4 minutes). */
    fun lagnaSignsBetween(a: Instant, b: Instant, input: BirthInput): List<Rashi> {
        val out = LinkedHashSet<Rashi>()
        var t = a
        while (!t.isAfter(b)) {
            out += Rashi.of(Ephemeris.ascendant(Ephemeris.toTime(t), input.latitude, input.longitude))
            t = t.plusSeconds(240)
        }
        out += Rashi.of(Ephemeris.ascendant(Ephemeris.toTime(b), input.latitude, input.longitude))
        return out.toList()
    }
}
