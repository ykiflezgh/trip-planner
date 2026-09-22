package app.tripplanner.shared.feature.trips

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NewTripTest {
    private val uid = "user-1"
    private val start = LocalDate(2026, 10, 3)
    private val end = LocalDate(2026, 10, 5)

    @Test
    fun buildsOwnerOnlyTripMatchingSchema() {
        val trip = NewTrip.build(uid, "  Lisbon  ", start, end, TimeZone.of("Europe/Lisbon"))
        assertEquals("Lisbon", trip.name)
        assertEquals("2026-10-03", trip.startDate)
        assertEquals("2026-10-05", trip.endDate)
        assertEquals("Europe/Lisbon", trip.timeZone)
        assertEquals(uid, trip.ownerId)
        assertEquals(listOf(uid), trip.memberIds)            // creator is a member: rules require it
        assertEquals(mapOf(uid to "owner"), trip.roles)      // lower-case role string, as firestore.rules compares
        assertEquals("", trip.id)                            // repository assigns the document id
        assertNull(trip.createdAt)                           // repository stamps server timestamps
    }

    @Test
    fun sameDayTripIsValid() {
        assertNull(NewTrip.validate("Day trip", start, start))
    }

    @Test
    fun rejectsBlankName() {
        assertNotNull(NewTrip.validate("   ", start, end))
    }

    @Test
    fun rejectsOverlongName() {
        assertNotNull(NewTrip.validate("x".repeat(NewTrip.MAX_NAME_LENGTH + 1), start, end))
        assertNull(NewTrip.validate("x".repeat(NewTrip.MAX_NAME_LENGTH), start, end))
    }

    @Test
    fun rejectsMissingOrReversedDates() {
        assertNotNull(NewTrip.validate("Trip", null, end))
        assertNotNull(NewTrip.validate("Trip", start, null))
        assertNotNull(NewTrip.validate("Trip", end, start))
    }

    @Test
    fun buildRefusesInvalidInput() {
        assertFailsWith<IllegalArgumentException> { NewTrip.build(uid, "", start, end, TimeZone.UTC) }
        assertFailsWith<IllegalArgumentException> { NewTrip.build("", "Trip", start, end, TimeZone.UTC) }
    }
}
