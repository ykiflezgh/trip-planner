package app.tripplanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.feature.trips.TripDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Trip detail in its Phase 1 shape, which is also the Phase 0 interop stress test (spike task 7):
 * the platform map is the first item of a scrolling stop list, and selecting a stop (marker or
 * row) opens a modal bottom sheet with a notes text field over the map.
 *
 * Complexity:
 * - **Recomposition Time:** O(S log S) when stops change (days sorted, then flattened), then O(V)
 *   for the V rows in the viewport; O(1) when only the selection or the note changes.
 * - **Composition Memory:** O(V) list rows + the map's own nodes; S markers live in the map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(tripId: String, name: String, onBack: () -> Unit) {
    val vm: TripDetailViewModel = koinViewModel(key = tripId, parameters = { parametersOf(tripId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val stops = remember(state.stopsByDay) { state.stopsByDay.entries.sortedBy { it.key }.flatMap { it.value } }
    val selected = stops.firstOrNull { it.id == state.selectedStopId }
    var showAddStop by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAddStop = true }) { Text("Add stop") }
        },
        topBar = {
            TopAppBar(
                title = { Text(name) },
                // material-icons is not in commonMain (Android only gets it transitively); text arrow for now.
                navigationIcon = { TextButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "map") {
                Box(Modifier.fillMaxWidth().height(MAP_HEIGHT)) {
                    MapView(
                        stops = stops,
                        selectedStopId = state.selectedStopId,
                        onStopTapped = vm::selectStop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    when {
                        state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        state.error != null -> Text(state.error!!, Modifier.align(Alignment.Center).padding(24.dp))
                    }
                }
            }
            item(key = "header") {
                Text(
                    if (stops.isEmpty() && !state.loading) "No stops yet \u2014 add one" else "Stops",
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(stops, key = { it.id }) { stop ->
                StopRow(stop, selected = stop.id == state.selectedStopId, onClick = { vm.selectStop(stop.id) })
            }
        }
    }

    if (showAddStop) {
        AddStopSheet(
            tripId = tripId,
            dayCount = state.dayCount,
            stopsByDay = state.stopsByDay,
            initialDay = selected?.day ?: 0,
            onAdded = { id -> showAddStop = false; vm.selectStop(id) },
            onDismiss = { showAddStop = false },
        )
    } else {
        selected?.let { stop ->
            StopSheet(stop = stop, onDismiss = { vm.selectStop(null) })
        }
    }
}

private val MAP_HEIGHT = 320.dp

/**
 * One stop in the list; highlighted when selected.
 *
 * Complexity:
 * - **Recomposition Time:** O(1).
 * - **Composition Memory:** O(1).
 */
@Composable
private fun StopRow(stop: Stop, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(stop.name) },
        supportingContent = { Text(stop.address) },
        trailingContent = { Text("Day ${stop.day + 1}") },
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
    )
}

/**
 * Stop details over the map with a notes field: exercises sheet + keyboard + UIKit interop
 * together (spike task 7). Notes are local until Phase 1 persists them.
 *
 * Complexity:
 * - **Recomposition Time:** O(1) per keystroke (only the text field's state changes).
 * - **Composition Memory:** O(N) for the N characters of the note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopSheet(stop: Stop, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by remember(stop.id) { mutableStateOf(stop.notes) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).imePadding()) {
            Text(stop.name, style = MaterialTheme.typography.titleLarge)
            Text(stop.address, style = MaterialTheme.typography.bodyMedium)
            Text("Day ${stop.day + 1} · ${stop.durationMin} min", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
