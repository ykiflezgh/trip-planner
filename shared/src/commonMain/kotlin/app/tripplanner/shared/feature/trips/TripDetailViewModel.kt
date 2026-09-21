package app.tripplanner.shared.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.data.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripDetailUiState(
    val loading: Boolean = true,
    val stopsByDay: Map<Int, List<Stop>> = emptyMap(),
)

class TripDetailViewModel(
    private val tripId: String,
    private val repo: TripRepository,
) : ViewModel() {

    /**
     * UI state stream. Emits a new [TripDetailUiState] when stops change.
     *
     * Complexity:
     * - **Time:** O(S) per emission, where S is the number of stops in the trip (due to `stops.groupBy { it.day }`).
     * - **Space:** O(S) auxiliary heap space to hold the grouped map and stop lists.
     */
    val state: StateFlow<TripDetailUiState> =
        repo.stops(tripId)
            .map { stops -> TripDetailUiState(loading = false, stopsByDay = stops.groupBy { it.day }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

    /**
     * Reorder = one write of a new fractional key; neighbours come from current UI order.
     *
     * Complexity:
     * - **Time:** O(L) where L is the length of the fractional index key (effectively O(1)) + coroutine launch.
     * - **Space:** O(1) auxiliary heap space.
     */
    fun moveStop(stopId: String, day: Int, afterOrder: String?, beforeOrder: String?) {
        viewModelScope.launch { repo.moveStop(tripId, stopId, day, afterOrder, beforeOrder) }
        // TODO surface NOT_FOUND (stop deleted concurrently) as a toast per design SS7
    }
}
