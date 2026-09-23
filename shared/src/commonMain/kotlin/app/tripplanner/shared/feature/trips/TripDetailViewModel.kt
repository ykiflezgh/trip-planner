package app.tripplanner.shared.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.data.TripRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripDetailUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val stopsByDay: Map<Int, List<Stop>> = emptyMap(),
    val selectedDay: Int = 0,
    val selectedStopId: String? = null,
    val error: String? = null,
    /** One-shot user message (e.g. a concurrent delete); the screen shows it and calls [TripDetailViewModel.consumeMessage]. */
    val message: String? = null,
) {
    /** Days in the trip (inclusive of both ends); 1 until the trip document arrives. */
    val dayCount: Int get() = trip?.let { TripDays.count(it) } ?: 1
    /** Stops of the selected day in itinerary order (the repository sorts by fractional key). */
    val stopsForSelectedDay: List<Stop> get() = stopsByDay[selectedDay].orEmpty()
}

class TripDetailViewModel(
    private val tripId: String,
    private val repo: TripRepository,
) : ViewModel() {

    private val log = Logger.withTag("TripDetail")
    private val selectedStopId = MutableStateFlow<String?>(null)
    private val selectedDay = MutableStateFlow(0)
    private val message = MutableStateFlow<String?>(null)

    private val stops = repo.stops(tripId)
        .map { stops -> TripDetailUiState(loading = false, stopsByDay = stops.groupBy { it.day }) }
        .catch { emit(TripDetailUiState(loading = false, error = it.message ?: "Could not load stops")) }
    private val trip = repo.trip(tripId).catch { emit(null) }

    /**
     * UI state stream. Emits a new [TripDetailUiState] when stops or the selection change.
     *
     * Complexity:
     * - **Time:** O(S) per stops emission, where S is the number of stops in the trip (due to
     *   `stops.groupBy { it.day }`); O(1) per trip or selection change (shallow copy).
     * - **Space:** O(S) auxiliary heap space to hold the grouped map and stop lists.
     */
    val state: StateFlow<TripDetailUiState> =
        combine(stops, trip, selectedStopId, selectedDay, message) { s, t, sel, day, msg ->
            s.copy(trip = t, selectedStopId = sel, selectedDay = day, message = msg)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

    /**
     * Map-marker tap lands here (platform map -> Kotlin, design §6.4). `null` clears.
     *
     * Complexity:
     * - **Time:** O(1) state update + one log line.
     * - **Space:** O(1).
     */
    fun selectStop(stopId: String?) {
        log.i { "selectStop($stopId)" }
        selectedStopId.value = stopId
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun selectDay(day: Int) {
        selectedDay.value = day.coerceAtLeast(0)
    }

    /**
     * Reorder = one write of a new fractional key; neighbours come from current UI order.
     * A NOT_FOUND from Firestore means another member deleted the stop: the local change is
     * dropped by the next snapshot and the user gets a message (design §7).
     *
     * Complexity:
     * - **Time:** O(L) where L is the length of the fractional index key (effectively O(1)) + one document write.
     * - **Space:** O(1) auxiliary heap space.
     */
    fun moveStop(stopId: String, day: Int, afterOrder: String?, beforeOrder: String?) {
        viewModelScope.launch {
            try {
                repo.moveStop(tripId, stopId, day, afterOrder, beforeOrder)
            } catch (e: Exception) {
                log.w { "moveStop failed: ${e.message}" }
                message.value = if (e.isNotFound()) "That stop was removed by another member" else (e.message ?: "Could not move stop")
            }
        }
    }

    /**
     * Cross-day move: the stop lands at the end of [day].
     *
     * Complexity:
     * - **Time:** O(1) lookup of the target day's last key + one write.
     * - **Space:** O(1).
     */
    fun moveToDay(stopId: String, day: Int) {
        val last = state.value.stopsByDay[day]?.lastOrNull()?.order
        moveStop(stopId, day, afterOrder = last, beforeOrder = null)
    }

    /**
     * Complexity:
     * - **Time:** O(1) document delete.
     * - **Space:** O(1).
     */
    fun deleteStop(stopId: String) {
        if (selectedStopId.value == stopId) selectedStopId.value = null
        viewModelScope.launch {
            try {
                repo.deleteStop(tripId, stopId)
            } catch (e: Exception) {
                message.value = e.message ?: "Could not remove stop"
            }
        }
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun consumeMessage() {
        message.value = null
    }

    private fun Exception.isNotFound(): Boolean =
        (message ?: "").contains("NOT_FOUND", ignoreCase = true) ||
            (message ?: "").contains("No document to update", ignoreCase = true)
}
