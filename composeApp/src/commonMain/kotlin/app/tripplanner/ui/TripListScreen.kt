package app.tripplanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.feature.trips.TripListViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Trip list with sign-in / sign-out and the Phase 0 "New trip" action.
 *
 * Complexity:
 * - **Recomposition Time:** O(V) where V is the number of trips visible in the [LazyColumn]
 *   viewport (items are keyed by id, so unchanged rows are skipped).
 * - **Composition Memory:** O(V) node hierarchy and layout measurements; off-screen trips are
 *   held only in the state list, O(T).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(onOpenTrip: (Trip) -> Unit, onNewTrip: () -> Unit) {
    val vm: TripListViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.dismissError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                actions = {
                    if (state.signedIn) TextButton(onClick = vm::signOut) { Text("Sign out") }
                },
            )
        },
        floatingActionButton = {
            if (state.signedIn) {
                ExtendedFloatingActionButton(onClick = onNewTrip) { Text("New trip") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                !state.signedIn -> SignedOut(signingIn = state.signingIn, onSignIn = vm::signIn)
                state.trips.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Signed in as ${state.userName ?: "you"}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("No trips yet — create one")
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.trips, key = { it.id }) { trip ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenTrip(trip) },
                            headlineContent = { Text(trip.name) },
                            supportingContent = { Text("${trip.startDate} \u2013 ${trip.endDate}" + if (trip.pendingSync) "  \u00b7 syncing\u2026" else "") },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Signed-out empty state.
 *
 * Complexity:
 * - **Recomposition Time:** O(1).
 * - **Composition Memory:** O(1).
 */
@Composable
private fun SignedOut(signingIn: Boolean, onSignIn: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sign in to see your trips")
        Button(onClick = onSignIn, enabled = !signingIn) {
            if (signingIn) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Sign in with Google")
        }
    }
}
