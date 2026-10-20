// ASTRA core v0.1.0 — sidereal ephemeris layer over Astronomy Engine.
// Pure Kotlin: no Android code. Everything here is deterministic and unit-tested
// against Swiss Ephemeris (Lahiri) reference fixtures.
package app.astra.core

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.astraNutation
import io.github.cosinekitty.astronomy.eclipticGeoMoon
import io.github.cosinekitty.astronomy.equatorialToEcliptic
import io.github.cosinekitty.astronomy.geoVector
import io.github.cosinekitty.astronomy.siderealTime
import io.github.cosinekitty.astronomy.sunPosition
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** The nine grahas in traditional order. */
enum class Graha(val english: String, val sanskrit: String, val body: Body?) {
    SUN("Sun", "Surya", Body.Sun),
    MOON("Moon", "Chandra", Body.Moon),
    MARS("Mars", "Mangal", Body.Mars),
    MERCURY("Mercury", "Budh", Body.Mercury),
    JUPITER("Jupiter", "Guru", Body.Jupiter),
    VENUS("Venus", "Shukra", Body.Venus),
    SATURN("Saturn", "Shani", Body.Saturn),
    RAHU("Rahu", "Rahu", null),
    KETU("Ketu", "Ketu", null);
}

object Ephemeris {
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val J2000_EPOCH_DAY = 10_957.5 // 2000-01-01T12:00Z in days since 1970

    /**
     * Lahiri (Chitrapaksha) mean ayanamsha at J2000.0, in arcseconds.
     * Calibrated so that mean ayanamsha + nutation matches Swiss Ephemeris SIDM_LAHIRI
     * (verified 1900–2100 within 0.2 arcsec; see fixtures).
     */
    private const val LAHIRI_J2000_ARCSEC = 85_885.641

    fun toTime(instant: Instant): Time = Time(instant.toEpochMilli() / MILLIS_PER_DAY - J2000_EPOCH_DAY)
    fun toInstant(time: Time): Instant = Instant.ofEpochMilli(Math.round((time.ut + J2000_EPOCH_DAY) * MILLIS_PER_DAY))

    fun norm360(x: Double): Double { val r = x % 360.0; return if (r < 0) r + 360.0 else r }

    /** Signed smallest difference a - b in (-180, 180]. */
    fun diff(a: Double, b: Double): Double { var d = norm360(a - b); if (d > 180.0) d -= 360.0; return d }

    /** Mean Lahiri ayanamsha in degrees (IAU 2006 general precession in longitude). */
    fun ayanamsha(t: Time): Double {
        val c = t.tt / 36525.0
        val pA = c * (5028.796195 + c * (1.1054348 + c * (0.00007964 + c * (-0.000023857 + c * -0.0000000383))))
        return (LAHIRI_J2000_ARCSEC + pA) / 3600.0
    }

    /** Nutation in longitude, degrees. */
    fun nutationLongitude(t: Time): Double = astraNutation(t)[0]

    /** Apparent tropical longitude, true equinox of date, degrees. */
    fun tropicalLongitude(g: Graha, t: Time): Double = when (g) {
        Graha.SUN -> sunPosition(t).elon
        Graha.MOON -> eclipticGeoMoon(t).lon
        Graha.RAHU -> norm360(meanNodeTropical(t) + nutationLongitude(t))
        Graha.KETU -> norm360(meanNodeTropical(t) + nutationLongitude(t) + 180.0)
        else -> equatorialToEcliptic(geoVector(g.body!!, t, Aberration.Corrected)).elon
    }

    /** Mean lunar ascending node, mean equinox of date (Meeus 47.7), degrees. */
    fun meanNodeTropical(t: Time): Double {
        val c = t.tt / 36525.0
        return norm360(125.0445479 - 1934.1362891 * c + 0.0020754 * c * c + c * c * c / 467441.0 - c * c * c * c / 60616000.0)
    }

    /** Sidereal (Lahiri) longitude in degrees, 0 ≤ x < 360. */
    fun siderealLongitude(g: Graha, t: Time): Double = when (g) {
        Graha.RAHU -> norm360(meanNodeTropical(t) - ayanamsha(t))
        Graha.KETU -> norm360(meanNodeTropical(t) - ayanamsha(t) + 180.0)
        else -> norm360(tropicalLongitude(g, t) - nutationLongitude(t) - ayanamsha(t))
    }

    /** Daily motion in degrees per day (central difference over one day). */
    fun speed(g: Graha, t: Time): Double =
        diff(siderealLongitude(g, t.addDays(0.5)), siderealLongitude(g, t.addDays(-0.5)))

    fun isRetrograde(g: Graha, t: Time): Boolean = when (g) {
        Graha.SUN, Graha.MOON -> false
        Graha.RAHU, Graha.KETU -> true // mean nodes always move backwards
        else -> speed(g, t) < 0.0
    }

    /** Sidereal ascendant (Lagna) in degrees for a place. Longitude east positive. */
    fun ascendant(t: Time, latitude: Double, longitude: Double): Double {
        val nut = astraNutation(t)
        val eps = Math.toRadians(nut[2])
        val ramc = Math.toRadians(norm360(siderealTime(t) * 15.0 + longitude))
        val phi = Math.toRadians(latitude)
        var asc = Math.toDegrees(atan2(cos(ramc), -(sin(ramc) * cos(eps) + tan(phi) * sin(eps))))
        // The ascendant lies in the half of the ecliptic that follows the MC. Inside the polar
        // circles the formula can return the descendant; flip it (same convention as Swiss Ephemeris).
        val mc = Math.toDegrees(atan2(sin(ramc), cos(ramc) * cos(eps)))
        if (norm360(asc - mc) > 180.0) asc += 180.0
        return norm360(asc - nut[0] - ayanamsha(t))
    }

    fun signOf(lon: Double): Int = floor(norm360(lon) / 30.0).toInt() // 0 = Mesha
}
