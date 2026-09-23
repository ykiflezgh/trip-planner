package app.tripplanner.shared.feature.calendar

import app.tripplanner.schedule.DayHours
import app.tripplanner.schedule.DaySchedule
import app.tripplanner.schedule.EntryInput
import app.tripplanner.schedule.EntryKind
import app.tripplanner.schedule.Schedule
import app.tripplanner.schedule.TravelMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TimeDisplayTest {
    private val lisbon = TimeZone.of("Europe/Lisbon")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val london = TimeZone.of("Europe/London")
    private val date = LocalDate(2026, 9, 22)
    private val schedule = Schedule.computeDay(
        date, DayHours(LocalTime(9, 0), LocalTime(21, 0)),
        listOf(EntryInput("a", "A", EntryKind.PLACE, 60), EntryInput("b", "B", EntryKind.PLACE, 60)),
        { _, _, _ -> 600 }, TravelMode.DRIVING,
    )

    @Test
    fun shiftsEveryTimeAndKeepsDateAndWarnings() {
        val shifted = TimeDisplay.shift(schedule, lisbon, tokyo) // +8 h in September
        assertEquals(date, shifted.date)
        assertEquals(LocalDateTime(date, LocalTime(17, 0)), shifted.entries[0].start)
        assertEquals(LocalDateTime(date, LocalTime(18, 10)), shifted.entries[1].start) // 10:00 + 10 min drive, +8 h
        assertEquals(LocalDateTime(date, LocalTime(18, 0)), shifted.entries[1].travelBefore!!.start)
        assertEquals(LocalTime(17, 0), shifted.hours.start)
        // 21:00 Lisbon is 05:00 the next day in Tokyo: the hours end wraps but stays a LocalTime.
        assertEquals(LocalTime(5, 0), shifted.hours.end)
        assertEquals(schedule.warnings, shifted.warnings)
    }

    @Test
    fun aDayThatLandsOnAnotherCalendarDayIsAnchoredThere() {
        val honolulu = TimeZone.of("Pacific/Honolulu") // -11 h from Lisbon in September
        val shifted = TimeDisplay.shift(schedule, lisbon, honolulu)
        assertEquals(LocalDate(2026, 9, 21), shifted.date)
        assertEquals(LocalTime(22, 0), shifted.hours.start)
        assertEquals(LocalDateTime(LocalDate(2026, 9, 21), LocalTime(22, 0)), shifted.entries[0].start)
    }

    @Test
    fun sameZoneIsIdentityAndUnknownZoneIsNull() {
        assertSame(schedule, TimeDisplay.shift(schedule, lisbon, lisbon))
        assertNull(TimeDisplay.zone("Mars/Olympus"))
    }

    @Test
    fun differsOnlyWhenOffsetsDiffer() {
        val at = LocalDateTime(date, LocalTime(12, 0))
        assertTrue(TimeDisplay.differs(lisbon, tokyo, at))
        assertFalse(TimeDisplay.differs(lisbon, london, at)) // same offset in September
        assertFalse(TimeDisplay.differs(lisbon, lisbon, at))
    }

    @Test
    fun labels() {
        assertEquals("Lisbon", TimeDisplay.label("Europe/Lisbon"))
        assertEquals("New York", TimeDisplay.label("America/New_York"))
        assertEquals("UTC", TimeDisplay.label("UTC"))
    }
}
