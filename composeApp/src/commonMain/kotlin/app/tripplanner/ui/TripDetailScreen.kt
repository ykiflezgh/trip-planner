package app.tripplanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.shared.feature.trips.TripDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Phase 0 spike: the platform map over the trip's stops; tapping a marker selects it.
 *
 * Complexity:
 * - **Recomposition Time:** O(S log S) when stops change (days sorted, then flattened) and O(S)
 *   when only the selection changes (linear lookup of the selected stop + S marker updates).
 * - **Composition Memory:** O(S) for the flattened stop list and one marker node per stop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(tripId: String, name: String, onBack: () -> Unit) {
    val vm: TripDetailViewModel = koinViewModel(key = tripId, parameters = { parametersOf(tripId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val stops = remember(state.stopsByDay) { state.stopsByDay.entries.sortedBy { it.key }.flatMap { it.value } }
    val selected = stops.firstOrNull { it.id == state.selectedStopId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                // material-icons is not in commonMain (Android only gets it transitively); text arrow for now.
                navigationIcon = { TextButton(onClick = onBack) { Text("\u2190", style = MaterialTheme.typography.titleLarge) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MapView(
                stops = stops,
                selectedStopId = state.selectedStopId,
                onStopTapped = vm::selectStop,
                modifier = Modifier.fillMaxSize(),
            )
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(state.error!!, Modifier.align(Alignment.Center).padding(24.dp))
                stops.isEmpty() -> Text("No stops yet", Modifier.align(Alignment.Center))
            }
            selected?.let { stop ->
                Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stop.name, style = MaterialTheme.typography.titleMedium)
                        Text(stop.address, style = MaterialTheme.typography.bodyMedium)
                        Text("Day ${stop.day + 1} · ${stop.durationMin} min", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
