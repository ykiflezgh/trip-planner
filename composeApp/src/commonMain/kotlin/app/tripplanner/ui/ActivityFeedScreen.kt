package app.tripplanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.shared.core.model.ActivityEvent
import app.tripplanner.shared.feature.activity.ActivityFeedViewModel
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

/**
 * Who changed what, newest first (design §10). Rows come pre-worded from the view model; the
 * same wording is used for Android notifications.
 *
 * Complexity:
 * - **Recomposition Time:** O(V) for the V visible rows (LazyColumn); O(1) per row change.
 * - **Composition Memory:** O(V).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityFeedScreen(tripId: String, name: String, onBack: () -> Unit) {
    val vm: ActivityFeedViewModel = koinViewModel(key = "activity:$tripId", parameters = { parametersOf(tripId) })
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (name.isBlank()) "Activity" else "Activity · $name") },
                navigationIcon = { TextButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.error != null -> Text(state.error!!, Modifier.padding(24.dp))
                state.rows.isEmpty() -> Text("No changes yet", style = MaterialTheme.typography.bodyMedium)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.event.id }) { row ->
                        ListItem(
                            headlineContent = { Text(row.text) },
                            supportingContent = { Text(timeLabel(row.event) + if (row.mine) " · you" else "") },
                        )
                    }
                }
            }
        }
    }
}

/**
 * "2026-09-23 14:05" in the device zone; blank until the server timestamp lands.
 *
 * Complexity:
 * - **Time:** O(1).
 * - **Space:** O(1).
 */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun timeLabel(e: ActivityEvent): String {
    val ts = e.createdAt as? Timestamp ?: return "sending…"
    val ldt = Instant.fromEpochSeconds(ts.seconds).toLocalDateTime(TimeZone.currentSystemDefault())
    val hh = ldt.hour.toString().padStart(2, '0')
    val mm = ldt.minute.toString().padStart(2, '0')
    return "${ldt.date} $hh:$mm"
}
