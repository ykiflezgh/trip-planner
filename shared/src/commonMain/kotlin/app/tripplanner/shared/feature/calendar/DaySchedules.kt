package app.tripplanner.shared.feature.calendar

import app.tripplanner.schedule.DayHours
import app.tripplanner.schedule.DaySchedule
import app.tripplanner.schedule.EventInput
import app.tripplanner.schedule.Schedule
import app.tripplanner.schedule.TravelLookup
import app.tripplanner.shared.core.model.DayHoursDoc
import app.tripplanner.shared.core.model.Event
import app.tripplanner.shared.core.model.TravelLeg
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.feature.trips.TripDays
import app.tripplanner.schedule.TravelMode as EngineMode
import app.tripplanner.shared.core.model.TravelMode as StoredMode

/**
 * Bridges Firestore documents to the schedule engine (design v1.2 §8.4 step 1): every device
 * runs the same function on the same data, so every member sees the same times. A stop is
 * identified to the engine by its event's id, which is also how travel legs are keyed.
 */
object DaySchedules {
    /**
     * Complexity:
     * - **Time:** O(E) for the E events of the day plus O(1) per travel lookup.
     * - **Space:** O(E).
     */
    fun compute(trip: Trip, day: Int, events: List<Event>, legs: Map<Triple<String, String, StoredMode>, TravelLeg>, override: DayHoursDoc?): DaySchedule? {
        val date = TripDays.date(trip, day) ?: return null
        val hours = DayHours(
            start = Schedule.parseTime(override?.start) ?: Schedule.parseTime(trip.defaultDayStart) ?: Schedule.DEFAULT_START,
            end = Schedule.parseTime(override?.end) ?: Schedule.parseTime(trip.defaultDayEnd) ?: Schedule.DEFAULT_END,
        )
        val travel = TravelLookup { from, to, mode -> legs[Triple(from, to, toStored(mode))]?.takeIf { it.ok }?.seconds }
        return Schedule.computeDay(date, hours, events.map(::toInput), travel, mode(trip.defaultTravelMode))
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun toInput(event: Event): EventInput = EventInput(
        id = event.id,
        title = event.title,
        durationMin = event.durationMin,
        stopId = if (event.hasStop) event.id else null,
        fixedStart = Schedule.parseTime(event.fixedStart),
        modeToNext = event.modeToNext?.let(::mode),
    )

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun mode(stored: String?): EngineMode = if (stored.equals("walking", ignoreCase = true)) EngineMode.WALKING else EngineMode.DRIVING

    private fun toStored(mode: EngineMode): StoredMode = if (mode == EngineMode.WALKING) StoredMode.WALK else StoredMode.DRIVE
}
