// ASTRA core v0.1.0 — daily panchang: tithi, paksha, nakshatra, yoga, vara, sunrise/sunset, Rahu Kaal.
// Values are those prevailing at local sunrise (traditional convention), each with its end time.
package app.astra.core

import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.searchRiseSet
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Limb<T>(val value: T, val endsAt: Instant?)

data class Panchang(
    val date: LocalDate,
    val sunrise: Instant?,
    val sunset: Instant?,
    val vara: String,
    val tithi: Limb<Tithi>?,
    val nakshatra: Limb<Nakshatra>?,
    val yoga: Limb<String>?,
    val rahuKaalStart: Instant?,
    val rahuKaalEnd: Instant?,
    val note: String?
)

data class Tithi(val index: Int) { // 0..29
    val paksha: String get() = if (index < 15) "Shukla" else "Krishna"
    val numberInPaksha: Int get() = index % 15 + 1
    val name: String get() = when {
        index == 14 -> "Purnima"
        index == 29 -> "Amavasya"
        else -> NAMES[index % 15]
    }
    val label: String get() = "$paksha $name"

    companion object {
        val NAMES = listOf("Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami", "Ashtami",
            "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Purnima")
    }
}

object PanchangCalculator {
    val YOGAS = listOf("Vishkambha", "Priti", "Ayushman", "Saubhagya", "Shobhana", "Atiganda", "Sukarma", "Dhriti", "Shula",
        "Ganda", "Vriddhi", "Dhruva", "Vyaghata", "Harshana", "Vajra", "Siddhi", "Vyatipata", "Variyana", "Parigha", "Shiva",
        "Siddha", "Sadhya", "Shubha", "Shukla", "Brahma", "Indra", "Vaidhriti")
    val VARA = mapOf(
        DayOfWeek.SUNDAY to "Ravivar", DayOfWeek.MONDAY to "Somvar", DayOfWeek.TUESDAY to "Mangalvar",
        DayOfWeek.WEDNESDAY to "Budhvar", DayOfWeek.THURSDAY to "Guruvar", DayOfWeek.FRIDAY to "Shukravar", DayOfWeek.SATURDAY to "Shanivar"
    )
    /** Which eighth of daytime is Rahu Kaal, by weekday. */
    val RAHU_KAAL_PART = mapOf(
        DayOfWeek.MONDAY to 2, DayOfWeek.TUESDAY to 7, DayOfWeek.WEDNESDAY to 5, DayOfWeek.THURSDAY to 6,
        DayOfWeek.FRIDAY to 4, DayOfWeek.SATURDAY to 3, DayOfWeek.SUNDAY to 8
    )

    fun tithiIndex(t: Instant): Int {
        val tt = Ephemeris.toTime(t)
        val e = Ephemeris.norm360(Ephemeris.tropicalLongitude(Graha.MOON, tt) - Ephemeris.tropicalLongitude(Graha.SUN, tt))
        return (e / 12.0).toInt().coerceIn(0, 29)
    }
    fun nakshatraIndex(t: Instant): Int = Nakshatra.of(Ephemeris.siderealLongitude(Graha.MOON, Ephemeris.toTime(t))).ordinal
    fun yogaIndex(t: Instant): Int {
        val tt = Ephemeris.toTime(t)
        val s = Ephemeris.norm360(Ephemeris.siderealLongitude(Graha.SUN, tt) + Ephemeris.siderealLongitude(Graha.MOON, tt))
        return (s / Nakshatra.SPAN).toInt().coerceIn(0, 26)
    }

    /** First instant after t0 where f changes value (1-minute accuracy), searching up to 3 days. */
    fun nextChange(t0: Instant, f: (Instant) -> Int): Instant? {
        val v0 = f(t0)
        var a = t0
        val limit = t0.plus(Duration.ofDays(3))
        while (a.isBefore(limit)) {
            val b = a.plus(Duration.ofMinutes(60))
            if (f(b) != v0) {
                var lo = a; var hi = b
                while (Duration.between(lo, hi).toSeconds() > 60) {
                    val m = lo.plusMillis(Duration.between(lo, hi).toMillis() / 2)
                    if (f(m) == v0) lo = m else hi = m
                }
                return hi
            }
            a = b
        }
        return null
    }

    fun compute(date: LocalDate, latitude: Double, longitude: Double, zoneId: String): Panchang {
        val zone = ZoneId.of(zoneId)
        val midnight = date.atStartOfDay(zone).toInstant()
        val obs = Observer(latitude, longitude, 0.0)
        val start = Ephemeris.toTime(midnight)
        val riseT = searchRiseSet(Body.Sun, obs, Direction.Rise, start, 1.0)
        val setT = searchRiseSet(Body.Sun, obs, Direction.Set, riseT ?: start, 1.0)
        val sunrise = riseT?.let { Ephemeris.toInstant(it) }
        val sunset = setT?.let { Ephemeris.toInstant(it) }
        val vara = VARA.getValue(date.dayOfWeek)
        if (sunrise == null || sunset == null) {
            return Panchang(date, sunrise, sunset, vara, null, null, null, null, null,
                "The Sun does not both rise and set here on this date, so sunrise-based panchang is not available.")
        }
        val tithi = Limb(Tithi(tithiIndex(sunrise)), nextChange(sunrise, ::tithiIndex))
        val nak = Limb(Nakshatra.entries[nakshatraIndex(sunrise)], nextChange(sunrise, ::nakshatraIndex))
        val yoga = Limb(YOGAS[yogaIndex(sunrise)], nextChange(sunrise, ::yogaIndex))
        val dayMillis = Duration.between(sunrise, sunset).toMillis()
        val part = RAHU_KAAL_PART.getValue(date.dayOfWeek)
        val rkStart = sunrise.plusMillis(dayMillis * (part - 1) / 8)
        val rkEnd = sunrise.plusMillis(dayMillis * part / 8)
        return Panchang(date, sunrise, sunset, vara, tithi, nak, yoga, rkStart, rkEnd, null)
    }
}
