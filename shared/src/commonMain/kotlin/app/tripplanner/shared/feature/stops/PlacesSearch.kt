package app.tripplanner.shared.feature.stops

import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.core.util.FractionalIndex
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Pure rules of the add-stop flow (design §8.1), kept Firebase-free for unit tests. */
object PlacesSearch {
    const val MIN_QUERY_LENGTH = 3
    const val DEBOUNCE_MS = 250L

    /**
     * Complexity:
     * - **Time:** O(N) for the trim of an N-character query.
     * - **Space:** O(1).
     */
    fun shouldQuery(input: String): Boolean = input.trim().length >= MIN_QUERY_LENGTH

    /**
     * One token per search series; a details call ends the series (single billed session).
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    @OptIn(ExperimentalUuidApi::class)
    fun newSessionToken(): String = Uuid.random().toString()

    /**
     * Order key that places a new stop after the last stop of a day.
     *
     * Complexity:
     * - **Time:** O(S) to find the maximal key among the day's S stops, plus O(L) key generation.
     * - **Space:** O(L) for the new key.
     */
    fun appendOrderKey(stopsOfDay: List<Stop>): String =
        FractionalIndex.between(stopsOfDay.maxOfOrNull { it.order }?.ifEmpty { null }, null)
}
