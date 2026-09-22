package app.tripplanner

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.tripplanner.ui.TripDetailScreen
import app.tripplanner.ui.TripListScreen
import kotlinx.serialization.Serializable

@Serializable
object TripListRoute

@Serializable
data class TripDetailRoute(val tripId: String, val name: String)

/**
 * Phase 0/1 skeleton: trip list (+ Google sign-in) -> trip detail (map). Day tabs, drag
 * reorder and Places search land in Phase 1 (see planning/).
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
                TripListScreen(onOpenTrip = { trip -> nav.navigate(TripDetailRoute(trip.id, trip.name)) })
            }
            composable<TripDetailRoute> { entry ->
                val route = entry.toRoute<TripDetailRoute>()
                TripDetailScreen(tripId = route.tripId, name = route.name, onBack = { nav.popBackStack() })
            }
        }
    }
}
