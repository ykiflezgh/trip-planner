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
import app.tripplanner.shared.feature.links.DeepLink
import app.tripplanner.shared.feature.links.DeepLinkIntake
import app.tripplanner.ui.ActivityFeedScreen
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

/** [name] may be blank when opened from a notification; the screen then uses the live trip name. */
@Serializable
data class TripDetailRoute(val tripId: String, val name: String, val focusStopId: String? = null)

@Serializable
data class ActivityRoute(val tripId: String, val name: String)

@Serializable
data class JoinRoute(val code: String)

/**
 * Trip list (+ Google sign-in) -> new trip form / join / trip detail (map, day tabs) -> activity feed.
 *
 * Complexity:
 * - **Recomposition Time:** O(1) - the NavHost composes exactly one destination; per-screen
 *   costs are documented on [TripListScreen] and [TripDetailScreen].
 * - **Composition Memory:** O(B) where B is the back-stack depth (at most 3 entries here).
 */
@Composable
fun App() {
    MaterialTheme {
        val nav = rememberNavController()
        // External entry points act once signed in: invite links redeem (design §8.2),
        // notification taps open the trip at the changed stop (design §10).
        val intake: DeepLinkIntake = koinInject()
        val auth: AuthRepository = koinInject()
        val pending by intake.pending.collectAsState()
        val user by auth.user.collectAsState(initial = auth.currentUser)
        LaunchedEffect(pending, user?.uid) {
            if (pending != null && user != null) {
                when (val link = intake.consume()) {
                    is DeepLink.Join -> nav.navigate(JoinRoute(link.code)) { launchSingleTop = true }
                    is DeepLink.OpenTrip -> nav.navigate(TripDetailRoute(link.tripId, name = "", focusStopId = link.stopId)) {
                        popUpTo<TripListRoute>()
                        launchSingleTop = true
                    }
                    null -> Unit
                }
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
                TripDetailScreen(
                    tripId = route.tripId,
                    name = route.name,
                    focusStopId = route.focusStopId,
                    onOpenActivity = { title -> nav.navigate(ActivityRoute(route.tripId, title)) },
                    onBack = { if (!nav.popBackStack()) nav.navigate(TripListRoute) { popUpTo<TripListRoute>() } },
                )
            }
            composable<ActivityRoute> { entry ->
                val route = entry.toRoute<ActivityRoute>()
                ActivityFeedScreen(tripId = route.tripId, name = route.name, onBack = { nav.popBackStack() })
            }
        }
    }
}
