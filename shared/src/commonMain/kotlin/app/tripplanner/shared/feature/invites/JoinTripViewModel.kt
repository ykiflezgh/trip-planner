package app.tripplanner.shared.feature.invites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.shared.data.PlanningFunctions
import app.tripplanner.shared.data.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class JoinTripUiState(
    val joining: Boolean = true,
    val error: String? = null,
    /** Set on success; the screen navigates to the trip and calls [JoinTripViewModel.consumeJoined]. */
    val joinedTripId: String? = null,
    val joinedTripName: String = "",
    val alreadyMember: Boolean = false,
)

/**
 * Redeems an invite code through the Function (design §8.2), then waits for the trip document
 * to become readable (membership is written server-side) so the detail screen opens populated.
 */
class JoinTripViewModel(
    private val code: String,
    private val functions: PlanningFunctions,
    private val repo: TripRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinTripUiState())
    val state: StateFlow<JoinTripUiState> = _state.asStateFlow()

    init { join() }

    /**
     * Complexity:
     * - **Time:** O(1) callable + one document read.
     * - **Space:** O(1).
     */
    fun join() {
        viewModelScope.launch {
            _state.update { it.copy(joining = true, error = null) }
            try {
                val result = functions.redeemInvite(code)
                val trip = withTimeoutOrNull(10_000) { repo.trip(result.tripId).first { it != null } }
                _state.update {
                    it.copy(joining = false, joinedTripId = result.tripId, joinedTripName = trip?.name ?: "Trip", alreadyMember = result.alreadyMember)
                }
            } catch (e: Exception) {
                _state.update { it.copy(joining = false, error = e.message ?: "Could not join this trip") }
            }
        }
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun consumeJoined() = _state.update { it.copy(joinedTripId = null) }
}
