package app.tripplanner.shared.feature.stops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.data.AuthRepository
import app.tripplanner.shared.data.PlacesApi
import app.tripplanner.shared.data.PlacesApi.PlaceSuggestion
import app.tripplanner.shared.data.TripRepository
import app.tripplanner.shared.platform.ConnectivityMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddStopUiState(
    val query: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val searching: Boolean = false,
    val adding: Boolean = false,
    val error: String? = null,
    /** Set when the stop write is acknowledged locally; the sheet closes and selects it. */
    val addedStopId: String? = null,
    val online: Boolean = true,
) {
    val needsMoreInput: Boolean get() = !PlacesSearch.shouldQuery(query)
}

/**
 * Search sheet driver (design §8.1): debounced autocomplete (>= 3 chars, >= 250 ms), details
 * fetch on selection, stop written at the end of the chosen day. One session token spans the
 * typing series and the details call, then a fresh one starts.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AddStopViewModel(
    private val tripId: String,
    private val places: PlacesApi,
    private val repo: TripRepository,
    private val auth: AuthRepository,
    connectivity: ConnectivityMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(AddStopUiState(online = connectivity.online.value))
    val state: StateFlow<AddStopUiState> = _state.asStateFlow()
    private var sessionToken = PlacesSearch.newSessionToken()

    init {
        // Search needs connectivity (design §9): the sheet disables the field with a message.
        connectivity.online.onEach { online -> _state.update { it.copy(online = online) } }.launchIn(viewModelScope)
        _state.map { it.query.trim() }
            .debounce(PlacesSearch.DEBOUNCE_MS)
            .distinctUntilChanged()
            .mapLatest { q ->
                if (!PlacesSearch.shouldQuery(q) || !_state.value.online) return@mapLatest Result.success(emptyList())
                _state.update { it.copy(searching = true, error = null) }
                runCatching { places.autocomplete(q, sessionToken) }
            }
            .onEach { result ->
                _state.update { s ->
                    result.fold(
                        onSuccess = { s.copy(suggestions = it, searching = false) },
                        onFailure = { s.copy(suggestions = emptyList(), searching = false, error = it.message ?: "Search failed") },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Complexity:
     * - **Time:** O(1) state copy (the search itself runs debounced in [init]).
     * - **Space:** O(N) for the new query string.
     */
    fun setQuery(query: String) = _state.update { it.copy(query = query, error = null, addedStopId = null) }

    /**
     * Details fetch (closes the billing session) then a single stop write after [afterOrder] on
     * [day]. The write applies locally at once and the sheet closes on the new id; the row shows
     * "syncing…" until the server acknowledges, and a rejection surfaces on the detail screen (§9).
     *
     * Complexity:
     * - **Time:** O(1) network details call + O(L) order key + O(1) document write.
     * - **Space:** O(1).
     */
    fun add(suggestion: PlaceSuggestion, day: Int, afterOrder: String?) {
        if (_state.value.adding) return
        val uid = auth.currentUser?.uid ?: run { _state.update { it.copy(error = "Sign in first") }; return }
        viewModelScope.launch {
            _state.update { it.copy(adding = true, error = null) }
            try {
                val token = sessionToken
                sessionToken = PlacesSearch.newSessionToken() // the details call ends this session
                val place = places.details(suggestion.placeId, token)
                val stop = Stop(
                    placeId = place.id.ifBlank { suggestion.placeId },
                    name = place.displayName.text.ifBlank { suggestion.primaryText },
                    lat = place.location.latitude,
                    lng = place.location.longitude,
                    address = place.formattedAddress,
                    day = day,
                    addedBy = uid,
                )
                val id = repo.addStop(tripId, stop, afterOrder = afterOrder, beforeOrder = null)
                _state.update { it.copy(adding = false, addedStopId = id, query = "", suggestions = emptyList()) }
            } catch (e: Exception) {
                _state.update { it.copy(adding = false, error = e.message ?: "Could not add stop") }
            }
        }
    }

    /**
     * Custom entry with no place (design v1.1 §3.1 item 3): "Flight to Rome", "Free time". Works
     * offline like any stop write; the travel trigger skips it.
     *
     * Complexity:
     * - **Time:** O(1) queued write.
     * - **Space:** O(1).
     */
    fun addCustom(name: String, durationMin: Int, day: Int, afterOrder: String?, fixedStart: String? = null, beforeOrder: String? = null): Boolean {
        val title = name.trim().take(MAX_CUSTOM_NAME)
        if (title.isBlank()) return false
        val uid = auth.currentUser?.uid ?: run { _state.update { it.copy(error = "Sign in first") }; return false }
        val stop = Stop(kind = Stop.KIND_CUSTOM, name = title, day = day, durationMin = durationMin.coerceIn(5, 1440), fixedStart = fixedStart, addedBy = uid)
        val id = repo.addStop(tripId, stop, afterOrder = afterOrder, beforeOrder = beforeOrder)
        _state.update { it.copy(addedStopId = id) }
        return true
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun consumeAdded() = _state.update { it.copy(addedStopId = null) }

    companion object { const val MAX_CUSTOM_NAME = 80 }
}
