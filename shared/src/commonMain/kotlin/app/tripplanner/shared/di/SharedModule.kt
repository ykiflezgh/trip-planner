package app.tripplanner.shared.di

import app.tripplanner.shared.data.AuthRepository
import app.tripplanner.shared.data.CalendarApi
import app.tripplanner.shared.data.FirestoreTripRepository
import app.tripplanner.shared.data.PlacesApi
import app.tripplanner.shared.data.PlanningFunctions
import app.tripplanner.shared.data.TripRepository
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
 * Platform boundaries ([app.tripplanner.shared.platform.GoogleSignInProvider], ...) are
 * bound by the platform module started alongside this one (design §6.4).
 */
fun sharedModule(placesApiKey: String) = module {
    single { HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } } }
    single<TripRepository> { FirestoreTripRepository() }
    single { AuthRepository(get()) }
    single { PlacesApi(get(), placesApiKey) }
    single { CalendarApi(get()) }
    single { PlanningFunctions() }
    viewModel { TripListViewModel(get(), get()) }
    viewModel { NewTripViewModel(get(), get()) }
    viewModel { (tripId: String) -> TripDetailViewModel(tripId, get()) }
}
