package app.tripplanner.shared.feature.reminders

import app.tripplanner.schedule.Schedule
import app.tripplanner.shared.core.model.DayHoursDoc
import app.tripplanner.shared.core.model.ReminderPrefs
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.core.model.TravelLeg
import app.tripplanner.shared.core.model.TravelMode
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.feature.calendar.DaySchedules
import kotlinx.datetime.TimeZone
import app.tripplanner.shared.feature.trips.TripDays
import app.tripplanner.shared.platform.Reminder

/** Everything the planner needs about one trip, read once at sync time. */
data class TripData(
    val trip: Trip,
    val stops: List<Stop>,
    val legs: Map<Triple<String, String, TravelMode>, TravelLeg>,
    val dayHours: Map<Int, DayHoursDoc>,
)

/**
 * Pure planning for "time to leave" reminders (design v1.1 §8.5): for every entry starting in
 * the next [WINDOW_SECONDS], one reminder [ReminderPrefs.leadMin] before its computed leave time
 * (the start of the travel leg into it, or its start when there is no leg). Times are computed
 * by the same engine as the calendar and converted to instants in the trip's zone only here.
 */
object ReminderPlanner {
    const val WINDOW_SECONDS = 48L * 3600
    /** iOS allows 64 pending local notifications; leave headroom for the activity push channel. */
    const val MAX_REMINDERS = 60

    /**
     * Complexity:
     * - **Time:** O(T · D · S) over the T trips, their D days inside the window and S stops per day, plus O(R log R) to sort the R results.
     * - **Space:** O(R).
     */
    fun plan(trips: List<TripData>, prefs: ReminderPrefs, nowEpochSeconds: Long): List<Reminder> {
        if (!prefs.enabled) return emptyList()
        val until = nowEpochSeconds + WINDOW_SECONDS
        val out = ArrayList<Reminder>()
        for (data in trips) {
            val trip = data.trip
            if (runCatching { TimeZone.of(trip.timeZone) }.isFailure) continue
            val byDay = data.stops.groupBy { it.day }
            for (day in 0 until TripDays.count(trip)) {
                val schedule = DaySchedules.compute(trip, day, byDay[day].orEmpty(), data.legs, data.dayHours[day]) ?: continue
                // Skip days that end before now or start after the window (cheap bound before per-entry work).
                val dayStart = Schedule.epochSeconds(schedule.entries.firstOrNull()?.start ?: continue, trip.timeZone)
                if (dayStart > until) continue
                for (entry in schedule.entries) {
                    val leave = entry.travelBefore?.start ?: entry.start
                    val fireAt = Schedule.epochSeconds(leave, trip.timeZone) - prefs.leadMin * 60L
                    if (fireAt <= nowEpochSeconds || fireAt > until) continue
                    out += Reminder(
                        id = id(trip.id, entry.input.id),
                        tripId = trip.id,
                        stopId = entry.input.id,
                        fireAtEpochSeconds = fireAt,
                        title = "Leave for ${entry.input.name} in ${prefs.leadMin} min",
                        body = "${trip.name} · Day ${day + 1} · starts ${Schedule.formatTime(entry.start.time)}" +
                            (entry.travelBefore?.takeIf { it.seconds > 0 }?.let { " · ${(it.seconds + 59) / 60} min travel" } ?: ""),
                    )
                }
            }
        }
        return out.sortedBy { it.fireAtEpochSeconds }.take(MAX_REMINDERS)
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun id(tripId: String, stopId: String): String = "trip:$tripId:$stopId"
}
