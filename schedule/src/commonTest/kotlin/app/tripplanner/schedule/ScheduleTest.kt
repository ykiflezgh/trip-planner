package app.tripplanner.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleTest {
    private val date = LocalDate(2026, 9, 22)
    private val hours = DayHours(LocalTime(9, 0), LocalTime(21, 0))
    private fun place(id: String, min: Int, pinned: String? = null, mode: TravelMode? = null) =
        EventInput(id, id, min, stopId = "stop-$id", fixedStart = Schedule.parseTime(pinned), modeToNext = mode)
    private fun custom(id: String, min: Int, pinned: String? = null) = EventInput(id, id, min, stopId = null, fixedStart = Schedule.parseTime(pinned))
    private fun t(s: String) = LocalDateTime.parse("2026-09-22T$s")

    @Test
    fun chainsDurationsAndTravelFromTheDayStart() {
        val legs = mapOf(Triple("stop-a", "stop-b", TravelMode.DRIVING) to 754, Triple("stop-b", "stop-c", TravelMode.WALKING) to 61)
        val day = Schedule.computeDay(date, hours, listOf(place("a", 90), place("b", 60, mode = TravelMode.WALKING), place("c", 30)), { f, to, m -> legs[Triple(f, to, m)] }, TravelMode.DRIVING)
        assertEquals(listOf(t("09:00"), t("10:43"), t("11:45")), day.events.map { it.start })
        assertEquals(listOf(t("10:30"), t("11:43"), t("12:15")), day.events.map { it.end })
        assertEquals(TravelMode.WALKING, day.events[2].travelBefore!!.mode)
        assertTrue(day.warnings.isEmpty())
    }

    @Test
    fun eventsWithoutStopsDoNotBreakTravelAdjacency() {
        val legs = mapOf(Triple("stop-a", "stop-b", TravelMode.DRIVING) to 600)
        val day = Schedule.computeDay(date, hours, listOf(place("a", 60), custom("lunch", 45), place("b", 60)), { f, to, m -> legs[Triple(f, to, m)] }, TravelMode.DRIVING)
        assertNull(day.events[1].travelBefore)
        val leg = day.events[2].travelBefore!!
        assertEquals("stop-a" to "stop-b", leg.fromStopId to leg.toStopId)
        assertEquals(t("10:45"), leg.start)
        assertEquals(t("10:55"), day.events[2].start)
    }

    @Test
    fun missingTravelIsZeroAndPending() {
        val day = Schedule.computeDay(date, hours, listOf(place("a", 60), place("b", 60)), { _, _, _ -> null }, TravelMode.DRIVING)
        assertTrue(day.events[1].travelBefore!!.pending)
        assertEquals(t("10:00"), day.events[1].start)
    }

    @Test
    fun pinnedEntryAnchorsWithGapAndLateArrival() {
        val early = Schedule.computeDay(date, hours, listOf(place("a", 60), place("museum", 90, pinned = "14:00")), { _, _, _ -> 1800 }, TravelMode.DRIVING)
        assertEquals(t("14:00"), early.events[1].start)
        assertEquals(210, early.events[1].gapBeforeMin) // 10:30 -> 14:00
        assertTrue(early.warnings.isEmpty())

        val late = Schedule.computeDay(date, hours, listOf(place("a", 360), place("museum", 90, pinned = "14:00")), { _, _, _ -> 1800 }, TravelMode.DRIVING)
        assertEquals(t("14:00"), late.events[1].start)
        assertEquals(listOf<Warning>(Warning.LateArrival("museum", 90), Warning.Overlap("museum", 60)), late.warnings)
    }

    @Test
    fun dayOverrunAndMidnightRollover() {
        val day = Schedule.computeDay(date, hours, listOf(custom("club", 16 * 60)), { _, _, _ -> null }, TravelMode.DRIVING)
        assertEquals(LocalDateTime.parse("2026-09-23T01:00"), day.end)
        assertEquals(listOf<Warning>(Warning.DayOverrun("club", 240)), day.warnings)
    }

    @Test
    fun durationsAreClamped() {
        val day = Schedule.computeDay(date, hours, listOf(custom("x", 0), custom("y", 100_000)), { _, _, _ -> null }, TravelMode.DRIVING)
        assertEquals(5, day.events[0].start.minutesUntil(day.events[0].end))
        assertEquals(1440, day.events[1].start.minutesUntil(day.events[1].end))
    }

    @Test
    fun dstEdgesResolveAtTheBoundaryOnly() {
        // Europe/Lisbon springs forward 2026-03-29 at 01:00 -> 02:00; wall-clock math is unaffected.
        val spring = LocalDate(2026, 3, 29)
        val day = Schedule.computeDay(spring, DayHours(LocalTime(0, 30), LocalTime(23, 0)), listOf(custom("night", 120)), { _, _, _ -> null }, TravelMode.DRIVING)
        assertEquals(LocalDateTime(spring, LocalTime(2, 30)), day.events[0].end)
        // 00:30 UTC and 02:30 (which is 01:30 UTC after the gap): the instant span is 60 min, not 120.
        assertEquals(3600, Schedule.epochSeconds(day.events[0].end, "Europe/Lisbon") - Schedule.epochSeconds(day.events[0].start, "Europe/Lisbon"))
    }

    @Test
    fun timeParsingAndFormatting() {
        assertEquals(LocalTime(14, 5), Schedule.parseTime("14:05"))
        assertNull(Schedule.parseTime("24:00")); assertNull(Schedule.parseTime("9:00")); assertNull(Schedule.parseTime(null))
        assertEquals("09:05", Schedule.formatTime(LocalTime(9, 5)))
    }

    @Test
    fun jsonFacadeMatchesTheEngine() {
        val out = ScheduleJson.computeDay(
            "2026-09-22", "09:00", "21:00",
            """[{"id":"a","title":"A","durationMin":90,"stopId":"sa"},{"id":"b","title":"B","durationMin":60,"stopId":"sb","fixedStart":"11:00"}]""",
            """[{"from":"sa","to":"sb","mode":"driving","seconds":600}]""",
            "driving",
        )
        assertTrue(out.contains("\"start\":\"2026-09-22T11:00\"") && out.contains("\"pinned\":true") && out.contains("\"gapBeforeMin\":20"), out)
        assertTrue(out.contains("\"warnings\":[]"), out)
    }
}
