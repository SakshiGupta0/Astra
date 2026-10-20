// Prints a human-readable chart, dasha, tracker and panchang report for spot-checking
// against another tool (e.g. Jagannatha Hora). Not shipped in the app.
// Usage: Demo YYYY-MM-DD HH:MM|unknown lat lon zone [today YYYY-MM-DD]
package app.astra.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Demo {
    private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val fmtT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

    fun report(input: BirthInput, today: LocalDate, cityLat: Double, cityLon: Double, cityZone: String): String {
        val z = ZoneId.of(cityZone)
        fun d(i: Instant) = i.atZone(z).format(fmt)
        fun dt(i: Instant) = i.atZone(z).format(fmtT)
        val c = ChartCalculator.compute(input)
        val sb = StringBuilder()
        sb.appendLine("BIRTH  ${input.date} ${input.time ?: "time unknown"} (${input.timeStatus}) at ${input.latitude}, ${input.longitude} ${input.zoneId}")
        sb.appendLine("UTC    ${c.instant}    method: ${c.calcVersion}")
        c.warnings.forEach { sb.appendLine("NOTE   $it") }
        sb.appendLine()
        sb.appendLine("LAGNA  " + when (c.lagnaStatus) {
            LagnaStatus.EXACT -> formatSidereal(c.lagnaLon!!)
            LagnaStatus.APPROXIMATE -> formatSidereal(c.lagnaLon!!) + " (approximate)"
            LagnaStatus.UNCERTAIN -> "uncertain: " + c.lagnaCandidates.joinToString(" or ") { it.sanskrit }
            LagnaStatus.UNAVAILABLE -> "not available (birth time unknown)"
        })
        sb.appendLine("%-9s %-18s %-20s %-4s %-5s %s".format("Graha", "Position", "Nakshatra", "Pada", "House", "From Moon"))
        for (p in c.placements) {
            sb.appendLine("%-9s %-18s %-20s %-4d %-5s %s".format(
                p.graha.english + if (p.retrograde && p.graha !in listOf(Graha.RAHU, Graha.KETU)) " (R)" else "",
                formatSidereal(p.lon), p.nakshatra.title, p.pada, p.houseFromLagna?.toString() ?: "–", p.houseFromMoon))
        }
        sb.appendLine()
        val now = Instant.now().let { if (today == LocalDate.now(z)) it else today.atTime(9, 0).atZone(z).toInstant() }
        val dn = c.dasha.at(now)
        sb.appendLine("DASHA  born in ${c.dasha.birthNakshatra.title} → ${c.dasha.firstLord.english} mahadasha, balance ${"%.2f".format(c.dasha.balanceYears)} years" +
            if (c.dashaUncertaintyDays > 0) " (boundaries ±${"%.0f".format(c.dashaUncertaintyDays)} days)" else "")
        if (dn != null) {
            sb.appendLine("  now: ${dn.maha.lord.english} / ${dn.antar.lord.english} / ${dn.pratyantar.lord.english}")
            sb.appendLine("  maha ${d(dn.maha.start)} – ${d(dn.maha.end)};  antar ${d(dn.antar.start)} – ${d(dn.antar.end)};  pratyantar until ${d(dn.pratyantar.end)}")
        }
        c.dasha.mahadashas.take(9).forEach { sb.appendLine("  ${it.lord.english.padEnd(8)} ${d(it.start)} – ${d(it.end)}") }
        sb.appendLine()
        val tr = Tracker(c)
        sb.appendLine("TRACKER NOW (${d(now)})")
        for (p in tr.now(now)) {
            sb.appendLine("  %-8s %-16s %-17s %-3s %-5s %s".format(p.graha.english, formatSidereal(p.lon), p.nakshatra.title,
                if (p.retrograde && p.graha !in listOf(Graha.RAHU, Graha.KETU)) "R" else "",
                ordinal(p.houseFromMoon) + if (p.favourableFromMoon) "*" else "",
                p.nextSignChange?.let { "→ ${p.nextRashi?.sanskrit} on ${d(it)}" } ?: ""))
        }
        sb.appendLine("  (* = classically favourable house from Moon)")
        val tl = tr.timeline(now, now.plusSeconds(365L * 86400 * 2))
        sb.appendLine()
        sb.appendLine("PERIOD TONE (${Gochar.RULE_VERSION})")
        tl.tone.forEach { sb.appendLine("  ${d(it.start)} – ${d(it.end)}  ${it.tone.label.padEnd(10)} ${it.factors.joinToString("; ")}") }
        sb.appendLine("SATURN")
        tl.saturn.forEach { sb.appendLine("  ${d(it.start)} – ${d(it.end)}  ${it.phase.label} (house ${it.house})") }
        sb.appendLine()
        sb.appendLine("MAJOR EVENTS, NEXT 24 MONTHS")
        tl.events.filter { it.major }.forEach { sb.appendLine("  ${d(it.instant).padEnd(12)} ${it.title} — ${it.detail}") }
        sb.appendLine()
        val p = PanchangCalculator.compute(today, cityLat, cityLon, cityZone)
        sb.appendLine("PANCHANG ${today} (${p.vara}) at $cityLat, $cityLon")
        if (p.sunrise == null) sb.appendLine("  ${p.note}") else {
            sb.appendLine("  Sunrise ${dt(p.sunrise)}   Sunset ${dt(p.sunset!!)}")
            sb.appendLine("  Tithi     ${p.tithi!!.value.label} until ${p.tithi.endsAt?.let { dt(it) }}")
            sb.appendLine("  Nakshatra ${p.nakshatra!!.value.title} until ${p.nakshatra.endsAt?.let { dt(it) }}")
            sb.appendLine("  Yoga      ${p.yoga!!.value} until ${p.yoga.endsAt?.let { dt(it) }}")
            sb.appendLine("  Rahu Kaal ${dt(p.rahuKaalStart!!)} – ${dt(p.rahuKaalEnd!!)}")
        }
        return sb.toString()
    }
}

object DemoMain {
    @JvmStatic fun main(args: Array<String>) {
    val a = if (args.size >= 5) args else arrayOf("1947-08-15", "00:00", "28.6139", "77.2090", "Asia/Kolkata")
    val unknown = a[1] == "unknown"
    val input = BirthInput(LocalDate.parse(a[0]), if (unknown) null else LocalTime.parse(a[1]),
        if (unknown) BirthTimeStatus.UNKNOWN else BirthTimeStatus.KNOWN, 0, a[2].toDouble(), a[3].toDouble(), a[4])
    val today = if (a.size >= 6) LocalDate.parse(a[5]) else LocalDate.now(ZoneId.of(a[4]))
    print(Demo.report(input, today, 12.9716, 77.5946, "Asia/Kolkata"))
}
}
