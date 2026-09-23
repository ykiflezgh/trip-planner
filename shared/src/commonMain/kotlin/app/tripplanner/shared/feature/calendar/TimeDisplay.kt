package app.tripplanner.shared.feature.calendar

import app.tripplanner.schedule.DayHours
import app.tripplanner.schedule.DaySchedule
import app.tripplanner.schedule.TimedEntry
import app.tripplanner.schedule.TravelLeg
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Time-zone toggle (design v1.1 §6.6): the calendar can show the trip's wall-clock times or the
 * device's local equivalents. Only the display changes; stored values (pinned "HH:mm", day hours)
 * are never converted. Conversion goes through an instant per time, so DST edges resolve exactly.
 */
object TimeDisplay {
    /**
     * `null` when the trip's zone id is unknown to this device (display then stays in trip time).
     *
     * Complexity:
     * - **Time:** O(1) zone lookup.
     * - **Space:** O(1).
     */
    fun zone(id: String): TimeZone? = runCatching { TimeZone.of(id) }.getOrNull()

    /**
     * True when showing local time would change anything: the two zones differ in id and in
     * offset on the given date (Europe/Lisbon vs Europe/London agree most of the year).
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun differs(tripZone: TimeZone, device: TimeZone, at: LocalDateTime): Boolean =
        tripZone.id != device.id && convert(at, tripZone, device) != at

    /**
     * Same instant, read in another zone.
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun convert(t: LocalDateTime, from: TimeZone, to: TimeZone): LocalDateTime = t.toInstant(from).toLocalDateTime(to)

    /**
     * The schedule as the device sees it: every start/end and travel leg converted from
     * [tripZone] to [device]. The schedule's date becomes the local date of the day's start, so a
     * grid anchored on it keeps the day hours and entries together even when the whole day lands
     * on another calendar day (a Lisbon morning is the previous evening in Honolulu). Times that
     * spill past that date keep their real date, so callers must measure minutes from the
     * schedule's date rather than the time of day. Warnings are unchanged.
     *
     * Complexity:
     * - **Time:** O(E) for the E entries.
     * - **Space:** O(E).
     */
    fun shift(schedule: DaySchedule, tripZone: TimeZone, device: TimeZone): DaySchedule {
        if (tripZone.id == device.id) return schedule
        fun c(t: LocalDateTime) = convert(t, tripZone, device)
        val hoursStart = c(LocalDateTime(schedule.date, schedule.hours.start))
        val hoursEnd = c(LocalDateTime(schedule.date, schedule.hours.end))
        return DaySchedule(
            date = hoursStart.date,
            hours = DayHours(hoursStart.time, hoursEnd.time),
            entries = schedule.entries.map { e ->
                TimedEntry(
                    input = e.input,
                    start = c(e.start),
                    end = c(e.end),
                    travelBefore = e.travelBefore?.let { l -> TravelLeg(l.fromStopId, l.toStopId, l.mode, l.seconds, l.pending, c(l.start), c(l.end)) },
                    gapBeforeMin = e.gapBeforeMin,
                )
            },
            warnings = schedule.warnings,
        )
    }

    /**
     * Short label for a zone id: "Europe/Lisbon" -> "Lisbon", "America/New_York" -> "New York".
     *
     * Complexity:
     * - **Time:** O(L) for the L-character id.
     * - **Space:** O(L).
     */
    fun label(id: String): String = id.substringAfterLast('/').replace('_', ' ').ifBlank { id }
}
