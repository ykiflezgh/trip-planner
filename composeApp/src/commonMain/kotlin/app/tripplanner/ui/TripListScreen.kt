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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.feature.links.DeepLinkIntake
import app.tripplanner.shared.feature.trips.TripListViewModel
import org.koin.compose.koinInject
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
    val intake: DeepLinkIntake = koinInject()
    var showMenu by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var joinInput by remember { mutableStateOf("") }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.dismissError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                actions = {
                    if (state.signedIn) {
                        TextButton(onClick = { joinInput = ""; showJoin = true }) { Text("Join") }
                        // material-icons is not in commonMain; text glyph for the overflow menu.
                        TextButton(onClick = { showMenu = true }) { Text("\u22ee", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Notifications") },
                                trailingIcon = { Switch(checked = state.pushEnabled, onCheckedChange = null) },
                                onClick = { vm.setPushEnabled(!state.pushEnabled) },
                            )
                            DropdownMenuItem(text = { Text("Sign out") }, onClick = { showMenu = false; vm.signOut() })
                        }
                    }
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
        if (showJoin) {
            JoinDialog(joinInput, { joinInput = it }, onJoin = { intake.offer(joinInput) }, onDismiss = { showJoin = false })
        }
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
/**
 * "Join a trip": paste an invite link or code; the navigation graph redeems it (design §8.2).
 *
 * Complexity:
 * - **Recomposition Time:** O(1) per keystroke.
 * - **Composition Memory:** O(N) for the N-character input.
 */
@Composable
private fun JoinDialog(input: String, onInput: (String) -> Unit, onJoin: () -> Boolean, onDismiss: () -> Unit) {
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a trip") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { onInput(it); invalid = false },
                label = { Text("Invite link or code") },
                singleLine = true,
                isError = invalid,
                supportingText = { if (invalid) Text("That doesn't look like an invite link or code") },
            )
        },
        confirmButton = { Button(onClick = { if (onJoin()) onDismiss() else invalid = true }) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
