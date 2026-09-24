package app.tripplanner.shared.web

import app.tripplanner.schedule.ScheduleJs
import app.tripplanner.shared.web.externals.getAuth
import app.tripplanner.shared.web.externals.getRedirectResult
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.di.AppConfig
import app.tripplanner.shared.di.sharedModule
import app.tripplanner.shared.feature.trips.TripDetailUiState
import app.tripplanner.shared.feature.trips.TripDetailViewModel
import app.tripplanner.shared.feature.trips.TripListUiState
import app.tripplanner.shared.feature.trips.TripListViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform
import kotlin.js.JsExport
import kotlin.js.Promise

/**
 * The web client's door into the Kotlin core (design v1.1.1 §6.5, companion §6.2): plain
 * objects out, callbacks and Promises in, because `@JsExport` cannot carry `Flow` or `suspend`.
 * React consumes this through `lib/kotlin` and the `useKotlinState` hook; Kotlin types never
 * reach a component.
 */
@JsExport
object TripPlannerWeb {
    private val scope = MainScope()

    /**
     * Process-wide startup: Firebase default app, Koin with the shared and browser modules.
     * Mirrors `initKoin` on iOS and `TripPlannerApp` on Android.
     *
     * Complexity:
     * - **Time:** O(D) Koin definitions.
     * - **Space:** O(D).
     */
    fun start(applicationId: String, apiKey: String, projectId: String, authDomain: String, gcmSenderId: String, placesApiKey: String, appLinkHost: String) {
        Firebase.initialize(
            options = FirebaseOptions(applicationId = applicationId, apiKey = apiKey, projectId = projectId, authDomain = authDomain, gcmSenderId = gcmSenderId),
        )
        val config = if (appLinkHost.isBlank()) AppConfig() else AppConfig(appLinkHost = appLinkHost)
        startKoin { modules(sharedModule(placesApiKey, config), jsModule()) }
    }

    /**
     * Finishes a redirect sign-in after the browser returns (companion §8.1); resolves false when
     * none was pending. A plain promise chain on purpose: awaiting this Firebase promise from a
     * coroutine on Dispatchers.Main raised a "fatal exception in coroutines machinery" in the
     * W0 spike (kotlinx-coroutines 1.10.2 on JS), which is logged as an open question.
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun resumeRedirect(): Promise<Boolean> = getRedirectResult(getAuth()).then<Boolean> { result: dynamic -> result != null }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun tripList(): TripListFacade = TripListFacade(KoinPlatform.getKoin().get(), scope)

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun tripDetail(tripId: String): TripDetailFacade = TripDetailFacade(KoinPlatform.getKoin().get { parametersOf(tripId) }, scope)

    /**
     * The schedule engine, same build as the apps and the feed (design §6.5): JSON in, JSON out.
     *
     * Complexity:
     * - **Time:** O(E) for E entries.
     * - **Space:** O(E).
     */
    fun computeDay(dateIso: String, dayStart: String, dayEnd: String, entriesJson: String, travelJson: String, defaultMode: String): String =
        ScheduleJs.computeDay(dateIso, dayStart, dayEnd, entriesJson, travelJson, defaultMode)
}

/** Trip list screen state (design companion §6.2). `subscribe` returns the unsubscribe function. */
@JsExport
class TripListFacade internal constructor(private val vm: TripListViewModel, private val scope: kotlinx.coroutines.CoroutineScope) {
    /**
     * Complexity:
     * - **Time:** O(T) per emission for T trips.
     * - **Space:** O(T).
     */
    fun subscribe(onState: (Any) -> Unit): () -> Unit = vm.state.subscribeJs(scope) { onState(it.toJs()) }
    fun signIn() = vm.signIn()
    fun signOut() = vm.signOut()
    fun dismissError() = vm.dismissError()
}

/** Trip detail screen state: the stops of the selected day and the computed schedule. */
@JsExport
class TripDetailFacade internal constructor(private val vm: TripDetailViewModel, private val scope: kotlinx.coroutines.CoroutineScope) {
    /**
     * Complexity:
     * - **Time:** O(S) per emission for S stops on the selected day.
     * - **Space:** O(S).
     */
    fun subscribe(onState: (Any) -> Unit): () -> Unit = vm.state.subscribeJs(scope) { onState(it.toJs()) }
    fun selectDay(day: Int) = vm.selectDay(day)
    fun selectStop(stopId: String?) = vm.selectStop(stopId)
}

private fun <T> StateFlow<T>.subscribeJs(scope: kotlinx.coroutines.CoroutineScope, onEach: (T) -> Unit): () -> Unit {
    val job: Job = scope.launch { collect { onEach(it) } }
    return { job.cancel() }
}

private fun obj(): dynamic = js("({})")

private fun Trip.toJs(): dynamic {
    val o = obj()
    o.id = id; o.name = name; o.startDate = startDate; o.endDate = endDate; o.timeZone = timeZone
    o.defaultDayStart = defaultDayStart; o.defaultDayEnd = defaultDayEnd; o.defaultTravelMode = defaultTravelMode
    o.ownerId = ownerId; o.memberIds = memberIds.toTypedArray(); o.pendingSync = pendingSync
    return o
}

private fun TripListUiState.toJs(): dynamic {
    val o = obj()
    o.loading = loading; o.signedIn = signedIn; o.userName = userName; o.signingIn = signingIn
    o.pushEnabled = pushEnabled; o.error = error
    o.trips = trips.map { t -> t.toJs() }.toTypedArray()
    return o
}

private fun TripDetailUiState.toJs(): dynamic {
    val o = obj()
    o.loading = loading; o.error = error; o.online = online; o.selectedDay = selectedDay; o.selectedStopId = selectedStopId
    o.dayCount = dayCount; o.canEdit = canEdit; o.isOwner = isOwner; o.pendingSync = pendingSync
    o.trip = trip?.toJs()
    o.stops = stopsForSelectedDay.map { s ->
        val j = obj()
        j.id = s.id; j.name = s.name; j.kind = s.kind; j.day = s.day; j.order = s.order; j.durationMin = s.durationMin
        j.fixedStart = s.fixedStart; j.address = s.address; j.lat = s.lat; j.lng = s.lng
        j
    }.toTypedArray()
    // Computed times come from the same engine call the apps make; handed over as JSON text.
    o.scheduleJson = schedule?.let { app.tripplanner.schedule.ScheduleJson.encode(it) }
    return o
}
