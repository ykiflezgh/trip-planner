package app.tripplanner.shared.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.shared.core.model.Trip
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
    val error: String? = null,
)

class TripListViewModel(
    private val auth: AuthRepository,
    private val repo: TripRepository,
) : ViewModel() {

    private data class Local(val signingIn: Boolean = false, val error: String? = null)
    private val local = MutableStateFlow(Local())

    init {
        // Writes apply locally at once; rejections (e.g. rules) arrive here later (design §9).
        viewModelScope.launch { repo.writeFailures.collect { f -> local.value = local.value.copy(error = "Could not ${f.operation}: ${f.message}") } }
    }

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
            r.copy(signingIn = l.signingIn, error = l.error ?: r.error)
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
