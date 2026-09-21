package app.tripplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import app.tripplanner.shared.feature.trips.TripListViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Phase 0/1 skeleton: trip list + Google sign-in. Navigation graph, trip detail with
 * day tabs + drag reorder, and the map screen land in Phase 1 (see planning/).
 *
 * Complexity:
 * - **Recomposition Time:** O(T) where T is the number of trips to render into [ListItem] composables.
 * - **Composition Memory:** O(T) node hierarchy and layout measurements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme {
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
                    else -> Column(Modifier.fillMaxSize()) {
                        state.trips.forEach { trip ->
                            ListItem(
                                headlineContent = { Text(trip.name) },
                                supportingContent = { Text("${trip.startDate} – ${trip.endDate}") },
                            )
                        }
                    }
                }
            }
        }
    }
}

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
