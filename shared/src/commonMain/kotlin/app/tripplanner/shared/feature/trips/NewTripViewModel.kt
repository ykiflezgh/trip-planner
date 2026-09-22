package app.tripplanner.shared.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.shared.data.AuthRepository
import app.tripplanner.shared.data.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

data class NewTripUiState(
    val name: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val creating: Boolean = false,
    val error: String? = null,
    /** Set once the write is acknowledged locally; the screen navigates and calls [NewTripViewModel.consumeCreated]. */
    val createdTripId: String? = null,
) {
    val validationError: String? get() = NewTrip.validate(name, startDate, endDate)
    val canCreate: Boolean get() = validationError == null && !creating
}

/**
 * Create-trip flow (design §3.1 (2)). The time zone defaults to the device zone; the
 * creator becomes owner + sole member ([NewTrip]).
 */
class NewTripViewModel(
    private val auth: AuthRepository,
    private val repo: TripRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NewTripUiState())
    val state: StateFlow<NewTripUiState> = _state.asStateFlow()

    /**
     * Complexity:
     * - **Time:** O(1) state copy.
     * - **Space:** O(N) for the new name string.
     */
    fun setName(name: String) = _state.update { it.copy(name = name, error = null) }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun setDates(start: LocalDate?, end: LocalDate?) = _state.update { it.copy(startDate = start, endDate = end, error = null) }

    /**
     * Writes the trip; Firestore acknowledges from the local cache first, so this resolves
     * offline too (design §9).
     *
     * Complexity:
     * - **Time:** O(1) document write plus the name copy.
     * - **Space:** O(1).
     */
    fun create() {
        val s = _state.value
        if (!s.canCreate) return
        val uid = auth.currentUser?.uid ?: run { _state.update { it.copy(error = "Sign in first") }; return }
        val trip = NewTrip.build(uid, s.name, s.startDate!!, s.endDate!!, TimeZone.currentSystemDefault())
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            try {
                val id = repo.createTrip(trip)
                _state.update { it.copy(creating = false, createdTripId = id) }
            } catch (e: Exception) {
                _state.update { it.copy(creating = false, error = e.message ?: "Could not create trip") }
            }
        }
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun consumeCreated() = _state.update { it.copy(createdTripId = null) }
}
