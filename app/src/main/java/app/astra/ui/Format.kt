package app.astra.ui

import app.astra.core.Ephemeris
import app.astra.core.Graha
import app.astra.core.Rashi
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val dateShort = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val monthYear = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)

    fun date(i: Instant, z: ZoneId): String = i.atZone(z).format(date)
    fun dateShort(i: Instant, z: ZoneId): String = i.atZone(z).format(dateShort)
    fun time(i: Instant, z: ZoneId): String = i.atZone(z).format(time).lowercase(Locale.ENGLISH)
    fun monthYear(i: Instant, z: ZoneId): String = i.atZone(z).format(monthYear)

    /** "in 3 days", "in 5 months", "in 2 years 4 months" */
    fun until(from: Instant, to: Instant): String {
        val days = Duration.between(from, to).toDays()
        return when {
            days <= 0 -> "today"
            days == 1L -> "tomorrow"
            days < 45 -> "in $days days"
            days < 730 -> "in ${(days / 30.44).toInt()} months"
            else -> { val y = (days / 365.25).toInt(); val m = ((days - y * 365.25) / 30.44).toInt(); if (m > 0) "in $y years $m months" else "in $y years" }
        }
    }

    fun degree(lon: Double): String {
        val inSign = Ephemeris.norm360(lon) % 30.0
        val d = inSign.toInt(); val m = ((inSign - d) * 60).toInt()
        return "$d°${"%02d".format(m)}′"
    }

    fun sign(r: Rashi) = "${r.sanskrit} (${r.english})"
    fun abbrev(g: Graha) = when (g) {
        Graha.SUN -> "Su"; Graha.MOON -> "Mo"; Graha.MARS -> "Ma"; Graha.MERCURY -> "Me"; Graha.JUPITER -> "Ju"
        Graha.VENUS -> "Ve"; Graha.SATURN -> "Sa"; Graha.RAHU -> "Ra"; Graha.KETU -> "Ke"
    }
}
