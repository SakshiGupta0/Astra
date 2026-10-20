// ASTRA core test suite. Runs standalone (kotlinc harness) and from JUnit in CI.
package app.astra.core

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class CoreSuite(private val fixtures: File) {
    private var passed = 0
    private val failures = ArrayList<String>()
    private val notes = ArrayList<String>()

    private fun check(name: String, ok: Boolean, detail: () -> String = { "" }) {
        if (ok) passed++ else failures += "$name ${detail()}"
    }

    private fun tsv(name: String): List<Map<String, String>> {
        val lines = File(fixtures, name).readLines().filter { it.isNotBlank() }
        val head = lines.first().split("\t")
        return lines.drop(1).map { l -> head.zip(l.split("\t")).toMap() }
    }

    fun run(): Boolean {
        natalFixtures()
        dashaMath()
        birthTimeStates()
        timeZones()
        trackerFixtures()
        panchangFixtures()
        toneRule()
        copyRules()
        println("\nASTRA core ${CoreVersion.CORE} | ${CoreVersion.CALC}")
        notes.forEach { println("  note: $it") }
        println("PASSED $passed  FAILED ${failures.size}")
        failures.forEach { println("  FAIL $it") }
        return failures.isEmpty()
    }

    // ---- 1. Natal charts vs Swiss Ephemeris (Lahiri) --------------------------------------------
    private fun natalFixtures() {
        val tol = 0.05 // degrees (PRD acceptance)
        var maxErr = 0.0; var maxAsc = 0.0
        for (r in tsv("natal.tsv")) {
            val (d, t) = r.getValue("local").split(" ")
            val input = BirthInput(LocalDate.parse(d), LocalTime.parse(t), BirthTimeStatus.KNOWN, 0,
                r.getValue("lat").toDouble(), r.getValue("lon").toDouble(), r.getValue("zone"))
            val c = ChartCalculator.compute(input)
            check("${r["id"]} utc", c.instant == Instant.parse(r.getValue("utc"))) { "${c.instant} vs ${r["utc"]}" }
            val keys = mapOf(Graha.SUN to "sun", Graha.MOON to "moon", Graha.MERCURY to "mercury", Graha.VENUS to "venus",
                Graha.MARS to "mars", Graha.JUPITER to "jupiter", Graha.SATURN to "saturn", Graha.RAHU to "rahu")
            for ((g, k) in keys) {
                val ref = r.getValue(k).toDouble()
                val err = abs(Ephemeris.diff(c.placement(g).lon, ref))
                maxErr = maxOf(maxErr, err)
                check("${r["id"]} ${g.english}", err < tol) { "err=${"%.4f".format(err)}° ours=${c.placement(g).lon} ref=$ref" }
                if (minOf(ref % 30, 30 - ref % 30) > tol) check("${r["id"]} ${g.english} sign", c.placement(g).rashi == Rashi.of(ref))
                if (minOf(ref % Nakshatra.SPAN, Nakshatra.SPAN - ref % Nakshatra.SPAN) > tol)
                    check("${r["id"]} ${g.english} nakshatra", c.placement(g).nakshatra == Nakshatra.of(ref))
            }
            val ketuErr = abs(Ephemeris.diff(c.placement(Graha.KETU).lon, r.getValue("rahu").toDouble() + 180))
            check("${r["id"]} Ketu opposite Rahu", ketuErr < tol)
            val asc = r.getValue("asc").toDouble()
            val aErr = abs(Ephemeris.diff(c.lagnaLon!!, asc))
            maxAsc = maxOf(maxAsc, aErr)
            check("${r["id"]} Lagna", aErr < tol) { "err=${"%.4f".format(aErr)}° ours=${c.lagnaLon} ref=$asc" }
            if (minOf(asc % 30, 30 - asc % 30) > tol) check("${r["id"]} Lagna sign", c.lagnaRashi == Rashi.of(asc))
            check("${r["id"]} houses whole-sign", c.placements.all { it.houseFromLagna == Houses.fromLon(it.lon, c.lagnaLon) })
        }
        notes += "natal: max graha error ${"%.2f".format(maxErr * 3600)} arcsec, max Lagna error ${"%.2f".format(maxAsc * 3600)} arcsec over 30 charts"
    }

    // ---- 2. Vimshottari arithmetic -------------------------------------------------------------
    private fun dashaMath() {
        val birth = Instant.parse("2000-01-01T00:00:00Z")
        val d0 = DashaTimeline(birth, 0.0)
        check("dasha: Moon at 0° starts full Ketu", d0.firstLord == Graha.KETU && abs(d0.balanceYears - 7.0) < 1e-9)
        val d1 = DashaTimeline(birth, 10.0) // Ashwini, 75% traversed
        check("dasha: balance at 10° Mesha", abs(d1.balanceYears - 1.75) < 1e-9) { "${d1.balanceYears}" }
        val firstEnd = d1.mahadashas[0].end
        val expected = DashaTimeline.plusYears(birth, 1.75)
        check("dasha: first maha ends after balance", abs(ChronoUnit.MINUTES.between(firstEnd, expected)) <= 1)
        check("dasha: second maha Venus", d1.mahadashas[1].lord == Graha.VENUS)
        val d2 = DashaTimeline(birth, 125.0) // Magha (Ketu)
        check("dasha: Magha → Ketu", d2.firstLord == Graha.KETU)
        val d3 = DashaTimeline(birth, 359.9) // Revati → Mercury
        check("dasha: Revati → Mercury", d3.firstLord == Graha.MERCURY)
        for (m in d1.mahadashas.take(9)) {
            val subs = d1.subPeriods(m)
            check("dasha: antars of ${m.lord} start with own lord", subs.first().lord == m.lord)
            check("dasha: antars of ${m.lord} tile the maha", subs.first().start == m.start && subs.last().end == m.end &&
                subs.zipWithNext().all { (a, b) -> a.end == b.start })
            val pr = d1.subPeriods(subs[3])
            check("dasha: pratyantars tile", pr.first().start == subs[3].start && pr.last().end == subs[3].end)
        }
        val total = ChronoUnit.DAYS.between(d1.mahadashas[0].start, d1.mahadashas[8].end)
        check("dasha: 9 mahas = 120 years", abs(total - 120 * 365.25) <= 1) { "$total" }
        val now = d1.at(Instant.parse("2030-06-01T00:00:00Z"))
        check("dasha: at() returns nested periods", now != null && now.maha.contains(Instant.parse("2030-06-01T00:00:00Z")) &&
            now.antar.parentLords == listOf(now.maha.lord) && now.pratyantar.parentLords == listOf(now.maha.lord, now.antar.lord))
    }

    // ---- 3. Birth-time states ----------------------------------------------------------------
    private fun birthTimeStates() {
        val base = BirthInput(LocalDate.of(1990, 6, 15), null, BirthTimeStatus.UNKNOWN, 0, 12.9716, 77.5946, "Asia/Kolkata")
        val u = ChartCalculator.compute(base)
        check("unknown: no Lagna", u.lagnaLon == null && u.lagnaStatus == LagnaStatus.UNAVAILABLE)
        check("unknown: no houses from Lagna", u.placements.all { it.houseFromLagna == null })
        check("unknown: warning shown", u.warnings.any { it.contains("Birth time unknown") })

        // find a day when the Moon changes nakshatra mid-day → nakshatra must be flagged uncertain
        var day = LocalDate.of(1990, 6, 1); var found: ChartSnapshot? = null
        while (found == null && day.isBefore(LocalDate.of(1990, 7, 1))) {
            val c = ChartCalculator.compute(base.copy(date = day))
            if (!c.moonNakshatraCertain) found = c
            day = day.plusDays(1)
        }
        check("unknown: nakshatra change detected in a month", found != null)
        check("unknown: dasha flagged uncertain", found != null && (found.dashaLordUncertain || found.dashaUncertaintyDays > 0))

        // approximate: scan times until a ±120 window spans two Lagnas, and a ±30 window does not
        var spanning: ChartSnapshot? = null; var single: ChartSnapshot? = null
        for (h in 0 until 24) {
            val wide = ChartCalculator.compute(base.copy(time = LocalTime.of(h, 0), timeStatus = BirthTimeStatus.APPROXIMATE, windowMinutes = 120))
            if (wide.lagnaStatus == LagnaStatus.UNCERTAIN && spanning == null) spanning = wide
            val narrow = ChartCalculator.compute(base.copy(time = LocalTime.of(h, 0), timeStatus = BirthTimeStatus.APPROXIMATE, windowMinutes = 30))
            if (narrow.lagnaStatus == LagnaStatus.APPROXIMATE && single == null) single = narrow
        }
        check("approx ±120: Lagna uncertain with ≥2 candidates and no houses",
            spanning != null && spanning.lagnaCandidates.size >= 2 && spanning.lagnaLon == null && spanning.placements.all { it.houseFromLagna == null })
        check("approx ±30: single Lagna labelled approximate", single != null && single.lagnaCandidates.size == 1 && single.lagnaLon != null)
        check("approx: dasha uncertainty reported", single != null && single.dashaUncertaintyDays > 0)
        try { BirthInput(LocalDate.of(1990, 1, 1), LocalTime.NOON, BirthTimeStatus.APPROXIMATE, 45, 0.0, 0.0, "UTC"); check("approx: invalid window rejected", false) }
        catch (e: IllegalArgumentException) { check("approx: invalid window rejected", true) }
    }

    // ---- 4. Time zones -------------------------------------------------------------------------
    private fun timeZones() {
        val gap = ChartCalculator.compute(BirthInput(LocalDate.of(2021, 3, 14), LocalTime.of(2, 30), BirthTimeStatus.KNOWN, 0, 40.7128, -74.006, "America/New_York"))
        check("tz: DST gap warned", gap.warnings.any { it.contains("skipped") })
        val old = ChartCalculator.compute(BirthInput(LocalDate.of(1890, 5, 1), LocalTime.of(10, 0), BirthTimeStatus.KNOWN, 0, 28.6139, 77.209, "Asia/Kolkata"))
        check("tz: pre-standard local time warned", old.warnings.any { it.contains("local time offset") })
        val war = ChartCalculator.compute(BirthInput(LocalDate.of(1943, 7, 10), LocalTime.of(14, 30), BirthTimeStatus.KNOWN, 0, 22.5726, 88.3639, "Asia/Kolkata"))
        check("tz: 1943 war time is UTC+6:30", war.instant == Instant.parse("1943-07-10T08:00:00Z"))
        val ov = ChartCalculator.compute(BirthInput(LocalDate.of(1890, 5, 1), LocalTime.of(10, 0), BirthTimeStatus.KNOWN, 0, 28.6139, 77.209, "Asia/Kolkata", utcOffsetOverrideMinutes = 330))
        check("tz: manual offset override", ov.instant == Instant.parse("1890-05-01T04:30:00Z"))
    }

    // ---- 5. Tracker: ingresses and stations vs reference ----------------------------------------
    private fun trackerFixtures() {
        val chart = ChartCalculator.compute(BirthInput(LocalDate.of(1995, 2, 14), LocalTime.of(11, 5), BirthTimeStatus.KNOWN, 0, 12.9716, 77.5946, "Asia/Kolkata"))
        val from = Instant.parse("2026-10-01T00:00:00Z"); val to = Instant.parse("2028-12-29T00:00:00Z")
        val t0 = System.currentTimeMillis()
        val tl = Tracker(chart).timeline(from, to)
        val ms = System.currentTimeMillis() - t0
        notes += "tracker: 27-month timeline computed in $ms ms on JVM (${tl.events.size} events)"
        val names = mapOf("SUN" to Graha.SUN, "MARS" to Graha.MARS, "MERCURY" to Graha.MERCURY, "JUPITER" to Graha.JUPITER,
            "VENUS" to Graha.VENUS, "SATURN" to Graha.SATURN, "RAHU" to Graha.RAHU)
        val ing = tsv("ingress.tsv")
        for ((key, g) in names) {
            val refs = ing.filter { it["graha"] == key }
            val ours = tl.signIntervals.getValue(g).drop(1)
            check("ingress count $key", refs.size == ours.size) { "ref=${refs.size} ours=${ours.size}" }
            for (r in refs) {
                val day = LocalDate.parse(r.getValue("day_before_utc")).atStartOfDay(ZoneId.of("UTC")).toInstant()
                val hit = ours.any { !it.start.isBefore(day.minus(1, ChronoUnit.DAYS)) && it.start.isBefore(day.plus(2, ChronoUnit.DAYS)) &&
                    it.rashi.ordinal == r.getValue("to_sign").toInt() }
                check("ingress $key ${r["day_before_utc"]} → ${r["to_sign"]}", hit)
            }
        }
        val st = tsv("stations.tsv")
        for (g in listOf(Graha.MERCURY, Graha.VENUS, Graha.MARS, Graha.JUPITER, Graha.SATURN)) {
            val refs = st.filter { it["graha"] == g.name }
            val ours = tl.retrogrades.filter { it.graha == g }
            val oursPoints = ours.flatMap { listOfNotNull(if (it.start.isAfter(from)) it.start to "RETRO" else null, if (it.end.isBefore(to)) it.end to "DIRECT" else null) }
            check("stations count ${g.name}", refs.size == oursPoints.size) { "ref=${refs.size} ours=${oursPoints.size}" }
            for (r in refs) {
                val day = LocalDate.parse(r.getValue("day_before_utc")).atStartOfDay(ZoneId.of("UTC")).toInstant()
                check("station ${g.name} ${r["day_before_utc"]} ${r["kind"]}", oursPoints.any { (t, k) ->
                    k == r["kind"] && !t.isBefore(day.minus(1, ChronoUnit.DAYS)) && t.isBefore(day.plus(2, ChronoUnit.DAYS)) })
            }
        }
        // structure checks
        check("tracker: tone segments tile the window", tl.tone.first().start == from && tl.tone.last().end == to &&
            tl.tone.zipWithNext().all { (a, b) -> a.end == b.start })
        check("tracker: Ketu always opposite Rahu", tl.signIntervals.getValue(Graha.KETU).zip(tl.signIntervals.getValue(Graha.RAHU))
            .all { (k, r) -> (k.rashi.ordinal - r.rashi.ordinal + 12) % 12 == 6 })
        check("tracker: events sorted", tl.events.zipWithNext().all { (a, b) -> !b.instant.isBefore(a.instant) })
        check("tracker: no tone-change event without a change", tl.events.filter { it.type == EventType.TONE_CHANGE }.size ==
            tl.tone.zipWithNext().count { (a, b) -> a.tone != b.tone })
        val now = Tracker(chart).now(Instant.parse("2026-10-20T06:00:00Z"))
        check("tracker now: 9 grahas with next sign change", now.size == 9 && now.filter { it.graha != Graha.KETU }.all { it.nextSignChange != null })
        check("tracker now: Lagna houses present when time known", now.all { it.houseFromLagna != null })
        val ketuNow = now.first { it.graha == Graha.KETU }; val rahuNow = now.first { it.graha == Graha.RAHU }
        check("tracker now: Ketu next sign = Rahu's", ketuNow.nextSignChange != null && rahuNow.nextSignChange != null &&
            abs(ChronoUnit.MINUTES.between(ketuNow.nextSignChange, rahuNow.nextSignChange)) <= 2)
    }

    // ---- 6. Panchang vs reference ------------------------------------------------------------
    private fun panchangFixtures() {
        var maxRise = 0L
        for (r in tsv("panchang.tsv")) {
            val p = PanchangCalculator.compute(LocalDate.parse(r.getValue("date")), r.getValue("lat").toDouble(), r.getValue("lon").toDouble(), r.getValue("zone"))
            val ref = Instant.parse(r.getValue("sunrise_utc"))
            val dr = abs(ChronoUnit.SECONDS.between(p.sunrise!!, ref)); maxRise = maxOf(maxRise, dr)
            check("panchang ${r["date"]} ${r["place"]} sunrise", dr <= 120) { "Δ=${dr}s" }
            val ds = abs(ChronoUnit.SECONDS.between(p.sunset!!, Instant.parse(r.getValue("sunset_utc"))))
            check("panchang ${r["date"]} ${r["place"]} sunset", ds <= 120) { "Δ=${ds}s" }
            fun nearBoundary(end: Instant?) = end != null && ChronoUnit.MINUTES.between(p.sunrise, end) < 5
            if (!nearBoundary(p.tithi!!.endsAt)) check("panchang ${r["date"]} tithi", p.tithi.value.index == r.getValue("tithi").toInt()) { "${p.tithi.value.index} vs ${r["tithi"]}" }
            if (!nearBoundary(p.nakshatra!!.endsAt)) check("panchang ${r["date"]} nakshatra", p.nakshatra.value.ordinal == r.getValue("nakshatra").toInt())
            if (!nearBoundary(p.yoga!!.endsAt)) check("panchang ${r["date"]} yoga", PanchangCalculator.YOGAS.indexOf(p.yoga.value) == r.getValue("yoga").toInt())
            check("panchang ${r["date"]} Rahu Kaal inside daytime", p.rahuKaalStart!!.isAfter(p.sunrise.minusSeconds(1)) && !p.rahuKaalEnd!!.isAfter(p.sunset))
            check("panchang ${r["date"]} limbs end after sunrise", listOf(p.tithi.endsAt, p.nakshatra.endsAt, p.yoga.endsAt).all { it != null && it.isAfter(p.sunrise) })
        }
        notes += "panchang: max sunrise difference ${maxRise}s over 12 date-city pairs"
        val polar = PanchangCalculator.compute(LocalDate.of(2026, 12, 21), 69.6492, 18.9553, "Europe/Oslo")
        check("panchang: polar night handled", polar.sunrise == null && polar.tithi == null && polar.note != null)
        val p = PanchangCalculator.compute(LocalDate.of(2026, 11, 2), 12.9716, 77.5946, "Asia/Kolkata") // Monday
        val len = java.time.Duration.between(p.sunrise, p.sunset).toMillis()
        check("panchang: Monday Rahu Kaal is 2nd eighth", p.rahuKaalStart == p.sunrise!!.plusMillis(len / 8))
        check("tithi naming", Tithi(14).name == "Purnima" && Tithi(29).name == "Amavasya" && Tithi(15).label == "Krishna Pratipada")
    }

    // ---- 7. Tone rule ------------------------------------------------------------------------
    private fun toneRule() {
        check("tone: J11 S3 supportive", Gochar.tone(11, 3) == Tone.SUPPORTIVE)
        check("tone: J3 S1 demanding (Sade Sati peak)", Gochar.tone(3, 1) == Tone.DEMANDING)
        check("tone: J11 S1 mixed", Gochar.tone(11, 1) == Tone.MIXED)
        check("tone: J1 S5 mixed", Gochar.tone(1, 5) == Tone.MIXED)
        check("tone: J8 S8 demanding (Ashtama)", Gochar.tone(8, 8) == Tone.DEMANDING)
        check("tone: J5 S4 mixed (Ardhashtama with Jupiter support)", Gochar.tone(5, 4) == Tone.MIXED)
        for (j in 1..12) for (s in 1..12) check("tone total $j/$s", Gochar.tone(j, s) in Tone.entries)
        check("ordinal", ordinal(1) == "1st" && ordinal(2) == "2nd" && ordinal(3) == "3rd" && ordinal(11) == "11th" && ordinal(12) == "12th")
    }

    // ---- 8. Copy rules: no fear words in any generated text ----------------------------------
    private fun copyRules() {
        val banned = Regex("\\b(good|bad|unlucky|lucky|danger|dangerous|dosha|curse|doom|warning)\\b", RegexOption.IGNORE_CASE)
        val chart = ChartCalculator.compute(BirthInput(LocalDate.of(1989, 11, 9), LocalTime.of(21, 10), BirthTimeStatus.KNOWN, 0, 26.1445, 91.7362, "Asia/Kolkata"))
        val tl = Tracker(chart).timeline(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2029-10-01T00:00:00Z"))
        val texts = tl.events.flatMap { listOf(it.title, it.detail) } + tl.tone.flatMap { it.factors } +
            SaturnPhase.entries.map { it.label } + Tone.entries.map { it.label } +
            (1..12).flatMap { j -> (1..12).flatMap { s -> Gochar.factors(j, s) } }
        val bad = texts.filter { banned.containsMatchIn(it) }
        check("copy: no banned words in tracker text (${texts.size} strings)", bad.isEmpty()) { bad.take(3).toString() }
    }
}

fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "core/src/test/resources/fixtures")
    val ok = CoreSuite(dir).run()
    if (!ok) kotlin.system.exitProcess(1)
}
