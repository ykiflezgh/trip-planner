package app.tripplanner.shared.feature.trips

import app.tripplanner.shared.core.model.Trip
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TripDaysTest {
    @Test
    fun countsBothEndsInclusive() {
        assertEquals(3, TripDays.count(Trip(startDate = "2026-10-10", endDate = "2026-10-12")))
        assertEquals(1, TripDays.count(Trip(startDate = "2026-10-10", endDate = "2026-10-10")))
    }

    @Test
    fun malformedDatesFallBackToOneDay() {
        assertEquals(1, TripDays.count(Trip(startDate = "", endDate = "")))
    }

    @Test
    fun dateOfDayIndex() {
        assertEquals(LocalDate(2026, 10, 12), TripDays.date(Trip(startDate = "2026-10-10", endDate = "2026-10-12"), 2))
    }
}
