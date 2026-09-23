package app.tripplanner.schedule

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON boundary shared by the JS export and the golden fixtures, so a fixture file is
 * literally the engine's input and expected output on every target.
 */
object ScheduleJson {
    @Serializable
    data class EventIn(val id: String, val title: String, val durationMin: Int, val stopId: String? = null, val fixedStart: String? = null, val modeToNext: String? = null)
    @Serializable
    data class LegIn(val from: String, val to: String, val mode: String, val seconds: Int)
    @Serializable
    data class LegOut(val from: String, val to: String, val mode: String, val seconds: Int, val pending: Boolean, val start: String, val end: String)
    @Serializable
    data class EventOut(val id: String, val start: String, val end: String, val pinned: Boolean, val gapBeforeMin: Int, val travelBefore: LegOut? = null)
    @Serializable
    data class WarningOut(val type: String, val eventId: String, val minutes: Int)
    @Serializable
    data class DayOut(val date: String, val end: String, val events: List<EventOut>, val warnings: List<WarningOut>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    /**
     * Complexity:
     * - **Time:** O(E + L) to parse E events and L legs plus the engine's O(E).
     * - **Space:** O(E + L).
     */
    fun computeDay(dateIso: String, dayStart: String, dayEnd: String, eventsJson: String, travelJson: String, defaultMode: String): String {
        val events = json.decodeFromString<List<EventIn>>(eventsJson).map {
            EventInput(
                id = it.id, title = it.title, durationMin = it.durationMin, stopId = it.stopId,
                fixedStart = Schedule.parseTime(it.fixedStart),
                modeToNext = it.modeToNext?.let(::mode),
            )
        }
        val legs = json.decodeFromString<List<LegIn>>(travelJson).associate { Triple(it.from, it.to, mode(it.mode)) to it.seconds }
        val day = Schedule.computeDay(
            date = LocalDate.parse(dateIso),
            hours = DayHours(Schedule.parseTime(dayStart) ?: Schedule.DEFAULT_START, Schedule.parseTime(dayEnd) ?: Schedule.DEFAULT_END),
            events = events,
            travel = { f, t, m -> legs[Triple(f, t, m)] },
            defaultMode = mode(defaultMode),
        )
        return json.encodeToString(DayOut.serializer(), toOut(day))
    }

    /**
     * Complexity:
     * - **Time:** O(E + W).
     * - **Space:** O(E + W).
     */
    fun toOut(day: DaySchedule): DayOut = DayOut(
        date = day.date.toString(),
        end = day.end.toString(),
        events = day.events.map { e ->
            EventOut(
                id = e.input.id, start = e.start.toString(), end = e.end.toString(), pinned = e.pinned, gapBeforeMin = e.gapBeforeMin,
                travelBefore = e.travelBefore?.let { LegOut(it.fromStopId, it.toStopId, it.mode.name.lowercase(), it.seconds, it.pending, it.start.toString(), it.end.toString()) },
            )
        },
        warnings = day.warnings.map {
            when (it) {
                is Warning.LateArrival -> WarningOut("lateArrival", it.eventId, it.minutes)
                is Warning.Overlap -> WarningOut("overlap", it.eventId, it.minutes)
                is Warning.DayOverrun -> WarningOut("dayOverrun", it.eventId, it.minutes)
            }
        },
    )

    private fun mode(s: String): TravelMode = if (s.equals("walking", ignoreCase = true)) TravelMode.WALKING else TravelMode.DRIVING
}
