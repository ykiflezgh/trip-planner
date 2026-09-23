package app.tripplanner.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Schedule engine (design v1.1 §6.5). One pure function turns a day's entries, its hours and
 * the travel legs into timed calendar entries plus warnings. Computed times are never stored:
 * every device and the ICS feed derive them from the same data with this same code.
 *
 * All arithmetic is wall-clock ([LocalDateTime]) in the trip zone; conversion to instants
 * happens only at the edges (`epochSeconds` on the outputs), so DST gaps and overlaps resolve
 * with kotlinx.datetime's standard rules and "14:00 at the museum" stays 14:00 local.
 */
enum class EntryKind { PLACE, CUSTOM }
enum class TravelMode { DRIVING, WALKING }

/** Day start/end as wall-clock times (trip defaults or the `days/{day}` override). */
data class DayHours(val start: LocalTime, val end: LocalTime)

/** One stop as the engine sees it, already in `order`. */
data class EntryInput(
    val id: String,
    val name: String,
    val kind: EntryKind,
    val durationMin: Int,
    /** Pinned wall-clock start in the trip zone (design §7: stored as "HH:mm"). */
    val fixedStart: LocalTime? = null,
    /** Travel mode to the next place stop; `null` = trip default. */
    val modeToNext: TravelMode? = null,
)

/** `(fromStopId, toStopId, mode) -> seconds`, `null` when the Function has not computed it yet. */
fun interface TravelLookup {
    fun seconds(fromStopId: String, toStopId: String, mode: TravelMode): Int?
}

sealed interface Warning {
    val entryId: String
    /** The cursor reached a pinned entry after its start. */
    data class LateArrival(override val entryId: String, val minutes: Int) : Warning
    /** A pinned entry starts before the previous entry ends. */
    data class Overlap(override val entryId: String, val minutes: Int) : Warning
    /** The last entry ends after the day's end time. */
    data class DayOverrun(override val entryId: String, val minutes: Int) : Warning
}

data class TravelLeg(
    val fromStopId: String,
    val toStopId: String,
    val mode: TravelMode,
    val seconds: Int,
    /** No travel document yet: counted as 0 s and shown as a shimmer (design §8.3). */
    val pending: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
)

data class TimedEntry(
    val input: EntryInput,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** The leg that precedes this entry, when it is a place stop after another place stop. */
    val travelBefore: TravelLeg? = null,
    /** Free time between the cursor and a pinned start. */
    val gapBeforeMin: Int = 0,
) {
    val pinned: Boolean get() = input.fixedStart != null
}

data class DaySchedule(
    val date: LocalDate,
    val hours: DayHours,
    val entries: List<TimedEntry>,
    val warnings: List<Warning>,
) {
    /** When the day's plan actually ends (or the day start when empty). */
    val end: LocalDateTime get() = entries.lastOrNull()?.end ?: LocalDateTime(date, hours.start)
}

object Schedule {
    val DEFAULT_START: LocalTime = LocalTime(9, 0)
    val DEFAULT_END: LocalTime = LocalTime(21, 0)
    const val MIN_DURATION_MIN = 5
    const val MAX_DURATION_MIN = 1440

