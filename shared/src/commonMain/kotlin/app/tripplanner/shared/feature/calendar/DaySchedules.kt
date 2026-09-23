package app.tripplanner.shared.feature.calendar

import app.tripplanner.schedule.DayHours
import app.tripplanner.schedule.DaySchedule
import app.tripplanner.schedule.EntryInput
import app.tripplanner.schedule.EntryKind
import app.tripplanner.schedule.Schedule
import app.tripplanner.schedule.TravelLookup
import app.tripplanner.shared.core.model.DayHoursDoc
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.core.model.TravelLeg
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.feature.trips.TripDays
import app.tripplanner.schedule.TravelMode as EngineMode
import app.tripplanner.shared.core.model.TravelMode as StoredMode

/**
 * Bridges Firestore documents to the schedule engine (design v1.1 §8.4 step 1): every device
 * runs the same function on the same data, so every member sees the same times.
 */
object DaySchedules {
    /**
     * Complexity:
     * - **Time:** O(S) for the S stops of the day plus O(1) per travel lookup.
     * - **Space:** O(S).
     */
    fun compute(trip: Trip, day: Int, stops: List<Stop>, legs: Map<Triple<String, String, StoredMode>, TravelLeg>, override: DayHoursDoc?): DaySchedule? {
        val date = TripDays.date(trip, day) ?: return null
        val hours = DayHours(
            start = Schedule.parseTime(override?.start) ?: Schedule.parseTime(trip.defaultDayStart) ?: Schedule.DEFAULT_START,
            end = Schedule.parseTime(override?.end) ?: Schedule.parseTime(trip.defaultDayEnd) ?: Schedule.DEFAULT_END,
        )
        val travel = TravelLookup { from, to, mode -> legs[Triple(from, to, toStored(mode))]?.takeIf { it.ok }?.seconds }
        return Schedule.computeDay(date, hours, stops.map(::toInput), travel, mode(trip.defaultTravelMode))
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun toInput(stop: Stop): EntryInput = EntryInput(
        id = stop.id,
        name = stop.name,
        kind = if (stop.kind == Stop.KIND_CUSTOM) EntryKind.CUSTOM else EntryKind.PLACE,
        durationMin = stop.durationMin,
        fixedStart = Schedule.parseTime(stop.fixedStart),
        modeToNext = stop.modeToNext?.let(::mode),
    )

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun mode(stored: String?): EngineMode = if (stored.equals("walking", ignoreCase = true)) EngineMode.WALKING else EngineMode.DRIVING

    private fun toStored(mode: EngineMode): StoredMode = if (mode == EngineMode.WALKING) StoredMode.WALK else StoredMode.DRIVE
}
