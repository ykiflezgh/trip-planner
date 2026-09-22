package app.tripplanner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.shared.feature.trips.NewTripViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Create-trip form (design §3.1 (2)): name + date range; time zone defaults to the device
 * zone inside the ViewModel.
 *
 * Complexity:
 * - **Recomposition Time:** O(1) per keystroke or date change (the picker recomposes its own
 *   month grid, O(D) for the D visible days, only when its selection changes).
 * - **Composition Memory:** O(D) picker grid nodes plus O(N) for the name text.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun NewTripScreen(onCreated: (tripId: String, name: String) -> Unit, onBack: () -> Unit) {
    val vm: NewTripViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val dateRange = rememberDateRangePickerState()

    // Material's picker reports UTC-midnight epoch millis; the trip stores plain dates.
    LaunchedEffect(dateRange.selectedStartDateMillis, dateRange.selectedEndDateMillis) {
        vm.setDates(dateRange.selectedStartDateMillis?.toLocalDate(), dateRange.selectedEndDateMillis?.toLocalDate())
    }
    LaunchedEffect(state.createdTripId) {
        state.createdTripId?.let { id ->
            vm.consumeCreated()
            onCreated(id, state.name.trim())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New trip") },
                navigationIcon = { TextButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::setName,
                label = { Text("Trip name") },
                singleLine = true,
                isError = state.name.isNotEmpty() && state.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            DateRangePicker(
                state = dateRange,
                modifier = Modifier.weight(1f),
                showModeToggle = true,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = vm::create,
                enabled = state.canCreate,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                if (state.creating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(state.validationError ?: "Create trip")
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
