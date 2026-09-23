package app.tripplanner

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.tripplanner.shared.data.AuthRepository
import app.tripplanner.shared.feature.invites.InviteIntake
import app.tripplanner.ui.JoinTripScreen
import app.tripplanner.ui.NewTripScreen
import app.tripplanner.ui.TripDetailScreen
import app.tripplanner.ui.TripListScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object TripListRoute

@Serializable
object NewTripRoute

@Serializable
data class TripDetailRoute(val tripId: String, val name: String)

@Serializable
data class JoinRoute(val code: String)

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
        // Invite links (App Link / Universal Link / "Join a trip"): redeem once signed in (design §8.2).
        val intake: InviteIntake = koinInject()
        val auth: AuthRepository = koinInject()
        val pendingCode by intake.pendingCode.collectAsState()
        val user by auth.user.collectAsState(initial = auth.currentUser)
        LaunchedEffect(pendingCode, user?.uid) {
            if (pendingCode != null && user != null) {
                intake.consume()?.let { code -> nav.navigate(JoinRoute(code)) { launchSingleTop = true } }
            }
        }
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
            composable<JoinRoute> { entry ->
                val route = entry.toRoute<JoinRoute>()
                JoinTripScreen(
                    code = route.code,
                    onJoined = { id, name -> nav.navigate(TripDetailRoute(id, name)) { popUpTo<TripListRoute>() } },
                    onBack = { nav.popBackStack(TripListRoute, inclusive = false) },
                )
            }
            composable<TripDetailRoute> { entry ->
                val route = entry.toRoute<TripDetailRoute>()
                TripDetailScreen(tripId = route.tripId, name = route.name, onBack = { nav.popBackStack() })
            }
        }
    }
}
