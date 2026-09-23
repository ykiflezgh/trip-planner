package app.tripplanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.feature.stops.StopReorder
import app.tripplanner.shared.feature.trips.TripDays
import app.tripplanner.shared.feature.trips.TripDetailViewModel
import kotlinx.coroutines.flow.first
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Trip detail (design §3.1 (3-4)): day tabs filter the map and the stop list to one day; rows
 * reorder by dragging their handle (one fractional-key write on drop, [StopReorder]); the stop
 * sheet moves a stop to another day or removes it.
 *
 * Complexity:
 * - **Recomposition Time:** O(S log S) when stops change (days sorted, then flattened) and O(V)
 *   for the V rows in the viewport; O(V) per drag frame (the local list is re-emitted); O(1)
 *   when only the selection or day changes.
 * - **Composition Memory:** O(D + V) tab and row nodes plus the map's own nodes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(tripId: String, name: String, onBack: () -> Unit) {
    val vm: TripDetailViewModel = koinViewModel(key = tripId, parameters = { parametersOf(tripId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val dayStops = state.stopsForSelectedDay
    val selected = dayStops.firstOrNull { it.id == state.selectedStopId }
        ?: state.stopsByDay.values.flatten().firstOrNull { it.id == state.selectedStopId }
    var showAddStop by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Local copy reordered while dragging; resets whenever the day's stops change remotely.
    var localStops by remember(dayStops) { mutableStateOf(dayStops) }
    val listState = rememberLazyListState()
    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        localStops = StopReorder.move(localStops, from.key as String, to.key as String)
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                // material-icons is not in commonMain (Android only gets it transitively); text arrow for now.
                navigationIcon = { TextButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAddStop = true }) { Text("Add stop") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.online) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Offline \u2014 changes are saved on this device and sync when you're back online",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            DayTabs(trip = state.trip, dayCount = state.dayCount, selected = state.selectedDay, onSelect = vm::selectDay)
            LazyColumn(Modifier.fillMaxSize().testTagCompat("stop_list"), state = listState) {
                item(key = "map") {
                    Box(Modifier.fillMaxWidth().height(MAP_HEIGHT)) {
                        MapView(
                            stops = localStops,
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
                        when {
                            state.loading -> "Loading…"
                            localStops.isEmpty() -> "No stops on this day — add one"
                            else -> "${localStops.size} stop${if (localStops.size == 1) "" else "s"} · drag ≡ to reorder"
                        } + if (state.pendingSync) "  \u00b7 syncing\u2026" else "",
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(localStops, key = { it.id }) { stop ->
                    ReorderableItem(reorderable, key = stop.id) { isDragging ->
                        StopRow(
                            stop = stop,
                            position = localStops.indexOfFirst { it.id == stop.id } + 1,
                            selected = stop.id == state.selectedStopId,
                            dragging = isDragging,
                            onClick = { vm.selectStop(stop.id) },
                            onDropped = {
                                val (after, before) = StopReorder.neighbours(localStops, stop.id)
                                vm.moveStop(stop.id, state.selectedDay, after, before)
                            },
                        )
                    }
                }
                item(key = "spacer") { Spacer(Modifier.height(88.dp)) } // keep the last row clear of the FAB
            }
        }
    }

    if (showAddStop) {
        AddStopSheet(
            tripId = tripId,
            dayCount = state.dayCount,
            stopsByDay = state.stopsByDay,
            initialDay = state.selectedDay,
            onAdded = { id -> showAddStop = false; vm.selectStop(id) },
            onDismiss = { showAddStop = false },
        )
    } else {
        selected?.let { stop ->
            StopSheet(
                stop = stop,
                trip = state.trip,
                dayCount = state.dayCount,
                onMoveToDay = { day -> vm.moveToDay(stop.id, day); vm.selectDay(day) },
                onRemove = { vm.deleteStop(stop.id) },
                onDismiss = { vm.selectStop(null) },
            )
        }
    }
}

private val MAP_HEIGHT = 320.dp

/** testTag is Android-only semantics today; keep the call site tidy without pulling ui-test deps. */
private fun Modifier.testTagCompat(@Suppress("UNUSED_PARAMETER") tag: String): Modifier = this

/**
 * Day tabs labelled with the calendar date when the trip document is available.
 *
 * Complexity:
 * - **Recomposition Time:** O(D) for D days.
 * - **Composition Memory:** O(D).
 */
@Composable
private fun DayTabs(trip: Trip?, dayCount: Int, selected: Int, onSelect: (Int) -> Unit) {
    ScrollableTabRow(selectedTabIndex = selected.coerceIn(0, (dayCount - 1).coerceAtLeast(0)), edgePadding = 8.dp) {
        repeat(dayCount) { day ->
            Tab(
                selected = day == selected,
                onClick = { onSelect(day) },
                text = { Text(dayLabel(trip, day)) },
            )
        }
    }
}

/**
 * Complexity:
 * - **Time:** O(1).
 * - **Space:** O(1).
 */
private fun dayLabel(trip: Trip?, day: Int): String {
    val date = trip?.let { TripDays.date(it, day) } ?: return "Day ${day + 1}"
    val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "Day ${day + 1} · $month ${date.day}"
}

/**
 * One stop in the day's list with a drag handle; highlighted when selected, raised while dragging.
 *
 * Complexity:
 * - **Recomposition Time:** O(1).
 * - **Composition Memory:** O(1).
 */
@Composable
private fun ReorderableCollectionItemScope.StopRow(
    stop: Stop,
    position: Int,
    selected: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    onDropped: () -> Unit,
) {
    Surface(shadowElevation = if (dragging) 6.dp else 0.dp) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            leadingContent = { Text("$position", style = MaterialTheme.typography.titleMedium) },
            headlineContent = { Text(stop.name) },
            supportingContent = { Text(stop.address + if (stop.pendingSync) "  \u00b7 syncing\u2026" else "") },
            trailingContent = {
                Text(
                    "≡",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 8.dp).draggableHandle(onDragStopped = onDropped),
                )
            },
            colors = if (selected) {
                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            } else {
                ListItemDefaults.colors()
            },
        )
    }
}

/**
 * Stop details over the map: notes (local until Phase 1 persists them), move to another day,
 * remove.
 *
 * Complexity:
 * - **Recomposition Time:** O(D) for the D day chips; O(1) per keystroke.
 * - **Composition Memory:** O(D + N) for the chips and the N-character note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopSheet(
    stop: Stop,
    trip: Trip?,
    dayCount: Int,
    onMoveToDay: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by remember(stop.id) { mutableStateOf(stop.notes) }

    // Perf signpost from the sheet entering composition until it is fully expanded.
    LaunchedEffect(stop.id) { snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).imePadding()) {
            Text(stop.name, style = MaterialTheme.typography.titleLarge)
            Text(stop.address, style = MaterialTheme.typography.bodyMedium)
            Text("${dayLabel(trip, stop.day)} · ${stop.durationMin} min", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            if (dayCount > 1) {
                Spacer(Modifier.height(16.dp))
                Text("Move to", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(dayCount) { day ->
                        FilterChip(
                            selected = day == stop.day,
                            enabled = day != stop.day,
                            onClick = { onMoveToDay(day) },
                            label = { Text("Day ${day + 1}") },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRemove) { Text("Remove stop", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
