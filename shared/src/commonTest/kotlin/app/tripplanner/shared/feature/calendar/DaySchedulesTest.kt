package app.tripplanner.shared.feature.calendar

import app.tripplanner.schedule.Warning
import app.tripplanner.shared.core.model.DayHoursDoc
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.core.model.TravelLeg
import app.tripplanner.shared.core.model.TravelMode
import app.tripplanner.shared.core.model.Trip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DaySchedulesTest {
    private val trip = Trip(id = "t", startDate = "2026-09-22", endDate = "2026-09-24", timeZone = "Europe/Lisbon")
    private val a = Stop(id = "a", name = "A", durationMin = 60)
    private val b = Stop(id = "b", name = "B", durationMin = 30, fixedStart = "12:00")
    private val legs = mapOf(Triple("a", "b", TravelMode.DRIVE) to TravelLeg("a", "b", TravelMode.DRIVE, seconds = 600, meters = 1))

    @Test
    fun usesTripDefaultsAndDrivingLegs() {
        val day = DaySchedules.compute(trip, 1, listOf(a, b), legs, null)!!
        assertEquals("2026-09-23T09:00", day.entries[0].start.toString())
        assertEquals(600, day.entries[1].travelBefore!!.seconds)
        assertEquals("2026-09-23T12:00", day.entries[1].start.toString())
        assertEquals(110, day.entries[1].gapBeforeMin)
    }

    @Test
    fun dayOverrideAndWalkingDefaultApply() {
        val walkLegs = mapOf(Triple("a", "b", TravelMode.WALK) to TravelLeg("a", "b", TravelMode.WALK, seconds = 1200, meters = 1))
        val day = DaySchedules.compute(trip.copy(defaultTravelMode = "walking"), 0, listOf(a, b.copy(fixedStart = null)), walkLegs, DayHoursDoc(start = "08:00", end = "10:00"))!!
        assertEquals("2026-09-22T08:00", day.entries[0].start.toString())
        assertEquals("2026-09-22T09:20", day.entries[1].start.toString())
        assertEquals(listOf<Warning>(), day.warnings)
    }

    @Test
    fun errorLegsCountAsPendingAndBadDatesYieldNull() {
        val bad = mapOf(Triple("a", "b", TravelMode.DRIVE) to TravelLeg("a", "b", TravelMode.DRIVE, error = "boom"))
        val day = DaySchedules.compute(trip, 0, listOf(a, b.copy(fixedStart = null)), bad, null)!!
        assertTrue(day.entries[1].travelBefore!!.pending)
        assertNull(DaySchedules.compute(trip.copy(startDate = "x"), 0, listOf(a), legs, null))
    }
}
