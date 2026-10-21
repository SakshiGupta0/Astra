// ASTRA core — what to notify on a given day (PRD F-08). Opt-in, capped, quiet hours, never fear-based.
// The app runs this once a day at the user's chosen time and posts the result.
package app.astra.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

data class NotifyPrefs(
    val daily: Boolean = true,
    val weekly: Boolean = true,
    val events: Boolean = true,
    /** Alerts when the period tone turns demanding: separate opt-in, off by default. */
    val demandingAlerts: Boolean = false,
    /** Follow-ups on open questions in My Story. */
    val checkins: Boolean = true,
    val time: LocalTime = LocalTime.of(7, 30),
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(7, 0)
)

enum class NotifyKind { DAILY, WEEKLY, EVENT, CHECKIN }

data class PlannedNotice(val kind: NotifyKind, val key: String, val title: String, val body: String, val priority: Int = 0)

object NotifyPlanner {
    const val MAX_EVENT_ALERTS_PER_WEEK = 2
    val DAYS_BEFORE = listOf(7, 0)

    /** The user's chosen time, moved out of quiet hours if needed. */
    fun fireTime(p: NotifyPrefs): LocalTime {
        val t = p.time
        val inQuiet = if (p.quietStart > p.quietEnd) (t >= p.quietStart || t < p.quietEnd) else (t >= p.quietStart && t < p.quietEnd)
        return if (inQuiet) p.quietEnd else t
    }

    fun weekKey(date: LocalDate) = "${date.get(IsoFields.WEEK_BASED_YEAR)}-W${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

    private fun eventPriority(e: TransitEvent): Int = when (e.type) {
        EventType.MAHADASHA_CHANGE -> 100
        EventType.ANTARDASHA_CHANGE -> 80
        EventType.TONE_CHANGE -> 70
        EventType.INGRESS -> if (e.graha == Graha.SATURN) 60 else 50
        EventType.NODES_INGRESS -> 40
        EventType.RETRO_START, EventType.RETRO_END -> 20
    }

    /**
     * Notices to post on [date].
     * @param alreadySentEventKeys event-alert keys already posted (so nothing repeats)
     * @param eventAlertsThisWeek how many event alerts were posted earlier this ISO week
     */
    fun forDay(
        date: LocalDate, zone: ZoneId, prefs: NotifyPrefs, timeline: Timeline, panchang: Panchang?,
        moonHouseFromMoon: Int?, alreadySentEventKeys: Set<String>, eventAlertsThisWeek: Int,
        /** Thread whose check-in started today (from StoryRules.rollCheckins). */
        checkin: StoryThread? = null
    ): List<PlannedNotice> {
        val out = ArrayList<PlannedNotice>()
        val sunday = date.dayOfWeek == DayOfWeek.SUNDAY
        // One daily nudge: the week-ahead replaces the daily note on Sundays.
        if (prefs.weekly && sunday) weekAhead(date, zone, timeline)?.let { out += it }
        else if (prefs.daily) daily(date, zone, panchang, moonHouseFromMoon)?.let { out += it }

        if (prefs.events) {
            val candidates = ArrayList<Pair<PlannedNotice, Int>>()
            for (e in timeline.events) {
                if (!e.major) continue
                if (e.type == EventType.TONE_CHANGE && e.title.contains("demanding") && !prefs.demandingAlerts) continue
                if (e.type == EventType.RETRO_START || e.type == EventType.RETRO_END) if (e.graha != Graha.JUPITER && e.graha != Graha.SATURN) continue
                val day = e.instant.atZone(zone).toLocalDate()
                val daysBefore = ChronoUnit.DAYS.between(date, day).toInt()
                if (daysBefore !in DAYS_BEFORE) continue
                val key = "${e.type}:${e.graha}:$day:$daysBefore"
                if (key in alreadySentEventKeys) continue
                val title = if (daysBefore == 0) "Today: ${e.title}" else "In a week: ${e.title}"
                val body = "${e.detail}. Tap to see what it means for you."
                candidates += PlannedNotice(NotifyKind.EVENT, key, title, body, eventPriority(e)) to eventPriority(e)
            }
            val room = (MAX_EVENT_ALERTS_PER_WEEK - eventAlertsThisWeek).coerceAtLeast(0)
            out += candidates.sortedByDescending { it.second }.take(room).map { it.first }
        }
        if (prefs.checkins && checkin != null)
            out += PlannedNotice(NotifyKind.CHECKIN, "checkin:${checkin.id}:$date", "Any update?", "On your story: ${checkin.title}. Tap to add what's happened.")
        return out
    }

    private fun daily(date: LocalDate, zone: ZoneId, p: Panchang?, moonHouse: Int?): PlannedNotice? {
        val parts = ArrayList<String>()
        if (p?.tithi != null) parts += p.tithi.value.label
        if (moonHouse != null) parts += "Moon in your ${ordinal(moonHouse)}"
        if (p?.rahuKaalStart != null && p.rahuKaalEnd != null) {
            val f = { i: Instant -> i.atZone(zone).toLocalTime().let { t -> "${(t.hour + 11) % 12 + 1}:${"%02d".format(t.minute)}" } }
            parts += "Rahu Kaal ${f(p.rahuKaalStart)}–${f(p.rahuKaalEnd)}"
        }
        if (parts.isEmpty()) return null
        return PlannedNotice(NotifyKind.DAILY, "daily:$date", "Today", parts.joinToString(" · "))
    }

    private fun weekAhead(date: LocalDate, zone: ZoneId, tl: Timeline): PlannedNotice {
        val end = date.plusDays(7)
        val events = tl.events.filter { it.major }.filter {
            val d = it.instant.atZone(zone).toLocalDate(); !d.isBefore(date) && d.isBefore(end)
        }
        val body = when (events.size) {
            0 -> "A steady week in your chart. Open the tracker to see where you are."
            1 -> "${events[0].title} this week. Open the tracker for details."
            else -> "${events.size} changes in your chart this week, first: ${events[0].title}."
        }
        return PlannedNotice(NotifyKind.WEEKLY, "weekly:${weekKey(date)}", "Your week ahead", body)
    }
}