    /**
     * Design §6.5 rules 1-6.
     *
     * Complexity:
     * - **Time:** O(E) for the E entries of the day (one travel lookup per place adjacency).
     * - **Space:** O(E) for the timed entries and warnings.
     */
    fun computeDay(
        date: LocalDate,
        hours: DayHours,
        entries: List<EntryInput>,
        travel: TravelLookup,
        defaultMode: TravelMode,
    ): DaySchedule {
        val out = ArrayList<TimedEntry>(entries.size)
        val warnings = ArrayList<Warning>()
        var cursor = LocalDateTime(date, hours.start)
        var previousPlace: EntryInput? = null
        var previousEnd: LocalDateTime? = null

        for (entry in entries) {
            // Rule 2: travel from the previous *place* stop; custom entries don't break adjacency.
            var leg: TravelLeg? = null
            if (entry.kind == EntryKind.PLACE && previousPlace != null) {
                val mode = previousPlace.modeToNext ?: defaultMode
                val seconds = travel.seconds(previousPlace.id, entry.id, mode)
                val legStart = cursor
                cursor = cursor.plusMinutes(((seconds ?: 0) + 59) / 60)
                leg = TravelLeg(previousPlace.id, entry.id, mode, seconds ?: 0, pending = seconds == null, start = legStart, end = cursor)
            }
            // Rules 3-4: pinned entries anchor the cursor; late arrivals and free time are reported.
            var gap = 0
            val start = entry.fixedStart?.let { pinned ->
                val pinnedAt = LocalDateTime(date, pinned)
                val delta = cursor.minutesUntil(pinnedAt)
                if (delta < 0) warnings += Warning.LateArrival(entry.id, -delta) else gap = delta
                // Rule 5: overlap with what came before.
                previousEnd?.let { pe -> if (pinnedAt < pe) warnings += Warning.Overlap(entry.id, pinnedAt.minutesUntil(pe)) }
                pinnedAt
            } ?: cursor
            val end = start.plusMinutes(entry.durationMin.coerceIn(MIN_DURATION_MIN, MAX_DURATION_MIN))
            out += TimedEntry(entry, start, end, leg, gap)
            cursor = end
            previousEnd = end
            if (entry.kind == EntryKind.PLACE) previousPlace = entry
        }
        // Rule 5: day overrun.
        out.lastOrNull()?.let { last ->
            val dayEnd = LocalDateTime(date, hours.end)
            if (last.end > dayEnd) warnings += Warning.DayOverrun(last.input.id, dayEnd.minutesUntil(last.end))
        }
        return DaySchedule(date, hours, out, warnings)
    }

    /**
     * Epoch seconds of a wall-clock time in [zone], resolved by kotlinx.datetime's DST rules
     * (a time inside a spring-forward gap moves forward; an ambiguous fall-back time takes the
     * earlier offset). Used only at the edges: reminders and the ICS feed.
     *
     * Complexity:
     * - **Time:** O(1) zone lookup.
     * - **Space:** O(1).
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun epochSeconds(t: LocalDateTime, zone: TimeZone): Long = t.toInstant(zone).epochSeconds

    /**
     * Same, from an IANA id; loads the zone database first on platforms that need it.
     *
     * Complexity:
     * - **Time:** O(1) zone lookup.
     * - **Space:** O(1).
     */
    fun epochSeconds(t: LocalDateTime, zoneId: String): Long {
        ensureTimeZones()
        return epochSeconds(t, TimeZone.of(zoneId))
    }

    /**
     * Parses a stored `"HH:mm"`; `null` when malformed so a bad document never breaks the day.
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun parseTime(hhmm: String?): LocalTime? {
        if (hhmm == null || hhmm.length != 5 || hhmm[2] != ':') return null
        val h = hhmm.substring(0, 2).toIntOrNull() ?: return null
        val m = hhmm.substring(3, 5).toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) LocalTime(h, m) else null
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun formatTime(t: LocalTime): String = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

/**
 * Complexity:
 * - **Time:** O(1).
 * - **Space:** O(1).
 */
internal fun LocalDateTime.plusMinutes(minutes: Int): LocalDateTime {
    val total = hour * 60 + minute + minutes
    val perDay = 24 * 60
    val dayOverflow = if (total >= 0) total / perDay else -((-total + perDay - 1) / perDay)
    val rem = total - dayOverflow * perDay
    val d = if (dayOverflow == 0) date else LocalDate.fromEpochDays(date.toEpochDays() + dayOverflow)
    return LocalDateTime(d, LocalTime(rem / 60, rem % 60))
}

/** Whole minutes from this to [other] (negative when [other] is earlier). */
internal fun LocalDateTime.minutesUntil(other: LocalDateTime): Int {
    val days = (other.date.toEpochDays() - date.toEpochDays()).toInt()
    return days * 24 * 60 + (other.hour * 60 + other.minute) - (hour * 60 + minute)
}

/**
 * JS-friendly facade for the ICS feed Function (design §8.6): plain strings and numbers in,
 * plain objects out, so the Node build needs no Kotlin types on the TypeScript side.
 */
@JsExport
object ScheduleJs {
    @JsName("computeDay")
    fun computeDay(
        dateIso: String,
        dayStart: String,
        dayEnd: String,
        entriesJson: String,
        travelJson: String,
        defaultMode: String,
    ): String = ScheduleJson.computeDay(dateIso, dayStart, dayEnd, entriesJson, travelJson, defaultMode)
}

/** Loads platform time-zone data where it is not built in (js-joda on Node); no-op elsewhere. */
internal expect fun ensureTimeZones()
