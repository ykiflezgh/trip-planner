package app.tripplanner.shared.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.shared.core.model.ActivityEvent
import app.tripplanner.shared.data.ActivityRepository
import app.tripplanner.shared.data.AuthRepository
import app.tripplanner.shared.feature.notifications.NotificationText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One rendered feed row. */
data class ActivityRow(val event: ActivityEvent, val text: String, val mine: Boolean)

data class ActivityFeedUiState(
    val loading: Boolean = true,
    val rows: List<ActivityRow> = emptyList(),
    val error: String? = null,
)

class ActivityFeedViewModel(
    tripId: String,
    repo: ActivityRepository,
    auth: AuthRepository,
) : ViewModel() {

    /**
     * Complexity:
     * - **Time:** O(E) per snapshot to describe the E events.
     * - **Space:** O(E).
     */
    val state: StateFlow<ActivityFeedUiState> =
        repo.activity(tripId)
            .map { events ->
                val me = auth.currentUser?.uid
                ActivityFeedUiState(loading = false, rows = events.map { ActivityRow(it, NotificationText.describe(it), mine = it.actorId == me) })
            }
            .catch { emit(ActivityFeedUiState(loading = false, error = it.message ?: "Could not load activity")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityFeedUiState())
}
