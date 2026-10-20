// Computes everything the screens show from the profile. All calculation is on-device and deterministic (ASTRA core).
package app.astra.ui

import app.astra.core.ChartCalculator
import app.astra.core.ChartSnapshot
import app.astra.core.NowPosition
import app.astra.core.Panchang
import app.astra.core.PanchangCalculator
import app.astra.core.Profile
import app.astra.core.Timeline
import app.astra.core.Tracker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class Session(val profile: Profile, val at: Instant = Instant.now()) {
    val zone: ZoneId = ZoneId.of(profile.currentPlace.zoneId)
    val chart: ChartSnapshot = ChartCalculator.compute(profile.birthInput())
    val tracker = Tracker(chart)
    val today: LocalDate = at.atZone(zone).toLocalDate()

    // Heavier results are computed once per session, on a background thread (see produceState callers).
    val positions: List<NowPosition> by lazy { now(at) }
    val timelineNow: Timeline by lazy { timeline(at) }
    val panchangToday: Panchang by lazy { panchang(today) }

    fun now(at: Instant): List<NowPosition> = tracker.now(at)

    /** 12 months back to 24 months ahead, starting at the first of the month. */
    fun timeline(at: Instant): Timeline {
        val start = ZonedDateTime.ofInstant(at, zone).withDayOfMonth(1).toLocalDate().atStartOfDay(zone).minusMonths(12).toInstant()
        val end = ZonedDateTime.ofInstant(at, zone).withDayOfMonth(1).toLocalDate().atStartOfDay(zone).plusMonths(25).toInstant()
        return tracker.timeline(start, end)
    }

    fun panchang(date: LocalDate): Panchang =
        PanchangCalculator.compute(date, profile.currentPlace.latitude, profile.currentPlace.longitude, profile.currentPlace.zoneId)
}
