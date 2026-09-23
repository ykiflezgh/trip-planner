package app.tripplanner.shared.di

import app.tripplanner.shared.data.ActivityRepository
import app.tripplanner.shared.data.AuthRepository
import app.tripplanner.shared.data.FirestoreActivityRepository
import app.tripplanner.shared.data.FirestoreUserRepository
import app.tripplanner.shared.data.UserRepository
import app.tripplanner.shared.feature.activity.ActivityFeedViewModel
import app.tripplanner.shared.feature.links.DeepLinkIntake
import app.tripplanner.shared.feature.notifications.PushRegistrar
import app.tripplanner.shared.data.CalendarApi
import app.tripplanner.shared.data.FirestoreNetwork
import app.tripplanner.shared.data.FirestoreNetworkSync
import app.tripplanner.shared.data.GitLiveFirestoreNetwork
import app.tripplanner.shared.data.FirestoreTripRepository
import app.tripplanner.shared.data.PlacesApi
import app.tripplanner.shared.data.PlanningFunctions
import app.tripplanner.shared.data.TripRepository
import app.tripplanner.shared.feature.invites.JoinTripViewModel
import app.tripplanner.shared.feature.events.AddEventViewModel
import app.tripplanner.shared.feature.trips.NewTripViewModel
import app.tripplanner.shared.feature.trips.TripDetailViewModel
import app.tripplanner.shared.feature.trips.TripListViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * [placesApiKey] comes from platform config (manifest placeholder / plist), not source.
 * Platform boundaries ([app.tripplanner.shared.platform.GoogleSignInProvider],
 * [app.tripplanner.shared.platform.ConnectivityMonitor], ...) are bound by the platform
 * module started alongside this one (design §6.4).
 */
/** Build-time switches passed from the platform entry point. */
data class AppConfig(
    /** Firebase Hosting domain that serves /join/{code} and the App/Universal Link files (design §8.2). */
    val appLinkHost: String = "tripplanner-dev-fe0a4.web.app",
)

fun sharedModule(placesApiKey: String, config: AppConfig = AppConfig()) = module {
    single { config }
    single {
        HttpClient {
            expectSuccess = true // non-2xx -> exception; PlacesApi turns it into a readable message
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }
    single<TripRepository> { FirestoreTripRepository() }
    single { AuthRepository(get()) }
    single<FirestoreNetwork> { GitLiveFirestoreNetwork() }
    // Process-lifetime: follows connectivity and kicks Firestore's connection (design §9).
    single(createdAtStart = true) { FirestoreNetworkSync(get(), get()).also { it.start() } }
    single { PlacesApi(get(), placesApiKey) }
    single { CalendarApi(get()) }
    single { PlanningFunctions() }
    single { DeepLinkIntake() }
    single<UserRepository> { FirestoreUserRepository() }
    single<ActivityRepository> { FirestoreActivityRepository() }
    // Process-lifetime: registers this device's FCM token for the signed-in user (design §10).
    single(createdAtStart = true) { PushRegistrar(get(), get(), get()).also { it.start() } }
    viewModel { TripListViewModel(get(), get(), get(), get()) }
    viewModel { NewTripViewModel(get(), get()) }
    viewModel { (tripId: String) -> TripDetailViewModel(tripId, get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (tripId: String) -> ActivityFeedViewModel(tripId, get(), get()) }
    viewModel { (code: String) -> JoinTripViewModel(code, get(), get()) }
    viewModel { (tripId: String) -> AddEventViewModel(tripId, get(), get(), get(), get()) }
}
