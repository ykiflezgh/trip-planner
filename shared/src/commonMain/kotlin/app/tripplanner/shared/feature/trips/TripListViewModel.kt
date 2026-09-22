package app.tripplanner.shared.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.core.util.FractionalIndex
import app.tripplanner.shared.data.AuthRepository
import app.tripplanner.shared.data.TripRepository
import app.tripplanner.shared.platform.SignInCancelledException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripListUiState(
    val loading: Boolean = true,
    val trips: List<Trip> = emptyList(),
    val signedIn: Boolean = false,
    val userName: String? = null,
    val signingIn: Boolean = false,
    val creating: Boolean = false,
    val error: String? = null,
)

class TripListViewModel(
    private val auth: AuthRepository,
    private val repo: TripRepository,
) : ViewModel() {

    private data class Local(val signingIn: Boolean = false, val creating: Boolean = false, val error: String? = null)
    private val local = MutableStateFlow(Local())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val remote: Flow<TripListUiState> = auth.user.flatMapLatest { user ->
        if (user == null) {
            flowOf(TripListUiState(loading = false))
        } else {
            val signedIn = TripListUiState(loading = false, signedIn = true, userName = user.displayName ?: user.email)
            repo.myTrips(user.uid)
                .map { signedIn.copy(trips = it) }
                // e.g. PERMISSION_DENIED until firestore.rules are deployed: keep the screen alive.
                .catch { emit(signedIn.copy(error = it.message ?: "Could not load trips")) }
        }
    }

    /**
     * Combined UI state stream merging authentication status, remote trip list, and local UI flags.
     *
     * Complexity:
     * - **Time:** O(1) combining state references (shallow copy of data classes).
     * - **Space:** O(1) auxiliary state object allocation per state change.
     */
    val state: StateFlow<TripListUiState> =
        combine(remote, local) { r, l ->
            r.copy(signingIn = l.signingIn, creating = l.creating, error = l.error ?: r.error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripListUiState())

    /**
     * Initiates Google Sign-In flow.
     *
     * Complexity:
     * - **Time:** O(1) dispatch + external auth flow latency.
     * - **Space:** O(1).
     */
    fun signIn() {
        if (local.value.signingIn) return
        viewModelScope.launch {
            local.value = local.value.copy(signingIn = true, error = null)
            try {
                auth.signInWithGoogle()
                local.value = local.value.copy(signingIn = false)
            } catch (_: SignInCancelledException) {
                local.value = local.value.copy(signingIn = false)
            } catch (e: Exception) {
                local.value = local.value.copy(signingIn = false, error = e.message ?: (e::class.simpleName ?: "Sign-in failed"))
            }
        }
    }

    /**
     * Signs out the current authenticated user.
     *
     * Complexity:
     * - **Time:** O(1) dispatch + auth sign out call.
     * - **Space:** O(1).
     */
    fun signOut() {
        viewModelScope.launch { runCatching { auth.signOut() } }
    }

    /**
     * Phase 0 spike: create [SampleTrip] and seed its stops. The trip shows up through the
     * live `myTrips` listener, not through local state - that round trip is the point.
     *
     * Complexity:
     * - **Time:** O(K · L) where K is the number of seeded stops (one sequential write each) and
     *   L is the fractional index key length; dominated by K round trips to Firestore.
     * - **Space:** O(K) for the fixture list + O(L) for the running order key.
     */
    fun createSampleTrip() {
        val uid = auth.currentUser?.uid ?: return
        if (local.value.creating) return
        viewModelScope.launch {
            local.value = local.value.copy(creating = true, error = null)
            try {
                val tripId = repo.createTrip(SampleTrip.trip(uid))
                var previousOrder: String? = null
                SampleTrip.stops(uid).forEach { stop ->
                    repo.addStop(tripId, stop, afterOrder = previousOrder, beforeOrder = null)
                    previousOrder = FractionalIndex.between(previousOrder, null) // same key addStop assigned
                }
            } catch (e: Exception) {
                local.value = local.value.copy(error = e.message ?: (e::class.simpleName ?: "Could not create trip"))
            } finally {
                local.value = local.value.copy(creating = false)
            }
        }
    }

    /**
     * Dismisses the currently displayed error message.
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun dismissError() {
        local.value = local.value.copy(error = null)
    }
}
