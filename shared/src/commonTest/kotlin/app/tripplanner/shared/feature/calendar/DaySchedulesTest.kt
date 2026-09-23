package app.tripplanner.shared.feature.calendar

import app.tripplanner.schedule.Warning
import app.tripplanner.shared.core.model.DayHoursDoc
import app.tripplanner.shared.core.model.Event
import app.tripplanner.shared.core.model.StopPlace
import app.tripplanner.shared.core.model.TravelLeg
import app.tripplanner.shared.core.model.TravelMode
import app.tripplanner.shared.core.model.Trip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DaySchedulesTest {
    private val trip = Trip(id = "t", startDate = "2026-09-22", endDate = "2026-09-24", timeZone = "Europe/Lisbon")
    private val a = Event(id = "a", title = "A", durationMin = 60, stop = StopPlace(placeId = "pa"))
    private val lunch = Event(id = "l", title = "Lunch", durationMin = 45)
    private val b = Event(id = "b", title = "B", durationMin = 30, fixedStart = "12:00", stop = StopPlace(placeId = "pb"))
    private val legs = mapOf(Triple("a", "b", TravelMode.DRIVE) to TravelLeg("a", "b", TravelMode.DRIVE, seconds = 600, meters = 1))

    @Test
    fun usesTripDefaultsAndDrivingLegsAcrossAPlainEvent() {
        val day = DaySchedules.compute(trip, 1, listOf(a, lunch, b), legs, null)!!
        assertEquals("2026-09-23T09:00", day.events[0].start.toString())
        assertNull(day.events[1].travelBefore)
        assertEquals(600, day.events[2].travelBefore!!.seconds)
        assertEquals("2026-09-23T12:00", day.events[2].start.toString())
        assertEquals(65, day.events[2].gapBeforeMin) // 10:45 + 10 min drive -> 12:00
    }

    @Test
    fun dayOverrideAndWalkingDefaultApply() {
        val walkLegs = mapOf(Triple("a", "b", TravelMode.WALK) to TravelLeg("a", "b", TravelMode.WALK, seconds = 1200, meters = 1))
        val day = DaySchedules.compute(trip.copy(defaultTravelMode = "walking"), 0, listOf(a, b.copy(fixedStart = null)), walkLegs, DayHoursDoc(start = "08:00", end = "10:00"))!!
        assertEquals("2026-09-22T08:00", day.events[0].start.toString())
        assertEquals("2026-09-22T09:20", day.events[1].start.toString())
        assertEquals(listOf<Warning>(), day.warnings)
    }

    @Test
    fun errorLegsCountAsPendingAndBadDatesYieldNull() {
        val bad = mapOf(Triple("a", "b", TravelMode.DRIVE) to TravelLeg("a", "b", TravelMode.DRIVE, error = "boom"))
        val day = DaySchedules.compute(trip, 0, listOf(a, b.copy(fixedStart = null)), bad, null)!!
        assertTrue(day.events[1].travelBefore!!.pending)
        assertNull(DaySchedules.compute(trip.copy(startDate = "x"), 0, listOf(a), legs, null))
    }
}
