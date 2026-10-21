// ASTRA core — the calculated facts the AI is allowed to use (the DATA block). Same schema as eval/cases.mjs.
// The app writes this to Firestore; the AI gateway reads it from there, never from the request.
package app.astra.core

import java.time.Instant
import java.time.ZoneId

object Facts {
    const val SCHEMA = "facts-1"

    private fun d(i: Instant, z: ZoneId): String = i.atZone(z).toLocalDate().toString()

    fun build(chart: ChartSnapshot, timeline: Timeline, panchang: Panchang?, at: Instant, zone: ZoneId, language: ReplyLanguage): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["schema"] = SCHEMA
        out["calc_version"] = chart.calcVersion
        out["language"] = when (language) { ReplyLanguage.EN -> "en"; ReplyLanguage.HINGLISH -> "hinglish"; ReplyLanguage.HI -> "hi" }
        out["today"] = at.atZone(zone).toLocalDate().toString()
        out["birth_time_status"] = chart.input.timeStatus.name.lowercase()
        out["lagna"] = chart.lagnaRashi?.sanskrit
        out["lagna_status"] = when (chart.lagnaStatus) {
            LagnaStatus.EXACT -> "exact"; LagnaStatus.APPROXIMATE -> "approximate"
            LagnaStatus.UNCERTAIN -> "uncertain"; LagnaStatus.UNAVAILABLE -> "unavailable"
        }
        if (chart.lagnaStatus == LagnaStatus.UNCERTAIN) out["lagna_candidates"] = chart.lagnaCandidates.map { it.sanskrit }
        out["moon_sign_certain"] = chart.moonSignCertain
        out["placements"] = chart.placements.map { p ->
            linkedMapOf<String, Any?>("graha" to p.graha.english, "sign" to p.rashi.sanskrit, "degree" to Math.round(p.degreeInSign * 10) / 10.0).apply {
                p.houseFromLagna?.let { put("house_from_lagna", it) }
                put("house_from_moon", p.houseFromMoon)
                put("nakshatra", p.nakshatra.title)
                put("pada", p.pada)
                if (p.retrograde && p.graha != Graha.RAHU && p.graha != Graha.KETU) put("retrograde", true)
            }
        }
        val dn = chart.dasha.at(at)
        if (dn != null) {
            out["dasha_now"] = linkedMapOf<String, Any?>(
                "maha" to dn.maha.lord.english, "maha_start" to d(dn.maha.start, zone), "maha_end" to d(dn.maha.end, zone),
                "antar" to dn.antar.lord.english, "antar_start" to d(dn.antar.start, zone), "antar_end" to d(dn.antar.end, zone),
                "pratyantar" to dn.pratyantar.lord.english, "pratyantar_end" to d(dn.pratyantar.end, zone)
            ).apply {
                if (chart.dashaUncertaintyDays > 0) put("boundary_uncertainty_days", Math.round(chart.dashaUncertaintyDays))
                if (chart.dashaLordUncertain) put("sequence_uncertain", true)
            }
            val nextAntars = chart.dasha.subPeriods(dn.maha).filter { it.start.isAfter(at) }.take(2)
            val nextMaha = chart.dasha.mahadashas.firstOrNull { it.start.isAfter(at) }
            out["dasha_upcoming"] = nextAntars.map { mapOf("period" to "${dn.maha.lord.english}–${it.lord.english}", "starts" to d(it.start, zone)) } +
                listOfNotNull(nextMaha?.let { mapOf("period" to "${it.lord.english} mahadasha", "starts" to d(it.start, zone)) })
        }
        val tone = timeline.tone.firstOrNull { !at.isBefore(it.start) && at.isBefore(it.end) }
        val tracker = LinkedHashMap<String, Any?>()
        if (tone != null) {
            tracker["tone_now"] = tone.tone.label.lowercase()
            val next = timeline.tone.drop(timeline.tone.indexOf(tone) + 1).firstOrNull { it.tone != tone.tone }
            tracker["tone_until"] = next?.let { d(it.start, zone) }
            tracker["factors"] = tone.factors
        }
        val sat = timeline.saturn.firstOrNull { !at.isBefore(it.start) && at.isBefore(it.end) }
        tracker["saturn_phase"] = if (sat == null || sat.phase == SaturnPhase.OTHER) "none" else sat.phase.label
        sadeSati(timeline, at, zone)?.let { tracker["sade_sati"] = it }
        tracker["next_events"] = timeline.events.filter { it.major && it.instant.isAfter(at) }.take(6)
            .map { mapOf("date" to d(it.instant, zone), "event" to "${it.title} — ${it.detail}") }
        tracker["window"] = "${d(timeline.from, zone)} to ${d(timeline.to, zone)}"
        out["tracker"] = tracker
        if (panchang?.sunrise != null && panchang.tithi != null) {
            fun t(i: Instant?) = i?.atZone(zone)?.toLocalTime()?.withSecond(0)?.withNano(0)?.toString()
            out["panchang"] = linkedMapOf(
                "date" to panchang.date.toString(), "vara" to panchang.vara,
                "tithi" to panchang.tithi.value.label + (panchang.tithi.endsAt?.let { " until ${t(it)}" } ?: ""),
                "nakshatra" to (panchang.nakshatra?.value?.title ?: "") + (panchang.nakshatra?.endsAt?.let { " until ${t(it)}" } ?: ""),
                "yoga" to panchang.yoga?.value,
                "sunrise" to t(panchang.sunrise), "sunset" to t(panchang.sunset),
                "rahu_kaal" to "${t(panchang.rahuKaalStart)}–${t(panchang.rahuKaalEnd)}"
            )
        }
        out["support"] = mapOf("helpline" to "Tele-MANAS 14416 (India, free, 24x7)", "emergency" to "112")
        return out
    }

    /** Current Sade Sati span (start, peak end, end) if the user is in it or it starts within the window. */
    private fun sadeSati(tl: Timeline, at: Instant, zone: ZoneId): Map<String, String>? {
        val ss = setOf(SaturnPhase.SADE_SATI_RISING, SaturnPhase.SADE_SATI_PEAK, SaturnPhase.SADE_SATI_SETTING)
        val cur = tl.saturn.firstOrNull { !at.isBefore(it.start) && at.isBefore(it.end) } ?: return null
        if (cur.phase !in ss) return null
        val idx = tl.saturn.indexOf(cur)
        var a = idx; while (a > 0 && tl.saturn[a - 1].phase in ss) a--
        var b = idx; while (b < tl.saturn.lastIndex && tl.saturn[b + 1].phase in ss) b++
        val startKnown = a > 0 || tl.saturn[a].start.isAfter(tl.from)
        val endKnown = b < tl.saturn.lastIndex
        val peakEnd = tl.saturn.subList(a, b + 1).lastOrNull { it.phase == SaturnPhase.SADE_SATI_PEAK }?.end
        return linkedMapOf<String, String>().apply {
            put("started", if (startKnown) d(tl.saturn[a].start, zone) else "before ${d(tl.from, zone)}")
            peakEnd?.let { if (it.isBefore(tl.to)) put("peak_until", d(it, zone)) }
            put("ends", if (endKnown) d(tl.saturn[b].end, zone) else "after ${d(tl.to, zone)}")
        }
    }
}

/** A saved reading in the library (PRD F-11). */
data class Reading(
    val id: String,
    val type: String,          // "ask"
    val question: String,
    val answer: String,
    val storyItemsUsed: List<String>,
    val language: String,
    val model: String,
    val promptVersion: String,
    val createdAt: Instant
)

object ReadingCodec {
    fun encode(list: List<Reading>): String = Json.write(list.map {
        mapOf("id" to it.id, "type" to it.type, "q" to it.question, "a" to it.answer, "story" to it.storyItemsUsed,
            "lang" to it.language, "model" to it.model, "prompt" to it.promptVersion, "at" to it.createdAt.toString())
    })

    @Suppress("UNCHECKED_CAST")
    fun decode(text: String): List<Reading> = try {
        (Json.parse(text) as List<Map<String, Any?>>).map {
            Reading(it["id"] as String, it["type"] as String, it["q"] as String, it["a"] as String, (it["story"] as List<String>?) ?: emptyList(),
                (it["lang"] as String?) ?: "en", (it["model"] as String?) ?: "", (it["prompt"] as String?) ?: "", Instant.parse(it["at"] as String))
        }
    } catch (e: Exception) { emptyList() }
}

