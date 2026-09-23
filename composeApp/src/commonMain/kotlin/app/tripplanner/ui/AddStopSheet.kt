package app.tripplanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.feature.stops.AddStopViewModel
import app.tripplanner.shared.feature.stops.PlacesSearch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Places search sheet (design §8.1): pick a day, type >= 3 characters, tap a suggestion.
 * The stop lands at the end of the chosen day and the sheet reports its id through [onAdded].
 *
 * Complexity:
 * - **Recomposition Time:** O(D + V) per state change, for D day chips and V visible
 *   suggestions; typing recomposes only the field and status line, O(1).
 * - **Composition Memory:** O(D + V) nodes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStopSheet(
    tripId: String,
    dayCount: Int,
    stopsByDay: Map<Int, List<Stop>>,
    initialDay: Int,
    onAdded: (stopId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val vm: AddStopViewModel = koinViewModel(key = "add-stop-$tripId", parameters = { parametersOf(tripId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var day by remember { mutableStateOf(initialDay.coerceIn(0, (dayCount - 1).coerceAtLeast(0))) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(state.addedStopId) {
        state.addedStopId?.let { id ->
            vm.consumeAdded()
            onAdded(id)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 16.dp).imePadding()) {
            Text("Add a stop", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(dayCount) { index ->
                    FilterChip(selected = day == index, onClick = { day = index }, label = { Text("Day ${index + 1}") })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("Search places") },
                singleLine = true,
                enabled = state.online,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                trailingIcon = {
                    if (state.searching || state.adding) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                },
            )
            val status = when {
                !state.online -> "Search needs a connection \u2014 you're offline"
                state.error != null -> state.error
                state.needsMoreInput -> "Type at least ${PlacesSearch.MIN_QUERY_LENGTH} characters"
                !state.searching && state.suggestions.isEmpty() -> "No matches"
                else -> null
            }
            status?.let {
                Text(
                    it,
                    Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.error != null || !state.online) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(state.suggestions, key = { it.placeId }) { suggestion ->
                    ListItem(
                        modifier = Modifier.clickable(enabled = !state.adding) {
                            val afterOrder = stopsByDay[day]?.maxOfOrNull { it.order }
                            vm.add(suggestion, day, afterOrder)
                        },
                        headlineContent = { Text(suggestion.primaryText) },
                        supportingContent = { if (suggestion.secondaryText.isNotBlank()) Text(suggestion.secondaryText) },
                    )
                }
            }
        }
    }
}
