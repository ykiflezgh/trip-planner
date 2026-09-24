package app.tripplanner.shared.web

import app.tripplanner.schedule.ScheduleJs
import app.tripplanner.shared.web.externals.getAuth
import app.tripplanner.shared.web.externals.getRedirectResult
import app.tripplanner.shared.core.model.TravelMode
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.feature.calendar.TimeDisplay
import app.tripplanner.shared.feature.trips.TripDays
import app.tripplanner.shared.di.AppConfig
import app.tripplanner.shared.di.sharedModule
import app.tripplanner.shared.feature.trips.TripDetailUiState
import app.tripplanner.shared.feature.trips.TripDetailViewModel
import app.tripplanner.shared.feature.trips.TripListUiState
import app.tripplanner.shared.feature.trips.TripListViewModel
import androidx.lifecycle.ViewModelStore
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
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
class TripListFacade internal constructor(private val vm: TripListViewModel, private val scope: CoroutineScope) {
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

/**
 * Trip detail screen state: the stops of the selected day, both travel legs between consecutive
 * place stops, and the computed schedule, in trip time or (after [setLocalTime]) device-local
 * time exactly as the apps' toggle does (design v1.1 §6.6).
 */
@JsExport
class TripDetailFacade internal constructor(private val vm: TripDetailViewModel, private val scope: CoroutineScope) {
    private val localTime = MutableStateFlow(false)
    // ViewModel.clear() is internal; a private store owns the ViewModel so close() can clear it.
    private val store = ViewModelStore().apply { put("trip", vm) }

    /**
     * Complexity:
     * - **Time:** O(S) per emission for S stops on the selected day.
     * - **Space:** O(S).
     */
    fun subscribe(onState: (Any) -> Unit): () -> Unit {
        val job: Job = scope.launch { combine(vm.state, localTime) { s, local -> s.toJs(local) }.collect { onState(it) } }
        return { job.cancel() }
    }
    fun selectDay(day: Int) = vm.selectDay(day)
    fun selectStop(stopId: String?) = vm.selectStop(stopId)
    /** Show times in the browser's zone instead of the trip's; display only, nothing stored. */
    fun setLocalTime(enabled: Boolean) { localTime.value = enabled }
    /**
     * Releases the ViewModel (Firestore listeners detach once the last subscriber is gone).
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun close() = store.clear()
}

private fun <T> StateFlow<T>.subscribeJs(scope: CoroutineScope, onEach: (T) -> Unit): () -> Unit {
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

private fun TripDetailUiState.toJs(local: Boolean): dynamic {
    val o = obj()
    o.loading = loading; o.error = error; o.online = online; o.selectedDay = selectedDay; o.selectedStopId = selectedStopId
    o.dayCount = dayCount; o.canEdit = canEdit; o.isOwner = isOwner; o.pendingSync = pendingSync
    o.trip = trip?.toJs()
    val dayStops = stopsForSelectedDay
    o.stops = dayStops.map { s ->
        val j = obj()
        j.id = s.id; j.name = s.name; j.kind = s.kind; j.day = s.day; j.order = s.order; j.durationMin = s.durationMin
        j.fixedStart = s.fixedStart; j.address = s.address; j.lat = s.lat; j.lng = s.lng; j.notes = s.notes
        j
    }.toTypedArray()
    // Both modes between consecutive place stops (design §8.3), as the Android agenda shows them.
    val places = dayStops.filter { it.hasPlace }
    o.legs = places.zipWithNext().flatMap { (a, b) ->
        TravelMode.entries.mapNotNull { mode ->
            legs[Triple(a.id, b.id, mode)]?.let { leg ->
                val j = obj()
                j.from = a.id; j.to = b.id; j.mode = mode.name.lowercase(); j.seconds = leg.seconds; j.meters = leg.meters; j.error = leg.error
                j
            }
        }
    }.toTypedArray()
    // Time-zone toggle (design v1.1 §6.6): shift the computed schedule through instants, never the stored values.
    val tripZone = trip?.timeZone?.let(TimeDisplay::zone)
    val device = TimeZone.currentSystemDefault()
    val date = trip?.let { TripDays.date(it, selectedDay) }
    val differs = tripZone != null && date != null && TimeDisplay.differs(tripZone, device, LocalDateTime(date, LocalTime(12, 0)))
    val shown = schedule?.let { if (local && differs && tripZone != null) TimeDisplay.shift(it, tripZone, device) else it }
    o.scheduleJson = shown?.let { app.tripplanner.schedule.ScheduleJson.encode(it) }
    o.tripZone = trip?.timeZone; o.deviceZone = device.id; o.zoneDiffers = differs; o.localTime = local && differs
    o.zoneLabel = TimeDisplay.label(if (local && differs) device.id else trip?.timeZone.orEmpty())
    return o
}
