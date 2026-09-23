package app.tripplanner.shared.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.shared.core.model.Event
import app.tripplanner.shared.core.model.StopPlace
import app.tripplanner.shared.data.AuthRepository
import app.tripplanner.shared.data.PlacesApi
import app.tripplanner.shared.data.PlacesApi.PlaceSuggestion
import app.tripplanner.shared.data.TripRepository
import app.tripplanner.shared.platform.ConnectivityMonitor
import dev.gitlive.firebase.firestore.GeoPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEventUiState(
    val query: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val searching: Boolean = false,
    val adding: Boolean = false,
    val error: String? = null,
    /** Set once the write is issued; the sheet closes and selects it. */
    val addedEventId: String? = null,
    val online: Boolean = true,
) {
    val needsMoreInput: Boolean get() = query.trim().length < PlacesSearch.MIN_QUERY_LENGTH
}

/**
 * Add an event to a day (design v1.2 §8.1): either one that contains a stop found through Places
 * autocomplete (debounced, one session token per search series), or a plain event with no place.
 */
class AddEventViewModel(
    private val tripId: String,
    private val places: PlacesApi,
    private val repo: TripRepository,
    private val auth: AuthRepository,
    connectivity: ConnectivityMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(AddEventUiState())
    val state: StateFlow<AddEventUiState> = _state.asStateFlow()
    private var sessionToken = PlacesSearch.newSessionToken()

    init {
        viewModelScope.launch { connectivity.online.collect { on -> _state.update { it.copy(online = on) } } }
        viewModelScope.launch { searchResults().collect() }
    }

    /**
     * Complexity:
     * - **Time:** O(1) per keystroke; one Places call per debounce window.
     * - **Space:** O(N) for the N suggestions held.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun searchResults() = _state.map { it.query }
        .distinctUntilChanged()
        .debounce(PlacesSearch.DEBOUNCE_MS)
        .mapLatest { q ->
            if (q.trim().length < PlacesSearch.MIN_QUERY_LENGTH || !_state.value.online) {
                _state.update { it.copy(suggestions = emptyList(), searching = false) }
                return@mapLatest
            }
            _state.update { it.copy(searching = true, error = null) }
            try {
                val results = places.autocomplete(q.trim(), sessionToken)
                _state.update { it.copy(suggestions = results, searching = false) }
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, error = e.message ?: "Search failed") }
            }
        }

    private suspend fun kotlinx.coroutines.flow.Flow<Unit>.collect() = collect { }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun setQuery(query: String) = _state.update { it.copy(query = query, error = null, addedEventId = null) }

    /**
     * Fetches details for [suggestion] (ends the Places session) and adds an event containing that
     * stop at the end of [day] (or between the given neighbours). The write is fire-and-forget.
     *
     * Complexity:
     * - **Time:** O(1) details call + one queued write.
     * - **Space:** O(1).
     */
    fun addPlace(suggestion: PlaceSuggestion, day: Int, afterOrder: String?, beforeOrder: String? = null) {
        if (_state.value.adding) return
        val uid = auth.currentUser?.uid ?: run { _state.update { it.copy(error = "Sign in first") }; return }
        viewModelScope.launch {
            _state.update { it.copy(adding = true, error = null) }
            try {
                val token = sessionToken
                sessionToken = PlacesSearch.newSessionToken() // the details call ends this session
                val place = places.details(suggestion.placeId, token)
                val name = place.displayName.text.ifBlank { suggestion.primaryText }
                val event = Event(
                    title = name,
                    day = day,
                    stop = StopPlace(
                        placeId = place.id.ifBlank { suggestion.placeId },
                        name = name,
                        location = GeoPoint(place.location.latitude, place.location.longitude),
                        address = place.formattedAddress,
                    ),
                    addedBy = uid,
                )
                val id = repo.addEvent(tripId, event, afterOrder = afterOrder, beforeOrder = beforeOrder)
                _state.update { it.copy(adding = false, addedEventId = id, query = "", suggestions = emptyList()) }
            } catch (e: Exception) {
                _state.update { it.copy(adding = false, error = e.message ?: "Could not add stop") }
            }
        }
    }

    /**
     * Plain event with no stop (design v1.2 §3.1 item 3): "Flight to Rome", "Free time". Works
     * offline like any event write; the travel trigger skips it.
     *
     * Complexity:
     * - **Time:** O(1) queued write.
     * - **Space:** O(1).
     */
    fun addPlain(title: String, durationMin: Int, day: Int, afterOrder: String?, fixedStart: String? = null, beforeOrder: String? = null): Boolean {
        val clean = title.trim().take(MAX_TITLE)
        if (clean.isBlank()) return false
        val uid = auth.currentUser?.uid ?: run { _state.update { it.copy(error = "Sign in first") }; return false }
        val event = Event(title = clean, day = day, durationMin = durationMin.coerceIn(5, 1440), fixedStart = fixedStart, addedBy = uid)
        val id = repo.addEvent(tripId, event, afterOrder = afterOrder, beforeOrder = beforeOrder)
        _state.update { it.copy(addedEventId = id) }
        return true
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun consumeAdded() = _state.update { it.copy(addedEventId = null) }

    companion object { const val MAX_TITLE = 80 }
}
