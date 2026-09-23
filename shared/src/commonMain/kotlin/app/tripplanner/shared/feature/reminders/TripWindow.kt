package app.tripplanner.shared.feature.reminders

import app.tripplanner.schedule.Schedule
import app.tripplanner.shared.core.model.Trip
import kotlinx.datetime.TimeZone
import app.tripplanner.shared.feature.trips.TripDays
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Cheap date-range test so a sync only reads trips that could have entries in the window. */
object TripWindow {
    /**
     * True when any part of the trip's date range (whole days in the trip zone) falls between
     * [nowEpochSeconds] and [nowEpochSeconds] + [windowSeconds].
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun overlaps(trip: Trip, nowEpochSeconds: Long, windowSeconds: Long): Boolean {
        if (runCatching { TimeZone.of(trip.timeZone) }.isFailure) return false
        val first = TripDays.date(trip, 0) ?: return false
        val last = TripDays.date(trip, TripDays.count(trip) - 1) ?: return false
        val start = Schedule.epochSeconds(LocalDateTime(first, LocalTime(0, 0)), trip.timeZone)
        val end = Schedule.epochSeconds(LocalDateTime(last, LocalTime(23, 59)), trip.timeZone)
        return end >= nowEpochSeconds && start <= nowEpochSeconds + windowSeconds
    }
}
