package app.tripplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.shared.feature.invites.JoinTripViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Redeems an invite code (design §8.2): spinner while the Function runs, then either the
 * trip opens or a clear message with retry / back.
 *
 * Complexity:
 * - **Recomposition Time:** O(1).
 * - **Composition Memory:** O(1).
 */
@Composable
fun JoinTripScreen(code: String, onJoined: (tripId: String, name: String) -> Unit, onBack: () -> Unit) {
    val vm: JoinTripViewModel = koinViewModel(key = "join-$code", parameters = { parametersOf(code) })
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.joinedTripId) {
        state.joinedTripId?.let { id ->
            vm.consumeJoined()
            onJoined(id, state.joinedTripName)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            state.joining -> {
                CircularProgressIndicator()
                Text("Joining trip…", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge)
            }
            state.error != null -> {
                Text("Couldn't join", style = MaterialTheme.typography.titleLarge)
                Text(state.error!!, Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                Button(onClick = vm::join) { Text("Try again") }
                TextButton(onClick = onBack) { Text("Back to trips") }
            }
        }
    }
}
