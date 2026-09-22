package app.tripplanner

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.tripplanner.ui.NewTripScreen
import app.tripplanner.ui.TripDetailScreen
import app.tripplanner.ui.TripListScreen
import kotlinx.serialization.Serializable

@Serializable
object TripListRoute

@Serializable
object NewTripRoute

@Serializable
data class TripDetailRoute(val tripId: String, val name: String)

/**
 * Trip list (+ Google sign-in) -> new trip form / trip detail (map). Day tabs, drag reorder
 * and Places search are the remaining Phase 1 items (see planning/).
 *
 * Complexity:
 * - **Recomposition Time:** O(1) - the NavHost composes exactly one destination; per-screen
 *   costs are documented on [TripListScreen] and [TripDetailScreen].
 * - **Composition Memory:** O(B) where B is the back-stack depth (at most 2 entries here).
 */
@Composable
fun App() {
    MaterialTheme {
        val nav = rememberNavController()
        NavHost(nav, startDestination = TripListRoute) {
            composable<TripListRoute> {
                TripListScreen(
                    onOpenTrip = { trip -> nav.navigate(TripDetailRoute(trip.id, trip.name)) },
                    onNewTrip = { nav.navigate(NewTripRoute) },
                )
            }
            composable<NewTripRoute> {
                NewTripScreen(
                    onCreated = { id, name ->
                        // Replace the form with the new trip so Back returns to the list.
                        nav.navigate(TripDetailRoute(id, name)) { popUpTo<TripListRoute>() }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<TripDetailRoute> { entry ->
                val route = entry.toRoute<TripDetailRoute>()
                TripDetailScreen(tripId = route.tripId, name = route.name, onBack = { nav.popBackStack() })
            }
        }
    }
}
