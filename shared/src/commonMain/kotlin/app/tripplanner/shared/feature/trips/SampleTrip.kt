package app.tripplanner.shared.feature.trips

import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.core.model.Trip
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Phase 0 spike fixture (docs/phase0-spike.md tasks 4 + 6): a trip the "New trip" action
 * writes through [app.tripplanner.shared.data.TripRepository] so the Firestore write path,
 * the live `myTrips`/`stops` listeners and the map boundary can be exercised before Places
 * search exists. Replaced by the real create-trip flow in Phase 1.
 */
@OptIn(ExperimentalTime::class)
object SampleTrip {
    /**
     * Builds the fixture trip for [uid], dated from today in the device time zone.
     *
     * Complexity:
     * - **Time:** O(1) (clock read + date arithmetic).
     * - **Space:** O(1) - one [Trip] with single-element member/role collections.
     */
    fun trip(uid: String): Trip {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        return Trip(
            name = "Lisbon long weekend",
            startDate = today.toString(),
            endDate = today.plus(2, DateTimeUnit.DAY).toString(),
            timeZone = tz.id,
            ownerId = uid,
            memberIds = listOf(uid),
            roles = mapOf(uid to "owner"), // lower-case: firestore.rules compares role(t) == 'owner'
        )
    }

    /**
     * The fixture stops, in itinerary order (day 0 first).
     *
     * Complexity:
     * - **Time:** O(K) where K is the fixed number of fixture stops (8).
     * - **Space:** O(K) list allocation.
     */
    fun stops(uid: String): List<Stop> = listOf(
        Stop(name = "Belém Tower", lat = 38.6916, lng = -9.2160, address = "Av. Brasília, Lisboa", day = 0, addedBy = uid),
        Stop(name = "Jerónimos Monastery", lat = 38.6979, lng = -9.2068, address = "Praça do Império, Lisboa", day = 0, durationMin = 90, addedBy = uid),
        Stop(name = "Pastéis de Belém", lat = 38.6975, lng = -9.2033, address = "R. de Belém 84, Lisboa", day = 0, durationMin = 30, addedBy = uid),
        Stop(name = "LX Factory", lat = 38.7034, lng = -9.1786, address = "R. Rodrigues de Faria 103, Lisboa", day = 0, durationMin = 120, addedBy = uid),
        Stop(name = "Time Out Market", lat = 38.7070, lng = -9.1458, address = "Av. 24 de Julho 49, Lisboa", day = 1, addedBy = uid),
        Stop(name = "Praça do Comércio", lat = 38.7077, lng = -9.1366, address = "Praça do Comércio, Lisboa", day = 1, durationMin = 45, addedBy = uid),
        Stop(name = "Castelo de São Jorge", lat = 38.7139, lng = -9.1335, address = "R. de Santa Cruz do Castelo, Lisboa", day = 1, durationMin = 90, addedBy = uid),
        Stop(name = "Miradouro da Senhora do Monte", lat = 38.7195, lng = -9.1327, address = "Largo Monte, Lisboa", day = 2, durationMin = 30, addedBy = uid),
    )
}
