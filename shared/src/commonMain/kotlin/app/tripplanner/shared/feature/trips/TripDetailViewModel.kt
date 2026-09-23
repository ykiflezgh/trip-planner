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
    val selectedStopId: String? = null,
    val error: String? = null,
) {
    /** Days in the trip (inclusive of both ends); 1 until the trip document arrives. */
    val dayCount: Int get() = trip?.let { TripDays.count(it) } ?: 1
}

class TripDetailViewModel(
    private val tripId: String,
    private val repo: TripRepository,
) : ViewModel() {

    private val log = Logger.withTag("TripDetail")
    private val selectedStopId = MutableStateFlow<String?>(null)

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
        combine(stops, trip, selectedStopId) { s, t, sel -> s.copy(trip = t, selectedStopId = sel) }
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
     * Reorder = one write of a new fractional key; neighbours come from current UI order.
     *
     * Complexity:
     * - **Time:** O(L) where L is the length of the fractional index key (effectively O(1)) + coroutine launch.
     * - **Space:** O(1) auxiliary heap space.
     */
    fun moveStop(stopId: String, day: Int, afterOrder: String?, beforeOrder: String?) {
        viewModelScope.launch { repo.moveStop(tripId, stopId, day, afterOrder, beforeOrder) }
        // TODO surface NOT_FOUND (stop deleted concurrently) as a toast per design §7
    }
}
