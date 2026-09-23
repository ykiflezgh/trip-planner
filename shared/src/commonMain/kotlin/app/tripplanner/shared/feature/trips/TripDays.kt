package app.tripplanner.shared.feature.trips

import app.tripplanner.shared.core.model.Trip
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Day arithmetic over a trip's ISO dates (day index 0 = start date). */
object TripDays {
    /**
     * Complexity:
     * - **Time:** O(1) date parse + difference.
     * - **Space:** O(1).
     */
    fun count(trip: Trip): Int = runCatching {
        val start = LocalDate.parse(trip.startDate)
        val end = LocalDate.parse(trip.endDate)
        (start.daysUntil(end) + 1).coerceAtLeast(1)
    }.getOrDefault(1)

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun date(trip: Trip, day: Int): LocalDate? =
        runCatching { LocalDate.parse(trip.startDate).plus(day, DateTimeUnit.DAY) }.getOrNull()
}
