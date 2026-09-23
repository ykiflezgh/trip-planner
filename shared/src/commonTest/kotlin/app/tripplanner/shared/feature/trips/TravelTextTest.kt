package app.tripplanner.shared.feature.trips

import app.tripplanner.shared.core.model.TravelLeg
import app.tripplanner.shared.core.model.TravelMode
import kotlin.test.Test
import kotlin.test.assertEquals

class TravelTextTest {
    @Test
    fun formatsDurationsAndDistances() {
        assertEquals("<1 min", TravelText.duration(20))
        assertEquals("13 min", TravelText.duration(754))
        assertEquals("1 h 05 min", TravelText.duration(3900))
        assertEquals("850 m", TravelText.distance(850))
        assertEquals("4.2 km", TravelText.distance(4210))
        assertEquals("12 km", TravelText.distance(12_499))
    }

    @Test
    fun formatsLegsAndFailures() {
        assertEquals("13 min \u00b7 4.2 km", TravelText.format(TravelLeg("a", "b", TravelMode.DRIVE, seconds = 754, meters = 4210)))
        assertEquals("\u2014", TravelText.format(TravelLeg("a", "b", TravelMode.WALK, error = "ROUTE_NOT_FOUND")))
        assertEquals("\u2014", TravelText.format(TravelLeg("a", "b", TravelMode.WALK)))
    }
}
