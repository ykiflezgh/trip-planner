package app.tripplanner.shared.feature.reminders

import app.tripplanner.shared.core.model.ReminderPrefs
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.core.model.TravelLeg
import app.tripplanner.shared.core.model.TravelMode
import app.tripplanner.shared.core.model.Trip
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderPlannerTest {
    private val zone = TimeZone.of("Europe/Lisbon")
    private val trip = Trip(id = "t", name = "Lisbon", startDate = "2026-09-22", endDate = "2026-09-24", timeZone = "Europe/Lisbon")
    private val a = Stop(id = "a", name = "Monastery", durationMin = 90, order = "a")
    private val b = Stop(id = "b", name = "Tower", durationMin = 60, order = "b")
    private val legs = mapOf(Triple("a", "b", TravelMode.DRIVE) to TravelLeg("a", "b", TravelMode.DRIVE, seconds = 600, meters = 1))
    private val data = TripData(trip, listOf(a, b), legs, emptyMap())

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun at(s: String): Long = LocalDateTime.parse(s).toInstant(zone).epochSeconds

    @Test
    fun firesLeadMinutesBeforeTheLeaveTime() {
        val now = at("2026-09-22T06:00")
        val r = ReminderPlanner.plan(listOf(data), ReminderPrefs(enabled = true, leadMin = 15), now)
        assertEquals(listOf("trip:t:a", "trip:t:b"), r.map { it.id })
        assertEquals(at("2026-09-22T08:45"), r[0].fireAtEpochSeconds)           // 09:00 start, no leg
        assertEquals(at("2026-09-22T10:15"), r[1].fireAtEpochSeconds)           // leg starts 10:30, start 10:40
        assertEquals("Leave for Tower in 15 min", r[1].title)
        assertTrue(r[1].body.contains("Day 1") && r[1].body.contains("10:40") && r[1].body.contains("10 min travel"))
    }

    @Test
    fun windowIsFortyEightHoursAndPastEntriesAreSkipped() {
        val now = at("2026-09-22T09:30") // Monastery already started
        val r = ReminderPlanner.plan(listOf(data), ReminderPrefs(enabled = true), now)
        assertEquals(listOf("trip:t:b"), r.map { it.id })
        val early = at("2026-09-19T09:00") // more than 48 h before the trip
        assertEquals(emptyList(), ReminderPlanner.plan(listOf(data), ReminderPrefs(enabled = true), early))
    }

    @Test
    fun disabledPrefsAndUnknownZonesYieldNothing() {
        assertEquals(emptyList(), ReminderPlanner.plan(listOf(data), ReminderPrefs(enabled = false), at("2026-09-22T06:00")))
        val odd = data.copy(trip = trip.copy(timeZone = "Mars/Olympus"))
        assertEquals(emptyList(), ReminderPlanner.plan(listOf(odd), ReminderPrefs(enabled = true), at("2026-09-22T06:00")))
    }

    @Test
    fun capsAtSixtyEarliestFirst() {
        val many = (0 until 80).map { Stop(id = "s$it", name = "S$it", durationMin = 10, order = "k${it.toString().padStart(3, '0')}") }
        val r = ReminderPlanner.plan(listOf(TripData(trip, many, emptyMap(), emptyMap())), ReminderPrefs(enabled = true, leadMin = 1), at("2026-09-22T06:00"))
        assertEquals(60, r.size)
        assertTrue(r.zipWithNext().all { (x, y) -> x.fireAtEpochSeconds <= y.fireAtEpochSeconds })
    }
}
